package com.samtheoracle.proxy.verticles;

import java.util.logging.Logger;

import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.services.DiscoveryHelperService;
import com.samtheoracle.proxy.utils.Config;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

public class HealthCheckVerticle extends AbstractVerticle {

    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());

    private DiscoveryHelperService helper;
    private WebClient client;

    @Override
    public void start() throws Exception {

        LOGGER.info("Starting periodic health check");

        helper = DiscoveryHelperService.create(Config.discovery(vertx));
        client = Config.httpClient(vertx);
        vertx.setPeriodic(Config.HEARTBEAT * 1000L, this::health);
    }

    private void health(Long id) {

        helper.getRecords().onSuccess(records -> records.forEach(record -> {
            int port = record.getLocation().getInteger("port");
            String host = record.getLocation().getString("host");
            LOGGER.info(String.format("making ping request to http://%s:%s/ping", host, port));
            client.get(port, host, "/ping").expect(ResponsePredicate.SC_OK).timeout(Config.TIMEOUT_FAILURE * 1000L).send().onFailure(
                    cause -> {
                        cause.printStackTrace();
                        helper.deleteRecordByRegistration(record.getRegistration()).onSuccess(
                                unused -> LOGGER.info("unpublished record")).onFailure(Throwable::printStackTrace);
                    });
        }));
    }

}
