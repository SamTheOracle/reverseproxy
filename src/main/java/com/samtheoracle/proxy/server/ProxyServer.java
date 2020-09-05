package com.samtheoracle.proxy.server;

import com.oracolo.database.builder.DatabaseServiceBuilder;
import com.oracolo.database.builder.redis.RedisOptions;
import com.samtheoracle.proxy.utils.SSLUtils;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProxyServer extends RestEndpoint {

    private static final String ROOT_PATH = Optional.ofNullable(System.getenv("ROOT_PATH")).orElse("/api/v1");
    private static final int PORT = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("8080"));
    private static final String REDIS_KEY_SERVICES = Optional.ofNullable(System.getenv("REDIS_KEY_SERVICES")).orElse("http_endpoints");
    private static final String REDIS_DB_HOST = Optional.ofNullable(System.getenv("REDIS_DB_HOST")).orElse("localhost");
    private static final String REDIS_DB_PORT = Optional.ofNullable(System.getenv("REDIS_DB_PORT")).orElse("6379");
    private static final int CACHE_MAX_AGE = Integer.parseInt(Optional.ofNullable(System.getenv("CACHE_MAX_AGE")).orElse("60"));
    private final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
    private ServiceDiscovery discovery;

    private static Promise<Record> publishHttpEndPoint(Record record, ServiceDiscovery discovery) {
        Promise<Record> recordPromise = Promise.promise();

        discovery.publish(record, recordPromise);
        return recordPromise;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        super.start();
        final HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
        final Router router = Router.router(vertx);

        router.route().handler(CorsHandler.create(".*.")
                .allowedHeader(HttpHeaderNames.CONTENT_TYPE.toString())
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString()));
        router.route().handler(BodyHandler.create());
        router.get("/").handler(routingContext -> routingContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.TEXT_PLAIN).end("Welcome to ssl proxy!"));
        router.post("/services").handler(this::handleServiceCreation);
        router.get("/services").handler(this::handleGetServices);
        router.delete("/services").handler(routingContext -> handleDeleteAllServices(routingContext, healthCheckHandler));

        router.route(ROOT_PATH + "/*").handler(routingContext -> {
            String uri = routingContext.request().uri().split(ROOT_PATH)[1];
            String root = routingContext.normalisedPath().split("/")[3];
            rerouteToService(routingContext, "/" + root, uri);
        });

        router.get("/infos").handler(routingContext -> {
            JsonArray j = new JsonArray();
            router.getRoutes().stream().map(JsonObject::mapFrom).forEach(j::add);
            routingContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(new JsonObject().put("message", "Reverse Proxy Routes").put("routes", j).encodePrettily());
        });


        createServer(PORT, router, SSLUtils.httpSSLServerOptions()).future().onSuccess(httpServer -> {
            LOGGER.info("Server started on port " + httpServer.actualPort());

            this.discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions().setBackendConfiguration(
                    new JsonObject().put("host", REDIS_DB_HOST).put("port", REDIS_DB_PORT).put("key", REDIS_KEY_SERVICES)));

            LOGGER.info("deployed at port " + PORT + " with root path " + ROOT_PATH);
            startPromise.complete();
        }).onFailure(startPromise::fail);
    }

    private void handleDeleteAllServices(RoutingContext routingContext, HealthCheckHandler healthCheckHandler) {
        Promise<List<Record>> recordsPromise = Promise.promise();
        List<Promise<Void>> unpublishPromises = new ArrayList<>();

        discovery.getRecords(record -> true, recordsPromise);
        recordsPromise.future().onSuccess(records -> records.forEach(record -> {
            Promise<Void> promise = Promise.promise();
            discovery.unpublish(record.getRegistration(), promise);
            healthCheckHandler.unregister(record.getRegistration());
            unpublishPromises.add(promise);
        }));
        CompositeFuture.all(unpublishPromises.stream().map(Promise::future).collect(Collectors.toList()))
                .onSuccess(event -> NoContent(new HashMap<>(), routingContext))
                .onFailure(cause -> ServerError(cause.getMessage(), routingContext));
    }

    private void handleGetServices(RoutingContext routingContext) {
        Promise<List<Record>> promise = Promise.promise();
        discovery.getRecords(record -> true, ar -> {
            if (ar.succeeded() && !ar.result().isEmpty()) {
                promise.complete(ar.result());
            } else if (ar.succeeded()) {
                promise.fail(new IllegalArgumentException("No service is present on the system"));
            } else {
                promise.fail(ar.cause());
            }
        });
        promise.future().onSuccess(records -> {
            JsonArray jsonArray = new JsonArray();
            records.stream().map(JsonObject::mapFrom).forEach(jsonArray::add);
            Ok(jsonArray, new HashMap<>(), routingContext);
        }).onFailure(cause -> NotFound(cause.getMessage(), routingContext));
    }

    private void handleServiceCreation(RoutingContext routingContext) {
        JsonObject service = routingContext.getBodyAsJson();
        Record record = HttpEndpoint.createRecord(service.getString("name"), service.getJsonObject("location").getString("host"),
                service.getJsonObject("location").getInteger("port"), service.getJsonObject("location").getString("root"), new JsonObject().put("creationDate", LocalDateTime.now().toString()));
        publishHttpEndPoint(record, discovery).future().onSuccess(r -> {
            LOGGER.info("correctly published");
            LOGGER.info(JsonObject.mapFrom(r).encodePrettily());
            HashMap<String, String> headers = new HashMap<>();
            headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
            headers.put(HttpHeaderNames.LAST_MODIFIED.toString(), r.getMetadata().getString("creationDate"));
            headers.put(HttpHeaderNames.LOCATION.toString(), routingContext.request().absoluteURI() + "/" + r.getRegistration());
            Created(JsonObject.mapFrom(r), headers, routingContext);
        }).onFailure(cause -> {
            BadRequest(cause.getMessage(), routingContext);
            cause.printStackTrace();
        });
    }

    private void rerouteToService(RoutingContext routingContext, String root, String uri) {

        HttpEndpoint.getWebClient(discovery, record -> record.getLocation().getString("root").equals(root), webClientAsyncResult -> {
            if (webClientAsyncResult.succeeded()) {
                WebClient webClient = webClientAsyncResult.result();
                Buffer body = routingContext.getBody();
                HttpServerRequest httpServerRequest = routingContext.request();
                HttpServerResponse httpServerResponse = routingContext.response();
                HttpMethod method = httpServerRequest.method();

                HttpRequest<Buffer> request = webClient.request(method, uri);

                request.putHeaders(httpServerRequest.headers());

                //put, post with body
                if (body != null && !body.toString().isEmpty()) {
                    //send json object does not work...
                    LOGGER.info("Payload " + body.toString());
                    request.sendBuffer(body,
                            httpResponseAsyncResult -> handleHttpResponse(uri, httpServerResponse, httpResponseAsyncResult, webClient));
                } else if (method == HttpMethod.GET) {
                    handleCachingGetRequest(uri, request, httpServerResponse, routingContext, webClient);
                } else {
                    request.send(httpResponseAsyncResult -> handleHttpResponse(uri, httpServerResponse, httpResponseAsyncResult, webClient));
                }
            } else {
                BadRequest("service with root " + root + " was not found", routingContext);

                //				discovery.close();
            }

        });
    }

    private void handleCachingGetRequest(String uri, HttpRequest<Buffer> request, HttpServerResponse httpServerResponse,
                                         RoutingContext routingContext, WebClient webClient) {

        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
        DatabaseServiceBuilder.redis().findBuilder(vertx).setOptions(CachedResponse.class).find(uri).future().onSuccess(cacheResponse -> {
            LOGGER.info("Retrieving " + uri + " from redis");
            Ok(JsonObject.mapFrom(cacheResponse), hashMap, routingContext);
        }).onFailure(cause -> request.send(httpResponseAsyncResult -> {
            if (httpResponseAsyncResult.succeeded() && httpResponseAsyncResult.result().statusCode() == HttpResponseStatus.OK.code()) {
                HttpResponse<Buffer> response = httpResponseAsyncResult.result();
                response.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
                Buffer responseBody = response.body();
                //send back result and cache in redis
                int cacheAge = Math.min(Integer.parseInt(Optional.ofNullable(request.headers().get(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE)).orElse("0")), CACHE_MAX_AGE);
                LOGGER.info("cache age " + cacheAge);
                //if responsobody is not a JSON string, it breaks
                CachedResponse cachedResponse = new CachedResponse(responseBody.toJson(), false);
                JsonObject httpServerResponseJson = JsonObject.mapFrom(cachedResponse);
                int bytes = httpServerResponseJson.encode().getBytes().length;
                response.headers().entries().forEach(entry -> httpServerResponse.putHeader(entry.getKey(), entry.getValue()));
                httpServerResponse.putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(bytes));
                if (httpResponseAsyncResult.result().statusCode() == HttpResponseStatus.OK.code() && cacheAge != 0) {
                    httpServerResponse.setStatusCode(response.statusCode())
                            .end(httpServerResponseJson.toBuffer());
                    ServiceDiscovery.releaseServiceObject(discovery, webClient);
                    LOGGER.info(httpServerResponseJson.encodePrettily());
                    DatabaseServiceBuilder.redis().insertBuilder(vertx)
                            .setOptions(CachedResponse.class, new RedisOptions().setCacheExpiration(cacheAge).setKey(uri))
                            .save(new CachedResponse(responseBody.toJson(), true)).future()
                            .onSuccess(redis -> LOGGER.info("successfully cached get request " + uri))
                            .onFailure(reason -> LOGGER.info("could not cache in redis " + reason.getMessage()));
                } else {
                    httpServerResponse.setStatusCode(response.statusCode())
                            .end(httpServerResponseJson.encode());
                    ServiceDiscovery.releaseServiceObject(discovery, webClient);
                }
            } else if (httpResponseAsyncResult.succeeded()) {
                JsonObject errorJson = new JsonObject()
                        .put("status", httpResponseAsyncResult.result().statusCode())
                        .put("error", httpResponseAsyncResult.result().body().toString());
                httpServerResponse.setStatusCode(httpResponseAsyncResult.result().statusCode())
                        .end(errorJson.encode());
                ServiceDiscovery.releaseServiceObject(discovery, webClient);
            } else {
                JsonObject errorJson = new JsonObject()
                        .put("status", HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .put("error", httpResponseAsyncResult.result().body());
                ServerError(errorJson.encode(), routingContext);
                ServiceDiscovery.releaseServiceObject(discovery, webClient);
            }
            //			discovery.close();
        }));
    }

    private void handleHttpResponse(String uri, HttpServerResponse serverResponse,
                                    AsyncResult<HttpResponse<Buffer>> httpResponseAsyncResult, WebClient webClient) {
        if (httpResponseAsyncResult.succeeded()) {
            HttpResponse<Buffer> response = httpResponseAsyncResult.result();
            Buffer body = response.bodyAsBuffer();
            response.headers().forEach(header -> serverResponse.putHeader(header.getKey(), header.getValue()));
            serverResponse.putHeader(HttpHeaderNames.LOCATION, ROOT_PATH + uri);
            if (body == null) {
                serverResponse.setStatusCode(response.statusCode()).end();
            } else {
                serverResponse.setStatusCode(response.statusCode()).end(body);
            }
        } else {
            LOGGER.info("Request " + uri + " failed\n" + httpResponseAsyncResult.cause().getMessage());
            serverResponse.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
        ServiceDiscovery.releaseServiceObject(discovery, webClient);
        //		discovery.close();

    }
}
