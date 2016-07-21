package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCMessage;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

import java.util.HashMap;

// Client singleton as well as Handler that will send all asynchronous requests from UI thread towards signaling thread and will
// receive responses via Handler handleMessage()
public class UIClient extends Handler {

   // Interface the UIClient listener needs to implement, to get events from us
   public interface UIClientListener {
      // Replies
      void onOpenReply(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      void onCloseReply(String id, RCClient.ErrorCodes status, String text);

      void onReconfigureReply(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      //void onCallReply(String id, RCClient.ErrorCodes status, String text);

      void onMessageReply(String id, RCClient.ErrorCodes status, String text);

      // Unsolicited Events
      void onCallArrivedEvent(String id, String peer, String sdpOffer);

      void onMessageArrivedEvent(String id, String peer, String messageText);

      void onErrorEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text);

      void onConnectivityEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus);

      // TODO: this needs redesign
      // Call related events that are delegated to RCConnection
      void onCallRelatedMessage(SignalingMessage message);

      // Event to convey trying to Register, so that UI can convey that to user (typically by changing RCDevice state to Offline, until register response arrives)
      void onRegisteringEvent(String id);
   }

   /*
   public interface UICallListener {
       void onCallPeerHangupEvent(String jobId);
       void onCallPeerRingingEvent(String jobId);
       void onCallConnectedEvent(String jobId, String sdpAnswer);
   }
   */

   // ------ Not used yet, we 'll use it when we introduce the new messaging API
   public interface UIMessageListener {
      void onMessageSentEvent(String id);
   }


   private static final UIClient instance = new UIClient();
   UIClient.UIClientListener listener;
   private static final String TAG = "UIClient";

   // handler at signaling thread to send messages to
   SignalingHandlerThread signalingHandlerThread;
   Handler signalingHandler;
   //UIHandler uiHandler;
   Context context;
   //HashMap<String, RCMessage> messages;

   // private constructor to avoid client applications to use constructor
   private UIClient()
   {
      super();

      // create signaling handler thread and handler/signal
      signalingHandlerThread = new SignalingHandlerThread(this);
      signalingHandler = signalingHandlerThread.getHandler();
   }

   public static UIClient getInstance()
   {
      return instance;
   }

   @Override
   public void handleMessage(Message inputMessage)
   {
      // Gets the image task from the incoming Message object.
      SignalingMessage message = (SignalingMessage) inputMessage.obj;

      RCLogger.i(TAG, "handleMessage: type: " + message.type + ", id: " + message.id);

      if (message.type == SignalingMessage.MessageType.OPEN_REPLY) {
         listener.onOpenReply(message.id, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CLOSE_REPLY) {
         listener.onCloseReply(message.id, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.RECONFIGURE_REPLY) {
         listener.onReconfigureReply(message.id, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.ERROR_EVENT) {
         listener.onErrorEvent(message.id, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CONNECTIVITY_EVENT) {
         listener.onConnectivityEvent(message.id, message.connectivityStatus);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_EVENT) {
         listener.onMessageArrivedEvent(message.id, message.peer, message.messageText);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_REPLY) {
         /*
         RCMessage rcMessage = messages.get(message.id);
         if (rcMessage == null) {
            throw new RuntimeException("No RCMessage matching incoming message id: " + message.id);
         }

         rcMessage.onMessageSentEvent(message.id);
         */
         listener.onMessageReply(message.id, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.REGISTERING_EVENT) {
         listener.onRegisteringEvent(message.id);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_EVENT) {
         listener.onCallArrivedEvent(message.id, message.peer, message.sdp);
      }
      else {
         listener.onCallRelatedMessage(message);
      }
   }
   /*
   public UIClient(UIClientListener listener, Context context)
   {

   }
   */

    /*
    void setCallListener(UICallListener callListener)
    {

    }
    */

   public String open(UIClientListener listener, Context context, HashMap<String, Object> parameters)
   {
      //uiHandler = new UIHandler(listener);
      this.context = context;
      this.listener = listener;

      String id = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.OPEN_REQUEST);
      signalingMessage.setParameters(parameters);
      signalingMessage.setAndroidContext(context);

      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      return id;
   }

   // Change signaling configuration, like update username/password, change domain, etc
   public String reconfigure(HashMap<String, Object> parameters)
   {
      String id = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.RECONFIGURE_REQUEST);
      signalingMessage.setParameters(parameters);

      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      return id;
   }

   // -- Call related methods. For these the id is already generated by the application
   public void call(String id, HashMap<String, Object> parameters)
   {
      //String id = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CALL_REQUEST);
      signalingMessage.setParameters(parameters);
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      //return id;
   }

   public void accept(String id, HashMap<String, Object> parameters)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CALL_ACCEPT_REQUEST);
      signalingMessage.setParameters(parameters);
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }


   public void disconnect(String id)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CALL_DISCONNECT_REQUEST);
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void sendDigits(String id, String digits)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.SEND_DIGITS_REQUEST);
      signalingMessage.dtmfDigits = digits;
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   //public String sendMessage(RCMessage rcMessage)
   public String sendMessage(HashMap<String, Object> parameters)
   {
      String id = generateId();

      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.MESSAGE_REQUEST);
      signalingMessage.parameters = parameters;
      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      //messages.put(id, rcMessage);

      return id;
   }

   public String close()
   {
      String id = generateId();
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CLOSE_REQUEST);
      //signalingMessage.setParameters(parameters);

      Message message = signalingHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      return id;
   }

   // Helpers

   // Generate unique identifier for 'transactions' created by UIClient, this can then be used as call-id when it enters JAIN SIP
   private String generateId()
   {
      return Long.toString(System.currentTimeMillis());
   }
}
