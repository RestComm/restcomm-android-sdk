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

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;
//import org.restcomm.android.sdk.util.ErrorStruct;
import org.restcomm.android.sdk.util.RCException;
import org.restcomm.android.sdk.util.RCUtils;

import java.util.HashMap;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener,
      ServiceConnection {
   private SettingsFragment settingsFragment;
   SharedPreferences prefs;
   HashMap<String, Object> params;
   RCDevice device;
   boolean serviceBound = false;
   boolean updated;
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
         if (updated) {
            try {
               RCUtils.validatePreferenceParms((HashMap<String, Object>) prefs.getAll());
               if (!device.updateParams(params)) {
                  // TODO:
                  //showOkAlert("RCDevice Error", "No Wifi connectivity");
               }
               NavUtils.navigateUpFromSameTask(this);

            }
            catch (RCException e) {
               showOkAlert("Error saving Settings", e.errorText);
            }

            /*
            if (errorStruct.statusCode != RCClient.ErrorCodes.SUCCESS) {
               showOkAlert("Error saving Settings", errorStruct.statusText);
            }
            else {
               if (!device.updateParams(params)) {
                  // TODO:
                  //showOkAlert("RCDevice Error", "No Wifi connectivity");
               }
               NavUtils.navigateUpFromSameTask(this);
            }
            */
         }
         else {
            NavUtils.navigateUpFromSameTask(this);
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
}