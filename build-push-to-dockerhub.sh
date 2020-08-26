#!/bin/bash
docker start localredis
mvn clean install -DskipTests
docker stop localredis
docker build --tag oracolo/proxy .
docker push oracolo/proxy