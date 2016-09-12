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
      public static final String COLUMN_NAME_CONTACT_ID = "contact_id";
      // message actual text
      public static final String COLUMN_NAME_TEXT = "text";
      // message type: 'local' or 'remote'
      public static final String COLUMN_NAME_TYPE = "type";
      // time sent or received
      public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
   }

}

