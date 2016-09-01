package com.telestax.restcomm_helloworld;

//import android.support.v7.app.ActionBarActivity;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.view.View.OnClickListener;

import java.util.HashMap;

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

   /*
   private GLSurfaceView videoView;
   private VideoRenderer.Callbacks localRender = null;
   private VideoRenderer.Callbacks remoteRender = null;
   private boolean videoReady = false;
   VideoTrack localVideoTrack, remoteVideoTrack;
   VideoRenderer localVideoRenderer, remoteVideoRenderer;

   // Local preview screen position before call is connected.
   private static final int LOCAL_X_CONNECTING = 0;
   private static final int LOCAL_Y_CONNECTING = 0;
   private static final int LOCAL_WIDTH_CONNECTING = 100;
   private static final int LOCAL_HEIGHT_CONNECTING = 100;
   // Local preview screen position after call is connected.
   private static final int LOCAL_X_CONNECTED = 72;
   private static final int LOCAL_Y_CONNECTED = 2;
   private static final int LOCAL_WIDTH_CONNECTED = 25;
   private static final int LOCAL_HEIGHT_CONNECTED = 25;
   // Remote video screen position
   private static final int REMOTE_X = 0;
   private static final int REMOTE_Y = 0;
   private static final int REMOTE_WIDTH = 100;
   private static final int REMOTE_HEIGHT = 100;
   private VideoRendererGui.ScalingType scalingType;
   */

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
      // we don't have a separate activity for the calls, so use the same intent both for calls and messages
      device.setPendingIntents(intent, intent);

      /*
      // Setup video stuff
      scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
      videoView = (GLSurfaceView) findViewById(R.id.glview_call);
      // Create video renderers.
      VideoRendererGui.setView(videoView, new Runnable() {
         @Override
         public void run()
         {
            videoContextReady();
         }
      });
      remoteRender = VideoRendererGui.create(
            REMOTE_X, REMOTE_Y,
            REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
      localRender = VideoRendererGui.create(
            LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
            LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);
            */
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

         HashMap<String, Object> connectParams = new HashMap<String, Object>();
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


         // if you want to add custom SIP headers, please uncomment this
         //HashMap<String, String> sipHeaders = new HashMap<>();
         //sipHeaders.put("X-SIP-Header1", "Value1");
         //connectParams.put("sip-headers", sipHeaders);

         connection = device.connect(connectParams, this);
         if (connection == null) {
            Log.e(TAG, "Error: error connecting");
            return;
         }
         //device.updateParams(params);
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

      /*
      // reside local renderer to take up all screen now that the call is over
      VideoRendererGui.update(localRender,
            LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
            LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

      if (localVideoTrack != null) {

         localVideoTrack.removeRenderer(localVideoRenderer);
         localVideoTrack = null;
      }

      if (remoteVideoTrack != null) {
         remoteVideoTrack.removeRenderer(remoteVideoRenderer);
         remoteVideoTrack = null;
      }
      */
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


}
