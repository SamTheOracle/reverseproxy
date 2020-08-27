package com.samtheoracle.proxy.utils;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.client.WebClientOptions;

public class SSLUtils {
    private final static JksOptions jksOptions = new JksOptions()
            .setPath("proxy-keystore.jks")
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
}
