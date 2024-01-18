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
package com.github.susom.app.server.services;

import com.github.susom.database.Database;
import java.util.function.Supplier;

/**
 * Database layer for storing and retrieving messages. The general
 * idea is to isolate the SQL and other database-specific things in
 * this class and provide a more code-friendly representation.
 *
 * @author garricko
 */
public class MessageDao {
  private final Supplier<Database> dbs;

  public MessageDao(Supplier<Database> dbs) {
    this.dbs = dbs;
  }

  public long addMessage(String message) {
    return dbs.get().toInsert("insert into app_message (app_message_id, message) values (?,?)")
        .argPkSeq("app_pk_seq").argString(message).insertReturningPkSeq("app_pk_seq");
  }

  public Message findMessageById(Long messageId) {
    if (messageId == null) {
      return null;
    }

    return dbs.get().toSelect("select message from app_message where app_message_id=?")
        .argLong(messageId).queryOneOrNull(rs -> {
          Message result = new Message();
          result.messageId = messageId;
          result.message = rs.getStringOrNull();
          return result;
        });
  }

  public static class Message {
    public Long messageId;
    public String message;
  }
}
