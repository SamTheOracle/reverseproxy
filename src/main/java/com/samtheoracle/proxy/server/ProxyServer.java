package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisOptions;
import com.samtheoracle.proxy.services.CacheService;
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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
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
    private static final int TIMEOUT_FAILURE = Integer.parseInt(Optional.ofNullable(System.getenv("TIMEOUT_FAILURE")).orElse("4"));
    private final static int HEARTBEAT = Integer.parseInt(Optional.ofNullable(System.getenv("HEARTBEAT")).orElse("10"));
    private static final int CACHE_MAX_AGE = Integer.parseInt(Optional.ofNullable(System.getenv("CACHE_MAX_AGE")).orElse("60"));
    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
    private CacheService cacheService;
    private ServiceDiscovery discovery;



    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        super.start();
        this.cacheService = CacheService.create(vertx);
        this.discovery = createDiscovery(REDIS_DB_HOST, REDIS_DB_PORT, REDIS_KEY_SERVICES);
        final Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create(".*.")
                .allowedHeader(HttpHeaderNames.CONTENT_TYPE.toString())
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString()));
        router.route().handler(BodyHandler.create());
        router.get("/stats").handler(routingContext -> {
            double divider = 1024.0 * 1024.0;
            Runtime runtime = Runtime.getRuntime();
            JsonObject object = new JsonObject();
            object.put("currentHeapSize", runtime.totalMemory() / divider)
                    .put("maxHeap", runtime.maxMemory() / divider)
                    .put("freeMemory", runtime.freeMemory() / divider);
            routingContext.response().end(object.encode());
        });
        router.get("/").handler(routingContext -> routingContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.TEXT_PLAIN).end("Welcome to ssl proxy!"));
        router.post("/services").handler(this::handleServiceCreation);
        router.get("/services").handler(this::handleGetServices);
        router.delete("/services").handler(this::handleDeleteAllServices);
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
            LOGGER.info("deployed at port " + PORT + " with root path " + ROOT_PATH);
            startPromise.complete();
        }).onFailure(startPromise::fail);
    }

    private void handleDeleteAllServices(RoutingContext routingContext) {
        Promise<List<Record>> recordsPromise = Promise.promise();
        List<Promise<Void>> unpublishPromises = new ArrayList<>();

        discovery.getRecords(record -> true, recordsPromise);

        recordsPromise.future().onSuccess(records -> records.forEach(record -> {
            Promise<Void> promise = Promise.promise();
            discovery.unpublish(record.getRegistration(), promise);
            unpublishPromises.add(promise);
        }));
        CompositeFuture.all(unpublishPromises.stream().map(Promise::future).collect(Collectors.toList()))
                .onComplete(cFutures -> {
                    if (cFutures.succeeded()) {
                        NoContent(new HashMap<>(), routingContext);
                    } else {
                        ServerError(cFutures.cause().getMessage(), routingContext);
                    }
                });
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
        Promise<Record> serviceAlreadyPresent = Promise.promise();
        discovery.getRecord(r -> r.getName().equals(record.getName()) && r.getLocation().getString("host").equals(record.getLocation().getString("host")), recordAsync -> {
            if (recordAsync.succeeded() && recordAsync.result() != null) {
                serviceAlreadyPresent.complete(recordAsync.result());

            } else if (recordAsync.succeeded()) {
                serviceAlreadyPresent.fail(new IllegalArgumentException("Service is not present"));
            } else {
                serviceAlreadyPresent.fail(recordAsync.cause());
            }
        });
        serviceAlreadyPresent.future()
                .onSuccess(r -> {
                    LOGGER.info("Service already exists");
                    Ok(record.toJson(), new HashMap<>(), routingContext);
                })
                .onFailure(cause -> publishHttpEndPoint(record, discovery)
                        .future()
                        .onSuccess(r -> {
                            LOGGER.info("correctly published");
                            LOGGER.info(JsonObject.mapFrom(r).encodePrettily());
                            HashMap<String, String> headers = new HashMap<>();
                            headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
                            headers.put(HttpHeaderNames.LAST_MODIFIED.toString(), r.getMetadata().getString("creationDate"));
                            headers.put(HttpHeaderNames.LOCATION.toString(), routingContext.request().absoluteURI() + "/" + r.getRegistration());
                            Created(JsonObject.mapFrom(r), headers, routingContext);
                        }).onFailure(reason -> {
                            BadRequest(cause.getMessage(), routingContext);
                            cause.printStackTrace();
                        }));

    }

    private void rerouteToService(RoutingContext routingContext, String root, String uri) {
        LOGGER.info("handling request " + routingContext.request().method().name() + " " + routingContext.request().absoluteURI());
        HttpEndpoint.getWebClient(discovery, record -> record.getLocation().getString("root").equals(root), webClientAsyncResult -> {
            if (webClientAsyncResult.succeeded()) {
                WebClient webClient = webClientAsyncResult.result();
                Buffer body = routingContext.getBody();
                HttpServerRequest httpServerRequest = routingContext.request();
                HttpServerResponse httpServerResponse = routingContext.response();
                HttpMethod method = httpServerRequest.method();

                HttpRequest<Buffer> request = webClient.request(method, uri);
                request.timeout(1000);
                request.putHeaders(httpServerRequest.headers());

                //put, post with body
                if (body != null && !body.toString().isEmpty()) {
                    //send json object does not work...
                    LOGGER.info("Payload " + body.toString());
                    request.sendBuffer(body, httpResponseAsyncResult -> handleHttpResponse(uri, httpServerResponse, httpResponseAsyncResult, webClient, discovery));
                } else if (method == HttpMethod.GET) {
                    handleCachingGetRequest(uri, request, httpServerResponse, routingContext, webClient, discovery);
                } else {
                    request.send(httpResponseAsyncResult -> handleHttpResponse(uri, httpServerResponse, httpResponseAsyncResult, webClient, discovery));
                }
            } else {
                BadRequest("service with root " + root + " was not found", routingContext);

            }

        });
    }

    private void handleCachingGetRequest(String uri, HttpRequest<Buffer> request, HttpServerResponse httpServerResponse,
                                         RoutingContext routingContext, WebClient webClient, ServiceDiscovery discovery) {

        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
        cacheService.findCachedResponse(uri)
                .future()
                .onSuccess(cacheResponse -> {
                    LOGGER.info("Retrieving " + uri + " from redis");
                    Ok(JsonObject.mapFrom(cacheResponse), hashMap, routingContext);
                }).onFailure(cause -> request.timeout(2000).send(httpResponseAsyncResult -> {
            if (httpResponseAsyncResult.succeeded() && httpResponseAsyncResult.result().statusCode() == HttpResponseStatus.OK.code()) {
                HttpResponse<Buffer> response = httpResponseAsyncResult.result();
                response.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
                Buffer responseBody = response.body();
                //send back result and cache in redis
                int cacheAge = Math.min(Integer.parseInt(Optional.ofNullable(request.headers().get(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE)).orElse("0")), CACHE_MAX_AGE);

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
                    cachedResponse.setCached(true);
                    cachedResponse.setRedisOptions(new RedisOptions().setCacheExpiration(cacheAge).setKey(uri));
                    cacheService.saveCachedResponse(cachedResponse)
                            .future()
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
                        .put("error", httpResponseAsyncResult.result() != null ? httpResponseAsyncResult.result().body() : "Unknown error");
                ServerError(errorJson.encode(), routingContext);
                ServiceDiscovery.releaseServiceObject(discovery, webClient);
            }
        }));
    }

    private void handleHttpResponse(String uri, HttpServerResponse serverResponse,
                                    AsyncResult<HttpResponse<Buffer>> httpResponseAsyncResult, WebClient webClient, ServiceDiscovery discovery) {
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
    }

    private static Promise<Record> publishHttpEndPoint(Record record, ServiceDiscovery discovery) {
        Promise<Record> recordPromise = Promise.promise();

        discovery.publish(record, recordPromise);
        return recordPromise;
    }

}
