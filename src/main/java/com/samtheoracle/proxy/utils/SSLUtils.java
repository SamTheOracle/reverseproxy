package com.samtheoracle.proxy.utils;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.Optional;

public class SSLUtils {
    private final static String KEYSTORE_FILE_NAME = Optional.ofNullable(System.getenv("KEYSTORE_FILE_NAME")).orElse("proxy-keystore-local.jks");

    private final static JksOptions jksOptions = new JksOptions()
            .setPath(KEYSTORE_FILE_NAME)
            .setPassword("changeit");
    private final static JksOptions jksOptionsHealthchecks = new JksOptions()
            .setPath("proxy-keystore-healthcheck.jks")
            .setPassword("changeit");

    public static HttpServerOptions httpSSLServerOptions() {
        return new HttpServerOptions()
                .setSsl(true)
                .setKeyStoreOptions(jksOptions);
    }

    public static HttpServerOptions httpSSLServerOptionsHealthchecks() {

        return new HttpServerOptions()
                .setSsl(true)
                .setKeyStoreOptions(jksOptionsHealthchecks);
    }

    public static WebClientOptions sslWebClientOptions() {
        return new WebClientOptions()
                .setSsl(true)
                .setTrustOptions(jksOptions);
    }

    public static WebClientOptions sslWebClientOptionsHealthchecks() {
        return new WebClientOptions()
                .setSsl(true)
                .setTrustOptions(jksOptionsHealthchecks);
    }

    public static WebClientOptions sslProxySSLOptions(Vertx vertx) {

        return new WebClientOptions()
                .setSsl(true)
                .setTrustStoreOptions(jksOptions);
    }

}
