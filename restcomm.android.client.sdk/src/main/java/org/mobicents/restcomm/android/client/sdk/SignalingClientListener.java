package org.mobicents.restcomm.android.client.sdk;

import java.util.HashMap;

public interface SignalingClientListener {
    // Replies
    public abstract void onOpenReply(String id, RCClient.ErrorCodes status, String text);
    public abstract void onCloseReply(String id, String text);

    public abstract void onCallReply(String callId);

    public abstract void onSendMessageReply(int errorCode, String errorText);

    // Unsolicited Events (due to incoming SIP requests)
    public abstract void onCallArrivedEvent(String callId);
    public abstract void onCallHangupEvent(String callId);
    // Incoming call was cancelled before it was answered
    public abstract void onCallCancelledEvent(String callId);

    public abstract void onMessageArrivedEvent(String id, String peer, String text);

    public abstract void onErrorEvent(String id, int errorCode, String errorText);
}
