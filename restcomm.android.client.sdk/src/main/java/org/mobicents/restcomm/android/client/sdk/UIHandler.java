package org.mobicents.restcomm.android.client.sdk;

import android.os.Handler;
import android.os.Message;

// Attached to UI thread, handles incoming messages from signaling thread. These can either be replies to previous requests towards signaling thread or
// unsolicited events (like incoming calls or messages)
class UIHandler extends Handler {
   UIClient.UIClientListener listener;

   public UIHandler(UIClient.UIClientListener listener)
   {
      // instantiate parent Handler; remember by default associates this handler with the Looper for the current thread, hence UI thread
      super();

      this.listener = listener;
   }

   @Override
   public void handleMessage(Message inputMessage)
   {
      // Gets the image task from the incoming Message object.
      SignalingMessage message = (SignalingMessage) inputMessage.obj;

      if (message.type == SignalingMessage.MessageType.OPEN_REPLY) {
         listener.onOpenReply(message.id, message.connectivityStatus, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CLOSE_REPLY) {
         listener.onCloseReply(message.id, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.RECONFIGURE_REPLY) {
         listener.onReconfigureReply(message.id, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.ERROR_EVENT) {
         listener.onErrorEvent(message.id, message.status, message.text);
      }
      else if (message.type == SignalingMessage.MessageType.CONNECTIVITY_EVENT) {
         listener.onConnectivityEvent(message.id, message.connectivityStatus);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_EVENT) {
         listener.onMessageArrivedEvent(message.id, message.peer, message.messageText);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_REPLY) {
         listener.onMessageReply(message.id, message.status, message.text);
      }
      else {
         listener.onCallRelatedMessage(message);
      }
   }

}
