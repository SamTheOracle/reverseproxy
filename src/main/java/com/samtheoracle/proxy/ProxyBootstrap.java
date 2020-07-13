package com.samtheoracle.proxy;

import com.oracolo.database.redis.RedisAccessVerticle;
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
    Promise<String> p = Promise.promise();
    Promise<String> p2 = Promise.promise();
    vertx.deployVerticle(new ProxyServer(), p);
    p.future()
      .compose(serverDeploy -> {

        vertx.deployVerticle(new RedisAccessVerticle(), p2);
        return p2.future();
      })
      .onSuccess(id -> startPromise.complete())
      .onFailure(startPromise::fail);
  }
}
