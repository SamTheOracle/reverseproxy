package com.samtheoracle.proxy.verticles;

import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.utils.Config;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.servicediscovery.ServiceDiscovery;

import java.util.logging.Logger;

public class HealthCheckVerticle extends AbstractVerticle {

    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());

    private ServiceDiscovery discovery;
    private WebClient client;

    @Override
    public void start() throws Exception {

        LOGGER.info("Starting periodic health check");

        discovery = Config.discovery(vertx);
        client = Config.httpClient(vertx);
        vertx.setPeriodic(Config.HEARTBEAT * 1000L, this::health);
    }

    private void health(Long id) {

        discovery.getRecords(record -> true, recordsAsync -> {
            if (recordsAsync.succeeded() && recordsAsync.result() != null) {
                recordsAsync.result().forEach(record -> {
                    int port = record.getLocation().getInteger("port");
                    String host = record.getLocation().getString("host");
                    LOGGER.info(String.format("making request to http://%s:%s/ping", host, port));
                    client.get(port, host, "/ping").expect(ResponsePredicate.SC_OK).timeout(Config.TIMEOUT_FAILURE * 1000L)
                            .send(asyncOp -> {
                                if (asyncOp.failed()) {
                                    Promise<Void> p = Promise.promise();
                                    // LOGGER.info("Record " + record.toJson().encode() + " is DOWN");
                                    discovery.unpublish(record.getRegistration(), p);
                                    p.future().onComplete(aVoid -> {
                                        if (aVoid.succeeded()) {
                                            LOGGER.info("unpublished record");
                                        } else {
                                            aVoid.cause().printStackTrace();
                                        }
                                    });
                                    asyncOp.cause().printStackTrace();
                                }  // LOGGER.info("Record " + record.toJson().encode() + " is UP");

                            });
                });
            }
        });

    }

}
