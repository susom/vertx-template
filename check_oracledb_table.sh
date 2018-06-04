#!/bin/bash
SQLPATH="/u01/app/oracle/product/12.2.0/dbhome_1/bin/sqlplus"
LOGON="sys/Oradoc_db1@$ORACLEDB_SERVER:1521/ORCLCDB.localdomain as sysdba"

num_retry=0
until [[ "$num_retry" -gt "$DB_TBL_CHK_MAX_RETRY" ]]
do
  echo "retry-$num_retry to check existence of table app_message in Oracle DB"
  num_retry=$((num_retry+1))
  RESP=$("$SQLPATH" -S "$LOGON" << EOF
describe app_message
EOF
)
  echo "app_message table creation status is: $RESP"
  if [[ "$RESP" != *"ERROR:"* ]]; then
    exit 0
  fi
  sleep "$DB_TBL_CHK_SLEEP"
done

exit 1
