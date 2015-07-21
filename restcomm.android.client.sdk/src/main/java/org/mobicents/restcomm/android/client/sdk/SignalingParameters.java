package org.mobicents.restcomm.android.client.sdk;

import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.LinkedList;
import java.util.List;

public class SignalingParameters {
    private static final String TAG = "SignalingParameters";

    public List<PeerConnection.IceServer> iceServers;
    public final boolean initiator;
    public final String clientId;
    //public final String wssUrl;
    public final String sipUrl;
    public final String wssPostUrl;
    public SessionDescription offerSdp;
    public List<IceCandidate> iceCandidates;

    public SignalingParameters(
            List<PeerConnection.IceServer> iceServers,
            boolean initiator, String clientId,
            String sipUrl, String wssPostUrl,
            SessionDescription offerSdp, List<IceCandidate> iceCandidates) {
        this.iceServers = iceServers;
        this.initiator = initiator;
        this.clientId = clientId;
        this.sipUrl = sipUrl;
        this.wssPostUrl = wssPostUrl;
        this.offerSdp = offerSdp;
        this.iceCandidates = iceCandidates;
    }

    // combines offerSdp with iceCandidates and comes up with the full SDP
    public String generateSipSDP() {
        // concatenate all candidates in one String
        String candidates = "";
        for (IceCandidate candidate : iceCandidates) {
            candidates += "a=" + candidate.sdp + "\r\n";
        }

        Log.e(TAG, "@@@@ Before replace: " + offerSdp.description);
        // place the candidates after the 'a=rtcp:' string; use replace all because
        // we are supporting both audio and video so more than one replacements will be made
        String resultString = offerSdp.description.replaceAll("(a=rtcp:.*?\\r\\n)", "$1" + candidates);

        Log.e(TAG, "@@@@ After replace: " + resultString);

        return resultString;
    }

    public void addIceCandidate(IceCandidate iceCandidate)
    {
        if (this.iceCandidates == null) {
            this.iceCandidates = new LinkedList<IceCandidate>();
        }
        this.iceCandidates.add(iceCandidate);
    }

    public void addIceServer(PeerConnection.IceServer iceServer)
    {
        if (this.iceServers == null) {
            this.iceServers = new LinkedList<PeerConnection.IceServer>();
        }
        this.iceServers.add(iceServer);
    }

}

/*
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
*/