package com.samtheoracle.proxy;

import com.oracolo.database.redis.RedisAccessVerticle;
import com.samtheoracle.proxy.server.HealthChecksVerticle;
import com.samtheoracle.proxy.server.ProxyServer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class ProxyBootstrap extends AbstractVerticle {

    public static void main(String[] args) {

        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ProxyBootstrap());
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Promise<String> proxyServerPromise = Promise.promise();
        Promise<String> redisAccessPromise = Promise.promise();
        Promise<String> healthChecksPromise = Promise.promise();
        vertx.deployVerticle(new RedisAccessVerticle(), redisAccessPromise);
        redisAccessPromise.future()
                .compose(serverDeploy -> {

                    vertx.deployVerticle(new ProxyServer(), proxyServerPromise);
                    return proxyServerPromise.future();
                })
                .compose(serverDeploy -> {

                    vertx.deployVerticle(new HealthChecksVerticle(), healthChecksPromise);
                    return healthChecksPromise.future();
                })
                .onSuccess(id -> startPromise.complete())
                .onFailure(startPromise::fail);
    }
}
