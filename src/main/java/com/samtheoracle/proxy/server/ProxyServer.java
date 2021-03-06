package com.samtheoracle.proxy.server;

import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Logger;

import com.oracolo.database.redis.RedisOptions;
import com.samtheoracle.proxy.utils.Config;
import com.samtheoracle.proxy.utils.SSLUtils;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ProxyServer extends BaseProxy {
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	public ProxyServer(RedisOptions redisOptions) {
		super(redisOptions);
	}

	public ProxyServer() {
		super();
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {

		super.start();
		router.route(Config.ROOT_PATH + "/*").handler(this::handleRoutes);
		router.route(Config.ROOT_PATH + "/*").method(HttpMethod.DELETE).method(HttpMethod.POST).method(HttpMethod.PUT).handler(
				this::simpleReroute);
		router.get(Config.ROOT_PATH + "/*").method(HttpMethod.GET).handler(this::handleGet);
		Future<HttpServer> serverCreationFuture;
		if (Config.SSL) {
			serverCreationFuture = createServer(router, SSLUtils.httpSSLServerOptions());
		} else {
			serverCreationFuture = createServer(router);
		}
		serverCreationFuture.compose(httpServer -> {
			logger.info("Proxy Server Instance " + this);
			logger.info("Server started on port " + httpServer.actualPort());
			logger.info("deployed at port " + Config.PORT + " with root path " + Config.ROOT_PATH);
			return redis.connect(vertx);
		}).onFailure(cause -> {
			logger.severe("Failed to start server: " + cause.getMessage());
			startPromise.fail(cause);
		}).onSuccess(event -> startPromise.complete());
	}

	private void handleRoutes(RoutingContext routingContext) {
		logger.info("handling request " + routingContext.request().method().name() + " " + routingContext.request().absoluteURI());
		String uri = routingContext.request().uri().split(Config.ROOT_PATH)[1];
		String root = uri.split("/")[1];

		routingContext.put("uri", uri).put("root", root).next();
	}

	private void simpleReroute(RoutingContext routingContext) {
		Buffer body = routingContext.getBody();
		HttpServerRequest httpServerRequest = routingContext.request();
		HttpServerResponse httpServerResponse = routingContext.response();
		HttpMethod method = httpServerRequest.method();
		MultiMap headers = httpServerRequest.headers();
		String uri = routingContext.get("uri");
		String root = routingContext.get("root");
		proxyService.reroute("/" + root, uri, method, Config.SERVICE_REQUEST_TIMEOUT, body, headers).onSuccess(httpResponseFromService -> {
			Buffer bodyFromService = httpResponseFromService.body();
			httpResponseFromService.headers().forEach(header -> httpServerResponse.putHeader(header.getKey(), header.getValue()));
			httpServerResponse.setStatusCode(httpResponseFromService.statusCode());
			if (bodyFromService == null) {
				httpServerResponse.end();
			} else {
				CachedResponse cachedResponse;
				try {
					cachedResponse = new CachedResponse(httpResponseFromService.body().toJson(), false);
				} catch (Exception e) {
					cachedResponse = new CachedResponse(httpResponseFromService.body(), false);
				}
				httpServerResponse.end(JsonObject.mapFrom(cachedResponse).encode());
			}
		}).onFailure(cause -> ServerError(cause.getMessage(), routingContext));
	}

	private void handleGet(RoutingContext routingContext) {
		HttpServerRequest httpServerRequest = routingContext.request();
		HttpMethod method = httpServerRequest.method();
		MultiMap headers = httpServerRequest.headers();
		HttpServerResponse serverResponse = routingContext.response();
		String uri = routingContext.get("uri");
		String root = routingContext.get("root");
		redis.get(uri).onSuccess(cacheResponse -> {
			logger.info("Retrieving " + uri + " from redis");
			Ok(JsonObject.mapFrom(cacheResponse), new HashMap<>(), routingContext);
		}).onFailure(cause -> circuitBreaker.<Void>execute(promise -> handleCacheGet(root, uri, method, headers, serverResponse, promise)));
	}

	private void handleCacheGet(String root, String uri, HttpMethod method, MultiMap headers, HttpServerResponse serverResponse, Promise<Void> circuitBreakerPromise) {
		proxyService.reroute("/" + root, uri, method, Config.SERVICE_REQUEST_TIMEOUT, null, headers).onSuccess(httpResponseFromService -> {
			if (httpResponseFromService.statusCode() == HttpResponseStatus.OK.code()) {
				Buffer responseBody = httpResponseFromService.body();
				// send back result and cache in redis
				String maxAge = headers.get(HttpHeaderNames.CACHE_CONTROL);
				int age;
				if (maxAge != null && maxAge.contains(HttpHeaderValues.MAX_AGE + "=")) {
					age = Integer.parseInt(maxAge.replace(HttpHeaderValues.MAX_AGE + "=", ""));
				} else {
					age = 0;
				}
				int cacheAge = Math.min(age, Config.CACHE_MAX_AGE);
				// if response body is not a JSON string, it breaks
				CachedResponse cachedResponse;
				try {
					cachedResponse = new CachedResponse(responseBody.toJson(), false);
				} catch (Exception e) {
					cachedResponse = new CachedResponse(responseBody.toString(), false);
				}
				JsonObject httpServerResponseJson = JsonObject.mapFrom(cachedResponse);
				int bytes = httpServerResponseJson.encode().getBytes().length;
				httpResponseFromService.headers().forEach(header -> serverResponse.putHeader(header.getKey(), header.getValue()));
				serverResponse.putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(bytes));
				serverResponse.setStatusCode(httpResponseFromService.statusCode()).end(httpServerResponseJson.toBuffer());
				if (cacheAge != 0) {
					cachedResponse.setCached(true);
					redis.set(uri, cacheAge, cachedResponse).onSuccess(
							redis -> logger.info("successfully cached get request " + uri)).onFailure(
							reason -> logger.info("could not cache in redis " + reason.getMessage()));
				}
			} else {
				serverResponse.setStatusCode(httpResponseFromService.statusCode()).end(
						Optional.ofNullable(httpResponseFromService.body()).orElse(
								Buffer.buffer("No error description from request " + uri)));
			}
			circuitBreakerPromise.complete();
		}).onFailure(reason -> {
			reason.printStackTrace();
			serverResponse.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(
					"Error processing request " + uri + ". Reason: " + reason.getMessage());
			circuitBreakerPromise.complete();
		});
	}

}
