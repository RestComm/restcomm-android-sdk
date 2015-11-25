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
            prefEdit.putString("sip:alice@telestax.com", "Alice");
            prefEdit.putString("sip:bob@telestax.com", "Bob");
            prefEdit.putString("sip:1235@telestax.com", "Hello World App");
            prefEdit.putString("sip:1311@telestax.com", "Conference App");
            prefEdit.putString("sip:+15126001502@telestax.com", "Team Call");
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
        /*
        int index = 0;
        if ((index = list.indexOf(map)) != -1) {
            list.set(index, map);
        }
        else {
            Log.w(TAG, "addContact(): contact not found in ListView adapter list: " + username + ", " + sipuri);
        }
        */
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
