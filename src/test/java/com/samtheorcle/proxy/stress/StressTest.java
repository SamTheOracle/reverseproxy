package com.samtheorcle.proxy.stress;

import com.samtheoracle.proxy.server.CachedResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.WebClient;

import java.util.stream.IntStream;

public class StressTest {

    public static String REMOTE = "http://findmycar-proxy.com/proxy/api/v1/tracks/508229488/vehicles";
    public static String LOCAL = "http://localhost:80/proxy/api/v1/tracks/508229488/vehicles";

    public static void main(String[] args) {
        String value = "gt34759023098432qwegt12345";
        String[] split = value.split("gt");
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        IntStream.range(0, 30000).forEach(i -> {

            System.out.println("making http request " + i + "-th");
            // users/telegram/508229488
            // client.getAbs("http://findmycar-proxy.com/proxy/api/v1/tracks/positions/508229488").send(event
            // -> {
            // System.out.println("done with " + i + "-th request");
            // if (event.failed()) {
            // System.out.println(event.cause().getMessage());
            // }
            // });
            // client.getAbs("http://findmycar-proxy.com/proxy/api/v1/users/telegram/508229488")
            //         .putHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "30").send(event -> {
            //             System.out.println("done with " + i + "-th request");
            //             if (event.failed()) {
            //                 System.out.println(event.cause().getMessage());
            //             }
            //         });
            client.getAbs(LOCAL).putHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "30").send(event -> {
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
