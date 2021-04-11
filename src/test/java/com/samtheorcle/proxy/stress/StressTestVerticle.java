package com.samtheorcle.proxy.stress;

import com.samtheoracle.proxy.server.CachedResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.WebClient;

import java.util.stream.IntStream;

import static com.samtheorcle.proxy.stress.StressTest.REMOTE;

public class StressTestVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        super.start();
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        IntStream.range(0, 50000).forEach(i -> {

            System.out.println("making http request " + i + "-th");
            client.getAbs("http://findmycar-proxy.com/proxy/api/v1/tracks/positions/9114a51f-45a7-47e8-a4db-171fc3bc241f/last")
                    .putHeader(HttpHeaderNames.CACHE_CONTROL.toString(), "max-age=30")
                    .send(event
                            -> {
                        System.out.println("done with " + i + "-th request");
                        if (event.failed()) {
                            System.out.println(event.cause().getMessage());
                        }
                    });
            client.getAbs("http://findmycar-proxy.com/proxy/api/v1/users/telegram/508229488")
                    .putHeader(HttpHeaderNames.CACHE_CONTROL.toString(), HttpHeaderValues.MAX_AGE+"=30").send(event -> {
                System.out.println("done with " + i + "-th request");
                if (event.failed()) {
                    System.out.println(event.cause().getMessage());
                }
            });
            client.getAbs(REMOTE).putHeader(HttpHeaderNames.CACHE_CONTROL.toString(), HttpHeaderValues.MAX_AGE.toString() + "=30").send(event -> {
                System.out.println("done with " + i + "-th");
                if (event.failed()) {
                    System.out.println(event.cause().getMessage());
                } else if (event.succeeded() && event.result().statusCode() == HttpResponseStatus.OK.code()) {
                    System.out.println("From server " + event.result().headers().get(HttpHeaderNames.FROM));
                    CachedResponse cachedResponse = Json.decodeValue(event.result().body(), CachedResponse.class);
                    System.out.println("response is cached? " + cachedResponse.isCached());
                } else {
                    System.out.println(event.result().statusCode());
                    System.out.println(event.result().bodyAsString());
                }
            });
        });
    }
}

