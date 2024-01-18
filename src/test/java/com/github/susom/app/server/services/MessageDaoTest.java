/*
 * Copyright 2024 The Board of Trustees of The Leland Stanford Junior University.
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
package com.github.susom.app.server.services;

import com.github.susom.database.Config;
import com.github.susom.database.ConfigFrom;
import com.github.susom.database.Database;
import com.github.susom.database.DatabaseProvider;
import com.github.susom.database.OptionsOverride;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MessageDaoTest {
  protected DatabaseProvider dbp;
  protected Database db;
  protected Date now = new Date();

  @Test
  public void addMessage() {
    MessageDao messageDao = new MessageDao(dbp);
    long id = messageDao.addMessage("Tjenare");
    assertEquals("Tjenare", messageDao.findMessageById(id).message);
  }

  @Before
  public void setupJdbc() {
    dbp = createDatabaseProvider(new OptionsOverride() {
      @Override
      public Date currentDate() {
        return now;
      }

      @Override
      public Calendar calendarForTimestamps() {
        return Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"), Locale.US);
      }
    });
    db = dbp.get();
  }

  protected DatabaseProvider createDatabaseProvider(OptionsOverride options) {
    String propertiesFile = System.getProperty("properties", "sample.properties");
    Config config = ConfigFrom.firstOf()
        .systemProperties()
        .propertyFile(propertiesFile).get();
    return DatabaseProvider.fromDriverManager(config)
        .withSqlParameterLogging()
        .withSqlInExceptionMessages()
        .withOptions(options).create();
  }
}