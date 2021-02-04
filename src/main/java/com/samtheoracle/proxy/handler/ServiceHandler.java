package com.samtheoracle.proxy.handler;

import io.netty.util.concurrent.Promise;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;

public class ServiceHandler{

    private final ServiceDiscovery discovery;

    public ServiceHandler(ServiceDiscovery discovery){
        this.discovery = discovery;
    }

    public Promise<Record> createRecord(Record record){
        return null;
    }
    
}
