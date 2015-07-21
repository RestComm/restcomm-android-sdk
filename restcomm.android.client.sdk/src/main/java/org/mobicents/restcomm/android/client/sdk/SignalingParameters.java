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
        // Candidates go after the 'a=rtcp' SDP attribute

        // Split the SDP in 2 parts: firstPart is up to the end of the 'a=rtcp' line
        // and the second part is from there to the end. Then insert the candidates
        // in-between

        // Since iOS doesn't support regex in NSStrings we need to find the place
        // to split by first searching for 'a=rtcp' and from that point to look
        // for the end of this line (i.e. \r\n)
        String rtcpAttribute = "a=rtcp:";
        String resultString = offerSdp.description;
        //NSRange startRange = [self.sdp rangeOfString:rtcpAttribute];
        //NSString *fromRtcpAttribute = [self.sdp substringFromIndex:startRange.location];
        //NSRange endRange = [fromRtcpAttribute rangeOfString:@"\r\n"];

        // Found the split point, break the sdp string in 2
        //NSString *firstPart = [self.sdp substringToIndex:startRange.location + endRange.location + 2];
        //NSString *lastPart = [self.sdp substringFromIndex:startRange.location + endRange.location + 2];

        String candidates = "";  // = [[NSMutableString alloc] init];
        for (IceCandidate candidate : iceCandidates) {
            candidates += "a=" + candidate.sdp + "\r\n";
        }

        Log.e(TAG, "@@@@ Before replace: " + offerSdp.description);
        // (?s) turns on DOTALL to make '.' match even new line
        resultString = offerSdp.description.replaceAll("(a=rtcp:.*?\\r\\n)", "$1" + candidates);
        Log.e(TAG, "@@@@ After replace: " + resultString);
        //NSString *updatedSdp = [NSString stringWithFormat:@"%@%@%@", firstPart, candidates, lastPart];
        // the complete message also has the sofia handle (so that sofia knows which active session to associate this with)
        //NSString * completeMessage = [NSString stringWithFormat:@"%@ %@", self.sofia_handle, updatedSdp];
        //NSLog(@"Complete Message: %@", completeMessage);
        //return completeMessage;
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