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

package com.telestax.restcomm_messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;

import java.util.HashMap;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;

import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoTrack;

public class CallActivity extends Activity implements RCConnectionListener, View.OnClickListener,
        KeypadFragment.OnFragmentInteractionListener {

    private GLSurfaceView videoView;
    private VideoRenderer.Callbacks localRender = null;
    private VideoRenderer.Callbacks remoteRender = null;

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

    private RCConnection connection, pendingConnection;
    SharedPreferences prefs;
    private static final String TAG = "CallActivity";
    private HashMap<String, Object> connectParams = new HashMap<String, Object>();
    private HashMap<String, Object> acceptParams = new HashMap<String, Object>();
    private RCDevice device;
    private boolean pendingError = false;
    private boolean activityVisible = false;
    private boolean muteAudio = false;
    private boolean muteVideo = false;

    //CheckBox cbMuted;
    Button btnMuteAudio, btnMuteVideo;
    Button btnHangup;
    Button btnAnswer, btnAnswerAudio;
    ImageButton btnKeypad;
    KeypadFragment keypadFragment;

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
        btnHangup = (Button)findViewById(R.id.button_hangup);
        btnHangup.setOnClickListener(this);
        btnAnswer = (Button)findViewById(R.id.button_answer);
        btnAnswer.setOnClickListener(this);
        btnAnswerAudio = (Button)findViewById(R.id.button_answer_audio);
        btnAnswerAudio.setOnClickListener(this);
        //cbMuted = (CheckBox)findViewById(R.id.checkbox_muted);
        //cbMuted.setOnCheckedChangeListener(this);
        //cbMuted.setEnabled(false);
        btnMuteAudio = (Button)findViewById(R.id.button_mute_audio);
        btnMuteAudio.setOnClickListener(this);
        btnMuteVideo = (Button)findViewById(R.id.button_mute_video);
        btnMuteVideo.setOnClickListener(this);
        btnKeypad = (ImageButton)findViewById(R.id.button_keypad);
        btnKeypad.setOnClickListener(this);

        device = RCClient.listDevices().get(0);

        PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get Intent parameters.
        final Intent intent = getIntent();
        if (intent.getAction() == RCDevice.OUTGOING_CALL) {
            btnAnswer.setVisibility(View.INVISIBLE);
            btnAnswerAudio.setVisibility(View.INVISIBLE);
        }
        else {
            btnAnswer.setVisibility(View.VISIBLE);
            btnAnswerAudio.setVisibility(View.VISIBLE);
        }
        // Setup video stuff
        scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
        videoView = (GLSurfaceView) findViewById(R.id.glview_call);
        // Create video renderers.
        VideoRendererGui.setView(videoView, new Runnable() {
            @Override
            public void run() {
                videoContextReady(intent);
            }
        });
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        keypadFragment = new KeypadFragment();

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

        if (pendingConnection != null) {
            // incoming ringing
            pendingConnection.reject();
            pendingConnection = null;
        } else {
            if (connection != null) {
                // incoming established or outgoing any state (pending, connecting, connected)
                connection.disconnect();
                connection = null;
                pendingConnection = null;
            }
        }
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "%% onStart");
        activityVisible = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "%% onStop");
        activityVisible = false;
    }

    private void videoContextReady(Intent intent)
    {
        final Intent finalIntent = intent;
        final CallActivity finalActivity = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (finalIntent.getAction().equals(RCDevice.OUTGOING_CALL)) {
                    connectParams.put("username", finalIntent.getStringExtra(RCDevice.EXTRA_DID));
                    connectParams.put("video-enabled", finalIntent.getBooleanExtra(RCDevice.EXTRA_VIDEO_ENABLED, false));

                    // if you want to add custom SIP headers, please uncomment this
                    //HashMap<String, String> sipHeaders = new HashMap<>();
                    //sipHeaders.put("X-SIP-Header1", "Value1");
                    //connectParams.put("sip-headers", sipHeaders);

                    connection = device.connect(connectParams, finalActivity);

                    if (connection == null) {
                        Log.e(TAG, "Error: error connecting");
                        showOkAlert("RCDevice Error", "No Wifi connectivity");
                        return;
                    }
                }
                if (finalIntent.getAction().equals(RCDevice.INCOMING_CALL)) {
                    pendingConnection = device.getPendingConnection();
                    pendingConnection.setConnectionListener(finalActivity);

                    // the number from which we got the call
                    String incomingCallDid = finalIntent.getStringExtra(RCDevice.EXTRA_DID);
                    // notice that this is not used yet; the sdk doesn't tell us if the incoming call is video/audio (TODO)
                    acceptParams.put("video-enabled", finalIntent.getBooleanExtra(RCDevice.EXTRA_VIDEO_ENABLED, false));
                }
            }
        });
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_hangup) {
            if (pendingConnection != null) {
                // incoming ringing
                pendingConnection.reject();
                pendingConnection = null;
            } else {
                if (connection != null) {
                    // incoming established or outgoing any state (pending, connecting, connected)
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
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put("video-enabled", true);
                pendingConnection.accept(params);
                connection = this.pendingConnection;
                pendingConnection = null;
            }
        } else if (view.getId() == R.id.button_answer_audio) {
            if (pendingConnection != null) {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put("video-enabled", false);
                pendingConnection.accept(params);
                connection = this.pendingConnection;
                pendingConnection = null;
            }
        } else if (view.getId() == R.id.button_keypad) {
            //keypadFragment = new KeypadFragment();
            keypadFragment.setConnection(connection);

            // show keypad
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.show(keypadFragment);
            ft.commit();
        } else if (view.getId() == R.id.button_mute_audio) {
            if (connection != null) {
                muteAudio = !muteAudio;
                connection.setAudioMuted(muteAudio);
            }
        } else if (view.getId() == R.id.button_mute_video) {
            if (connection != null) {
                muteVideo = !muteVideo;
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // RCConnection Listeners
    public void onConnecting(RCConnection connection)
    {
        Log.i(TAG, "RCConnection connecting");
    }

    public void onConnected(RCConnection connection) {
        Log.i(TAG, "RCConnection connected");
        //cbMuted.setEnabled(true);
        btnMuteAudio.setVisibility(View.VISIBLE);
        btnMuteVideo.setVisibility(View.VISIBLE);

        // reset to no mute at beggining of new call
        muteAudio = false;
        muteVideo = false;

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    public void onDisconnected(RCConnection connection) {
        Log.i(TAG, "RCConnection disconnected");
        //cbMuted.setEnabled(false);
        btnMuteAudio.setVisibility(View.INVISIBLE);
        btnMuteVideo.setVisibility(View.INVISIBLE);

        this.connection = null;
        pendingConnection = null;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (!pendingError) {
            finish();
        }
        else {
            pendingError = false;
        }
    }

    public void onCancelled(RCConnection connection) {
        Log.i(TAG, "RCConnection cancelled");
        this.connection = null;
        pendingConnection = null;

        finish();
    }

    public void onDeclined(RCConnection connection) {
        Log.i(TAG, "RCConnection declined");

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

    /*
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (buttonView.getId() == R.id.checkbox_muted) {
            if (connection != null) {
                connection.setMuted(isChecked);
            }
        }
    }
    */

    public void onReceiveLocalVideo(RCConnection connection, VideoTrack videoTrack) {
        if (videoTrack != null) {
            //show media on screen
            videoTrack.setEnabled(true);
            videoTrack.addRenderer(new VideoRenderer(localRender));
        }
    }

    public void onReceiveRemoteVideo(RCConnection connection, VideoTrack videoTrack) {
        if (videoTrack != null) {
            //show media on screen
            videoTrack.setEnabled(true);
            videoTrack.addRenderer(new VideoRenderer(remoteRender));

            VideoRendererGui.update(remoteRender,
                    REMOTE_X, REMOTE_Y,
                    REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
            VideoRendererGui.update(localRender,
                    LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                    LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                    VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
        }
    }

    // Helpers
    private void showOkAlert(final String title, final String detail) {
        if (activityVisible) {
            AlertDialog alertDialog = new AlertDialog.Builder(CallActivity.this).create();
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
        if (action == "cancel") {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(keypadFragment);
            ft.commit();

        }
    }
}
