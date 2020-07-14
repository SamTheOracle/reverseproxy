package com.samtheoracle.proxy.server;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.types.HttpEndpoint;

public class MockVerticle extends RestEndpoint {
  private final String testName;

  public MockVerticle(String testName) {
    this.testName = testName;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {


    final Router router = Router.router(vertx);
    router.get("/ping").handler(routingContext -> routingContext.response().end());
    router.get("/test/welcome").handler(routingContext->routingContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN).end("Welcome"));
    router.get("/test/infos").handler(routingContext -> {
      JsonArray j = new JsonArray();
      router.getRoutes().stream().map(JsonObject::mapFrom).forEach(j::add);
      routingContext.response().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON).end(new JsonObject()
              .put("message", "Users API Microservice")
              .put("routes", j)
              .encodePrettily());
    });
    createServer(0, router)
      .future()
      .compose(server -> publishToProxy(HttpEndpoint.createRecord(testName, "localhost", server.actualPort(), "/test")).future())
      .onFailure(startPromise::fail)
      .onSuccess(startPromise::complete);
  }

  protected Promise<Void> publishToProxy(Record record) {
    Promise<Void> recordPromise = Promise.promise();
    WebClient.create(vertx)
      .post(8080,
        "localhost",
        "/services")
      .sendBuffer(JsonObject.mapFrom(record).toBuffer(), handler -> {
        if (handler.succeeded()) {
          recordPromise.complete();
        } else {
          recordPromise.fail(handler.cause());
        }
      });
    return recordPromise;
  }
}
