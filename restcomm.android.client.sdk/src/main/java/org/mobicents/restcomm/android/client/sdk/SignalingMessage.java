package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;

import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.util.HashMap;

// Structure signaling messages exchanged between UI and signaling thread
public class SignalingMessage {
    /**
     * Device capability (<b>Not Implemented yet</b>)
     */
    public enum MessageType {
        OPEN_REQUEST,
        OPEN_REPLY,
        CALL_REQUEST,
        CALL_REPLY,
        CALL_EVENT,
    }

    public String id;
    public MessageType type;
    public HashMap<String, Object> parameters;
    public Context androidContext;

    public boolean connectivity;
    public SipManager.NetworkInterfaceType networkInterfaceType;

    public String status;
    /*
    public String peer;
    public String domain;
    public String username;
    public String password;
    public String text;
    */

    /*
    public SignalingMessage()
    {

    }
    */

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

    /*
    public void setParametersOpen(String domain, String username, String password)
    {
        this.domain = domain;
        this.username = username;
        this.password = password;
    }
    */

}
