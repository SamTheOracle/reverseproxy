package com.samtheoracle.proxy.server;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class ProxyServerTest {

    @BeforeAll
    static void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new ProxyServer(), proxyAsync -> vertx.deployVerticle(new MockService(), testContext.completing()));

    }

    @Test
    void simpleGet(Vertx vertx, VertxTestContext testContext) {
        WebClient.create(vertx).get(8080, "localhost", "/api/v1/" + MockService.PATH + "/welcome")
                .expect(ResponsePredicate.SC_OK)
                .send(event -> {
                    if (event.succeeded()) {
                        System.out.println(event.result().bodyAsString());
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                });
    }

    @Test
    void simplePut(Vertx vertx, VertxTestContext testContext) {
        WebClient.create(vertx).put(8080, "localhost", "/api/v1/" + MockService.PATH + "/putMethod")
                .expect(ResponsePredicate.SC_OK)
                .sendBuffer(new JsonObject().put("test", 12).toBuffer(), event -> {
                    if (event.succeeded()) {
                        System.out.println(event.result().bodyAsString());
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                });
    }

    @Test
    void simplePost(Vertx vertx, VertxTestContext testContext) {
        WebClient.create(vertx).post(8080, "localhost", "/api/v1/" + MockService.PATH + "/postMethod")
                .expect(ResponsePredicate.SC_CREATED)
                .sendBuffer(new JsonObject().put("test", 12).toBuffer(), event -> {
                    if (event.succeeded()) {
                        System.out.println(event.result().bodyAsString());
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                });
    }

    @Test
    void simpleDelete(Vertx vertx, VertxTestContext testContext) {
        WebClient.create(vertx).delete(8080, "localhost", "/api/v1/" + MockService.PATH + "/deleteMethod")
                .expect(ResponsePredicate.SC_NO_CONTENT)
                .send(event -> {
                    if (event.succeeded()) {
                        System.out.println(event.result().bodyAsString());
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                });
    }
}