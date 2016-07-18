package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;

import java.util.HashMap;

// Structure signaling messages exchanged between UI and signaling thread
class SignalingMessage {
   public enum MessageType {
      OPEN_REQUEST,
      OPEN_REPLY,
      CLOSE_REQUEST,
      CLOSE_REPLY,
      RECONFIGURE_REQUEST,
      RECONFIGURE_REPLY,

      CALL_REQUEST,
      //CALL_REPLY,
      CALL_EVENT,
      ERROR_EVENT,
      CONNECTIVITY_EVENT,

      CALL_PEER_RINGING_EVENT,
      CALL_PEER_DISCONNECT_EVENT,
      CALL_OUTGOING_CONNECTED_EVENT,
      CALL_INCOMING_CONNECTED_EVENT,
      CALL_ERROR_EVENT,

      CALL_ACCEPT_REQUEST,
      CALL_DISCONNECT_REQUEST,
      CALL_DISCONNECT_REPLY,
      //CALL_DISCONNECTED_EVENT,
      CALL_CANCELED_EVENT,

      MESSAGE_REQUEST,
      MESSAGE_REPLY,
      MESSAGE_EVENT,

      SEND_DIGITS_REQUEST,
      SEND_DIGITS_RESPONSE,
   }

    /*
    public enum MessageStatus {
        STATUS_SUCCESS,
        STATUS_FAILURE,
    }
    */

   public String id;
   public MessageType type;
   public HashMap<String, Object> parameters;
   public Context androidContext;

   // result status and text
   public RCClient.ErrorCodes status;
   public String text;

   // additional fields per request
   public RCDeviceListener.RCConnectivityStatus connectivityStatus;
   // SDP
   public String sdp;
   // incoming messages
   public String messageText;
   public String peer;
   // DTMF digits
   public String dtmfDigits;

   // let's enforce id and type, to make sure we always get them
   public SignalingMessage(String id, MessageType type)
   {
      this.id = id;
      this.type = type;
   }

   public void setParameters(HashMap<String, Object> parameters)
   {
      this.parameters = parameters;
   }

   public void setAndroidContext(Context androidContext)
   {
      this.androidContext = androidContext;
   }

}
