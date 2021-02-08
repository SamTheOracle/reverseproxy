package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisOptions;
import com.samtheoracle.proxy.handler.ProxyHandler;
import com.samtheoracle.proxy.handler.ServiceHandler;
import com.samtheoracle.proxy.search.ServiceSearchParameter;
import com.samtheoracle.proxy.services.CacheService;
import com.samtheoracle.proxy.utils.ClientUtils;
import com.samtheoracle.proxy.utils.Config;
import com.samtheoracle.proxy.utils.SSLUtils;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProxyServer extends RestEndpoint {
    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
    private static final String REGISTRATION_ID = "registrationId";

    private CacheService cacheService;
    private ServiceHandler serviceHandler;
    private ProxyHandler proxyHandler;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        super.start();
        final ServiceDiscovery discovery = createDiscovery();
        final WebClient client = ClientUtils.httpClient(vertx);

        this.cacheService = new CacheService(vertx);
        this.serviceHandler = new ServiceHandler(discovery);
        this.proxyHandler = new ProxyHandler(client, serviceHandler);

        final Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create(".*.").allowedHeader(HttpHeaderNames.CONTENT_TYPE.toString())
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString()));
        router.route().handler(BodyHandler.create());
        router.get("/stats").handler(routingContext -> {
            double divider = 1024.0 * 1024.0;
            Runtime runtime = Runtime.getRuntime();
            JsonObject object = new JsonObject();
            object.put("currentHeapSize", runtime.totalMemory() / divider).put("maxHeap", runtime.maxMemory() / divider)
                    .put("freeMemory", runtime.freeMemory() / divider);
            routingContext.response().end(object.encode());
        });
        router.get("/")
                .handler(routingContext -> routingContext.response()
                        .putHeader(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.TEXT_PLAIN)
                        .end("Welcome to ssl proxy!"));
        router.post("/services").handler(this::handleServiceCreation);
        router.get("/services").handler(this::handleGetServices);
        router.get("/service/:" + REGISTRATION_ID).handler(this::handleGetServiceById);
        router.delete("/services/all").handler(this::handleDeleteAllServices);
        router.delete("/services/:" + REGISTRATION_ID).handler(this::handleDeleteById);
        router.route(Config.ROOT_PATH + "/*").handler(routingContext -> {
            String uri = routingContext.request().uri().split(Config.ROOT_PATH)[1];
            String root = routingContext.normalisedPath().split("/")[3];
            rerouteToService(routingContext, "/" + root, uri);
        });

        router.get("/infos").handler(routingContext -> {
            JsonArray j = new JsonArray();
            router.getRoutes().stream().map(JsonObject::mapFrom).forEach(j::add);
            routingContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(new JsonObject().put("message", "Reverse Proxy Routes").put("routes", j).encodePrettily());
        });
        if (Config.SSL) {
            createServer(Config.PORT, router, SSLUtils.httpSSLServerOptions()).future().onSuccess(httpServer -> {
                LOGGER.info("Server started on port " + httpServer.actualPort());
                LOGGER.info("deployed at port " + Config.PORT + " with root path " + Config.ROOT_PATH);
                startPromise.complete();
            }).onFailure(startPromise::fail);
        } else {

            createServer(Config.PORT, router).future().onSuccess(httpServer -> {
                LOGGER.info("Proxy Server Instance " + this);
                LOGGER.info("Server started on port " + httpServer.actualPort());
                LOGGER.info("deployed at port " + Config.PORT + " with root path " + Config.ROOT_PATH);
                startPromise.complete();
            }).onFailure(startPromise::fail);
        }

    }

    private void handleDeleteById(RoutingContext routingContext) {
        String registration = routingContext.pathParam(REGISTRATION_ID);
        if (registration == null || registration.isEmpty()) {
            BadRequest("Wrong registration id", routingContext);
            return;
        }
        serviceHandler.deleteRecordByRegistration(registration).future()
                .onSuccess(record -> NoContent(new HashMap<>(), routingContext))
                .onFailure(cause -> BadRequest(cause.getMessage(), routingContext));
    }

    private void handleGetServiceById(RoutingContext routingContext) {
        String registration = routingContext.pathParam(REGISTRATION_ID);
        if (registration == null || registration.isEmpty()) {
            BadRequest("Wrong registration id", routingContext);
            return;
        }
        serviceHandler.getRecordByRegistration(registration).future()
                .onSuccess(record -> Ok(JsonObject.mapFrom(record), new HashMap<>(), routingContext))
                .onFailure(cause -> BadRequest(cause.getMessage(), routingContext));
    }

    private void handleDeleteAllServices(RoutingContext routingContext) {
        serviceHandler.deleteAllRecords().future()
                .onSuccess(aVoid -> NoContent(new HashMap<>(), routingContext))
                .onFailure(cause -> ServerError(cause.getMessage(), routingContext));
    }

    private void handleGetServices(RoutingContext routingContext) {
        Map<ServiceSearchParameter, List<String>> query = new HashMap<>();
        Arrays.stream(ServiceSearchParameter.values()).forEach(serviceSearchParameter -> {
            List<String> queryParam = routingContext.queryParam(serviceSearchParameter.name());
            if (!queryParam.isEmpty()) {
                query.put(serviceSearchParameter, queryParam);
            }
        });

        Promise<List<Record>> promise;

        if (query.isEmpty()) {
            promise = serviceHandler.getRecords();
        } else {
            promise = serviceHandler.getRecords(query);
        }

        promise.future().onSuccess(records -> {
            if (query.containsKey(ServiceSearchParameter.format) && Boolean.parseBoolean(query.get(ServiceSearchParameter.format).get(0))) {
                JsonObject groupedByName = new JsonObject(records.stream().collect(Collectors.toMap(Record::getName, Function.identity())));
                Ok(groupedByName, new HashMap<>(), routingContext);
            } else {
                JsonArray jsonArray = new JsonArray();
                records.stream().map(JsonObject::mapFrom).forEach(jsonArray::add);
                Ok(jsonArray, new HashMap<>(), routingContext);
            }


        }).onFailure(cause -> NotFound(cause.getMessage(), routingContext));
    }

    private void handleServiceCreation(RoutingContext routingContext) {
        JsonObject recordJson = routingContext.getBodyAsJson();

        serviceHandler.createRecord(recordJson).future()
                .onSuccess(record -> {
                    LOGGER.info("correctly published");
                    LOGGER.info(JsonObject.mapFrom(record).encodePrettily());
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
                    headers.put(HttpHeaderNames.LAST_MODIFIED.toString(), record.getMetadata().getString("creationDate"));
                    headers.put(HttpHeaderNames.LOCATION.toString(),
                            routingContext.request().absoluteURI() + "/" + record.getRegistration());
                    Created(JsonObject.mapFrom(record), headers, routingContext);
                }).onFailure(cause -> BadRequest(cause.getMessage(), routingContext));


    }

    private void rerouteToService(RoutingContext routingContext, String root, String uri) {
        LOGGER.info("handling request " + routingContext.request().method().name() + " "
                + routingContext.request().absoluteURI());
        Buffer body = routingContext.getBody();
        HttpServerRequest httpServerRequest = routingContext.request();
        HttpServerResponse httpServerResponse = routingContext.response();
        HttpMethod method = httpServerRequest.method();
        if (body != null && !body.toString().isEmpty()) {
            proxyHandler.reroute(root, uri, method, 1000, body, httpServerRequest.headers())
                    .future()
                    .onSuccess(httpResponseFromService -> {
                        Buffer bodyFromService = httpResponseFromService.body();
                        httpResponseFromService.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
                        httpServerResponse.putHeader(HttpHeaderNames.FROM, "Proxy instance " + this).setStatusCode(httpResponseFromService.statusCode());
                        if (bodyFromService == null) {
                            httpServerResponse.end();
                        } else {
                            httpServerResponse.end(bodyFromService);
                        }
                    }).onFailure(cause -> ServerError(cause.getMessage(), routingContext));
        } else if (method == HttpMethod.GET) {
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaderNames.FROM.toString(), "Proxy instance " + this);
            cacheService.findCachedResponse(uri).future().onSuccess(cacheResponse -> {
                LOGGER.info("Retrieving " + uri + " from redis");
                Ok(JsonObject.mapFrom(cacheResponse), headers, routingContext);
            }).onFailure(cause -> proxyHandler.reroute(root, uri, method, 1000, httpServerRequest.headers())
                    .future().onSuccess(httpResponseFromService -> {
                        if (httpResponseFromService.statusCode() == HttpResponseStatus.OK.code()) {


                            Buffer responseBody = httpResponseFromService.body();
                            // send back result and cache in redis
                            int cacheAge = Math.min(Integer.parseInt(Optional.ofNullable(httpServerRequest.headers().get(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE)).orElse("0")),
                                    Config.CACHE_MAX_AGE);
                            // if responsobody is not a JSON string, it breaks

                            CachedResponse cachedResponse;
                            try {
                                cachedResponse = new CachedResponse(responseBody.toJson(), false);
                            } catch (Exception e) {
                                cachedResponse = new CachedResponse(responseBody.toString(), false);
                            }
                            JsonObject httpServerResponseJson = JsonObject.mapFrom(cachedResponse);
                            int bytes = httpServerResponseJson.encode().getBytes().length;
                            httpResponseFromService.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
                            httpServerResponse.putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(bytes));
                            httpServerResponse.setStatusCode(httpResponseFromService.statusCode())
                                    .putHeader(HttpHeaderNames.FROM, "Proxy instance " + this)
                                    .end(httpServerResponseJson.toBuffer());
                            if (cacheAge != 0) {
                                LOGGER.info(httpServerResponseJson.encodePrettily());
                                cachedResponse.setCached(true);
                                cachedResponse.setRedisOptions(new RedisOptions().setCacheExpiration(cacheAge).setKey(uri));
                                cacheService.saveCachedResponse(cachedResponse).future()
                                        .onSuccess(redis -> LOGGER.info("successfully cached get request " + uri))
                                        .onFailure(reason -> LOGGER.info("could not cache in redis " + reason.getMessage()));
                            }
                        } else {
                            JsonObject errorJson = new JsonObject().put("status", httpResponseFromService.statusCode())
                                    .put("error", Optional.ofNullable(httpResponseFromService.body()).orElse(Buffer.buffer("No error description from request " + uri)).toString());
                            httpServerResponse.putHeader(HttpHeaderNames.FROM, "Proxy instance " + this)
                                    .setStatusCode(httpResponseFromService.statusCode()).end(errorJson.encode());
                        }
                    }).onFailure(reason -> ServerError(reason.getMessage(), routingContext)));

        } else {
            proxyHandler.reroute(root, uri, method, 1000, httpServerRequest.headers()).future().onSuccess(httpRespFromService -> {
                Buffer bodyFromService = httpRespFromService.body();
                httpRespFromService.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
                httpServerResponse.putHeader(HttpHeaderNames.FROM, "Proxy instance " + this).setStatusCode(httpRespFromService.statusCode());
                if (bodyFromService == null) {
                    httpServerResponse.end();
                } else {
                    httpServerResponse.end(bodyFromService);
                }
            }).onFailure(cause -> ServerError(cause.getMessage(), routingContext));
        }

    }


}
