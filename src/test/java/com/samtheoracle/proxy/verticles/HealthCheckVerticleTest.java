package com.samtheoracle.proxy.verticles;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class HealthCheckVerticleTest {

    @Test
    void startOk(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new HealthCheckVerticle(), testContext.succeedingThenComplete());
    }
}