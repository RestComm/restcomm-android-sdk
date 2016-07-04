package org.mobicents.restcomm.android.client.sdk;

import java.util.HashMap;

public interface UIClientListener {
    // Replies
    public abstract void onOpenReply(String id, boolean successful, String text);
    public abstract void onCallReply(String id, String status);
    public abstract void onSendMessageReply(String id, String status);

    // Unsolicited Events
    public abstract void onCallArrivedEvent(String id, String peer);
    public abstract void onMessageArrivedEvent(String id, String peer, String text);
    public abstract void onErrorEvent(String id, int errorCode, String errorText);
}
