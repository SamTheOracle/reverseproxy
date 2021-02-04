#!/bin/bash
docker-compose stop
docker start localredis
mvn clean install
docker stop localredis
docker-compose build
docker-compose pull
docker-compose up --detach
echo [--------- LOGS ---------]
docker logs -f localproxy