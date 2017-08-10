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
import com.github.susom.database.Flavor;
import com.github.susom.dbgoodies.vertx.DatabaseHealthCheck;
import com.github.susom.vertx.base.PortInfo;
import com.github.susom.vertx.base.Security;
import com.github.susom.vertx.base.SecurityImpl;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import java.io.File;
import java.io.FilePermission;
import java.lang.management.ManagementPermission;
import java.net.SocketPermission;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Permission;
import java.security.SecureRandom;
import java.security.SecurityPermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;
import javax.management.MBeanPermission;
import javax.management.MBeanServerPermission;
import javax.management.MBeanTrustPermission;
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

    enableSecurityManager();

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
      });
    }
  }

  private Main installSecurityPolicy() throws Exception {
    Config config = readConfig();
    List<Permission> permissions = new ArrayList<>();

    // Need access to the network interface/port to which we listen
    PortInfo listen = PortInfo.parseUrl(config.getString("listen.url", "http://localhost:8000"));
    permissions.add(new SocketPermission("*:" + listen.port(), "listen,resolve"));

    // Configurable list of servers to which we can connect
    String csv = config.getString("connect.outbound");
    if (csv != null) {
      for (String s : csv.split(",")) {
        permissions.add(new SocketPermission(s, "connect,resolve"));
      }
    }

    // For fake security we need to act as a client to our own embedded authentication
    if (config.getBooleanOrFalse("insecure.fake.security")) {
      permissions.add(new SocketPermission("localhost:" + listen.port(), "connect,resolve"));
    }

    // Connecting to centralized authentication server
    PortInfo authServer = PortInfo.parseUrl(config.getString("auth.server.base.uri"));
    if (authServer != null) {
      permissions.add(new SocketPermission(authServer.host() + ":" + authServer.port(), "connect,resolve"));
    }

    // These two are for hsqldb to store its database files
    permissions.add(new FilePermission(workDir() + "/.hsql", "read,write,delete"));
    permissions.add(new FilePermission(workDir() + "/.hsql/-", "read,write,delete"));

    // In case we are terminating SSL/TLS on the server
    permissions.add(new FilePermission(workDir() + "/local.ssl.jks", "read"));

    // Vert.x default directory for handling file uploads
    permissions.add(new FilePermission(workDir() + "/file-uploads", "read,write"));

    // The SAML implementation needs these four (xml parsing; write metadata into conf)
    permissions.add(new FilePermission(workDir() + "/conf", "read,write"));
    permissions.add(new FilePermission(workDir() + "/conf/-", "read,write"));
    permissions.add(new SecurityPermission("org.apache.xml.security.register"));
    permissions.add(new PropertyPermission("org.apache.xml.security.ignoreLineBreaks", "write"));

    // Oracle JDBC driver requires these
    Flavor flavor = Flavor.fromJdbcUrl(config.getString("database.url", "jdbc:postgresql:"));
    if (flavor == Flavor.oracle) {
      permissions.add(new MBeanServerPermission("createMBeanServer"));
      permissions.add(new ManagementPermission("control"));
      permissions.add(new MBeanPermission("*", "registerMBean"));
      permissions.add(new MBeanTrustPermission("register"));
    }

    setSecurityPolicy(permissions.toArray(new Permission[0]));
    return this;
  }

  private Config readConfig() {
    String properties = System.getProperty("properties", "conf/app.properties:local.properties:sample.properties");
    return Config.from().systemProperties().propertyFile(properties.split(File.pathSeparator)).get();
  }

  public static void main(String[] args) {
    try {
      new Main().installSecurityPolicy().launch(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
