package org.mobicents.restcomm.android.client.sdk;

import java.util.HashMap;

public interface UIClientListener {
    // Replies
    public abstract void onOpenReply(String id, RCClient.ErrorCodes status, String text);
    public abstract void onCloseReply(String id, RCClient.ErrorCodes status, String text);
    public abstract void onReconfigureReply(String id, RCClient.ErrorCodes status, String text);
    public abstract void onCallReply(String id, RCClient.ErrorCodes status, String text);
    public abstract void onSendMessageReply(String id, RCClient.ErrorCodes status, String text);

    // Unsolicited Events
    public abstract void onCallArrivedEvent(String id, String peer);
    public abstract void onMessageArrivedEvent(String id, String peer, String text);
    public abstract void onErrorEvent(String id, RCClient.ErrorCodes status, String text);
}
