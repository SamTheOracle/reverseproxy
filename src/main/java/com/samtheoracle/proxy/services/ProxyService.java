package com.samtheoracle.proxy.services;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class ProxyService {
    private final WebClient client;
    private final DiscoveryService discoveryService;

    public ProxyService(WebClient client, DiscoveryService discoveryService) {
        this.client = client;
        this.discoveryService = discoveryService;
    }

    public Future<HttpResponse<Buffer>> reroute(String root, String uri, HttpMethod method, int timeout, Buffer body, MultiMap headers) {
        return discoveryService.getRecordByRoot(root).future()
                .compose(record -> {
                    int port = record.getLocation().getInteger("port");
                    String host = record.getLocation().getString("host");
                    return client.request(method, port, host, uri).putHeaders(headers).timeout(timeout).sendBuffer(body);
                });

    }

    public Future<HttpResponse<Buffer>> reroute(String root, String uri, HttpMethod method, int timeout, MultiMap headers) {
        return discoveryService.getRecordByRoot(root).future()
                .compose(record -> {
                    int port = record.getLocation().getInteger("port");
                    String host = record.getLocation().getString("host");
                    return client.request(method, port, host, uri).putHeaders(headers).timeout(timeout).send();
                });

    }


}
