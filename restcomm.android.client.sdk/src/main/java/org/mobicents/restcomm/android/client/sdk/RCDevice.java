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
import java.util.Iterator;
import java.util.Map;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.mobicents.restcomm.android.client.sdk.MediaClient.AppRTCAudioManager;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient.JainSipConfiguration;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.SignalingClient;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.SignalingMessage;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;


/**
 * <p>RCDevice represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
 * in order to be notified of Restcomm Client events you need to set a listener to RCDevice and implement the applicable methods, and also 'register'
 * to the applicable intents by calling RCDevice.setPendingIntents() and provide one intent for whichever activity will be receiving calls and another
 * intent for the activity receiving messages.
 * If you want to initiate a media connection towards another party you use RCDevice.connect() which returns an RCConnection object representing
 * the new outgoing connection. From then on you can act on the new connection by applying RCConnection methods on the handle you got from RCDevice.connect().
 * If there is an incoming connection you will be receiving an intent with action 'RCDevice.INCOMING_CALL', and with it you will get:
 * <ol>
 *    <li>The calling party id via string extra named RCDevice.EXTRA_DID, like: <i>intent.getStringExtra(RCDevice.EXTRA_DID)</i></li>
 *    <li>Whether the incoming call has video enabled via boolean extra named RCDevice.EXTRA_VIDEO_ENABLED, like:
 *       <i>intent.getBooleanExtra(RCDevice.EXTRA_VIDEO_ENABLED, false)</i></li>
 *    <li>Restcomm parameters via serializable extra named RCDevice.EXTRA_CUSTOM_HEADERS, like: <i>intent.getSerializableExtra(RCDevice.EXTRA_CUSTOM_HEADERS)</i>
 *    where you need to cast the result to HashMap<String, String> where each entry is a Restcomm parameter with key and value of type string
 *    (for example you will find the Restcomm Call-Sid under 'X-RestComm-CallSid' key).</li>
 * </ol>
 * At that point you can use RCConnection methods to accept or reject the connection.
 * </p>
 * <p>
 * As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 * through an intent with action 'RCDevice.INCOMING_MESSAGE'. For an incoming message you can retrieve:
 * <ol>
 *    <li>The sending party id via string extra named RCDevice.EXTRA_DID, like: <i>intent.getStringExtra(RCDevice.EXTRA_DID)</i></li>
 *    <li>The actual message text via string extra named RCDevice.INCOMING_MESSAGE_TEXT, like: <i>intent.getStringExtra(RCDevice.INCOMING_MESSAGE_TEXT)</i></li>
 * </ol>
 * </p>
 * @see RCConnection
 */

public class RCDevice implements SignalingClient.SignalingClientListener {
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

   /**
    * Parameter keys for RCClient.createDevice() and RCDevice.updateParams()
    */
   public static class ParameterKeys {
      public static final String SIGNALING_USERNAME = "pref_sip_user";
      public static final String SIGNALING_DOMAIN = "pref_proxy_domain";
      public static final String SIGNALING_PASSWORD = "pref_sip_password";
      public static final String SIGNALING_SECURE_ENABLED = "signaling-secure";
      public static final String SIGNALING_LOCAL_PORT = "signaling-local-port";
      public static final String SIGNALING_JAIN_SIP_LOGGING_ENABLED = "jain-sip-logging-enabled";
      public static final String MEDIA_TURN_ENABLED = "turn-enabled";
      public static final String MEDIA_ICE_URL = "turn-url";
      public static final String MEDIA_ICE_USERNAME = "turn-username";
      public static final String MEDIA_ICE_PASSWORD = "turn-password";
      public static final String RESOURCE_SOUND_CALLING = "sound-calling";
      public static final String RESOURCE_SOUND_RINGING = "sound-ringing";
      public static final String RESOURCE_SOUND_DECLINED = "sound-declined";
      public static final String RESOURCE_SOUND_MESSAGE = "sound-message";
   }

   private static final String TAG = "RCDevice";
   //private static boolean online = false;
   public static String OUTGOING_CALL = "ACTION_OUTGOING_CALL";
   public static String INCOMING_CALL = "ACTION_INCOMING_CALL";
   public static String OPEN_MESSAGE_SCREEN = "ACTION_OPEN_MESSAGE_SCREEN";
   public static String INCOMING_MESSAGE = "ACTION_INCOMING_MESSAGE";
   public static String INCOMING_MESSAGE_TEXT = "INCOMING_MESSAGE_TEXT";
   //public static String INCOMING_MESSAGE_PARAMS = "INCOMING_MESSAGE_PARAMS";
   public static String EXTRA_DID = "com.telestax.restcomm_messenger.DID";
   public static String EXTRA_CUSTOM_HEADERS = "com.telestax.restcomm_messenger.CUSTOM_HEADERS";
   public static String EXTRA_VIDEO_ENABLED = "com.telestax.restcomm_messenger.VIDEO_ENABLED";
   public static String EXTRA_SDP = "com.telestax.restcomm_messenger.SDP";
   //public static String EXTRA_DEVICE = "com.telestax.restcomm.android.client.sdk.extra-device";
   //public static String EXTRA_CONNECTION = "com.telestax.restcomm.android.client.sdk.extra-connection";
   // Parameters passed in the RCDevice constructor
   HashMap<String, Object> parameters;
   PendingIntent pendingCallIntent;
   PendingIntent pendingMessageIntent;
   HashMap<String, RCConnection> connections;
   //private RCConnection incomingConnection;
   private RCDeviceListener.RCConnectivityStatus cachedConnectivityStatus = RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone;
   private SignalingClient signalingClient;
   private AppRTCAudioManager audioManager = null;

   /**
    * Initialize a new RCDevice object
    *
    * @param parameters     RCDevice parameters
    * @param deviceListener Listener of RCDevice
    */
   protected RCDevice(HashMap<String, Object> parameters, RCDeviceListener deviceListener)
   {
      RCLogger.i(TAG, "RCDevice(): " + parameters.toString());
      //this.updateCapabilityToken(capabilityToken);
      this.listener = deviceListener;

      // TODO: check if those headers are needed
      HashMap<String, String> customHeaders = new HashMap<>();
      state = DeviceState.OFFLINE;

      connections = new HashMap<String, RCConnection>();
      // initialize JAIN SIP if we have connectivity
      this.parameters = parameters;

      // check if TURN keys are there
      //params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true));

      signalingClient = SignalingClient.getInstance();
      signalingClient.open(this, RCClient.getContext(), parameters);

      // Create and audio manager that will take care of audio routing,
      // audio modes, audio device enumeration etc.
      audioManager = AppRTCAudioManager.create(RCClient.getContext(), new Runnable() {
               // This method will be called each time the audio state (number and
               // type of devices) has been changed.
               @Override
               public void run()
               {
                  onAudioManagerChangedState();
               }
            }
      );

      // Store existing audio settings and change audio mode to
      // MODE_IN_COMMUNICATION for best possible VoIP performance.
      RCLogger.d(TAG, "Initializing the audio manager...");
      audioManager.init(populateAudioResourceIds(parameters));
   }

   /*
    * Populate audio resource ids for various sounds like calling, ringing, etc. The logic here is that
    * we have default resources in the SDK level, found at R.raw.*, and if the user wants to override
    * them they need to update R.raw in the Application level.
    */
   private HashMap<String, Integer> populateAudioResourceIds(HashMap<String, Object> parameters)
   {
      HashMap<String, Integer> resourceIds = new HashMap<String, Integer>();

      if (parameters.containsKey(ParameterKeys.RESOURCE_SOUND_CALLING)) {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, (Integer)parameters.get(ParameterKeys.RESOURCE_SOUND_CALLING));
      }
      else {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.calling_sample);
      }

      if (parameters.containsKey(ParameterKeys.RESOURCE_SOUND_RINGING)) {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, (Integer)parameters.get(ParameterKeys.RESOURCE_SOUND_RINGING));
      }
      else {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.ringing_sample);
      }

      if (parameters.containsKey(ParameterKeys.RESOURCE_SOUND_DECLINED)) {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, (Integer)parameters.get(ParameterKeys.RESOURCE_SOUND_DECLINED));
      }
      else {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.busy_tone_sample);
      }

      if (parameters.containsKey(ParameterKeys.RESOURCE_SOUND_MESSAGE)) {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, (Integer)parameters.get(ParameterKeys.RESOURCE_SOUND_MESSAGE));
      }
      else {
         resourceIds.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.message_sample);
      }


      return resourceIds;
   }

   // TODO: this is for RCConnection, but see if they can figure out the connectivity in a different way, like asking the signaling thread directly?
   public RCDeviceListener.RCConnectivityStatus getConnectivityStatus()
   {
      return cachedConnectivityStatus;
   }

   // 'Copy' constructor
   public RCDevice(RCDevice device)
   {
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
   public void release()
   {
      RCLogger.i(TAG, "release()");
      if (audioManager != null) {
         audioManager.close();
         audioManager = null;
      }

      this.listener = null;

      signalingClient.close();
      state = DeviceState.OFFLINE;
   }

   /**
    * Start listening for incoming connections
    */
   public void listen()
   {
      RCLogger.i(TAG, "listen()");

      if (state == DeviceState.READY) {
         // TODO: implement with new signaling

      }
   }

   /**
    * Stop listening for incoming connections
    */
   public void unlisten()
   {
      RCLogger.i(TAG, "unlisten()");

      if (state == DeviceState.READY) {
         // TODO: implement with new signaling
      }
   }

   /**
    * Update Device listener to be receiving Device related events. This is
    * usually needed when we switch activities and want the new activity to receive
    * events
    *
    * @param listener New device listener
    */
   public void setDeviceListener(RCDeviceListener listener)
   {
      RCLogger.i(TAG, "setDeviceListener()");

      this.listener = listener;
   }

   /**
    * Retrieves the capability token passed to RCClient.createDevice
    *
    * @return Capability token
    */
   public String getCapabilityToken()
   {
      return "";
   }

   /**
    * Updates the capability token (<b>Not implemented yet</b>)
    */
   public void updateCapabilityToken(String token)
   {

   }

   /**
    * Create an outgoing connection to an endpoint. Important: if you work with Android API 23 or above you will need to handle dynamic Android permissions in your Activity
    * as described at https://developer.android.com/training/permissions/requesting.html. More specifically the Restcomm Client SDK needs RECORD_AUDIO, CAMERA (only if the local user
    * has enabled local video via RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED; if not then this permission isn't needed), and USE_SIP permission
    * to be able to connect(). For an example of such permission handling you can check MainActivity of restcomm-hello world sample App. Notice that if any of these permissions
    * are missing, the call will fail with a ERROR_CONNECTION_PERMISSION_DENIED error.
    *
    * @param parameters Parameters such as the endpoint we want to connect to, etc. Possible keys: <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PEER</b>: Who is the called number, like <i>'+1235'</i> or <i>'sip:+1235@cloud.restcomm.com'</i> <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED</b>: Whether we want WebRTC video enabled or not <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO</b>: PercentFrameLayout containing the view where we want the local video to be rendered in. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO</b>: PercentFrameLayout containing the view where we want the remote video to be rendered. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required  <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC</b>: Preferred video codec to use. Default is VP8. Possible values: <i>'VP8', 'VP9'</i> <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS</b>: An optional HashMap<String,String> of custom SIP headers we want to add. For an example
    *                   please check HelloWorld sample or Olympus App. <br>
    * @param listener   The listener object that will receive events when the connection state changes
    * @return An RCConnection object representing the new connection or null in case of error. Error
    * means that RCDevice.state not ready to make a call (this usually means no WiFi available)
    */
   public RCConnection connect(Map<String, Object> parameters, RCConnectionListener listener)
   {
      RCLogger.i(TAG, "connect(): " + parameters.toString());

      if (cachedConnectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         // Phone state Intents to capture connection failed event
         String username = "";
         if (parameters != null && parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER) != null)
            username = parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER).toString();
         sendQoSNoConnectionIntent(username, this.getConnectivityStatus().toString());
      }

      if (state == DeviceState.READY) {
         RCLogger.i(TAG, "RCDevice.connect(), with connectivity");

         state = DeviceState.BUSY;

         RCConnection connection = new RCConnection.Builder(false, RCConnection.ConnectionState.PENDING, this, signalingClient, audioManager)
               .listener(listener)
               .build();
         connection.open(parameters);

         // keep connection in the connections hashmap
         connections.put(connection.getId(), connection);

         return connection;
      }
      else {
         return null;
      }
   }

   /**
    * Send an instant message to an endpoint
    *
    * @param message    Message text
    * @param parameters Parameters used for the message, such as 'username' that holds the recepient for the message
    */
   public boolean sendMessage(String message, Map<String, String> parameters)
   {
      RCLogger.i(TAG, "sendMessage(): message:" + message + "\nparameters: " + parameters.toString());

      if (state == DeviceState.READY) {
         HashMap<String, Object> messageParameters = new HashMap<>();
         messageParameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER));
         messageParameters.put("text-message", message);
         //RCMessage message = RCMessage.newInstanceOutgoing(messageParameters, listener);
         signalingClient.sendMessage(messageParameters);
         return true;
      }
      else {
         return false;
      }
   }

   /**
    * Disconnect all connections
    */
   public void disconnectAll()
   {
      RCLogger.i(TAG, "disconnectAll()");

      if (state == DeviceState.BUSY) {
         for (Map.Entry<String, RCConnection> entry : connections.entrySet()) {
            RCConnection connection = entry.getValue();
            connection.disconnect();
         }
         connections.clear();
         state = DeviceState.READY;
      }
   }

   /**
    * Retrieve the capabilities
    *
    * @return Capabilities
    */
   public Map<DeviceCapability, Object> getCapabilities()
   {
      HashMap<DeviceCapability, Object> map = new HashMap<DeviceCapability, Object>();
      return map;
   }

   /**
    * Retrieve the Device state
    *
    * @return State
    */
   public DeviceState getState()
   {
      return state;
   }

   /**
    * Set pending intents for incoming calls and messages. In order to be notified of RestComm Client
    * events you need to associate your Activities with intents and provide one intent for whichever activity
    * will be receiving calls and another intent for the activity receiving messages. If you use a single Activity
    * for both then you can pass the same intent both as a callIntent as well as a messageIntent
    *
    * @param callIntent    an intent that will be sent on an incoming call
    * @param messageIntent an intent that will be sent on an incoming text message
    */
   public void setPendingIntents(Intent callIntent, Intent messageIntent)
   {
      RCLogger.i(TAG, "setPendingIntents()");
      pendingCallIntent = PendingIntent.getActivity(RCClient.getContext(), 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);
      pendingMessageIntent = PendingIntent.getActivity(RCClient.getContext(), 0, messageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
   }

   public RCConnection getPendingConnection()
   {
      Iterator it = connections.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry pair = (Map.Entry) it.next();
         RCConnection connection = (RCConnection) pair.getValue();
         if (connection.incoming && connection.state == RCConnection.ConnectionState.CONNECTING) {
            return connection;
         }
      }

      return null;
   }

   /**
    * Should a ringing sound be played in a incoming connection or message
    *
    * @param incomingSound Whether or not the sound should be played
    */
   public void setIncomingSoundEnabled(boolean incomingSound)
   {
      RCLogger.i(TAG, "setIncomingSoundEnabled()");
      // TODO: implement with new signaling
      //DeviceImpl.GetInstance().soundManager.setIncoming(incomingSound);
   }

   /**
    * Retrieve the incoming sound setting
    *
    * @return Whether the sound will be played
    */
   public boolean isIncomingSoundEnabled()
   {
      // TODO: implement with new signaling
      //return DeviceImpl.GetInstance().soundManager.getIncoming();
      return true;
   }

   /**
    * Should a ringing sound be played in an outgoing connection or message
    *
    * @param outgoingSound Whether or not the sound should be played
    */
   public void setOutgoingSoundEnabled(boolean outgoingSound)
   {
      RCLogger.i(TAG, "setOutgoingSoundEnabled()");
      // TODO: implement with new signaling
      //DeviceImpl.GetInstance().soundManager.setOutgoing(outgoingSound);
   }

   /**
    * Retrieve the outgoint sound setting
    *
    * @return Whether the sound will be played
    */
   public boolean isOutgoingSoundEnabled()
   {
      // TODO: implement with new signaling
      //return DeviceImpl.GetInstance().soundManager.getOutgoing();
      return true;
   }

   /**
    * Should a disconnect sound be played when disconnecting a connection
    *
    * @param disconnectSound Whether or not the sound should be played
    */
   public void setDisconnectSoundEnabled(boolean disconnectSound)
   {
      RCLogger.i(TAG, "setDisconnectSoundEnabled()");
      // TODO: implement with new signaling
      //DeviceImpl.GetInstance().soundManager.setDisconnect(disconnectSound);
   }

   /**
    * Retrieve the disconnect sound setting
    *
    * @return Whether the sound will be played
    */
   public boolean isDisconnectSoundEnabled()
   {
      // TODO: implement with new signaling
      //return DeviceImpl.GetInstance().soundManager.getDisconnect();
      return true;
   }

   /**
    *  Update Device parameters such as username, password, domain, etc
    *
    * @param params  Parameters for the Device entity (prefer using the string constants shown below, i.e. RCDevice.ParameterKeys.*, instead of using strings
    *                like 'signaling-secure', etc. Possible keys: <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_USERNAME</b>: Identity for the client, like <i>'bob'</i> (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_PASSWORD</b>: Password for the client (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_DOMAIN</b>: Restcomm instance to use, like <i>'cloud.restcomm.com'</i>. Leave empty for registrar-less mode<br>
    *   <b>RCDevice.ParameterKeys.MEDIA_ICE_URL</b>: ICE url to use, like <i>'https://turn.provider.com/turn'</i> (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.MEDIA_ICE_USERNAME</b>: ICE username for authentication (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD</b>: ICE password for authentication (mandatory) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED</b>: Should signaling traffic be encrypted? If this is the case, then a key pair is generated when
    *                signaling facilities are initialized and added to a custom keystore. Also, added to this custom keystore are all the trusted certificates from
    *                the System Wide Android CA Store, so that we properly accept legit server certificates (optional) <br>
    *   <b>RCDevice.ParameterKeys.MEDIA_TURN_ENABLED</b>: Should TURN be enabled for webrtc media? (optional) <br>
    *   <b>RCDevice.ParameterKeys.SIGNALING_LOCAL_PORT</b>: Local port to use for signaling (optional) <br>
    * @see RCDevice
    */
   public boolean updateParams(HashMap<String, Object> params)
   {
      signalingClient.reconfigure(params);

      // remember that the new parameters can be just a subset of the currently stored in this.parameters, so to update the current parameters we need
      // to merge them with the new (i.e. keep the old and replace any new keys with new values)
      this.parameters = JainSipConfiguration.mergeParameters(this.parameters, params);

      // TODO: need to provide asynchronous status for this
      return true;
   }

   public HashMap<String, Object> getParameters()
   {
      return parameters;
   }

   public SignalingClient.SignalingClientCallListener getConnectionByJobId(String jobId)
   {
      if (connections.containsKey(jobId)) {
         return connections.get(jobId);
      }
      else {
         throw new RuntimeException("No RCConnection exists to handle message with jobid: " + jobId);
      }
   }

      // -- SignalingClientListener events for incoming messages from signaling thread
      // Replies

   public void onOpenReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onOpenReply(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;
      if (status != RCClient.ErrorCodes.SUCCESS) {
         // TODO: Maybe we should introduce separate message specifically for RCDevice initialization. Using onStopListening() looks weird
         //listener.onStopListening(this, status.ordinal(), text);
         listener.onInitialized(this, connectivityStatus, status.ordinal(), text);
         return;
      }

      state = DeviceState.READY;
      listener.onInitialized(this, connectivityStatus, RCClient.ErrorCodes.SUCCESS.ordinal(), RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
   }

   public void onCloseReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onCloseReply(): id: " + jobId + ", status: " + status + ", text: " + text);

      if (status == RCClient.ErrorCodes.SUCCESS) {
         // TODO: notify App that device is closed
      }
      else {
      }
   }

   public void onReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onReconfigureReply(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;
      if (status == RCClient.ErrorCodes.SUCCESS) {
         state = DeviceState.READY;
         listener.onStartListening(this, connectivityStatus);
      }
      else {
         state = DeviceState.OFFLINE;
         listener.onStopListening(this, status.ordinal(), text);
      }
   }

   /*
   public void onCallReply(String jobId, RCClient.ErrorCodes status, String text)
   {

   }
   */

   public void onMessageReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      RCLogger.i(TAG, "onMessageReply(): id: " + jobId + ", status: " + status + ", text: " + text);

      listener.onMessageSent(this, status.ordinal(), text);
   }

   // Unsolicited Events
   public void onCallArrivedEvent(String jobId, String peer, String sdpOffer, HashMap<String, String> customHeaders)
   {
      RCLogger.i(TAG, "onCallArrivedEvent(): id: " + jobId + ", peer: " + peer);

      RCConnection connection = new RCConnection.Builder(true, RCConnection.ConnectionState.CONNECTING, this, signalingClient, audioManager)
            .jobId(jobId)
            .incomingCallSdp(sdpOffer)
            .build();

      // keep connection in the connections hashmap
      connections.put(jobId, connection);

      state = DeviceState.BUSY;

      try {
         Intent dataIntent = new Intent();
         dataIntent.setAction(INCOMING_CALL);
         dataIntent.putExtra(RCDevice.EXTRA_DID, peer);
         dataIntent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, (connection.getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO));
         if (customHeaders != null) {
            dataIntent.putExtra(RCDevice.EXTRA_CUSTOM_HEADERS, customHeaders);
         }
         pendingCallIntent.send(RCClient.getContext(), 0, dataIntent);

         // Phone state Intents to capture incoming phone call event
         sendQoSIncomingConnectionIntent(peer, connection);
      }
      catch (PendingIntent.CanceledException e) {
         e.printStackTrace();
      }
   }

   public void onRegisteringEvent(String jobId)
   {
      RCLogger.i(TAG, "onRegisteringEvent(): id: " + jobId);
      state = DeviceState.OFFLINE;
      listener.onStopListening(this, RCClient.ErrorCodes.SUCCESS.ordinal(), "Trying to register with Service");
   }


   public void onMessageArrivedEvent(String jobId, String peer, String messageText)
   {
      RCLogger.i(TAG, "onMessageArrivedEvent(): id: " + jobId + ", peer: " + peer + ", text: " + messageText);

      audioManager.playMessageSound();
      HashMap<String, String> parameters = new HashMap<String, String>();
      // filter out SIP URI stuff and leave just the name
      String from = peer.replaceAll("^<", "").replaceAll(">$", "");
      //parameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, from);

      try {
         Intent dataIntent = new Intent();
         dataIntent.setAction(INCOMING_MESSAGE);
         //dataIntent.putExtra(INCOMING_MESSAGE_PARAMS, parameters);
         dataIntent.putExtra(EXTRA_DID, from);
         dataIntent.putExtra(INCOMING_MESSAGE_TEXT, messageText);
         pendingMessageIntent.send(RCClient.getContext(), 0, dataIntent);
      }
      catch (PendingIntent.CanceledException e) {
         e.printStackTrace();
      }
   }

   public void onErrorEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      RCLogger.e(TAG, "onErrorEvent(): id: " + jobId + ", connectivityStatus: " + connectivityStatus + ", status: " + status + ", text: " + text);
      cachedConnectivityStatus = connectivityStatus;
      if (status == RCClient.ErrorCodes.SUCCESS) {
      }
      else {
         listener.onStopListening(this, status.ordinal(), text);
      }
   }

   public void onConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      RCLogger.i(TAG, "onConnectivityEvent(): id: " + jobId + ", connectivityStatus: " + connectivityStatus);
      cachedConnectivityStatus = connectivityStatus;
      if (state == DeviceState.OFFLINE && connectivityStatus != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.READY;
      }
      if (state != DeviceState.OFFLINE && connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.OFFLINE;
      }
      listener.onConnectivityUpdate(this, connectivityStatus);
   }

   // ------ Helpers

   // -- Notify QoS module of Device related event through intents, if the module is available
   // Phone state Intents to capture incoming call event
    private void sendQoSIncomingConnectionIntent (String user, RCConnection connection)
    {
        Intent intent = new Intent ("org.mobicents.restcomm.android.CALL_STATE");
        intent.putExtra("STATE", "ringing");
        intent.putExtra("INCOMING", true);
        intent.putExtra("FROM", user);
        Context context = RCClient.getContext();
        try {
            // Restrict the Intent to MMC Handler running within the same application
            Class aclass = Class.forName("com.cortxt.app.corelib.Services.Intents.IntentHandler");
            intent.setClass(context.getApplicationContext(), aclass);
            context.sendBroadcast(intent);
        }
        catch (ClassNotFoundException e)
        {
            // If there is no MMC class isn't here, no intent
        }
    }

    private void sendQoSNoConnectionIntent (String user, String message) {
        Intent intent = new Intent("org.mobicents.restcomm.android.CONNECT_FAILED");
        intent.putExtra("STATE", "connect failed");
        intent.putExtra("ERRORTEXT", message);
        intent.putExtra("ERROR", RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY);
        intent.putExtra("INCOMING", false);
        intent.putExtra("USER", user);
        Context context = RCClient.getContext();
        try {
            // Restrict the Intent to MMC Handler running within the same application
            Class aclass = Class.forName("com.cortxt.app.corelib.Services.Intents.IntentHandler");
            intent.setClass(context.getApplicationContext(), aclass);
            context.sendBroadcast(intent);
        } catch (ClassNotFoundException e) {
            // If there is no MMC class isn't here, no intent
        }
    }

   void removeConnection(String jobId)
   {
      RCLogger.i(TAG, "removeConnection(): id: " + jobId + ", total connections before removal: " + connections.size());
      connections.remove(jobId);
   }

   private void onAudioManagerChangedState()
   {
      // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
      // is active.
   }
}
