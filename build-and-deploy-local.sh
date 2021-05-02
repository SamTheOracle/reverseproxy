#!/bin/bash
docker-compose stop
mvn clean install
docker-compose pull
docker-compose up --build --detach
echo [--------- LOGS ---------]
docker logs -f localproxy