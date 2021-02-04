package com.samtheoracle.proxy;

import java.util.logging.Logger;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.verticles.HealthCheckVerticle;
import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.utils.Config;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class ProxyBootstrap extends AbstractVerticle {

        private final Logger LOGGER = Logger.getLogger(ProxyBootstrap.class.getName());

        public static void main(String[] args) {
                Vertx vertx = Vertx.vertx();
                vertx.deployVerticle(new ProxyBootstrap());
        }

        @Override
        public void start(Promise<Void> startPromise) throws Exception {
                LOGGER.info(String.format(
                                "Proxy condifiguration:\nROOT_PATH %s\nPORT %s\nREDIS_KEY_SERVICES %s\nREDIS_DB_HOST %s\nREDIS_DB_PORT %s\nTIMEOUT_FAILURE %s\nHEARTBEAT %s\nCACHE_MAX_AGE %s\nKEYSTORE %s\nSSL %s",
                                Config.ROOT_PATH, Config.PORT, Config.REDIS_KEY_SERVICES, Config.REDIS_DB_HOST,
                                Config.REDIS_DB_PORT, Config.TIMEOUT_FAILURE, Config.HEARTBEAT, Config.CACHE_MAX_AGE,
                                Config.KEYSTORE, Config.SSL));
                LOGGER.info("Deploying " + Config.PROXY_INSTANCES + " of proxy server and redis access");

                Promise<String> proxyServerPromise = Promise.promise();
                Promise<String> redisAccessPromise = Promise.promise();
                Promise<String> healthChecksPromise = Promise.promise();
                DeploymentOptions proxyDeployment = new DeploymentOptions().setInstances(Config.PROXY_INSTANCES);
                vertx.deployVerticle(RedisAccessVerticle.class, proxyDeployment, redisAccessPromise);
                redisAccessPromise.future().compose(serverDeploy -> {

                        vertx.deployVerticle(ProxyServer.class, proxyDeployment, proxyServerPromise);
                        return proxyServerPromise.future();
                }).compose(serverDeploy -> {

                        vertx.deployVerticle(new HealthCheckVerticle(), new DeploymentOptions().setWorker(true),
                                        healthChecksPromise);
                        return healthChecksPromise.future();
                }).onSuccess(id -> startPromise.complete()).onFailure(startPromise::fail);
        }
}
