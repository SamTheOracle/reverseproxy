package com.samtheoracle.proxy;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.utils.Config;
import com.samtheoracle.proxy.verticles.HealthCheckVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.logging.Logger;

public class ProxyBootstrap extends AbstractVerticle {

    private final Logger LOGGER = Logger.getLogger(ProxyBootstrap.class.getName());

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ProxyBootstrap());
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {


        Promise<String> proxyServerPromise = Promise.promise();
        Promise<String> redisAccessPromise = Promise.promise();
        Promise<String> healthChecksPromise = Promise.promise();
        DeploymentOptions proxyDeployment = new DeploymentOptions().setInstances(Config.PROXY_INSTANCES);
        vertx.deployVerticle(RedisAccessVerticle.class, proxyDeployment, redisAccessPromise);
        redisAccessPromise.future().compose(serverDeploy -> {
            vertx.deployVerticle(ProxyServer.class, proxyDeployment, proxyServerPromise);
            return proxyServerPromise.future();
        }).compose(serverDeploy -> {

            if (Config.HEALTHCHECK) {
                vertx.deployVerticle(new HealthCheckVerticle(), new DeploymentOptions().setWorker(true),
                        healthChecksPromise);
                return healthChecksPromise.future();
            }
            healthChecksPromise.complete();
            return healthChecksPromise.future();

        }).onSuccess(id -> startPromise.complete()).onFailure(startPromise::fail);
    }
}
