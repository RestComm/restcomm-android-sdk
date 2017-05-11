/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.olympus;

import android.provider.BaseColumns;

// Define a contract for the DB schema, to make easier to picture and use
public final class DatabaseContract {
   // To prevent someone from accidentally instantiating the contract class,
   // give it an empty constructor
   public DatabaseContract() {

   }

   // Let's keep track of consecutive SQLite versions, so that we can handle upgrades properly, without data loss.
   // Each time the DB schema changes we need to come up with a new version (i.e. previous + 1), with a descriptive name and add it below.
   // Then we need to update DatabaseHelper.DATABASE_VERSION to be assigned to that.
   public class DatabaseVersions {
      // First DB version that we want to support
      public static final int DB_VERSION_GROUND_ZERO = 14;
      // Introducing delivery status field and SimpleCursorAdapter (issue #568)
      public static final int DB_VERSION_DELIVERY_STATUS = 15;
   };

   public enum MessageDeliveryStatus {
      TEXT_MESSAGE_PENDING,  // 0
      TEXT_MESSAGE_DELIVERED,  // 1
      TEXT_MESSAGE_FAILED,  // 2
   }

   // Inner class that defines the table contents
   public static abstract class ContactEntry implements BaseColumns {
      public static final String TABLE_NAME = "contact";
      // contact name, like 'Conference Bridge', or 'Bob'
      public static final String COLUMN_NAME_NAME = "name";
      // contact uri, like '+1235', or 'bob'
      public static final String COLUMN_NAME_URI = "uri";
   }

   // Inner class that defines the table contents
   public static abstract class MessageEntry implements BaseColumns {
      public static final String TABLE_NAME = "message";
      // Source contact for the message (i.e. remote peer that sent the message)
      // TODO: Notice that right now if the message's source is us, then we still use the remote peer's name as the contact_id to make sure that there's
      // an entry in the contact table in the db (we don't store 'us' in the db as a contact right now. Although strictly speaking this is wrong, we work around it
      // by using the 'type' field, and if type is local, then we don't depend on the contact_id. We haven't spent too much time on it because
      // the future plan is about Restcomm providing us with APIs for contacts so we need to consider how the db will be used then.
      public static final String COLUMN_NAME_CONTACT_ID = "contact_id";
      // job id created when a message was sent, so that we can associate a response to the message with original message. We need this
      // to be able to correlate a message delivery status with the message to which it is related with
      public static final String COLUMN_NAME_JOB_ID = "job_id";
      // message actual text
      public static final String COLUMN_NAME_TEXT = "text";
      // message type: 'local' or 'remote'
      public static final String COLUMN_NAME_TYPE = "type";
      // time sent or received
      public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
      // Was the message properly delivered?
      public static final String COLUMN_NAME_DELIVERY_STATUS = "delivery_status";
   }

}

