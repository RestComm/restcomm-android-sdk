package org.mobicents.restcomm.android.client.sdk;

import android.util.Log;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignalingParameters {
    private static final String TAG = "SignalingParameters";

    public List<PeerConnection.IceServer> iceServers;
    public final boolean initiator;
    public final String clientId;
    //public final String wssUrl;
    public final String sipUrl;
    public final String wssPostUrl;
    public SessionDescription offerSdp;
    public SessionDescription answerSdp;
    public List<IceCandidate> iceCandidates;
    public HashMap<String, String> sipHeaders;
    //public List<IceCandidate> answerIceCandidates;

    public SignalingParameters(
            List<PeerConnection.IceServer> iceServers,
            boolean initiator, String clientId,
            String sipUrl, String wssPostUrl,
            SessionDescription offerSdp, List<IceCandidate> iceCandidates,
            HashMap<String, String> sipHeaders) {
        this.iceServers = iceServers;
        this.initiator = initiator;
        this.clientId = clientId;
        this.sipUrl = sipUrl;
        this.wssPostUrl = wssPostUrl;
        this.offerSdp = offerSdp;
        this.answerSdp = null;
        this.iceCandidates = iceCandidates;
        this.sipHeaders = sipHeaders;
        //this.answerIceCandidates = null;
    }
    public SignalingParameters() {
        this.iceServers = null;
        this.initiator = false;
        this.clientId = "";
        this.sipUrl = "";
        this.wssPostUrl = "";
        this.offerSdp = null;
        this.answerSdp = null;
        this.iceCandidates = null;
        this.sipHeaders = null;
        //this.answerIceCandidates = null;
    }

    // combines offerSdp with iceCandidates and comes up with the full SDP
    public String generateSipSdp(SessionDescription offerSdp, List<IceCandidate> iceCandidates) {
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

    // gets a full SDP and a. populates .iceCandidates with individual candidates, and
    // b. removes the candidates from the SDP string and returns it as .offerSdp
    public static SignalingParameters extractCandidates(SessionDescription sdp) {
        SignalingParameters params = new SignalingParameters();
        params.iceCandidates = new LinkedList<IceCandidate>();

        // first parse the candidates
        // TODO: for video to work properly we need to do some more work to split the full SDP and differentiate candidates
        // based on media type (i.e. audio vs. video)
        Matcher matcher = Pattern.compile("a=(candidate.*?)\\r\\n").matcher(sdp.description);
        while (matcher.find()) {
            IceCandidate iceCandidate = new IceCandidate("audio", 0, matcher.group(1));
            params.iceCandidates.add(iceCandidate);
        }

        // remove candidates from SDP
        params.offerSdp = new SessionDescription(sdp.type, sdp.description.replaceAll("a=candidate.*?\\r\\n", ""));

        return params;
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