package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.utils.MockService1;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(VertxExtension.class)
class ProxyRerouteTest {

    @BeforeAll
    static void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new RedisAccessVerticle(), redisAsync -> vertx.deployVerticle(new ProxyServer(), proxyAsync -> WebClient.create(vertx)
                .delete(8080, "localhost", "/services/all").send(responseAsync -> {
                    if (responseAsync.succeeded()) {
                        vertx.deployVerticle(new MockService1(), testContext.succeedingThenComplete());
                    } else {
                        testContext.failNow(responseAsync.cause());
                    }
                })));

    }

    @Test
    void rerouteGet(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/api/v1/" + MockService1.PATH + "/welcome")
                .expect(ResponsePredicate.SC_OK)
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });

    }

    @Test
    void rerouteGetWithCache(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/api/v1/" + MockService1.PATH + "/welcome")
                .expect(ResponsePredicate.SC_OK)
                .putHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "30")
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.verify(() -> Assertions.assertDoesNotThrow(() -> event.result().bodyAsJsonObject()))
                                .verify(() -> Assertions.assertDoesNotThrow(() -> Json.decodeValue(event.result().body(), CachedResponse.class)))
                                .completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });

    }

    @Test
    void reroutePut(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.put(8080, "localhost", "/api/v1/" + MockService1.PATH + "/putMethod")
                .expect(ResponsePredicate.SC_OK)
                .sendBuffer(new JsonObject().put("test", 12).toBuffer(), event -> {
                    if (event.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }

    @Test
    void reroutePost(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.post(8080, "localhost", "/api/v1/" + MockService1.PATH + "/postMethod")
                .expect(ResponsePredicate.SC_CREATED)
                .sendBuffer(new JsonObject().put("test", 12).toBuffer(), event -> {
                    if (event.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }

    @Test
    void rerouteDelete(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.delete(8080, "localhost", "/api/v1/" + MockService1.PATH + "/deleteMethod")
                .expect(ResponsePredicate.SC_NO_CONTENT)
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }

    @AfterAll
    static void tearDown(Vertx vertx, VertxTestContext testContext) {
        List<Promise<Void>> undeployPromises = new ArrayList<>();
        vertx.deploymentIDs().forEach(id -> {

            Promise<Void> undeployPromise = Promise.promise();
            vertx.undeploy(id, undeployPromise);
            undeployPromises.add(undeployPromise);
        });
        CompositeFuture.all(undeployPromises.stream().map(Promise::future).collect(Collectors.toList()))
                .onComplete(event -> testContext.completeNow());
    }
}
