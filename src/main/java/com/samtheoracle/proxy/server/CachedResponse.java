package com.samtheoracle.proxy.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oracolo.database.redis.RedisOptions;

public class CachedResponse {

  private boolean cached;

  private Object data;

  @JsonIgnore
  private RedisOptions redisOptions;

  public CachedResponse() {
  }

  public CachedResponse(Object data, boolean cached) {
    this.data = data;
    this.cached = cached;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public boolean isCached() {
    return cached;
  }

  public void setCached(boolean cached) {
    this.cached = cached;
  }

  @JsonIgnore
  public RedisOptions getRedisOptions() {
    return redisOptions;
  }

  @JsonIgnore
  public void setRedisOptions(RedisOptions redisOptions) {
    this.redisOptions = redisOptions;
  }
}
