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
import com.github.susom.database.Config;
import com.github.susom.database.DatabaseProviderVertx.Builder;
import com.github.susom.vertx.base.MetricsHandler;
import com.github.susom.vertx.base.Valid;
import com.github.susom.vertx.base.WebAppJwtAuthHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CookieHandler;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main application, and will set up the various resources
 * that will be served.
 *
 * @author garricko
 */
public class App {
  private static final Logger log = LoggerFactory.getLogger(App.class);
  private final Builder dbb;
  private final SecureRandom random;
  private final JWTAuth jwt;
  private final Config config;

  public App(Builder dbb, SecureRandom random, JWTAuth jwt, Config config) {
    this.dbb = dbb;
    this.random = random;
    this.jwt = jwt;
    this.config = config;
  }

  public String addContext(Vertx vertx, Router root) {
    String context = '/' + config.getString("app.web.context", "app");
    Router router = Router.router(vertx);
    root.mountSubRouter(context, router);

    // ***
    // *** Be careful with this method! This is very security critical code!
    // *** Your routes must be set up in the correct way and order to secure
    // *** the resources and ensure proper authentication and authorization.
    // ***

    // User session will be picked up from cookie if present,
    // but is optional at this point (upgraded to required below)
    router.route().handler(CookieHandler.create());
    router.route().handler(WebAppJwtAuthHandler.optional(jwt));
    router.route().handler(new MetricsHandler(random, config.getBooleanOrFalse("insecure.log.full.requests")));

    // A public API with no authentication or authorization required
    router.get("/public").handler(rc -> {
      Long messageId = Valid.nonnegativeLongOpt(rc.request().getParam("id"), "Expecting a number for id");
      dbb.transactAsync(dbs -> {
        return new MessageDao(dbs).findMessageById(messageId);
      }, r -> {
        if (r.succeeded() && r.result() != null) {
          rc.response().end("The server says: " + r.result().message + '\n');
        } else {
          rc.response().end("The server says hello!\n");
        }
      });
    }).failureHandler(rc -> {
      log.error("Error in handler", rc.failure());
      rc.response().setStatusCode(500).end("Eek!\n");
    });

    // See how error handling works (client will see 500 Internal Error)
    router.get("/broken").handler(rc -> {
      throw new RuntimeException("Eek!");
    });

    // Information for the client about whether we are logged in, how to login, etc.
//    router.get("/login-status").handler(this::loginStatus);
//    router.get("/logout").handler(this::logout);
//    router.get("/callback").handler(this::loginCallback);

    // An API that is protected behind user authentication
    router.get("/secret").handler(WebAppJwtAuthHandler.mandatory(jwt));
    router.get("/secret").handler(rc -> {
      rc.response().end("The server says some secret stuff!\n");
    }).failureHandler(rc -> {
      log.error("Error in handler", rc.failure());
      rc.response().setStatusCode(500).end("Eek!\n");
    });

    // Static content coming from the Java classpath, no authentication
    // or authorization necessary. This is last in this method because
    // the routing path overlaps with the others above, and we want
    // them to take precedence.
//    router.get("/*").handler(new StrictResourceHandler(vertx)
//        .addDir("static/mystudy")
//        .addDir("static/assets", "**/*", "assets")
//        .rootIndex("mystudy.html")
//    );

    return context;
  }
}
