package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient.JainSipCall;
import org.mobicents.restcomm.android.client.sdk.SignalingClient.JainSipClient.JainSipClient;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

/**
 * SignalingHandler takes care of all the messaging from UI thread -> JainSipClient and the opposite
 */
class SignalingHandler extends Handler implements JainSipClient.JainSipClientListener, JainSipCall.JainSipCallListener {
   JainSipClient jainSipClient;
   Handler uiHandler;
   private static final String TAG = "SignalingHandler";

   public SignalingHandler(Looper looper, Handler uiHandler)
   {
      // instantiate parent Handler, and pass non UI looper remember by default associates this handler with the Looper for the current thread, hence signaling thread
      super(looper);
      this.uiHandler = uiHandler;
      jainSipClient = null;
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

      RCLogger.i(TAG, "handleMessage: type: " + message.type + ", jobId: " + message.jobId);

      // all requests apart from OPEN_REQUEST require an initialized jainSipClient
      if (message.type != SignalingMessage.MessageType.OPEN_REQUEST && jainSipClient == null) {
         // wrong usage of API
         throw new RuntimeException("JainSipClient has not been initialized");
      }

      if (message.type == SignalingMessage.MessageType.OPEN_REQUEST) {
         if (jainSipClient != null) {
            onClientOpenedReply(message.jobId, RCDeviceListener.RCConnectivityStatus.RCConnectivityStatusNone,
                  RCClient.ErrorCodes.ERROR_DEVICE_ALREADY_OPEN,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_ALREADY_OPEN));
            return;
         }
         jainSipClient = new JainSipClient(this);
         jainSipClient.open(message.jobId, message.androidContext, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.CLOSE_REQUEST) {
         jainSipClient.close(message.jobId);
      }
      if (message.type == SignalingMessage.MessageType.RECONFIGURE_REQUEST) {
         jainSipClient.reconfigure(message.jobId, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_REQUEST) {
         jainSipClient.call(message.jobId, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_DISCONNECT_REQUEST) {
         jainSipClient.disconnect(message.jobId, this);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_ACCEPT_REQUEST) {
         jainSipClient.accept(message.jobId, message.parameters, this);
      }
      else if (message.type == SignalingMessage.MessageType.MESSAGE_REQUEST) {
         jainSipClient.sendMessage(message.jobId, message.parameters);
      }
      else if (message.type == SignalingMessage.MessageType.CALL_SEND_DIGITS_REQUEST) {
         jainSipClient.sendDigits(message.jobId, message.dtmfDigits);
      }
   }

   // -- JainSipClientListener events
   public void onClientOpenedReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      //listener.onOpenReply(jobId, RCClient.ErrorCodes.SUCCESS, "Success");
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.OPEN_REPLY);
      signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
      signalingMessage.text = text;  //"Success";
      signalingMessage.connectivityStatus = connectivityStatus;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientErrorReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      //listener.onOpenReply(jobId, status, text);
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.ERROR_EVENT);
      signalingMessage.status = status;
      signalingMessage.text = text;
      signalingMessage.connectivityStatus = connectivityStatus;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientClosedEvent(String jobId, RCClient.ErrorCodes status, String text)
   {
      //listener.onCloseReply(jobId, "Successfully closed client");
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CLOSE_REPLY);
      signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
      signalingMessage.text = text;  //"Success";
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();

      // remove reference so that it can be GC'd
      this.jainSipClient = null;
   }

   public void onClientReconfigureReply(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus, RCClient.ErrorCodes status, String text)
   {
      //listener.onOpenReply(jobId, RCClient.ErrorCodes.SUCCESS, "Success");
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.RECONFIGURE_REPLY);
      signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
      signalingMessage.text = text;  //"Success";
      signalingMessage.connectivityStatus = connectivityStatus;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientConnectivityEvent(String jobId, RCDeviceListener.RCConnectivityStatus connectivityStatus)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CONNECTIVITY_EVENT);
      signalingMessage.connectivityStatus = connectivityStatus;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientMessageArrivedEvent(String jobId, String peer, String messageText)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.MESSAGE_INCOMING_EVENT);
      signalingMessage.messageText = messageText;
      signalingMessage.peer = peer;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientMessageReply(String jobId, RCClient.ErrorCodes status, String text)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.MESSAGE_REPLY);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onClientRegisteringEvent(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.REGISTERING_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   // -- JainSipCallListener events
   public void onCallArrivedEvent(String jobId, String peer, String sdpOffer)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_INCOMING_EVENT);
      signalingMessage.sdp = sdpOffer;
      signalingMessage.peer = peer;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallOutgoingConnectedEvent(String jobId, String sdpAnswer)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_OUTGOING_CONNECTED_EVENT);
      signalingMessage.sdp = sdpAnswer;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallIncomingConnectedEvent(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_INCOMING_CONNECTED_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallPeerDisconnectedEvent(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_PEER_DISCONNECT_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallLocalDisconnectedEvent(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_LOCAL_DISCONNECT_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallOutgoingPeerRingingEvent(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_OUTGOING_PEER_RINGING_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallIncomingCanceledEvent(String jobId)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_INCOMING_CANCELED_EVENT);
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallIgnoredEvent(String jobId)
   {

   }

   public void onCallErrorEvent(String jobId, RCClient.ErrorCodes status, String text)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_ERROR_EVENT);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }

   public void onCallDigitsEvent(String jobId, RCClient.ErrorCodes status, String text)
   {
      SignalingMessage signalingMessage = new SignalingMessage(jobId, SignalingMessage.MessageType.CALL_SEND_DIGITS_EVENT);
      signalingMessage.status = status;
      signalingMessage.text = text;
      Message message = uiHandler.obtainMessage(1, signalingMessage);
      message.sendToTarget();
   }
}
