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
import com.github.susom.dbgoodies.vertx.DatabaseHealthCheck;
import com.github.susom.vertx.base.SecurityImpl;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static com.github.susom.vertx.base.VertxBase.*;

/**
 * This is the main entry point for the application, but is mostly
 * boilerplate for configuring and launching things.
 */
public class Server {
  public void launch(String[] args) throws Exception {
    // Configure SLF4J and capture System.out and System.err into the log
    initializeLogging();
    Logger log = LoggerFactory.getLogger(Server.class);
    redirectConsoleToLog();

    // Read local configuration needed to start the app. It is a good idea
    // to keep these to a minimum, specifying only environment-dependent
    // variables needed to bootstrap things. Other application configuration
    // can be read from the database once the server is up.
    String properties = System.getProperty("properties", "conf/app.properties:local.properties:sample.properties");
    Config config = Config.from().systemProperties().propertyFile(properties.split(File.pathSeparator)).get();

    log.info("Configuration is being loaded as follows:\n" + config.sources());

    // Coming soon...dev mode should start fake authentication, automatic reloading, etc.
    boolean devMode = config.getBooleanOrFalse("insecure.dev.mode");
    if (devMode) {
      log.warn("Running in development mode");
    }

    String proto = config.getString("listen.proto", devMode ? "http" : "https");
    int port = config.getInteger("listen.port", 8000);
    String host = config.getString("listen.host", devMode ? "localhost" : "0.0.0.0");

    // Run the application inside the Java sandbox (like an Applet) to improve
    // security. Even if an attacker managed to trick our code into doing a
    // bad thing, the sandbox should prevent our code from being able to break
    // out to expose the underlying operating system.
    System.setSecurityManager(new SecurityManager());

    // Make sure the SecurityManager is doing something useful
    try {
      Files.exists(Paths.get(".."));
      log.error("Looks like the security sandbox is not working!");
    } catch (AccessControlException unused) {
      // Good, it's working
      log.info("Started the security manager");
    }

    // Create the database schema if requested or we are running hsql the first time
    Set<String> argSet = new HashSet<>(Arrays.asList(args));
    if (argSet.contains("create-database") || (devMode && !Files.exists(Paths.get(".hsql"))
        && "jdbc:hsqldb:file:.hsql/db;shutdown=true".equals(config.getString("database.url")))) {
      CreateSchema.run(argSet, config);
    }

    // Launch the server if requested
    if (argSet.isEmpty() || argSet.contains("run")) {
      Vertx vertx = Vertx.vertx();

      Builder db = DatabaseProviderVertx.pooledBuilder(vertx, config).withSqlParameterLogging();

      SecureRandom random = createSecureRandom(vertx);

      // Avoid using sessions by cryptographically signing tokens with JWT
      // To create the private key do something like this:
      // keytool -genseckey -keystore keystore.jceks -storetype jceks -storepass secret \
      //         -keyalg HMacSHA256 -keysize 2048 -alias HS256 -keypass secret
      // For more info: https://vertx.io/docs/vertx-auth-jwt/java/
      String keystoreType = config.getString("jwt.keystore.type", "jceks");
      String keystorePath = config.getString("jwt.keystore.path", "local.jwt.jceks");
      String keystorePassword = config.getString("jwt.keystore.password", "secret");
      if (devMode && !Files.exists(Paths.get(keystorePath))) {
        log.info("Dev mode: creating a keystore for JWT");
        sun.security.tools.keytool.Main.main(new String[] { "-genseckey", "-keystore", keystorePath,
            "-storetype", keystoreType, "-storepass", keystorePassword, "-keyalg", "HMacSHA256", "-keysize", "2048",
            "-alias", "HS256", "-keypass", keystorePassword });
      }
      JWTAuth jwt = JWTAuth.create(vertx, new JsonObject()
          .put("keyStore", new JsonObject()
              .put("type", keystoreType)
              .put("path", keystorePath)
              .put("password", keystorePassword)));

      // The meat of the application goes here
      Router root = Router.router(vertx);
      root.route().handler(rc -> {
        // Make sure all requests start with a clean slate for logging
        MDC.clear();
        rc.next();
      });
      String context = new App(db, random, jwt, config).addContext(vertx, root);
      SecurityImpl security = new SecurityImpl(vertx, random, jwt, config::getString);
      String context2 = new SecureApp(db, random, security, config).addContext(vertx, root);
      // TODO add active defense handler here in front of everything
      // TODO add optional root redirect to app here?

      // Add status pages per DCS standards (JSON returned from /status and /status/app)
      new DatabaseHealthCheck(vertx, db, config).addStatusHandlers(root);

      // Start the server
      HttpServerOptions options = new HttpServerOptions();
      if (proto.equals("https")) {
        String sslKeyType = config.getString("ssl.keystore.type", "pkcs12");
        String sslKeyPath = config.getString("ssl.keystore.path", "local.ssl.pkcs12");
        String sslKeyPassword = config.getString("ssl.keystore.password", "secret");
        if (devMode && !Files.exists(Paths.get(sslKeyPath))) {
          log.info("Dev mode: creating a keystore for SSL/TLS");
          sun.security.tools.keytool.Main.main(new String[] { "-keystore", sslKeyPath,
              "-storetype", sslKeyType, "-storepass", keystorePassword, "-genkey", "-keyalg", "RSA", "-validity",
              "3650", "-alias", "self", "-dname", "CN=localhost, OU=ME, O=Mine, L=Here, ST=CA, C=US" });
        }
        options.setSsl(true).setKeyStoreOptions(new JksOptions().setPath(sslKeyPath).setPassword(sslKeyPassword));
      }
      vertx.createHttpServer(options).requestHandler(root::accept).listen(port, host, result -> {
        if (result.succeeded()) {
          int actualPort = result.result().actualPort();
          log.info("Started server on port {}:\n    {}://localhost:{}{}\n    {}://localhost:{}{}"
                  + "\n    {}://localhost:{}{}?a=b%20b#c+c"
                  + "\n    {}://localhost:{}{}/assets/css/bootstrap-3.3.6.min.cache.css",
              actualPort, proto, actualPort, context, proto, actualPort, context2, proto, actualPort,
              context2, proto, actualPort, context2);

          // Make sure we cleanly shutdown Vert.x and the database pool on exit
          addShutdownHook(vertx, db::close);
        } else {
          log.error("Could not start server on port " + port, result.cause());

          vertx.close();
          db.close();
        }
      });
    }
  }
}
