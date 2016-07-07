package org.mobicents.restcomm.android.client.sdk;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class SignalingHandler extends Handler implements JainSipClientListener, JainSipCallListener {
    //SignalingClientListener listener;
    JainSipClient jainSipClient;
    Handler uiHandler;
    private static final String TAG = "SignalingHandler";

    public SignalingHandler(Looper looper, Handler uiHandler) {
        // instantiate parent Handler, and pass non UI looper remember by default associates this handler with the Looper for the current thread, hence signaling thread
        super(looper);

        jainSipClient = new JainSipClient(this);
        this.uiHandler = uiHandler;
        //this.listener = listener;
    }

    @Override
    public void handleMessage(Message inputMessage) {
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
            jainSipClient.call(message.id, message.parameters);
            //listener.onCallArrivedEvent();
        }
        else {

        }
    }

    // -- SignalingClientListener events, that send messages towards UI thread
    // Replies
    /*
    public void onOpenReply(String id, RCClient.ErrorCodes status, String text)
    {
        SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.OPEN_REPLY);
        signalingMessage.status = status;
        signalingMessage.text = text;
        Message message = uiHandler.obtainMessage(1, signalingMessage);
        message.sendToTarget();
    }

    public void onCloseReply(String id, String text)
    {

    }
    public void onCallReply(String callId)
    {

    }
    public void onSendMessageReply(int errorCode, String errorText)
    {

    }

    // Unsolicited Events (due to incoming SIP requests)
    public void onCallArrivedEvent(String callId)
    {

    }
    public void onCallHangupEvent(String callId)
    {

    }
    // Incoming call was cancelled before it was answered
    public void onCallCancelledEvent(String callId)
    {

    }
    public void onMessageArrivedEvent(String id, String peer, String text)
    {

    }
    public void onErrorEvent(String id, int errorCode, String errorText)
    {

    }
    */

    // -- JainSipClientListener events
    public void onClientOpenedEvent(String id, RCClient.ErrorCodes status, String text)
    {
        //listener.onOpenReply(id, RCClient.ErrorCodes.SUCCESS, "Success");
        SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.OPEN_REPLY);
        signalingMessage.status = status;  //RCClient.ErrorCodes.SUCCESS;
        signalingMessage.text = text;  //"Success";
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


    // -- JainSipCallListener events
    public void onCallRingingEvent(String callId)
    {
        //listener.onCallArrivedEvent(callId);
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
