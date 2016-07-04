package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;
import android.os.Message;

import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.util.HashMap;

// similar to webrtcomm client
public class SignalingClient implements JainSipClientListener, JainSipCallListener {
    SignalingClientListener listener;
    JainSipClient jainSipClient;

    // create signaling client and pass listener for replies and events we need to send
    public SignalingClient(SignalingClientListener listener)
    {
        this.listener = listener;

        jainSipClient = new JainSipClient();
    }

    void open(String id, Context androidContext, HashMap<String,Object> parameters, boolean connectivity, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        // open and pass ourself as listener for future events
        jainSipClient.open(id, androidContext, parameters, this, connectivity, networkInterfaceType);
    }

    void call(String id, HashMap<String,Object> parameters)
    {
        jainSipClient.call(id, parameters);
    }

    void sendMessage(String id, String peer, String text)
    {
        jainSipClient.sendMessage(id, peer, text);
    }

    void close()
    {
        jainSipClient.close("register call id");
    }


    // -- JainSipClientListener events
    public void onClientOpenedEvent(String id)
    {
        listener.onOpenReply(id, RCClient.ErrorCodes.SUCCESS, "Success");
    }

    public void onClientErrorEvent(String id, RCClient.ErrorCodes status, String text)
    {
        listener.onOpenReply(id, status, text);
    }

    public void onClientClosedEvent(String id)
    {
        listener.onCloseReply(id, "Successfully closed client");
    }

    // -- JainSipCallListener events
    public void onCallRingingEvent(String callId)
    {
        listener.onCallArrivedEvent(callId);
    }
    public void onCallPeerHangupEvent(String callId)
    {
        //listener.
    }
    public void onCallPeerRingingEvent(String callId)
    {

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

    }
    public void onCallIgnoredEvent(String callId)
    {

    }
    public void onCallErrorEvent(String callId)
    {

    }
}
