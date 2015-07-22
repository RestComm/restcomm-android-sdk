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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.Handler;

import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.SipUADeviceListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

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

public class RCDevice implements SipUADeviceListener {
    /**
     * @abstract Device state (<b>Not Implemented yet</b>: device is always READY)
     */
    DeviceState state;
    /**
     * @abstract Device capabilities (<b>Not Implemented yet</b>)
     */
    HashMap<DeviceCapability, Object> capabilities;
    /**
     * @abstract Listener that will be receiving RCDevice events described at RCDeviceListener
     */
    RCDeviceListener listener;
    /**
     * @abstract Is sound for incoming connections enabled (<b>Not Implemented yet</b>)
     */
    boolean incomingSoundEnabled;
    /**
     * @abstract Is sound for outgoing connections enabled (<b>Not Implemented yet</b>)
     */
    boolean outgoingSoundEnabled;
    /**
     * @abstract Is sound for disconnect enabled (<b>Not Implemented yet</b>)
     */
    boolean disconnectSoundEnabled;

    private SipProfile sipProfile;

    /**
     * Device state (<b>Not Implemented yet</b>)
     */
    public enum DeviceState {
        OFFLINE, /**
         * Device is offline
         */
        READY, /**
         * Device is ready to make and receive connections
         */
        BUSY,  /** Device is busy */
    }

    /**
     * Device capability (<b>Not Implemented yet</b>)
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

    private static final String TAG = "RCDevice";
    public static String OUTGOING_CALL = "ACTION_OUTGOING_CALL";
    public static String INCOMING_CALL = "ACTION_INCOMING_CALL";
    public static String INCOMING_MESSAGE = "ACTION_INCOMING_MESSAGE";
    public static String EXTRA_DID = "com.telestax.restcomm_messenger.DID";
    public static String EXTRA_SDP = "com.telestax.restcomm_messenger.SDP";
    //public static String EXTRA_DEVICE = "com.telestax.restcomm.android.client.sdk.extra-device";
    //public static String EXTRA_CONNECTION = "com.telestax.restcomm.android.client.sdk.extra-connection";
    PendingIntent pendingIntent;
    public RCConnection incomingConnection;

    /*
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
    private SignalingParameters signalingParameters;
    private AppRTCAudioManager audioManager = null;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private ScalingType scalingType;
    private Toast logToast;
    private boolean commandLineRun;
    private int runTimeMs;
    private boolean activityRunning;
    private PeerConnectionParameters peerConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs = 0;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET"
    };
    */

    /**
     * Initialize a new RCDevice object
     *
     * @param capabilityToken Capability Token
     * @param deviceListener  Listener of RCDevice
     */
    protected RCDevice(String capabilityToken, RCDeviceListener deviceListener) {
        this.updateCapabilityToken(capabilityToken);
        this.listener = deviceListener;

        sipProfile = new SipProfile();
        // TODO: check if those headers are needed
        HashMap<String, String> customHeaders = new HashMap<>();

        DeviceImpl deviceImpl = DeviceImpl.GetInstance();
        deviceImpl.Initialize(RCClient.getInstance().context, sipProfile, customHeaders);
        DeviceImpl.GetInstance().sipuaDeviceListener = this;
    }

    /*
    public void initializeWebrtc(GLSurfaceView videoView, SharedPreferences prefs)
    {
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

        setupWebrtc(videoView, prefs);
    }
    */

    // 'Copy' constructor
    public RCDevice(RCDevice device) {
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
    public void release() {

    }

    /**
     * Start listening for incoming connections (<b>Not Implemented yet</b>: for now once the RCDevice is created we are always listening for incoming connections)
     */
    public void listen() {

    }

    /**
     * Stop listeninig for incoming connections (Not Implemented yet)
     */
    public void unlisten() {

    }

    /**
     * Retrieves the capability token passed to RCClient.createDevice
     *
     * @return Capability token
     */
    public String getCapabilityToken() {
        return "";
    }

    /**
     * Updates the capability token (<b>Not implemented yet</b>
     */
    public void updateCapabilityToken(String token) {

    }

    /**
     * Create an outgoing connection to an endpoint
     *
     * @param parameters Connections such as the endpoint we want to connect to
     * @param listener   The listener object that will receive events when the connection state changes
     * @return An RCConnection object representing the new connection
     */
    public RCConnection connect(Map<String, String> parameters, RCConnectionListener listener) {
        return connect(parameters, listener, null, null);
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
                                SharedPreferences prefs) {
        Activity activity = (Activity) listener;
        if (haveConnectivity()) {
            RCConnection connection = new RCConnection(listener);
            connection.incoming = false;
            connection.state = RCConnection.ConnectionState.PENDING;
            DeviceImpl.GetInstance().sipuaConnectionListener = connection;
            connection.setupWebrtcAndCall(videoView, prefs, parameters.get("username"));

            return connection;
        } else {
            return null;
        }
    }

    /**
     * Send an instant message to a an endpoint
     *
     * @param message    Message text
     * @param parameters Parameters used for the message, such as 'username' that holds the recepient for the message
     */
    public boolean sendMessage(String message, Map<String, String> parameters) {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().SendMessage(parameters.get("username"), message);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Disconnect all connections (<b>Not implemented yet</b>)
     */
    public void disconnectAll() {
        if (haveConnectivity()) {
            // TODO: disconnect open connections
        }
    }

    /**
     * Retrieve the capabilities
     *
     * @return Capabilities
     */
    public Map<DeviceCapability, Object> getCapabilities() {
        HashMap<DeviceCapability, Object> map = new HashMap<DeviceCapability, Object>();
        return map;
    }

    /**
     * Retrieve the Device state
     *
     * @return State
     */
    public DeviceState getState() {
        DeviceState state = DeviceState.READY;
        return state;
    }

    /**
     * Update the Device listener
     *
     * @param listener Updated device listener
     */
    public void setDeviceListener(RCDeviceListener listener) {

    }

    public void setIncomingIntent(Intent intent) {
        //intent.putExtra(EXTRA_DEVICE, this);
        //intent.putExtra(EXTRA_CONNECTION, this);
        //intent.setAction("ACTION_INCOMING_CALL");
        pendingIntent = PendingIntent.getActivity(RCClient.getInstance().context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public RCConnection getPendingConnection() {
        return (RCConnection) DeviceImpl.GetInstance().sipuaConnectionListener;
    }

    public RCConnection getDevice() {
        return (RCConnection) DeviceImpl.GetInstance().sipuaConnectionListener;
    }

    /**
     * Should a ringing sound be played in a incoming connection or message
     *
     * @param incomingSound Whether or not the sound should be played
     */
    public void setIncomingSoundEnabled(boolean incomingSound) {

    }

    /**
     * Retrieve the incoming sound setting
     *
     * @return Whether the sound will be played
     */
    public boolean isIncomingSoundEnabled() {
        return true;
    }

    /**
     * Should a ringing sound be played in an outgoing connection or message
     *
     * @param outgoingSound Whether or not the sound should be played
     */
    public void setOutgoingSoundEnabled(boolean outgoingSound) {

    }

    /**
     * Retrieve the outgoint sound setting
     *
     * @return Whether the sound will be played
     */
    public boolean isOutgoingSoundEnabled() {
        return true;
    }

    /**
     * Should a disconnect sound be played when disconnecting a connection
     *
     * @param disconnectSound Whether or not the sound should be played
     */
    public void setDisconnectSoundEnabled(boolean disconnectSound) {

    }

    /**
     * Retrieve the disconnect sound setting
     *
     * @return Whether the sound will be played
     */
    public boolean isDisconnectSoundEnabled() {
        return true;
    }

    /**
     * Update prefernce parameters such as username/password
     *
     * @param params The params to be updated
     */
    public void updateParams(HashMap<String, String> params) {
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
    public void onSipUAConnectionArrived(SipEvent event) {
        //RCConnectionListener connectionListener = (RCConnectionListener) this.listener;
        incomingConnection = new RCConnection();
        incomingConnection.incoming = true;
        incomingConnection.state = RCConnection.ConnectionState.CONNECTING;
        DeviceImpl.GetInstance().sipuaConnectionListener = incomingConnection;

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        final String from = event.from;
        final String sdp = event.sdp;
        Handler mainHandler = new Handler(RCClient.getInstance().context.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // bring the App to front
                try {
                    Intent dataIntent = new Intent();
                    dataIntent.setAction(INCOMING_CALL);
                    dataIntent.putExtra(RCDevice.EXTRA_DID, from);
                    dataIntent.putExtra(RCDevice.EXTRA_SDP, sdp);
                    pendingIntent.send(RCClient.getInstance().context, 0, dataIntent);

                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
                //listener.onIncomingConnection(finalDevice, finalConnection);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onSipUAMessageArrived(SipEvent event) {
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
                    dataIntent.setAction(INCOMING_MESSAGE);
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
    private boolean haveConnectivity() {
        RCClient client = RCClient.getInstance();
        WifiManager wifi = (WifiManager) client.context.getSystemService(client.context.WIFI_SERVICE);
        if (wifi.isWifiEnabled()) {
            return true;
        } else {
            if (this.listener != null) {
                this.listener.onStopListening(this, RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal(), RCClient.errorText(RCClient.ErrorCodes.NO_CONNECTIVITY));
            }
            return false;
        }
    }
}