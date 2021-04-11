package com.samtheoracle.proxy.services;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class ProxyService {
    private static ProxyService instance;
    private final WebClient client;
    private final DiscoveryHelperService discoveryHelperService;

    private ProxyService(WebClient client, DiscoveryHelperService discoveryHelperService) {
        this.client = client;
        this.discoveryHelperService = discoveryHelperService;
    }

    public static ProxyService instance(WebClient client, DiscoveryHelperService discoveryHelperService) {
        if (instance == null) {
            instance = new ProxyService(client, discoveryHelperService);
        }
        return instance;
    }

    public Future<HttpResponse<Buffer>> reroute(String root, String uri, HttpMethod method, int timeout, Buffer body, MultiMap headers) {
        return discoveryHelperService.getRecordByRoot(root).compose(record -> {
            int port = record.getLocation().getInteger("port");
            String host = record.getLocation().getString("host");
            return body == null ? client.request(method, port, host, uri).putHeaders(headers).timeout(timeout).send() :
                    client.request(method, port, host, uri).putHeaders(headers).sendBuffer(body);
        });
    }


}
