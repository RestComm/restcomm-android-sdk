package org.mobicents.restcomm.android.client.sdk;


// Call related events
public interface JainSipCallListener {
    public abstract void onCallRingingEvent(String callId);  // onPrivateCallConnectorCallRingingEvent
    public abstract void onCallPeerHangupEvent(String callId);  // either when we receive 200 OK in our BYE, or we send 200 OK to peer's BYE, onPrivateCallConnectorCallHangupEvent
    public abstract void onCallPeerRingingEvent(String callId);  // ringback, onPrivateCallConnectorCallRingingBackEvent
    public abstract void onCallInProgressEvent(String callId);  // SIP code 183, onPrivateCallConnectorCallInProgressEvent
    public abstract void onCallPeerSdpAnswerEvent(String callId);  // onPrivateCallConnectorRemoteSdpAnswerEvent
    public abstract void onCallPeerSdpOfferEvent(String callId);  // onPrivateCallConnectorRemoteSdpOfferEvent
    public abstract void onCallCancelledEvent(String callId);  // cancel was received and answered, onPrivateCallConnectorCallCanceledEvent
    public abstract void onCallIgnoredEvent(String callId);  // we ignored the call, onPrivateCallConnectorCallClosedEvent
    public abstract void onCallErrorEvent(String callId);  // onPrivateCallConnectorCallOpenErrorEvent
}
