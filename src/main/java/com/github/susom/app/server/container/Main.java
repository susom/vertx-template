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

import com.github.susom.database.Config;
import com.github.susom.database.DatabaseProviderVertx;
import com.github.susom.database.DatabaseProviderVertx.Builder;
import com.github.susom.dbgoodies.vertx.DatabaseHealthCheck;
import com.github.susom.vertx.base.Lazy;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.susom.vertx.base.VertxBase.*;

/**
 * Sample application for manual testing and experimentation.
 */
public class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    try {
      initializeLogging();
      redirectConsoleToLog();

      startSecurityManager(
          // Our server must listen on a local port
          new SocketPermission("localhost:8080", "listen,resolve"),
          // If we need to connect to something like an email server
//          new SocketPermission("smtp.stanford.edu:587", "connect");
          // These two are for hsqldb to store its database files
          new FilePermission(workDir() + "/target/hsql", "read,write,delete"),
          new FilePermission(workDir() + "/target/hsql/-", "read,write,delete"),
          new FilePermission(workDir() + "/.vertx", "read,write,delete")
      );

      Config config = Config.from()
          .value("database.url", "jdbc:hsqldb:file:target/hsql/db;shutdown=true")
          .value("database.user", "SA")
          .value("database.password", "").get();
//      String propertiesFile = System.getProperty("properties", "local.properties");
//      Config config = Config.from().systemProperties().propertyFile(propertiesFile.split(":")).get();

      Vertx vertx = Vertx.vertx();

      Builder db = DatabaseProviderVertx.pooledBuilder(vertx, config).withSqlParameterLogging();

      SecureRandom random = createSecureRandom(vertx);

      // Avoid using sessions by cryptographically signing tokens with JWT
      // To create the private key do something like this:
      // keytool -genseckey -keystore keystore.jceks -storetype jceks -storepass secret \
      //         -keyalg HMacSHA256 -keysize 2048 -alias HS256 -keypass secret
      // For more info: https://vertx.io/docs/vertx-auth-jwt/java/
      Lazy<JWTAuth> jwt = Lazy.initializer(() -> JWTAuth.create(vertx, new JsonObject()
          .put("keyStore", new JsonObject()
              .put("type", "jceks")
              .put("path", config.getString("jwt.keystore.path", "conf/keystore.jceks"))
              .put("password", config.getString("jwt.keystore.secret", "secret")))));

      // The meat of the application goes here
      Router root = Router.router(vertx);
//      String context = "/" + config.getString("app.web.context", "app");
//      root.mountSubRouter(context, new SampleApi(db, random, jwt.get(), config).router(vertx));
      root.get("/hello").handler(rc -> {
        rc.response().end("Hello");
      });

      // Add status pages per DCS standards (JSON returned from /status and /status/app)
      new DatabaseHealthCheck(vertx, db, config).addStatusHandlers(root);

      // Start the server
      vertx.createHttpServer().requestHandler(root::accept).listen(8080, result ->
          log.info("Started server on port " + 8080 + ": http://localhost:8080/hello")
      );

      // Make sure we cleanly shutdown Vert.x and the database pool
      addShutdownHook(vertx, db::close);
    } catch (Exception e) {
      log.error("Unexpected exception in main()", e);
      System.exit(1);
    }
  }
}
