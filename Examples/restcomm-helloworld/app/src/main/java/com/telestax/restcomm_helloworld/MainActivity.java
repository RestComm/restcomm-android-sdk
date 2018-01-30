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

package com.telestax.restcomm_helloworld;

//import android.support.v7.app.ActionBarActivity;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.view.View.OnClickListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ListIterator;

//import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCConnectionListener;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;
import org.restcomm.android.sdk.RCPresenceEvent;
import org.restcomm.android.sdk.util.RCException;
//import org.webrtc.VideoRenderer;
//import org.webrtc.VideoRendererGui;
//import org.webrtc.VideoTrack;

public class MainActivity extends Activity implements RCDeviceListener, RCConnectionListener, OnClickListener,
        ServiceConnection {

   private RCDevice device;
   boolean serviceBound = false;

   private RCConnection connection, pendingConnection;
   private HashMap<String, Object> params;
   private static final String TAG = "MainActivity";
   HashMap<String, Object> connectParams;
   private final int PERMISSION_REQUEST_DANGEROUS = 1;

   // UI elements
   Button btnDial;
   Button btnHangup;

   @Override
   protected void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      // Set window styles for fullscreen-window size. Needs to be done before
      // adding content.
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
                  | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                  | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                  | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
      getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

      setContentView(R.layout.activity_main);

      // initialize UI
      btnDial = (Button) findViewById(R.id.button_dial);
      btnDial.setOnClickListener(this);
      btnHangup = (Button) findViewById(R.id.button_hangup);
      btnHangup.setOnClickListener(this);
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
      device.release();
      /*
      RCClient.shutdown();
      device = null;
      */
   }

   // Callbacks for service binding, passed to bindService()
   @Override
   public void onServiceConnected(ComponentName className, IBinder service)
   {
      Log.i(TAG, "%% onServiceConnected");
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      RCDevice.RCDeviceBinder binder = (RCDevice.RCDeviceBinder) service;
      device = binder.getService();

      Intent intent = new Intent(getApplicationContext(), MainActivity.class);

      HashMap<String, Object> params = new HashMap<String, Object>();
      // we don't have a separate activity for the calls and messages, so let's use the same intent both for calls and messages
      params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, intent);
      params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, intent);
      params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "");
      params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk");
      params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "https://es.xirsys.com/_turn");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, "cloud.restcomm.com");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "atsakiridis");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, "4e89a09e-bf6f-11e5-a15c-69ffdcc2b8a7");
      params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE,1);
      params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, true);

      // The SDK provides the user with default sounds for calling, ringing, busy (declined) and message, but the user can override them
      // by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them with Resource IDs like R.raw.user_provided_calling_sound
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.user_provided_calling_sound);
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.user_provided_ringing_sound);
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.user_provided_declined_sound);
      //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.user_provided_message_sound);

      // This is for debugging purposes, not for release builds
      //params.put(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.SIGNALING_JAIN_SIP_LOGGING_ENABLED, true));

      if (!device.isInitialized()) {
         try {
            device.initialize(getApplicationContext(), params, this);
            device.setLogLevel(Log.VERBOSE);
         }
         catch (RCException e) {
            Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
         }
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

      //noinspection SimplifiableIfStatement
      if (id == R.id.action_settings) {
         return true;
      }

      return super.onOptionsItemSelected(item);
   }

   // UI Events
   public void onClick(View view)
   {
      if (view.getId() == R.id.button_dial) {
         if (connection != null) {
            Log.e(TAG, "Error: already connected");
            return;
         }

         connectParams = new HashMap<String, Object>();
         // CHANGEME: You can update the IP address to your Restcomm instance. Also, you can update the number
         // from '1235' to any Restcomm application you wish to reach
         connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "sip:+1235@cloud.restcomm.com");
         connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
         connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, findViewById(R.id.local_video_layout));
         connectParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, findViewById(R.id.remote_video_layout));

         handlePermissions(true);
         // if you want to add custom SIP headers, please uncomment this
         //HashMap<String, String> sipHeaders = new HashMap<>();
         //sipHeaders.put("X-SIP-Header1", "Value1");
         //connectParams.put("sip-headers", sipHeaders);
      }
      else if (view.getId() == R.id.button_hangup) {
         if (connection == null) {
            Log.e(TAG, "Error: not connected");
         }
         else {
            connection.disconnect();
            connection = null;
            pendingConnection = null;
         }
      }
   }


   // RCDevice Listeners
   public void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {

   }

   public void onStopListening(RCDevice device, int errorCode, String errorText)
   {
      Log.i(TAG, errorText);
   }

   public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
   {

   }

   // RCConnection Listeners
   public void onConnecting(RCConnection connection)
   {
      Log.i(TAG, "RCConnection connecting");
   }

   public void onConnected(RCConnection connection, HashMap<String, String> customHeaders) {
      Log.i(TAG, "RCConnection connected");
   }


   public void onDisconnected(RCConnection connection)
   {
      Log.i(TAG, "RCConnection disconnected");
      this.connection = null;
      pendingConnection = null;
   }

   public void onDisconnected(RCConnection connection, int errorCode, String errorText)
   {

      Log.i(TAG, errorText);
      this.connection = null;
      pendingConnection = null;
   }

   public void onCancelled(RCConnection connection)
   {
      Log.i(TAG, "RCConnection cancelled");
      this.connection = null;
      pendingConnection = null;
   }

   public void onDeclined(RCConnection connection)
   {
      Log.i(TAG, "RCConnection declined");
      this.connection = null;
      pendingConnection = null;
   }

   public void onLocalVideo(RCConnection connection)
   {
   }

   public void onRemoteVideo(RCConnection connection)
   {
   }

   public void onError(RCConnection connection, int errorCode, String errorText)
   {
   }

   public void onDigitSent(RCConnection connection, int statusCode, String statusText)
   {
   }

   public void onMessageSent(RCDevice device, int statusCode, String statusText, String jobId)
   {
   }

   @Override
   public void onError(RCDevice device, int statusCode, String statusText) {

   }

   public void onReleased(RCDevice device, int statusCode, String statusText)
   {
   }

   public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
      Log.i(TAG, "onInitialized");
   }

   @Override
   public void onReconfigured(RCDevice device, RCConnectivityStatus connectivityStatus, int statusCode, String statusText) {

   }

   // Resume call after permissions are checked
   private void resumeCall()
   {
      if (connectParams != null) {
         try {
            connection = device.connect(connectParams, this);
            if (connection == null) {
               Log.e(TAG, "Error: error connecting");
               return;
            }
         }
         catch (RCException e) {
            Log.e(TAG, "Error: not connected" + e.errorText);
         }
      }
   }

   // Handle android permissions needed for Marshmallow (API 23) devices or later
   private boolean handlePermissions(boolean isVideo)
   {
      ArrayList<String> permissions = new ArrayList<>(Arrays.asList(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.USE_SIP}));
      if (isVideo) {
         // Only add CAMERA permission if this is a video call
         permissions.add(Manifest.permission.CAMERA);
      }

      if (!havePermissions(permissions)) {
         // Dynamic permissions where introduced in M
         // PERMISSION_REQUEST_DANGEROUS is an app-defined int constant. The callback method (i.e. onRequestPermissionsResult) gets the result of the request.
         ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), PERMISSION_REQUEST_DANGEROUS);

         return false;
      }

      resumeCall();

      return true;
   }

   // Checks if user has given 'permissions'. If it has them all, it returns true. If not it returns false and modifies 'permissions' to keep only
   // the permission that got rejected, so that they can be passed later into requestPermissions()
   private boolean havePermissions(ArrayList<String> permissions)
   {
      boolean allgranted = true;
      ListIterator<String> it = permissions.listIterator();
      while (it.hasNext()) {
         if (ActivityCompat.checkSelfPermission(this, it.next()) != PackageManager.PERMISSION_GRANTED) {
            allgranted = false;
         }
         else {
            // permission granted, remove it from permissions
            it.remove();
         }
      }
      return allgranted;
   }

   @Override
   public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      switch (requestCode) {
         case PERMISSION_REQUEST_DANGEROUS: {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               // permission was granted, yay! Do the contacts-related task you need to do.
               resumeCall();

            } else {
               // permission denied, boo! Disable the functionality that depends on this permission.
               Log.e(TAG, "Error: Permission(s) denied; aborting call");
            }
            return;
         }

         // other 'case' lines to check for other permissions this app might request
      }
   }

}
