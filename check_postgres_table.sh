#!/bin/bash
VAR=1
while [[ $VAR != 0 ]]
do
  echo "checking existence of app_message"
  PGPASSWORD=$POSTGRES_PASSWORD psql -U $POSTGRES_USER -d $POSTGRES_DB -h $POSTGRES_URL -c "\d app_message"
  VAR=`echo $?`
  sleep 10
done
echo "app_message table is created"
