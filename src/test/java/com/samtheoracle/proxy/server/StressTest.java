package com.samtheoracle.proxy.server;

import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.stream.IntStream;

public class StressTest {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustOptions(new JksOptions()
                .setPath("certificates/keystore.jks")
                .setPassword("changeit")));
        IntStream.range(0, 800).forEach(i -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("making http request " + i + "-th");
//            users/telegram/508229488
            client.getAbs("https://findmycar-proxy.com:7000/api/v1/users/telegram/508229488").send(event -> {
                System.out.println("done with " + i + "-th request");
                if (event.failed()) {
                    System.out.println(event.cause().getMessage());
                }
            });
        });
    }
}
