package com.samtheoracle.proxy.utils;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class MockService2 extends AbstractVerticle {

    public static final String PATH = "test2";

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        final Router router = Router.router(vertx);
        router.route("/*").handler(BodyHandler.create());
        router.get("/" + PATH + "/ping").handler(this::handlePing);
        router.get("/" + PATH + "/welcome").handler(this::handleWelcome);
        router.post("/" + PATH + "/postMethod").handler(this::handlePost);
        router.put("/" + PATH + "/putMethod").handler(this::handlePut);
        router.delete("/" + PATH + "/deleteMethod").handler(this::handleDelete);
        vertx.createHttpServer().requestHandler(router).listen(9001, event -> {
            if (event.succeeded()) {
                if (event.succeeded()) {
                    TestUtils.publishRecord(vertx, TestUtils.record(event.result().actualPort(), "testmicroservice2", PATH))
                            .onSuccess(publishResponse -> startPromise.complete())
                            .onFailure(startPromise::fail);
                }
            }
        });

    }

    private void handleDelete(RoutingContext routingContext) {
        routingContext.response().setStatusCode(HttpResponseStatus.NO_CONTENT.code()).end();
    }

    private void handlePut(RoutingContext routingContext) {
        Buffer body = routingContext.getBody();
        if (body == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();

        }
    }

    private void handlePost(RoutingContext routingContext) {
        Buffer body = routingContext.getBody();
        if (body == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            routingContext.response().setStatusCode(HttpResponseStatus.CREATED.code()).end();

        }
    }

    private void handleWelcome(RoutingContext routingContext) {
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end("Welcome");

    }

    private void handlePing(RoutingContext routingContext) {
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
    }
}
