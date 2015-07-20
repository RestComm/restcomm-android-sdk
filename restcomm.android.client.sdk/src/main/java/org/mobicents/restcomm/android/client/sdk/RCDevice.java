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

package org.mobicents.restcomm.android.client.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import android.app.Activity;
//import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
//import android.app.FragmentTransaction;

import org.mobicents.restcomm.android.client.sdk.PeerConnectionClient.PeerConnectionParameters;
//import org.mobicents.restcomm.android.client.sdk.CallFragment;
//import org.mobicents.restcomm.android.client.sdk.HudFragment;
import org.mobicents.restcomm.android.client.sdk.PeerConnectionClient.PeerConnectionEvents;
import org.mobicents.restcomm.android.client.sdk.SignalingParameters;

import org.mobicents.restcomm.android.client.sdk.util.LooperExecutor;
import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.SipUADeviceListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

// #webrtc
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoRendererGui.ScalingType;

/**
 *  RCDevice Represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
 *  in order to be notified of RestComm Client events you need to set a listener to RCDevice and implement the applicable methods.
 *  If you want to initiate a media connection towards another party you use RCDevice.connect() which returns an RCConnection object representing
 *  the new outgoing connection. From then on you can act on the new connection by applying RCConnection methods on the handle you got from RCDevice.connect().
 *  If there’s an incoming connection you will be notified by RCDeviceListener.onIncomingConnection() listener method. At that point you can use RCConnection methods to
 *  accept or reject the connection.
 *
 *  As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 *  via RCDeviceListener.onIncomingMessage() listener method.
 *
 *  @see RCConnection
 */

public class RCDevice implements SipUADeviceListener, PeerConnectionClient.PeerConnectionEvents {
    /**
     *  @abstract Device state (<b>Not Implemented yet</b>: device is always READY)
     */
    DeviceState state;
    /**
     *  @abstract Device capabilities (<b>Not Implemented yet</b>)
     */
    HashMap<DeviceCapability, Object> capabilities;
    /**
     *  @abstract Listener that will be receiving RCDevice events described at RCDeviceListener
     */
    RCDeviceListener listener;
    /**
     *  @abstract Is sound for incoming connections enabled (<b>Not Implemented yet</b>)
     */
    boolean incomingSoundEnabled;
    /**
     *  @abstract Is sound for outgoing connections enabled (<b>Not Implemented yet</b>)
     */
    boolean outgoingSoundEnabled;
    /**
     *  @abstract Is sound for disconnect enabled (<b>Not Implemented yet</b>)
     */
    boolean disconnectSoundEnabled;

    private SipProfile sipProfile;

    /**
     *  Device state (<b>Not Implemented yet</b>)
     */
    public enum DeviceState {
        OFFLINE,  /** Device is offline */
        READY,  /** Device is ready to make and receive connections */
        BUSY,  /** Device is busy */
    }

    /**
     *  Device capability (<b>Not Implemented yet</b>)
     */
    public enum DeviceCapability {
        INCOMING,
        OUTGOING,
        EXPIRATION,
        ACCOUNT_SID,
        APPLICATION_SID,
        APPLICATION_PARAMETERS,
        CLIENT_NAME,
    }

    public static String EXTRA_DEVICE = "com.telestax.restcomm.android.client.sdk.extra-device";
    public static String EXTRA_CONNECTION = "com.telestax.restcomm.android.client.sdk.extra-connection";
    PendingIntent pendingIntent;
    private static final String TAG = "RCDevice";

    // #webrtc
    private String keyprefVideoCallEnabled;
    private String keyprefResolution;
    private String keyprefFps;
    private String keyprefVideoBitrateType;
    private String keyprefVideoBitrateValue;
    private String keyprefVideoCodec;
    private String keyprefAudioBitrateType;
    private String keyprefAudioBitrateValue;
    private String keyprefAudioCodec;
    private String keyprefHwCodecAcceleration;
    private String keyprefNoAudioProcessingPipeline;
    private String keyprefCpuUsageDetection;
    private String keyprefDisplayHud;
    private String keyprefRoomServerUrl;
    private String keyprefRoom;
    private String keyprefRoomList;

    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;

    private PeerConnectionClient peerConnectionClient = null;
    //private AppRTCClient appRtcClient;
    private SignalingParameters signalingParameters;
    private AppRTCAudioManager audioManager = null;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private ScalingType scalingType;
    private Toast logToast;
    private boolean commandLineRun;
    private int runTimeMs;
    private boolean activityRunning;
    //private RoomConnectionParameters roomConnectionParameters;
    private PeerConnectionParameters peerConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs = 0;

    //CallFragment callFragment;
    //HudFragment hudFragment;


    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET"
    };

    /**
     *  Initialize a new RCDevice object
     *
     *  @param capabilityToken Capability Token
     *  @param deviceListener  Listener of RCDevice
     *
     */
    protected RCDevice(String capabilityToken, RCDeviceListener deviceListener,
                       GLSurfaceView videoView, SharedPreferences prefs, int viewId)
    {
        //this.client = client;
        this.updateCapabilityToken(capabilityToken);
        this.listener = deviceListener;

        sipProfile = new SipProfile();
        // TODO: check if those headers are needed
        HashMap<String, String> customHeaders = new HashMap<>();

        DeviceImpl deviceImpl = DeviceImpl.GetInstance();
        deviceImpl.Initialize(RCClient.getInstance().context, sipProfile, customHeaders);
        DeviceImpl.GetInstance().sipuaDeviceListener = this;

        // #webrtc
        Activity activity = (Activity)listener;
        setupWebrtc(videoView, activity, prefs, viewId);
        // Get setting keys.
        //PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        //sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Context context = RCClient.getInstance().context;
        keyprefVideoCallEnabled = context.getString(R.string.pref_videocall_key);
        keyprefResolution = context.getString(R.string.pref_resolution_key);
        keyprefFps = context.getString(R.string.pref_fps_key);
        keyprefVideoBitrateType = context.getString(R.string.pref_startvideobitrate_key);
        keyprefVideoBitrateValue = context.getString(R.string.pref_startvideobitratevalue_key);
        keyprefVideoCodec = context.getString(R.string.pref_videocodec_key);
        keyprefHwCodecAcceleration = context.getString(R.string.pref_hwcodec_key);
        keyprefAudioBitrateType = context.getString(R.string.pref_startaudiobitrate_key);
        keyprefAudioBitrateValue = context.getString(R.string.pref_startaudiobitratevalue_key);
        keyprefAudioCodec = context.getString(R.string.pref_audiocodec_key);
        keyprefNoAudioProcessingPipeline = context.getString(R.string.pref_noaudioprocessing_key);
        keyprefCpuUsageDetection = context.getString(R.string.pref_cpu_usage_detection_key);
        keyprefDisplayHud = context.getString(R.string.pref_displayhud_key);
        keyprefRoomServerUrl = context.getString(R.string.pref_room_server_url_key);
        keyprefRoom = context.getString(R.string.pref_room_key);
        keyprefRoomList = context.getString(R.string.pref_room_list_key);
    }

    // 'Copy' constructor
    public RCDevice(RCDevice device)
    {
        this.state = device.state;
        this.incomingSoundEnabled = device.incomingSoundEnabled;
        this.outgoingSoundEnabled = device.outgoingSoundEnabled;
        this.disconnectSoundEnabled = device.disconnectSoundEnabled;
        this.listener = device.listener;

        // Not used yet
        this.capabilities = null;
    }

    /**
     * Shuts down and release the Device (<b>Not Implemented yet</b>)
     */
    public void release()
    {

    }

    /**
     * Start listening for incoming connections (<b>Not Implemented yet</b>: for now once the RCDevice is created we are always listening for incoming connections)
     */
    public void listen()
    {

    }

    /**
     * Stop listeninig for incoming connections (Not Implemented yet)
     */
    public void unlisten()
    {

    }

    /**
     * Retrieves the capability token passed to RCClient.createDevice
     * @return  Capability token
     */
    public String getCapabilityToken()
    {
        return "";
    }

    /**
     * Updates the capability token (<b>Not implemented yet</b>
     */
    public void updateCapabilityToken(String token)
    {

    }

    /**
     *  Create an outgoing connection to an endpoint
     *
     *  @param parameters Connections such as the endpoint we want to connect to
     *  @param listener   The listener object that will receive events when the connection state changes
     *
     *  @return An RCConnection object representing the new connection
     */
    public RCConnection connect(Map<String, String> parameters, RCConnectionListener listener)
    {
        return connect(parameters, listener, null, null, 0);
        /*
        if (haveConnectivity()) {
            RCConnection connection = new RCConnection(listener);
            connection.incoming = false;
            connection.state = RCConnection.ConnectionState.PENDING;
            //DeviceImpl.GetInstance().listener = this;
            DeviceImpl.GetInstance().sipuaConnectionListener = connection;

            DeviceImpl.GetInstance().Call(parameters.get("username"));

            return connection;
        }
        else {
            return null;
        }
        */
    }

    public RCConnection connect(Map<String, String> parameters, RCConnectionListener listener, GLSurfaceView videoView,
                                SharedPreferences prefs, int viewId)
    {
        Activity activity = (Activity)listener;
        //setupWebrtc(videoView, activity, prefs, viewId);
        if (haveConnectivity()) {
            RCConnection connection = new RCConnection(listener);
            connection.incoming = false;
            connection.state = RCConnection.ConnectionState.PENDING;
            //DeviceImpl.GetInstance().listener = this;
            DeviceImpl.GetInstance().sipuaConnectionListener = connection;
            this.signalingParameters = new SignalingParameters(true, parameters.get("username"));
            startCall();
            //DeviceImpl.GetInstance().Call(parameters.get("username"));

            return connection;
        }
        else {
            return null;
        }
    }

    /**
     *  Send an instant message to a an endpoint
     *
     *  @param message  Message text
     *  @param parameters Parameters used for the message, such as 'username' that holds the recepient for the message
     */
    public boolean sendMessage(String message, Map<String, String> parameters)
    {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().SendMessage(parameters.get("username"), message);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     *  Disconnect all connections (<b>Not implemented yet</b>)
     */
    public void disconnectAll()
    {
        if (haveConnectivity()) {
            // TODO: disconnect open connections
        }
    }

    /**
     *  Retrieve the capabilities
     *  @return  Capabilities
     */
    public Map<DeviceCapability, Object> getCapabilities()
    {
        HashMap<DeviceCapability, Object> map = new HashMap<DeviceCapability, Object>();
        return map;
    }

    /**
     * Retrieve the Device state
     * @return State
     */
    public DeviceState getState()
    {
        DeviceState state = DeviceState.READY;
        return state;
    }

    /**
     * Update the Device listener
     * @param listener  Updated device listener
     */
    public void setDeviceListener(RCDeviceListener listener)
    {

    }
    public void setIncomingIntent(Intent intent)
    {
        //intent.putExtra(EXTRA_DEVICE, this);
        //intent.putExtra(EXTRA_CONNECTION, this);
        //intent.setAction("ACTION_INCOMING_CALL");
        pendingIntent = PendingIntent.getActivity(RCClient.getInstance().context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public RCConnection getPendingConnection()
    {
        return (RCConnection)DeviceImpl.GetInstance().sipuaConnectionListener;
    }

    public RCConnection getDevice()
    {
        return (RCConnection)DeviceImpl.GetInstance().sipuaConnectionListener;
    }

    /**
     * Should a ringing sound be played in a incoming connection or message
     * @param incomingSound  Whether or not the sound should be played
     */
    public void setIncomingSoundEnabled(boolean incomingSound)
    {

    }

    /**
     * Retrieve the incoming sound setting
     * @return  Whether the sound will be played
     */
    public boolean isIncomingSoundEnabled()
    {
        return true;
    }
    /**
     * Should a ringing sound be played in an outgoing connection or message
     * @param outgoingSound  Whether or not the sound should be played
     */
    public void setOutgoingSoundEnabled(boolean outgoingSound)
    {

    }
    /**
     * Retrieve the outgoint sound setting
     * @return  Whether the sound will be played
     */
    public boolean isOutgoingSoundEnabled()
    {
        return true;
    }

    /**
     * Should a disconnect sound be played when disconnecting a connection
     * @param disconnectSound  Whether or not the sound should be played
     */
    public void setDisconnectSoundEnabled(boolean disconnectSound)
    {

    }

    /**
     * Retrieve the disconnect sound setting
     * @return  Whether the sound will be played
     */
    public boolean isDisconnectSoundEnabled()
    {
        return true;
    }

    /**
     * Update prefernce parameters such as username/password
     * @param params  The params to be updated
     */
    public void updateParams(HashMap<String, String> params)
    {
        if (haveConnectivity()) {
            for (String key : params.keySet()) {
                if (key.equals("pref_proxy_ip")) {
                    sipProfile.setRemoteIp(params.get(key));
                } else if (key.equals("pref_proxy_port")) {
                    sipProfile.setRemotePort(Integer.parseInt(params.get(key)));
                } else if (key.equals("pref_sip_user")) {
                    sipProfile.setSipUserName(params.get(key));
                } else if (key.equals("pref_sip_password")) {
                    sipProfile.setSipPassword(params.get(key));
                }
            }
            DeviceImpl.GetInstance().Register();
        }
    }

    // SipUA listeners
    public void onSipUAConnectionArrived(SipEvent event)
    {
        RCConnectionListener connectionListener = (RCConnectionListener)this.listener;
        RCConnection connection = new RCConnection(connectionListener);
        connection.incoming = true;
        connection.state = RCConnection.ConnectionState.CONNECTING;
        DeviceImpl.GetInstance().sipuaConnectionListener = connection;

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // bring the App to front
                try {
                    Intent dataIntent = new Intent();
                    dataIntent.setAction("ACTION_INCOMING_CALL");
                    pendingIntent.send(RCClient.getInstance().context, 0, dataIntent);

                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
                //listener.onIncomingConnection(finalDevice, finalConnection);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onSipUAMessageArrived(SipEvent event)
    {
        HashMap<String, String> parameters = new HashMap<String, String>();
        // filter out SIP URI stuff and leave just the name
        String from = event.from.replaceAll("^<sip:", "").replaceAll("@.*$", "");
        parameters.put("username", from);

        final String finalContent = new String(event.content);
        final HashMap<String, String> finalParameters = new HashMap<String, String>(parameters);
        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // bring the App to front
                try {
                    Intent dataIntent = new Intent();
                    dataIntent.setAction("ACTION_INCOMING_MESSAGE");
                    dataIntent.putExtra("MESSAGE_PARMS", finalParameters);
                    dataIntent.putExtra("MESSAGE", finalContent);
                    pendingIntent.send(RCClient.getInstance().context, 0, dataIntent);
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
                //listener.onIncomingMessage(finalDevice, finalContent, finalParameters);
            }
        };
        mainHandler.post(myRunnable);
    }

    // Helpers
    private boolean haveConnectivity()
    {
        RCClient client = RCClient.getInstance();
        WifiManager wifi = (WifiManager)client.context.getSystemService(client.context.WIFI_SERVICE);
        if (wifi.isWifiEnabled()) {
            return true;
        }
        else {
            if (this.listener != null) {
                this.listener.onStopListening(this, RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal(), RCClient.errorText(RCClient.ErrorCodes.NO_CONNECTIVITY));
            }
            return false;
        }
    }

    public void setupWebrtc(GLSurfaceView videoView, Activity activity, SharedPreferences prefs, int viewId)
    {
        Context context = RCClient.getInstance().context;
        //Thread.setDefaultUncaughtExceptionHandler(
        //        new UnhandledExceptionHandler(this));

        /*
        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        activity.setContentView(viewId);
        */

        iceConnected = false;
        signalingParameters = null;
        scalingType = ScalingType.SCALE_ASPECT_FILL;

        // Create UI controls.
        //videoView = (GLSurfaceView) findViewById(R.id.glview_call);
        //callFragment = new CallFragment();
        //hudFragment = new HudFragment();

        // Create video renderers.
        VideoRendererGui.setView(videoView, new Runnable() {
            @Override
            public void run() {
                createPeerConnectionFactory();
            }
        });
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        // Show/hide call control fragment on view click.
        videoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //toggleCallControlFragmentVisibility();
            }
        });

        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                activity.setResult(activity.RESULT_CANCELED);
                activity.finish();
                return;
            }
        }

        // Get video resolution from settings.
        int videoWidth = 0;
        int videoHeight = 0;
        String resolution = prefs.getString(keyprefResolution, context.getString(R.string.pref_resolution_default));
        String[] dimensions = resolution.split("[ x]+");
        if (dimensions.length == 2) {
            try {
                videoWidth = Integer.parseInt(dimensions[0]);
                videoHeight = Integer.parseInt(dimensions[1]);
            } catch (NumberFormatException e) {
                videoWidth = 0;
                videoHeight = 0;
                Log.e(TAG, "Wrong video resolution setting: " + resolution);
            }
        }

        // Get camera fps from settings.
        int cameraFps = 0;
        String fps = prefs.getString(keyprefFps, context.getString(R.string.pref_fps_default));
        String[] fpsValues = fps.split("[ x]+");
        if (fpsValues.length == 2) {
            try {
                cameraFps = Integer.parseInt(fpsValues[0]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Wrong camera fps setting: " + fps);
            }
        }

        // Get video and audio start bitrate.
        int videoStartBitrate = 0;
        String bitrateTypeDefault = context.getString(R.string.pref_startvideobitrate_default);
        String bitrateType = prefs.getString(keyprefVideoBitrateType, bitrateTypeDefault);
        if (!bitrateType.equals(bitrateTypeDefault)) {
            String bitrateValue = prefs.getString(keyprefVideoBitrateValue, context.getString(R.string.pref_startvideobitratevalue_default));
            videoStartBitrate = Integer.parseInt(bitrateValue);
        }

        int audioStartBitrate = 0;
        bitrateTypeDefault = context.getString(R.string.pref_startaudiobitrate_default);
        bitrateType = prefs.getString(
                keyprefAudioBitrateType, bitrateTypeDefault);
        if (!bitrateType.equals(bitrateTypeDefault)) {
            String bitrateValue = prefs.getString(keyprefAudioBitrateValue, context.getString(R.string.pref_startaudiobitratevalue_default));
            audioStartBitrate = Integer.parseInt(bitrateValue);
        }

        // TODO: could make that configurable
        boolean loopback = false;  //intent.getBooleanExtra(EXTRA_LOOPBACK, false);
        peerConnectionParameters = new PeerConnectionParameters(
                false, //prefs.getBoolean(keyprefVideoCallEnabled, Boolean.valueOf(context.getString(R.string.pref_videocall_default))),
                loopback,
                videoWidth,
                videoHeight,
                cameraFps,
                videoStartBitrate,
                prefs.getString(keyprefVideoCodec, context.getString(R.string.pref_videocodec_default)),
                prefs.getBoolean(keyprefHwCodecAcceleration, Boolean.valueOf(context.getString(R.string.pref_hwcodec_default))),
                audioStartBitrate,
                prefs.getString(keyprefAudioCodec, context.getString(R.string.pref_audiocodec_default)),
                prefs.getBoolean(keyprefNoAudioProcessingPipeline, Boolean.valueOf(context.getString(R.string.pref_noaudioprocessing_default))),
                prefs.getBoolean(keyprefCpuUsageDetection, Boolean.valueOf(context.getString(R.string.pref_cpu_usage_detection_default))));

        // not interested in command line run (at least not yet)
        //commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
        //runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

        //String roomUrl = prefs.getString(keyprefRoomServerUrl, getString(R.string.pref_room_server_url_default));

        // Check statistics display option.
        boolean displayHud = prefs.getBoolean(keyprefDisplayHud,
                Boolean.valueOf(context.getString(R.string.pref_displayhud_default)));

        /*
        // Get Intent parameters.
        final Intent intent = getIntent();
        Uri roomUri = intent.getData();
        if (roomUri == null) {
            logAndToast(activity.getString(R.string.missing_url));
            Log.e(TAG, "Didn't get any URL in intent!");
            activity.setResult(activity.RESULT_CANCELED);
            activity.finish();
            return;
        }
        String roomId = intent.getStringExtra(EXTRA_ROOMID);
        if (roomId == null || roomId.length() == 0) {
            logAndToast(activity.getString(R.string.missing_url));
            Log.e(TAG, "Incorrect room ID in intent!");
            activity.setResult(activity.RESULT_CANCELED);
            activity.finish();
            return;
        }
        */

        // Create connection client and connection parameters.
        /*
        appRtcClient = new WebSocketRTCClient(this, new LooperExecutor());
        roomConnectionParameters = new RoomConnectionParameters(
                roomUri.toString(), roomId, loopback);
        */
        // Send intent arguments to fragments.
        //callFragment.setArguments(intent.getExtras());
        //hudFragment.setArguments(intent.getExtras());
        // Activate call and HUD fragments and start the call.
        //FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
        //ft.add(callFragmentContainer, callFragment);
        //ft.add(hudFragmentContainer, hudFragment);
        //ft.commit();
        //startCall();

        /*
        // For command line execution run connection for <runTimeMs> and exit.
        if (commandLineRun && runTimeMs > 0) {
            videoView.postDelayed(new Runnable() {
                public void run() {
                    disconnect();
                }
            }, runTimeMs);
        }
        */
    }

    ////////////// Helpers
    private void startCall() {
        /*
        if (appRtcClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.");
            return;
        }
        */
        callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        logAndToast("Preparing call");

        // don't call using JAIN SIP just yet; we need sdp first
        //appRtcClient.connectToRoom(roomConnectionParameters);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(RCClient.getInstance().context, new Runnable() {
                    // This method will be called each time the audio state (number and
                    // type of devices) has been changed.
                    @Override
                    public void run() {
                        onAudioManagerChangedState();
                    }
                }
        );
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Initializing the audio manager...");
        audioManager.init();

        // we don't have room functionality to notify us when ready; instead, we start connecting right now
        this.onConnectedToRoom(signalingParameters);
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    public void disconnect() {
        activityRunning = false;
        /* Signaling is already disconnected
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }
        */
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.close();
            audioManager = null;
        }
        /*
        if (iceConnected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
        */
    }

    private void onAudioManagerChangedState() {
        // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
        // is active.
    }


    // Create peer connection factory when EGL context is ready.
    private void createPeerConnectionFactory() {
        final RCDevice device = this;
        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    final long delta = System.currentTimeMillis() - callStartedTimeMs;
                    Log.d(TAG, "Creating peer connection factory, delay=" + delta + "ms");
                    peerConnectionClient = PeerConnectionClient.getInstance();
                    peerConnectionClient.createPeerConnectionFactory(RCClient.getInstance().context,
                            VideoRendererGui.getEGLContext(), peerConnectionParameters,
                            device);
                }
                if (signalingParameters != null) {
                    Log.w(TAG, "EGL context is ready after room connection.");
                    onConnectedToRoomInternal(signalingParameters);
                }
            }
        };
        mainHandler.post(myRunnable);
        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    final long delta = System.currentTimeMillis() - callStartedTimeMs;
                    Log.d(TAG, "Creating peer connection factory, delay=" + delta + "ms");
                    peerConnectionClient = PeerConnectionClient.getInstance();
                    peerConnectionClient.createPeerConnectionFactory(CallActivity.this,
                            VideoRendererGui.getEGLContext(), peerConnectionParameters,
                            CallActivity.this);
                }
                if (signalingParameters != null) {
                    Log.w(TAG, "EGL context is ready after room connection.");
                    onConnectedToRoomInternal(signalingParameters);
                }
            }
        });
        */
    }

    // Helper functions.
    /*
    private void toggleCallControlFragmentVisibility() {
        if (!iceConnected || !callFragment.isAdded()) {
            return;
        }
        // Show/hide call control fragment
        callControlFragmentVisible = !callControlFragmentVisible;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (callControlFragmentVisible) {
            ft.show(callFragment);
            ft.show(hudFragment);
        } else {
            ft.hide(callFragment);
            ft.hide(hudFragment);
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }
    */

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        /* TODO: uncomment this when we found why we are breaking
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(RCClient.getInstance().context, msg, Toast.LENGTH_SHORT);
        logToast.show();
        */
    }

    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    @Override
    public void onLocalDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        final RCDevice device = this;
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (signalingParameters != null && !signalingParameters.sipUri.isEmpty()) {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
                    if (signalingParameters.initiator) {
                        // keep it around so that we combine it with candidates before sending it over
                        device.signalingParameters.offerSdp = sdp;
                        //appRtcClient.sendOfferSdp(sdp);

                    } else {
                        //appRtcClient.sendAnswerSdp(sdp);
                    }
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        final RCDevice device = this;
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                device.signalingParameters.iceCandidates.add(candidate);
                /*
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidate(candidate);
                }
                */
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        final RCDevice device = this;

        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected, delay=" + delta + "ms");
                iceConnected = true;
                //callConnected();

                // we have gathered all candidates and SDP. Combine then in SIP SDP and send over to JAIN SIP
                DeviceImpl.GetInstance().CallWebrtc(signalingParameters.sipUri, device.signalingParameters.generateSipSDP());
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onIceDisconnected() {
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
                iceConnected = false;
                disconnect();
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onPeerConnectionClosed() {
    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isError && iceConnected) {
                    //hudFragment.updateEncoderStatistics(reports);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onPeerConnectionError(final String description) {
        //reportError(description);
        Log.e(TAG, "PeerConnection error");
        disconnect();
    }


    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.
    //@Override
    public void onConnectedToRoom(final SignalingParameters params) {
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        };
        mainHandler.post(myRunnable);
    }

    private void onConnectedToRoomInternal(final SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        if (peerConnectionClient == null) {
            Log.w(TAG, "Room is connected, but EGL context is not ready yet.");
            return;
        }
        logAndToast("Creating peer connection, delay=" + delta + "ms");
        peerConnectionClient.createPeerConnection(
                localRender, remoteRender, signalingParameters);

        if (signalingParameters.initiator) {
            logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createOffer();
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                logAndToast("Creating ANSWER...");
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createAnswer();
            }
            if (params.iceCandidates != null) {
                // Add remote ICE candidates from room.
                for (IceCandidate iceCandidate : params.iceCandidates) {
                    peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                }
            }
        }
    }

    public void onRemoteDescription(String sdpString) {
        onRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, sdpString));
    }

    //@Override
    public void onRemoteDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
                peerConnectionClient.setRemoteDescription(sdp);
                if (!signalingParameters.initiator) {
                    logAndToast("Creating ANSWER...");
                    // Create answer. Answer SDP will be sent to offering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createAnswer();
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    //@Override
    public void onRemoteIceCandidate(final IceCandidate candidate) {
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG,
                            "Received ICE candidate for non-initilized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        };
        mainHandler.post(myRunnable);
    }

    //@Override
    public void onChannelClose() {
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        };
        mainHandler.post(myRunnable);
    }
}
