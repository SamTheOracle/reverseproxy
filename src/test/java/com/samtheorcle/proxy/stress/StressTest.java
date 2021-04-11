package com.samtheorcle.proxy.stress;

import com.samtheoracle.proxy.utils.Config;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class StressTest {

	public static String REMOTE = "http://findmycar-proxy.com/proxy/api/v1/tracks/508229488/vehicles";
	public static String LOCAL = "http://localhost:80/proxy/api/v1/tracks/508229488/vehicles";

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(StressTestVerticle.class, new DeploymentOptions().setInstances(Config.PROXY_INSTANCES + 5));
	}
}
