package com.samtheoracle.proxy.server;

import com.samtheoracle.proxy.utils.SSLUtils;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

public class TestUtilsWithSSL {
    private static final int TIME = 2000;
    private static final int SCALE = 1000;


    public static HttpRequest<Buffer> get(Vertx vertx, int port, String uri, ResponsePredicate responsePredicate) {
        return WebClient.create(vertx, SSLUtils.sslWebClientOptions())
                .get(port, "localhost", uri)
                .expect(responsePredicate)
                .timeout(TIME * SCALE);
    }

    public static HttpRequest<Buffer> post(Vertx vertx, int port, String uri, ResponsePredicate responsePredicate) {

        return WebClient.create(vertx, SSLUtils.sslWebClientOptions())
                .post(port, "localhost", uri)
                .expect(responsePredicate)
                .timeout(TIME * SCALE);


    }

    public static HttpRequest<Buffer> update(Vertx vertx, int port, String uri, ResponsePredicate responsePredicate) {
        return WebClient.create(vertx, SSLUtils.sslWebClientOptions())
                .put(port, "localhost", uri)
                .expect(responsePredicate)
                .timeout(TIME * SCALE);
    }

    public static HttpRequest<Buffer> delete(Vertx vertx, int port, String uri, ResponsePredicate responsePredicate) {
        return WebClient.create(vertx, SSLUtils.sslWebClientOptions())
                .delete(port, "localhost", uri)
                .expect(responsePredicate)
                .timeout(TIME * SCALE);
    }
}


