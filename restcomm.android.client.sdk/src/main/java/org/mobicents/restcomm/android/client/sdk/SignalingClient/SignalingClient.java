package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
//import org.mobicents.restcomm.android.client.sdk.RCMessage;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

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
      void onCallArrivedEvent(String jobId, String peer, String sdpOffer);

      void onMessageArrivedEvent(String jobId, String peer, String messageText);

      void onErrorEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      void onConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus);

      // Event to convey trying to Register, so that UI can convey that to user (typically by changing RCDevice state to Offline, until register response arrives)
      void onRegisteringEvent(String jobId);

      // TODO: this should be removed after we remodel the whole connection/device communication
      // Call related events that are delegated to RCConnection
      void onCallRelatedMessage(SignalingMessage message);
   }

   /**
    * Call related interface callbacks that user of the API needs to implement
    */
   public interface SignalingClientCallListener {
      void onCallOutgoingConnectedEvent(String jobId, String sdpAnswer);

      void onCallIncomingConnectedEvent(String jobId);

      // peer disconnected the call
      void onCallPeerDisconnectEvent(String jobId);

      // peer ringing for outgoing call
      void onCallOutgoingPeerRingingEvent(String jobId);

      // call was disconnected due to local disconnect() call
      void onCallLocalDisconnectedEvent(String jobId);

      void onCallErrorEvent(String jobId);

      // cancel was was answered for incoming call
      void onCallIncomingCanceledEvent(String jobId);

      void onCallSentDigitsEvent(String jobId);
   }


   // ------ Not used yet, we 'll use it when we introduce the new messaging API
   public interface UIMessageListener {
      void onMessageSentEvent(String jobId);
   }


   private static final SignalingClient instance = new SignalingClient();
   SignalingClient.SignalingClientListener listener;
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
    * @return
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
    * @param parameters
    * @return
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
    * @param parameters
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
    * @param parameters
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
    */
   public void disconnect(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_DISCONNECT_REQUEST);
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   /**
    * Send DTMF digits to peer over existing call
    * @param jobId Unique identifier to identify future replies & events
    * @param digits
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
      else if (message.type == SignalingMessage.MessageType.CALL_INCOMING_EVENT) {
         listener.onCallArrivedEvent(message.jobId, message.peer, message.sdp);
      }
      else {
         listener.onCallRelatedMessage(message);
      }
   }


   // ------ Helpers

   // Generate unique identifier for 'transactions' created by SignalingClient, this can then be used as call-id when it enters JAIN SIP
   private String generateId()
   {
      return Long.toString(System.currentTimeMillis());
   }
}
