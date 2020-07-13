package com.samtheoracle.proxy.server;

public class CachedResponse {

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
