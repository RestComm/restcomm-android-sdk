package com.telestax.restcomm_messenger;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.view.View.OnClickListener;
import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.ListActivity;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class ContactsActivity extends ListActivity implements RCDeviceListener,
        OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
        AddUserDialogFragment.ContactDialogListener {
    //private static final String PREFS_CONTACTS_NAME = "contacts.xml";
    //private static final String PREFS_CONTACTS_INIT_KEY = "prefs-initialized";
    private static final String TAG = "ContactsActivity";
    // webrtc
    private static final int CONNECTION_REQUEST = 1;

    SharedPreferences prefs;
    //SharedPreferences prefsContacts;
    private RCDevice device;
    private HashMap<String, Object> params;
    private ContactsController contactsController;
    private SimpleAdapter listViewAdapter;
    private ArrayList<Map<String, String>> contactList;

    ImageButton btnAdd;

    protected void onCreate(Bundle savedInstanceState)
    {
        Log.i(TAG, "%% onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        btnAdd = (ImageButton)findViewById(R.id.imageButton_add);
        btnAdd.setOnClickListener(this);

        contactsController = new ContactsController(getApplicationContext());
        contactList = contactsController.initializeContacts(); //populateContacts();
        String[] from = { "username", "sipuri" };
        int[] to = { android.R.id.text1, android.R.id.text2 };

        listViewAdapter = new SimpleAdapter(this, contactList,
                android.R.layout.simple_list_item_2, from, to);
        setListAdapter(listViewAdapter);
        registerForContextMenu(getListView());


        /*
        // initialize UI
        btnRegister = (Button)findViewById(R.id.button_register);
        btnRegister.setOnClickListener(this);
        btnDial = (Button)findViewById(R.id.button_dial);
        btnDial.setOnClickListener(this);
        btnDialAudio= (Button)findViewById(R.id.button_dial_audio);
        btnDialAudio.setOnClickListener(this);
        txtUri = (EditText)findViewById(R.id.text_uri);
        btnMessage = (Button)findViewById(R.id.button_message);
        btnMessage.setOnClickListener(this);
        */

        PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        RCClient.setLogLevel(Log.VERBOSE);
        RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener() {
            public void onInitialized() {
                Log.i(TAG, "RCClient initialized");
            }

            public void onError(Exception exception) {
                Log.e(TAG, "RCClient initialization error");
            }
        });

        params = new HashMap<String, Object>();
        params.put("pref_proxy_ip", prefs.getString("pref_proxy_ip", "23.23.228.238"));
        params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5080"));
        params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
        params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
        device = RCClient.createDevice(params, this);
        device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
                new Intent(getApplicationContext(), MessageActivity.class));

        // preferences
        prefs.registerOnSharedPreferenceChangeListener(this);

        //txtUri.setText("sip:1235@23.23.228.238:5080");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // The activity is about to become visible.
        Log.i(TAG, "%% onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (device.getState() == RCDevice.DeviceState.OFFLINE) {
            showOkAlert("No Connectivity", "No Wifi connectivity");
        }
        // The activity has become visible (it is now "resumed").
        Log.i(TAG, "%% onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Another activity is taking focus (this activity is about to be "paused").
        Log.i(TAG, "%% onPause");
    }
    @Override
    protected void onStop() {
        super.onStop();
        // The activity is no longer visible (it is now "stopped")
        Log.i(TAG, "%% onStop");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The activity is about to be destroyed.
        Log.i(TAG, "%% onDestroy");
        RCClient.shutdown();
        device = null;
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
    {
        // Do something when a list item is clicked
        HashMap item = (HashMap)getListView().getItemAtPosition(position);

        Intent intent = new Intent(this, CallActivity.class);
        intent.setAction(RCDevice.OUTGOING_CALL);
        intent.putExtra(RCDevice.EXTRA_DID, (String) item.get("sipuri"));  //txtUri.getText().toString());
        intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
        startActivityForResult(intent, CONNECTION_REQUEST);
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.imageButton_add) {
            DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT, "", "");
            newFragment.show(getFragmentManager(), "dialog");
        }

        /*
        if (view.getId() == R.id.button_dial || view.getId() == R.id.button_dial_audio) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, txtUri.getText().toString());
            if (view.getId() == R.id.button_dial_audio) {
                intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
            } else {
                intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
            }
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        else if (view.getId() == R.id.button_register) {
            if (!device.updateParams(params)) {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }

        }
        else if (view.getId() == R.id.button_message) {
            Intent intent = new Intent(this, MessageActivity.class);
            intent.setAction(RCDevice.OPEN_MESSAGE_SCREEN);
            intent.putExtra(RCDevice.EXTRA_DID, txtUri.getText().toString());
            startActivity(intent);
        }
        else if (view.getId() == R.id.button_listen) {
            device.listen();
        }
        else if (view.getId() == R.id.button_unlisten) {
            device.unlisten();
        }
        else if (view.getId() == R.id.button_init) {
            device = RCClient.createDevice(params, this);
            device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
                    new Intent(getApplicationContext(), MessageActivity.class));
        }
        else if (view.getId() == R.id.button_shutdown) {
            RCClient.shutdown();
            device = null;
        }
        */
    }

    // RCDevice Listeners
    public void onStartListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device, int errorCode, String errorText)
    {
        showOkAlert("RCDevice Error", errorText);
        /*
        if (errorCode == RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal()) {
            showOkAlert("No Wifi Connectivity", errorText);
        }
        else if (errorCode == RCClient.ErrorCodes.GENERIC_ERROR.ordinal()) {
            showOkAlert("Generic Error", errorText);
        }
        else {
            showOkAlert("Unknown Error", "Unknown Restcomm Client error");
        }
        */
    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    // Settings Menu stuff
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        boolean updated = false;
        if (key.equals("pref_proxy_ip")) {
            params.put("pref_proxy_ip", prefs.getString("pref_proxy_ip", "23.23.228.238"));
            updated = true;
        } else if (key.equals("pref_proxy_port")) {
            params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5060"));
            updated = true;
        } else if (key.equals("pref_sip_user")) {
            params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
            updated = true;
        } else if (key.equals("pref_sip_password")) {
            params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
            updated = true;
        }
        if (updated) {
            if (!device.updateParams(params)) {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }
        }
    }

    // Context Menu stuff
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == android.R.id.list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle("Contact Actions");
            menu.add("Update Contact");
            menu.add("Remove Contact");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        HashMap<String, String> contact = (HashMap)contactList.get(info.position);

        if (item.getTitle().toString().equals("Update Contact")) {
            DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT, contact.get("username"), contact.get("sipuri"));
            newFragment.show(getFragmentManager(), "dialog");
        }
        else {
            contactsController.removeContact(contactList, contact.get("username"), contact.get("sipuri"));
            this.listViewAdapter.notifyDataSetChanged();
        }
        return true;
    }

    // Callbacks for contacts dialog fragment
    public void onDialogPositiveClick(int type, String username, String sipuri)
    {
        if (type == AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT) {
            if (username.isEmpty() || sipuri.isEmpty()) {
                showOkAlert("Addition Cancelled", "Both Username and SIP URI fields must be provided");
                return;
            }
            else {
                this.contactsController.addContact(contactList, username, sipuri);
            }
        }
        else {
            this.contactsController.updateContact(contactList, username, sipuri);
        }
        // notify adapter that ListView needs to be updated
        this.listViewAdapter.notifyDataSetChanged();
    }

    public void onDialogNegativeClick()
    {

    }

    // Helper methods
    private void showOkAlert(final String title, final String detail) {
        AlertDialog alertDialog = new AlertDialog.Builder(ContactsActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(detail);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alertDialog.show();
    }
}
