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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;

import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.SipUADeviceListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

/**
 *  RCDevice Represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
 *  in order to be notified of RestComm Client events you need to set a listener to RCDevice and implement the applicable methods, and also 'register'
 *  to the applicable intents by calling RCDevice.setPendingIntents() and provide one intent for whichever activity will be receiving calls and another
 *  intent for the activity receiving messages.
 *  If you want to initiate a media connection towards another party you use RCDevice.connect() which returns an RCConnection object representing
 *  the new outgoing connection. From then on you can act on the new connection by applying RCConnection methods on the handle you got from RCDevice.connect().
 *  If there’s an incoming connection you will be receiving an intent with action 'RCDevice.INCOMING_CALL'. At that point you can use RCConnection methods to
 *  accept or reject the connection.
 *
 *  As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 *  through an intent with action 'RCDevice.INCOMING_MESSAGE'.
 *
 *  @see RCConnection
 */

public class RCDevice extends BroadcastReceiver implements SipUADeviceListener, AudioManager.OnAudioFocusChangeListener  {
    /**
     * @abstract Device state
     */
    static DeviceState state;
    /**
     * @abstract Device capabilities (<b>Not Implemented yet</b>)
     */
    HashMap<DeviceCapability, Object> capabilities;
    /**
     * @abstract Listener that will be receiving RCDevice events described at RCDeviceListener
     */
    RCDeviceListener listener;
    /**
     * @abstract Is sound for incoming connections enabled
     */
    boolean incomingSoundEnabled;
    /**
     * @abstract Is sound for outgoing connections enabled
     */
    boolean outgoingSoundEnabled;
    /**
     * @abstract Is sound for disconnect enabled
     */
    boolean disconnectSoundEnabled;

    /**
     * Device state
     */
    public enum DeviceState {
        OFFLINE, /** Device is offline */
        READY, /** Device is ready to make and receive connections */
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

    public enum ReachabilityState {
        REACHABILITY_WIFI,
        REACHABILITY_MOBILE,
        REACHABILITY_NONE,
    }

    private static final String TAG = "RCDevice";
    //private static boolean online = false;
    public static String OUTGOING_CALL = "ACTION_OUTGOING_CALL";
    public static String INCOMING_CALL = "ACTION_INCOMING_CALL";
    public static String OPEN_MESSAGE_SCREEN = "ACTION_OPEN_MESSAGE_SCREEN";
    public static String INCOMING_MESSAGE = "ACTION_INCOMING_MESSAGE";
    public static String INCOMING_MESSAGE_TEXT = "INCOMING_MESSAGE_TEXT";
    public static String INCOMING_MESSAGE_PARAMS = "INCOMING_MESSAGE_PARAMS";
    public static String EXTRA_DID = "com.telestax.restcomm_messenger.DID";
    public static String EXTRA_VIDEO_ENABLED = "com.telestax.restcomm_messenger.VIDEO_ENABLED";
    public static String EXTRA_SDP = "com.telestax.restcomm_messenger.SDP";
    //public static String EXTRA_DEVICE = "com.telestax.restcomm.android.client.sdk.extra-device";
    //public static String EXTRA_CONNECTION = "com.telestax.restcomm.android.client.sdk.extra-connection";
    HashMap<String, Object> parameters;
    PendingIntent pendingCallIntent;
    PendingIntent pendingMessageIntent;
    private RCConnection incomingConnection;
    private ReachabilityState reachabilityState = ReachabilityState.REACHABILITY_NONE;
    private SipProfile sipProfile = null;
    //MediaPlayer messagePlayer;
    //AudioManager audioManager;

    /**
     * Initialize a new RCDevice object
     *
     * @param parameters RCDevice parameters
     * @param deviceListener  Listener of RCDevice
     */
    protected RCDevice(HashMap<String, Object> parameters, RCDeviceListener deviceListener) {
        //this.updateCapabilityToken(capabilityToken);
        this.listener = deviceListener;

        // TODO: check if those headers are needed
        HashMap<String, String> customHeaders = new HashMap<>();
        state = DeviceState.OFFLINE;

        // register broadcast receiver for reachability
        /*
        BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReachabilityChanged(checkReachability());
            }
        };
        */
        Context context = RCClient.getContext();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, filter);

        // initialize JAIN SIP if we have connectivity
        this.parameters = parameters;
        //RCClient client = RCClient.getInstance();
        reachabilityState = checkReachability();

        if (reachabilityState == ReachabilityState.REACHABILITY_WIFI ||
                reachabilityState == ReachabilityState.REACHABILITY_MOBILE) {
            initializeSignalling();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        onReachabilityChanged(checkReachability());
    }

    private void initializeSignalling()
    {
        sipProfile = new SipProfile();
        updateSipProfile(parameters);
        DeviceImpl deviceImpl = DeviceImpl.GetInstance();
        deviceImpl.Initialize(RCClient.getContext(), sipProfile);
        DeviceImpl.GetInstance().sipuaDeviceListener = this;
        // register after initialization
        DeviceImpl.GetInstance().Register();
        state = DeviceState.READY;
    }

    private ReachabilityState checkReachability()
    {
        ConnectivityManager cm = (ConnectivityManager) RCClient.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (null != activeNetwork) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                Log.w(TAG, "Reachability event: WIFI");
                return ReachabilityState.REACHABILITY_WIFI;
            }

            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                Log.w(TAG, "Reachability event: MOBILE");
                return ReachabilityState.REACHABILITY_MOBILE;
            }
        }
        Log.w(TAG, "Reachability event: NONE");
        return ReachabilityState.REACHABILITY_NONE;
    }

    private void onReachabilityChanged(final ReachabilityState newState)
    {
        //final RCDevice device = this;
        //final ReachabilityState state = newState;

        // important: post this in the main thread in next loop as broadcast receivers have issues with asynchronous operations. Not sure
        // what JAIN does behind the scenes but I got crashes when trying shut down jain without 'post'ing below
        /*
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
            */

        if (newState == ReachabilityState.REACHABILITY_NONE && state != DeviceState.OFFLINE) {
            Log.w(TAG, "Reachability changed; no connectivity");
            // TODO: here we need to unregister before shutting down, but for that we need to wait for the unREGISTER reply, which complicates things
            DeviceImpl.GetInstance().Shutdown();
            sipProfile = null;
            state = DeviceState.OFFLINE;
            reachabilityState = newState;
            return;
        }

        // old state wifi and new state mobile or the reverse; need to shutdown and restart network facilities
        if ((reachabilityState == ReachabilityState.REACHABILITY_WIFI && newState == ReachabilityState.REACHABILITY_MOBILE) ||
                (reachabilityState == ReachabilityState.REACHABILITY_MOBILE && newState == ReachabilityState.REACHABILITY_WIFI)) {
            if (state != DeviceState.OFFLINE) {
                Log.w(TAG, "Reachability action: wifi/mobile available from movile/wifi. Device state: " + state);
                // stop JAIN
                DeviceImpl.GetInstance().Shutdown();
                sipProfile = null;
                state = DeviceState.OFFLINE;
                // start JAIN
                initializeSignalling();
                reachabilityState = newState;
                return;
            }
        }

        if ((newState == ReachabilityState.REACHABILITY_WIFI || newState == ReachabilityState.REACHABILITY_MOBILE)
                && state != DeviceState.READY) {
            Log.w(TAG, "Reachability action: wifi/mobile available. Device state: " + state);
            initializeSignalling();
            reachabilityState = newState;
        }

        /*
            }
        };
        mainHandler.post(myRunnable);
        */
    }

    public ReachabilityState getReachability()
    {
        return reachabilityState;
    }

    public void shutdown() {
        this.listener = null;

        if (DeviceImpl.isInitialized()) {
            DeviceImpl.GetInstance().Shutdown();
        }
        state = DeviceState.OFFLINE;
    }

    // 'Copy' constructor
    public RCDevice(RCDevice device) {
        //this.state = device.state;
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
     * Start listening for incoming connections
     */
    public void listen() {
        if (state == DeviceState.READY) {
            DeviceImpl.GetInstance().Register();
        }
    }

    /**
     * Stop listeninig for incoming connections
     */
    public void unlisten() {
        if (state == DeviceState.READY) {
            DeviceImpl.GetInstance().Unregister();
        }
    }

    /**
     * Update Device listener to be receiving Device related events. This is
     * usually needed when we switch activities and want the new activity to receive
     * events
     * @param listener  New device listener
     */
    public void setDeviceListener(RCDeviceListener listener)
    {
        this.listener = listener;
        //DeviceImpl.GetInstance().sipuaConnectionListener = this;
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
     * @param parameters Parameters such as the endpoint we want to connect to or SIP custom headers. If
     *                   you want to pass SIP custom headers, you need to add a separate (String, String) HashMap
     *                   inside 'parameters' hash and introduce your headers there.
     *                   For an example please check HelloWorld or Messenger samples.
     * @param listener   The listener object that will receive events when the connection state changes
     * @return An RCConnection object representing the new connection or null in case of error. Error
     *                   means that RCDevice.state not ready to make a call (this usually means no WiFi available)
     */
    public RCConnection connect(Map<String, Object> parameters, RCConnectionListener listener) {
        //Activity activity = (Activity) listener;
        if (state == DeviceState.READY) {
            Log.i(TAG, "RCDevice.connect(), with connectivity");

            Boolean enableVideo = (Boolean)parameters.get("video-enabled");
            RCConnection connection = new RCConnection(listener);
            connection.incoming = false;
            connection.state = RCConnection.ConnectionState.PENDING;
            DeviceImpl.GetInstance().sipuaConnectionListener = connection;

            // create a new hash map
            HashMap<String, String> sipHeaders = null;
            if (parameters.containsKey("sip-headers")) {
                sipHeaders = (HashMap<String, String>)parameters.get("sip-headers");
            }
            connection.setupWebrtcAndCall((String)parameters.get("username"), sipHeaders, enableVideo.booleanValue());
            state = DeviceState.BUSY;

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
        if (state == DeviceState.READY) {
            DeviceImpl.GetInstance().SendMessage(parameters.get("username"), message);
            /*
            int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                messagePlayer.start();
            }
            */

            return true;
        } else {
            return false;
        }
    }

    /**
     * Disconnect all connections
     */
    public void disconnectAll() {
        if (state == DeviceState.BUSY) {
            // TODO: currently only support one live connection. Maybe would be a better idea to use a separate reference to the active RCConnection
            RCConnection connection = (RCConnection)DeviceImpl.GetInstance().sipuaConnectionListener;
            connection.disconnect();
            state = DeviceState.READY;
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
        return state;
    }

    /**
     * Set pending intents for incoming calls and messages. In order to be notified of RestComm Client
     * events you need to associate your Activities with intents and provide one intent for whichever activity
     * will be receiving calls and another intent for the activity receiving messages. If you use a single Activity
     * for both then you can pass the same intent both as a callIntent as well as a messageIntent
     *
     * @param callIntent: an intent that will be sent on an incoming call
     * @param messageIntent: an intent that will be sent on an incoming text message
     */
    public void setPendingIntents(Intent callIntent, Intent messageIntent) {
        pendingCallIntent = PendingIntent.getActivity(RCClient.getContext(), 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        pendingMessageIntent = PendingIntent.getActivity(RCClient.getContext(), 0, messageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public RCConnection getPendingConnection() {
        return incomingConnection;
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
        DeviceImpl.GetInstance().soundManager.setIncoming(incomingSound);
    }

    /**
     * Retrieve the incoming sound setting
     *
     * @return Whether the sound will be played
     */
    public boolean isIncomingSoundEnabled() {
        return DeviceImpl.GetInstance().soundManager.getIncoming();
    }

    /**
     * Should a ringing sound be played in an outgoing connection or message
     *
     * @param outgoingSound Whether or not the sound should be played
     */
    public void setOutgoingSoundEnabled(boolean outgoingSound) {
        DeviceImpl.GetInstance().soundManager.setOutgoing(outgoingSound);
    }

    /**
     * Retrieve the outgoint sound setting
     *
     * @return Whether the sound will be played
     */
    public boolean isOutgoingSoundEnabled() {
        return DeviceImpl.GetInstance().soundManager.getOutgoing();
    }

    /**
     * Should a disconnect sound be played when disconnecting a connection
     *
     * @param disconnectSound Whether or not the sound should be played
     */
    public void setDisconnectSoundEnabled(boolean disconnectSound) {
        DeviceImpl.GetInstance().soundManager.setDisconnect(disconnectSound);
    }

    /**
     * Retrieve the disconnect sound setting
     *
     * @return Whether the sound will be played
     */
    public boolean isDisconnectSoundEnabled() {
        return DeviceImpl.GetInstance().soundManager.getDisconnect();
    }

    /**
     * Update prefernce parameters such as username/password
     *
     * @param params The params to be updated
     * @return Whether the update was successful or not
     */
    public boolean updateParams(HashMap<String, Object> params) {
        if (state == DeviceState.READY) {
            updateSipProfile(params);
            DeviceImpl.GetInstance().Register();
            return true;
        }
        else {
            return false;
        }
    }

    public void updateSipProfile(HashMap<String, Object> params) {
        if (params != null) {
            for (String key : params.keySet()) {
                if (key.equals("pref_proxy_ip")) {
                    sipProfile.setRemoteIp((String) params.get(key));
                } else if (key.equals("pref_proxy_port")) {
                    sipProfile.setRemotePort(Integer.parseInt((String) params.get(key)));
                } else if (key.equals("pref_sip_user")) {
                    sipProfile.setSipUserName((String) params.get(key));
                } else if (key.equals("pref_sip_password")) {
                    sipProfile.setSipPassword((String) params.get(key));
                }
            }
        }
    }

    /**
     * INTERNAL: not to be used from the Application
     */
    public void onSipUAConnectionArrived(SipEvent event) {
        //RCConnectionListener connectionListener = (RCConnectionListener) this.listener;
        incomingConnection = new RCConnection();
        incomingConnection.incoming = true;
        incomingConnection.state = RCConnection.ConnectionState.CONNECTING;
        incomingConnection.incomingCallSdp = event.sdp;
        //incomingConnection.initializeWebrtc();
        DeviceImpl.GetInstance().sipuaConnectionListener = incomingConnection;
        state = DeviceState.BUSY;

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        final String from = event.from;
        //final String sdp = event.sdp;
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // bring the App to front
                try {
                    Intent dataIntent = new Intent();
                    dataIntent.setAction(INCOMING_CALL);
                    dataIntent.putExtra(RCDevice.EXTRA_DID, from);
                    pendingCallIntent.send(RCClient.getContext(), 0, dataIntent);

                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    /**
     * INTERNAL: not to be used from the Application
     */
    public void onSipUAMessageArrived(SipEvent event) {
        HashMap<String, String> parameters = new HashMap<String, String>();
        // filter out SIP URI stuff and leave just the name
        String from = event.from.replaceAll("^<", "").replaceAll(">$", "");
        //String from = event.from.replaceAll("^<sip:", "").replaceAll("@.*$", "");
        parameters.put("username", from);

        final String finalContent = new String(event.content);
        final HashMap<String, String> finalParameters = new HashMap<String, String>(parameters);
        final RCDevice device = this;
        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // bring the App to front
                try {
                    Intent dataIntent = new Intent();
                    dataIntent.setAction(INCOMING_MESSAGE);
                    dataIntent.putExtra(INCOMING_MESSAGE_PARAMS, finalParameters);
                    dataIntent.putExtra(INCOMING_MESSAGE_TEXT, finalContent);
                    pendingMessageIntent.send(RCClient.getContext(), 0, dataIntent);
                    /*
                    int result = audioManager.requestAudioFocus(device, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        messagePlayer.start();
                    }
                    */

                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
                //listener.onIncomingMessage(finalDevice, finalContent, finalParameters);
            }
        };
        mainHandler.post(myRunnable);
    }

    // Helpers
    /*
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
    */

    // Callbacks for audio focus change events
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

}
