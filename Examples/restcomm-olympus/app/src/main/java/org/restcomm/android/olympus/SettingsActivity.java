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
import android.os.IBinder;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.restcomm.android.olympus.Util.Utils;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;
//import org.restcomm.android.sdk.util.ErrorStruct;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.util.RCException;
import org.restcomm.android.sdk.util.RCUtils;

import java.util.HashMap;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener,
      ServiceConnection, RCDeviceListener {
   private SettingsFragment settingsFragment;
   SharedPreferences prefs;
   HashMap<String, Object> params;
   RCDevice device;
   boolean serviceBound = false;
   boolean updated;
   boolean pushUpdated;
   private AlertDialog alertDialog;
   private static final String TAG = "SettingsActivity";

   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_settings);

      Toolbar toolbar = (Toolbar) findViewById(R.id.settings_toolbar);
      setSupportActionBar(toolbar);
      toolbar.setTitle(getTitle());

      ActionBar actionBar = getSupportActionBar();
      if (actionBar != null) {
         // Show the Up button in the action bar.
         actionBar.setDisplayHomeAsUpEnabled(true);
      }

      // Display the fragment as the main content.
      settingsFragment = new SettingsFragment();
      getFragmentManager().beginTransaction().replace(R.id.content_frame, settingsFragment).commit();

      params = new HashMap<String, Object>();

      prefs = PreferenceManager.getDefaultSharedPreferences(this);
      prefs.registerOnSharedPreferenceChangeListener(this);

      alertDialog = new AlertDialog.Builder(SettingsActivity.this, R.style.SimpleAlertStyle).create();
   }

   protected void onResume()
   {
      super.onResume();

      Preference updatedPref = settingsFragment.findPreference(RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC);
      updatedPref.setSummary(prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC, ""));

      updatedPref = settingsFragment.findPreference(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC);
      updatedPref.setSummary(prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC, ""));

      updatedPref = settingsFragment.findPreference(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION);
      updatedPref.setSummary(prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION, ""));

      updatedPref = settingsFragment.findPreference(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE);
      updatedPref.setSummary(prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE, ""));

      updatedPref = settingsFragment.findPreference(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE);
      int iceServersDiscoveryType =  Integer.parseInt(prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, "0"));
      updatedPref.setSummary(getResources().getStringArray(R.array.ice_servers_discovery_types_entries)[iceServersDiscoveryType]);

      updatedPref = settingsFragment.findPreference(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT);
      //int candidateTimeout =  Integer.parseInt(prefs.getString(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT, "0"));
      //updatedPref.setSummary(getResources().getStringArray(R.array.candidate_timeout_entries)[candidateTimeout]);
      updatedPref.setSummary(candidateTimeoutValue2Summary(prefs.getString(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT, "0")));

      updated = false;
   }

   @Override
   protected void onStart()
   {
      super.onStart();
      Log.i(TAG, "%% onStart");

      //handleCall(getIntent());
      bindService(new Intent(this, RCDevice.class), this, Context.BIND_AUTO_CREATE);
   }

   @Override
   protected void onStop()
   {
      super.onStop();
      Log.i(TAG, "%% onStop");

      // Unbind from the service
      if (serviceBound) {
         //device.detach();
         unbindService(this);
         serviceBound = false;
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

      // Remember there's a chance that the user navigates to Settings and then hits home. In that case,
      // when they come back RCDevice won't be initialized
      if (!device.isInitialized()) {
         Log.i(TAG, "RCDevice not initialized; initializing");
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

      // We have the device reference
      if (device.getState() == RCDevice.DeviceState.OFFLINE) {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorTextSecondary)));
      }
      else {
         getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
      }

      serviceBound = true;
   }

   @Override
   public void onServiceDisconnected(ComponentName arg0)
   {
      Log.i(TAG, "%% onServiceDisconnected");
      serviceBound = false;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item)
   {
      // Handle action bar item clicks here. The action bar will
      // automatically handle clicks on the Home/Up button, so long
      // as you specify a parent activity in AndroidManifest.xml.
      int id = item.getItemId();
      if (id == android.R.id.home) {
         try {
            HashMap<String, Object> prefHashMap = (HashMap<String, Object>) prefs.getAll();
            if (updated || pushUpdated) {
                // There a slight difference between the data structure of SharedPreferences and
                // the one that the SDK understands. In SharedPreferences the value for
                // MEDIA_ICE_SERVERS_DISCOVERY_TYPE key is a String, which the SDK wants a
                // MediaIceServersDiscoveryType enum, so we need to convert between the 2.
                // In this case we remove the one and introduce the other
                String iceServersDiscoveryType = "0";
                if (prefHashMap.containsKey(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE)) {
                    iceServersDiscoveryType = (String) prefHashMap.get(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE);
                    prefHashMap.remove(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE);
                }
                prefHashMap.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE,
                        RCDevice.MediaIceServersDiscoveryType.values()[Integer.parseInt(iceServersDiscoveryType)]
                );

                // Same for candidate timeout
                String candidateTimeout = "0";
                if (prefHashMap.containsKey(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT)) {
                    candidateTimeout = (String) prefHashMap.get(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT);
                    prefHashMap.remove(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT);
                }
                prefHashMap.put(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT,
                        Integer.parseInt(candidateTimeout)
                );

                if (device.isInitialized()) {
                   device.reconfigure(params);
                } else {
                   //try to initialize with params
                   device.initialize(this, params, this);
                }

            }
            NavUtils.navigateUpFromSameTask(this);
         } catch (RCException e) {
            showOkAlert("Error saving Settings", e.errorText);
         }

         return true;
      }
      return super.onOptionsItemSelected(item);
   }

   @Override
   public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                         String key)
   {
      if (key.equals(RCDevice.ParameterKeys.SIGNALING_DOMAIN)) {
         params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "sip:cloud.restcomm.com:5060"));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.SIGNALING_USERNAME)) {
         params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk"));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.SIGNALING_PASSWORD)) {
         params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, prefs.getString(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234"));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED)) {
         params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.MEDIA_ICE_URL)) {
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_URL, ""));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME)) {
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ""));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD)) {
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ""));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN)) {
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ""));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED)) {
         params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false));
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE)) {
         params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE,
                 RCDevice.MediaIceServersDiscoveryType.values()[Integer.parseInt(prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, "0"))]);
         Preference updatedPref = settingsFragment.findPreference(key);
         if (updatedPref != null) {
            int iceServersDiscoveryType =  Integer.parseInt(prefs.getString(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, "0"));
            updatedPref.setSummary(getResources().getStringArray(R.array.ice_servers_discovery_types_entries)[iceServersDiscoveryType]);
         }
         updated = true;
      }
      else if (key.equals(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT)) {
         params.put(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT,
                 Integer.parseInt(prefs.getString(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT, "0")));
         Preference updatedPref = settingsFragment.findPreference(key);
         if (updatedPref != null) {
            //int candidateTimeout =  Integer.parseInt(prefs.getString(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT, "0"));
            //updatedPref.setSummary(getResources().getStringArray(R.array.candidate_timeout_entries)[candidateTimeout]);
            updatedPref.setSummary(candidateTimeoutValue2Summary(prefs.getString(RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT, "0")));
         }
         updated = true;
      }
      else if (key.equals(RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC)) {
         params.put(RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC, prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC, "Default"));
         Preference updatedPref = settingsFragment.findPreference(key);
         if (updatedPref != null) {
            updatedPref.setSummary(prefs.getString(key, ""));
         }
         updated = true;
      }
      else if (key.equals(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC)) {
         params.put(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC, prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC, "Default"));
         Preference updatedPref = settingsFragment.findPreference(key);
         if (updatedPref != null) {
            updatedPref.setSummary(prefs.getString(key, ""));
         }
         updated = true;
      }
      else if (key.equals(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION)) {
         params.put(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION, prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION, "Default"));
         Preference updatedPref = settingsFragment.findPreference(key);
         if (updatedPref != null) {
            updatedPref.setSummary(prefs.getString(key, ""));
         }
         updated = true;
      }
      else if (key.equals(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE)) {
         params.put(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE, prefs.getString(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE, "Default"));
         Preference updatedPref = settingsFragment.findPreference(key);
         if (updatedPref != null) {
            updatedPref.setSummary(prefs.getString(key, ""));
         }
         updated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT)) {
         params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, prefs.getBoolean(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT,false));
         pushUpdated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL)) {
         params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, ""));
         pushUpdated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD)) {
         params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, ""));
         pushUpdated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN)) {
         params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, ""));
         pushUpdated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN)) {
         params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, prefs.getString(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, ""));
         pushUpdated = true;
      }
      else if (key.equals(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT)) {
         params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, prefs.getBoolean(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false));
         updated = true;
      }

   }

   private void showOkAlert(final String title, final String detail)
   {
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

   private String candidateTimeoutValue2Summary(String value)
   {
      String summary = "No timeout";
      if (Integer.parseInt(value) > 0) {
         summary = value + " " + "seconds";
      }
      return summary;
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

}