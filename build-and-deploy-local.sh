#!/bin/bash
docker-compose stop
mvn clean install -DskipTests
docker-compose build
docker-compose pull
docker-compose up --detach
echo [--------- LOGS ---------]
docker logs -f localproxy