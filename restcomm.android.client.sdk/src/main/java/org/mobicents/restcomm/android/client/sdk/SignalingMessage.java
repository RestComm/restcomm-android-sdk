package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;

import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.util.HashMap;

// Structure signaling messages exchanged between UI and signaling thread
public class SignalingMessage {
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
        CALL_PEER_HANGUP_EVENT,
        CALL_CONNECTED_EVENT,
        CALL_ERROR_EVENT,
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
