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
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ContactsController {
    private static final String TAG = "ContactsController";
    private static final String PREFS_CONTACTS_NAME = "contacts.xml";
    private static final String PREFS_CONTACTS_INIT_KEY = "prefs-initialized";
    private Context context;

    SharedPreferences prefsContacts = null;

    ContactsController(Context context)
    {
        this.context = context;
    }

    // Check if default contact entries already exist in the data store (android preferences) and if not
    // add them
    ArrayList<Map<String, String>> initializeContacts()
    {
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
            prefEdit.putString("sip:+1234@cloud.restcomm.com", "Play App");
            prefEdit.putString("sip:+1235@cloud.restcomm.com", "Say App");
            prefEdit.putString("sip:+1236@cloud.restcomm.com", "Gather App");
            prefEdit.putString("sip:+1310@cloud.restcomm.com", "Conference App");
            prefEdit.putString("sip:+1311@cloud.restcomm.com", "Conference Admin App");

            prefEdit.putBoolean(PREFS_CONTACTS_INIT_KEY, true);
            prefEdit.commit();
        }

        ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
        Map<String, ?> contacts = prefsContacts.getAll();
        for (Map.Entry<String, ?> entry : contacts.entrySet()) {
            if (!entry.getKey().equals(PREFS_CONTACTS_INIT_KEY)) {
                list.add(createEntry(entry.getKey(), (String) entry.getValue()));
            }
        }
        return list;
    }

    // Adds contact to a. the preferences data store and b. to the given list (which is the backing store for the ListView)
    void addContact(ArrayList<Map<String, String>> list, String username, String sipuri)
    {
        if (prefsContacts.getString(sipuri, "not found").equals("not found")) {
            SharedPreferences.Editor prefEdit = prefsContacts.edit();
            prefEdit.putString(sipuri, username);
            prefEdit.commit();
        }
        else {
            Log.w(TAG, "addContact(): contact already exists: " + username + ", " + sipuri);
            return;
        }

        list.add(createEntry(sipuri, username));
    }

    // Updates contact to a. the preferences data store and b. to the given list
    void updateContact(ArrayList<Map<String, String>> list, String username, String sipuri)
    {
        if (!prefsContacts.getString(sipuri, "not found").equals("not found")) {
            SharedPreferences.Editor prefEdit = prefsContacts.edit();
            prefEdit.putString(sipuri, username);
            prefEdit.commit();
        }
        else {
            // TODO: we could add some error reporting at some point
            Log.w(TAG, "addContact(): contact not found: " + username + ", " + sipuri);
            return;
        }

        int index = 0;
        boolean found = false;
        for (Map<String, String> item : list) {
            if (item.containsValue(sipuri)) {
                found = true;
                break;
            }
            index++;
        }

        if (found) {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("sipuri", sipuri);
            map.put("username", username);
            list.set(index, map);
        }
    }

    // Removes a contact from a. the preferences data store and b. to the given list
    void removeContact(ArrayList<Map<String, String>> list, String username, String sipuri)
    {
        if (!prefsContacts.getString(sipuri, "not found").equals("not found")) {
            SharedPreferences.Editor prefEdit = prefsContacts.edit();
            prefEdit.remove(sipuri);
            prefEdit.commit();
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
    }

    // Helpers
    private HashMap<String, String> createEntry(String sipuri, String username)
    {
        HashMap<String, String> item = new HashMap<String, String>();
        item.put("sipuri", sipuri);
        item.put("username", username);
        return item;
    }

}
