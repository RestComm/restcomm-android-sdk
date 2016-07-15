package org.mobicents.restcomm.android.client.sdk;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.HashMap;

public class SignalingHandler extends Handler implements JainSipClient.JainSipClientListener, JainSipCall.JainSipCallListener {
   JainSipClient jainSipClient;
   Handler uiHandler;
   private static final String TAG = "SignalingHandler";

   public SignalingHandler(Looper looper, Handler uiHandler)
   {
      // instantiate parent Handler, and pass non UI looper remember by default associates this handler with the Looper for the current thread, hence signaling thread
      super(looper);

      jainSipClient = new JainSipClient(this);
      this.uiHandler = uiHandler;
      //this.listener = listener;
   }

   @Override
   public void handleMessage(Message inputMessage)
   {
      // Gets the image task from the incoming Message object.
      SignalingMessage message = (SignalingMessage) inputMessage.obj;

        /*
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e(TAG, "In main UI thread");
        }
        else {
            Log.e(TAG, "NOT In main UI thread");
        }
        */

      if (message.type == SignalingMessage.MessageType.OPEN_REQUEST) {
         jainSipClient.open(message.id, message.androidContext, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.CLOSE_REQUEST) {
         jainSipClient.close(message.id);
      }
      if (message.type == SignalingMessage.MessageType.RECONFIGURE_REQUEST) {
         jainSipClient.reconfigure(message.id, message.androidContext, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_REQUEST) {
         jainSipClient.call(message.id, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_DISCONNECT_REQUEST) {
         jainSipClient.hangup(message.id, this);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_ACCEPT_REQUEST) {
         jainSipClient.accept(message.id, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_REQUEST) {
         jainSipClient.sendMessage(message.id, message.parameters);
      }
      else if (message.type == SignalingMessage.MessageType.SEND_DIGITS_REQUEST) {
         jainSipClient.sendDigits(message.id, message.dtmfDigits);
      }
   }

   // -- JainSipClientListener events
   public void onClientOpenedEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      //listener.onOpenReply(id, RCClient.ErrorCodes.SUCCESS, "Success");
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.OPEN_REPLY);
      signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
      signalingMessage.text = text;  //"Success";
      signalingMessage.connectivityStatus = connectivityStatus;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientErrorEvent(String id, RCClient.ErrorCodes status, String text)
   {
      //listener.onOpenReply(id, status, text);
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.ERROR_EVENT);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientClosedEvent(String id, RCClient.ErrorCodes status, String text)
   {
      //listener.onCloseReply(id, "Successfully closed client");
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CLOSE_REPLY);
      signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
      signalingMessage.text = text;  //"Success";
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientReconfigureEvent(String id, RCClient.ErrorCodes status, String text)
   {
      //listener.onOpenReply(id, RCClient.ErrorCodes.SUCCESS, "Success");
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.RECONFIGURE_REPLY);
      signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
      signalingMessage.text = text;  //"Success";
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientConnectivityEvent(String id, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CONNECTIVITY_EVENT);
      signalingMessage.connectivityStatus = connectivityStatus;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientMessageArrivedEvent(String id, String peer, String messageText)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.MESSAGE_EVENT);
      signalingMessage.messageText = messageText;
      signalingMessage.peer = peer;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientMessageSentEvent(String id, RCClient.ErrorCodes status, String text)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.MESSAGE_REPLY);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   // -- JainSipCallListener events
   public void onCallArrivedEvent(String id, String peer, String sdpOffer)
   {
      SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CALL_EVENT);
      signalingMessage.sdp = sdpOffer;
      signalingMessage.peer = peer;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallOutgoingConnectedEvent(String callId, String sdpAnswer)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_OUTGOING_CONNECTED_EVENT);
      signalingMessage.sdp = sdpAnswer;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallIncomingConnectedEvent(String callId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_INCOMING_CONNECTED_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallPeerDisconnectedEvent(String callId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_PEER_DISCONNECT_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallLocalDisconnectedEvent(String callId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_DISCONNECT_REPLY);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallRingingEvent(String callId)
   {
   }

   /*
   public void onCallPeerHangupEvent(String callId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_PEER_DISCONNECT_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }
   */

   public void onCallPeerRingingEvent(String callId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_PEER_RINGING_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallInProgressEvent(String callId)
   {

   }

   public void onCallPeerSdpAnswerEvent(String callId)
   {

   }

   public void onCallPeerSdpOfferEvent(String callId)
   {

   }

   public void onCallCancelledEvent(String callId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_CANCELED_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallIgnoredEvent(String callId)
   {

   }

   public void onCallErrorEvent(String callId, RCClient.ErrorCodes status, String text)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.CALL_ERROR_EVENT);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallDigitsEvent(String callId, RCClient.ErrorCodes status, String text)
   {
      SignalingMessage signalingMessage = new SignalingMessage(callId, SignalingMessage.MessageType.SEND_DIGITS_RESPONSE);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }
}
