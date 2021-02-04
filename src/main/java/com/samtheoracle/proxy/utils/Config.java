package com.samtheoracle.proxy.utils;

import java.util.Optional;

public class Config {
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

        private Config(){}
}
