package com.samtheoracle.proxy;

import java.util.logging.Logger;

import com.samtheoracle.proxy.server.ProxyServer;
import com.samtheoracle.proxy.utils.Config;
import com.samtheoracle.proxy.verticles.HealthCheckVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
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

		DeploymentOptions proxyDeployment = new DeploymentOptions().setInstances(Config.PROXY_INSTANCES);
		vertx.deployVerticle(ProxyServer.class, proxyDeployment).compose(
				event -> Config.HEALTHCHECK ? vertx.deployVerticle(new HealthCheckVerticle()) : Future.succeededFuture()).onSuccess(
				event -> startPromise.complete()).onFailure(startPromise::fail);

	}
}
