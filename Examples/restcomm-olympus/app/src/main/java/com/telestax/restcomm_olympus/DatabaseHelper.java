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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {
   // If you change the database schema, you must increment the database version.
   public static final int DATABASE_VERSION = 4;
   public static final String DATABASE_NAME = "Olympus.db";

   private static final String TAG = "DatabaseHelper";

   private static final String TEXT_TYPE = " TEXT";
   private static final String COMMA_SEP = ",";
   private static final String SQL_CREATE_CONTACT_TABLE =
         "CREATE TABLE " + DatabaseContract.ContactEntry.TABLE_NAME + " (" +
               DatabaseContract.ContactEntry._ID + " INTEGER PRIMARY KEY," +
               DatabaseContract.ContactEntry.COLUMN_NAME_NAME + TEXT_TYPE + COMMA_SEP +
               DatabaseContract.ContactEntry.COLUMN_NAME_URI + TEXT_TYPE +
               " );";

   private static final String SQL_DELETE_ENTRIES =
         "DROP TABLE IF EXISTS " + DatabaseContract.ContactEntry.TABLE_NAME;

   public DatabaseHelper(Context context)
   {
      super(context, DATABASE_NAME, null, DATABASE_VERSION);
      Log.i(TAG, "+++ DatabaseHelper constructor");
   }

   public void onCreate(SQLiteDatabase db)
   {
      Log.i(TAG, "+++ Creating table: " + SQL_CREATE_CONTACT_TABLE);
      db.execSQL(SQL_CREATE_CONTACT_TABLE);
      populateSampleEntries(db);
   }

   public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
   {
      // This database is only a cache for online data, so its upgrade policy is
      // to simply to discard the data and start over
      db.execSQL(SQL_DELETE_ENTRIES);
      onCreate(db);
   }

   public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)
   {
      onUpgrade(db, oldVersion, newVersion);
   }

   // ---- Helpers
   // Populate DB with sample contacts, targeting Restcomm sample applications
   private void populateSampleEntries(SQLiteDatabase db)
   {
      // Gets the data repository in write mode
      //SQLiteDatabase db = this.getWritableDatabase();

      // Create a new map of values, where column names are the keys
      ContentValues values = new ContentValues();

      // TODO: for some reason 5 entries with same data are inserted, the data of the last entry below
      // commit once in the end
      db.beginTransaction();
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, "Play App");
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, "+1234");
      db.insert(DatabaseContract.ContactEntry.TABLE_NAME, null, values);
      values.clear();

      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, "Say App");
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, "+1235");
      db.insert(DatabaseContract.ContactEntry.TABLE_NAME, null, values);
      values.clear();

      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, "Gather App");
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, "+1236");
      db.insert(DatabaseContract.ContactEntry.TABLE_NAME, null, values);
      values.clear();

      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, "Conference App");
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, "+1310");
      db.insert(DatabaseContract.ContactEntry.TABLE_NAME, null, values);
      values.clear();

      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_NAME, "Conference Admin App");
      values.put(DatabaseContract.ContactEntry.COLUMN_NAME_URI, "+1311");
      db.insert(DatabaseContract.ContactEntry.TABLE_NAME, null, values);
      values.clear();

      // veeery important, otherwise transaction is deemed failed and is rolled back
      db.setTransactionSuccessful();
      db.endTransaction();
      //db.close();

      Log.i(TAG, "+++ Populated sample entries");
   }
}
