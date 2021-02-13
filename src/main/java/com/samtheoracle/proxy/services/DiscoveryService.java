package com.samtheoracle.proxy.services;

import com.samtheoracle.proxy.search.QuerySearch;
import com.samtheoracle.proxy.search.ServiceSearchParameter;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DiscoveryService {

    private final ServiceDiscovery discovery;

    public DiscoveryService(ServiceDiscovery discovery) {
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
        Function<Record, Boolean> alreadyPresentCondition = r -> r.getName().equals(record.getName())
                || r.getLocation().getString("root").equals(record.getLocation().getString("root"));

        discovery.getRecord(alreadyPresentCondition, serviceAlreadyPresent);

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
                if (recordsAsync.cause() != null) {
                    recordsAsync.cause().printStackTrace();
                }
                finalResult.fail("Not found");
            }
        });
        return finalResult;
    }

    public Promise<List<Record>> getRecords(Map<ServiceSearchParameter, List<String>> searchConditions) {
        Promise<List<Record>> finalResult = Promise.promise();
        List<Predicate<Record>> conditions = new ArrayList<>();
        List<Predicate<Record>> orConditions = new ArrayList<>();
        searchConditions.forEach((serviceSearchParameter, values) -> {
            switch (serviceSearchParameter) {
                case root:
                case ssl:
                case port:
                case host:
                case endpoint:
                    values.forEach(value -> orConditions.add(record -> record.getLocation().getString(serviceSearchParameter.name()).equals(value)));
                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                    orConditions.clear();
                    break;
                case status:
                    values.forEach(value -> {
                        if (Arrays.stream(Status.values()).anyMatch(status -> status.name().equals(value.toUpperCase()))) {
                            Status status = Status.valueOf(value.toUpperCase());
                            orConditions.add(record -> record.getStatus().equals(status));
                        } else {
                            orConditions.add(record -> false);
                        }
                    });
                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                    orConditions.clear();
                    break;
                case creationDate:
                    values.forEach(value -> {
                        Optional<QuerySearch> querySearchOptional = Optional.empty();
                        LocalDateTime date;
                        if (Arrays.stream(QuerySearch.values()).anyMatch(qs -> qs.name().equals(value.substring(0, 3)))) {
                            querySearchOptional = Arrays.stream(QuerySearch.values()).filter(qs -> qs.name().equals(value.substring(0, 3))).findFirst();
                            date = LocalDateTime.parse(value.substring(3));
                        } else if (Arrays.stream(QuerySearch.values()).anyMatch(qs -> qs.name().equals(value.substring(0, 2)))) {
                            querySearchOptional = Arrays.stream(QuerySearch.values()).filter(qs -> qs.name().equals(value.substring(0, 2))).findFirst();
                            date = LocalDateTime.parse(value.substring(2));
                        } else {
                            date = LocalDateTime.parse(value);
                        }
                        if (querySearchOptional.isPresent()) {
                            QuerySearch query = querySearchOptional.get();
                            switch (query) {
                                case gt:
                                    orConditions.add(record -> {
                                        try {
                                            LocalDateTime recordCreationDate = LocalDateTime.parse(Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(LocalDateTime.now().toString()));
                                            return recordCreationDate.isAfter(date);
                                        } catch (Exception e) {
                                            finalResult.fail("Not a correct timestamp");
                                            return false;
                                        }
                                    });
                                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                                    orConditions.clear();
                                    break;
                                case lt:
                                    orConditions.add(record -> {
                                        try {
                                            LocalDateTime recordCreationDate = LocalDateTime.parse(Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(LocalDateTime.now().toString()));
                                            return recordCreationDate.isBefore(date);
                                        } catch (Exception e) {
                                            finalResult.fail("Not a correct timestamp");
                                            return false;
                                        }
                                    });
                                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                                    orConditions.clear();
                                    break;
                                case gte:
                                    orConditions.add(record -> {
                                        try {
                                            LocalDateTime recordCreationDate = LocalDateTime.parse(Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(LocalDateTime.now().toString()));
                                            return recordCreationDate.isAfter(date) || recordCreationDate.isEqual(date);
                                        } catch (Exception e) {
                                            finalResult.fail("Not a correct timestamp");
                                            return false;
                                        }
                                    });
                                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                                    orConditions.clear();
                                    break;
                                case lte:
                                    orConditions.add(record -> {
                                        try {
                                            LocalDateTime recordCreationDate = LocalDateTime.parse(Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(LocalDateTime.now().toString()));
                                            return recordCreationDate.isBefore(date) || recordCreationDate.isEqual(date);
                                        } catch (Exception e) {
                                            finalResult.fail("Not a correct timestamp");
                                            return false;
                                        }
                                    });
                                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                                    orConditions.clear();
                                    break;

                            }
                        } else {
                            orConditions.add(record -> {
                                try {
                                    LocalDateTime recordCreationDate = LocalDateTime.parse(Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(LocalDateTime.now().toString()));
                                    return recordCreationDate.isEqual(date);
                                } catch (Exception e) {
                                    finalResult.fail("Not a correct timestamp");
                                    return false;
                                }

                            });
                            conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                            orConditions.clear();
                        }

                    });

                    break;
                case name:
                    values.forEach(value -> orConditions.add(record -> record.getName().equals(value)));
                    conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
                    orConditions.clear();
                    break;
            }
        });
        if (!finalResult.future().isComplete()) {
            discovery.getRecords(record -> conditions.stream().allMatch(recordPredicate -> recordPredicate.test(record)), recordsAsync -> {
                if (recordsAsync.succeeded() && !recordsAsync.result().isEmpty()) {
                    finalResult.complete(recordsAsync.result());
                } else {
                    finalResult.fail("Not found");
                }
            });
        }

        return finalResult;
    }

    public Promise<String> deleteAllRecords() {
        Promise<String> finalResult = Promise.promise();
        Promise<List<Record>> recordsPromise = Promise.promise();

        discovery.getRecords(record -> true, recordsPromise);

        recordsPromise.future().compose(records -> {
            List<Promise<Void>> unpublishPromises = new ArrayList<>();
            records.forEach(record -> {
                Promise<Void> unpublishPromise = Promise.promise();
                discovery.unpublish(record.getRegistration(), unpublishPromise);
                unpublishPromises.add(unpublishPromise);
            });
            return Future.succeededFuture(unpublishPromises);
        }).compose(unpublishPromises -> CompositeFuture.all(unpublishPromises.stream().map(Promise::future).collect(Collectors.toList())))
                .onSuccess(event -> finalResult.complete())
                .onFailure(finalResult::fail);
        return finalResult;
    }

    public Promise<Record> getRecordByRoot(String root) {
        Promise<Record> finalResult = Promise.promise();
        discovery.getRecords(record -> record.getLocation().getString("root").equals(root), recordAsync -> {
            if (recordAsync.succeeded() && !recordAsync.result().isEmpty()) {
                finalResult.complete(recordAsync.result().get(0));
            } else {
                finalResult.fail("No service found");
            }
        });
        return finalResult;
    }


    public Promise<Record> getRecordByRegistration(String registration) {
        Promise<Record> finalResult = Promise.promise();
        discovery.getRecord(record -> record.getRegistration().equals(registration), recordAsync -> {
            if (recordAsync.succeeded() && recordAsync.result() != null) {
                finalResult.complete(recordAsync.result());
            } else {
                finalResult.fail("No service found");
            }
        });
        return finalResult;
    }

    public Promise<Void> deleteRecordByRegistration(String registration) {
        Promise<Void> finalResult = Promise.promise();
        discovery.unpublish(registration, finalResult);
        return finalResult;
    }
}
