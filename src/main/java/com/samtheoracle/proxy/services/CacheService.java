package com.samtheoracle.proxy.services;

import com.oracolo.database.DatabaseService;
import com.samtheoracle.proxy.server.CachedResponse;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class CacheService {
    private static CacheService instance;
    private final DatabaseService<CachedResponse> redis;

    private CacheService(Vertx vertx) {
        this.redis = DatabaseService.redis(vertx)
                .build(CachedResponse.class);

    }

    public static CacheService create(Vertx vertx) {
        if (instance == null) {
            instance = new CacheService(vertx);
        }
        return instance;
    }

    public Promise<CachedResponse> findCachedResponse(String uri) {
        return redis.find(uri);
    }

    public Promise<CachedResponse> saveCachedResponse(CachedResponse cachedResponse) {
        return redis.save(cachedResponse);
    }
}
