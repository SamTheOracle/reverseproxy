package com.samtheoracle.proxy.verticles;

import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.server.RestEndpoint;
import com.samtheoracle.proxy.utils.ClientUtils;
import com.samtheoracle.proxy.utils.Config;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.servicediscovery.ServiceDiscovery;

import java.util.logging.Logger;

public class HealthCheckVerticle extends RestEndpoint {

    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());
    private ServiceDiscovery discovery;
    private WebClient webClient;

    @Override
    public void start() throws Exception {

        LOGGER.info("Starting periodic health check");
        discovery = createDiscovery();
        webClient = ClientUtils.httpClient(vertx);
        vertx.setPeriodic(Config.HEARTBEAT * 1000, this::health);
    }

    private void health(Long id) {

        discovery.getRecords(record -> true, recordsAsync -> {
            if (recordsAsync.succeeded() && recordsAsync.result() != null) {
                recordsAsync.result().forEach(record -> {
                    int port = record.getLocation().getInteger("port");
                    String host = record.getLocation().getString("host");
                    LOGGER.info(String.format("making request to http://%s:%s/ping", host, port));
                    webClient.get(port, host, "/ping").expect(ResponsePredicate.SC_OK).timeout(Config.TIMEOUT_FAILURE*1000)
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
                                } else {
                                    // LOGGER.info("Record " + record.toJson().encode() + " is UP");
                                }
                            });
                });
            }
        });

    }

}
