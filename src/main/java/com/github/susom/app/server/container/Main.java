/*
 * Copyright 2016 The Board of Trustees of The Leland Stanford Junior University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.susom.app.server.container;

import com.github.susom.app.server.services.CreateSchema;
import com.github.susom.database.Config;
import com.github.susom.database.DatabaseProviderVertx;
import com.github.susom.database.DatabaseProviderVertx.Builder;
import com.github.susom.vertx.base.DatabaseHealthCheck;
import com.github.susom.vertx.base.PortInfo;
import com.github.susom.vertx.base.Security;
import com.github.susom.vertx.base.SecurityImpl;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.susom.vertx.base.VertxBase.*;

/**
 * This is the main entry point for the application, but is mostly
 * boilerplate for configuring and launching things.
 */
public class Main {
  private void launch(String[] args) throws Exception {
    // Configure SLF4J and capture System.out and System.err into the log
    initializeLogging();
    Logger log = LoggerFactory.getLogger(Main.class);
    redirectConsoleToLog();

    // Read local configuration needed to start the app. It is a good idea
    // to keep these to a minimum, specifying only environment-dependent
    // variables needed to bootstrap things. Other application configuration
    // can be read from the database once the server is up.
    Config config = readConfig();
    log.info("Configuration is being loaded from the following sources in priority order:\n" + config.sources());

    // Coming soon...dev mode should start fake authentication, automatic reloading, etc.
    boolean devMode = config.getBooleanOrFalse("insecure.dev.mode");
    if (devMode) {
      log.warn("Running in development mode (INSECURE) because of property 'insecure.dev.mode'");
    }
    PortInfo listen = PortInfo.parseUrl(config.getString("listen.url", "http://0.0.0.0:8080"));
    String context = '/' + config.getString("app.context", "home");

    // Create the database schema if requested or we are running hsql the first time
    Set<String> argSet = new HashSet<>(Arrays.asList(args));
    if (argSet.contains("create-database") || (devMode && !Files.exists(Paths.get(".hsql"))
        && "jdbc:hsqldb:file:.hsql/db;shutdown=true".equals(config.getString("database.url")))) {
      CreateSchema.run(argSet, config);
      if (argSet.size() == 1 && argSet.contains("create-database")) {
        log.info("Only the create-database argument was provided, so exiting without starting the server");
        System.exit(0);
      }
    }
    // TODO database upgrade checks

    // Launch the server if requested
    if (argSet.isEmpty() || argSet.contains("run")) {
      Vertx vertx = Vertx.vertx();
      SecureRandom random = createSecureRandom(vertx);
      Builder db = DatabaseProviderVertx.pooledBuilder(vertx, config).withSqlParameterLogging();
      Router root = rootRouter(vertx, context);
      Security security = new SecurityImpl(vertx, root, random, config::getString);

      new SecureApp(db, random, security, config).configureRouter(vertx, security.authenticatedRouter(context));

      // Add status pages per DCS standards (JSON returned from /status and /status/app)
      new DatabaseHealthCheck(vertx, db, config).addStatusHandlers(root);

      // Start the server
      HttpServerOptions options = new HttpServerOptions();
      if (listen.proto().equals("https")) {
//        String sslKeyType = config.getString("ssl.keystore.type", "pkcs12");
        String sslKeyPath = config.getString("ssl.keystore.path", "local.ssl.pkcs12");
        String sslKeyPassword = config.getString("ssl.keystore.password", "secret");
//        if (devMode && !Files.exists(Paths.get(sslKeyPath))) {
//          log.info("Dev mode: creating a self-signed keystore for SSL/TLS");
//          sun.security.tools.keytool.Main.main(new String[] { "-keystore", sslKeyPath,
//              "-storetype", sslKeyType, "-storepass", sslKeyPassword, "-genkey", "-keyalg", "RSA", "-validity",
//              "3650", "-alias", "self", "-dname", "CN=localhost, OU=ME, O=Mine, L=Here, ST=CA, C=US" });
//        }
        options.setSsl(true).setKeyStoreOptions(new JksOptions().setPath(sslKeyPath).setPassword(sslKeyPassword));
      }
      options.setCompressionSupported(config.getBooleanOrTrue("http.compression"));
      vertx.createHttpServer(options).requestHandler(root::accept).listen(listen.port(), listen.host(), result -> {
        try {
          if (result.succeeded()) {
            int actualPort = result.result().actualPort();
            if (devMode) {
              log.info("Started server: {}://localhost:{}{}/", listen.proto(), actualPort, context);
            } else {
              log.info("Started server on port {}", actualPort);
            }

            // Make sure we cleanly shutdown Vert.x and the database pool on exit
            addShutdownHook(vertx, db::close);
          } else {
            log.error("Could not start server on port " + listen.port(), result.cause());

            vertx.close();
            db.close();
          }
        } catch (Throwable t) {
          log.error("Unexpected error", t);
          throw t;
        }
      });
    }
  }

  private Config readConfig() {
    String properties = System.getProperty("properties", "conf/app.properties" + File.pathSeparator + "local.properties" + File.pathSeparator + "sample.properties");
    return Config.from().systemProperties().propertyFile(properties.split(File.pathSeparator)).get();
  }

  public static void main(String[] args) {
    // Make sure we use the real console for error logging here because something
    // might have gone wrong during log config or console redirection
    PrintStream err = System.err;
    try {
      new Main().launch(args);
    } catch (Throwable t) {
      t.printStackTrace(err);
      err.println("Exiting with error code 1");
      System.exit(1);
    }
  }
}
