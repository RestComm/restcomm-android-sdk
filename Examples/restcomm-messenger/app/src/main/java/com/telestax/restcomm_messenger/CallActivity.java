package com.telestax.restcomm_messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
import android.widget.RelativeLayout;
import java.util.HashMap;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;

public class CallActivity extends Activity implements RCConnectionListener, View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, AudioManager.OnAudioFocusChangeListener {

    //private GLSurfaceView videoView;
    private RCConnection connection, pendingConnection;
    SharedPreferences prefs;
    private static final String TAG = "CallActivity";
    private HashMap<String, Object> connectParams = new HashMap<String, Object>();
    private RCDevice device;
    // #WEBRTC-VIDEO TODO: uncomment when video is introduced
    //private RelativeLayout parentLayout;
    MediaPlayer ringingPlayer;
    MediaPlayer callingPlayer;
    AudioManager audioManager;
    final String TAG_LOCAL_VIDEO_VIEW = "local-video-view";

    CheckBox cbMuted;
    Button btnHangup;
    Button btnAnswer;
    Button btnDecline;
    Button btnCancel;

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
        btnDecline = (Button)findViewById(R.id.button_decline);
        btnDecline.setOnClickListener(this);
        btnCancel = (Button)findViewById(R.id.button_cancel);
        btnCancel.setOnClickListener(this);
        cbMuted = (CheckBox)findViewById(R.id.checkbox_muted);
        cbMuted.setOnCheckedChangeListener(this);
        // #WEBRTC-VIDEO TODO: uncomment when video is introduced
        //parentLayout = (RelativeLayout) findViewById(R.id.layout_video_call);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        // volume control should be by default 'music' which will control the ringing sounds and 'voice call' when within a call
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Setup Media (notice that I'm not preparing the media as create does that implicitly plus
        // I'm not ever stopping a player -instead I'm pausing so no additional preparation is needed
        // there either. We might need to revisit this at some point though
        ringingPlayer = MediaPlayer.create(getApplicationContext(), R.raw.ringing);
        ringingPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        ringingPlayer.setLooping(true);
        callingPlayer = MediaPlayer.create(getApplicationContext(), R.raw.calling);
        callingPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        callingPlayer.setLooping(true);

        cbMuted.setEnabled(false);

        device = RCClient.getInstance().listDevices().get(0);

        PreferenceManager.setDefaultValues(this, "preferences.xml", MODE_PRIVATE, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get Intent parameters.
        final Intent intent = getIntent();
        if (intent.getAction() == RCDevice.OUTGOING_CALL) {
            connectParams.put("username", intent.getStringExtra(RCDevice.EXTRA_DID));

            // if you want to add custom SIP headers, please uncomment this
            //HashMap<String, String> sipHeaders = new HashMap<>();
            //sipHeaders.put("X-SIP-Header1", "Value1");
            //connectParams.put("sip-headers", sipHeaders);

            connection = device.connect(connectParams, this);

            if (connection == null) {
                Log.e(TAG, "Error: error connecting");
                return;
            }
        }
        if (intent.getAction() == RCDevice.INCOMING_CALL) {
            int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                ringingPlayer.start();
            }
            pendingConnection = device.incomingConnection;
            pendingConnection.listenerReady(this);

            // the number from which we got the call
            String incomingCallDid =  intent.getStringExtra(RCDevice.EXTRA_DID);
        }
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_hangup) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
            }
            else {
                connection.disconnect();
                connection = null;
                pendingConnection = null;
                finish();
            }
        } else if (view.getId() == R.id.button_answer) {
            if (pendingConnection != null) {
                pendingConnection.accept();
                connection = this.pendingConnection;
                pendingConnection = null;
                ringingPlayer.pause();
                // Abandon audio focus when playback complete
                audioManager.abandonAudioFocus(this);
            }
        } else if (view.getId() == R.id.button_decline) {
            if (pendingConnection != null) {
                pendingConnection.reject();
                pendingConnection = null;
                ringingPlayer.pause();
                // Abandon audio focus when playback complete
                audioManager.abandonAudioFocus(this);
                finish();
            }
        } else if (view.getId() == R.id.button_cancel) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
            }
            else {
                connection.disconnect();
                connection = null;
                pendingConnection = null;
                callingPlayer.pause();
                // Abandon audio focus when playback complete
                audioManager.abandonAudioFocus(this);
                finish();
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

    @Override
    public void onResume() {
        super.onResume();
    }

    // RCConnection Listeners
    public void onConnecting(RCConnection connection)
    {
        Log.i(TAG, "RCConnection connecting");
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            callingPlayer.start();
        }
    }

    public void onConnected(RCConnection connection) {
        Log.i(TAG, "RCConnection connected");
        cbMuted.setEnabled(true);
        if (!connection.isIncoming()) {
            callingPlayer.pause();
            // Abandon audio focus when playback complete
            audioManager.abandonAudioFocus(this);
        }
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    public void onDisconnected(RCConnection connection) {
        Log.i(TAG, "RCConnection disconnected");
        cbMuted.setEnabled(false);

        this.connection = null;
        pendingConnection = null;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        finish();
    }

    public void onCancelled(RCConnection connection) {
        Log.i(TAG, "RCConnection cancelled");
        if (connection.isIncoming() == true) {
            ringingPlayer.pause();
        }
        else {
            callingPlayer.pause();
        }
        // Abandon audio focus when playback complete
        audioManager.abandonAudioFocus(this);

        this.connection = null;
        pendingConnection = null;

        finish();
    }

    public void onDeclined(RCConnection connection) {
        Log.i(TAG, "RCConnection declined");
        callingPlayer.pause();
        // Abandon audio focus when playback complete
        audioManager.abandonAudioFocus(this);


        this.connection = null;
        pendingConnection = null;

        finish();
    }

    public void onDisconnected(RCConnection connection, int errorCode, String errorText) {
        if (errorCode == RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal()) {
            showOkAlert("No Connectivity", errorText);
        } else if (errorCode == RCClient.ErrorCodes.GENERIC_ERROR.ordinal()) {
            showOkAlert("Generic Error", errorText);
        } else {
            showOkAlert("Unknown Error", "Unknown Restcomm Client error");
        }
        this.connection = null;
        pendingConnection = null;
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (buttonView.getId() == R.id.checkbox_muted) {
            if (connection != null) {
                connection.setMuted(isChecked);
            }
        }
    }

    // #WEBRTC-VIDEO TODO: uncomment when video is introduced
    /*
    public void onReceiveLocalVideo(RCConnection connection, GLSurfaceView videoView) {
        if (videoView != null) {
            //show media on screen
            videoView.setTag(TAG_LOCAL_VIDEO_VIEW);
            if (parentLayout.findViewWithTag(TAG_LOCAL_VIDEO_VIEW) != null) {
                parentLayout.removeView(videoView);
            }
            parentLayout.addView(videoView, 0);
        }
    }
    */

    // Callbacks for auio focus change events
    public void onAudioFocusChange(int focusChange)
    {
        Log.i(TAG, "onAudioFocusChange: " + focusChange);
		/*
		if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
			// Pause playback
		}
		else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			// Resume playback or raise it back to normal if we were ducked
		}
		else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
			//am.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
			audio.abandonAudioFocus(this);
			// Stop playback
		}
		else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            // Lower the volume
        }
		*/
    }

    // Helpers
    private void showOkAlert(final String title, final String detail) {
        AlertDialog alertDialog = new AlertDialog.Builder(CallActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(detail);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alertDialog.show();
    }


}
