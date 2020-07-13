package com.samtheoracle.proxy.server;

import com.oracolo.database.builder.DatabaseServiceBuilder;
import com.oracolo.database.builder.redis.RedisOptions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProxyServer extends RestEndpoint {

  private final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
  private final BiConsumer<WebClient, Promise<Status>> procedureFn = (webClient, promise) -> webClient.get("/ping").timeout(2000).send(responseHandler -> {
    if (responseHandler.succeeded()) {
      promise.tryComplete(Status.OK());
    } else {
      //if timeout is reached promise is already completed, so use try
      promise.tryComplete(Status.KO(new JsonObject().put("failedTime", LocalDateTime.now().toString())));
    }

  });
  private ServiceDiscovery discovery;

  private static Promise<Record> publishHttpEndPoint(Record record, ServiceDiscovery discovery) {
    Promise<Record> recordPromise = Promise.promise();

    discovery.publish(record, recordPromise);
    return recordPromise;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    super.start();
    final HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
    final Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.post("/services").handler(this::handleServiceCreation);
    router.get("/services").handler(this::handleGetServices);
    router.delete("/services").handler(routingContext -> handleDeleteAllServices(routingContext, healthCheckHandler));

    router.route("/api/v1/*").handler(routingContext -> {
      String uri = routingContext.request().uri().split("/api/v1")[1];
      String root = routingContext.normalisedPath().split("/")[3];
      rerouteToService(routingContext, "/" + root, uri);
    });


    router.get("/health").handler(healthCheckHandler);

    vertx.eventBus().<JsonObject>consumer("vertx.discovery.announce").handler(event -> handleIncomingService(event, healthCheckHandler));

    createApiServer(Optional.ofNullable(Integer.getInteger("http.port")).orElse(8080), router)
      .future()
      .onSuccess(httpServer -> {
        System.out.println("Server started on port " + httpServer.actualPort());
        String redisHost = Optional.ofNullable(System.getenv("REDIS_DB_HOST"))
          .orElse("localhost");
        String redisPort = Optional.ofNullable(System.getenv("REDIS_DB_PORT"))
          .orElse("6379");
        this.discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
          .setBackendConfiguration(new JsonObject()
            .put("host", redisHost)
            .put("port", redisPort)
            .put("key", "http_endpoints")));

        startHealthCheck(healthCheckHandler);
        startPromise.complete();
      }).onFailure(startPromise::fail);
  }

  private void handleDeleteAllServices(RoutingContext routingContext, HealthCheckHandler healthCheckHandler) {
    Promise<List<Record>> recordsPromise = Promise.promise();
    List<Promise<Void>> unpublishPromises = new ArrayList<>();

    discovery.getRecords(record -> true, recordsPromise);
    recordsPromise.future()
      .onSuccess(records -> records.forEach(record -> {
        Promise<Void> promise = Promise.promise();
        discovery.unpublish(record.getRegistration(), promise);
        healthCheckHandler.unregister(record.getRegistration());
        unpublishPromises.add(promise);
      }));
    CompositeFuture.all(unpublishPromises.stream().map(Promise::future).collect(Collectors.toList()))
      .onSuccess(event -> NoContent(new HashMap<>(), routingContext))
      .onFailure(cause -> ServerError(cause.getMessage(), routingContext));
  }

  private void handleIncomingService(Message<JsonObject> objectMessage, HealthCheckHandler healthCheckHandler) {
    JsonObject json = objectMessage.body();
    if (json.getString("status").equals(io.vertx.servicediscovery.Status.UP.name())) {
      discovery.getRecord(record -> record.getMetadata().encode().equals(json.getJsonObject("metadata").encode()), ar -> {
        if (ar.succeeded()) {
          System.out.println(ar.result().toJson().encodePrettily());
          WebClient webClient = discovery.getReference(ar.result()).getAs(WebClient.class);
          healthCheckHandler.register(ar.result().getRegistration(),
            Optional.ofNullable(Integer.getInteger("service_heartbeat")).orElse(6000),
            promise -> procedureFn.accept(webClient, promise));
        }
      });
    }
  }

  private void startHealthCheck(HealthCheckHandler healthCheckHandler) {

    discovery.getRecords(record -> true, handler -> {
      if (handler.succeeded()) {
        List<Record> records = handler.result();
        records.forEach(record -> healthCheckHandler.register(record.getRegistration(), Optional.ofNullable(Integer.getInteger("service_heartbeat")).orElse(6000), promise -> {
          WebClient webClient = discovery.getReference(record).getAs(WebClient.class);
          procedureFn.accept(webClient, promise);
        }));
      }
    });
    //Start periodic check to services
    vertx.setPeriodic(Optional.ofNullable(Integer.getInteger("HEALTHCHECK_INTERVAL")).orElse(10) * 1000,
      handler -> WebClient.create(vertx)
        .get(Optional.ofNullable(Integer.getInteger("http.port")).orElse(8080), "localhost", "/health")
        .send(ar -> {
          if (ar.succeeded() && ar.result().body() != null) {
            JsonObject statusList = ar.result().bodyAsJsonObject();
            JsonArray checks = statusList.getJsonArray("checks");
            List<JsonObject> recordsToEliminate = checks.stream()
              .map(o -> (JsonObject) o)
              .filter(j -> j.getString("status").equals(io.vertx.servicediscovery.Status.DOWN.name()))
              .collect(Collectors.toList());
            if (!recordsToEliminate.isEmpty()) {
              List<Promise<Void>> unPublishPromises = new ArrayList<>();

              recordsToEliminate.forEach(r -> {

                healthCheckHandler.unregister(r.getString("id"));
                Promise<Void> unPublishPromise = Promise.promise();
                discovery.unpublish(r.getString("id"), unPublishPromise);
                LOGGER.info("Service has been removed: " + r.getString("id"));
                unPublishPromises.add(unPublishPromise);

              });
              CompositeFuture compositeFuture = CompositeFuture.all(unPublishPromises.stream().map(Promise::future).collect(Collectors.toList()));
              compositeFuture.onFailure(Throwable::printStackTrace);
            }
          }

        }));
  }

  private void handleGetServices(RoutingContext routingContext) {
    Promise<List<Record>> promise = Promise.promise();
    discovery.getRecords(record -> true, ar -> {
      if (ar.succeeded() && !ar.result().isEmpty()) {
        promise.complete(ar.result());
      } else if (ar.succeeded()) {
        promise.fail(new IllegalArgumentException("No service is present on the system"));
      } else {
        promise.fail(ar.cause());
      }
    });
    promise.future()
      .onSuccess(records -> {
        JsonArray jsonArray = new JsonArray();
        records.stream().map(JsonObject::mapFrom).forEach(jsonArray::add);
        Ok(jsonArray, new HashMap<>(), routingContext);
      }).onFailure(cause -> NotFound(cause.getMessage(), routingContext));
  }

  private void handleServiceCreation(RoutingContext routingContext) {
    JsonObject service = routingContext.getBodyAsJson();
    Record record = HttpEndpoint.createRecord(service.getString("name"),
      service.getJsonObject("location").getString("host"),
      service.getJsonObject("location").getInteger("port"),
      service.getJsonObject("location").getString("root"));
    record.setMetadata(new JsonObject()
      .put("creationDate", LocalDateTime.now().toString()));
    publishHttpEndPoint(record, discovery)
      .future()
      .onSuccess(r -> {
        System.out.println("correctly published");
        System.out.println(r.toJson().encodePrettily());
        HashMap<String, String> headers = new HashMap<>();
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
        headers.put(HttpHeaderNames.LAST_MODIFIED.toString(), r.getMetadata().getString("creationDate"));
        headers.put(HttpHeaderNames.LOCATION.toString(), routingContext.request().absoluteURI() + "/" + r.getRegistration());
        Created(JsonObject.mapFrom(r), headers, routingContext);
      }).onFailure(cause -> {
      BadRequest(cause.getMessage(), routingContext);
      cause.printStackTrace();
    });
  }

  private void rerouteToService(RoutingContext routingContext, String root, String uri) {

    HttpEndpoint.getWebClient(discovery, record -> record.getLocation().getString("root").equals(root), webClientAsyncResult -> {
      if (webClientAsyncResult.succeeded()) {
        WebClient webClient = webClientAsyncResult.result();
        Buffer body = routingContext.getBody();
        HttpServerRequest httpServerRequest = routingContext.request();
        HttpServerResponse httpServerResponse = routingContext.response();
        HttpMethod method = httpServerRequest.method();

        HttpRequest<Buffer> request = webClient.request(method, uri);

        request.putHeaders(httpServerRequest.headers());

        //put, post with body
        if (body != null && !body.toString().isEmpty()) {
          //send json object does not work...
          request.sendBuffer(body, httpResponseAsyncResult -> handleHttpResponse(httpServerResponse, webClient, httpResponseAsyncResult));
        } else if (method == HttpMethod.GET) {
//          request.timeout(2000).send(httpResponseAsyncResult -> handleHttpResponse(httpServerResponse, webClient, httpResponseAsyncResult));
          HashMap<String, String> hashMap = new HashMap<>();
          hashMap.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
          DatabaseServiceBuilder.redis()
            .findBuilder(vertx)
            .setOptions(CachedResponse.class)
            .find(uri)
            .future()
            .onSuccess(cacheResponse -> {
              System.out.println("From redis");
              Ok(JsonObject.mapFrom(cacheResponse), hashMap, routingContext);
            })
            .onFailure(cause -> request.send(httpResponseAsyncResult -> {
                if (httpResponseAsyncResult.succeeded()) {
                  HttpResponse<Buffer> response = httpResponseAsyncResult.result();
                  response.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
                  Buffer responseBody = response.body();
                  //send back result and cache in redis
                  if (httpResponseAsyncResult.result().statusCode() == HttpResponseStatus.OK.code()
                    || httpResponseAsyncResult.result().statusCode() == HttpResponseStatus.NO_CONTENT.code()) {
                    CachedResponse cachedResponse = new CachedResponse(responseBody.toJson(), false);
                    JsonObject httpServerResponseJson = JsonObject.mapFrom(cachedResponse);
                    System.out.println("Response is ended\n" + httpServerResponseJson.encodePrettily());
//                    String encoded = httpServerResponseJson.encode();
                    httpServerResponse
                      .setStatusCode(response.statusCode())
                      .end(responseBody);
//                      .end(httpServerResponseJson.toBuffer());
                    DatabaseServiceBuilder.redis()
                      .insertBuilder(vertx)
                      .setOptions(CachedResponse.class, new RedisOptions()
                        .setCacheExpiration(60)
                        .setKey(uri))
                      .save(new CachedResponse(responseBody.toJson(), true))
                      .future()
                      .onSuccess(redis -> System.out.println("successfully cached get request " + uri))
                      .onFailure(reason -> System.out.println("could not cache in redis " + reason.getMessage()));
                  } else {
                    httpServerResponse
                      .setStatusCode(response.statusCode())
                      .end(responseBody);
                  }
                }
//                ServiceDiscovery.releaseServiceObject(discovery, webClient);
              })
            );
////
        } else {
          request.send(httpResponseAsyncResult -> handleHttpResponse(httpServerResponse, webClient, httpResponseAsyncResult));
        }
      } else {
        BadRequest("service with root " + root + " was not found", routingContext);
        return;
      }


//      discovery.close();

    });
  }

  private void handleHttpResponse(HttpServerResponse serverResponse, WebClient webClient, AsyncResult<HttpResponse<Buffer>> httpResponseAsyncResult) {
    if (httpResponseAsyncResult.succeeded()) {
      HttpResponse<Buffer> response = httpResponseAsyncResult.result();
      Buffer body = response.bodyAsBuffer();
      response.headers().forEach(header -> serverResponse.putHeader(header.getKey(), header.getValue()));
      if (body == null) {
        serverResponse
          .setStatusCode(response.statusCode())
          .end();
      } else {
        serverResponse
          .setStatusCode(response.statusCode())
          .end(body);
      }
    } else {
      serverResponse.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
        .end();
    }
//    ServiceDiscovery.releaseServiceObject(discovery, webClient);

  }
}
