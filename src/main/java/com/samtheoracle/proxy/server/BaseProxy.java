package com.samtheoracle.proxy.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.oracolo.database.builder.ServiceBuilder;
import com.oracolo.database.redis.RedisService;
import com.samtheoracle.proxy.search.ServiceSearchParameter;
import com.samtheoracle.proxy.services.DiscoveryHelperService;
import com.samtheoracle.proxy.services.ProxyService;
import com.samtheoracle.proxy.utils.Config;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.AllowForwardHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;

public abstract class BaseProxy extends AbstractVerticle {
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private static final String REGISTRATION_ID = "registrationId";
	protected RedisService<CachedResponse> redis;
	protected DiscoveryHelperService discoveryHelperService;
	protected ProxyService proxyService;
	protected Router router;
	protected ServiceDiscovery discovery;
	protected WebClient client;
	protected CircuitBreaker circuitBreaker;

	@Override
	public void start() throws Exception {
		super.start();
		discovery = Config.discovery(vertx);
		client = Config.httpClient(vertx);

		CircuitBreakerOptions circuitBreakerOptions = new CircuitBreakerOptions().setMaxFailures(3).setResetTimeout(2000).setMaxRetries(
				3).setTimeout(Config.TIMEOUT_FAILURE * 1000L);
		circuitBreaker = CircuitBreaker.create("proxy-breaker", vertx, circuitBreakerOptions);
		redis = ServiceBuilder.create(vertx).redis(CachedResponse.class);
		discoveryHelperService = DiscoveryHelperService.create(discovery);
		proxyService = ProxyService.instance(client, discoveryHelperService);

		router = Router.router(vertx);
		router.allowForward(AllowForwardHeaders.ALL);
		router.route().handler(CorsHandler.create());
		router.route().handler(BodyHandler.create());

		router.get("/infos").handler(this::handleInfo);
		router.post("/services").handler(this::handleServiceCreation);
		router.get("/services").handler(this::handleGetServices);
		router.get("/service/:" + REGISTRATION_ID).handler(this::handleGetServiceById);
		router.delete("/services/all").handler(this::handleDeleteAllServices);
		router.delete("/services/:" + REGISTRATION_ID).handler(this::handleDeleteById);
	}

	protected static void BadRequest(String message, RoutingContext routingContext) {
		end(message, HttpResponseStatus.BAD_REQUEST.code(), routingContext);
	}

	protected static void NotFound(String message, RoutingContext routingContext) {
		end(message, HttpResponseStatus.NOT_FOUND.code(), routingContext);
	}

	protected static void Created(JsonObject endObject, Map<String, String> headers, RoutingContext routingContext) {
		end(endObject, headers, HttpResponseStatus.CREATED.code(), routingContext);
	}

	protected static void Ok(JsonArray endObject, Map<String, String> headers, RoutingContext routingContext) {
		end(endObject, headers, HttpResponseStatus.OK.code(), routingContext);
	}

	protected static void Ok(JsonObject endObject, Map<String, String> headers, RoutingContext routingContext) {
		end(endObject, headers, HttpResponseStatus.OK.code(), routingContext);
	}

	protected static void ServerError(JsonObject endObject, Map<String, String> headers, int errorCode, RoutingContext routingContext) {
		end(endObject, headers, errorCode, routingContext);
	}

	protected static void ServerError(String message, RoutingContext routingContext) {
		end(message, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), routingContext);
	}

	protected static void NoContent(Map<String, String> headers, RoutingContext routingContext) {
		end(headers, HttpResponseStatus.NO_CONTENT.code(), routingContext);
	}

	protected static void end(JsonObject endObject, Map<String, String> headers, int code, RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
		headers.forEach(response::putHeader);
		response.setStatusCode(code).end(endObject == null ? "" : endObject.encodePrettily());
	}

	protected static void end(String message, int code, RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
		response.setStatusCode(code).end(message);
	}

	protected static void end(JsonArray jsonArray, Map<String, String> map, int code, RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
		map.forEach(response::putHeader);
		response.setStatusCode(code).end(jsonArray.encodePrettily());
	}

	protected static void end(Map<String, String> map, int code, RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
		map.forEach(response::putHeader);
		response.setStatusCode(code).end();

	}

	private void handleInfo(RoutingContext routingContext) {
		JsonArray j = new JsonArray();
		router.getRoutes().stream().map(JsonObject::mapFrom).forEach(j::add);
		Ok(j, Collections.singletonMap(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
				routingContext);
	}

	private void handleDeleteById(RoutingContext routingContext) {
		String registration = routingContext.pathParam(REGISTRATION_ID);
		if (registration == null || registration.isEmpty()) {
			BadRequest("Wrong registration id", routingContext);
			return;
		}
		discoveryHelperService.deleteRecordByRegistration(registration).onSuccess(
				record -> NoContent(new HashMap<>(), routingContext)).onFailure(cause -> BadRequest(cause.getMessage(), routingContext));
	}

	private void handleGetServiceById(RoutingContext routingContext) {
		String registration = routingContext.pathParam(REGISTRATION_ID);
		if (registration == null || registration.isEmpty()) {
			BadRequest("Wrong registration id", routingContext);
			return;
		}
		discoveryHelperService.getRecordByRegistration(registration).onSuccess(
				record -> Ok(JsonObject.mapFrom(record), new HashMap<>(), routingContext)).onFailure(
				cause -> BadRequest(cause.getMessage(), routingContext));
	}

	private void handleDeleteAllServices(RoutingContext routingContext) {
		discoveryHelperService.deleteAllRecords().onSuccess(aVoid -> NoContent(new HashMap<>(), routingContext)).onFailure(
				cause -> ServerError(cause.getMessage(), routingContext));
	}

	private void handleGetServices(RoutingContext routingContext) {
		Map<ServiceSearchParameter, List<String>> query = new HashMap<>();
		Arrays.stream(ServiceSearchParameter.values()).forEach(serviceSearchParameter -> {
			List<String> queryParam = routingContext.queryParam(serviceSearchParameter.name());
			if (!queryParam.isEmpty()) {
				query.put(serviceSearchParameter, queryParam);
			}
		});

		Future<List<Record>> recordsFuture;

		if (query.isEmpty()) {
			recordsFuture = discoveryHelperService.getRecords();
		} else {
			recordsFuture = discoveryHelperService.getRecords(query);
		}

		recordsFuture.onSuccess(records -> {
			if (query.containsKey(ServiceSearchParameter.format) && Boolean.parseBoolean(query.get(ServiceSearchParameter.format).get(0))) {
				JsonObject groupedByName = new JsonObject(records.stream().collect(Collectors.toMap(Record::getName, Function.identity())));
				Ok(groupedByName, new HashMap<>(), routingContext);
			} else {
				JsonArray jsonArray = new JsonArray();
				records.stream().map(JsonObject::mapFrom).forEach(jsonArray::add);
				Ok(jsonArray, new HashMap<>(), routingContext);
			}

		}).onFailure(cause -> NotFound(cause.getMessage(), routingContext));
	}

	private void handleServiceCreation(RoutingContext routingContext) {
		JsonObject recordJson = routingContext.getBodyAsJson();

		discoveryHelperService.createRecord(recordJson).onSuccess(record -> {
			HashMap<String, String> headers = new HashMap<>();
			logger.info("publishing record " + record.toJson().encodePrettily());
			headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString());
			//                    headers.put(HttpHeaderNames.LAST_MODIFIED.toString(), record.getMetadata().getString("creationDate"));
			headers.put(HttpHeaderNames.LOCATION.toString(), routingContext.request().absoluteURI() + "/" + record.getRegistration());
			Created(JsonObject.mapFrom(record), headers, routingContext);
		}).onFailure(cause -> BadRequest(cause.getMessage(), routingContext));

	}

	protected void Ok(JsonObject mapFrom, RoutingContext routingContext) {
		end(mapFrom, HttpResponseStatus.OK.code(), routingContext);
	}

	private void end(JsonObject responseObject, int code, RoutingContext routingContext) {
		routingContext.response().setStatusCode(code).end(responseObject.encodePrettily());
	}

	protected Future<HttpServer> createServer(Router router, HttpServerOptions httpServerOptions) {
		return vertx.createHttpServer(httpServerOptions).requestHandler(router).listen(Config.PORT);
	}

	protected Future<HttpServer> createServer(Router router) {
		Promise<HttpServer> httpServerPromise = Promise.promise();
		return vertx.createHttpServer().requestHandler(router).listen(Config.PORT);
	}

}
