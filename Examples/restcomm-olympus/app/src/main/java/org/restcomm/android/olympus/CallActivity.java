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

import android.Manifest;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ListIterator;

import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCConnectionListener;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.util.PercentFrameLayout;
import org.webrtc.SurfaceViewRenderer;


public class CallActivity extends AppCompatActivity implements RCConnectionListener, View.OnClickListener,
        KeypadFragment.OnFragmentInteractionListener, ServiceConnection {

    private RCConnection connection, pendingConnection;
    SharedPreferences prefs;
    private static final String TAG = "CallActivity";
    private HashMap<String, Object> connectParams;  // = new HashMap<String, Object>();
    private HashMap<String, Object> acceptParams; // = new HashMap<String, Object>();
    private RCDevice device;
    boolean serviceBound = false;
    private boolean pendingError = false;
    private boolean activityVisible = false;
    private boolean muteAudio = false;
    private boolean muteVideo = false;
    private boolean isVideo = false;
    // handler for the timer
    private Handler timerHandler = new Handler();
    int secondsElapsed = 0;
    private final int PERMISSION_REQUEST_DANGEROUS = 1;
    private AlertDialog alertDialog;
    private boolean callOutgoing = true;

    ImageButton btnMuteAudio, btnMuteVideo;
    ImageButton btnHangup;
    ImageButton btnAnswer, btnAnswerAudio;
    ImageButton btnKeypad;
    KeypadFragment keypadFragment;
    TextView lblCall, lblStatus, lblTimer;

    //public static String ACTION_OUTGOING_CALL = "org.restcomm.android.olympus.ACTION_OUTGOING_CALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        setContentView(R.layout.activity_call);

        // Initialize UI
        btnHangup = (ImageButton) findViewById(R.id.button_hangup);
        btnHangup.setOnClickListener(this);
        btnAnswer = (ImageButton) findViewById(R.id.button_answer);
        btnAnswer.setOnClickListener(this);
        btnAnswerAudio = (ImageButton) findViewById(R.id.button_answer_audio);
        btnAnswerAudio.setOnClickListener(this);
        btnMuteAudio = (ImageButton) findViewById(R.id.button_mute_audio);
        btnMuteAudio.setOnClickListener(this);
        btnMuteVideo = (ImageButton) findViewById(R.id.button_mute_video);
        btnMuteVideo.setOnClickListener(this);
        btnKeypad = (ImageButton) findViewById(R.id.button_keypad);
        btnKeypad.setOnClickListener(this);
        lblCall = (TextView) findViewById(R.id.label_call);
        lblStatus = (TextView) findViewById(R.id.label_status);
        lblTimer = (TextView) findViewById(R.id.label_timer);

        alertDialog = new AlertDialog.Builder(CallActivity.this, R.style.SimpleAlertStyle).create();

        //device = RCClient.listDevices().get(0);

        PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get Intent parameters.
        final Intent intent = getIntent();
        if (intent.getAction().equals(RCDevice.ACTION_OUTGOING_CALL)) {
            btnAnswer.setVisibility(View.INVISIBLE);
            btnAnswerAudio.setVisibility(View.INVISIBLE);
        } else {
            btnAnswer.setVisibility(View.VISIBLE);
            btnAnswerAudio.setVisibility(View.VISIBLE);
        }

        keypadFragment = new KeypadFragment();

        lblTimer.setVisibility(View.INVISIBLE);
        // these might need to be moved to Resume()
        btnMuteAudio.setVisibility(View.INVISIBLE);
        btnMuteVideo.setVisibility(View.INVISIBLE);
        btnKeypad.setVisibility(View.INVISIBLE);

        activityVisible = true;

        // open keypad
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.keypad_fragment_container, keypadFragment);
        ft.hide(keypadFragment);
        ft.commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "%% onPause");

        if (connection != null && connection.getState() == RCConnection.ConnectionState.CONNECTED) {
            connection.pauseVideo();
        }
    }

    @Override
    protected void onStart()
    {
       super.onStart();
       Log.i(TAG, "%% onStart");

       // User requested to disconnect via foreground service notification. At this point the service has already
       // disconnected the call, so let's close the call activity
       if (getIntent().getAction().equals(RCDevice.ACTION_CALL_DISCONNECT)) {
          Intent intent = new Intent(this, MainActivity.class);
           intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
           intent.setAction(MainActivity.ACTION_DISCONNECTED_BACKGROUND);
           startActivity(intent);
       }
       else {

           activityVisible = true;

           bindService(new Intent(this, RCDevice.class), this, Context.BIND_AUTO_CREATE);
       }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "%% onStop");
        activityVisible = false;

        // Unbind from the service
        if (serviceBound) {
            //device.detach();
            unbindService(this);
            serviceBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The activity has become visible (it is now "resumed").
        Log.i(TAG, "%% onResume");
        if (connection != null && connection.getState() == RCConnection.ConnectionState.CONNECTED) {
            connection.resumeVideo();

           // Now that we can mute/umnute via notification, we need to update the UI accordingly if there was a change
           // while we were not in the foreground
           muteAudio = connection.isAudioMuted();
           if (!muteAudio) {
              btnMuteAudio.setImageResource(R.drawable.audio_active_50x50);
           }
           else {
              btnMuteAudio.setImageResource(R.drawable.audio_muted_50x50);
           }
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        // The activity is about to be destroyed.
        Log.i(TAG, "%% onDestroy");

        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }

        if (pendingConnection != null) {
            // incoming ringing
            pendingConnection.reject();
            pendingConnection = null;
        } else {
            if (connection != null) {
                // incoming established or outgoing any state (pending, connecting, connected)
                if (connection.getState() == RCConnection.ConnectionState.CONNECTED) {
                    // If user leaves activity while on call we need to stop local video
                    //connection.pauseVideo();
                    connection.disconnect();
                }
                else {
                    connection = null;
                    pendingConnection = null;
                }
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent)
    {
        Log.i(TAG, "%% onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
    }


    // Callbacks for service binding, passed to bindService()
    @Override
    public void onServiceConnected(ComponentName className, IBinder service)
    {
        Log.i(TAG, "%% onServiceConnected");
        // We've bound to LocalService, cast the IBinder and get LocalService instance
        RCDevice.RCDeviceBinder binder = (RCDevice.RCDeviceBinder) service;
        device = binder.getService();

        // We have the device reference, let's handle the call
        handleCall(getIntent());

        serviceBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0)
    {
        Log.i(TAG, "%% onServiceDisconnected");
        serviceBound = false;
    }

    private void handleCall(Intent intent) {
        if (connection != null) {
            return;
        }

        isVideo = intent.getBooleanExtra(RCDevice.EXTRA_VIDEO_ENABLED, false);
        if (intent.getAction().equals(RCDevice.ACTION_OUTGOING_CALL)) {
            String text;
            if (isVideo) {
                text = "Video Calling ";
            }
            else {
                text = "Audio Calling ";
            }

            lblCall.setText(text + intent.getStringExtra(RCDevice.EXTRA_DID).replaceAll(".*?sip:", "").replaceAll("@.*$", ""));
            lblStatus.setText("Initiating Call...");

            connectParams = new HashMap<String, Object>();
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, intent.getStringExtra(RCDevice.EXTRA_DID));
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, intent.getBooleanExtra(RCDevice.EXTRA_VIDEO_ENABLED, false));
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, findViewById(R.id.local_video_layout));
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, findViewById(R.id.remote_video_layout));
            // by default we use VP8 for video as it tends to be more adopted, but you can override that and specify VP9 or H264 as follows:
            //connectParams.put(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC, "VP9");
            //connectParams.put(RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC, "H264");

            // *** if you want to add custom SIP headers, please uncomment this
            //HashMap<String, String> sipHeaders = new HashMap<>();
            //sipHeaders.put("X-SIP-Header1", "Value1");
            //connectParams.put(RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS, sipHeaders);

            handlePermissions(isVideo);
        }
        if (intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL) ||
              intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL_ANSWER_AUDIO) || intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL_ANSWER_VIDEO)) {
            String text;
            if (isVideo) {
                text = "Video Call from ";
            }
            else {
                text = "Audio Call from ";
            }

            lblCall.setText(text + intent.getStringExtra(RCDevice.EXTRA_DID).replaceAll(".*?sip:", "").replaceAll("@.*$", ""));
            lblStatus.setText("Call Received...");

            //callOutgoing = false;
            pendingConnection = device.getPendingConnection();
            pendingConnection.setConnectionListener(this);

            // the number from which we got the call
            String incomingCallDid = intent.getStringExtra(RCDevice.EXTRA_DID);
            HashMap<String, String> customHeaders = (HashMap<String, String>) intent.getSerializableExtra(RCDevice.EXTRA_CUSTOM_HEADERS);
            if (customHeaders != null) {
                Log.i(TAG, "Got custom headers in incoming call: " + customHeaders.toString());
            }

            if (intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL_ANSWER_AUDIO) || intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL_ANSWER_VIDEO)) {
                // The Intent has been sent from the Notification subsystem. It can be either of type 'decline', 'video answer and 'audio answer'
                boolean answerVideo = intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL_ANSWER_VIDEO);
                btnAnswer.setVisibility(View.INVISIBLE);
                btnAnswerAudio.setVisibility(View.INVISIBLE);

                acceptParams = new HashMap<String, Object>();
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, answerVideo);
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, findViewById(R.id.local_video_layout));
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, findViewById(R.id.remote_video_layout));

                // Check permissions asynchronously and then accept the call
                handlePermissions(true);
            }
        }
        /* TODO: Issue #380: once we figure out the issue with the backgrounding we need to uncomment this
        if (intent.getAction().equals(RCDevice.LIVE_CALL)) {
            String text;
            connection = device.getLiveConnection();
            connection.setConnectionListener(this);

            if (connection.isIncoming()) {
                // Incoming
                if (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO) {
                    text = "Video Call from ";
                }
                else {
                    text = "Audio Call from ";
                }
            }
            else {
                // Outgoing
                if (connection.getLocalMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO) {
                    text = "Video Calling ";
                }
                else {
                    text = "Audio Calling ";
                }
            }

            lblCall.setText(text + connection.getPeer().replaceAll(".*?sip:", "").replaceAll("@.*$", ""));
            lblStatus.setText("Connected");
            connection.resumeVideo((PercentFrameLayout)findViewById(R.id.local_video_layout),
                    (PercentFrameLayout)findViewById(R.id.remote_video_layout));

            // Hide answering buttons and show mute & keypad
            btnAnswer.setVisibility(View.INVISIBLE);
            btnAnswerAudio.setVisibility(View.INVISIBLE);
            btnMuteAudio.setVisibility(View.VISIBLE);
            btnMuteVideo.setVisibility(View.VISIBLE);
            btnKeypad.setVisibility(View.VISIBLE);

            lblTimer.setVisibility(View.VISIBLE);
        }
        */
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_hangup) {
            if (pendingConnection != null) {
                // incoming ringing
                lblStatus.setText("Rejecting Call...");
                pendingConnection.reject();
                pendingConnection = null;
            } else {
                if (connection != null) {
                    // incoming established or outgoing any state (pending, connecting, connected)
                    lblStatus.setText("Disconnecting Call...");
                    connection.disconnect();
                    connection = null;
                    pendingConnection = null;
                } else {
                    Log.e(TAG, "Error: not connected/connecting/pending");
                }
            }
            finish();
        } else if (view.getId() == R.id.button_answer) {
            if (pendingConnection != null) {
                lblStatus.setText("Answering Call...");
                btnAnswer.setVisibility(View.INVISIBLE);
                btnAnswerAudio.setVisibility(View.INVISIBLE);

                acceptParams = new HashMap<String, Object>();
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, findViewById(R.id.local_video_layout));
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, findViewById(R.id.remote_video_layout));
                // Check permissions asynchronously and then accept the call
                handlePermissions(true);
            }
        } else if (view.getId() == R.id.button_answer_audio) {
            if (pendingConnection != null) {
                lblStatus.setText("Answering Call...");
                btnAnswer.setVisibility(View.INVISIBLE);
                btnAnswerAudio.setVisibility(View.INVISIBLE);

                acceptParams = new HashMap<String, Object>();
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, false);
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, findViewById(R.id.local_video_layout));
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, findViewById(R.id.remote_video_layout));
                // Check permissions asynchronously and then accept the call
                handlePermissions(false);
            }
        } else if (view.getId() == R.id.button_keypad) {
            keypadFragment.setConnection(connection);

            View rootView = getWindow().getDecorView().findViewById(android.R.id.content);

            // show keypad
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.show(keypadFragment);
            ft.commit();
        } else if (view.getId() == R.id.button_mute_audio) {
            if (connection != null) {
                if (!muteAudio) {
                    btnMuteAudio.setImageResource(R.drawable.audio_muted);
                } else {
                    btnMuteAudio.setImageResource(R.drawable.audio_unmuted);
                }
                muteAudio = !muteAudio;
                connection.setAudioMuted(muteAudio);
            }
        } else if (view.getId() == R.id.button_mute_video) {
            if (connection != null) {
                muteVideo = !muteVideo;
                if (muteVideo) {
                    btnMuteVideo.setImageResource(R.drawable.video_muted);
                    //connection.off();
                    //connection.pauseVideo();
                } else {
                    btnMuteVideo.setImageResource(R.drawable.video_unmuted);
                    //connection.on((PercentFrameLayout)findViewById(R.id.local_video_layout), (PercentFrameLayout)findViewById(R.id.remote_video_layout));
                    //connection.resumeVideo((PercentFrameLayout)findViewById(R.id.local_video_layout), (PercentFrameLayout)findViewById(R.id.remote_video_layout));
                }

                connection.setVideoMuted(muteVideo);
            }
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

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // RCConnection Listeners
    public void onConnecting(RCConnection connection) {
        Log.i(TAG, "RCConnection connecting");
        lblStatus.setText("Started Connecting...");
    }

    public void onConnected(RCConnection connection, HashMap<String, String> customHeaders) {
        Log.i(TAG, "RCConnection connected, customHeaders: " + customHeaders);
        lblStatus.setText("Connected");

        btnMuteAudio.setVisibility(View.VISIBLE);
        btnMuteVideo.setVisibility(View.VISIBLE);
        btnKeypad.setVisibility(View.VISIBLE);

        lblTimer.setVisibility(View.VISIBLE);
        startTimer();

        // reset to no mute at beggining of new call
        muteAudio = false;
        muteVideo = false;

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    public void onDisconnected(RCConnection connection) {
        Log.i(TAG, "RCConnection disconnected");
        lblStatus.setText("Disconnected");

        btnMuteAudio.setVisibility(View.INVISIBLE);
        btnMuteVideo.setVisibility(View.INVISIBLE);

        this.connection = null;
        pendingConnection = null;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (!pendingError) {
            finish();
        } else {
            pendingError = false;
        }
    }

    public void onCancelled(RCConnection connection) {
        Log.i(TAG, "RCConnection cancelled");
        lblStatus.setText("Cancelled");

        this.connection = null;
        pendingConnection = null;

        finish();
    }

    public void onDeclined(RCConnection connection) {
        Log.i(TAG, "RCConnection declined");
        lblStatus.setText("Declined");

        this.connection = null;
        pendingConnection = null;

        finish();
    }

    public void onDisconnected(RCConnection connection, int errorCode, String errorText) {
        pendingError = true;
        showOkAlert("RCConnection Error", errorText);
        this.connection = null;
        pendingConnection = null;
    }

    public void onError(RCConnection connection, int errorCode, String errorText)
    {
        pendingError = true;
        showOkAlert("RCConnection Error", errorText);
        this.connection = null;
        pendingConnection = null;
    }

    public void onDigitSent(RCConnection connection, int statusCode, String statusText)
    {

    }

    public void onLocalVideo(RCConnection connection)
    {

    }

    public void onRemoteVideo(RCConnection connection)
    {

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
        boolean allGranted = true;
        ListIterator<String> it = permissions.listIterator();
        while (it.hasNext()) {
            if (ActivityCompat.checkSelfPermission(this, it.next()) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
            }
            else {
                // permission granted, remove it from permissions
                it.remove();
            }
        }
        return allGranted;
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
                    showOkAlert("Call Error", "Permission(s) denied; aborting call");
                }
                return;
            }

            // other 'case' lines to check for other permissions this app might request
        }
    }

    // Resume call after permissions are checked
    private void resumeCall()
    {
        if (connectParams != null) {
            // outgoing call
            connection = device.connect(connectParams, this);
            if (connection == null) {
                Log.e(TAG, "Error: error connecting");
                showOkAlert("RCDevice Error", "Device is Offline");
            }
        }
        else if (acceptParams != null) {
            // incoming call
            pendingConnection.accept(acceptParams);
            connection = this.pendingConnection;
            pendingConnection = null;
        }
    }

    // Helpers
    private void showOkAlert(final String title, final String detail) {
        if (activityVisible) {
            if (alertDialog.isShowing()) {
                Log.w(TAG, "Alert already showing, hiding to show new alert");
                alertDialog.hide();
            }

            alertDialog.setTitle(title);
            alertDialog.setMessage(detail);
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
            alertDialog.show();
        }
    }

    @Override
    public void onFragmentInteraction(String action) {
        if (action.equals("cancel")) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(keypadFragment);
            ft.commit();

        }
    }

    public void startTimer() {
        String time = String.format("%02d:%02d:%02d", secondsElapsed / 3600, (secondsElapsed % 3600) / 60, secondsElapsed % 60);
        lblTimer.setText(time);
        secondsElapsed++;

        timerHandler.removeCallbacksAndMessages(null);
        // schedule a registration update after 'registrationRefresh' seconds
        Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                startTimer();
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }
}
