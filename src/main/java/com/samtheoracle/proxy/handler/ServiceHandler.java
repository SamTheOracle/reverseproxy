package com.samtheoracle.proxy.handler;

import com.samtheoracle.proxy.search.ServiceSearchParameter;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceHandler {

    private final ServiceDiscovery discovery;

    public ServiceHandler(ServiceDiscovery discovery) {
        this.discovery = discovery;
    }

    public Promise<Record> createRecord(JsonObject recordJson) {
        Record record = HttpEndpoint.createRecord(recordJson.getString("name"),
                recordJson.getJsonObject("location").getString("host"),
                recordJson.getJsonObject("location").getInteger("port"),
                recordJson.getJsonObject("location").getString("root"),
                new JsonObject().put("creationDate", LocalDateTime.now().toString()));
        Promise<Record> finalResult = Promise.promise();
        Promise<Record> serviceAlreadyPresent = Promise.promise();
        discovery.getRecord(r -> r.getName().equals(record.getName()) && r.getLocation().getString("host").equals(record.getLocation().getString("host")), serviceAlreadyPresent);
        serviceAlreadyPresent.future().onSuccess(alreadyPresentRecord -> {
            if (alreadyPresentRecord == null) {
                discovery.publish(record, finalResult);
            } else {
                finalResult.complete(alreadyPresentRecord);
            }
        }).onFailure(cause -> discovery.publish(record, finalResult));

        return finalResult;
    }

    public Promise<List<Record>> getRecords() {
        Promise<List<Record>> finalResult = Promise.promise();

        discovery.getRecords(record -> true, recordsAsync -> {
            if (recordsAsync.succeeded() && !recordsAsync.result().isEmpty()) {
                finalResult.complete(recordsAsync.result());
            } else {
                finalResult.fail("Not found");
            }
        });
        return finalResult;
    }

    public Promise<List<Record>> getRecords(Map<ServiceSearchParameter, String> searchConditions) {
        Promise<List<Record>> finalResult = Promise.promise();
        JsonObject query = new JsonObject();
        searchConditions.forEach((serviceSearchParameter, value) -> query.put(serviceSearchParameter.name(), value));
        discovery.getRecords(query, recordsAsync -> {
            if (recordsAsync.succeeded() && !recordsAsync.result().isEmpty()) {
                finalResult.complete(recordsAsync.result());
            } else {
                finalResult.fail("Not found");
            }
        });
        return finalResult;
    }

    public Promise<Void> deleteAllRecords() {
        Promise<Void> finalResult = Promise.promise();
        Promise<List<Record>> recordsPromise = Promise.promise();
        List<Promise<Void>> unpublishPromises = new ArrayList<>();

        discovery.getRecords(record -> true, recordsPromise);

        recordsPromise.future().onSuccess(records -> records.forEach(record -> {
            Promise<Void> unpublishPromise = Promise.promise();
            discovery.unpublish(record.getRegistration(), unpublishPromise);
            unpublishPromises.add(unpublishPromise);
        }));
        CompositeFuture.all(unpublishPromises.stream().map(Promise::future).collect(Collectors.toList()))
                .onSuccess(event -> finalResult.complete())
                .onFailure(finalResult::fail);
        return finalResult;
    }

    public Promise<Record> getRecordByRoot(String root) {
        Promise<Record> finalResult = Promise.promise();
        discovery.getRecord(record -> record.getLocation().getString("root").equals(root), recordAsync -> {
            if (recordAsync.succeeded() && recordAsync.result() != null) {
                finalResult.complete(recordAsync.result());
            } else {
                finalResult.fail("No service found");
            }
        });
        return finalResult;
    }


}
