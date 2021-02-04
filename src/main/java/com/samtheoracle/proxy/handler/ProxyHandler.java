package com.samtheoracle.proxy.handler;

import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class ProxyHandler {
    private final WebClient client;
    private final ServiceHandler serviceHandler;

    public ProxyHandler(WebClient client, ServiceHandler serviceHandler) {
        this.client = client;
        this.serviceHandler = serviceHandler;
    }

    public Promise<HttpResponse<Buffer>> reroute(String root, String uri, HttpMethod method, int timeout, Buffer body, MultiMap headers) {
        Promise<HttpResponse<Buffer>> finalResult = Promise.promise();
        serviceHandler.getRecordByRoot(root).future().onSuccess(record -> {
            int port = record.getLocation().getInteger("port");
            String host = record.getLocation().getString("host");
            HttpRequest<Buffer> request = client.request(method, port, host, uri).putHeaders(headers).timeout(timeout);
            request.sendBuffer(body, finalResult);
        }).onFailure(finalResult::fail);
        return finalResult;

    }

    public Promise<HttpResponse<Buffer>> reroute(String root, String uri, HttpMethod method, int timeout, MultiMap headers) {
        Promise<HttpResponse<Buffer>> finalResult = Promise.promise();
        serviceHandler.getRecordByRoot(root).future().onSuccess(record -> {
            int port = record.getLocation().getInteger("port");
            String host = record.getLocation().getString("host");
            HttpRequest<Buffer> request = client.request(method, port, host, uri).putHeaders(headers).timeout(timeout);
            request.send(finalResult);
        }).onFailure(finalResult::fail);
        return finalResult;
    }


}
