package org.mobicents.restcomm.android.client.sdk;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

public class SignalingParameters {
    public List<PeerConnection.IceServer> iceServers;
    public boolean initiator;
    public String clientId;
    public String sipUri;
    //public final String wssUrl;
    //public final String wssPostUrl;
    public SessionDescription offerSdp;
    public List<IceCandidate> iceCandidates;

    public SignalingParameters(List<PeerConnection.IceServer> iceServers,
            boolean initiator, String clientId,
            String sipUri, SessionDescription offerSdp,
            List<IceCandidate> iceCandidates) {
        this.iceServers = iceServers;
        this.initiator = initiator;
        this.clientId = clientId;
        //this.wssUrl = wssUrl;
        this.sipUri = sipUri;
        //this.wssPostUrl = wssPostUrl;
        this.offerSdp = offerSdp;
        this.iceCandidates = iceCandidates;
    }

    public SignalingParameters(boolean initiator, String sipUri) {
        this.initiator = initiator;
        this.sipUri = sipUri;
    }

    // combines offerSdp with iceCandidates and comes up with the full SDP
    public String generateSipSDP() {
        return "";
    }

}
