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

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity
        implements MainFragment.Callbacks, RCDeviceListener,
        View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
        AddUserDialogFragment.ContactDialogListener, ActionFragment.ActionListener {

    private static final String TAG = "MainActivity";
    SharedPreferences prefs;
    private RCDevice device;
    private HashMap<String, Object> params;
    private MainFragment listFragment;
    private AlertDialog alertDialog;
    private RCConnectivityStatus previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusWiFi;

    ImageButton btnAdd;

    private static final int CONNECTION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        listFragment = (MainFragment)getSupportFragmentManager().findFragmentById(R.id.item_list);

        btnAdd = (ImageButton)findViewById(R.id.imageButton_add);
        btnAdd.setOnClickListener(this);

        alertDialog = new AlertDialog.Builder(MainActivity.this).create();

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
        params.put("pref_proxy_domain", prefs.getString("pref_proxy_domain", ""));
        params.put("pref_sip_user", prefs.getString("pref_sip_user", "android-sdk"));
        params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
        params.put("turn-enabled", prefs.getBoolean("turn-enabled", true));
        params.put("turn-url", prefs.getString("turn-url", ""));
        params.put("turn-username", prefs.getString("turn-username", ""));
        params.put("turn-password", prefs.getString("turn-password", ""));
        params.put("signaling-secure", prefs.getBoolean("signaling-secure", false));
        device = RCClient.createDevice(params, this);
        device.setPendingIntents(new Intent(getApplicationContext(), CallActivity.class),
                new Intent(getApplicationContext(), MessageActivity.class));

        // preferences
        prefs.registerOnSharedPreferenceChangeListener(this);

        // set it to wifi by default to avoid the status message when starting with wifi
        previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusWiFi;
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
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
        }
        else {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
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

    /**
     * MainFragment Callbacks
     */
    @Override
    public void onItemSelected(HashMap<String, String> contact, MainFragment.ContactSelectionType type) {
        // forward to onActionClicked
        onActionClicked(ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL, contact.get("username"), contact.get("sipuri"));
    }

    public void onContactUpdate(HashMap<String, String> contact, int type)
    {
        DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT, contact.get("username"), contact.get("sipuri"));
        newFragment.show(getFragmentManager(), "dialog");
    }

    public void onAccessoryClicked(HashMap<String, String> contact)
    {
        DialogFragment actionFragment = ActionFragment.newInstance(contact.get("username"), contact.get("sipuri"));
        actionFragment.show(getFragmentManager(), "dialog-accessory");
    }


    /**
     * Callbacks for AddUserDialogFragment
     */
    public void onDialogPositiveClick(int type, String username, String sipuri)
    {
        listFragment.updateContact(type, username, sipuri);
    }

    public void onDialogNegativeClick()
    {

    }

    /**
     * Callbacks for ActionFragment
     */
    public void onActionClicked(ActionFragment.ActionType action, String username, String sipuri)
    {
        if (action == ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, sipuri);
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        if (action == ActionFragment.ActionType.ACTION_TYPE_AUDIO_CALL) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, sipuri);
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        if (action == ActionFragment.ActionType.ACTION_TYPE_TEXT_MESSAGE) {
            Intent intent = new Intent(this, MessageActivity.class);
            intent.setAction(RCDevice.OPEN_MESSAGE_SCREEN);
            intent.putExtra(RCDevice.EXTRA_DID, sipuri);
            startActivity(intent);
        }
    }

    /**
     * Main Activity onClick
     */
    public void onClick(View view) {
        if (view.getId() == R.id.imageButton_add) {
            DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT, "", "");
            newFragment.show(getFragmentManager(), "dialog");
        }
    }

    /**
     * RCDeviceListener callbacks
     */
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

    public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
    {
        String text = "";
        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            text = "Lost connectivity";
        }
        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusWiFi) {
            text = "Reestablished connectivity (Wifi)";
        }
        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusCellular) {
            text = "Reestablished connectivity (Cellular)";
        }

        if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
        }
        else {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
        }

        if (connectivityStatus != this.previousConnectivityStatus) {
            showOkAlert("RCDevice connectivity change", text);
            this.previousConnectivityStatus = connectivityStatus;
        }
    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

    }

    /**
     * Settings Menu callbacks
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

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
        if (id == R.id.action_about) {
            DialogFragment newFragment = AboutFragment.newInstance();
            newFragment.show(getFragmentManager(), "dialog-about");
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        /*
        boolean updated = false;
        if (key.equals("pref_proxy_domain")) {
            params.put("pref_proxy_domain", prefs.getString("pref_proxy_domain", "sip:cloud.restcomm.com:5060"));
            updated = true;
        }
        else if (key.equals("pref_sip_user")) {
            params.put("pref_sip_user", prefs.getString("pref_sip_user", "android-sdk"));
            updated = true;
        }
        else if (key.equals("pref_sip_password")) {
            params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
            updated = true;
        }
        else if (key.equals("turn-enabled")) {
            params.put("turn-enabled", prefs.getBoolean("turn-enabled", true));
            updated = true;
        }
        else if (key.equals("turn-url")) {
            params.put("turn-url", prefs.getString("turn-url", ""));
            updated = true;
        }
        else if (key.equals("turn-username")) {
            params.put("turn-username", prefs.getString("turn-username", ""));
            updated = true;
        }
        else if (key.equals("turn-password")) {
            params.put("turn-password", prefs.getString("turn-password", ""));
            updated = true;
        }

        if (updated) {
            if (!device.updateParams(params)) {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }
        }
        */
    }

    /**
     * Helpers
     */
    private void showOkAlert(final String title, final String detail) {
        if (alertDialog.isShowing()) {
            Log.w(TAG, "Alert already showing, hiding to show new alert");
            alertDialog.hide();
        }

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

