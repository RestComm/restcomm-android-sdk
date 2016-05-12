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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import org.mobicents.restcomm.android.sipua.RCLogger;

import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.SipUADeviceListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

/**
 *  RCDevice Represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
 *  in order to be notified of RestComm Client events you need to set a listener to RCDevice and implement the applicable methods, and also 'register'
 *  to the applicable intents by calling RCDevice.setPendingIntents() and provide one intent for whichever activity will be receiving calls and another
 *  intent for the activity receiving messages.
 *  If you want to initiate a media connection towards another party you use RCDevice.connect() which returns an RCConnection object representing
 *  the new outgoing connection. From then on you can act on the new connection by applying RCConnection methods on the handle you got from RCDevice.connect().
 *  If there is an incoming connection you will be receiving an intent with action 'RCDevice.INCOMING_CALL'. At that point you can use RCConnection methods to
 *  accept or reject the connection.
 *
 *  As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 *  through an intent with action 'RCDevice.INCOMING_MESSAGE'.
 *
 *  @see RCConnection
 */

public class RCDevice extends BroadcastReceiver implements SipUADeviceListener  {
    /**
     * Device state
     */
    static DeviceState state;
    /**
     * Device capabilities (<b>Not Implemented yet</b>)
     */
    HashMap<DeviceCapability, Object> capabilities;
    /**
     * Listener that will be receiving RCDevice events described at RCDeviceListener
     */
    RCDeviceListener listener;
    /**
     * Is sound for incoming connections enabled
     */
    boolean incomingSoundEnabled;
    /**
     * Is sound for outgoing connections enabled
     */
    boolean outgoingSoundEnabled;
    /**
     * Is sound for disconnect enabled
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

    /**
     * Configuration preferences.
     */
    public static class Prefs {
        public static final String USER_NAME = "pref_sip_user";
        public static final String DOMAIN = "pref_proxy_domain";
        public static final String AUTH_USER_NAME = "pref_sip_auth_user";
        public static final String PASSWORD = "pref_sip_password";
        public static final String PROXY = "pref_sip_proxy";
        public static final String VIDEO_ENABLED = "video-enabled";
        public static final String SIP_HEADERS = "sip-headers";
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
    private RCDeviceListener.RCConnectivityStatus reachabilityState = RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone;
    private SipProfile sipProfile = null;

    /**
     * Initialize a new RCDevice object
     *
     * @param parameters RCDevice parameters
     * @param deviceListener  Listener of RCDevice
     */
    protected RCDevice(HashMap<String, Object> parameters, RCDeviceListener deviceListener) {
        RCLogger.i(TAG, "RCDevice(): " + parameters.toString());
        //this.updateCapabilityToken(capabilityToken);
        this.listener = deviceListener;

        // TODO: check if those headers are needed
        HashMap<String, String> customHeaders = new HashMap<>();
        state = DeviceState.OFFLINE;

        // register broadcast receiver for reachability
        Context context = RCClient.getContext();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, filter);

        // initialize JAIN SIP if we have connectivity
        this.parameters = parameters;
        reachabilityState = DeviceImpl.checkReachability(RCClient.getContext());

        boolean connectivity = false;
        if (reachabilityState != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
            connectivity = true;
        }
        initializeSignalling(connectivity);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        onReachabilityChanged(DeviceImpl.checkReachability(RCClient.getContext()));
    }

    private void initializeSignalling(boolean connectivity)
    {
        RCLogger.i(TAG, "initializeSignalling()");
        sipProfile = new SipProfile();
        updateSipProfile(parameters);
        DeviceImpl deviceImpl = DeviceImpl.GetInstance();
        if (reachabilityState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi) {
            deviceImpl.Initialize(RCClient.getContext(), sipProfile, connectivity, SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi);
        }
        else {
            deviceImpl.Initialize(RCClient.getContext(), sipProfile, connectivity, SipManager.NetworkInterfaceType.NetworkInterfaceTypeCellularData);
        }
        DeviceImpl.GetInstance().sipuaDeviceListener = this;
        // register after initialization
        if (connectivity) {
            if (!parameters.containsKey(Prefs.DOMAIN) ||
                    parameters.containsKey(Prefs.DOMAIN) && parameters.get(Prefs.DOMAIN).equals("")) {
                // registrarless; we can transition to ready right away (i.e. without waiting for Restcomm to reply to REGISTER)
                state = DeviceState.READY;
            }
            else {
                DeviceImpl.GetInstance().Register();
            }
        }
    }

    private void onReachabilityChanged(final RCDeviceListener.RCConnectivityStatus newState)
    {
        if (newState == reachabilityState) {
            RCLogger.w(TAG, "Reachability event, but remained the same: " + newState);
            return;
        }

        if (newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone && state != DeviceState.OFFLINE) {
            RCLogger.w(TAG, "Reachability changed; no connectivity");
            DeviceImpl.GetInstance().unbind();
            state = DeviceState.OFFLINE;
            reachabilityState = newState;
            this.listener.onConnectivityUpdate(this, newState);
            return;
        }

        // old state wifi and new state mobile or the reverse; need to shutdown and restart network facilities
        if ((reachabilityState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi && newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular) ||
                (reachabilityState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular && newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi)) {
            if (state != DeviceState.OFFLINE) {
                RCLogger.w(TAG, "Reachability action: switch between wifi and mobile. Device state: " + state);
                // refresh JAIN networking facilities so that we use the new available interface
                if (newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi) {
                    DeviceImpl.GetInstance().RefreshNetworking(SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi);
                }
                else {
                    DeviceImpl.GetInstance().RefreshNetworking(SipManager.NetworkInterfaceType.NetworkInterfaceTypeCellularData);
                }
                reachabilityState = newState;
                this.listener.onConnectivityUpdate(this, newState);
                return;
            }
        }

        if ((newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi || newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusCellular)
                && state == DeviceState.OFFLINE) {
            RCLogger.w(TAG, "Reachability action: wifi/mobile available. Device state: " + state);
            if (newState == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusWiFi) {
                DeviceImpl.GetInstance().bind(SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi);
            }
            else {
                DeviceImpl.GetInstance().bind(SipManager.NetworkInterfaceType.NetworkInterfaceTypeCellularData);
            }
            reachabilityState = newState;
            if (!parameters.containsKey(Prefs.DOMAIN) ||
                    parameters.containsKey(Prefs.DOMAIN) && parameters.get(Prefs.DOMAIN).equals("")) {
                // registrarless; we can transition to ready right away (i.e. without waiting for Restcomm to reply to REGISTER)
                state = DeviceState.READY;
                this.listener.onConnectivityUpdate(this, newState);
            }
            else {
                DeviceImpl.GetInstance().Register();
            }
        }
    }

    public RCDeviceListener.RCConnectivityStatus getReachability()
    {
        return reachabilityState;
    }

    // 'Copy' constructor
    public RCDevice(RCDevice device) {
        this.incomingSoundEnabled = device.incomingSoundEnabled;
        this.outgoingSoundEnabled = device.outgoingSoundEnabled;
        this.disconnectSoundEnabled = device.disconnectSoundEnabled;
        this.listener = device.listener;

        // Not used yet
        this.capabilities = null;
    }

    /**
     * Shuts down and release the Device
     */
    public void release() {
        RCLogger.i(TAG, "release()");
        this.listener = null;

        if (DeviceImpl.isInitialized()) {
            if (state != DeviceState.OFFLINE) {
                DeviceImpl.GetInstance().Unregister();
                // allow for the unregister to be serviced before we shut down the stack (delay 2 secs)
                // TODO: a better way to do this would be to wait for the response to the unregistration
                // before we shutdown
                Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        DeviceImpl.GetInstance().Shutdown();
                    }
                };
                mainHandler.postDelayed(myRunnable, 500);
            }
            else {
                // we are offline, no need for unregister
                DeviceImpl.GetInstance().Shutdown();
            }
        }
        // important, otherwise if shutdown and re-initialized the old RCDevice instance will be getting events
        RCClient.getContext().unregisterReceiver(this);
        state = DeviceState.OFFLINE;
    }

    /**
     * Start listening for incoming connections
     */
    public void listen() {
        RCLogger.i(TAG, "listen()");

        if (state == DeviceState.READY) {
            DeviceImpl.GetInstance().Register();
        }
    }

    /**
     * Stop listeninig for incoming connections
     */
    public void unlisten() {
        RCLogger.i(TAG, "unlisten()");

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
        RCLogger.i(TAG, "setDeviceListener()");

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
     * Updates the capability token (<b>Not implemented yet</b>)
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
        RCLogger.i(TAG, "connect(): " + parameters.toString());

        if (DeviceImpl.checkReachability(RCClient.getContext()) == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
            RCLogger.e(TAG, "connect(): No reachability");
            return null;
        }

        if (state == DeviceState.READY) {
            RCLogger.i(TAG, "RCDevice.connect(), with connectivity");

            Boolean enableVideo = (Boolean)parameters.get(Prefs.VIDEO_ENABLED);
            RCConnection connection = new RCConnection(listener);
            connection.incoming = false;
            connection.state = RCConnection.ConnectionState.PENDING;
            DeviceImpl.GetInstance().sipuaConnectionListener = connection;

            if (enableVideo == null) {
                enableVideo = false;
            }

            // create a new hash map
            HashMap<String, String> sipHeaders = null;
            if (parameters.containsKey(Prefs.SIP_HEADERS)) {
                sipHeaders = (HashMap<String, String>)parameters.get(Prefs.SIP_HEADERS);
            }

            if (connection.setupWebrtcAndCall((String)parameters.get("username"), sipHeaders, enableVideo) == RCConnection.Status.FAIL) {
                state = DeviceState.READY;
                return null;
            }
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
        RCLogger.i(TAG, "sendMessage(): message:" + message + "\nparameters: " + parameters.toString());

        if (state == DeviceState.READY) {
            DeviceImpl.GetInstance().SendMessage(parameters.get("username"), message);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Disconnect all connections
     */
    public void disconnectAll() {
        RCLogger.i(TAG, "disconnectAll()");

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
        RCLogger.i(TAG, "setPendingIntents()");
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
        RCLogger.i(TAG, "setIncomingSoundEnabled()");

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
        RCLogger.i(TAG, "setOutgoingSoundEnabled()");
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
        RCLogger.i(TAG, "setDisconnectSoundEnabled()");
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
        RCLogger.i(TAG, "updateParams(): " + params.toString());
        boolean status = false;

        if (params.containsKey(Prefs.DOMAIN) && !params.get(Prefs.DOMAIN).equals("")) {
            // we have a new (non empty) domain, need to register
            updateSipProfile(params);
            if (reachabilityState != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
                DeviceImpl.GetInstance().Register();
                status = true;
            }
        }
        else {
            // we have an empty domain
            if (!sipProfile.getRemoteEndpoint().equals("")) {
                // previously we had a registrar setup, need to unregister (important: we call updateSipProfile afterwards cause if we do no
                // unregister will check the SipProfile, find that domain is empty and skip unregistration
                DeviceImpl.GetInstance().Unregister();
            }
            // previously we didn't have a registrar setup, no need to do anything
            updateSipProfile(params);
            status = true;
        }
        return status;
    }

    public void updateSipProfile(HashMap<String, Object> params) {
        if (params == null) {
			return;
		}

		String domain = (String)params.get(Prefs.DOMAIN);
		if (domain != null && !domain.isEmpty()) {
			sipProfile.setSipDomain(domain);
		}

		String userName = (String)params.get(Prefs.USER_NAME);
		String authUserName = (String)params.get(Prefs.AUTH_USER_NAME);

		if (userName != null && !userName.isEmpty()) {
			sipProfile.setSipUserName(userName);

			if (authUserName != null && !authUserName.isEmpty()) {
				sipProfile.setSipAuthUserName(authUserName);
			} else {
				sipProfile.setSipAuthUserName(userName);
			}
		}

		String password = (String)params.get(Prefs.PASSWORD);

		if (password != null && !password.isEmpty()) {
			sipProfile.setSipPassword(password);
		}

		String outboundProxy = (String)params.get(Prefs.PROXY);

		if (outboundProxy != null && !outboundProxy.isEmpty()) {
			sipProfile.setRemoteEndpoint("sip:" + outboundProxy);
		}
		else if (domain != null && !domain.isEmpty()) {
			sipProfile.setRemoteEndpoint("sip:" + domain);
		}
    }

    /**
     * INTERNAL: not to be used from the Application
     */
    public void onSipUAConnectionArrived(SipEvent event) {
        RCLogger.i(TAG, "onSipUAConnectionArrived()");

        incomingConnection = new RCConnection();
        incomingConnection.incoming = true;
        incomingConnection.state = RCConnection.ConnectionState.CONNECTING;
        incomingConnection.incomingCallSdp = event.sdp;
        DeviceImpl.GetInstance().sipuaConnectionListener = incomingConnection;
        state = DeviceState.BUSY;

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        final String from = event.from;
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
        RCLogger.i(TAG, "onSipUAMessageArrived()");

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
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onSipUAError(final RCClient.ErrorCodes errorCode, final String errorText)
    {
        RCLogger.i(TAG, "onSipUAError()");

        final RCDevice device = this;
        // Important: need to fire the event in UI context cause currently we might be in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (device.listener != null) {
                    if (errorCode == RCClient.ErrorCodes.SIGNALLING_REGISTER_AUTH_ERROR ||
                            errorCode == RCClient.ErrorCodes.SIGNALLING_REGISTER_ERROR) {
                        state = DeviceState.OFFLINE;
                        device.listener.onConnectivityUpdate(device, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone);
                    }
                    device.listener.onStopListening(device, errorCode.ordinal(), errorText);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onSipUARegisterSuccess(SipEvent event)
    {
        RCLogger.i(TAG, "onSipUARegisterSuccess()");

        final RCDevice device = this;
        final RCDeviceListener.RCConnectivityStatus state = this.reachabilityState;

        // Important: need to fire the event in UI context cause currently we might be in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (device.listener != null) {
                    if (device.state == DeviceState.OFFLINE) {
                        device.state = DeviceState.READY;
                        device.listener.onConnectivityUpdate(device, state);
                    }
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    // Helpers
}
