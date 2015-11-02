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


package com.telestax.restcommmessenger;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a ItemDetailActivity representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p/>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link MainFragment} and the item details
 * (if present) is a ItemDetailFragment.
 * <p/>
 * This activity also implements the required
 * {@link MainFragment.Callbacks} interface
 * to listen for item selections.
 */
public class MessageActivity extends AppCompatActivity
        implements MessageFragment.Callbacks, RCDeviceListener,
        View.OnClickListener {

    private RCDevice device;
    HashMap<String, Object> params = new HashMap<String, Object>();
    private static final String TAG = "MessageActivity";

    Button btnSend;
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

        //btnAdd = (ImageButton)findViewById(R.id.imageButton_add);
        //btnAdd.setOnClickListener(this);
        //FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        //fab.setOnClickListener(this);
        btnSend = (Button)findViewById(R.id.button_send);
        btnSend.setOnClickListener(this);
        //txtWall = (TextView) findViewById(R.id.text_wall);
        //txtWall.setOnClickListener(this);
        //txtWall.setMovementMethod(new ScrollingMovementMethod());
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

        // Get Intent parameters.
        final Intent finalIntent = getIntent();
        if (finalIntent.getAction().equals(RCDevice.OPEN_MESSAGE_SCREEN)) {
            params.put("username", finalIntent.getStringExtra(RCDevice.EXTRA_DID));
        }
        if (finalIntent.getAction().equals(RCDevice.INCOMING_MESSAGE)) {
            String message = finalIntent.getStringExtra(RCDevice.INCOMING_MESSAGE_TEXT);
            HashMap<String, String> intentParams = (HashMap<String, String>) finalIntent.getSerializableExtra(RCDevice.INCOMING_MESSAGE_PARAMS);
            String username = intentParams.get("username");
            String shortname = username.replaceAll("^sip:", "").replaceAll("@.*$", "");
            params.put("username", username);

            listFragment.addRemoteMessage(message, username);
            //txtWall.append(shortname + ": " + message + "\n\n");
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
     * MainFragment Callbacks
     */
    /*
    @Override
    public void onItemSelected(HashMap<String, String> contact, MainFragment.ContactSelectionType type) {
        if (type == MainFragment.ContactSelectionType.VIDEO_CALL) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, contact.get("sipuri"));
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        if (type == MainFragment.ContactSelectionType.AUDIO_CALL) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, contact.get("sipuri"));
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
            startActivityForResult(intent, CONNECTION_REQUEST);
        }
        if (type == MainFragment.ContactSelectionType.TEXT_MESSAGE) {
            Intent intent = new Intent(this, MessageActivity.class);
            intent.setAction(RCDevice.OPEN_MESSAGE_SCREEN);
            intent.putExtra(RCDevice.EXTRA_DID, contact.get("sipuri"));
            startActivity(intent);
        }
    }

    public void onContactUpdate(HashMap<String, String> contact, int type)
    {
        DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT, contact.get("username"), contact.get("sipuri"));
        newFragment.show(getFragmentManager(), "dialog");
    }
    */

    /**
     * Callbacks for AddUserDialogFragment
     */
    /*
    public void onDialogPositiveClick(int type, String username, String sipuri)
    {
        listFragment.updateContact(type, username, sipuri);
    }

    public void onDialogNegativeClick() {

    }
    */

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
                //txtWall.append("Me: " + txtMessage.getText().toString() + "\n\n");
                //txtMessage.setText("");
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
        AlertDialog alertDialog = new AlertDialog.Builder(MessageActivity.this).create();
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

/*
package com.telestax.restcommmessenger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDevice;

import java.util.HashMap;


public class MessageActivity extends Activity implements View.OnClickListener {

    private RCDevice device;
    HashMap<String, Object> params = new HashMap<String, Object>();
    private static final String TAG = "MessageActivity";

    Button btnSend;
    EditText txtMessage;
    TextView txtWall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        btnSend = (Button)findViewById(R.id.button_send);
        btnSend.setOnClickListener(this);
        txtWall = (TextView) findViewById(R.id.text_wall);
        txtWall.setOnClickListener(this);
        txtWall.setMovementMethod(new ScrollingMovementMethod());
        txtMessage = (EditText)findViewById(R.id.text_message);
        txtMessage.setOnClickListener(this);

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

    @Override
    public void onResume()
    {
        super.onResume();

        // retrieve the device
        device = RCClient.listDevices().get(0);

        // Get Intent parameters.
        final Intent finalIntent = getIntent();
        if (finalIntent.getAction().equals(RCDevice.OPEN_MESSAGE_SCREEN)) {
            params.put("username", finalIntent.getStringExtra(RCDevice.EXTRA_DID));
        }
        if (finalIntent.getAction().equals(RCDevice.INCOMING_MESSAGE)) {
            String message = finalIntent.getStringExtra(RCDevice.INCOMING_MESSAGE_TEXT);
            HashMap<String, String> intentParams = (HashMap<String, String>) finalIntent.getSerializableExtra(RCDevice.INCOMING_MESSAGE_PARAMS);
            String username = intentParams.get("username");
            String shortname = username.replaceAll("^sip:", "").replaceAll("@.*$", "");
            params.put("username", username);

            txtWall.append(shortname + ": " + message + "\n\n");
        }
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_send) {
            HashMap<String, String> sendParams = new HashMap<String, String>();
            sendParams.put("username", (String)params.get("username"));
            if (device.sendMessage(txtMessage.getText().toString(), sendParams)) {
                // also output the message in the wall
                txtWall.append("Me: " + txtMessage.getText().toString() + "\n\n");
                txtMessage.setText("");
            }
            else {
                showOkAlert("RCDevice Error", "No Wifi connectivity");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    // Helpers
    private void showOkAlert(final String title, final String detail) {
        AlertDialog alertDialog = new AlertDialog.Builder(MessageActivity.this).create();
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
*/