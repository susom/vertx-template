#!/bin/bash

# Oracle image pull need authentication to Docker Store
echo "Login to Docker Store : docker login"
docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD

# docker-compose.yml is used by docker-compose
# Start Oracle database service
echo "Starting Oracle DB service: docker-compose up -d dbserver"
docker-compose up -d dbserver

echo "Checking Oracle database health ... "

DB_HEALTH=""
while [ "${DB_HEALTH}" != "\"healthy\"" ]
do
 DB_HEALTH="$(docker inspect --format='{{json .State.Health.Status}}' dbserver)"
 sleep 20
 echo $DB_HEALTH
done

# Start application build service:
# ojdbc7.jar dependency will be installed in local maven repo.
# App Source code will be built and the application will run against the Oracle Database

echo "Starting application build service: docker-compose up appserver"
docker-compose up appserver

# Checking existence of table app_server in Oracle Database created by the application
sudo docker exec dbserver /workspace/check_oracle_table.sh

# Cleaning the docker-compose resources
docker-compose down
