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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ContactsController {
   private static final String TAG = "ContactsController";
   //private static final String PREFS_CONTACTS_NAME = "contacts.xml";
   //private static final String PREFS_CONTACTS_INIT_KEY = "prefs-initialized";
   public static final String CONTACT_KEY = "username";
   public static final String CONTACT_VALUE = "sipuri";
   private Context context;

   SharedPreferences prefsContacts = null;

   ContactsController(Context context)
   {
      this.context = context;
   }

   // Retrieve all contact entries from DB and return them in an ArrayList suitable for use by the ContactAdapter
   ArrayList<Map<String, String>> retrieveContacts()
   {
      /*
      // didn't find a clean way to add default values as an .xml in resources without binding them to UI elements
      //PreferenceManager.setDefaultValues(this, "contacts.xml", MODE_PRIVATE, R.xml.contacts, false);
      //prefs = PreferenceManager.getDefaultSharedPreferences(this);
      prefsContacts = context.getSharedPreferences(PREFS_CONTACTS_NAME, 0);
      boolean initialized = prefsContacts.getBoolean(PREFS_CONTACTS_INIT_KEY, false);
      // initialize data store if not already populated
      if (!initialized) {
         SharedPreferences.Editor prefEdit = prefsContacts.edit();
         //prefEdit.putString("sip:alice@cloud.restcomm.com", "Alice");
         //prefEdit.putString("sip:bob@cloud.restcomm.com", "Bob");
         prefEdit.putString("+1234", "Play App");
         prefEdit.putString("+1235", "Say App");
         prefEdit.putString("+1236", "Gather App");
         prefEdit.putString("+1310", "Conference App");
         prefEdit.putString("+1311", "Conference Admin App");

         prefEdit.putBoolean(PREFS_CONTACTS_INIT_KEY, true);
         prefEdit.apply();
      }

      ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
      Map<String, ?> contacts = prefsContacts.getAll();
      for (Map.Entry<String, ?> entry : contacts.entrySet()) {
         if (!entry.getKey().equals(PREFS_CONTACTS_INIT_KEY)) {
            list.add(createEntry(entry.getKey(), (String) entry.getValue()));
         }
      }
      return list;
      */
      return DatabaseManager.getInstance().retrieveContacts();
   }

   // Adds contact to a. the db and b. to the given list (which is the backing store for the ListView)
   void addContact(ArrayList<Map<String, String>> list, String username, String sipuri) throws Exception
   {
      try {
         DatabaseManager.getInstance().addContact(username, sipuri);
      }
      catch (SQLException e) {
         if (e.getMessage().contains("UNIQUE constraint failed")) {
            throw new Exception("Contact already exists", e);
         }
         else {
            throw new Exception(e.getMessage(), e);
         }
      }

      list.add(createEntry(sipuri, username));
   }

   // Updates contact to a. the preferences data store and b. to the given list
   public int updateContact(ArrayList<Map<String, String>> list, String username, String sipuri)
   {
      int rowIndex = DatabaseManager.getInstance().updateContact(username, sipuri);
      if (rowIndex != -1) {
         HashMap<String, String> map = new HashMap<String, String>();
         map.put(CONTACT_KEY, username);
         map.put(CONTACT_VALUE, sipuri);

         list.set(rowIndex, map);
      }

      return rowIndex;
   }

   // Removes a contact from a. the preferences data store and b. to the given list
   int removeContact(ArrayList<Map<String, String>> list, String username, String sipuri)
   {
      int rowIndex = DatabaseManager.getInstance().removeContact(username, sipuri);
      if (rowIndex != -1) {
         HashMap<String, String> map = new HashMap<String, String>();
         map.put(CONTACT_KEY, username);
         map.put(CONTACT_VALUE, sipuri);

         list.remove(rowIndex);
      }

      return rowIndex;

      /*
      if (!prefsContacts.getString(sipuri, "not found").equals("not found")) {
         SharedPreferences.Editor prefEdit = prefsContacts.edit();
         prefEdit.remove(sipuri);
         prefEdit.apply();
      }
      else {
         Log.w(TAG, "removeContact(): contact not found in ListView adapter list: " + username + ", " + sipuri);
         return;
      }

      HashMap<String, String> map = new HashMap<String, String>();
      map.put("sipuri", sipuri);
      map.put("username", username);

      int index = 0;
      if ((index = list.indexOf(map)) != -1) {
         list.remove(index);
      }
      else {
         Log.w(TAG, "removeContact(): contact not found in ListView adapter list: " + username + ", " + sipuri);
         return;
      }
      */
   }

   // Helpers
   private HashMap<String, String> createEntry(String sipuri, String username)
   {
      HashMap<String, String> item = new HashMap<String, String>();
      item.put(CONTACT_VALUE, sipuri);
      item.put(CONTACT_KEY, username);
      return item;
   }

}
