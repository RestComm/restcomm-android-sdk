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

package org.restcomm.android.sdk.SignalingClient;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDeviceListener;
//import org.restcomm.android.sdk.RCMessage;
import org.restcomm.android.sdk.util.RCLogger;

import java.util.HashMap;

/**
 * SignalingClient is a singleton that provides asynchronous access to lower level signaling facilities. Requests are sent via methods
 * like open(), close(), etc towards signaling thread. Responses are received via Handler.handleMessage() from signaling thread
 * and sent for further processing to SignalingClientListener listener for register/configuration specific functionality and to
 * SignalingClientCallListener listener for call related functionality. Hence, users of this API should implement those
 * and properly handle responses and events.
 *
 * Each request towards the signaling thread is sent together with a unique jobId that identifies this 'job' until it is either complete
 * or an error occurs. Each reply/event associated with this request carries the same jobId, so that the App can correlate them. The lifetime
 * of the job depends on its type. For example for open() jobs it is until the signaling facilities are properly initialized or we get an error
 * at which point we get a onCloseReply() conveying either of the facts. For a call() job the jobId is sent back and forth until the call
 * is disconnected or an error occurs.
 *
 * Notice that some callbacks are called on*Reply() (as opposed to on*Event()). The convention is that those are directly associated
 * to a a simple request (typically non call related) and also carry error status (and possibly connectivity status). In contrast,
 * for call-related functionality separate callbacks are defined for a. success and b. for error codes, since they are much more complicated.
 *
 * The whole architecture in a nutshell is as follows. The Android App or a higher level API (like RCDevice/RCConnection) use the SignalingClient API
 * which underneath creates signaling messages (see SignalingMessage for structure of the messages) and sends them to the Signaling thread
 * that handles them by dispatching them to JainSipClient that encapsulates JAIN SIP. When a response/event comes in from JAIN SIP to JainSipClient
 * the reverse happens: a message is created from the Signaling thread to the UI thread and after it is received at handleMessage() the respective
 * listener callback is used to notify the UI.
 */
public class SignalingClient extends Handler {

   /**
    * Registration/configuration related interface callbacks that user of the API needs to implement
    */

   public interface SignalingClientListener {
      // Replies
      void onOpenReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      void onCloseReply(String jobId, RCClient.ErrorCodes status, String text);

      void onReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      void onMessageReply(String jobId, RCClient.ErrorCodes status, String text);

      // Unsolicited Events
      void onCallArrivedEvent(String jobId, String peer, String sdpOffer, HashMap<String, String> customHeaders);

      void onMessageArrivedEvent(String jobId, String peer, String messageText);

      void onErrorEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      void onConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus);

      // Event to convey trying to Register, so that UI can convey that to user (typically by changing RCDevice state to Offline, until register response arrives)
      void onRegisteringEvent(String jobId);

      // TODO: this should be removed after we remodel the whole connection/device communication
      // Call related events that are delegated to RCConnection
      //void onCallRelatedMessage(SignalingMessage message);

      // this is not a callback but we want the listener to implement it so that we cat retrieve the connection from the jobId
      SignalingClient.SignalingClientCallListener getConnectionByJobId(String jobId);
   }

   /**
    * Call related interface callbacks that user of the API needs to implement
    */
   public interface SignalingClientCallListener {
      void onCallOutgoingConnectedEvent(String jobId, String sdpAnswer, HashMap<String, String> customHeaders);

      void onCallIncomingConnectedEvent(String jobId);

      // peer disconnected the call
      void onCallPeerDisconnectEvent(String jobId);

      // peer ringing for outgoing call
      void onCallOutgoingPeerRingingEvent(String jobId);

      // call was disconnected due to local disconnect() call
      void onCallLocalDisconnectedEvent(String jobId);

      void onCallErrorEvent(String jobId, RCClient.ErrorCodes status, String text);

      // cancel was was answered for incoming call
      void onCallIncomingCanceledEvent(String jobId);

      void onCallSentDigitsEvent(String jobId, RCClient.ErrorCodes statusCode, String statusText);
   }

   // ------ Not used yet, we 'll use it when we introduce the new messaging API
   public interface UIMessageListener {
      void onMessageSentEvent(String jobId);
   }


   private static final SignalingClient instance = new SignalingClient();
   SignalingClientListener listener;
   private static final String TAG = "SignalingClient";

   // handler at signaling thread to send messages to
   SignalingHandlerThread signalingHandlerThread;
   Handler signalingHandler;
   //UIHandler uiHandler;
   Context context;
   //HashMap<String, RCMessage> messages;

   // private constructor to avoid client applications to use constructor
   private SignalingClient()
   {
      super();

      // create signaling handler thread and handler/signal
      signalingHandlerThread = new SignalingHandlerThread(this);
      signalingHandler = signalingHandlerThread.getHandler();
   }

   public static SignalingClient getInstance()
   {
      return instance;
   }

   /**
    * Initialize the signaling facilities
    * @param listener Listener to register/configuration specific events
    * @param context Android context needed by signaling thread
    * @param parameters A map of parameters of the open (TODO: add doc for specific keys)
    * @return The jobId for the new job created at Signaling thread
    */
   public String open(SignalingClientListener listener, Context context, HashMap<String, Object> parameters)
   {
      //uiHandler = new UIHandler(listener);
      this.context = context;
      this.listener = listener;

      String jobId = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.OPEN_REQUEST);
      signalingMessage.setParameters(parameters);
      signalingMessage.setAndroidContext(context);

      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      return jobId;
   }

   /**
    * Change signaling configuration, like update username/password, change domain, etc
    * @param parameters Reconfigure paramemeters
    * @return The jobId for the new job created at Signaling thread
    */
   public String reconfigure(HashMap<String, Object> parameters)
   {
      String jobId = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.RECONFIGURE_REQUEST);
      signalingMessage.setParameters(parameters);

      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      return jobId;
   }

   // -- Call related methods. For these the jobId is already generated by the application

   /**
    * Make a call towards a peer
    * @param jobId Unique identifier to identify future replies & events
    * @param parameters Call parameters
    */
   public void call(String jobId, HashMap<String, Object> parameters)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_REQUEST);
      signalingMessage.setParameters(parameters);
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   /**
    * Accept a call from a peer
    * @param jobId Unique identifier to identify future replies & events
    * @param parameters Accept parameters
    */
   public void accept(String jobId, HashMap<String, Object> parameters)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_ACCEPT_REQUEST);
      signalingMessage.setParameters(parameters);
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   /**
    * Disconnect a call with a pper
    * @param jobId Unique identifier to identify future replies & events
    * @param reason Reason for the disconnect. If this is a normal disconnect triggered by the user, this is null or empty. But if this is caused because media
    *               connectivity has been severed, then 'reason' conveys the reason and is added as a SIP header to the generated BYE.
    */
   public void disconnect(String jobId, String reason)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_DISCONNECT_REQUEST);
      signalingMessage.reason = reason;
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   /**
    * Send DTMF digits to peer over existing call
    * @param jobId Unique identifier to identify future replies & events
    * @param digits DTMF digits to send (Important: for now we only support a single digit per sendDigits() call)
    */
   public void sendDigits(String jobId, String digits)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_SEND_DIGITS_REQUEST);
      signalingMessage.dtmfDigits = digits;
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   /**
    * Send text message to peer
    * @param parameters
    * @return
    */
   public String sendMessage(HashMap<String, Object> parameters)
   {
      String jobId = generateId();

      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.MESSAGE_REQUEST);
      signalingMessage.parameters = parameters;
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      //messages.put(jobId, rcMessage);

      return jobId;
   }

   /**
    * Release the signaling facilities
    * @return
    */
   public String close()
   {
      String jobId = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CLOSE_REQUEST);
      //signalingMessage.setParameters(parameters);

      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      return jobId;
   }

   /**
    * Handle incoming messages from signaling thread
    *
    * @param inputMessage incoming signaling message
    */
   @Override
   public void handleMessage(Message inputMessage)
   {
      // Gets the image task from the incoming Message object.
      SignalingMessage message = (SignalingMessage) inputMessage.obj;

      RCLogger.i(TAG, "handleMessage: type: " + message.type + ", jobId: " + message.jobId);

      if (message.type == SignalingMessage.MessageType.OPEN_REPLY) {
         listener.onOpenReply(message.jobId, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CLOSE_REPLY) {
         listener.onCloseReply(message.jobId, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.RECONFIGURE_REPLY) {
         listener.onReconfigureReply(message.jobId, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.ERROR_EVENT) {
         listener.onErrorEvent(message.jobId, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CONNECTIVITY_EVENT) {
         listener.onConnectivityEvent(message.jobId, message.connectivityStatus);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_INCOMING_EVENT) {
         listener.onMessageArrivedEvent(message.jobId, message.peer, message.messageText);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_REPLY) {
         /*
         RCMessage rcMessage = messages.get(message.jobId);
         if (rcMessage == null) {
            throw new RuntimeException("No RCMessage matching incoming message jobId: " + message.jobId);
         }

         rcMessage.onMessageSentEvent(message.jobId);
         */
         listener.onMessageReply(message.jobId, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.REGISTERING_EVENT) {
         listener.onRegisteringEvent(message.jobId);
      }
      // Call related events
      else if (message.type == SignalingMessage.MessageType.CALL_INCOMING_EVENT) {
         listener.onCallArrivedEvent(message.jobId, message.peer, message.sdp, message.customHeaders);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_OUTGOING_CONNECTED_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         // outgoing call is connected (got 200 OK and ACKed it)
         callListener.onCallOutgoingConnectedEvent(message.jobId, message.sdp, message.customHeaders);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_INCOMING_CONNECTED_EVENT) {
         // incoming call connected
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallIncomingConnectedEvent(message.jobId);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_PEER_DISCONNECT_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallPeerDisconnectEvent(message.jobId);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_OUTGOING_PEER_RINGING_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallOutgoingPeerRingingEvent(message.jobId);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_LOCAL_DISCONNECT_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallLocalDisconnectedEvent(message.jobId);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_ERROR_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallErrorEvent(message.jobId, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_INCOMING_CANCELED_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallIncomingCanceledEvent(message.jobId);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_SEND_DIGITS_EVENT) {
         SignalingClientCallListener callListener = listener.getConnectionByJobId(message.jobId);
         callListener.onCallSentDigitsEvent(message.jobId, message.status, message.text);
      }
      else {
         RCLogger.e(TAG, "handleSignalingMessage(): no handler for signaling message");
      }
   }


   // ------ Helpers

   // Generate unique identifier for 'transactions' created by SignalingClient, this can then be used as call-id when it enters JAIN SIP
   private String generateId()
   {
      return Long.toString(System.currentTimeMillis());
   }
}
