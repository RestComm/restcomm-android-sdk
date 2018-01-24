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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.restcomm.android.olympus.Util.Utils;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.util.RCException;

import java.util.HashMap;
import java.util.Locale;

public class MessageActivity extends AppCompatActivity
      implements MessageFragment.Callbacks, RCDeviceListener,
      View.OnClickListener, ServiceConnection {

   private RCDevice device;
   boolean serviceBound = false;
   HashMap<String, Object> params = new HashMap<String, Object>();
   // keep around for each jobId that creates a message the index it gets inside the ListView
   //HashMap<String, Integer> indexes = new HashMap<String, Integer>();
   private static final String TAG = "MessageActivity";
   private AlertDialog alertDialog;
   private String currentPeer;
   private String fullPeer;

   // Timer that starts if there's a live call on another Activity and periodically checks if the call is over to update the UI
   // TODO: need to improve this from polling to event based but we need to think in terms of general SDK API. Let's leave it like
   // this for now and we will revisit
   private Handler timerHandler = new Handler();

   ImageButton btnSend;
   EditText txtMessage;
   TextView lblOngoingCall;
   public static String ACTION_OPEN_MESSAGE_SCREEN = "org.restcomm.android.olympus.ACTION_OPEN_MESSAGE_SCREEN";
   public static String EXTRA_CONTACT_NAME = "org.restcomm.android.olympus.EXTRA_CONTACT_NAME";

   private MessageFragment listFragment;

   /**
    * Whether or not the activity is in two-pane mode, i.e. running on a tablet
    * device.
    */
   private boolean mTwoPane;

   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
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

      listFragment = (MessageFragment) getSupportFragmentManager().findFragmentById(R.id.message_list);

      alertDialog = new AlertDialog.Builder(MessageActivity.this, R.style.SimpleAlertStyle).create();

      btnSend = (ImageButton) findViewById(R.id.button_send);
      btnSend.setOnClickListener(this);
      txtMessage = (EditText) findViewById(R.id.text_message);
      txtMessage.setOnClickListener(this);
      lblOngoingCall = findViewById(R.id.resume_call);
      lblOngoingCall.setOnClickListener(this);

      fullPeer = getIntent().getStringExtra(RCDevice.EXTRA_DID);
      // keep on note of the current peer we are texting with
      currentPeer = getIntent().getStringExtra(RCDevice.EXTRA_DID).replaceAll("^sip.?:", "").replaceAll("@.*$", "");
      setTitle(currentPeer);
   }

   @Override
   protected void onStart()
   {
      super.onStart();
      // The activity is about to become visible.
      Log.i(TAG, "%% onStart");

      bindService(new Intent(this, RCDevice.class), this, Context.BIND_AUTO_CREATE);
   }

   @Override
   protected void onResume()
   {
      super.onResume();
      Log.i(TAG, "%% onResume");

      if (device != null) {
         // needed if we are returning from Message screen that becomes the Device listener
         device.setDeviceListener(this);
      }
   }

   @Override
   protected void onPause()
   {
      super.onPause();
      // Another activity is taking focus (this activity is about to be "paused").
      Log.i(TAG, "%% onPause");
      Intent intent = getIntent();
      // #129: clear the action so that on subsequent indirect activity open (i.e. via lock & unlock) we don't get the old action firing again
      intent.setAction("CLEAR_ACTION");
      setIntent(intent);
   }

   @Override
   protected void onStop()
   {
      super.onStop();
      // The activity is no longer visible (it is now "stopped")
      Log.i(TAG, "%% onStop");

      if (lblOngoingCall.getVisibility() != View.GONE) {
         lblOngoingCall.setVisibility(View.GONE);

         if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
         }
      }

      // Unbind from the service
      if (serviceBound) {
         //device.detach();
         unbindService(this);
         serviceBound = false;
      }
   }

   @Override
   protected void onDestroy()
   {
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

      // if a mesage arrives after we have created activity it will land here
      handleMessage(intent);
   }

   // Callbacks for service binding, passed to bindService()
   @Override
   public void onServiceConnected(ComponentName className, IBinder service)
   {
      Log.i(TAG, "%% onServiceConnected");
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      RCDevice.RCDeviceBinder binder = (RCDevice.RCDeviceBinder) service;
      device = binder.getService();

      if (device.isInitialized()) {
         RCConnection connection = device.getLiveConnection();
         if (connection != null) {
            // we have a live connection ongoing, need to update UI so that it can be resumed
            lblOngoingCall.setText(String.format("%s %s", getString(R.string.resume_ongoing_call_text), connection.getPeer()));
            lblOngoingCall.setVisibility(View.VISIBLE);
            startTimer();
         }
      }
      else {
         Log.i(TAG, "RCDevice not initialized; initializing");
         PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
         SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

         HashMap<String, Object> params = Utils.createParameters(prefs, this);

         // If exception is raised, we will close activity only if it comes from login
         // otherwise we will just show the error dialog
         device.setLogLevel(Log.VERBOSE);
         try {
            device.initialize(getApplicationContext(), params, this);
         } catch (RCException e) {
            showOkAlert("RCDevice Initialization Error", e.errorText);
         }

      }

      // needed if we are returning from Message screen that becomes the Device listener
      device.setDeviceListener(this);

      // We have the device reference, let's handle the call
      handleMessage(getIntent());

      serviceBound = true;
   }

   @Override
   public void onServiceDisconnected(ComponentName arg0)
   {
      Log.i(TAG, "%% onServiceDisconnected");
      serviceBound = false;
   }

   private void handleMessage(Intent intent)
   {
      if (device.getState() == RCDevice.DeviceState.OFFLINE) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      // Get Intent parameters.
      if (intent.getAction().equals(ACTION_OPEN_MESSAGE_SCREEN)) {
         params.put(RCConnection.ParameterKeys.CONNECTION_PEER, intent.getStringExtra(RCDevice.EXTRA_DID));
         String shortname = intent.getStringExtra(RCDevice.EXTRA_DID).replaceAll("^sip.?:", "").replaceAll("@.*$", "");
         setTitle(shortname);
      }
      if (intent.getAction().equals(RCDevice.ACTION_INCOMING_MESSAGE)) {
         String message = intent.getStringExtra(RCDevice.EXTRA_MESSAGE_TEXT);
         //HashMap<String, String> intentParams = (HashMap<String, String>) finalIntent.getSerializableExtra(RCDevice.INCOMING_MESSAGE_PARAMS);
         //String username = intentParams.get(RCConnection.ParameterKeys.CONNECTION_PEER);
         String username = intent.getStringExtra(RCDevice.EXTRA_DID);
         String shortname = username.replaceAll("^sip.?:", "").replaceAll("@.*$", "");

         if (!shortname.equals(currentPeer)) {
            // message originating from another peer, not the one we are currently texting with, just update DB and show a Toast
            Toast.makeText(getApplicationContext(), "New text from \'" + shortname + "\': " + message, Toast.LENGTH_LONG).show();
            if (DatabaseManager.getInstance().addContactIfNeded(username)) {
               Toast.makeText(getApplicationContext(), "Adding '" + shortname + "\' to contacts as it doesn't exist", Toast.LENGTH_LONG).show();
            }
            //DatabaseManager.getInstance().addMessage(shortname, message, false);
            listFragment.addRemoteMessage(message, shortname);
            return;
         }

         params.put(RCConnection.ParameterKeys.CONNECTION_PEER, username);
         //setTitle(shortname);

         if (DatabaseManager.getInstance().addContactIfNeded(username)) {
            Toast.makeText(getApplicationContext(), "Text message sender not found; updating Contacts with \'" + shortname + "\'", Toast.LENGTH_LONG).show();
         }
         listFragment.addRemoteMessage(message, shortname);
      }
   }

   /**
    * Message Activity onClick
    */
   public void onClick(View view)
   {
      if (view.getId() == R.id.button_send) {
         HashMap<String, String> sendParams = new HashMap<String, String>();
         String connectionPeer = (String) params.get(RCConnection.ParameterKeys.CONNECTION_PEER);
         sendParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, connectionPeer);
         try {
            String jobId = device.sendMessage(txtMessage.getText().toString(), sendParams);
            // also output the message in the wall
            listFragment.addLocalMessage(txtMessage.getText().toString(), connectionPeer.replaceAll("^sip.?:", "").replaceAll("@.*$", ""),
                    jobId);
            //indexes.put(messageStatus.jobId, index);
            txtMessage.setText("");
            //txtWall.append("Me: " + txtMessage.getText().toString() + "\n\n");
         } catch (RCException e) {
            showOkAlert("RCDevice Error", "No Wifi connectivity");
         }
      }
      else if (view.getId() == R.id.resume_call) {
         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.ACTION_RESUME_CALL);
         startActivity(intent);
      }
   }

   /**
    * RCDeviceListener callbacks
    */
   public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      Log.i(TAG, "%% onInitialized");
      if (statusCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(connectivityStatus, "RCDevice successfully initialized, using: " + connectivityStatus);
      }
      else if (statusCode == RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY.ordinal()) {
         // This is not really an error, since if connectivity comes back the RCDevice will resume automatically
         handleConnectivityUpdate(connectivityStatus, null);
      }
      else {
         if (!isFinishing()) {
            showOkAlert("RCDevice Initialization Error", statusText);
         }
      }
   }

   public void onReconfigured(RCDevice device, RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      Log.i(TAG, "%% onReconfigured");
   }

   public void onError(RCDevice device, int errorCode, String errorText)
   {
      if (errorCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice: " + errorText);
      }
      else {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice Error: " + errorText);
      }
   }

   public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
   {
      Log.i(TAG, "%% onConnectivityUpdate");

      handleConnectivityUpdate(connectivityStatus, null);
   }

   public void onMessageSent(RCDevice device, int statusCode, String statusText, String jobId)
   {
      Log.i(TAG, "onMessageSent(): statusCode: " + statusCode + ", statusText: " + statusText);

      listFragment.updateMessageDeliveryStatus(jobId, statusCode, currentPeer);
   }

   public void onReleased(RCDevice device, int statusCode, String statusText)
   {
      if (statusCode != RCClient.ErrorCodes.SUCCESS.ordinal()) {
         showOkAlert("RCDevice Error", statusText);
      }

      //maybe we stopped the activity before onReleased is called
      if (serviceBound) {
         unbindService(this);
         serviceBound = false;
      }
   }

   /**
    * Helpers
    */
   private void showOkAlert(final String title, final String detail)
   {
      Log.d(TAG, "Showing alert: " + title + ", detail: " + detail);

      if (alertDialog.isShowing()) {
         Log.w(TAG, "Alert already showing, hiding to show new alert");
         alertDialog.hide();
      }

      alertDialog.setTitle(title);
      alertDialog.setMessage(detail);
      alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int which)
         {
            dialog.dismiss();
         }
      });
      alertDialog.show();
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu)
   {
      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(R.menu.menu_message, menu);
      return true;
   }


   @Override
   public boolean onOptionsItemSelected(MenuItem item)
   {
      // Handle action bar item clicks here. The action bar will
      // automatically handle clicks on the Home/Up button, so long
      // as you specify a parent activity in AndroidManifest.xml.
      int id = item.getItemId();
      if (device.getState() == RCDevice.DeviceState.READY) {
         if (id == R.id.action_video_call) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.ACTION_OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, fullPeer);
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
            startActivity(intent);
         }
         if (id == R.id.action_audio_call) {
            Intent intent = new Intent(this, CallActivity.class);
            intent.setAction(RCDevice.ACTION_OUTGOING_CALL);
            intent.putExtra(RCDevice.EXTRA_DID, fullPeer);
            intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
            startActivity(intent);
         }
      }
      else if (device.getState() == RCDevice.DeviceState.BUSY) {
         showOkAlert("RCDevice is busy", "Call already ongoing, please hang up first if you want to start a new one");
      }

      return super.onOptionsItemSelected(item);
   }

   public void handleConnectivityUpdate(RCConnectivityStatus connectivityStatus, String text)
   {

      if (text == null) {
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
            text = "RCDevice connectivity change: Lost connectivity";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusWiFi) {
            text = "RCDevice connectivity change: Reestablished connectivity (Wifi)";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusCellular) {
            text = "RCDevice connectivity change: Reestablished connectivity (Cellular)";
         }
         if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusEthernet) {
            text = "RCDevice connectivity change: Reestablished connectivity (Ethernet)";
         }

      }

      if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
   }

   public void startTimer() {
      timerHandler.removeCallbacksAndMessages(null);
      // schedule a registration update after 'registrationRefresh' seconds
      Runnable timerRunnable = new Runnable() {
         @Override
         public void run() {
            if (device != null && device.isInitialized() && (device.getLiveConnection() == null)) {
               if (lblOngoingCall.getVisibility() != View.GONE) {
                  lblOngoingCall.setVisibility(View.GONE);
                  return;
               }
            }
            startTimer();
         }
      };
      timerHandler.postDelayed(timerRunnable, 1000);
   }

}
