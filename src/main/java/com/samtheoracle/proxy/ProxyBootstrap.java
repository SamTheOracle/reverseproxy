package com.samtheoracle.proxy;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.handler.HealthCheckHandler;
import com.samtheoracle.proxy.server.ProxyServer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.Optional;
import java.util.logging.Logger;

public class ProxyBootstrap extends AbstractVerticle {

    private final Logger LOGGER = Logger.getLogger(ProxyBootstrap.class.getName());
    private final static int PROXY_INSTANCES = Integer
            .parseInt(Optional.ofNullable(System.getenv("INSTANCES")).orElse("1"));
    private static final String ROOT_PATH = Optional.ofNullable(System.getenv("ROOT_PATH")).orElse("/api/v1");
    private static final int PORT = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("8080"));
    private static final String REDIS_KEY_SERVICES = Optional.ofNullable(System.getenv("REDIS_KEY_SERVICES"))
            .orElse("http_endpoints");
    private static final String REDIS_DB_HOST = Optional.ofNullable(System.getenv("REDIS_DB_HOST")).orElse("localhost");
    private static final String REDIS_DB_PORT = Optional.ofNullable(System.getenv("REDIS_DB_PORT")).orElse("6379");
    private static final int TIMEOUT_FAILURE = Integer
            .parseInt(Optional.ofNullable(System.getenv("TIMEOUT_FAILURE")).orElse("4"));
    private final static int HEARTBEAT = Integer.parseInt(Optional.ofNullable(System.getenv("HEARTBEAT")).orElse("10"));
    private static final int CACHE_MAX_AGE = Integer
            .parseInt(Optional.ofNullable(System.getenv("CACHE_MAX_AGE")).orElse("60"));
    private final static String KEYSTORE = Optional.ofNullable(System.getenv("KEYSTORE"))
            .orElse("proxy-keystore-local.jks");

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ProxyBootstrap());
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        LOGGER.info(String.format(
                "Proxy condifiguration:\nROOT_PATH %s\nPORT %s\nREDIS_KEY_SERVICES %s\nREDIS_DB_HOST %s\nREDIS_DB_PORT %s\nTIMEOUT_FAILURE %s\nHEARTBEAT %s\nCACHE_MAX_AGE %s\nKEYSTORE %s",
                ROOT_PATH, PORT, REDIS_KEY_SERVICES, REDIS_DB_HOST, REDIS_DB_PORT, TIMEOUT_FAILURE, HEARTBEAT,
                CACHE_MAX_AGE,KEYSTORE));
        LOGGER.info("Deploying " + PROXY_INSTANCES + " of proxy server and redis access");

        Promise<String> proxyServerPromise = Promise.promise();
        Promise<String> redisAccessPromise = Promise.promise();
        Promise<String> healthChecksPromise = Promise.promise();

        vertx.deployVerticle(RedisAccessVerticle.class, new DeploymentOptions().setInstances(PROXY_INSTANCES),
                redisAccessPromise);
        redisAccessPromise.future().compose(serverDeploy -> {

            vertx.deployVerticle(ProxyServer.class, new DeploymentOptions().setInstances(PROXY_INSTANCES),
                    proxyServerPromise);
            return proxyServerPromise.future();
        }).compose(serverDeploy -> {

            vertx.deployVerticle(new HealthCheckHandler(), new DeploymentOptions().setWorker(true),
                    healthChecksPromise);
            return healthChecksPromise.future();
        }).onSuccess(id -> startPromise.complete()).onFailure(startPromise::fail);
    }
}
