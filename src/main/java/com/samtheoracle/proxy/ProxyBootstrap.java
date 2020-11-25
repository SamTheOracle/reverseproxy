package com.samtheoracle.proxy;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.server.HealthChecksServer;
import com.samtheoracle.proxy.server.ProxyServer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.Optional;
import java.util.logging.Logger;

public class ProxyBootstrap extends AbstractVerticle {

    private final static int PROXY_INSTANCES = Integer.parseInt(Optional.ofNullable(System.getenv("INSTANCES")).orElse("1"));
    private final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ProxyBootstrap());
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        LOGGER.info("Deploying " + PROXY_INSTANCES + " of proxy server and redis access");

        Promise<String> proxyServerPromise = Promise.promise();
        Promise<String> redisAccessPromise = Promise.promise();
        Promise<String> healthChecksPromise = Promise.promise();

        vertx.deployVerticle(RedisAccessVerticle.class, new DeploymentOptions().setInstances(PROXY_INSTANCES), redisAccessPromise);
        redisAccessPromise.future()
                .compose(serverDeploy -> {

                    vertx.deployVerticle(ProxyServer.class, new DeploymentOptions().setInstances(PROXY_INSTANCES), proxyServerPromise);
                    return proxyServerPromise.future();
                })
                .compose(serverDeploy -> {

                    vertx.deployVerticle(new HealthChecksServer(), new DeploymentOptions().setWorker(true), healthChecksPromise);
                    return healthChecksPromise.future();
                })
                .onSuccess(id -> startPromise.complete())
                .onFailure(startPromise::fail);
    }
}
