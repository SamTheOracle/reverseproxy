package com.samtheoracle.proxy.services;

import com.oracolo.database.builder.ServiceBuilder;
import com.oracolo.database.redis.RedisService;
import com.samtheoracle.proxy.server.CachedResponse;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class CacheService {
    private final RedisService<CachedResponse> redis;

    public CacheService(Vertx vertx) {
        this.redis = ServiceBuilder.create(vertx)
                .redis(CachedResponse.class);
    }

    public Promise<CachedResponse> findCachedResponse(String uri) {
        return redis.get(uri);
    }

    public Promise<CachedResponse> saveCachedResponse(String uri, int cacheEx, CachedResponse cachedResponse) {
        return redis.set(uri, cacheEx, cachedResponse);
    }
}
