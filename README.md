# Vertx Proxy

This proxy is developed using Vertx ServiceDiscovery and Vertx HealthChecks.
On startup, two web servers are started, the proxy and healthcheck.

## Proxy
Proxy allows to add, get and delete services. In addition it proxies http requests to available services, caching if specified by header Access-Control-Max-Age.

It is possibile to add new services via `POST` request using the url `http://<host>:<port>/services`. The format of the body is as follow:

``` 
{
   "location" : {
     "endpoint" : "http://localhost:123/path",
     "host" : "localhost",
     "port" : 123,
     "root" : "/path",
     "ssl" : false
   },
   "metadata" : {
     "creationDate" : "2020-08-26T11:24:33.361977"
   },
   "name" : "random",
   "status" : "UP",
   "registration" : "77075d0c-d011-4a9c-bfca-f0e6d758e9dd",
   "type" : "http-endpoint"
 }
```
You can get current services or deleting services using same url.
Proxy request works by parsing the url of the main http request using the `ROOT_PATH` value.

Normally, to call a service you would use the service host and port. If the proxy is started and the service is correctly registered, it is necessary to know only proxy host and port in order to make the web request, adding the `ROOT_PATH` before the service path.

Let's have the service described by the json above. Proxy will reroute requests to the same service by making the following
> `HTTP METHOD http://<proxy_host>:<proxy_port>/<root_path>/path`.

If the header `ACCESS_CONTROL_MAX_AGE` is used, the `GET` request will be cached, expiring after the number of seconds specified by its value. It is possible to set the max possible cache lifespan by using the environment variable `CACHE_MAX_AGE`.
If the header is not used, no item will be cached.
#### Environment variables
- `ROOT_PATH`. It indicates what the root path is. Default is `/api/v1`.
- `PORT`. It indicates the port on which the proxy server is exposed. Default is `8080`.
- `REDIS_KEY_SERVICES`. It indicates the redis key at which the services are retrieved and saved. Default is `http_endpoints`.
- `REDIS_DB_HOST`. It indicates the redis host name. Default is `localhost`.
- `REDIS_DB_PORT`. It indicates the redis host port. Default is `6379`.
- `CACHE_MAX_AGE`. It indicates the max age for cache http results in redis. Default is `60`.
- `INSTANCES`. It indicates the number of instances of the proxy server for load balancing. Default is `1`. 

## HealthChecks
HealthChecks checks the status of services, eliminating the services that are not healthy. Every `HEARTBEAT` seconds, it will perform a get request to the service endpoint.
In order to work, services must expose a ` GET /ping` endpoint that completes the http request. 
#### Environment variables
- `HEARTBEAT`. Interval of service checking in seconds. Default is `10`.
- `TIMEOUT_FAILURE`. Timeout after which the procedure is declared automatically failed if no response from service. Default is `4`.