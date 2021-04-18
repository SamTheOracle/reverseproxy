#!/bin/bash
docker buildx use armbuilder

cd proxy
docker buildx build --platform linux/arm/v7 -t oracolo/proxy:armv7 --push .
cd ../apache
docker buildx build --platform linux/arm/v7 -t oracolo/apache:armv7 --push .
cd ../cadvisor
docker buildx build --platform linux/arm/v7 -t oracolo/cadvisor:armv7 --push .

docker context use default
docker buildx use default
