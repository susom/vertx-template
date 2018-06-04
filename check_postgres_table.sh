#!/bin/bash
num_retry=0
until [[ "$num_retry" -gt "$DB_TBL_CHK_MAX_RETRY" ]]
do
  echo "retry-$num_retry to check existence of table app_message in Postgres DB"
  num_retry=$((num_retry+1))
  RESP=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -h "$POSTGRES_SERVER" -c '\d app_message' > /dev/null 2>&1; echo $?)
  if [[ "$RESP" == 0 ]]; then
    echo "app_message table is created"
    exit 0
  fi
  sleep "$DB_TBL_CHK_SLEEP"
done

echo "app_message table is not created"
exit 1
