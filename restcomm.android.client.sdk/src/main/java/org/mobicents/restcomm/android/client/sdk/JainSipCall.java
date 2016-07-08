package org.mobicents.restcomm.android.client.sdk;

// Represents a call
public class JainSipCall {

    // Interface the JainSipCall listener needs to implement, to get events from us
    public interface JainSipCallListener {
        void onCallRingingEvent(String callId);  // onPrivateCallConnectorCallRingingEvent
        void onCallPeerHangupEvent(String callId);  // either when we receive 200 OK in our BYE, or we send 200 OK to peer's BYE, onPrivateCallConnectorCallHangupEvent
        void onCallPeerRingingEvent(String callId);  // ringback, onPrivateCallConnectorCallRingingBackEvent
        void onCallInProgressEvent(String callId);  // SIP code 183, onPrivateCallConnectorCallInProgressEvent
        void onCallPeerSdpAnswerEvent(String callId);  // onPrivateCallConnectorRemoteSdpAnswerEvent
        void onCallPeerSdpOfferEvent(String callId);  // onPrivateCallConnectorRemoteSdpOfferEvent
        void onCallCancelledEvent(String callId);  // cancel was received and answered, onPrivateCallConnectorCallCanceledEvent
        void onCallIgnoredEvent(String callId);  // we ignored the call, onPrivateCallConnectorCallClosedEvent
        void onCallErrorEvent(String callId);  // onPrivateCallConnectorCallOpenErrorEvent
    }


}
