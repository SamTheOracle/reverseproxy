package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.search.ServiceSearchParameter;
import com.samtheoracle.proxy.utils.MockService1;
import com.samtheoracle.proxy.utils.TestUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(VertxExtension.class)
class ServiceCRDTest {

    @BeforeAll
    static void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new RedisAccessVerticle(), redisAsync -> vertx.deployVerticle(new ProxyServer(), proxyAsync -> WebClient.create(vertx)
                .delete(8080, "localhost", "/services/all").send(responseAsync -> {
                    if (responseAsync.succeeded()) {
                        vertx.deployVerticle(new MockService1(), testContext.completing());
                    } else {
                        testContext.failNow(responseAsync.cause());
                    }
                })));
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

    @BeforeEach
    void publishRecord(Vertx vertx, VertxTestContext testContext) {
        TestUtils.publishRecord(vertx, TestUtils.record(1234, "randomservice", "/random"))
                .future()
                .onSuccess(response -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @Test
    void deleteServiceByRegistration(Vertx vertx, VertxTestContext testContext) {
        Promise<HttpResponse<Buffer>> searchPromise = Promise.promise();
        WebClient.create(vertx)
                .get(8080, "localhost", "/services")
                .addQueryParam(ServiceSearchParameter.name.name(), "randomservice")
                .expect(ResponsePredicate.SC_OK)
                .send(searchPromise);
        searchPromise.future()
                .map(response -> {
                    testContext.verify(() -> Assertions.assertDoesNotThrow(() -> response.bodyAsJsonArray().getJsonObject(0)));
                    return response.bodyAsJsonArray().getJsonObject(0);
                }).compose(recordJson -> {
            Promise<HttpResponse<Buffer>> deletePromise = Promise.promise();
            WebClient.create(vertx)
                    .delete(8080, "localhost", "/services/" + recordJson.getString("registration"))
                    .expect(ResponsePredicate.SC_NO_CONTENT)
                    .send(deletePromise);
            return deletePromise.future();
        }).compose(response -> {
            Promise<HttpResponse<Buffer>> searchPromiseNotFound = Promise.promise();
            WebClient.create(vertx)
                    .get(8080, "localhost", "/services")
                    .addQueryParam(ServiceSearchParameter.name.name(), "randomservice")
                    .expect(ResponsePredicate.SC_NOT_FOUND)
                    .send(searchPromiseNotFound);
            return searchPromiseNotFound.future();
        }).onSuccess(event -> testContext.completeNow())
                .onFailure(testContext::failNow);

    }

    @Test
    void deleteAllServices(Vertx vertx, VertxTestContext testContext) {
        Promise<HttpResponse<Buffer>> deletePromise = Promise.promise();
        WebClient.create(vertx)
                .delete(8080, "localhost", "/services/all")
                .expect(ResponsePredicate.SC_NO_CONTENT)
                .send(deletePromise);
        deletePromise.future().compose(response -> {
            Promise<HttpResponse<Buffer>> searchPromiseNotFound = Promise.promise();
            WebClient.create(vertx)
                    .get(8080, "localhost", "/services")
                    .addQueryParam(ServiceSearchParameter.name.name(), "randomservice")
                    .expect(ResponsePredicate.SC_NOT_FOUND)
                    .send(searchPromiseNotFound);
            return searchPromiseNotFound.future();
        }).onSuccess(event -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }
}