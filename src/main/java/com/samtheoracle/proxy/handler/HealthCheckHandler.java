package com.samtheoracle.proxy.handler;

import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.server.RestEndpoint;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.servicediscovery.ServiceDiscovery;

import java.util.Optional;
import java.util.logging.Logger;

public class HealthCheckHandler extends RestEndpoint {
    private final static int HEARTBEAT = Integer.parseInt(Optional.ofNullable(System.getenv("HEARTBEAT")).orElse("10"));
    private final static String REDIS_DB_HOST = Optional.ofNullable(System.getenv("REDIS_DB_HOST"))
            .orElse("localhost");
    private static final String REDIS_DB_PORT = Optional.ofNullable(System.getenv("REDIS_DB_PORT")).orElse("6379");
    private static final String REDIS_KEY_SERVICES = Optional.ofNullable(System.getenv("REDIS_KEY_SERVICES")).orElse("http_endpoints");
    private static final int TIMEOUT_FAILURE = Integer.parseInt(Optional.ofNullable(System.getenv("TIMEOUT_FAILURE")).orElse("4"));
    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
    private ServiceDiscovery discovery;

    @Override
    public void start() throws Exception {

        vertx.setPeriodic(HEARTBEAT * 1000, this::health);
    }

    private void health(Long id) {
        if (discovery != null) {
            discovery.close();
        }
        discovery = createDiscovery(REDIS_DB_HOST, REDIS_DB_PORT, REDIS_KEY_SERVICES);
        discovery.getRecords(record -> true, recordsAsync -> {
            if (recordsAsync.succeeded() && recordsAsync.result() != null) {
                recordsAsync.result().forEach(record -> {
                    WebClient webClient = discovery.getReference(record).getAs(WebClient.class);
                    webClient.get("/ping")
                            .expect(ResponsePredicate.SC_OK)
                            .timeout(800)
                            .send(asyncOp -> {
                                if (asyncOp.failed()) {
                                    Promise<Void> p = Promise.promise();
                                    LOGGER.info("Record " + record.toJson().encode() + " is DOWN");
                                    discovery.unpublish(record.getRegistration(), p);
                                    p.future().onSuccess(event -> LOGGER.info("unpublished record")).onFailure(Throwable::printStackTrace);
                                    asyncOp.cause().printStackTrace();
                                } else {
                                    LOGGER.info("Record " + record.toJson().encode() + " is UP");
                                }
                            });
                });
            }
        });

    }

}
