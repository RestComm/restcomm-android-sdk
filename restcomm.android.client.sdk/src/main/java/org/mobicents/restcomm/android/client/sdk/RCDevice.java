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

import org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient.JainSipConfiguration;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.SignalingClient;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.SignalingMessage;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

/**
 * RCDevice Represents an abstraction of a communications device able to make and receive calls, send and receive messages etc. Remember that
 * in order to be notified of RestComm Client events you need to set a listener to RCDevice and implement the applicable methods, and also 'register'
 * to the applicable intents by calling RCDevice.setPendingIntents() and provide one intent for whichever activity will be receiving calls and another
 * intent for the activity receiving messages.
 * If you want to initiate a media connection towards another party you use RCDevice.connect() which returns an RCConnection object representing
 * the new outgoing connection. From then on you can act on the new connection by applying RCConnection methods on the handle you got from RCDevice.connect().
 * If there is an incoming connection you will be receiving an intent with action 'RCDevice.INCOMING_CALL'. At that point you can use RCConnection methods to
 * accept or reject the connection.
 * <p/>
 * As far as instant messages are concerned you can send a message using RCDevice.sendMessage() and you will be notified of an incoming message
 * through an intent with action 'RCDevice.INCOMING_MESSAGE'.
 *
 * @see RCConnection
 */

public class RCDevice implements SignalingClient.SignalingClientListener {
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
      public static final String MEDIA_TURN_URL = "turn-url";
      public static final String MEDIA_TURN_USERNAME = "turn-username";
      public static final String MEDIA_TURN_PASSWORD = "turn-password";
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
   // Parameters passed in the RCDevice constructor
   HashMap<String, Object> parameters;
   PendingIntent pendingCallIntent;
   PendingIntent pendingMessageIntent;
   HashMap<String, RCConnection> connections;
   //private RCConnection incomingConnection;
   private RCDeviceListener.RCConnectivityStatus cachedConnectivityStatus = RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone;
   private SignalingClient signalingClient;

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

      signalingClient = SignalingClient.getInstance();
      signalingClient.open(this, RCClient.getContext(), parameters);
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
    * Create an outgoing connection to an endpoint
    *
    * @param parameters Parameters such as the endpoint we want to connect to or SIP custom headers. If
    *                   you want to pass SIP custom headers, you need to add a separate (String, String) HashMap
    *                   inside 'parameters' hash and introduce your headers there.
    *                   For an example please check HelloWorld or Messenger samples.
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
         if (parameters != null && parameters.get("username") != null)
            username = parameters.get("username").toString();
         sendQoSNoConnectionIntent(username, this.getConnectivityStatus().toString());
      }

      if (state == DeviceState.READY) {
         RCLogger.i(TAG, "RCDevice.connect(), with connectivity");

         RCConnection connection = new RCConnection(null, false, RCConnection.ConnectionState.PENDING, this, signalingClient, listener);
         connection.open(parameters);

         // keep connection in the connections hashmap
         connections.put(connection.getId(), connection);
         state = DeviceState.BUSY;

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
         messageParameters.put("username", parameters.get("username"));
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
    * @param callIntent:    an intent that will be sent on an incoming call
    * @param messageIntent: an intent that will be sent on an incoming text message
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
    * Update preference parameters such as username/password
    *
    * @param params The params to be updated
    * @return Whether the update was successful or not
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
      cachedConnectivityStatus = connectivityStatus;
      if (status != RCClient.ErrorCodes.SUCCESS) {
         RCLogger.e(TAG, "onOpenReply(): id: " + jobId + ", failure - " + text);

         // TODO: Maybe we should introduce separate message specifically for RCDevice initialization. Using onStopListening() looks weird
         //listener.onStopListening(this, status.ordinal(), text);
         listener.onInitialized(this, connectivityStatus, status.ordinal(), text);
         return;
      }

      RCLogger.i(TAG, "onOpenReply(): id: " + jobId + ", success - " + text);
      state = DeviceState.READY;
      listener.onInitialized(this, connectivityStatus, RCClient.ErrorCodes.SUCCESS.ordinal(), RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
   }

   public void onCloseReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      if (status == RCClient.ErrorCodes.SUCCESS) {
         RCLogger.i(TAG, "onCloseReply(): id: " + jobId + ", success - " + text);
         //signalingClient.open(parameters, true, SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi);
      }
      else {
         RCLogger.i(TAG, "onCloseReply(): id: " + jobId + ", failure - " + text);
      }
   }

   public void onReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      cachedConnectivityStatus = connectivityStatus;
      if (status == RCClient.ErrorCodes.SUCCESS) {
         RCLogger.i(TAG, "onReconfigureReply(): id: " + jobId + ", success - " + text);
         state = DeviceState.READY;
         listener.onStartListening(this, connectivityStatus);
      }
      else {
         RCLogger.i(TAG, "onReconfigureReply(): id: " + jobId + ", failure - " + text);
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
   /*
   public void onCallArrivedEvent(String jobId, String peer)
   {


   }
   */

   public void onRegisteringEvent(String jobId)
   {
      RCLogger.i(TAG, "onRegisteringEvent(): id: " + jobId);
      state = DeviceState.OFFLINE;
      listener.onStopListening(this, RCClient.ErrorCodes.SUCCESS.ordinal(), "Trying to register with Service");
   }


   public void onMessageArrivedEvent(String jobId, String peer, String messageText)
   {
      RCLogger.i(TAG, "onMessageArrivedEvent(): id: " + jobId + ", peer: " + peer + ", text: " + messageText);

      HashMap<String, String> parameters = new HashMap<String, String>();
      // filter out SIP URI stuff and leave just the name
      String from = peer.replaceAll("^<", "").replaceAll(">$", "");
      parameters.put("username", from);

      try {
         Intent dataIntent = new Intent();
         dataIntent.setAction(INCOMING_MESSAGE);
         dataIntent.putExtra(INCOMING_MESSAGE_PARAMS, parameters);
         dataIntent.putExtra(INCOMING_MESSAGE_TEXT, messageText);
         pendingMessageIntent.send(RCClient.getContext(), 0, dataIntent);
      }
      catch (PendingIntent.CanceledException e) {
         e.printStackTrace();
      }
   }

   public void onErrorEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      cachedConnectivityStatus = connectivityStatus;
      if (status == RCClient.ErrorCodes.SUCCESS) {
         RCLogger.i(TAG, "onErrorEvent(): id: " + jobId + ", success - " + text);
      }
      else {
         RCLogger.i(TAG, "onErrorEvent(): id: " + jobId + ", failure - " + text);
         listener.onStopListening(this, status.ordinal(), text);
      }
   }

   public void onConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      RCLogger.i(TAG, "onConnectivityEvent(): id: " + jobId + ", status - " + connectivityStatus);
      cachedConnectivityStatus = connectivityStatus;
      if (state == DeviceState.OFFLINE && connectivityStatus != RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.READY;
      }
      if (state != DeviceState.OFFLINE && connectivityStatus == RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone) {
         state = DeviceState.OFFLINE;
      }
      listener.onConnectivityUpdate(this, connectivityStatus);
   }

   public void onCallArrivedEvent(String jobId, String peer, String sdpOffer)
   {
      RCConnection connection = new RCConnection(jobId, true, RCConnection.ConnectionState.CONNECTING, this, signalingClient, null);
      connection.incomingCallSdp = sdpOffer;
      connection.remoteMediaType = RCConnection.sdp2Mediatype(sdpOffer);
      // keep connection in the connections hashmap
      connections.put(jobId, connection);

      state = DeviceState.BUSY;

      try {
         Intent dataIntent = new Intent();
         dataIntent.setAction(INCOMING_CALL);
         dataIntent.putExtra(RCDevice.EXTRA_DID, peer);
         dataIntent.putExtra(RCDevice.EXTRA_VIDEO_ENABLED, (connection.remoteMediaType == RCConnection.ConnectionMediaType.AUDIO_VIDEO));
         pendingCallIntent.send(RCClient.getContext(), 0, dataIntent);

         // Phone state Intents to capture incoming phone call event
         sendQoSIncomingConnectionIntent(peer, connection);
      }
      catch (PendingIntent.CanceledException e) {
         e.printStackTrace();
      }
   }

   /*
   // This is for messages that have to do with a call, which are delegated to RCConnection
   public void onCallRelatedMessage(SignalingMessage signalingMessage)
   {
      if (connections.containsKey(signalingMessage.jobId)) {
         connections.get(signalingMessage.jobId).handleSignalingMessage(signalingMessage);
      }
      else {
         throw new RuntimeException("Unexpected signaling message type");
      }
   }
   */


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
}
