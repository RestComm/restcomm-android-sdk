package com.telestax.restcomm_helloworld;

//import android.support.v7.app.ActionBarActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
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

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoTrack;

public class MainActivity extends Activity implements RCDeviceListener, RCConnectionListener, OnClickListener {

   private RCDevice device;
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

      // Initialized Restcomm Client SDK entities
      RCClient.setLogLevel(Log.VERBOSE);
      RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener() {
         public void onInitialized()
         {
            Log.i(TAG, "RCClient initialized");
         }

         public void onError(Exception exception)
         {
            Log.e(TAG, "RCClient initialization error");
         }
      });

      params = new HashMap<String, Object>();
      // update the IP address to your Restcomm instance
      params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "");
      params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "android-sdk");
      params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "https://service.xirsys.com/ice");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "atsakiridis");
      params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, "4e89a09e-bf6f-11e5-a15c-69ffdcc2b8a7");
      params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
      device = RCClient.createDevice(params, this);
      Intent intent = new Intent(getApplicationContext(), MainActivity.class);
      // we don't have a separate activity for the calls and messages, so let's use the same intent both for calls and messages
      device.setPendingIntents(intent, intent);
   }

   @Override
   protected void onDestroy()
   {
      super.onDestroy();
      // The activity is about to be destroyed.
      Log.i(TAG, "%% onDestroy");
      RCClient.shutdown();
      device = null;
   }

   /*
   private void videoContextReady()
   {
      videoReady = true;
   }
   */

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
         /*
         connectParams.put("username", "sip:+1235@cloud.restcomm.com");
         connectParams.put("video-enabled", true);
         */
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

   public void onStopListening(RCDevice device)
   {

   }

   public void onStopListening(RCDevice device, int errorCode, String errorText)
   {
      Log.i(TAG, errorText);
   }

   public boolean receivePresenceEvents(RCDevice device)
   {
      return false;
   }

   public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
   {

   }

   public void onIncomingConnection(RCDevice device, RCConnection connection)
   {
      Log.i(TAG, "Connection arrived");
      this.pendingConnection = connection;
   }

   public void onIncomingMessage(RCDevice device, String message, HashMap<String, String> parameters)
   {
      final HashMap<String, String> finalParameters = parameters;
      final String finalMessage = message;

      runOnUiThread(new Runnable() {
         @Override
         public void run()
         {
            String newText = finalParameters.get("username") + ": " + finalMessage + "\n";
            Log.i(TAG, "Message arrived: " + newText);
         }
      });
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

   public void onMessageSent(RCDevice device, int statusCode, String statusText)
   {
   }

   public void onReleased(RCDevice device, int statusCode, String statusText)
   {
   }

   public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
   {
   }

   public void onInitializationError(int errorCode, String errorText)
   {
   }

   // Resume call after permissions are checked
   private void resumeCall()
   {
      if (connectParams != null) {
         connection = device.connect(connectParams, this);
         if (connection == null) {
            Log.e(TAG, "Error: error connecting");
            return;
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
