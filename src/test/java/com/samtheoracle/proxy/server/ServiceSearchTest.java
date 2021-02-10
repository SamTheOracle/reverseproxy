package com.samtheoracle.proxy.server;

import com.samtheoracle.proxy.search.ServiceSearchParameter;
import com.samtheoracle.proxy.utils.MockService1;
import com.samtheoracle.proxy.utils.MockService2;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(VertxExtension.class)
class ServiceSearchTest {

    @BeforeAll
    static void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new ProxyServer(), proxyAsync -> WebClient.create(vertx)
                .delete(8080, "localhost", "/services/all").send(responseAsync -> {
                    if (responseAsync.succeeded()) {
                        vertx.deployVerticle(new MockService1(), mockAsync -> vertx.deployVerticle(new MockService2(), testContext.succeedingThenComplete()));
                    } else {
                        testContext.failNow(responseAsync.cause());
                    }
                }));
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

    @Test
    void getServicesWithQueryName(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_OK)
                .addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice")
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.verify(() -> Assertions.assertEquals("testmicroservice", event.result().bodyAsJsonArray().getJsonObject(0).getString("name")))
                                .completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }

    @Test
    void getAllService(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_OK)
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.verify(() -> Assertions.assertEquals(2, event.result().bodyAsJsonArray().size())).completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });

    }

    @Test
    void getServicesWithQueryRootAndName(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_OK)
                .addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice")
                .addQueryParam(ServiceSearchParameter.root.name(), "/test")
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.verify(() -> Assertions.assertEquals("testmicroservice", event.result().bodyAsJsonArray().getJsonObject(0).getString("name")))
                                .completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }

    @Test
    void getServicesWithQueryRoot(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_OK)
                .addQueryParam(ServiceSearchParameter.root.name(), "/test")
                .send(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        testContext.verify(() -> Assertions.assertDoesNotThrow(() -> asyncResult.result().bodyAsJsonArray()))
                                .verify(() -> Assertions.assertEquals(1, asyncResult.result().bodyAsJsonArray().size()))
                                .verify(() -> Assertions.assertTrue(asyncResult.result().bodyAsJsonArray().stream().map(o -> (JsonObject) o).anyMatch(recordJson -> "testmicroservice".equals(recordJson.getString("name")))))
                                .completeNow();
                    } else {
                        testContext.failNow(asyncResult.cause());
                    }
                    client.close();
                });
    }

    @Test
    void getServicesWithQueryCreationDateFailsLT(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_NOT_FOUND)
                .addQueryParam(ServiceSearchParameter.creationDate.name(), "lt" + LocalDateTime.now().minusYears(2))
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
    void getServicesWithQueryCreationDateLT(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_OK)
                .addQueryParam(ServiceSearchParameter.creationDate.name(), "lt" + LocalDateTime.now().plusHours(2))
                .send(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        testContext.verify(() -> Assertions.assertDoesNotThrow(() -> asyncResult.result().bodyAsJsonArray()))
                                .verify(() -> Assertions.assertEquals(2, asyncResult.result().bodyAsJsonArray().size()))
                                .verify(() -> Assertions.assertTrue(asyncResult.result().bodyAsJsonArray().stream().map(o -> (JsonObject) o).anyMatch(recordJson -> "testmicroservice".equals(recordJson.getString("name")))))
                                .completeNow();
                    } else {
                        testContext.failNow(asyncResult.cause());
                    }
                    client.close();
                });
    }

    @Test
    void getServicesWithQueryCreationDateFailsGT(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_NOT_FOUND)
                .addQueryParam(ServiceSearchParameter.creationDate.name(), "gt" + LocalDateTime.now().plusYears(2))
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
    void getServicesWithQueryCreationDateGT(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .expect(ResponsePredicate.SC_OK)
                .addQueryParam(ServiceSearchParameter.creationDate.name(), "gt" + LocalDateTime.now().minusYears(2))
                .send(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        testContext.verify(() -> Assertions.assertDoesNotThrow(() -> asyncResult.result().bodyAsJsonArray()))
                                .verify(() -> Assertions.assertEquals(2, asyncResult.result().bodyAsJsonArray().size()))
                                .verify(() -> Assertions.assertTrue(asyncResult.result().bodyAsJsonArray().stream().map(o -> (JsonObject) o).anyMatch(recordJson -> "testmicroservice".equals(recordJson.getString("name")))))
                                .completeNow();
                    } else {
                        testContext.failNow(asyncResult.cause());
                    }
                    client.close();
                });
    }

    @Test
    void getServicesWithQueryCreationDateGTE(Vertx vertx, VertxTestContext testContext) {
        Promise<HttpResponse<Buffer>> promise = Promise.promise();
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services").addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice")
                .expect(ResponsePredicate.SC_OK)
                .send(promise);
        promise.future().onSuccess(event -> {

            String date = event.bodyAsJsonArray().getJsonObject(0).getJsonObject("metadata").getString("creationDate");
            client.get(8080, "localhost", "/services")
                    .expect(ResponsePredicate.SC_OK)
                    .addQueryParam(ServiceSearchParameter.creationDate.name(), "gte" + date)
                    .send(asyncResult -> {
                        if (asyncResult.succeeded()) {
                            testContext.verify(() -> Assertions.assertDoesNotThrow(() -> asyncResult.result().bodyAsJsonArray()))
                                    .verify(() -> Assertions.assertTrue(asyncResult.result().bodyAsJsonArray().stream().map(o -> (JsonObject) o).anyMatch(recordJson -> "testmicroservice".equals(recordJson.getString("name")))))
                                    .completeNow();
                        } else {
                            testContext.failNow(asyncResult.cause());
                        }
                        client.close();
                    });
        }).onFailure(testContext::failNow);

    }

    @Test
    void getServicesWithMultipleQuery(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .addQueryParam(ServiceSearchParameter.creationDate.name(), "lt" + LocalDateTime.now().plusYears(2))
                .addQueryParam(ServiceSearchParameter.creationDate.name(), "gt" + LocalDateTime.now().minusYears(2))
                .addQueryParam(ServiceSearchParameter.root.name(), "/test")
                .addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice")
                .expect(ResponsePredicate.SC_OK)
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.verify(() -> Assertions.assertEquals("testmicroservice", event.result().bodyAsJsonArray().getJsonObject(0).getString("name")))
                                .completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }

    @Test
    void getServicesWithQueryCreationDateLTE(Vertx vertx, VertxTestContext testContext) {
        Promise<HttpResponse<Buffer>> promise = Promise.promise();
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services").addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice")
                .expect(ResponsePredicate.SC_OK)
                .send(promise);
        promise.future().onSuccess(event -> {

            String date = event.bodyAsJsonArray().getJsonObject(0).getJsonObject("metadata").getString("creationDate");
            client.get(8080, "localhost", "/services")
                    .expect(ResponsePredicate.SC_OK)
                    .addQueryParam(ServiceSearchParameter.creationDate.name(), "lte" + date)
                    .send(asyncResult -> {
                        if (asyncResult.succeeded()) {
                            testContext.verify(() -> Assertions.assertDoesNotThrow(() -> asyncResult.result().bodyAsJsonArray()))
                                    .verify(() -> Assertions.assertTrue(asyncResult.result().bodyAsJsonArray().stream().map(o -> (JsonObject) o).anyMatch(recordJson -> "testmicroservice".equals(recordJson.getString("name")))))
                                    .completeNow();
                        } else {
                            testContext.failNow(asyncResult.cause());
                        }
                        client.close();
                    });
        }).onFailure(testContext::failNow);

    }

    @Test
    void getServicesWithMultipleOrQuery(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/services")
                .addQueryParam(ServiceSearchParameter.status.name(), "down")
                .addQueryParam(ServiceSearchParameter.status.name(), "up")
                .addQueryParam(ServiceSearchParameter.root.name(), "/test")
                .addQueryParam(ServiceSearchParameter.root.name(), "/test2")
                .addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice")
                .addQueryParam(ServiceSearchParameter.name.name(), "testmicroservice2")
                .expect(ResponsePredicate.SC_OK)
                .send(event -> {
                    if (event.succeeded()) {
                        testContext.verify(() -> Assertions.assertEquals(2, event.result().bodyAsJsonArray().size())).completeNow();
                    } else {
                        testContext.failNow(event.cause());
                    }
                    client.close();
                });
    }
}