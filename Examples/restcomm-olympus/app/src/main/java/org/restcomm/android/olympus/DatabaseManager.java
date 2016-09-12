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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Provides access to DB facilities
class DatabaseManager {
   private static DatabaseManager instance = new DatabaseManager();
   private static DatabaseHelper databaseHelper = null;
   private static final String TAG = "DatabaseManager";

   public static DatabaseManager getInstance()
   {
      return instance;
   }

   private DatabaseManager()
   {
   }

   // Before we can use Database manager we need to first call open() and pass Android context
   public void open(Context context)
   {
      if (databaseHelper == null) {
         Log.i(TAG, "Database hasn't been opened; opening now");
         // If this turns out to be slow, we might have to put it to background thread (AsyncTask, etc), but I think data are too little to cause us trouble
         databaseHelper = new DatabaseHelper(context);
      }
      else {
         Log.w(TAG, "Database is already open");
      }
   }

   // ---- Contacts table
   // Retrieve all contact entries from DB and return them
   ArrayList<Map<String, String>> retrieveContacts()
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened yet, please call open()");
      }

      //SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
      //qb.setTables(DatabaseContract.ContactEntry.TABLE_NAME);

      // Define a projection that specifies which columns from the database
      // you will actually use after this query.
      String[] columns = {
            DatabaseContract.ContactEntry.COLUMN_NAME_NAME,
            DatabaseContract.ContactEntry.COLUMN_NAME_URI,
      };

      // How you want the results sorted in the resulting Cursor
      //String sortOrder = DatabaseContract.ContactEntry.COLUMN_NAME_NAME + " ASC";

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor = db.query(
            DatabaseContract.ContactEntry.TABLE_NAME,  // The table to query
            columns,                                   // The columns to return
            null,                                      // The columns for the WHERE clause
            null,                                      // The values for the WHERE clause
            null,                                      // don't group the rows
            null,                                      // don't filter by row groups
            null                                       // don't sort the results
      );

      ArrayList<Map<String, String>> contactList = new ArrayList<Map<String, String>>();

      // moveToFirst() fails if cursor is empty
      if (cursor.moveToFirst()) {
         // iterate the rows, read from db and populate contactList
         do {
            contactList.add(createContactEntry(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_NAME)),
                  cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_URI))));
         } while (cursor.moveToNext());
      }
      cursor.close();

      return contactList;
   }

   public void addContact(String name, String uri) throws SQLException
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened.");
      }

      // Gets the data repository in write mode
      SQLiteDatabase db = databaseHelper.getWritableDatabase();

      // Create a new map of values, where column names are the keys
      ContentValues values = new ContentValues();

      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, name);
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, uri);
      db.insertOrThrow(DatabaseContract.ContactEntry.TABLE_NAME, null, values);
   }

   /*
    * Add contact if it doesn't exist already.
    * @return true if contact didn't exist (and hence was added), false if it existed
    */
   public boolean addContactIfNeded(String uri)
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened.");
      }

      String contactName = uri.replaceAll("^sip:", "").replaceAll("@.*$", "");

      Cursor cursor = getContactFromName(contactName);
      if (!cursor.moveToFirst()) {
         // doesn't exist, need to create it
         // Gets the data repository in write mode
         SQLiteDatabase db = databaseHelper.getWritableDatabase();

         // Create a new map of values, where column names are the keys
         ContentValues values = new ContentValues();

         values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, contactName);
         values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, contactName);

         db.insertOrThrow(DatabaseContract.ContactEntry.TABLE_NAME, null, values);

         return true;
      }
      return false;
   }

   // Important: currently contactName passed by Application is in reality the user part of the sipuri, so to match a contact entry
   // we try with COLUMN_NAME_URI, not COLUMN_NAME_NAME
   private int getContactIdFromName(String contactName)
   {
      Cursor cursor = getContactFromName(contactName);

      /*
      // Only interested in the ID
      String[] columns = {
            DatabaseContract.ContactEntry._ID,
      };

      // Add the WHERE clause
      String selection = DatabaseContract.ContactEntry.COLUMN_NAME_URI + " LIKE ?";
      String[] selectionArgs = { "%" + contactName + "%"};

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor = db.query(
            DatabaseContract.ContactEntry.TABLE_NAME,  // The table to query
            columns,                                   // The columns to return
            selection,                                 // The columns for the WHERE clause
            selectionArgs,                             // The values for the WHERE clause
            null,                                      // don't group the rows
            null,                                      // don't filter by row groups
            null,                                      // don't sort the results
            "1"                                        // only keep one entry
      );
      */

      // moveToFirst() fails if cursor is empty
      if (cursor.moveToFirst()) {
         int contactId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry._ID));
         return contactId;
      }
      else {
         return -1;
      }
   }

   // Important: currently contactName passed by Application is in reality the user part of the sipuri, so to match a contact entry
   // we try with COLUMN_NAME_URI, not COLUMN_NAME_NAME
   private Cursor getContactFromName(String contactName)
   {
      // Only interested in the ID
      /*
      String[] columns = {
            DatabaseContract.ContactEntry._ID,
      };
      */

      // Add the WHERE clause
      String selection = DatabaseContract.ContactEntry.COLUMN_NAME_URI + " LIKE ?";
      String[] selectionArgs = { "%" + contactName + "%"};

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor = db.query(
            DatabaseContract.ContactEntry.TABLE_NAME,  // The table to query
            null,                                      // The columns to return (all)
            selection,                                 // The columns for the WHERE clause
            selectionArgs,                             // The values for the WHERE clause
            null,                                      // don't group the rows
            null,                                      // don't filter by row groups
            null,                                      // don't sort the results
            "1"                                        // only keep one entry
      );

      return cursor;
   }

   private Cursor getContactFromUri(String uri)
   {
      // Only interested in the ID
      /*
      String[] columns = {
            DatabaseContract.ContactEntry._ID,
      };
      */

      // Add the WHERE clause
      String selection = DatabaseContract.ContactEntry.COLUMN_NAME_URI + " LIKE ?";
      String[] selectionArgs = { uri };

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor = db.query(
            DatabaseContract.ContactEntry.TABLE_NAME,  // The table to query
            null,                                      // The columns to return (all)
            selection,                                 // The columns for the WHERE clause
            selectionArgs,                             // The values for the WHERE clause
            null,                                      // don't group the rows
            null,                                      // don't filter by row groups
            null,                                      // don't sort the results
            "1"                                        // only keep one entry
      );

      return cursor;
   }

   // Updates contact in DB. Returns -1 if contact is not found
   public int updateContact(String name, String uri)
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened.");
      }

      // Gets the data repository in write mode
      SQLiteDatabase db = databaseHelper.getWritableDatabase();

      // Update name & uri columns
      ContentValues values = new ContentValues();

      // Don't allow contact name to be modified
      //values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, name);
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, uri);

      // Add the WHERE clause
      String selection = DatabaseContract.ContactEntry.COLUMN_NAME_NAME + " LIKE ?";
      String[] selectionArgs = { name };

      int count = db.update(
            DatabaseContract.ContactEntry.TABLE_NAME,
            values,
            selection,
            selectionArgs);

      if (count > 0) {
         int i = 0;
         ArrayList<Map<String, String>> allContacts = retrieveContacts();
         for (Map<String, String> item: allContacts) {
            if (item.get("username").equals(name)) {
               return i;
            }
            i++;
         }
      }

      return -1;
   }

   // Removes contact from DB. Returns -1 if contact is not found
   public int removeContact(String name, String uri)
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened.");
      }

      boolean found = false;
      int i = 0;
      ArrayList<Map<String, String>> allContacts = retrieveContacts();
      for (Map<String, String> item: allContacts) {
         if (item.get("username").equals(name)) {
            found = true;
            break;
         }
         i++;
      }

      if (!found) {
         return -1;
      }

      // Gets the data repository in write mode
      SQLiteDatabase db = databaseHelper.getWritableDatabase();

      // Update name & uri columns
      ContentValues values = new ContentValues();

      // Add the WHERE clause
      String selection = DatabaseContract.ContactEntry.COLUMN_NAME_NAME + " LIKE ?";
      String[] selectionArgs = { name };

      int count = db.delete(
            DatabaseContract.ContactEntry.TABLE_NAME,
            selection,
            selectionArgs);

      if (count > 0) {
         return i;
      }

      return -1;
   }

   // ---- Message table
   // Retrieve all messages for a contact ordered by timestamp
   ArrayList<Map<String, String>> retrieveMessages(String contactName)
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened yet, please call open()");
      }

      SQLiteDatabase db = databaseHelper.getReadableDatabase();

      // Add the WHERE clause
      String[] selectionArgs = { contactName };

      // Here's an example: SELECT * FROM message INNER JOIN contact ON message.contact_id = contact._id WHERE contact.name LIKE ? ORDER BY timestamp ASC
      String sqlQuery = "SELECT * FROM " + DatabaseContract.MessageEntry.TABLE_NAME + " INNER JOIN " +
            DatabaseContract.ContactEntry.TABLE_NAME + " ON message." + DatabaseContract.MessageEntry.COLUMN_NAME_CONTACT_ID + " = contact." +
            DatabaseContract.ContactEntry._ID + " " +
            "WHERE " + DatabaseContract.ContactEntry.TABLE_NAME + "." + DatabaseContract.ContactEntry.COLUMN_NAME_NAME + " LIKE ? " +
            "ORDER BY " + DatabaseContract.MessageEntry.COLUMN_NAME_TIMESTAMP + " ASC";

      Log.i(TAG, "Query String: " + sqlQuery);
      Cursor cursor = db.rawQuery(sqlQuery, selectionArgs);

      ArrayList<Map<String, String>> messageList = new ArrayList<Map<String, String>>();

      // moveToFirst() fails if cursor is empty
      if (cursor.moveToFirst()) {
         // iterate the rows, read from db and populate contactList
         do {
            messageList.add(createMessageEntry(
                  cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.MessageEntry.COLUMN_NAME_TYPE)),
                  cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_NAME)),
                  cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.MessageEntry.COLUMN_NAME_TEXT))));
         } while (cursor.moveToNext());
      }
      cursor.close();

      return messageList;
   }

   public void addMessage(String contactName, String messageText, boolean isLocal) throws SQLException
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened.");
      }

      // Gets the data repository in write mode
      SQLiteDatabase db = databaseHelper.getWritableDatabase();

      // Create a new map of values, where column names are the keys
      ContentValues values = new ContentValues();

      String type = "local";
      if (!isLocal) {
         type = "remote";
      }

      int contactId = getContactIdFromName(contactName);

      values.put(DatabaseContract.MessageEntry.COLUMN_NAME_CONTACT_ID, contactId);
      values.put(DatabaseContract.MessageEntry.COLUMN_NAME_TEXT, messageText);
      values.put(DatabaseContract.MessageEntry.COLUMN_NAME_TYPE, type);

      db.insertOrThrow(DatabaseContract.MessageEntry.TABLE_NAME, null, values);
   }

   // Helpers for adapters
   private HashMap<String, String> createContactEntry(String name, String uri)
   {
      HashMap<String, String> item = new HashMap<String, String>();
      item.put("username", name);
      item.put("sipuri", uri);
      return item;
   }

   private HashMap<String, String> createMessageEntry(String type, String name, String message)
   {
      HashMap<String, String> item = new HashMap<String, String>();
      if (type.equals("local")) {
         item.put(MessageFragment.MESSAGE_CONTACT_KEY, "Me");
      }
      else {
         item.put(MessageFragment.MESSAGE_CONTACT_KEY, name);
      }
      item.put(MessageFragment.MESSAGE_TEXT_KEY, message);
      return item;
   }

}
