#!/bin/bash
# docker start localredis
mvn clean install -DskipTests
# docker stop localredis
cd apache
docker build --tag oracolo/apache .
docker build push oracolo/apache
cd ..
docker build --tag oracolo/proxy .
docker push oracolo/proxy