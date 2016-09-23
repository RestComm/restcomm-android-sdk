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
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.SyncStateContract;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import com.testfairy.TestFairy;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.RCPresenceEvent;

import java.util.HashMap;

import static org.restcomm.android.olympus.ContactsController.CONTACT_KEY;
import static org.restcomm.android.olympus.ContactsController.CONTACT_VALUE;

public class MainActivity extends AppCompatActivity
      implements MainFragment.Callbacks, RCDeviceListener,
      View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener,
      AddUserDialogFragment.ContactDialogListener, ServiceConnection {

   private RCDevice device = null;
   boolean serviceBound = false;

   private static final String TAG = "MainActivity";
   SharedPreferences prefs;
   private HashMap<String, Object> params;
   private MainFragment listFragment;
   private AlertDialog alertDialog;
   private RCConnectivityStatus previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusNone;
   private static final String APP_VERSION = "Restcomm Android Olympus Client " + BuildConfig.VERSION_NAME + "#" + BuildConfig.VERSION_CODE; //"Restcomm Android Olympus Client 1.0.0-BETA4#20";
   FloatingActionButton btnAdd;
   public static String ACTION_DISCONNECTED_BACKGROUND = "org.restcomm.android.olympus.ACTION_DISCONNECTED_BACKGROUND";

   private static final int CONNECTION_REQUEST = 1;

   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      Log.i(TAG, "%% onCreate");
      Log.i(TAG, "Olympus Version: " + APP_VERSION);

      // For TestFairy troubleshooting. IMPORTANT: remove for production apps, as TestFairy sends logs, screenshots, etc to their servers
      TestFairy.begin(this, "52d3ee1de1b7681d1fcbbc3295b2b86a0aa43fcc");

      Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
      setSupportActionBar(toolbar);
      // TODO set proper image at xhdpi with 48x48
      toolbar.setNavigationIcon(R.drawable.bar_icon_24dp);
      toolbar.setTitle(getTitle());

      listFragment = (MainFragment) getSupportFragmentManager().findFragmentById(R.id.item_list);

      btnAdd = (FloatingActionButton) findViewById(R.id.imageButton_add);
      btnAdd.setOnClickListener(this);

      alertDialog = new AlertDialog.Builder(MainActivity.this, R.style.SimpleAlertStyle).create();

      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      prefs = PreferenceManager.getDefaultSharedPreferences(this);

      // preferences
      prefs.registerOnSharedPreferenceChangeListener(this);

      // No longer needed, we'll change with toast
      // set it to wifi by default to avoid the status message when starting with wifi
      //previousConnectivityStatus = RCConnectivityStatus.RCConnectivityStatusWiFi;
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

      // The activity has become visible (it is now "resumed").
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
   }

   @Override
   protected void onStop()
   {
      super.onStop();
      // The activity is no longer visible (it is now "stopped")
      Log.i(TAG, "%% onStop");

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
      /*
      RCClient.shutdown();
      device = null;
      */
      prefs.unregisterOnSharedPreferenceChangeListener(this);
   }

   @Override
   public void onNewIntent(Intent intent)
   {
      super.onNewIntent(intent);

      // We get this intent from CallActivity, when the App is in the background and the user has requested hangup via notification
      // In that case we don't wont to interrupt the user from what they are currently doing in the foreground, so we just finish()
      if (intent.getAction().equals(ACTION_DISCONNECTED_BACKGROUND)) {
         finish();
      }
   }

   // Callbacks for service binding, passed to bindService()
   @Override
   public void onServiceConnected(ComponentName className, IBinder service)
   {
      Log.i(TAG, "%% onServiceConnected");
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      RCDevice.RCDeviceBinder binder = (RCDevice.RCDeviceBinder) service;
      device = binder.getService();

      if (!device.isInitialized()) {
         HashMap<String, Object> params = new HashMap<String, Object>();
         params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, getApplicationContext(), CallActivity.class));
         params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, getApplicationContext(), MessageActivity.class));
         params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, ""));
         params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk"));
         params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, prefs.getString(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234"));
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_URL, ""));
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ""));
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ""));
         params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));
         params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, true));
         // The SDK provides the user with default sounds for calling, ringing, busy (declined) and message, but the user can override them
         // by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them with Resource IDs like R.raw.user_provided_calling_sound
         //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.user_provided_calling_sound);
         //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.user_provided_ringing_sound);
         //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.user_provided_declined_sound);
         //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.user_provided_message_sound);

         // These are for debugging purposes, NOT for release builds!
         //params.put(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED, true));
         //params.put(RCDevice.ParameterKeys.DEBUG_JAIN_DISABLE_CERTIFICATE_VERIFICATION, prefs.getBoolean(RCDevice.ParameterKeys.DEBUG_JAIN_DISABLE_CERTIFICATE_VERIFICATION, true));

         device.initialize(getApplicationContext(), params, this);
         device.setLogLevel(Log.VERBOSE);
      }

      if (device.getState() == RCDevice.DeviceState.OFFLINE) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
         handleExternalCall();
      }

      serviceBound = true;
   }

   @Override
   public void onServiceDisconnected(ComponentName arg0)
   {
      Log.i(TAG, "%% onServiceDisconnected");
      serviceBound = false;
   }

   /**
    * MainFragment Callbacks
    */
   @Override
   public void onItemSelected(HashMap<String, String> contact, MainFragment.ContactSelectionType type)
   {
      Intent intent = new Intent(this, MessageActivity.class);
      intent.setAction(MessageActivity.ACTION_OPEN_MESSAGE_SCREEN);
      intent.putExtra(MessageActivity.EXTRA_CONTACT_NAME, contact.get(CONTACT_KEY));
      intent.putExtra(RCDevice.EXTRA_DID, contact.get(CONTACT_VALUE));
      startActivity(intent);

      // forward to onActionClicked
      //onActionClicked(ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL, contact.get(CONTACT_KEY), contact.get(CONTACT_VALUE));
   }

   public void onContactUpdate(HashMap<String, String> contact, int type)
   {
      AddUserDialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT, contact.get(CONTACT_KEY), contact.get(CONTACT_VALUE));
      newFragment.show(getSupportFragmentManager(), "dialog");
      //newFragment.show(getFragmentManager(), "dialog");
   }

   /*
   public void onAccessoryClicked(HashMap<String, String> contact)
   {
      DialogFragment actionFragment = ActionFragment.newInstance(contact.get(CONTACT_KEY), contact.get(CONTACT_VALUE));
      actionFragment.show(getFragmentManager(), "dialog-accessory");
   }
   */

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
   /*
   public void onActionClicked(ActionFragment.ActionType action, String username, String sipuri)
   {
      if (action == ActionFragment.ActionType.ACTION_TYPE_VIDEO_CALL) {
         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.ACTION_OUTGOING_CALL);
         intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
         startActivityForResult(intent, CONNECTION_REQUEST);
      }
      if (action == ActionFragment.ActionType.ACTION_TYPE_AUDIO_CALL) {
         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.ACTION_OUTGOING_CALL);
         intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
         startActivityForResult(intent, CONNECTION_REQUEST);
      }
      if (action == ActionFragment.ActionType.ACTION_TYPE_TEXT_MESSAGE) {
         Intent intent = new Intent(this, MessageActivity.class);
         intent.setAction(MessageActivity.ACTION_OPEN_MESSAGE_SCREEN);
         intent.putExtra(RCDevice.EXTRA_DID, sipuri);
         startActivity(intent);
      }
   }
   */

   /**
    * Main Activity onClick
    */
   public void onClick(View view)
   {
      if (view.getId() == R.id.imageButton_add) {
         /* TODO: Issue #380: once we figure out the issue with the backgrounding we need to uncomment this, but also place it to a suitable place
         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.LIVE_CALL);
         startActivityForResult(intent, CONNECTION_REQUEST);
         */
         //DialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT, "", "");
         //newFragment.show(getFragmentManager(), "dialog");

         AddUserDialogFragment newFragment = AddUserDialogFragment.newInstance(AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT, "", "");
         newFragment.show(getSupportFragmentManager(), "dialog");
      }
   }

   /**
    * RCDeviceListener callbacks
    */
   public void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      handleConnectivityUpdate(connectivityStatus, null);
   }

   public void onStopListening(RCDevice device)
   {

   }

   public void onStopListening(RCDevice device, int errorCode, String errorText)
   {
      if (errorCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice: " + errorText);
      }
      else {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice Error: " + errorText);
      }
   }

   public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      if (statusCode == RCClient.ErrorCodes.SUCCESS.ordinal()) {
         handleConnectivityUpdate(connectivityStatus, "RCDevice successfully initialized, using: " + connectivityStatus);
      }
      else if (statusCode == RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY.ordinal()) {
         // This is not really an error, since if connectivity comes back the RCDevice will resume automatically
         handleConnectivityUpdate(connectivityStatus, null);
      }
      else {
         //Toast.makeText(getApplicationContext(), "RCDevice Initialization Error: " + statusText, Toast.LENGTH_LONG).show();
         //showOkAlert("RCDevice Initialization Error", statusText);
         //handleConnectivityUpdate(connectivityStatus, "RCDevice Initialization Error: " + statusText);
         Toast.makeText(getApplicationContext(), "RCDevice Initialization Error: " + statusText, Toast.LENGTH_LONG).show();
      }

   }

   public void onInitializationError(int errorCode, String errorText)
   {
      Toast.makeText(getApplicationContext(), "RCDevice Initialization Error: " + errorText, Toast.LENGTH_LONG).show();
   }

   public void onReleased(RCDevice device, int statusCode, String statusText)
   {
      if (statusCode != RCClient.ErrorCodes.SUCCESS.ordinal()) {
         //showOkAlert("RCDevice Release Error", statusText);
         Toast.makeText(getApplicationContext(), "RCDevice Release Error: " + statusText, Toast.LENGTH_LONG).show();
      }
      else {
         handleConnectivityUpdate(RCConnectivityStatus.RCConnectivityStatusNone, "RCDevice Released: " + statusText);
      }
   }

   public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
   {
      handleConnectivityUpdate(connectivityStatus, null);
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
      }

      if (connectivityStatus == RCConnectivityStatus.RCConnectivityStatusNone) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
         handleExternalCall();
      }

      Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
      this.previousConnectivityStatus = connectivityStatus;
   }

   // Handle call issued by external App via CALL intent
   private void handleExternalCall()
   {
      // We have connectivity (either wifi or cellular), check if we have any external call requests and service it
      GlobalPreferences globalPreferences = new GlobalPreferences(getApplicationContext());
      String externalCallUriString = globalPreferences.getExternalCallUri();
      if (!externalCallUriString.isEmpty()) {
         Uri externalCallUri = Uri.parse(externalCallUriString);

         String parsedUriString = "";
         if (externalCallUri.getScheme().contains("sip")) {
            // either 'sip' or 'restcomm-sip'
            // normalize 'restcomm-sip' and replace with 'sip'
            String normalized =  externalCallUriString.replace("restcomm-sip", "sip"); //[[uri absoluteString] stringByReplacingOccurrencesOfString:@"restcomm-sip" withString:@"sip"];
            // also replace '://' with ':' so that the SIP stack can understand it
            parsedUriString = normalized.replace("://", ":");
         }
         else {
            // either 'tel', 'restcomm-tel', 'client' or 'restcomm-client'. Return just the host part, like 'bob' or '1235' that the Restcomm SDK can handle
            parsedUriString = externalCallUri.getHost();
         }

         Intent intent = new Intent(this, CallActivity.class);
         intent.setAction(RCDevice.ACTION_OUTGOING_CALL);
         intent.putExtra(RCDevice.EXTRA_DID, parsedUriString);
         intent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, true);
         startActivityForResult(intent, CONNECTION_REQUEST);

         globalPreferences.setExternalCallUri("");
      }
   }

   public void onMessageSent(RCDevice device, int statusCode, String statusText)
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
    * Settings Menu callbacks
    */
   @Override
   public void onConfigurationChanged(Configuration newConfig)
   {
      super.onConfigurationChanged(newConfig);
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu)
   {
      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(R.menu.menu_main, menu);
      return true;
   }


   @Override
   public boolean onOptionsItemSelected(MenuItem item)
   {
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
                                         String key)
   {

   }


   /**
    * Helpers
    */
   /*
   private void showOkAlert(final String title, final String detail)
   {
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
   */

}

