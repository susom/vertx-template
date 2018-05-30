#!/bin/bash
VAR="ERROR: ORA-"
while [[ $VAR =~ "ERROR: ORA-" ]]
do
  echo "checking existence of app_message"
  VAR=`/u01/app/oracle/product/12.2.0/dbhome_1/bin/sqlplus -S sys/Oradoc_db1@dbserver.workspace_default:1521/ORCLCDB.localdomain as sysdba   << EOF
describe app_message
EOF`
  sleep 10
done
echo "app_message table is created"
