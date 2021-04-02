#!/bin/bash
docker-compose stop
docker start localredis
mvn clean install
docker stop localredis
docker-compose pull
docker-compose up --build --detach
echo [--------- LOGS ---------]
docker logs -f localproxy