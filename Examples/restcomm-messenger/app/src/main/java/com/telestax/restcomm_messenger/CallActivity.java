package com.telestax.restcomm_messenger;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
//import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;


public class CallActivity extends Activity implements RCConnectionListener, View.OnClickListener {

    public static final String EXTRA_DID = "com.telestax.restcomm_messenger.DID";

    //private static final int CONNECTION_REQUEST = 1;
    // #webrtc
    private GLSurfaceView videoView;
    private RCConnection connection;
    SharedPreferences prefs;
    private static final String TAG = "CallActivity";
    private HashMap<String, String> connectParams = new HashMap<String, String>();
    private RCDevice device;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // #webrtc
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
        setContentView(R.layout.activity_call);

        videoView = (GLSurfaceView) findViewById(R.id.glview_call);
        videoView.setOnClickListener(this);
        // finished with #webrtc

        device = RCClient.getInstance().listDevices().get(0);
        // Get Intent parameters.
        final Intent intent = getIntent();
        prefs = getSharedPreferences("preferences.xml", MODE_PRIVATE);

        device.initializeWebrtc(videoView, prefs);

        connectParams.put("username", intent.getStringExtra(EXTRA_DID));
        //connection = device.connect(connectParams, this, videoView, prefs);
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.glview_call) {
            connection = device.connect(connectParams, this, videoView, prefs);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_call, menu);
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
    public void onResume() {
        super.onResume();
        videoView.onResume();
        /*
        activityRunning = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
        */
    }

    // RCConnection Listeners
    public void onConnecting(RCConnection connection)
    {
    }

    public void onConnected(RCConnection connection) {
    }

    public void onDisconnected(RCConnection connection) {
    }

    public void onCancelled(RCConnection connection) {
    }

    public void onDeclined(RCConnection connection) {
    }

    public void onDisconnected(RCConnection connection, int errorCode, String errorText) {
    }
}
