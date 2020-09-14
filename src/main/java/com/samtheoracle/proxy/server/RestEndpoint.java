package com.samtheoracle.proxy.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;

import java.util.Map;

public abstract class RestEndpoint extends AbstractVerticle {


    protected static void BadRequest(String message, RoutingContext routingContext) {
        end(message, HttpResponseStatus.BAD_REQUEST.code(), routingContext);
    }

    protected static void NotFound(String message, RoutingContext routingContext) {
        end(message, HttpResponseStatus.NOT_FOUND.code(), routingContext);
    }

    protected static void Created(JsonObject endObject, Map<String, String> headers, RoutingContext routingContext) {
        end(endObject, headers, HttpResponseStatus.CREATED.code(), routingContext);
    }

    protected static void Ok(JsonArray endObject, Map<String, String> headers, RoutingContext routingContext) {
        end(endObject, headers, HttpResponseStatus.OK.code(), routingContext);
    }

    protected static void Ok(JsonObject endObject, Map<String, String> headers, RoutingContext routingContext) {
        end(endObject, headers, HttpResponseStatus.OK.code(), routingContext);
    }

    protected static void ServerError(JsonObject endObject, Map<String, String> headers, int errorCode, RoutingContext routingContext) {
        end(endObject, headers, errorCode, routingContext);
    }

    protected static void ServerError(String message, RoutingContext routingContext) {
        end(message, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), routingContext);
    }

    protected static void NoContent(Map<String, String> headers, RoutingContext routingContext) {
        end(headers, HttpResponseStatus.NO_CONTENT.code(), routingContext);
    }

    protected static void end(JsonObject endObject, Map<String, String> headers, int code, RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        headers.forEach(response::putHeader);
        response.setStatusCode(code).end(endObject == null ? "" : endObject.encodePrettily());
    }

    protected static void end(String message, int code, RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        response.setStatusCode(code).end(message);
    }

    protected static void end(JsonArray jsonArray, Map<String, String> map, int code, RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        map.forEach(response::putHeader);
        response.setStatusCode(code).end(jsonArray.encode());
    }

    protected static void end(Map<String, String> map, int code, RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        map.forEach(response::putHeader);
        response.setStatusCode(code).end();

    }

    protected static <T> T decode(JsonObject jsonObject, Class<T> clazz) {
        T object;
        try {
            object = Json.decodeValue(jsonObject.encode(), clazz);
            return object;
        } catch (DecodeException e) {
            return null;
        }
    }

    protected Promise<HttpServer> createServer(int port, Router router, HttpServerOptions httpServerOptions) {
        Promise<HttpServer> httpServerPromise = Promise.promise();
        vertx.createHttpServer(httpServerOptions).requestHandler(router)
                .listen(port, httpServerAsyncResult -> {
                    if (httpServerAsyncResult.succeeded()) {
                        httpServerPromise.complete(httpServerAsyncResult.result());
                    } else {
                        httpServerPromise.fail(httpServerAsyncResult.cause());
                    }
                });
        return httpServerPromise;
    }

    protected ServiceDiscovery createDiscovery(String redisHost, String redisPort, String redisEndpointsKey) {
        return ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
                .setBackendConfiguration(new JsonObject().put("host", redisHost)
                        .put("port", redisPort)
                        .put("key", redisEndpointsKey)));
    }


}
