package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisAccessVerticle;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.function.Function;

import static com.oracolo.database.utils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class ProxyServerTest {
    private static final String TEST_MICROSERVICE_MOCK = "mock_microservice";
    private final Function<Buffer, CachedResponse> decodeToCacheResponse = buffer -> Json.decodeValue(buffer, CachedResponse.class);

    @BeforeAll
    static void deploy(Vertx vertx, VertxTestContext testContext) {
        Verticle verticle = new RedisAccessVerticle();
        Promise<String> proxyServerPromise = Promise.promise();
        Promise<String> healthChecksPromise = Promise.promise();
        Promise<String> redisAccessPromise = Promise.promise();


        vertx.deployVerticle(verticle, redisAccessPromise);

        redisAccessPromise.future()
                .compose(deploy -> {
                    vertx.deployVerticle(new ProxyServer(), proxyServerPromise);
                    return proxyServerPromise.future();
                })
                .compose(o -> {
                    vertx.deployVerticle(new HealthChecksServer(), healthChecksPromise);
                    return healthChecksPromise.future();
                })
                .compose(id -> {
                    Promise<HttpResponse<Buffer>> resultPromise = Promise.promise();
                    delete(vertx, 8080, "/services", ResponsePredicate.SC_NO_CONTENT)
                            .send(resultPromise);
                    return resultPromise.future();
                })
                .compose(id -> {
                    Promise<String> promise = Promise.promise();
                    vertx.deployVerticle(new MockVerticle(TEST_MICROSERVICE_MOCK), promise);
                    return promise.future();
                }).onSuccess(o -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }


    @Test
    void postService(Vertx vertx, VertxTestContext testContext) {
        post(vertx, 8080, "/services", ResponsePredicate.SC_CREATED)
                .sendBuffer(JsonObject.mapFrom(HttpEndpoint.createRecord("random", "localhost", 123, "/path")).toBuffer(), handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }

    @Test
    void getServices(Vertx vertx, VertxTestContext testContext) {

        get(vertx, 8080, "/services", ResponsePredicate.SC_OK)
                .send(ar -> {
                    if (ar.succeeded()) {
                        JsonArray services = ar.result().bodyAsJsonArray();
                        testContext.verify(() -> assertTrue(services.size() > 1))
                                .completeNow();
                    } else {
                        testContext.failNow(ar.cause());
                    }
                });
    }

    @Test
    void testHealthProcedureIsUp(Vertx vertx, VertxTestContext testContext) {

        get(vertx, 9000, "/health", null)
                .send(ar -> {
                    if (ar.succeeded()) {
                        JsonArray checks = ar.result().bodyAsJsonObject().getJsonArray("checks");
                        testContext.verify(() -> assertTrue(checks.stream().map(o -> (JsonObject) o).anyMatch(j -> j.getString("status").equals(Status.UP.name()))))
                                .completeNow();
                    } else {
                        testContext.failNow(ar.cause());
                    }
                });
    }

    @Test
    void deleteAllServices(Vertx vertx, VertxTestContext testContext) {
        Promise<HttpResponse<Buffer>> resultPromise = Promise.promise();

        delete(vertx, 8080, "/services", ResponsePredicate.SC_NO_CONTENT).send(resultPromise);
        resultPromise.future()
                .compose(response -> {
                    Promise<HttpResponse<Buffer>> getServicesPromise = Promise.promise();
                    get(vertx, 8080, "/services", ResponsePredicate.SC_OK)
                            .send(getServicesPromise);
                    return getServicesPromise.future();
                }).onSuccess(response -> {
            testContext.completeNow();
        }).onFailure(testContext::failNow);
    }

    @Test
    void proxyRequestTest(Vertx vertx, VertxTestContext vertxTestContext) {
        Checkpoint checkpoint = vertxTestContext.checkpoint(3);
        Promise<HttpResponse<Buffer>> httpResponsePromise = Promise.promise();
        get(vertx, 8080, "/api/v1/test/infos", null)
                .putHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "5")
                .send(httpResponsePromise);
        httpResponsePromise.future()
                .onSuccess(r -> checkpoint.flag())
                .onFailure(vertxTestContext::failNow);

        vertx.setTimer(3 * 1000, handler -> {
            Promise<HttpResponse<Buffer>> httpResponsePromiseIsCached = Promise.promise();
            get(vertx, 8080, "/api/v1/test/infos", null)
                    .putHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "5")
                    .send(httpResponsePromiseIsCached);
            httpResponsePromiseIsCached.future()
                    .onSuccess(r -> vertxTestContext.verify(() -> {
                        Buffer b = r.bodyAsBuffer();
                        Assertions.assertDoesNotThrow(() -> decodeToCacheResponse.apply(b));
                        CachedResponse cachedResponse = decodeToCacheResponse.apply(b);
                        Assertions.assertTrue(cachedResponse.isCached());
                        checkpoint.flag();
                    })).onFailure(vertxTestContext::failNow);
        });

        vertx.setTimer(6 * 1000, handler -> {
            Promise<HttpResponse<Buffer>> httpResponsePromiseIsNotCached = Promise.promise();
            get(vertx, 8080, "/api/v1/test/infos", null)
                    .putHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "5")
                    .send(httpResponsePromiseIsNotCached);
            httpResponsePromiseIsNotCached.future()
                    .onSuccess(r -> vertxTestContext.verify(() -> {
                        Buffer b = r.bodyAsBuffer();
                        Assertions.assertDoesNotThrow(() -> decodeToCacheResponse.apply(b));
                        CachedResponse cachedResponse = decodeToCacheResponse.apply(b);
                        Assertions.assertFalse(cachedResponse.isCached());
                        checkpoint.flag();
                    })).onFailure(vertxTestContext::failNow);
        });
    }
}
