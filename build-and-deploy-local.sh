#!/bin/bash
docker-compose rm --stop --force
./build-push-to-dockerhub.sh
docker-compose up --detach
echo [--------- LOGS ---------]
docker logs -f localproxy