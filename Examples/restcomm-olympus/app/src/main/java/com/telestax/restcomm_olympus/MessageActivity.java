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
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;

public class MessageActivity extends AppCompatActivity
        implements MessageFragment.Callbacks, RCDeviceListener,
        View.OnClickListener {

    private RCDevice device;
    HashMap<String, Object> params = new HashMap<String, Object>();
    private static final String TAG = "MessageActivity";
    private AlertDialog alertDialog;

    ImageButton btnSend;
    EditText txtMessage;


    private MessageFragment listFragment;

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        Toolbar toolbar = (Toolbar) findViewById(R.id.message_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        listFragment = (MessageFragment)getSupportFragmentManager().findFragmentById(R.id.message_list);

        alertDialog = new AlertDialog.Builder(MessageActivity.this).create();

        btnSend = (ImageButton)findViewById(R.id.button_send);
        btnSend.setOnClickListener(this);
        txtMessage = (EditText)findViewById(R.id.text_message);
        txtMessage.setOnClickListener(this);
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

        // retrieve the device
        device = RCClient.listDevices().get(0);

        if (device.getState() == RCDevice.DeviceState.OFFLINE) {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
        }
        else {
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
        }
        // Get Intent parameters.
        final Intent finalIntent = getIntent();
        if (finalIntent.getAction().equals(RCDevice.OPEN_MESSAGE_SCREEN)) {
            params.put("username", finalIntent.getStringExtra(RCDevice.EXTRA_DID));
            String shortname = finalIntent.getStringExtra(RCDevice.EXTRA_DID).replaceAll("^sip:", "").replaceAll("@.*$", "");
            setTitle(shortname);
        }
        if (finalIntent.getAction().equals(RCDevice.INCOMING_MESSAGE)) {
            String message = finalIntent.getStringExtra(RCDevice.INCOMING_MESSAGE_TEXT);
            HashMap<String, String> intentParams = (HashMap<String, String>) finalIntent.getSerializableExtra(RCDevice.INCOMING_MESSAGE_PARAMS);
            String username = intentParams.get("username");
            String shortname = username.replaceAll("^sip:", "").replaceAll("@.*$", "");
            params.put("username", username);

            listFragment.addRemoteMessage(message, shortname);
            setTitle(shortname);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Another activity is taking focus (this activity is about to be "paused").
        Log.i(TAG, "%% onPause");
        Intent intent = getIntent();
        // #129: clear the action so that on subsequent indirect activity open (i.e. via lock & unlock) we don't get the old action firing again
        intent.setAction("CLEAR_ACTION");
        setIntent(intent);
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
    }

    // We 've set MessageActivity to be 'singleTop' on the manifest to be able to receive messages while already open, without instantiating
    // a new activity. When that happens we receive onNewIntent()
    // An activity will always be paused before receiving a new intent, so you can count on onResume() being called after this method
    @Override
    public void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        // Note that getIntent() still returns the original Intent. You can use setIntent(Intent) to update it to this new Intent.
        setIntent(intent);
    }

    /**
     * Main Activity onClick
     */
    public void onClick(View view) {
        if (view.getId() == R.id.button_send) {
            HashMap<String, String> sendParams = new HashMap<String, String>();
            sendParams.put("username", (String)params.get("username"));
            if (device.sendMessage(txtMessage.getText().toString(), sendParams)) {
                // also output the message in the wall
                listFragment.addLocalMessage(txtMessage.getText().toString());
                txtMessage.setText("");
                //txtWall.append("Me: " + txtMessage.getText().toString() + "\n\n");
            }
            else {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }
        }
    }

    /**
     * RCDeviceListener callbacks
     */
    public void onStartListening(RCDevice device) {

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
        
    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

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
