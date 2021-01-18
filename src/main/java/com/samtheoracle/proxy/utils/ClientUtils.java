package com.samtheoracle.proxy.utils;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class ClientUtils{



    private static WebClient client;

    private ClientUtils(){}

    public static WebClient httpClient(Vertx vertx){
        if(client == null){
            WebClientOptions options = new WebClientOptions().setKeepAlive(false);
            client = WebClient.create(vertx,options);
        }
        return client;
    }
}