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

package com.telestax.restcomm_olympus;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
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

   // TODO: consider making DatabaseHelper singleton instead of DatabaseManager, I think it makes more sense
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
            contactList.add(createEntry(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_NAME)),
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
   // Retrieve all messages for a contact
   ArrayList<Map<String, String>> retrieveMessages(String contactName)
   {
      /*
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
            contactList.add(createEntry(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_NAME)),
                  cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_URI))));
         } while (cursor.moveToNext());
      }
      cursor.close();

      return contactList;
      */
      return null;
   }

   public void addMessage(String contactName, String messageText, boolean isLocal) throws SQLException
   {
      /*
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
      */
   }

   // Helpers
   private HashMap<String, String> createEntry(String name, String uri)
   {
      HashMap<String, String> item = new HashMap<String, String>();
      item.put("username", name);
      item.put("sipuri", uri);
      return item;
   }

}
