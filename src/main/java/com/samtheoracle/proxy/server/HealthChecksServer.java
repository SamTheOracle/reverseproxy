package com.samtheoracle.proxy.server;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HealthChecksServer extends RestEndpoint {
    private final Logger LOGGER = Logger.getLogger(HealthChecksServer.class.getName());
    private ServiceDiscovery discovery;
    private final static int PORT = 9000;
    private final static int HEARTBEAT = Integer.parseInt(Optional.ofNullable(System.getenv("HEARTBEAT")).orElse("10"));
    private final static String REDIS_DB_HOST = Optional.ofNullable(System.getenv("REDIS_DB_HOST"))
            .orElse("localhost");
    private final static int REDIS_DB_PORT = Integer.parseInt(Optional.ofNullable(System.getenv("REDIS_DB_PORT"))
            .orElse("6379"));
    private static final String REDIS_KEY_SERVICES = Optional.ofNullable(System.getenv("REDIS_KEY_SERVICES")).orElse("http_endpoints");
    private static final int TIMEOUT_FAILURE = Integer.parseInt(Optional.ofNullable(System.getenv("TIMEOUT_FAILURE")).orElse("4"));

    private final BiConsumer<WebClient, Promise<Status>> procedureFn = (webClient, promise) -> webClient.get("/ping").send(responseHandler -> {
        if (responseHandler.succeeded()) {
            promise.tryComplete(Status.OK());
        } else {
            //if timeout is reached promise is already completed, so use try
            promise.tryComplete(Status.KO(new JsonObject().put("failedTime", LocalDateTime.now().toString())));
        }
    });


    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        final HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
        final Router router = Router.router(vertx);
        router.get("/health").handler(healthCheckHandler);

        vertx.eventBus().<JsonObject>consumer("vertx.discovery.announce").handler(event -> handleIncomingService(event, healthCheckHandler));

        createServer(PORT, router)
                .future()
                .onSuccess(httpServer -> {
                    LOGGER.info("Deployed health checks verticles");
                    this.discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
                            .setBackendConfiguration(new JsonObject()
                                    .put("host", REDIS_DB_HOST)
                                    .put("port", REDIS_DB_PORT)
                                    .put("key", REDIS_KEY_SERVICES)));
                    startHealthCheck(healthCheckHandler);
                    startPromise.complete();
                }).onFailure(startPromise::fail);
    }

    private void handleIncomingService(Message<JsonObject> objectMessage, HealthCheckHandler healthCheckHandler) {
        JsonObject json = objectMessage.body();
        if (json.getString("status").equals(io.vertx.servicediscovery.Status.UP.name())) {
            discovery.getRecord(record -> record.getMetadata().encode().equals(json.getJsonObject("metadata").encode()), ar -> {
                if (ar.succeeded()) {
                    WebClient webClient = discovery.getReference(ar.result()).getAs(WebClient.class);
                    healthCheckHandler.register(ar.result().getRegistration(),
                            TIMEOUT_FAILURE * 1000,
                            promise -> procedureFn.accept(webClient, promise));
                }
            });
        }
    }

    private void startHealthCheck(HealthCheckHandler healthCheckHandler) {

        discovery.getRecords(record -> true, handler -> {
            if (handler.succeeded()) {
                List<Record> records = handler.result();
                records.forEach(record -> healthCheckHandler.register(record.getRegistration(), TIMEOUT_FAILURE * 1000, promise -> {
                    WebClient webClient = discovery.getReference(record).getAs(WebClient.class);
                    procedureFn.accept(webClient, promise);
                }));
            }
        });
        //Start periodic check to services
        vertx.setPeriodic(HEARTBEAT * 1000,
                handler -> WebClient.create(vertx)
                        .get(PORT, "localhost", "/health")
                        .send(ar -> {
                            if (ar.succeeded() && ar.result().body() != null) {
                                System.out.println(ar.result().body().toString());
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
}
