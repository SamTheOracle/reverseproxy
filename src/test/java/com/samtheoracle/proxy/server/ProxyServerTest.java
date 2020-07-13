package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisAccessVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.oracolo.database.utils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class ProxyServerTest {
  private static final String TEST_MICROSERVICE_MOCK = "mock_microservice";

  @BeforeAll
  static void deploy(Vertx vertx, VertxTestContext testContext) {
    Verticle verticle = new ProxyServer();
    Promise<String> deploymentPromise = Promise.promise();

    vertx.deployVerticle(verticle, deploymentPromise);

    deploymentPromise.future()
      .compose(id -> {
        Promise<HttpResponse<Buffer>> resultPromise = Promise.promise();
        delete(vertx, 8080, "/services", ResponsePredicate.SC_NO_CONTENT)
          .send(resultPromise);
        return resultPromise.future();
      })
//      .compose(deploy -> {
//        Promise<String> promise = Promise.promise();
//        vertx.deployVerticle(new MongoAccessVerticle(), promise);
//        return promise.future();
//      })
      .compose(o -> {
        Promise<String> p = Promise.promise();
        vertx.deployVerticle(new RedisAccessVerticle(), p);
        return p.future();
      }).compose(id -> {
      Promise<String> promise = Promise.promise();
      vertx.deployVerticle(new MockVerticle(TEST_MICROSERVICE_MOCK), promise);
      return promise.future();
    }).onSuccess(o -> testContext.completeNow())
      .onFailure(testContext::failNow);
  }


  @Test
  void postService(Vertx vertx, VertxTestContext testContext) {
    post(vertx, 8080, "/services", ResponsePredicate.SC_CREATED)
      .sendBuffer(JsonObject.mapFrom(HttpEndpoint.createRecord("test_microservice", "localhost", 123, "/test")).toBuffer(), handler -> {
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

    get(vertx, 8080, "/health", null)
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
}
