package com.github.susom.app.server.services;

import com.github.susom.database.Config;
import com.github.susom.database.Database;
import com.github.susom.database.DatabaseProvider;
import com.github.susom.database.Flavor;
import com.github.susom.database.Schema;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility to create a database schema for this application.
 */
public class CreateSchema {
  public static void main(String[] args) {
    Set<String> argSet = new HashSet<>(Arrays.asList(args));
    String propertiesFile = System.getProperty("properties", "local.properties");
    Config config = Config.from().systemProperties().propertyFile(propertiesFile).get();
    try {
      run(argSet, config);
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  @SuppressWarnings("tainting") // anything we can do, whoever gave us the database credentials can do
  public static void run(Set<String> argSet, Config config) throws Exception {
    String databaseUrl = config.getStringOrThrow("database.url");
    String databaseUser = config.getStringOrThrow("database.user");
    String databasePassword = config.getString("database.password");

    if (argSet.contains("-recreate")) {
      argSet.remove("-recreate");
      String systemUser = config.getString("database.system.user");
      String systemPassword = config.getString("database.system.password");
      DatabaseProvider.fromDriverManager(databaseUrl, systemUser, systemPassword).transact(dbp -> {
        Database db = dbp.get();
        if (db.flavor() == Flavor.postgresql) {
          // Drop quietly in case it doesn't already exist
          db.ddl("drop owned by " + databaseUser + " cascade").executeQuietly();
          db.ddl("drop user " + databaseUser).executeQuietly();

          db.ddl("create user " + databaseUser + " with password '" + databasePassword + '\'').execute();
          db.ddl("create schema authorization " + databaseUser).execute();
          db.ddl("grant all privileges on schema " + databaseUser + " to " + databaseUser).execute();
          db.ddl("grant connect on database " + databaseUrl.substring(databaseUrl.lastIndexOf('/') + 1) + " to "
              + databaseUser).execute();
        } else if (db.flavor() == Flavor.oracle) {
          // Drop quietly in case it doesn't already exist
          db.ddl("drop user " + databaseUser + " cascade").executeQuietly();

          db.ddl("create user " + databaseUser + " identified by \"" + databasePassword + '"'
              + " default tablespace users quota unlimited on users temporary tablespace temp").execute();
          db.ddl("grant connect to " + databaseUser).execute();
          db.ddl("grant create table to " + databaseUser).execute();
          db.ddl("grant create trigger to " + databaseUser).execute();
          db.ddl("grant create view to " + databaseUser).execute();
          db.ddl("grant create sequence to " + databaseUser).execute();
          db.ddl("grant create procedure to " + databaseUser).execute();
          db.ddl("grant ctxapp to " + databaseUser).execute();
          db.ddl("grant select any dictionary to " + databaseUser).execute();
        }
      });
    }

    DatabaseProvider.fromDriverManager(databaseUrl, databaseUser, databasePassword).transact(dbp -> {
      // @formatter:off
      new Schema()
        .addTable("app_message")
          .withComment("Store the various messages we will return.")
          .withStandardPk()
          .trackUpdateTime()
          .addColumn("message").asString(4000).schema()
        .addSequence("app_pk_seq").start(1000).schema().execute(dbp);
      // @formatter:on

      MessageDao messageDao = new MessageDao(dbp);
      messageDao.addMessage("Hello world!");
      messageDao.addMessage("Hejsan du!");
    });
  }
}
