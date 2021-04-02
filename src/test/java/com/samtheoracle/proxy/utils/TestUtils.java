package com.samtheoracle.proxy.utils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.types.HttpEndpoint;

public class TestUtils {

    public static Record record(int port, String name, String root) {
        return HttpEndpoint.createRecord(name, "localhost", port, root);
    }

	public static Future<HttpResponse<Buffer>> publishRecord(Vertx vertx, Record record) {
		Promise<HttpResponse<Buffer>> httpResponsePromise = Promise.promise();
		return WebClient.create(vertx).post(8080, "localhost", "/services")
				//                .expect(ResponsePredicate.SC_CREATED)
				.sendBuffer(JsonObject.mapFrom(record).toBuffer());

	}
}
