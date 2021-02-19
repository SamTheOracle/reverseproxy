package com.samtheoracle.proxy.utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;

import java.util.Optional;
import java.util.UUID;

public class Config {

        public static final int SERVICE_REQUEST_TIMEOUT = Integer.parseInt(Optional.ofNullable(System.getenv("SERVICE_REQUEST_TIMEOUT_MILL")).orElse("1000"));

        public final static int PROXY_INSTANCES = Runtime.getRuntime().availableProcessors() * 2;
        public static final String ROOT_PATH = Optional.ofNullable(System.getenv("ROOT_PATH")).orElse("/api/v1");
        public static final int PORT = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("8080"));
        public static final String REDIS_KEY_SERVICES = Optional.ofNullable(System.getenv("REDIS_KEY_SERVICES"))
                .orElse("http_endpoints");
        public static final String REDIS_DB_HOST = Optional.ofNullable(System.getenv("REDIS_DB_HOST"))
                .orElse("localhost");
        public static final String REDIS_DB_PORT = Optional.ofNullable(System.getenv("REDIS_DB_PORT")).orElse("6379");
        public static final int TIMEOUT_FAILURE = Integer
                        .parseInt(Optional.ofNullable(System.getenv("TIMEOUT_FAILURE")).orElse("4"));
        public final static int HEARTBEAT = Integer
                .parseInt(Optional.ofNullable(System.getenv("HEARTBEAT")).orElse("10"));
        public static final int CACHE_MAX_AGE = Integer
                .parseInt(Optional.ofNullable(System.getenv("CACHE_MAX_AGE")).orElse("60"));
        public final static String KEYSTORE = Optional.ofNullable(System.getenv("KEYSTORE"))
                .orElse("proxy-keystore-local.jks");
        public static final Boolean SSL = Optional.of(Boolean.parseBoolean(System.getenv("SSL"))).orElse(false);

        public static final Boolean HEALTHCHECK = Optional.of(Boolean.parseBoolean(System.getenv("HEALTHCHECK"))).orElse(false);
        private static WebClient client;

        public static ServiceDiscovery discovery(Vertx vertx) {
                return ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
                        .setName(UUID.randomUUID().toString())
                        .setBackendConfiguration(new JsonObject()
                                .put("connectionString", String.format("redis://%s:%s", Config.REDIS_DB_HOST, Config.REDIS_DB_PORT))
                                .put("key", Config.REDIS_KEY_SERVICES)));
        }

        public static WebClient httpClient(Vertx vertx) {
                if (client == null) {
                        WebClientOptions options = new WebClientOptions().setKeepAlive(false);
                        client = WebClient.create(vertx, options);
                }
                return client;
        }

        private Config() {
        }


}
