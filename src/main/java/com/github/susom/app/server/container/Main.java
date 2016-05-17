/*
 * Copyright 2016 The Board of Trustees of The Leland Stanford Junior University.
 * All Rights Reserved.
 *
 * See the NOTICE and LICENSE files distributed with this work for information
 * regarding copyright ownership and licensing. You may not use this file except
 * in compliance with a written license agreement with Stanford University.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See your
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.susom.app.server.container;

import com.github.susom.app.server.services.CreateSchema;
import com.github.susom.database.Config;
import com.github.susom.database.DatabaseProviderVertx;
import com.github.susom.database.DatabaseProviderVertx.Builder;
import com.github.susom.dbgoodies.vertx.DatabaseHealthCheck;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import java.io.FilePermission;
import java.net.SocketPermission;
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
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    try {
      // Configure SLF4J and capture System.out and System.err into the log
      initializeLogging();
      redirectConsoleToLog();

      // Read local configuration needed to start the app. It is a good idea
      // to keep these to a minimum, specifying only environment-dependent
      // variables needed to bootstrap things. Other application configuration
      // can be read from the database once the server is up.
      String propertiesFile = System.getProperty("properties", "local.properties");
      Config config = Config.from().systemProperties().propertyFile(propertiesFile.split(":")).get();

      // Run the application inside the Java sandbox (like an Applet) to improve
      // security. Even it an attacker managed to trick our code into doing a
      // bad thing, the sandbox should prevent our code from being able to break
      // out to expose the underlying operating system.
      startSecurityManager(
          // Our server must listen on a local port
          new SocketPermission("localhost:8080", "listen,resolve"),
          // If we need to connect to something like an email server
//          new SocketPermission("smtp.stanford.edu:587", "connect");
          // These two are for hsqldb to store its database files
          new FilePermission(workDir() + "/.hsql", "read,write,delete"),
          new FilePermission(workDir() + "/.hsql/-", "read,write,delete")
      );

      // Create the database schema if requested
      Set<String> argSet = new HashSet<>(Arrays.asList(args));
      if (argSet.contains("create-database")) {
        CreateSchema.run(argSet, config);
      }

      // Launch the server if requested
      if (argSet.contains("run")) {
        Vertx vertx = Vertx.vertx();

        Builder db = DatabaseProviderVertx.pooledBuilder(vertx, config).withSqlParameterLogging();

        SecureRandom random = createSecureRandom(vertx);

        // Avoid using sessions by cryptographically signing tokens with JWT
        // To create the private key do something like this:
        // keytool -genseckey -keystore keystore.jceks -storetype jceks -storepass secret \
        //         -keyalg HMacSHA256 -keysize 2048 -alias HS256 -keypass secret
        // For more info: https://vertx.io/docs/vertx-auth-jwt/java/
        JWTAuth jwt = JWTAuth.create(vertx, new JsonObject()
            .put("keyStore", new JsonObject()
                .put("type", "jceks")
                .put("path", config.getString("jwt.keystore.path", "local.jwt.jceks"))
                .put("password", config.getString("jwt.keystore.secret", "secret"))));

        // The meat of the application goes here
        Router root = Router.router(vertx);
        String context = '/' + config.getString("app.web.context", "app");
        root.mountSubRouter(context, new App(db, random, jwt, config).router(vertx));
        // TODO add active defense handler here in front of everything
        // TODO add optional root redirect to app here?

        // Add status pages per DCS standards (JSON returned from /status and /status/app)
        new DatabaseHealthCheck(vertx, db, config).addStatusHandlers(root);

        // Start the server
        vertx.createHttpServer().requestHandler(root::accept).listen(8080, result ->
            log.info("Started server on port {}: http://localhost:8080{}", 8080, context)
        );

        // Make sure we cleanly shutdown Vert.x and the database pool
        addShutdownHook(vertx, db::close);
      }
    } catch (Exception e) {
      log.error("Unexpected exception in main()", e);
      System.exit(1);
    }
  }
}
