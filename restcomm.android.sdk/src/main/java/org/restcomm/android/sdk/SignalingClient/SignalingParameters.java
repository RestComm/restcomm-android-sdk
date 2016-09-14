/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.sdk.SignalingClient;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
   public boolean videoEnabled;
   //public List<IceCandidate> answerIceCandidates;

   public SignalingParameters(
         List<PeerConnection.IceServer> iceServers,
         boolean initiator,
         String clientId,
         String sipUrl,
         String wssPostUrl,
         SessionDescription offerSdp,
         List<IceCandidate> iceCandidates,
         HashMap<String, String> sipHeaders,
         boolean videoEnabled)
   {
      this.iceServers = iceServers;
      this.initiator = initiator;
      this.clientId = clientId;
      this.sipUrl = sipUrl;
      this.wssPostUrl = wssPostUrl;
      this.offerSdp = offerSdp;
      this.answerSdp = null;
      this.iceCandidates = iceCandidates;
      this.sipHeaders = sipHeaders;
      this.videoEnabled = videoEnabled;
      //this.answerIceCandidates = null;
   }

   public SignalingParameters()
   {
      this.iceServers = null;
      this.initiator = false;
      this.clientId = "";
      this.sipUrl = "";
      this.wssPostUrl = "";
      this.offerSdp = null;
      this.answerSdp = null;
      this.iceCandidates = null;
      this.sipHeaders = null;
      this.videoEnabled = false;
      //this.answerIceCandidates = null;
   }

   // combines offerSdp with iceCandidates and comes up with the full SDP
   public String generateSipSdp(SessionDescription offerSdp, List<IceCandidate> iceCandidates)
   {
      // concatenate all candidates in one String
      String audioCandidates = "";
      String videoCandidates = "";
      boolean isVideo = false;
      for (IceCandidate candidate : iceCandidates) {
         //Log.e(TAG, "@@@@ candidate.sdp: " + candidate.sdp);
         if (candidate.sdpMid.equals("audio")) {
            audioCandidates += "a=" + candidate.sdp + "\r\n";
         }
         if (candidate.sdpMid.equals("video")) {
            videoCandidates += "a=" + candidate.sdp + "\r\n";
            isVideo = true;
         }
      }
      //Log.e(TAG, "@@@@ audio candidates: " + audioCandidates);
      //Log.e(TAG, "@@@@ video candidates: " + videoCandidates);
      //Log.e(TAG, "@@@@ Before replace: " + offerSdp.description);
      // first, audio
      // place the candidates after the 'a=rtcp:' string; use replace all because
      // we are supporting both audio and video so more than one replacements will be made
      //String resultString = offerSdp.description.replaceFirst("(a=rtcp:.*?\\r\\n)", "$1" + audioCandidates);

      Matcher matcher = Pattern.compile("(a=rtcp:.*?\\r\\n)").matcher(offerSdp.description);
      int index = 0;
      StringBuffer stringBuffer = new StringBuffer();
      while (matcher.find()) {
         if (index == 0) {
            // audio
            matcher.appendReplacement(stringBuffer, "$1" + audioCandidates);
         }
         else {
            // video
            matcher.appendReplacement(stringBuffer, "$1" + videoCandidates);
         }
         index++;
      }
      matcher.appendTail(stringBuffer);

      //Log.v(TAG, "@@@@ After replace: " + stringBuffer.toString());

      return stringBuffer.toString();
   }

   // gets a full SDP and a. populates .iceCandidates with individual candidates, and
   // b. removes the candidates from the SDP string and returns it as .offerSdp
   public static SignalingParameters extractCandidates(SessionDescription sdp)
   {
      SignalingParameters params = new SignalingParameters();
      params.iceCandidates = new LinkedList<IceCandidate>();

      // first parse the candidates
      // TODO: for video to work properly we need to do some more work to split the full SDP and differentiate candidates
      // based on media type (i.e. audio vs. video)
      //Matcher matcher = Pattern.compile("a=(candidate.*?)\\r\\n").matcher(sdp.description);
      Matcher matcher = Pattern.compile("m=audio|m=video|a=(candidate.*)\\r\\n").matcher(sdp.description);
      String collectionState = "none";
      while (matcher.find()) {
         if (matcher.group(0).equals("m=audio")) {
            collectionState = "audio";
            continue;
         }
         if (matcher.group(0).equals("m=video")) {
            collectionState = "video";
            continue;
         }

         IceCandidate iceCandidate = new IceCandidate(collectionState, 0, matcher.group(1));
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