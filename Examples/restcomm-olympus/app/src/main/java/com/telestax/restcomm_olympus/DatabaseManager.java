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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
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
   // Retrieve all contact entries from DB and return them in an ArrayList suitable for use by the ContactAdapter
   ArrayList<Map<String, String>> retrieveContactsArray()
   {
      if (databaseHelper == null) {
         throw new RuntimeException("Database hasn't been opened yet, please call open()");
      }

      SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
      qb.setTables(DatabaseContract.ContactEntry.TABLE_NAME);

      // Define a projection that specifies which columns from the database
      // you will actually use after this query.
      String[] columns = {
            DatabaseContract.ContactEntry.COLUMN_NAME_NAME,
            DatabaseContract.ContactEntry.COLUMN_NAME_URI,
      };

      // How you want the results sorted in the resulting Cursor
      String sortOrder = DatabaseContract.ContactEntry.COLUMN_NAME_NAME + " ASC";

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor = db.query(
            DatabaseContract.ContactEntry.TABLE_NAME,  // The table to query
            columns,                               // The columns to return
            null,                                // The columns for the WHERE clause
            null,                            // The values for the WHERE clause
            null,                                     // don't group the rows
            null,                                     // don't filter by row groups
            sortOrder                                 // The sort order
      );

      ArrayList<Map<String, String>> contactList = new ArrayList<Map<String, String>>();
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         // iterate the rows, read from db and populate contactList
         for (int i = 0; i < cursor.getCount(); i++) {
            contactList.add(createEntry(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_NAME)),
                  cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.ContactEntry.COLUMN_NAME_URI))));
            Log.w(TAG, "+++ contactList after addition: " + contactList);
         }
      }
      cursor.close();

      return contactList;
   }

   // Helpers
   private HashMap<String, String> createEntry(String uri, String name)
   {
      HashMap<String, String> item = new HashMap<String, String>();
      item.put("sipuri", uri);
      item.put("username", name);
      return item;
   }

}
