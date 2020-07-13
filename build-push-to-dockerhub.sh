#!/bin/bash
mvn clean install
docker build --tag oracolo/proxy .
docker push oracolo/proxy