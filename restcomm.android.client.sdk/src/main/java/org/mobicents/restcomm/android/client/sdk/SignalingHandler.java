package org.mobicents.restcomm.android.client.sdk;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class SignalingHandler extends Handler implements SignalingClientListener {
    //SignalingClientListener listener;
    SignalingClient signalingClient;
    Handler uiHandler;
    private static final String TAG = "SignalingHandler";

    public SignalingHandler(Looper looper, Handler uiHandler) {
        // instantiate parent Handler, and pass non UI looper remember by default associates this handler with the Looper for the current thread, hence signaling thread
        super(looper);

        signalingClient = new SignalingClient(this);
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
            signalingClient.open(message.id, message.androidContext, message.parameters, message.connectivity, message.networkInterfaceType);
            //listener.onOpenReply(message.id, message.text);
        }
        else if (message.type == SignalingMessage.MessageType.CALL_REQUEST) {
            signalingClient.call(message.id, message.parameters);
            //listener.onCallArrivedEvent();
        }
        else {

        }
    }

    // -- SignalingClientListener events, that send messages towards UI thread
    // Replies
    public void onOpenReply(String id, String text)
    {
        /*
        SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CALL_REPLY, "recepient");
        Message message = uiHandler.obtainMessage(1, signalingMessage);
        message.sendToTarget();
        */
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

}
