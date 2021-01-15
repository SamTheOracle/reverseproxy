package com.samtheoracle.proxy.server;

import com.oracolo.database.redis.RedisEntity;

public class CachedResponse extends RedisEntity {

  private boolean cached;

  private Object data;


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

}
