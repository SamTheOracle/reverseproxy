package com.samtheoracle.proxy.services;

import com.oracolo.database.DatabaseService;
import com.samtheoracle.proxy.server.CachedResponse;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class CacheService {
    private final DatabaseService<CachedResponse> redis;

    public CacheService(Vertx vertx) {
        this.redis = DatabaseService.redis(vertx)
                .build(CachedResponse.class);
    }

    public Promise<CachedResponse> findCachedResponse(String uri) {
        return redis.find(uri);
    }

    public Promise<CachedResponse> saveCachedResponse(CachedResponse cachedResponse) {
        return redis.save(cachedResponse);
    }
}
