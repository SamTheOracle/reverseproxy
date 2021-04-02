package com.samtheoracle.proxy.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

public class DiscoveryHelperService {

	private final ServiceDiscovery discovery;

	private DiscoveryHelperService(ServiceDiscovery discovery) {
		this.discovery = discovery;
	}

	public static DiscoveryHelperService create(ServiceDiscovery discovery) {

		return new DiscoveryHelperService(discovery);
	}

	public Future<Record> createRecord(JsonObject recordJson) {
		Record recordToPublish = HttpEndpoint.createRecord(recordJson.getString("name"),
				recordJson.getJsonObject("location").getString("host"), recordJson.getJsonObject("location").getInteger("port"),
				recordJson.getJsonObject("location").getString("root"),
				new JsonObject().put("creationDate", LocalDateTime.now().toString()));
		Function<Record, Boolean> alreadyPresentCondition = r -> r.getName().equals(recordToPublish.getName()) || r.getLocation().getString(
				"root").equals(recordToPublish.getLocation().getString("root"));

		Future<Record> recordFuture = discovery.getRecord(alreadyPresentCondition);
		return recordFuture.map(record -> record == null ? recordToPublish : record).compose(discovery::publish);
	}

	public Future<List<Record>> getRecords() {
		return discovery.getRecords(record -> true).compose(
				records -> records.isEmpty() ? Future.failedFuture("No service") : Future.succeededFuture(records));
	}

	public Future<List<Record>> getRecords(Map<ServiceSearchParameter, List<String>> searchConditions) {
		List<Predicate<Record>> conditions = new ArrayList<>();
		List<Predicate<Record>> orConditions = new ArrayList<>();
		searchConditions.forEach((serviceSearchParameter, values) -> {
			switch (serviceSearchParameter) {
			case root:
			case ssl:
			case port:
			case host:
			case endpoint:
				values.forEach(
						value -> orConditions.add(record -> record.getLocation().getString(serviceSearchParameter.name()).equals(value)));
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
						querySearchOptional = Arrays.stream(QuerySearch.values()).filter(
								qs -> qs.name().equals(value.substring(0, 3))).findFirst();
						date = LocalDateTime.parse(value.substring(3));
					} else if (Arrays.stream(QuerySearch.values()).anyMatch(qs -> qs.name().equals(value.substring(0, 2)))) {
						querySearchOptional = Arrays.stream(QuerySearch.values()).filter(
								qs -> qs.name().equals(value.substring(0, 2))).findFirst();
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
									LocalDateTime recordCreationDate = LocalDateTime.parse(
											Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(
													LocalDateTime.now().toString()));
									return recordCreationDate.isAfter(date);
								} catch (Exception e) {
									return false;
								}
							});
							conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
							orConditions.clear();
							break;
						case lt:
							orConditions.add(record -> {
								try {
									LocalDateTime recordCreationDate = LocalDateTime.parse(
											Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(
													LocalDateTime.now().toString()));
									return recordCreationDate.isBefore(date);
								} catch (Exception e) {
									return false;
								}
							});
							conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
							orConditions.clear();
							break;
						case gte:
							orConditions.add(record -> {
								try {
									LocalDateTime recordCreationDate = LocalDateTime.parse(
											Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(
													LocalDateTime.now().toString()));
									return recordCreationDate.isAfter(date) || recordCreationDate.isEqual(date);
								} catch (Exception e) {
									return false;
								}
							});
							conditions.add(orConditions.stream().reduce(record -> false, Predicate::or));
							orConditions.clear();
							break;
						case lte:
							orConditions.add(record -> {
								try {
									LocalDateTime recordCreationDate = LocalDateTime.parse(
											Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(
													LocalDateTime.now().toString()));
									return recordCreationDate.isBefore(date) || recordCreationDate.isEqual(date);
								} catch (Exception e) {
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
								LocalDateTime recordCreationDate = LocalDateTime.parse(
										Optional.ofNullable(record.getMetadata().getString(serviceSearchParameter.name())).orElse(
												LocalDateTime.now().toString()));
								return recordCreationDate.isEqual(date);
							} catch (Exception e) {
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

		return discovery.getRecords(record -> conditions.stream().allMatch(recordPredicate -> recordPredicate.test(record))).compose(
				records -> records.isEmpty() ? Future.failedFuture("No service") : Future.succeededFuture(records));
	}

	public Future<Void> deleteAllRecords() {

		return discovery.getRecords(record -> true).compose(records -> {
			List<Promise<Void>> unpublishPromises = new ArrayList<>();
			records.forEach(record -> {
				Promise<Void> unpublishPromise = Promise.promise();
				discovery.unpublish(record.getRegistration(), unpublishPromise);
				unpublishPromises.add(unpublishPromise);
			});
			return Future.succeededFuture(unpublishPromises);
		}).compose(unpublishPromises -> CompositeFuture.all(
				unpublishPromises.stream().map(Promise::future).collect(Collectors.toList()))).mapEmpty();

	}

	public Future<Record> getRecordByRoot(String root) {
		return discovery.getRecord(record -> record.getLocation().getString("root").equals(root));
	}

	public Future<Record> getRecordByRegistration(String registration) {
		return discovery.getRecord(record -> record.getRegistration().equals(registration));
	}

	public Future<Void> deleteRecordByRegistration(String registration) {
		return discovery.unpublish(registration);
	}
}
