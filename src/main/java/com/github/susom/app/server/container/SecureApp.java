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

import com.github.susom.app.server.services.MessageDao;
import com.github.susom.app.server.services.MessageDao.Message;
import com.github.susom.database.Config;
import com.github.susom.database.DatabaseProviderVertx.Builder;
import com.github.susom.vertx.base.Security;
import com.github.susom.vertx.base.StrictResourceHandler;
import com.github.susom.vertx.base.Valid;
import com.github.susom.vertx.base.VertxBase;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This defines all of the JSON APIs supported by the application.
 *
 * @author garricko
 */
public class SecureApp {
  private static final Logger log = LoggerFactory.getLogger(SecureApp.class);
  private final Builder dbb;
  private final SecureRandom random;
  private final Security security;
  private final Config config;

  public SecureApp(Builder dbb, SecureRandom random, Security security, Config config) {
    this.dbb = dbb;
    this.random = random;
    this.security = security;
    this.config = config;
  }

  public void configureRouter(Vertx vertx, Router router) {
    // Authentication handlers have already been configured at this point

    // Add your own APIs here with appropriate authorization checks
    // To keep things clean, use a method reference and implement the API in a method below.
    router.get("/api/v1/secret").handler(this::secretApi).failureHandler(VertxBase::jsonApiFail);

    // Static content coming from the Java classpath. This is last in this
    // method because the routing path overlaps with the others above, and
    // we want them to take precedence.
    router.get("/*").handler(new StrictResourceHandler(vertx)
        .addDir("static/secure-app")
        .addDir("static/assets", "**/*", "assets")
        .rootIndex("index.nocache.html")
    );
  }

  // Place API handlers into separate methods to keep the above routing
  // as clear as possible.
  private void secretApi(RoutingContext rc) {
    // There are lots of helpful input validation routines to choose from.
    // This wil throw a BadRequestException if it fails, which will be handled
    // by the VertxBase::jsonApiFail above to send a 400 status code.
    Long messageId = Valid.nonnegativeLongOpt(rc.request().getParam("id"), "Expecting a number for id");

    // Database transactions are explicit. Here we issue a database query that
    // will run on a worker thread (asynchronously) and provide the result to
    // a generic helper that will send it to the client (and do some error
    // handling if necessary).
    dbb.transactAsync(dbs -> {
      Message message = new MessageDao(dbs).findMessageById(messageId);
      String secret;
      if (message == null) {
        secret = "The server says some secret stuff!";
      } else {
        secret = "The server says: " + message.message;
      }
      log.info("Look at me sharing a secret (this log entry should show the authenticated user)");
      return new JsonObject().put("message", secret);
    }, VertxBase.sendJson(rc));
  }
}
