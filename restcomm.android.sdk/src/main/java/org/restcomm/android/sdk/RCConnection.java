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

/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.restcomm.android.sdk;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.restcomm.android.sdk.MediaClient.AppRTCAudioManager;
import org.restcomm.android.sdk.MediaClient.PeerConnectionClient;
import org.restcomm.android.sdk.SignalingClient.SignalingParameters;
import org.restcomm.android.sdk.SignalingClient.SignalingClient;
import org.restcomm.android.sdk.MediaClient.util.IceServerFetcher;

import org.restcomm.android.sdk.util.PercentFrameLayout;
import org.restcomm.android.sdk.util.RCLogger;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
//import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.Size;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.VideoCapturer;


/**
 * RCConnection represents a call. An RCConnection can be either incoming or outgoing. RCConnections are not created by themselves but
 * as a result on an action on RCDevice. For example to initiate an outgoing connection you call RCDevice.connect() which instantiates
 * and returns a new RCConnection. On the other hand when an incoming connection arrives the RCDevice delegate is notified with
 * RCDeviceListener.onIncomingConnection() and passes the new RCConnection object that is used by the delegate to
 * control the connection.
 * <br>
 * When an incoming connection arrives through RCDeviceListener.onIncomingConnection() it is considered RCConnectionStateConnecting until it is either
 * accepted with RCConnection.accept() or rejected with RCConnection.reject(). Once the connection is accepted the RCConnection transitions to RCConnectionStateConnected
 * state.
 * <br>
 * When an outgoing connection is created with RCDevice.connect() it starts with state RCConnectionStatePending. Once it starts ringing on the remote party it
 * transitions to RCConnectionStateConnecting. When the remote party answers it, the RCConnection state transitions to RCConnectionStateConnected.
 * <br>
 * Once an RCConnection (either incoming or outgoing) is established (i.e. RCConnectionStateConnected) media can start flowing over it. DTMF digits can be sent over to
 * the remote party using RCConnection.sendDigits(). When done with the RCConnection you can disconnect it with RCConnection.disconnect().
 */
public class RCConnection implements PeerConnectionClient.PeerConnectionEvents, IceServerFetcher.IceServerFetcherEvents, SignalingClient.SignalingClientCallListener {
   /**
    * Connection State
    */
   public enum ConnectionState {
      PENDING, /**
       * An (outgoing) Connection enters this state when RCDevice.connect() is called and until peer starts ringing at which point in enters CONNECTING state (or DISCONNECTED)
       */
      CONNECTING, /**
       * A Connection enters this state a. when it is outgoing and peer starts ringing, b. when it is incoming and at the point it first arrives at RCDevice listener
       */
      SIGNALING_CONNECTED, /**
       * A Connection *might* enter this intermediate state when signaling is connected but before media is connected. We use 'might' because there's a chance, only in inbound connections,
       * that media starts flowing before signaling is connected (remember that for inbound connections signaling is deemed connected when SIP ACK is received). For outbound connections
       * we always enter the SIGNALING_CONNECTED because 200 OK comes always before media is setup.
       */
      CONNECTED, /**
       * A Connection enters this state when actual media start to flow
       */
      DISCONNECTING,  /** Connection is being disconnected. When client App calls RCConnection.disconnect(), RCConnection state transitions to this until we get a response
       at which point we transition to DISCONNECTED */
      DISCONNECTED,  /** Connection is in state disconnected */
   }

   /**
    * Media Type
    */
   public enum ConnectionMediaType {
      UNDEFINED, /**
       * We don't know the type of media yet, for example for remote video before they answer.
       */
      AUDIO, /**
       * Connection is audio only.
       */
      AUDIO_VIDEO, /** Connection audio & video. */
   }

   String IncomingParameterFromKey = "RCConnectionIncomingParameterFromKey";
   String IncomingParameterToKey = "RCConnectionIncomingParameterToKey";
   String IncomingParameterAccountSIDKey = "RCConnectionIncomingParameterAccountSIDKey";
   String IncomingParameterAPIVersionKey = "RCConnectionIncomingParameterAPIVersionKey";
   String IncomingParameterCallSIDKey = "RCConnectionIncomingParameterCallSIDKey";

   /**
    * State of the connection. For more info please check ConnectionState.
    */
   ConnectionState state;

   /**
    * Type of local media transferred over the RCConnection.
    */
   private ConnectionMediaType localMediaType;

   /**
    * Type of local media transferred over the RCConnection.
    */
   private ConnectionMediaType remoteMediaType;

   /**
    * Has an error occurred? This is for internal use only
    */
   private boolean errorOccurred = false;

   /**
    * Direction of the connection. True if connection is incoming, false otherwise
    */
   boolean incoming;

   /**
    * Connection parameters (**Not Implemented yet**)
    */
   private HashMap<String, String> parameters;

   /**
    * Listener that will be called on RCConnection events described at RCConnectionListener
    */
   private RCConnectionListener listener;

   /**
    * Audio Codec for local audio
    */
   public enum AudioCodec {
      AUDIO_CODEC_DEFAULT,
      AUDIO_CODEC_OPUS,
      AUDIO_CODEC_ISAC,
   }

   /**
    * Video Codec for local video
    */
   public enum VideoCodec {
      VIDEO_CODEC_DEFAULT,
      VIDEO_CODEC_VP8,
      VIDEO_CODEC_VP9,
      VIDEO_CODEC_H264,
   }

   /**
    * Video Resolution for local video
    */
   public enum VideoResolution {
      RESOLUTION_DEFAULT,
      RESOLUTION_QQVGA_160x120,
      RESOLUTION_QCIF_176x144,
      RESOLUTION_QVGA_320x240,
      RESOLUTION_CIF_352x288,
      RESOLUTION_nHD_640x360,  // 360p
      RESOLUTION_VGA_640x480,
      RESOLUTION_SVGA_800x600,
      RESOLUTION_HD_1280x720,  // 720p
      RESOLUTION_UXGA_1600x1200,
      RESOLUTION_FHD_1920x1080,  // 1080p
      RESOLUTION_UHD_3840x2160,  // 4K
   }

   /**
    * Frame rate in FPS for local video
    */
   public enum VideoFrameRate {
      FPS_DEFAULT,
      FPS_15,
      FPS_30,
      //FPS_60,
   }

   // internal class to use to describe video resolution
   /*
   private class Resolution {
      public int width;
      public int height;

      Resolution(int width, int height)
      {
         this.width = width;
         this.height = height;
      }
   }
   */

   /**
    * Parameter keys for RCCDevice.connect() and RCConnection.accept()
    */
   public static class ParameterKeys {
      public static final String CONNECTION_PEER = "username";
      public static final String CONNECTION_VIDEO_ENABLED = "video-enabled";
      public static final String CONNECTION_LOCAL_VIDEO = "local-video";
      public static final String CONNECTION_REMOTE_VIDEO = "remote-video";
      public static final String CONNECTION_PREFERRED_AUDIO_CODEC = "preferred-audio-codec";
      // Preferred local video codec
      public static final String CONNECTION_PREFERRED_VIDEO_CODEC = "preferred-video-codec";
      // Preferred local video resolution (needs to be supported by local camera)
      public static final String CONNECTION_PREFERRED_VIDEO_RESOLUTION = "preferred-video-resolution";
      public static final String CONNECTION_PREFERRED_VIDEO_FRAME_RATE = "preferred-video-frame-rate";
      public static final String CONNECTION_CUSTOM_SIP_HEADERS = "sip-headers";
      // Incoming headers from Restcomm both for incoming and outgoing calls
      public static final String CONNECTION_CUSTOM_INCOMING_SIP_HEADERS = "sip-headers-incoming";
      public static final String CONNECTION_SIP_HEADER_KEY_CALL_SID = "X-RestComm-CallSid";

      // Until we have trickle, as a way to timeout sooner than 40 seconds (webrtc default timeout)
      public static final String DEBUG_CONNECTION_CANDIDATE_TIMEOUT = "debug-connection-candidate-timeout";
   }

   /**
    * Key values for the
    */
   public static class IceServersKeys {
      public static final String ICE_SERVER_URL = "url";
      public static final String ICE_SERVER_USERNAME = "username";
      public static final String ICE_SERVER_PASSWORD = "password";
   }

   // Let's use a builder since RCConnections don't have uniform way to construct
   static class Builder {
      // Required parameters
      private final boolean incoming;
      private final RCConnection.ConnectionState state;
      private final RCDevice device;
      private final SignalingClient signalingClient;
      private final AppRTCAudioManager audioManager;

      // Optional parameters - initialized to default values
      private String jobId = null;
      private RCConnectionListener listener = null;
      private String incomingCallSdp = null;
      private String peer = null;
      private ConnectionMediaType remoteMediaType = ConnectionMediaType.UNDEFINED;
      // Device is already busy with another Connection
      private boolean deviceAlreadyBusy = false;
      // Custom headers sent by Restcomm
      private HashMap<String, String> customHeaders;

      public Builder(boolean incoming, RCConnection.ConnectionState state, RCDevice device, SignalingClient signalingClient, AppRTCAudioManager audioManager)
      {
         this.incoming = incoming;
         this.state = state;
         this.device = device;
         this.signalingClient = signalingClient;
         this.audioManager = audioManager;
      }

      public Builder jobId(String val)
      {
         jobId = val;
         return this;
      }

      public Builder listener(RCConnectionListener val)
      {
         listener = val;
         return this;
      }

      public Builder peer(String val)
      {
         peer = val;
         return this;
      }

      public Builder incomingCallSdp(String val)
      {
         incomingCallSdp = val;
         return this;
      }

      public Builder remoteMediaType(ConnectionMediaType val)
      {
         remoteMediaType = val;
         return this;
      }

      public Builder deviceAlreadyBusy(boolean val)
      {
         deviceAlreadyBusy = val;
         return this;
      }

      public Builder customHeaders(HashMap<String, String> val)
      {
         customHeaders = val;
         return this;
      }

      public RCConnection build()
      {
         return new RCConnection(this);
      }
   }

   public RCDevice device = null;
   private String jobId;
   private SignalingClient signalingClient;
   private String incomingCallSdp = "";
   private String peer;
   //private EglBase rootEglBase;
   private boolean localVideoReceived = false;
   private boolean remoteVideoReceived = false;
   private SurfaceViewRenderer localRender;
   private SurfaceViewRenderer remoteRender;
   private PercentFrameLayout localRenderLayout;
   private PercentFrameLayout remoteRenderLayout;
   private PeerConnectionClient peerConnectionClient = null;
   private SignalingParameters signalingParameters;
   private AppRTCAudioManager audioManager = null;
   private ScalingType scalingType;
   private Toast logToast;
   private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
   private boolean iceConnected;
   private HashMap<String, Object> callParams = null;
   // Has user actually muted video? Notice the distinction between user having muted video and video being muted
   // in PeerConnection level automatically because user detachedVideo(). Hence apart from the PC state for video mute,
   // we need another parameter to keep track of user preference so that we can reattachVideo() properly
   private boolean hasUserMutedVideo;
   private long callStartedTimeMs = 0;
   private final boolean DO_TOAST = false;
   // if a call takes too long to establish this handler is used to emit a time out
   private Handler timeoutHandler = null;
   // call times out if it hasn't been established after 15 seconds
   private final int CALL_TIMEOUT_DURATION_MILIS = 15 * 1000;
   private Handler candidateTimeoutHandler = null;
   private boolean iceGatheringCompleteCalled = false;
   // Device was already busy with another Connection when this Connection arrived. If so we need to set this so that we have custom behavior later
   private boolean deviceAlreadyBusy = false;

   // Local preview screen position before call is connected.
   private static final int LOCAL_X_CONNECTING = 0;
   private static final int LOCAL_Y_CONNECTING = 0;
   private static final int LOCAL_WIDTH_CONNECTING = 100;
   private static final int LOCAL_HEIGHT_CONNECTING = 100;
   // Local preview screen position after call is connected.
   private static final int LOCAL_X_CONNECTED = 72;
   private static final int LOCAL_Y_CONNECTED = 2;
   private static final int LOCAL_WIDTH_CONNECTED = 25;
   private static final int LOCAL_HEIGHT_CONNECTED = 25;
   // Remote video screen position
   private static final int REMOTE_X = 0;
   private static final int REMOTE_Y = 0;
   private static final int REMOTE_WIDTH = 100;
   private static final int REMOTE_HEIGHT = 100;

   private String webrtcReportsJsonString = null;


   private enum VideoViewState {
      NONE,
      LOCAL_VIEW_RECEIVED,
      REMOTE_VIEW_RECEIVED,
      ICE_CONNECTED,
   }

   // List of 'dangerous' permissions that we need to check (CAMERA is added dynamically only if the local user uses video)
   private static final String[] MANDATORY_PERMISSIONS = {
           Manifest.permission.RECORD_AUDIO,
           Manifest.permission.USE_SIP
   };
   private static final String TAG = "RCConnection";

   // Construct RCConnection from Buider
   private RCConnection(Builder builder)
   {
      RCLogger.i(TAG, "RCConnection(Builder)");

      if (builder.jobId == null) {
         // create a unique jobId for the RCConnection, this is used for signaling actions to maintain state
         jobId = Long.toString(System.currentTimeMillis());
      }
      else {
         jobId = builder.jobId;
      }

      incoming = builder.incoming;
      state = builder.state;
      device = builder.device;
      signalingClient = builder.signalingClient;
      audioManager = builder.audioManager;
      listener = builder.listener;
      incomingCallSdp = builder.incomingCallSdp;
      incomingCallSdp = builder.incomingCallSdp;
      if (incomingCallSdp != null) {
         remoteMediaType = RCConnection.sdp2Mediatype(builder.incomingCallSdp);
      }
      peer = builder.peer;
      deviceAlreadyBusy = builder.deviceAlreadyBusy;
      timeoutHandler = new Handler(device.getMainLooper());
      candidateTimeoutHandler = new Handler(device.getMainLooper());

      callParams = new HashMap<>();
      if (builder.customHeaders != null) {
         callParams.put(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS, builder.customHeaders);
      }
   }

   /**
    * Initialize a new RCConnection object. <b>Important</b>: this is used internally by RCDevice and is not meant for application use
    *
    * @param connectionListener RCConnection listener that will be receiving RCConnection events (@see RCConnectionListener)
    */
   public RCConnection(RCConnectionListener connectionListener)
   {
      RCLogger.i(TAG, "RCConnection(RCConnectionListener)");

      this.listener = connectionListener;
   }

   /**
    * Retrieves the current state of the connection
    * @return the current state of the connection
    */
   public ConnectionState getState()
   {
      return this.state;
   }

   /**
    * Retrieves the current local media type of the connection
    * @return the current local media type of the connection
    */
   public ConnectionMediaType getLocalMediaType()
   {
      return this.localMediaType;
   }

   /**
    * Retrieves the current local media type of the connection
    * @return the current local media type of the connection
    */
   public ConnectionMediaType getRemoteMediaType()
   {
      return this.remoteMediaType;
   }

   /**
    * Retrieves the set of application parameters associated with this connection (<b>Not Implemented yet</b>)
    *
    * @return Connection parameters
    */
   public Map<String, String> getParameters()
   {
      return parameters;
   }

   /**
    * Returns whether the connection is incoming or outgoing
    *
    * @return True if incoming, false otherwise
    */
   public boolean isIncoming()
   {
      return this.incoming;
   }

   // Make a call using the passed parameters; not meant for application use
   public void open(Map<String, Object> parameters)
   {
      setupWebrtcAndCall(parameters);
   }

   /**
    * Accept the incoming connection. Important: if you work with Android API 23 or above you will need to handle dynamic Android permissions in your Activity
    * as described at https://developer.android.com/training/permissions/requesting.html. More specifically the Restcomm Client SDK needs RECORD_AUDIO, CAMERA (only if the local user
    * has enabled local video via RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED; if not then this permission isn't needed), and USE_SIP permission
    * to be able to accept() a connection. For an example of such permission handling you can check MainActivity of restcomm-hello world sample App. Notice that if any of these permissions
    * are missing, the call will fail with a ERROR_CONNECTION_PERMISSION_DENIED error.
    *
    * @param parameters Parameters such as whether we want video enabled, etc. Possible keys: <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED</b>: Whether we want WebRTC video enabled or not. If this is true, then we must provide proper views for CONNECTION_LOCAL_VIDEO and CONNECTION_REMOTE_VIDEO respectively (please check below). If this is false, then even if CONNECTION_LOCAL_VIDEO and CONNECTION_REMOTE_VIDEO are provided they will be ignored <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO</b>: PercentFrameLayout containing the view where we want the local video to be rendered in. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO</b>: PercentFrameLayout containing the view where we want the remote video to be rendered. You can check res/layout/activity_main.xml
    *                   in hello-world sample to see the structure required  <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC</b>: Preferred audio codec to use. Default is OPUS. Possible values are enumerated at <i>RCConnection.AudioCodec</i> <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC</b>: Preferred video codec to use. Default is VP8. Possible values are enumerated at <i>RCConnection.VideoCodec</i> <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION</b>: Preferred video resolution to use. Default is HD (1280x720). Possible values are enumerated at <i>RCConnection.VideoResolution</i> <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE</b>: Preferred frame rate to use. Default is 30fps. Possible values are enumerated at <i>RCConnection.VideoFrameRate</i> <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS</b>: An optional HashMap&lt;String,String&gt; of custom SIP headers we want to add. For an example
    *                   please check restcomm-helloworld or restcomm-olympus sample Apps (optional) <br>
    *   <b>RCConnection.ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT</b>: An optional Integer denoting how long to wait for ICE candidates. Zero means default behaviour which is
    *                   to depend on onIceGatheringComplete from Peer Connection facilities. Any other integer value means to wait at most that amount of time no matter if onIceGatheringComplete has fired.
    *                   The problem we are addressing here is the new Peer Connection ICE gathering timeout which is 40 seconds which is way too long. Notice that the root cause here is in reality
    *                   lack of support for Trickle ICE, so once it is supported we won't be needing such workarounds.
    *                   please check restcomm-helloworld or restcomm-olympus sample Apps (optional) <br>
    */
   public void accept(Map<String, Object> parameters)
   {
      RCLogger.i(TAG, "accept(): " + parameters.toString());
      if (!checkPermissions(parameters.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) && (Boolean)parameters.get(ParameterKeys.CONNECTION_VIDEO_ENABLED))) {
         return;
      }

      if (state == ConnectionState.CONNECTING) {
         this.callParams.putAll(parameters);
         // Especially, for incoming connections the peer DID is provided when the connection arrives in RCDevice and at that point RCConnection.peer is populated
         //this.callParams.put(ParameterKeys.CONNECTION_PEER, this.peer);
         initializeWebrtc(this.callParams.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) && (Boolean)this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED),
               (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_LOCAL_VIDEO),
               (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_REMOTE_VIDEO),
               (VideoCodec) parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC),
               (AudioCodec) parameters.get(ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC),
               (VideoResolution)parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION),
               (VideoFrameRate)parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE));

         startTurn();
         startMediaTimer();
      }
      else {
         // let's delay a millisecond to avoid calling code in the App getting intertwined with App listener code
         new Handler(device.getMainLooper()).postDelayed(
               new Runnable() {
                  @Override
                  public void run()
                  {
                     if (device.isAttached()) {
                        listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_WRONG_STATE.ordinal(),
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_WRONG_STATE));
                     }
                     else {
                        RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_WRONG_STATE));
                     }
                  }
               }
               , 1);
      }
   }

   /**
    * Ignore incoming connection (<b>Not Implemented yet</b>)
    */
   public void ignore()
   {
      if (state == ConnectionState.CONNECTING) {
         audioManager.stop();
         signalingClient.disconnect(jobId, null);
      }
      else {
         // let's delay a millisecond to avoid calling code in the App getting intertwined with App listener code
         new Handler(device.getMainLooper()).postDelayed(
               new Runnable() {
                  @Override
                  public void run()
                  {
                     if (device.isAttached()) {
                        listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_IGNORE_WRONG_STATE.ordinal(),
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_IGNORE_WRONG_STATE));
                     }
                     else {
                        RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_IGNORE_WRONG_STATE));
                     }

                  }
               }
               , 1);
      }
   }

   /**
    * Reject incoming connection
    */
   public void reject()
   {
      RCLogger.i(TAG, "reject()");

      if (state == ConnectionState.CONNECTING) {
         signalingClient.disconnect(jobId, null);

         // TODO: (minor) if reject() is called while we are already connected then we will disconnect, but in that
         // edge case we shouldn't set connection state to DICONNECTED right away

         // update state right away since rejecting a call is a response to the INVITE, so no further messages will come
         this.state = ConnectionState.DISCONNECTED;

         // Device was already busy with another Connection when this Connection arrived, need to skip rest of handling here
         if (deviceAlreadyBusy) {
            return;
         }

         audioManager.stop();

         // also update RCDevice state
         if (RCDevice.state == RCDevice.DeviceState.BUSY) {
            RCDevice.state = RCDevice.DeviceState.READY;
         }
      }
      else {
         // let's delay a millisecond to avoid calling code in the App getting intertwined with App listener code
         new Handler(device.getMainLooper()).postDelayed(
               new Runnable() {
                  @Override
                  public void run()
                  {
                     if (device.isAttached()) {
                        listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_REJECT_WRONG_STATE.ordinal(),
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_REJECT_WRONG_STATE));
                     }
                     else {
                        RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_REJECT_WRONG_STATE));
                     }
                  }
               }
               , 1);
      }
   }

   /**
    * Disconnect the established connection
    */
   public void disconnect()
   {
      RCLogger.i(TAG, "disconnect()");

      boolean statsFailed = true;
      if (peerConnectionClient != null) {
         if (peerConnectionClient.getStats()) {
            statsFailed = false;
         }
      }

      // if for any reason stats failed to get gathered, we need to call handleDisconnect() synchronously
      if (statsFailed) {
         handleDisconnect(null);
      }
   }

   // Not implemented yet
   /**
    * Gather connection statistics asynchronously. The actual stats are delivered when onStatsGathered() is called and are retrieved with RCConnection.getStats().
    * Right now we 're only supporting webrtc media stats (coming from PeerConnection.getStats()), but in the future we can extend to add more stats/qos info,
    * like potentially signalling stats or cell stats, etc
    */
/*
    public void gatherStats(String filter) {
        RCLogger.i(TAG, "gatherStats()");
    }
*/

   /**
    * Converts a WebRTC stats report array as provided by PeerConnection to a valid json string
    *
    * The input reports are in the format used by Google facilities at this point and for the most part resemble what is
    * described in the WebRTC PeerConnection spec: http://w3c.github.io/webrtc-pc/#sec.stats-model, but still there are
    * inconsistencies. The general idea is that stats are a series of reports, each of which has the following structure:
    * id, type, timestamp, series of key/value pairs. Here are 2 reports as they are returned from PeerConnection.getStats()
    * after being converted toString():
    *
    * id: ssrc_2321116827_send, type: ssrc, timestamp: 1.501168721148511E12, values: [audioInputLevel:188], [bytesSent:22532], [mediaType:audio], [packetsSent:131],
    *   [ssrc:2321116827], [transportId:Channel-audio-1], [googCodecName:PCMU], [googEchoCancellationReturnLoss:-100], [googEchoCancellationReturnLossEnhancement:-100],
    *   [googResidualEchoLikelihood:0.0581064], [googResidualEchoLikelihoodRecentMax:0.0581064], [googTrackId:ARDAMSa0], [googTypingNoiseState:false],
    * id: Channel-audio-1, type: googComponent, timestamp: 1.501168721148511E12, values: [selectedCandidatePairId: Conn-audio-1-0], [googComponent: 1],
    *   [dtlsCipher: TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256], [localCertificateId: googCertificate_34:0E:F4:9B:39:06:49:14:E0:25:34:96:95:9E:E3:4B:95:B4:86:31:86:4E:74:5D:4D:A4:C5:13:46:A1:31:17], [remoteCertificateId: googCertificate_82:1E:5E:EB:B5:0D:F8:CF:7A:72:43:FE:91:3A:CE:DC:20:D1:4E:F4:69:4B:06:B4:AA:03:41:67:19:F1:E5:24], [srtpCipher: AES_CM_128_HMAC_SHA1_80],
    *
    * We use a conversion mechanism to turn that into a usable json string of the following format. Here's how the previous
    * 2 reports look like to get an idea of the changes involved:
    * {
    *    "media":[
    *       {
    *          "id":"ssrc_2321116827_send",
    *          "type":"ssrc",
    *          "timestamp":"1.501168721148511E12",
    *          "values":{
    *             "audioInputLevel":"188",
    *             "bytesSent":"22532",
    *             "mediaType":"audio",
    *             "packetsSent":"131",
    *             "ssrc":"2321116827",
    *             "transportId":"Channel-audio-1",
    *             "googCodecName":"PCMU",
    *             "googEchoCancellationReturnLoss":"-100",
    *             "googEchoCancellationReturnLossEnhancement":"-100",
    *             "googResidualEchoLikelihood":"0.0581064",
    *             "googResidualEchoLikelihoodRecentMax":"0.0581064",
    *             "googTrackId":"ARDAMSa0",
    *             "googTypingNoiseState":"false"
    *          }
    *       },
    *       {
    *          "id":"Channel-audio-1",
    *          "type":"googComponent",
    *          "timestamp":"1.501168721148511E12",
    *          "values":{
    *             "selectedCandidatePairId":"Conn-audio-1-0",
    *             "googComponent":"1",
    *             "dtlsCipher":"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    *             "localCertificateId":"googCertificate_34:0E:F4:9B:39:06:49:14:E0:25:34:96:95:9E:E3:4B:95:B4:86:31:86:4E:74:5D:4D:A4:C5:13:46:A1:31:17",
    *             "remoteCertificateId":"googCertificate_82:1E:5E:EB:B5:0D:F8:CF:7A:72:43:FE:91:3A:CE:DC:20:D1:4E:F4:69:4B:06:B4:AA:03:41:67:19:F1:E5:24",
    *             "srtpCipher":"AES_CM_128_HMAC_SHA1_80"
    *          }
    *       },
    *       ...
    *    ]
    * }
    *
    * For now we only support stats relevant to the media of the call, but later we can introduce more keys apart from 'media', like 'signaling' or 'cellular'
    *
    * @param reports
    * @return the stats into a valid json string for easy parsing by the application
    */
   private String webrtcStatsReports2JsonString(StatsReport[] reports)
   {
      // This is a 'special' sequence of chars that is guaranteed to not exist inside the stats string. Reason we want that is that
      // we want to be able to differentiate in our regex processing between ': ' and ':'. And the reason is that the former is the
      // the delimiter between keys and values in json, while the second is just a color character that can be found inside values.
      final String SPECIAL_CHARS = "++";

      // Add a 'media' key that will contain all webrtc media related stats and open an array that will contain all reports coming from PeerConnection.getStats()
      StringBuilder statsStringBuilder = new StringBuilder("{\"media\": [");

      for (StatsReport report : reports) {
         // First do the regex work using a String. Strings aren't very efficient due to being immutable, but they have nice regex facilities.
         // Given that this only happens once every call I think we 're good
         String stringReport = report.toString().replaceFirst("\\[", "{")   // replace the first '[' found after the 'values' section to '{', so that all key/values in the 'values' section are grouped together
               .replace("[", "")   // remove all other '[' characters from the values section as they would break json
               .replace("]", "")   // same for all other '[' characters
               .replace(": ", SPECIAL_CHARS + " ")   // replace the delimiting character between original report string (i.e. ': ') to some special chars, so that other occurences of ':', like in the DTLS section isn't messed up
               .replaceAll("([^,\\[\\]\\{\\} ]+)", "\"$1\"")   // add double quotes around all words as they need to be quoted to be valid json
               .replace(SPECIAL_CHARS + "\"", "\":")   // replace special chars back to ':' now that the previous step is done and there is no fear for confusion
               .replace(": ,", ": \"\",");   // fix any non existing values and replace with empty string in the key/values pairs of the 'values' section

         // Then combine everything using StringBuilder
         statsStringBuilder.append("{")   // append new section in json before the report starts
               .append(stringReport)   //  append report we generated before
               .replace(statsStringBuilder.lastIndexOf(","), statsStringBuilder.lastIndexOf(",") + 1, "")   // remove last comma in report that would mess json, since there is not any other element afterwards
               .append("}},");   // wrap the report by closing all open braces, and adding a comma, so that reports are separated properly between themselves
      }

      // go back and remove comma from last report, to avoid ruining json
      statsStringBuilder.replace(statsStringBuilder.lastIndexOf(","), statsStringBuilder.lastIndexOf(",") + 1, "");
      // close array of reports and initial section
      statsStringBuilder.append("]}");

      return statsStringBuilder.toString();
   }

   // Return a json string representation of the connection stats (currently only webrtc peer connection stats are included)
   public String getStats()
   {
      RCLogger.i(TAG, "getStats()");

      return webrtcReportsJsonString;
   }

   /**
    * Mute connection so that the other party cannot hear local audio
    *
    * @param muted True to mute and false in order to unmute
    */
   public void setAudioMuted(boolean muted)
   {
      RCLogger.i(TAG, "setAudioMuted(): " + muted);

      /*
      if (audioManager != null) {
         audioManager.setMute(muted);
      }
      */

      if (this.peerConnectionClient != null) {
         this.peerConnectionClient.setLocalAudioEnabled(!muted);
      }
      else {
         RCLogger.e(TAG, "setAudioMuted called when peerConnectionClient in NULL");
      }

      device.onNotificationMuteChanged(this);
   }

   /**
    * Retrieve whether connection audio is muted or not
    *
    * @return True connection is muted and false otherwise
    */
   public boolean isAudioMuted()
   {
      /*
      if (audioManager != null) {
         return audioManager.getMute();
      }
      else {
         RCLogger.e(TAG, "isMuted called on null audioManager -check memory management");
      }
      */

      if (this.peerConnectionClient != null) {
         return !this.peerConnectionClient.getLocalAudioEnabled();
      }
      else {
         RCLogger.e(TAG, "isAudioMuted called when peerConnectionClient in NULL");
      }

      return false;
   }

   /**
    * Mute connection so that the other party cannot see local video
    *
    * @param muted True to mute and false in order to unmute
    */
   public void setVideoMuted(boolean muted)
   {
      RCLogger.i(TAG, "setVideoMuted(): " + muted);
      hasUserMutedVideo = muted;
      handleVideoMuted(muted);
   }

   private void handleVideoMuted(boolean muted)
   {
      if (this.peerConnectionClient != null) {
         this.peerConnectionClient.setLocalVideoEnabled(!muted);
         if (muted) {
            localRender.setVisibility(View.INVISIBLE);
         }
         else {
            localRender.setVisibility(View.VISIBLE);
         }
      }
   }


   /**
    * Retrieve whether connection video is muted or not
    *
    * @return True connection is muted and false otherwise
    */
   public boolean isVideoMuted()
   {
      if (this.peerConnectionClient != null) {
         return !this.peerConnectionClient.getLocalVideoEnabled();
      }
      return false;
   }

   /**
    * Send DTMF digits over the connection
    *
    * @param digits A string of DTMF digits to be sent
    */
   public void sendDigits(String digits)
   {
      RCLogger.i(TAG, "sendDigits(): " + digits);

      if (state == ConnectionState.CONNECTED) {
         signalingClient.sendDigits(this.jobId, digits);
      }
      else {
         // let's delay a millisecond to avoid calling code in the App getting intertwined with App listener code
         new Handler(device.getMainLooper()).postDelayed(
               new Runnable() {
                  @Override
                  public void run()
                  {
                     if (device.isAttached()) {
                        listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_WRONG_STATE.ordinal(),
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_WRONG_STATE));
                     }
                     else {
                        RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_WRONG_STATE));
                     }
                  }
               }
               , 1);
      }
   }

   /**
    * Update connection listener to be receiving Connection related events. This is
    * usually needed when we switch activities and want the new activity to receive
    * events
    *
    * @param listener New connection listener
    */
   public void setConnectionListener(RCConnectionListener listener)
   {
      RCLogger.i(TAG, "setConnectionListener()");

      this.listener = listener;
   }

   // ------ Call-related callbacks received from signaling thread are handled here
   public void onCallOutgoingPeerRingingEvent(String jobId)
   {
      RCLogger.i(TAG, "onCallOutgoingPeerRingingEvent(): jobId: " + jobId);

      //audioManager.play(R.raw.calling, true);
      audioManager.playCallingSound();
      state = ConnectionState.CONNECTING;
      if (device.isAttached()) {
         listener.onConnecting(this);
      }
      else {
         RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onConnecting()");
      }

      // Phone state Intents to capture connecting event
      sendQoSConnectionIntent("connecting");
   }

   public void onCallIncomingConnectedEvent(String jobId)
   {
      // no need to do any notifying as the App is notified when ICE is connected
      RCLogger.i(TAG, "onCallIncomingConnectedEvent(): jobId: " + jobId);

      // In inbound connections there's a chance that media starts flowing before signaling is connected (remember that for inbound connections signaling is deemed connected when SIP ACK is received).
      // If that happens we don't want to update to SIGNALLING_CONNECTED
      if (state != ConnectionState.CONNECTED) {
         state = ConnectionState.SIGNALING_CONNECTED;
      }
   }

   public void onCallOutgoingConnectedEvent(String jobId, String sdpAnswer, HashMap<String, String> customHeaders)
   {
      RCLogger.i(TAG, "onCallOutgoingConnectedEvent(): jobId: " + jobId + " customHeaders: " + customHeaders);

      state = ConnectionState.SIGNALING_CONNECTED;
      startMediaTimer();

      if (customHeaders != null) {
         callParams.put(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS, customHeaders);
      }
      // we want to notify webrtc onRemoteDescription *only* on an outgoing call
      if (!this.isIncoming()) {
         remoteMediaType = sdp2Mediatype(sdpAnswer);
         onRemoteDescription(sdpAnswer);
      }
   }

   public void onCallLocalDisconnectedEvent(String jobId)
   {
      RCLogger.i(TAG, "onCallLocalDisconnectedEvent(): jobId: " + jobId);
      handleDisconnected(jobId, true);
   }

   public void onCallIncomingCanceledEvent(String jobId)
   {
      RCLogger.i(TAG, "onCallIncomingCanceledEvent(): jobId: " + jobId);
      device.onNotificationCallCanceled(this);
      handleDisconnected(jobId, false);
   }

   public void onCallPeerDisconnectEvent(String jobId)
   {
      RCLogger.i(TAG, "onCallPeerDisconnectEvent(): jobId: " + jobId);

      handleDisconnected(jobId, false);
   }

   public void onCallSentDigitsEvent(String jobId, RCClient.ErrorCodes statusCode, String statusText)
   {
      RCLogger.i(TAG, "onCallSentDigitsEvent(): jobId: " + jobId + ", status: " + statusCode + ", text: " + statusText);
      if (device.isAttached()) {
         listener.onDigitSent(this, statusCode.ordinal(), statusText);
      }
      else {
         RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDigitSent()");
      }

   }

   // When a call signaling error occurs, we can assume that the call has been killed and the App doesn't have to do anything like hanging it up. The signaling facilities take care of proper call
   // termination
   public void onCallErrorEvent(String jobId, RCClient.ErrorCodes errorCode, String errorText)
   {
      RCLogger.e(TAG, "onCallErrorEvent(): jobId: " + jobId + ", error code: " + errorCode + ", error text: " + errorText);

      errorOccurred = true;

      // TODO: we need to see if there's a chance a call that causes an error to remain up,
      // if not we need to avoid disconnecting below
      if (state != ConnectionState.DISCONNECTING) {
         // only disconnect signaling facilities if we are not already disconnecting
         //signalingClient.disconnect(jobId, null);
      }
      /*
      else {
         // an error has occured while we are disconnecting. Since the normal disconnect flow is being interrupted, we need to tell notification
         // facilities that forground service notification needs to stop.
         device.onNotificationCallDisconnected(this);
      }
      */

      audioManager.stop();

      disconnectWebrtc();
      device.onNotificationCallDisconnected(this);

      if (RCDevice.state == RCDevice.DeviceState.BUSY) {
         RCDevice.state = RCDevice.DeviceState.READY;
      }

      this.state = ConnectionState.DISCONNECTED;
      device.removeConnection(jobId);

      if (device.isAttached() && listener != null) {
         listener.onError(this, errorCode.ordinal(), errorText);
      }
      else {
         RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDisconnected()");
      }

   }

   // Common disconnect code for local/remote disconnect and remote cancel
   // If this is called after we have disconnected locally (i.e. RCConnection.disconnect() was called) we
   // don't need to disconnect media
   private void handleDisconnected(String jobId, boolean haveDisconnectedLocally)
   {
      timeoutHandler.removeCallbacksAndMessages(null);
      candidateTimeoutHandler.removeCallbacksAndMessages(null);

      // Device was already busy with another Connection, skip all handling here
      if (deviceAlreadyBusy) {
         return;
      }

      // IMPORTANT: we 're first notifying listener and then setting new state because we want the listener to be able to
      // differentiate between disconnect and remote cancel events with the same listener method: onDisconnected.
      // In the first case listener will see state CONNECTED and in the second CONNECTING

      if (!isIncoming() && state == ConnectionState.CONNECTING) {
         // outgoing call is ringing at the peer, and the peer disconnects, need to play busy
         audioManager.playDeclinedSound();
      }
      else {
         audioManager.stop();
      }

      //if (inboundDisconnect && RCDevice.state == RCDevice.DeviceState.BUSY) {
      if (!haveDisconnectedLocally && RCDevice.state == RCDevice.DeviceState.BUSY) {
         // No need to disconnect signaling, it is already disconnected both when we cause
         // disconnect and when the remote party does
         disconnectWebrtc();
      }

      device.onNotificationCallDisconnected(this);

      if (listener != null && device.isAttached()) {
         listener.onDisconnected(this);
      }
      else {
         RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached or listener not set: onDisconnected()");
      }


      RCDevice.state = RCDevice.DeviceState.READY;
      this.state = ConnectionState.DISCONNECTED;
      device.removeConnection(jobId);

      // Phone state Intents to capture normal disconnect event
      sendQoSConnectionIntent("disconnected");
   }

   // Handle local disconnect
   private void handleDisconnect(String reason)
   {
      RCLogger.i(TAG, "handleDisconnect(): reason: " + reason);
      timeoutHandler.removeCallbacksAndMessages(null);
      candidateTimeoutHandler.removeCallbacksAndMessages(null);

      audioManager.stop();

      if (state != ConnectionState.DISCONNECTED && state != ConnectionState.DISCONNECTING) {
         signalingClient.disconnect(jobId, reason);
         disconnectWebrtc();

         state = ConnectionState.DISCONNECTING;
         // also update RCDevice state. Reason we need that is twofold: a. if a call times out in signaling for a reason it will take around half a minute to
         // get response from signaling, during which period we won't be able to make a call, b. there are some edge cases where signaling hangs and never times out
         if (RCDevice.state == RCDevice.DeviceState.BUSY) {
            RCDevice.state = RCDevice.DeviceState.READY;
         }

         // there are cases when there's a weird error from Restcomm that might not be handled in lower level signaling facilities and hence the
         // notification isn't removed from Android. To better handle that let's remove the notification upon local disconnect right away
         device.onNotificationCallDisconnected(this);
      }
      else if (state == ConnectionState.DISCONNECTING) {
         RCLogger.w(TAG, "disconnect(): Attempting to disconnect while we are in state disconnecting, skipping.");
      }
      else {
         // state == ConnectionState.DISCONNECTED

         // If an error has occurred it's normal to be already disconnected; signaling facilities have already terminated the call.
         // If not then we need to notify App of the error: that they are calling disconnect for a second time
         if (!errorOccurred) {
            // let's delay a millisecond to avoid calling code in the App getting intertwined with App listener code
            new Handler(device.getMainLooper()).postDelayed(
                    new Runnable() {
                       @Override
                       public void run() {
                          if (device.isAttached()) {
                             listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE.ordinal(),
                                     RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE));
                          } else {
                             RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                                     RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE));
                          }

                       }
                    }
                    , 1);
         }
      }
   }

   public String getId()
   {
      return jobId;
   }

   public String getPeer()
   {
      return peer;
   }

   // ------ WebRTC stuff:
   // IceServerFetcher callbacks
   @Override
   public void onIceServersReady(final LinkedList<PeerConnection.IceServer> iceServers)
   {
      RCLogger.d(TAG, "onIceServersReady");
      // Important: need to fire the event in UI context to make sure no races will arise
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {

            if (RCConnection.this.callParams.containsKey(ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT) &&
                    (Integer) RCConnection.this.callParams.get(ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT) != 0) {
               // cancel any pending timers before we start new one
               candidateTimeoutHandler.removeCallbacksAndMessages(null);
               Runnable runnable = new Runnable() {
                  @Override
                  public void run()
                  {
                     onCandidatesTimeout();
                  }
               };
               candidateTimeoutHandler.postDelayed(runnable, (Integer) RCConnection.this.callParams.get(ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT) * 1000);
               //candidateTimeoutHandler.postDelayed(runnable, 150);
            }

            if (!RCConnection.this.incoming) {
               // we are the initiator

               // create a new hash map
               HashMap<String, String> sipHeaders = null;
               if (RCConnection.this.callParams.containsKey(ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS)) {
                  sipHeaders = (HashMap<String, String>) RCConnection.this.callParams.get(ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS);
               }

               RCConnection.this.signalingParameters = new SignalingParameters(iceServers, true, "", peer,
                     "", null, null, sipHeaders, RCConnection.this.callParams.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) && (boolean) RCConnection.this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED));
            }
            else {
               // we are not the initiator
               RCConnection.this.signalingParameters = new SignalingParameters(iceServers, false, "", "", "", null, null, null,
                       RCConnection.this.callParams.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) && (boolean) RCConnection.this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED));
               SignalingParameters params = SignalingParameters.extractCandidates(new SessionDescription(SessionDescription.Type.OFFER, incomingCallSdp));
               RCConnection.this.signalingParameters.offerSdp = params.offerSdp;
               RCConnection.this.signalingParameters.iceCandidates = params.iceCandidates;
            }
            startCall(RCConnection.this.signalingParameters);
         }
      };
      mainHandler.post(myRunnable);
   }

   @Override
   public void onIceServersError(final String description)
   {
      // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            handleDisconnect(null);

            if (RCDevice.state == RCDevice.DeviceState.BUSY) {
               RCDevice.state = RCDevice.DeviceState.READY;
            }

            if (device.isAttached()) {
               RCConnection.this.listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_TURN_ERROR.ordinal(), description);
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDisconnected()");
            }

         }
      };
      mainHandler.post(myRunnable);
   }

   // Outgoing call
   private void setupWebrtcAndCall(Map<String, Object> parameters)
   {
      boolean videoEnabled = parameters.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) && (Boolean)parameters.get(ParameterKeys.CONNECTION_VIDEO_ENABLED);
      if (!checkPermissions(videoEnabled)) {
         return;
      }

      this.callParams.putAll(parameters);
      initializeWebrtc(this.callParams.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) && (Boolean) this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED),
            (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_LOCAL_VIDEO),
            (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_REMOTE_VIDEO),
            (VideoCodec) parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC),
            (AudioCodec) parameters.get(ParameterKeys.CONNECTION_PREFERRED_AUDIO_CODEC),
            (VideoResolution)parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_RESOLUTION),
            (VideoFrameRate)parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_FRAME_RATE));

      startTurn();
   }

   private void startTurn()
   {
      HashMap<String, Object> deviceParameters = device.getParameters();
      String url;

      boolean turnEnabled = false;
      if (deviceParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED) &&
              (Boolean)deviceParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED)) {
         turnEnabled = true;
      }

      RCDevice.MediaIceServersDiscoveryType iceServerDiscoveryType;
      //we are storing enum in hash map or in storage manager; in both facilities enum is stored differently
      if (deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE) instanceof Enum){
         iceServerDiscoveryType = (RCDevice.MediaIceServersDiscoveryType) deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE);
      } else {
         iceServerDiscoveryType = RCDevice.MediaIceServersDiscoveryType.values()[(int)deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE)];
      }
     
      if (iceServerDiscoveryType == RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V2) {
         url = deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_URL) +
                 "?ident=" + deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME) +
                 "&secret=" + deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD) +
                 "&domain=" + deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN) +
                 "&application=default&room=default&secure=1";
      }
      else if (iceServerDiscoveryType == RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3) {
         url = deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_URL) +
                 "/" + deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN);
      }
      else {
         // ICE_SERVERS_CUSTOM
         onIceServersReady(external2InternalIceServers((List<Map<String, String>>)deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS)));
         return;
      }

      //String url = "https://service.xirsys.com/ice?ident=atsakiridis&secret=SECRET_HERE&domain=cloud.restcomm.com&application=default&room=default&secure=1";
      //url = "https://ice.restcomm.io/_turn/restcomm";
      new IceServerFetcher(url, turnEnabled, iceServerDiscoveryType, (String)deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME),
              (String)deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD), this).makeRequest();
   }

   private LinkedList<PeerConnection.IceServer> external2InternalIceServers(List<Map<String, String>> iceServers)
   {
      RCLogger.e(TAG, "Using manual ICE server discovery");
      LinkedList<PeerConnection.IceServer> iceServersInternal = new LinkedList<PeerConnection.IceServer>();
      for (Map<String, String> iceServer : iceServers) {
         PeerConnection.IceServer iceServerInternal = external2InternalIceServer(iceServer);
         iceServersInternal.add(iceServerInternal);
         RCLogger.i(TAG, "ICE server: " + iceServerInternal.uri + ", " + iceServerInternal.username);
      }

      return iceServersInternal;
   }

   private PeerConnection.IceServer external2InternalIceServer(Map<String, String> iceServer)
   {
      String url = "";
      if (iceServer.containsKey(IceServersKeys.ICE_SERVER_URL)) {
         url = iceServer.get(IceServersKeys.ICE_SERVER_URL);
      }
      String username = "";
      if (iceServer.containsKey(IceServersKeys.ICE_SERVER_USERNAME)) {
         username = iceServer.get(IceServersKeys.ICE_SERVER_USERNAME);
      }
      String password = "";
      if (iceServer.containsKey(IceServersKeys.ICE_SERVER_PASSWORD)) {
         password = iceServer.get(IceServersKeys.ICE_SERVER_PASSWORD);
      }

      return new PeerConnection.IceServer(url, username, password);
   }

   private void startMediaTimer()
   {
      // cancel any pending timers before we start new one
      timeoutHandler.removeCallbacksAndMessages(null);
      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            onCallTimeout();
         }
      };
      timeoutHandler.postDelayed(runnable, CALL_TIMEOUT_DURATION_MILIS);
   }

   // If permission is granted we return true
   private boolean checkPermissions(boolean isVideo)
   {
      ArrayList<String> permissions = new ArrayList<>(Arrays.asList(MANDATORY_PERMISSIONS));
      if (isVideo) {
         // Only add CAMERA permission if this is a video call
         permissions.add(Manifest.permission.CAMERA);
      }

      // Check for mandatory permissions.
      for (String permission : permissions) {
         if (device.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            RCLogger.e(TAG, "Permission " + permission + " is not granted");

            handleDisconnect("Device-Permissions-Denied");

            if (device.isAttached()) {
               listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_PERMISSION_DENIED.ordinal(),
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_PERMISSION_DENIED));
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_PERMISSION_DENIED));
            }


            if (!isIncoming()) {
               // Only remove connection in outgoing calls where no signaling ever starts (hence we are really done with the connection).
               // Remember that for incoming signaling has already kicked in, hence the connection will be removed
               // when onCallLocalDisconnectedEvent() is called
               device.removeConnection(jobId);
            }

            return false;
         }
      }
      return true;
   }

   // Called if call hasn't been established in the predefined period
   private void onCallTimeout()
   {
      RCLogger.e(TAG, "onCallTimeout(): State: " + state + ", after: " + CALL_TIMEOUT_DURATION_MILIS);

      String reason = "Call-Timeout-Media";
      RCClient.ErrorCodes errorCode = RCClient.ErrorCodes.ERROR_CONNECTION_MEDIA_TIMEOUT;

      handleDisconnect(reason);

      if (device.isAttached() && listener != null) {
         this.listener.onError(this, errorCode.ordinal(), RCClient.errorText(errorCode));
      }
      else {
         RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDisconnected()");
      }

      // Phone state Intents to capture dropped call event
      sendQoSDisconnectErrorIntent(errorCode.ordinal(), RCClient.errorText(errorCode));
   }

   private void onCandidatesTimeout()
   {
      RCLogger.e(TAG, "onCandidatesTimeout: Candidates timed out after: " + RCConnection.this.callParams.get(ParameterKeys.DEBUG_CONNECTION_CANDIDATE_TIMEOUT) + " seconds");

      if (signalingParameters != null && signalingParameters.iceCandidates != null &&
              signalingParameters.iceCandidates.size() > 0) {
         RCLogger.w(TAG, "onCandidatesTimeout: Managed to collect: " + signalingParameters.iceCandidates.size() + " candidates");
         onIceGatheringComplete();
      }
      else {
         // no candidates are gathered
         handleDisconnect(null);

         if (RCDevice.state == RCDevice.DeviceState.BUSY) {
            RCDevice.state = RCDevice.DeviceState.READY;
         }

         if (device.isAttached()) {
            RCConnection.this.listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_CANDIDATES_TIMED_OUT.ordinal(),
                    RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_CANDIDATES_TIMED_OUT));
         }
         else {
            RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDisconnected()");
         }

      }
   }

   /**/
   // TODO: Issue #380: We should uncomment this once we figure out https://groups.google.com/forum/#!searchin/discuss-webrtc/tsakiridis$20android%7Csort:relevance/discuss-webrtc/XE2Ok67B1Ks/RrqmfZh9AQAJ
   // Implementation above is meant to be a temporary solution, as it doesn't allow for the call Activity to be destroyed and then re-created
   public void detachVideo()
   {
      RCLogger.i(TAG, "detachVideo()");
      // TODO: handle case of audio only call
      if (localRender != null) {
         localRender.setVisibility(View.INVISIBLE);
      }

      if(remoteRender != null) {
         remoteRender.setVisibility(View.INVISIBLE);
      }
      peerConnectionClient.detachVideo();
   }

   public void reattachVideo(final PercentFrameLayout localRenderLayout, final PercentFrameLayout remoteRenderLayout)
   {
      RCLogger.i(TAG, "reattachVideo()");
      boolean videoEnabled = false;
      if ((isIncoming() && getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO) ||
              (!isIncoming() && getLocalMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO))  {
         videoEnabled = true;
      }

      initializeVideo(videoEnabled, localRenderLayout, remoteRenderLayout);
      peerConnectionClient.reattachVideo(localRender, remoteRender, !hasUserMutedVideo);
   }
   /**/

   // Callback fired when video is paused after call to pauseVideo()
   // IMPORTANT: runs in media thread, need to post on Main thread
   public void onVideoDetached()
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onVideoDetached");

            releaseVideo();
         }
      };
      mainHandler.post(myRunnable);
   }

   // Callback fired when video is resumed after call to resumeVideo()
   // IMPORTANT: runs in media thread, need to post on Main thread
   public void onVideoReattached()
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onVideoReattached");
            updateVideoView(VideoViewState.ICE_CONNECTED);
         }
      };
      mainHandler.post(myRunnable);

   }

   private void initializeVideo(boolean videoEnabled, PercentFrameLayout localRenderLayout, PercentFrameLayout remoteRenderLayout)
   {
      if (localRenderLayout == null ||remoteRenderLayout == null) {
         return;
      }

      scalingType = ScalingType.SCALE_ASPECT_FILL;

      this.localRenderLayout = localRenderLayout;
      this.remoteRenderLayout = remoteRenderLayout;

      localRender = (SurfaceViewRenderer)localRenderLayout.getChildAt(0);
      remoteRender = (SurfaceViewRenderer)remoteRenderLayout.getChildAt(0);

      localRender.init(peerConnectionClient.getRenderContext(), null);
      localRender.setZOrderMediaOverlay(true);
      remoteRender.init(peerConnectionClient.getRenderContext(), null);
      updateVideoView(VideoViewState.NONE);
   }

   private Size resolutionEnum2Resolution(RCConnection.VideoResolution resolutionEnum)
   {
      if (resolutionEnum == null) {
         return new Size(0, 0);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_QQVGA_160x120) {
         return new Size(160, 120);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_QCIF_176x144) {
         return new Size(176, 144);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_QVGA_320x240) {
         return new Size(320, 240);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_CIF_352x288) {
         return new Size(352, 288);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_nHD_640x360) {
         return new Size(640, 360);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_VGA_640x480) {
         return new Size(640, 480);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_SVGA_800x600) {
         return new Size(800, 600);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_HD_1280x720) {
         return new Size(1280, 720);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_UXGA_1600x1200) {
         return new Size(1600, 1200);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_FHD_1920x1080) {
         return new Size(1920, 1080);
      }
      else if (resolutionEnum == VideoResolution.RESOLUTION_UHD_3840x2160) {
         return new Size(3840, 2160);
      }
      else {
         return new Size(0, 0);
      }
   }

   private int frameRateEnum2Int(VideoFrameRate videoFrameRate)
   {
      if (videoFrameRate == null) {
         return 0;
      }
      else if (videoFrameRate == VideoFrameRate.FPS_15) {
         return 15;
      }
      else if (videoFrameRate == VideoFrameRate.FPS_30) {
         return 30;
      }
      /*
      else if (videoFrameRate == VideoFrameRate.FPS_60) {
         return 60;
      }
      */
      else {
         // default is 0 which is conveyed by PeerConnection facilities as default
         return 0;
      }
   }

   private String audioCodecEnum2String(RCConnection.AudioCodec audioCodec)
   {
      if (audioCodec == null) {
         return "OPUS";
      }
      else if (audioCodec == AudioCodec.AUDIO_CODEC_ISAC) {
         return "ISAC";
      }
      else if (audioCodec == AudioCodec.AUDIO_CODEC_OPUS) {
         return "OPUS";
      }
      else {
         // default is OPUS
         return "OPUS";
      }
   }

   private String videoCodecEnum2String(RCConnection.VideoCodec videoCodec)
   {
      if (videoCodec == null) {
         return "VP8";
      }
      else if (videoCodec == VideoCodec.VIDEO_CODEC_VP8) {
         return "VP8";
      }
      else if (videoCodec == VideoCodec.VIDEO_CODEC_VP9) {
         return "VP9";
      }
      else if (videoCodec == VideoCodec.VIDEO_CODEC_H264) {
         return "H264";
      }
      else {
         // default is VP8
         return "VP8";
      }
   }

   // initialize webrtc facilities for the call
   private void initializeWebrtc(boolean videoEnabled,
                                 PercentFrameLayout localRenderLayout,
                                 PercentFrameLayout remoteRenderLayout,
                                 VideoCodec preferredVideoCodec,
                                 AudioCodec preferredAudioCodec,
                                 VideoResolution videoResolution,
                                 VideoFrameRate videoFrameRate)
   {
      RCLogger.i(TAG, "initializeWebrtc()");

      iceConnected = false;
      signalingParameters = null;

      if (videoEnabled) {
         localMediaType = ConnectionMediaType.AUDIO_VIDEO;
      }
      else {
         localMediaType = ConnectionMediaType.AUDIO;
      }

      // Create peer connection client
      peerConnectionClient = new PeerConnectionClient();

      if (localRenderLayout != null && remoteRenderLayout != null) {
         initializeVideo(videoEnabled, localRenderLayout, remoteRenderLayout);
      }

      String preferredVideoCodecString = videoCodecEnum2String(preferredVideoCodec);
      String preferredAudioCodecString = audioCodecEnum2String(preferredAudioCodec);
      // Local resolution
      Size resolution = resolutionEnum2Resolution(videoResolution);
      int frameRateInt = frameRateEnum2Int(videoFrameRate);

      RCLogger.i(TAG, "Initializing PeerConnection parameters: audioCodec: " + preferredAudioCodecString + ", videoCodec: " + preferredVideoCodecString +
            ", resolution: " + resolution + ", frameRate: " + frameRateInt);

      peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
            videoEnabled,  // video call
            false,  // loopback
            false,  // tracing
            resolution.width,  // video width
            resolution.height,  // video height
            frameRateInt,  // video fps
            0,  // video start bitrate
            preferredVideoCodecString,  // video codec
            true,  // video codec hw acceleration enabled
            false, // capture to texture
            0,  // audio start bitrate
            preferredAudioCodecString,  // audio codec
            false,  // no audio processing
            false,  // AEC dump
            false,  // use OpenGLES
            false,  // disable builtin AEC
            false,  // disable builtin AGC
            false,  // disable builtin NS
            false,
            false);  // enable level control

      createPeerConnectionFactory();
   }

   private void updateVideoView(VideoViewState state)
   {
      RCLogger.i(TAG, "updateVideoView(), state: " + state);
      // only if both local and remote views for video have been provided do we want to go ahead
      // and update the video views
      if (this.localRenderLayout == null && this.remoteRenderLayout == null) {
         return;
      }

      if (state == VideoViewState.NONE) {
         // when call starts both local and remote video views should be hidden
         localRender.setVisibility(View.INVISIBLE);
         remoteRender.setVisibility(View.INVISIBLE);
      }
      else if (state == VideoViewState.LOCAL_VIEW_RECEIVED) {
         // local video became available, which also means that local user has previously requested a video call,
         // hence we need to show local video view
         localRender.setVisibility(View.VISIBLE);

         localRenderLayout.setPosition(
               LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING, LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING);
         localRender.setScalingType(scalingType);
         localRender.setMirror(true);
         localRender.requestLayout();
      }
      else if (state == VideoViewState.REMOTE_VIEW_RECEIVED) {
         // remote video became available, which also means that remote user has requested a video call,
         // hence we need to show remote video view
         //remoteRender.setVisibility(View.VISIBLE);
      }
      else if (state == VideoViewState.ICE_CONNECTED) {
         if (remoteVideoReceived && localMediaType == ConnectionMediaType.AUDIO_VIDEO) {
            remoteRender.setVisibility(View.VISIBLE);

            remoteRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
            remoteRender.setScalingType(scalingType);
            remoteRender.setMirror(false);

            if (this.callParams.containsKey(ParameterKeys.CONNECTION_VIDEO_ENABLED) &&
                  ((Boolean) this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED)) &&
                  localRender.getVisibility() != View.VISIBLE) {
               localRender.setVisibility(View.VISIBLE);
            }
            localRenderLayout.setPosition(
                  LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
            localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
            localRender.setMirror(true);

            localRender.requestLayout();
            remoteRender.requestLayout();
         }
      }
   }

   private void startCall(SignalingParameters signalingParameters)
   {
      RCLogger.i(TAG, "startCall");
      callStartedTimeMs = System.currentTimeMillis();

      // Start room connection.
      logAndToast("Preparing call");

      // we don't have room functionality to notify us when ready; instead, we start connecting right now
      this.onConnectedToRoom(signalingParameters);
   }

   // Disconnect from remote resources, dispose of local resources, and exit.
   private void disconnectWebrtc()
   {
      RCLogger.i(TAG, "disconnectWebrtc");

      if (peerConnectionClient != null) {
         peerConnectionClient.close();
         peerConnectionClient = null;
      }

      releaseVideo();
      audioManager.endCallMedia();
   }

   private void releaseVideo()
   {
      if (localRender != null) {
         localRender.release();
         localRender = null;
      }
      if (localRenderLayout != null) {
         localRenderLayout = null;
      }
      if (remoteRender != null) {
         remoteRender.release();
         remoteRender = null;
      }
      if (remoteRenderLayout != null) {
         remoteRenderLayout = null;
      }
   }

   /*
   private void onAudioManagerChangedState()
   {
      // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
      // is active.
   }
   */


   // Create peer connection factory when EGL context is ready.
   private void createPeerConnectionFactory()
   {
      final RCConnection connection = this;
      // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "createPeerConnectionFactory");
            if (peerConnectionClient != null) {
               final long delta = System.currentTimeMillis() - callStartedTimeMs;
               RCLogger.d(TAG, "Creating peer connection factory, delay=" + delta + "ms");
               //peerConnectionClient = PeerConnectionClient.getInstance();
               peerConnectionClient.createPeerConnectionFactory(device,
                     peerConnectionParameters,
                     connection);
               logAndToast("Created PeerConnectionFactory.");
            }
            if (signalingParameters != null) {
               RCLogger.w(TAG, "EGL context is ready after room connection.");
               // #WEBRTC-VIDEO TODO: when I disabled the video view stuff, I also had to comment this out cause it turns out
               // that in that case this part of the code was executed (as if signalingParameters was null and now it isn't),
               // which resulted in onConnectedToRoomInternal being called twice for the same call! When I reinstate
               // video this should probably be uncommented:
               //onConnectedToRoomInternal(signalingParameters);
            }
         }
      };
      mainHandler.post(myRunnable);
   }

   // Log |msg| and Toast about it.
   private void logAndToast(String msg)
   {
      RCLogger.d(TAG, msg);
      if (DO_TOAST) {
         if (logToast != null) {
            logToast.cancel();
         }
         logToast = Toast.makeText(device, msg, Toast.LENGTH_SHORT);
         logToast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
         logToast.show();
      }
   }

   // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
   // Send local peer connection SDP and ICE candidates to remote party.
   // All callbacks are invoked from peer connection client looper thread and
   // are routed to UI thread.
   @Override
   public void onLocalDescription(final SessionDescription sdp)
   {
      final long delta = System.currentTimeMillis() - callStartedTimeMs;
      final RCConnection connection = this;
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onLocalDescription" + sdp.type + ", delay=" + delta + "ms");
            if (signalingParameters != null) {  // && !signalingParameters.sipUrl.isEmpty()) {
               //logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
               if (signalingParameters.initiator) {
                  // keep it around so that we combine it with candidates before sending it over
                  connection.signalingParameters.offerSdp = sdp;
                  //appRtcClient.sendOfferSdp(sdp);
               }
               else {
                  //appRtcClient.sendAnswerSdp(sdp);
                  connection.signalingParameters.answerSdp = sdp;
                  // for an incoming call we have already stored the offer candidates there, now
                  // we are done with those and need to come up with answer candidates
                  // TODO: this might prove dangerous as the signalingParms struct used to be all const,
                  // but I changed it since with JAIN sip signalling where various parts are picked up
                  // at different points in time
                  connection.signalingParameters.iceCandidates.clear();
               }
            }
         }
      };
      mainHandler.post(myRunnable);
   }

   @Override
   public void onIceCandidate(final IceCandidate candidate)
   {
      final RCConnection connection = this;
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onIceCandidate:" + candidate);
            connection.signalingParameters.addIceCandidate(candidate);
         }
      };
      mainHandler.post(myRunnable);
   }

   @Override
   public void onIceCandidatesRemoved(final IceCandidate[] candidates)
   {
      final RCConnection connection = this;
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onIceCandidateRemoved: Not Implemented Yet");
         }
      };
      mainHandler.post(myRunnable);

   }

   public void onIceGatheringComplete()
   {
      final RCConnection connection = this;

      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onIceGatheringComplete");

            candidateTimeoutHandler.removeCallbacksAndMessages(null);

            if (!iceGatheringCompleteCalled) {
               iceGatheringCompleteCalled = true;

               if (peerConnectionClient == null) {
                  // if the user hangs up the call before its setup we need to bail
                  return;
               }
               if (signalingParameters.initiator) {
                  HashMap<String, Object> parameters = new HashMap<String, Object>();
                  parameters.put(RCConnection.ParameterKeys.CONNECTION_PEER, signalingParameters.sipUrl);
                  parameters.put("sdp", connection.signalingParameters.generateSipSdp(connection.signalingParameters.offerSdp, connection.signalingParameters.iceCandidates));
                  parameters.put(ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS, connection.signalingParameters.sipHeaders);

                  signalingClient.call(jobId, parameters);
               } else {
                  HashMap<String, Object> parameters = new HashMap<>();
                  parameters.put("sdp", connection.signalingParameters.generateSipSdp(connection.signalingParameters.answerSdp,
                          connection.signalingParameters.iceCandidates));
                  signalingClient.accept(jobId, parameters);
                  //connection.state = ConnectionState.CONNECTING;
               }
            }
            else {
               RCLogger.w(TAG, "onIceGatheringComplete() already called, skipping");
            }
         }
      };
      mainHandler.post(myRunnable);
   }

   @SuppressWarnings("unchecked")
   @Override
   public void onIceConnected()
   {
      final long delta = System.currentTimeMillis() - callStartedTimeMs;

      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onIceConnected");

            // stop any calling or ringing sound
            audioManager.stop();
            audioManager.startCallMedia();

            // we 're connected, cancel any pending timeout timers
            timeoutHandler.removeCallbacksAndMessages(null);

            logAndToast("ICE connected, delay=" + delta + "ms");
            iceConnected = true;
            RCConnection.this.state = ConnectionState.CONNECTED;
            updateVideoView(VideoViewState.ICE_CONNECTED);

            HashMap<String, String> customHeaders = null;
            if (callParams.containsKey(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS)) {
               customHeaders = (HashMap<String, String>) callParams.get(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS);
            }

            sendQoSConnectionIntent("connected");

            if (device.isAttached()) {
               device.onNotificationCallConnected(RCConnection.this);
               listener.onConnected(RCConnection.this, customHeaders);
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onConnected()");
            }
            //peerConnectionClient.enableStatsEvents(true, 1000);
         }
      };
      mainHandler.post(myRunnable);
   }

   @Override
   public void onIceDisconnected()
   {
      // Notice that this is actually means that media connectivity has been lost, hence showing an error (maps to IceConnectionState.DISCONNECTED)
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onIceDisconnected");
            logAndToast("ICE disconnected");
            iceConnected = false;
            handleDisconnect("Connectivity-Drop");
         }
      };
      mainHandler.post(myRunnable);
   }

   @Override
   public void onPeerConnectionClosed()
   {
      RCLogger.i(TAG, "onPeerConnectionClosed");
   }

   @Override
   public void onPeerConnectionStatsReady(final StatsReport[] reports)
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            // by the time stats are returned (when requested at disconnect(), iceConnected might have transitioned to disconnected
            webrtcReportsJsonString = webrtcStatsReports2JsonString(reports);
            try {
               //String statsJsonString = webrtcReportsJsonString;
               String statsJsonString = "WebRTC getStats() reports in json format: " + new JSONObject(webrtcReportsJsonString).toString(3);
               //RCLogger.i(TAG, "Stats: " + statsJsonString);

               // Logcat enforces a max size to logged messages, so to avoid getting truncated logs, let's break
               // the json reports that tend to be huge in 1000-byte chunks
               final int CHUNK_SIZE = 1000;
               for (int i = 0; i <= statsJsonString.length() / CHUNK_SIZE; i++) {
                  int start = i * CHUNK_SIZE;
                  int end = (i + 1) * CHUNK_SIZE;
                  end = end > statsJsonString.length() ? statsJsonString.length() : end;

                  RCLogger.i(TAG, statsJsonString.substring(start, end));
               }
            } catch (JSONException e) {
               e.printStackTrace();
            }

            handleDisconnect(null);
         }
      };
      mainHandler.post(myRunnable);
   }

   @Override
   public void onPeerConnectionError(final String description)
   {
      final RCConnection connection = this;
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.e(TAG, "PeerConnection error: " + description);
            String reason = null;
            if (description.equals("ICE connection failed")) {
               // in cases where this is the result of IceConnectionState.FAILED, which means that media connectivity is lost we need to add proper reason header
               reason = "Connectivity-Drop";
            }
            handleDisconnect(reason);

            if (device.isAttached() && connection.listener != null) {
               connection.listener.onError(connection, RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_PEERCONNECTION_ERROR.ordinal(), description);
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDisconnected()");
            }

            // Phone state Intents to capture dropped call event
            sendQoSDisconnectErrorIntent(RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_PEERCONNECTION_ERROR.ordinal(), description);
         }
      };
      mainHandler.post(myRunnable);
   }

   public void onLocalVideo()
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onLocalVideo");
            localVideoReceived = true;
            updateVideoView(VideoViewState.LOCAL_VIEW_RECEIVED);
            if (device.isAttached()) {
               listener.onLocalVideo(RCConnection.this);
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onLocalVideo()");
            }

         }
      };
      mainHandler.post(myRunnable);
   }

   public void onRemoteVideo()
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onRemoteVideo");
            remoteVideoReceived = true;
            updateVideoView(VideoViewState.REMOTE_VIEW_RECEIVED);
            if (device.isAttached()) {
               listener.onRemoteVideo(RCConnection.this);
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onRemoteVideo()");
            }

         }
      };
      mainHandler.post(myRunnable);
   }

   // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
   // All callbacks are invoked from websocket signaling looper thread and
   // are routed to UI thread.
   //@Override
   private void onConnectedToRoom(final SignalingParameters params)
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            onConnectedToRoomInternal(params);
         }
      };
      mainHandler.post(myRunnable);
      // Phone state Intents to capture dialing or answering event
      if (signalingParameters.initiator)
         sendQoSConnectionIntent("dialing");
      else
         sendQoSConnectionIntent("answering");
   }

   private boolean useCamera2() {
      // TODO: uncomment when we want to support Camera2 API, notice that we need API LEVEL >= 21. Also maybe consider if we should expose option in UI like AppRTCMobile then
      // In any case need to understand what Camera2 brings.
      //return Camera2Enumerator.isSupported(device.getApplicationContext()) && getIntent().getBooleanExtra(EXTRA_CAMERA2, true);
      return false;
   }

   private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
      final String[] deviceNames = enumerator.getDeviceNames();

      // First, try to find front facing camera
      Logging.d(TAG, "Looking for front facing cameras.");
      for (String deviceName : deviceNames) {
         if (enumerator.isFrontFacing(deviceName)) {
            Logging.d(TAG, "Creating front facing camera capturer.");
            VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

            if (videoCapturer != null) {
               return videoCapturer;
            }
         }
      }

      // Front facing camera not found, try something else
      Logging.d(TAG, "Looking for other cameras.");
      for (String deviceName : deviceNames) {
         if (!enumerator.isFrontFacing(deviceName)) {
            Logging.d(TAG, "Creating other camera capturer.");
            VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

            if (videoCapturer != null) {
               return videoCapturer;
            }
         }
      }

      return null;
   }

   private VideoCapturer createVideoCapturer() {
      VideoCapturer videoCapturer = null;
      // TODO: uncomment if we want to enable streaming from file instead of camera
      //String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
      String videoFileAsCamera = null;
      if (videoFileAsCamera != null) {
         try {
            videoCapturer = new FileVideoCapturer(videoFileAsCamera);
         } catch (IOException e) {
            // TODO: uncomment if we want to enable streaming from file instead of camera
            //reportError("Failed to open video file for emulated camera");
            return null;
         }
      }
      /* TODO: uncomment when we want to support screen capture + sharing
      else if (screencaptureEnabled) {
         return createScreenCapturer();
      }
      else if (useCamera2()) {
         if (!captureToTexture()) {
            reportError(getString(R.string.camera2_texture_only_error));
            return null;
         }

         Logging.d(TAG, "Creating capturer using camera2 API.");
         videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
      }
      */
      else {
         Logging.d(TAG, "Creating capturer using camera1 API.");
         // TODO: uncomment if we decide to expose 'capture to texture' setting to UI
         //videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
         videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
      }
      if (videoCapturer == null) {
         //reportError("Failed to open camera");
         handleDisconnect(null);
         return null;
      }
      return videoCapturer;
   }

   private void onConnectedToRoomInternal(final SignalingParameters params)
   {
      RCLogger.i(TAG, "onConnectedToRoomInternal");
      final long delta = System.currentTimeMillis() - callStartedTimeMs;

      signalingParameters = params;
      if (peerConnectionClient == null) {
         RCLogger.w(TAG, "Room is connected, but EGL context is not ready yet.");
         return;
      }

      VideoCapturer videoCapturer = null;
      if (peerConnectionParameters.videoCallEnabled) {
         videoCapturer = createVideoCapturer();
      }

      logAndToast("Creating peer connection, delay=" + delta + "ms");
      peerConnectionClient.createPeerConnection(localRender, remoteRender, videoCapturer, signalingParameters);

      if (signalingParameters.initiator) {
         logAndToast("Creating OFFER...");
         // Create offer. Offer SDP will be sent to answering client in
         // PeerConnectionEvents.onLocalDescription event.
         peerConnectionClient.createOffer();
      }
      else {
         if (params.offerSdp != null) {
            peerConnectionClient.setRemoteDescription(params.offerSdp);
            logAndToast("Creating ANSWER...");
            // Create answer. Answer SDP will be sent to offering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createAnswer();
         }
         if (params.iceCandidates != null) {
            // Add remote ICE candidates from room.
            for (IceCandidate iceCandidate : params.iceCandidates) {
               peerConnectionClient.addRemoteIceCandidate(iceCandidate);
            }
         }
      }
   }

   private void onRemoteDescription(String sdpString)
   {
      onRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, sdpString));
   }

   //@Override
   private void onRemoteDescription(final SessionDescription sdp)
   {
      final long delta = System.currentTimeMillis() - callStartedTimeMs;
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onRemoteDescription");
            if (peerConnectionClient == null) {
               RCLogger.e(TAG, "Received remote SDP for non-initilized peer connection.");
               return;
            }
            logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
            SignalingParameters params = SignalingParameters.extractCandidates(sdp);
            peerConnectionClient.setRemoteDescription(params.offerSdp);
            onRemoteIceCandidates(params.iceCandidates);

            if (!signalingParameters.initiator) {
               logAndToast("Creating ANSWER...");
               // Create answer. Answer SDP will be sent to offering client in
               // PeerConnectionEvents.onLocalDescription event.
               peerConnectionClient.createAnswer();
            }
         }
      };
      mainHandler.post(myRunnable);
   }

   //@Override
   private void onRemoteIceCandidates(final List<IceCandidate> candidates)
   {
      RCLogger.i(TAG, "onRemoteIceCandidates");
      // no need to run it in UI thread it is already there due to onRemoteDescription
      if (peerConnectionClient == null) {
         RCLogger.e(TAG,
               "Received ICE candidates for non-initilized peer connection.");
         return;
      }
      for (IceCandidate candidate : candidates) {
         peerConnectionClient.addRemoteIceCandidate(candidate);
      }
   }

   // Helpers
   // get from SDP if this is an audio or audio/video call
   static ConnectionMediaType sdp2Mediatype(String sdp)
   {
      boolean foundVideo = false;

      // split the media SDP sections
      String[] sections = sdp.split("m=");
      for (int i = 0; i < sections.length; i++) {
         // and check if the media secion starts with 'video'
         if (sections[i].matches("(?s)^video.*")) {
            // if so checks if the video section has recvonly
            if (sections[i].matches("(?s).*a=recvonly.*")) {
               return ConnectionMediaType.AUDIO;
            }
            else if (sections[i].matches("(?s).*video 0.*")) {
               return ConnectionMediaType.AUDIO;
            }
            foundVideo = true;
         }
      }

      if (!foundVideo) {
         return ConnectionMediaType.AUDIO;
      }
      else {
         return ConnectionMediaType.AUDIO_VIDEO;
      }

      // if there is a video line AND the port value is different than 0 (hence 1-9 in the first digit) then we have video
        /* Let's keep this around commented in case Firefox changes how it works
        if (sdp.matches("(?s).*m=video [1-9].*")) {
            return ConnectionMediaType.AUDIO_VIDEO;
        }
        else {
            return ConnectionMediaType.AUDIO;
        }
        */
   }

   // -- Notify QoS module of Connection related events through intents, if the module is available
   // Phone state Intents to capture events
   private void sendQoSConnectionIntent(String state)
   {
      SignalingParameters params = this.signalingParameters;
      Intent intent = new Intent("org.restcomm.android.CALL_STATE");

      intent.putExtra("STATE", state);
      intent.putExtra("INCOMING", this.isIncoming());
      if (params != null) {
         intent.putExtra("VIDEO", params.videoEnabled);
         intent.putExtra("REQUEST", params.sipUrl);
      }
      if (this.getState() != null) {
         intent.putExtra("CONNECTIONSTATE", this.getState().toString());
      }

      // if state is connected check if we have Call-Sid custom header and is so send over to android-qos
      if (state.equals("connected") && callParams.containsKey(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS)) {
         HashMap<String, String> customHeaders = (HashMap<String, String>) callParams.get(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS);
         if (customHeaders.containsKey(ParameterKeys.CONNECTION_SIP_HEADER_KEY_CALL_SID)) {
            intent.putExtra("CALLSID", customHeaders.get(ParameterKeys.CONNECTION_SIP_HEADER_KEY_CALL_SID));
         }
      }

      try {
         // Restrict the Intent to MMC Handler running within the same application
         Class aclass = Class.forName("org.restcomm.app.qoslib.Services.Intents.IntentHandler");
         intent.setClass(device.getApplicationContext(), aclass);
         device.sendBroadcast(intent);
      }
      catch (ClassNotFoundException e) {
         // If the MMC class isn't here, no intent
      }
   }

   // Phone state Intents to capture dropped call event with reason
   private void sendQoSDisconnectErrorIntent(int error, String errorText)
   {
      Intent intent = new Intent("org.restcomm.android.DISCONNECT_ERROR");
      intent.putExtra("STATE", "disconnect error");
      if (errorText != null) {
         intent.putExtra("ERRORTEXT", errorText);
      }
      intent.putExtra("ERROR", error);
      intent.putExtra("INCOMING", this.isIncoming());

      try {
         // Restrict the Intent to MMC Handler running within the same application
         Class aclass = Class.forName("org.restcomm.app.qoslib.Services.Intents.IntentHandler");
         intent.setClass(device.getApplicationContext(), aclass);
         device.sendBroadcast(intent);
      }
      catch (ClassNotFoundException e) {
         // If the MMC class isn't here, no intent
      }
   }

   public static Map<String, String> createIceServerHashMap(String url, String username, String password)
   {
      HashMap<String, String> map = new HashMap<>();
      map.put(RCConnection.IceServersKeys.ICE_SERVER_URL, url);
      map.put(RCConnection.IceServersKeys.ICE_SERVER_USERNAME, username);
      map.put(RCConnection.IceServersKeys.ICE_SERVER_PASSWORD, password);

      return map;
   }

}
