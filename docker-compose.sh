#!/bin/bash

# docker-compose.yml is used by docker-compose
# Start Postgres database service
echo "Starting Postgres DB service: docker-compose up -d dbserver"
docker-compose up -d dbserver

# Start application build service:
# App Source code will be built and the application will run against the Postgres Database

echo "Starting application build service: docker-compose up appserver"
docker-compose up appserver

# Checking existence of table app_server in Postgres Database created by the application
sudo docker exec dbserver /workspace/check_postgres_table.sh

# Cleaning the docker-compose resources
docker-compose down
