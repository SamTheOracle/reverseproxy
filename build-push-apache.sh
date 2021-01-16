#!/bin/bash

cd apache
docker build --tag oracolo/apache .
docker push oracolo/apache
