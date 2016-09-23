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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.restcomm.android.sdk.MediaClient.AppRTCAudioManager;
import org.restcomm.android.sdk.MediaClient.PeerConnectionClient;
import org.restcomm.android.sdk.SignalingClient.SignalingParameters;
import org.restcomm.android.sdk.SignalingClient.SignalingClient;
import org.restcomm.android.sdk.MediaClient.util.IceServerFetcher;

import org.restcomm.android.sdk.util.PercentFrameLayout;
import org.restcomm.android.sdk.util.RCLogger;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.VideoTrack;

/**
 * RCConnection represents a call. An RCConnection can be either incoming or outgoing. RCConnections are not created by themselves but
 * as a result on an action on RCDevice. For example to initiate an outgoing connection you call RCDevice.connect() which instantiates
 * and returns a new RCConnection. On the other hand when an incoming connection arrives the RCDevice delegate is notified with
 * RCDeviceListener.onIncomingConnection() and passes the new RCConnection object that is used by the delegate to
 * control the connection.
 * <p/>
 * When an incoming connection arrives through RCDeviceListener.onIncomingConnection() it is considered RCConnectionStateConnecting until it is either
 * accepted with RCConnection.accept() or rejected with RCConnection.reject(). Once the connection is accepted the RCConnection transitions to RCConnectionStateConnected
 * state.
 * <p/>
 * When an outgoing connection is created with RCDevice.connect() it starts with state RCConnectionStatePending. Once it starts ringing on the remote party it
 * transitions to RCConnectionStateConnecting. When the remote party answers it, the RCConnection state transitions to RCConnectionStateConnected.
 * <p/>
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
       * A Connection enters this state when actual media starts flowing
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
       * We don't know the type of media yet, for example for remote video before they answer
       */
      AUDIO, /**
       * Connection is audio only
       */
      AUDIO_VIDEO, /** Connection audio & video */
   }

   String IncomingParameterFromKey = "RCConnectionIncomingParameterFromKey";
   String IncomingParameterToKey = "RCConnectionIncomingParameterToKey";
   String IncomingParameterAccountSIDKey = "RCConnectionIncomingParameterAccountSIDKey";
   String IncomingParameterAPIVersionKey = "RCConnectionIncomingParameterAPIVersionKey";
   String IncomingParameterCallSIDKey = "RCConnectionIncomingParameterCallSIDKey";

   /**
    * State of the connection. For more info please check ConnectionState
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
    * Direction of the connection. True if connection is incoming; false otherwise
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
    * Parameter keys for RCCDevice.connect() and RCConnection.accept()
    */
   public static class ParameterKeys {
      public static final String CONNECTION_PEER = "username";
      public static final String CONNECTION_VIDEO_ENABLED = "video-enabled";
      public static final String CONNECTION_LOCAL_VIDEO = "local-video";
      public static final String CONNECTION_REMOTE_VIDEO = "remote-video";
      public static final String CONNECTION_PREFERRED_VIDEO_CODEC = "preferred-video-codec";
      public static final String CONNECTION_CUSTOM_SIP_HEADERS = "sip-headers";
      public static final String CONNECTION_CUSTOM_INCOMING_SIP_HEADERS = "sip-headers-incoming";
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
   private EglBase rootEglBase;
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
   // Has user muted video? Notice the distinction between user having muted video and video being actually muted in PeerConnection level. For example when a call is paused via pauseVideo(), then
   // automatically video will be muted in PeerConnection level, but user hasn't actually muted it. We need that state to properly handle transitions
   private boolean videoExpectedMuted;
   private long callStartedTimeMs = 0;
   private final boolean DO_TOAST = false;
   // if a call takes too long to establish this handler is used to emit a time out
   private Handler timeoutHandler = null;
   // call times out if it hasn't been established after 15 seconds
   private final int CALL_TIMEOUT_DURATION_MILIS = 15 * 1000;


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
      timeoutHandler = new Handler(device.getMainLooper());
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
    */
   public ConnectionState getState()
   {
      return this.state;
   }

   /**
    * Retrieves the current local media type of the connection
    */
   public ConnectionMediaType getLocalMediaType()
   {
      return this.localMediaType;
   }

   /**
    * Retrieves the current local media type of the connection
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

   // Make a call using the passed parameters
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
    *   <b>RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED</b>: Whether we want WebRTC video enabled or not <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO</b>: View where we want the local video to be rendered <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO</b>: View where we want the remote video to be rendered  <br>
    *   <b>RCConnection.ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC</b>: Preferred video codec to use. Default is VP8. Possible values: <i>'VP8', 'VP9'</i> <br>
    * means that RCDevice.state not ready to make a call (this usually means no WiFi available)
    */
   public void accept(Map<String, Object> parameters)
   {
      RCLogger.i(TAG, "accept(): " + parameters.toString());
      if (!checkPermissions((Boolean)parameters.get(ParameterKeys.CONNECTION_VIDEO_ENABLED))) {
         return;
      }

      if (state == ConnectionState.CONNECTING) {
         this.callParams = (HashMap<String, Object>) parameters;
         // Especially, for incoming connections the peer DID is provided when the connection arrives in RCDevice and at that point RCConnection.peer is populated
         //this.callParams.put(ParameterKeys.CONNECTION_PEER, this.peer);
         initializeWebrtc((Boolean) this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED),
               (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_LOCAL_VIDEO),
               (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_REMOTE_VIDEO),
               (String) parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC));

         startTurn();
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
         audioManager.stop();
         signalingClient.disconnect(jobId, null);

         // TODO: (minor) if reject() is called while we are already connected then we will disconnect, but in that
         // edge case we shouldn't set connection state to DICONNECTED right away

         // update state right away since rejecting a call is a response to the INVITE, so no further messages will come
         this.state = ConnectionState.DISCONNECTED;

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

      handleDisconnect(null);
   }

   /**
    * Mute connection so that the other party cannot hear local audio
    *
    * @param muted True to mute and false in order to unmute
    */
   public void setAudioMuted(boolean muted)
   {
      RCLogger.i(TAG, "setAudioMuted(): " + muted);

      if (audioManager != null) {
         audioManager.setMute(muted);
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
      if (audioManager != null) {
         return audioManager.getMute();
      }
      else {
         RCLogger.e(TAG, "isMuted called on null audioManager -check memory management");
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
      videoExpectedMuted = muted;
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
      if (customHeaders != null) {
         callParams.put(ParameterKeys.CONNECTION_CUSTOM_INCOMING_SIP_HEADERS, customHeaders);
      }
      // we want to notify webrtc onRemoteDescription *only* on an outgoing call
      if (!this.isIncoming()) {
         remoteMediaType = sdp2Mediatype(sdpAnswer);
         onRemoteDescription(sdpAnswer);
      }
      sendQoSConnectionIntent("connected");
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

   public void onCallErrorEvent(String jobId, RCClient.ErrorCodes errorCode, String errorText)
   {
      RCLogger.e(TAG, "onCallErrorEvent(): jobId: " + jobId + ", error code: " + errorCode + ", error text: " + errorText);

      // TODO: we need to see if there's a chance a call that causes an error to remain up,
      // if not we need to avoid disconnecting below
      if (state != ConnectionState.DISCONNECTING) {
         // only disconnect signaling facilities if we are not already disconnecting
         signalingClient.disconnect(jobId, null);
      }
      disconnectWebrtc();

      if (RCDevice.state == RCDevice.DeviceState.BUSY) {
         RCDevice.state = RCDevice.DeviceState.READY;
      }

      this.state = ConnectionState.DISCONNECTED;
      device.removeConnection(jobId);

      if (device.isAttached() && listener != null) {
         listener.onDisconnected(this, errorCode.ordinal(), errorText);
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
      }
      else if (state == ConnectionState.DISCONNECTING) {
         RCLogger.w(TAG, "disconnect(): Attempting to disconnect while we are in state disconnecting, skipping.");
      }
      else {
         // let's delay a millisecond to avoid calling code in the App getting intertwined with App listener code
         new Handler(device.getMainLooper()).postDelayed(
               new Runnable() {
                  @Override
                  public void run()
                  {
                     if (device.isAttached()) {
                        listener.onError(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE.ordinal(),
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE));
                     }
                     else {
                        RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onError(): " +
                              RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_WRONG_STATE));
                     }

                  }
               }
               , 1);
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
      // Important: need to fire the event in UI context to make sure no races will arise
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            if (!RCConnection.this.incoming) {
               // we are the initiator

               // create a new hash map
               HashMap<String, String> sipHeaders = null;
               if (RCConnection.this.callParams.containsKey(ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS)) {
                  sipHeaders = (HashMap<String, String>) RCConnection.this.callParams.get(ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS);
               }

               RCConnection.this.signalingParameters = new SignalingParameters(iceServers, true, "", peer,
                     "", null, null, sipHeaders, (Boolean) RCConnection.this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED));
            }
            else {
               // we are not the initiator
               RCConnection.this.signalingParameters = new SignalingParameters(iceServers, false, "", "", "", null, null, null,
                     (Boolean) RCConnection.this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED));
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
               RCConnection.this.listener.onDisconnected(RCConnection.this, RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_TURN_ERROR.ordinal(), description);
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
      if (!checkPermissions((Boolean)parameters.get(ParameterKeys.CONNECTION_VIDEO_ENABLED))) {
         return;
      }

      this.callParams = (HashMap<String, Object>) parameters;
      initializeWebrtc((Boolean) this.callParams.get(ParameterKeys.CONNECTION_VIDEO_ENABLED),
            (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_LOCAL_VIDEO),
            (PercentFrameLayout) parameters.get(ParameterKeys.CONNECTION_REMOTE_VIDEO),
            (String) parameters.get(ParameterKeys.CONNECTION_PREFERRED_VIDEO_CODEC));

      startTurn();
   }

   private void startTurn()
   {
      HashMap<String, Object> deviceParameters = device.getParameters();
      String url = deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_URL) + "?ident=" +
            deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME) + "&secret=" +
            deviceParameters.get(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD) + "&domain=cloud.restcomm.com&application=default&room=default&secure=1";

      boolean turnEnabled = false;
      if (deviceParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED) &&
            !deviceParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED).equals("")) {
         turnEnabled = true;
      }

      //String url = "https://service.xirsys.com/ice?ident=atsakiridis&secret=4e89a09e-bf6f-11e5-a15c-69ffdcc2b8a7&domain=cloud.restcomm.com&application=default&room=default&secure=1";
      new IceServerFetcher(url, turnEnabled, this).makeRequest();

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
      // This situation can be legit, as long as we not SIGNALING_CONNECTED. If we are, then it means that signaling is established but
      // media takes way too long
      if (state == ConnectionState.SIGNALING_CONNECTED) {
         RCLogger.e(TAG, "onCallTimeout(): State: " + state + ", after: " + CALL_TIMEOUT_DURATION_MILIS);

         String reason = "Call-Timeout-Media";
         RCClient.ErrorCodes errorCode = RCClient.ErrorCodes.ERROR_CONNECTION_MEDIA_TIMEOUT;

         handleDisconnect(reason);

         if (device.isAttached() && listener != null) {
            this.listener.onDisconnected(this, errorCode.ordinal(), RCClient.errorText(errorCode));
         }
         else {
            RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onDisconnected()");
         }

         // Phone state Intents to capture dropped call event
         sendQoSDisconnectErrorIntent(errorCode.ordinal(), RCClient.errorText(errorCode));
      }
   }

   /*
   // DEBUG (Issue #380)
   public void off()
   {
      peerConnectionClient.off();
   }
   // DEBUG
   public void on(final PercentFrameLayout localRenderLayout, final PercentFrameLayout remoteRenderLayout)
   {
      initializeVideo(true, localRenderLayout, remoteRenderLayout);
      peerConnectionClient.on((SurfaceViewRenderer)localRenderLayout.getChildAt(0), (SurfaceViewRenderer)remoteRenderLayout.getChildAt(0));
   }
   */

   // Pause webrtc video, intented for allowing a call to transition to the background where we only want audio enabled
   public void pauseVideo()
   {
      handleVideoMuted(true);
      peerConnectionClient.stopVideoSource();
   }
   // Resume webrtc video, intented for allowing a call to transition from the background into the foreground where we want video enabled (it it was enabled to start with)
   public void resumeVideo()
   {
      peerConnectionClient.startVideoSource();
      setVideoMuted(videoExpectedMuted);
   }

   /*
   // TODO: Issue #380: We should uncomment this once we figure out https://groups.google.com/forum/#!searchin/discuss-webrtc/tsakiridis$20android%7Csort:relevance/discuss-webrtc/XE2Ok67B1Ks/RrqmfZh9AQAJ
   // Implementation above is meant to be a temporary solution, as it doesn't allow for the call Activity to be destroyed and then re-created
   public void pauseVideo()
   {
      // TODO: handle case of audio only call
      localRender.setVisibility(View.INVISIBLE);
      remoteRender.setVisibility(View.INVISIBLE);
      peerConnectionClient.pauseVideo();
   }
   public void resumeVideo(final PercentFrameLayout localRenderLayout, final PercentFrameLayout remoteRenderLayout)
   {
      boolean videoEnabled = false;
      if ((isIncoming() && getRemoteMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO) ||
              (!isIncoming() && getLocalMediaType() == RCConnection.ConnectionMediaType.AUDIO_VIDEO))  {
         videoEnabled = true;
      }

      initializeVideo(videoEnabled, localRenderLayout, remoteRenderLayout);
      peerConnectionClient.resumeVideo(localRender, remoteRender);
   }
   */

   // Callback fired when video is paused after call to pauseVideo()
   // IMPORTANT: runs in media thread, need to post on Main thread
   public void onVideoPaused()
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onVideoPaused");
            releaseVideo();
         }
      };
      mainHandler.post(myRunnable);
   }

   // Callback fired when video is resumed after call to resumeVideo()
   // IMPORTANT: runs in media thread, need to post on Main thread
   public void onVideoResumed()
   {
      Handler mainHandler = new Handler(device.getMainLooper());
      Runnable myRunnable = new Runnable() {
         @Override
         public void run()
         {
            RCLogger.i(TAG, "onVideoResumed");
            updateVideoView(VideoViewState.ICE_CONNECTED);

            /*
            remoteRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
            remoteRender.setScalingType(scalingType);
            remoteRender.setMirror(false);
            remoteRender.setVisibility(View.VISIBLE);

            if (localRender.getVisibility() != View.VISIBLE) {
               localRender.setVisibility(View.VISIBLE);
            }
            localRenderLayout.setPosition(
                  LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
            localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
            localRender.setMirror(true);

            localRender.requestLayout();
            remoteRender.requestLayout();
            */
         }
      };
      mainHandler.post(myRunnable);

   }

   private void initializeVideo(boolean videoEnabled, PercentFrameLayout localRenderLayout, PercentFrameLayout remoteRenderLayout)
   {
      scalingType = ScalingType.SCALE_ASPECT_FILL;

      this.localRenderLayout = localRenderLayout;
      this.remoteRenderLayout = remoteRenderLayout;

      rootEglBase = EglBase.create();
      localRender = (SurfaceViewRenderer)localRenderLayout.getChildAt(0);
      remoteRender = (SurfaceViewRenderer)remoteRenderLayout.getChildAt(0);

      if (videoEnabled) {
         localMediaType = ConnectionMediaType.AUDIO_VIDEO;
      }
      else {
         localMediaType = ConnectionMediaType.AUDIO;
      }

      localRender.init(rootEglBase.getEglBaseContext(), null);
      localRender.setZOrderMediaOverlay(true);
      remoteRender.init(rootEglBase.getEglBaseContext(), null);
      updateVideoView(VideoViewState.NONE);
   }

   // initialize webrtc facilities for the call
   private void initializeWebrtc(boolean videoEnabled, PercentFrameLayout localRenderLayout, PercentFrameLayout remoteRenderLayout, String preferredVideoCodec)
   {
      RCLogger.i(TAG, "initializeWebrtc  ");
      //Context context = RCClient.getContext();

      iceConnected = false;
      signalingParameters = null;

      initializeVideo(videoEnabled, localRenderLayout, remoteRenderLayout);

      // default to VP8 as VP9 doesn't seem to have that great android device support
      if (preferredVideoCodec == null) {
         preferredVideoCodec = "VP8";
      }

      peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
            videoEnabled,  // video call
            false,  // loopback
            false,  // tracing
            0,  // video width
            0,  // video height
            0,  // video fps
            0,  // video start bitrate
            preferredVideoCodec,  // video codec
            true,  // video condec hw acceleration
            false, // capture to texture
            0,  // audio start bitrate
            "OPUS",  // audio codec
            false,  // no audio processing
            false,  // aec dump
            false);  // use opengles

      createPeerConnectionFactory();
   }

   private void updateVideoView(VideoViewState state)
   {
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

      //audioManager.startCall();

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

      if (rootEglBase != null) {
         rootEglBase.release();
         rootEglBase = null;
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
            if (peerConnectionClient == null) {
               final long delta = System.currentTimeMillis() - callStartedTimeMs;
               RCLogger.d(TAG, "Creating peer connection factory, delay=" + delta + "ms");
               peerConnectionClient = PeerConnectionClient.getInstance();
               peerConnectionClient.createPeerConnectionFactory(device,
                     peerConnectionParameters,
                     connection);
               logAndToast("Created PeerConnectionFactory");
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
            RCLogger.i(TAG, "onLocalDescription");
            if (signalingParameters != null) {  // && !signalingParameters.sipUrl.isEmpty()) {
               logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
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
            RCLogger.i(TAG, "onIceCandidate:");
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
            }
            else {
               HashMap<String, Object> parameters = new HashMap<>();
               parameters.put("sdp", connection.signalingParameters.generateSipSdp(connection.signalingParameters.answerSdp,
                     connection.signalingParameters.iceCandidates));
               signalingClient.accept(jobId, parameters);
               //connection.state = ConnectionState.CONNECTING;
            }
         }
      };
      mainHandler.post(myRunnable);
   }

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
            if (device.isAttached()) {
               device.onNotificationCallConnected(RCConnection.this);
               listener.onConnected(RCConnection.this, customHeaders);
            }
            else {
               RCLogger.w(TAG, "RCConnectionListener event suppressed since Restcomm Client Service not attached: onConnected()");
            }

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
            if (iceConnected) {
               //hudFragment.updateEncoderStatistics(reports);
            }
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
               connection.listener.onDisconnected(connection, RCClient.ErrorCodes.ERROR_CONNECTION_WEBRTC_PEERCONNECTION_ERROR.ordinal(), description);
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

   public void onLocalVideo(VideoTrack videoTrack)
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

   public void onRemoteVideo(VideoTrack videoTrack)
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

   private void onConnectedToRoomInternal(final SignalingParameters params)
   {
      RCLogger.i(TAG, "onConnectedToRoomInternal");
      final long delta = System.currentTimeMillis() - callStartedTimeMs;

      signalingParameters = params;
      if (peerConnectionClient == null) {
         RCLogger.w(TAG, "Room is connected, but EGL context is not ready yet.");
         return;
      }

      logAndToast("Creating peer connection, delay=" + delta + "ms");
      peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
            localRender, remoteRender, signalingParameters);

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
      if (this.getState() != null)
         intent.putExtra("CONNECTIONSTATE", this.getState().toString());

      try {
         // Restrict the Intent to MMC Handler running within the same application
         Class aclass = Class.forName("com.cortxt.app.corelib.Services.Intents.IntentHandler");
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
      if (errorText != null)
         intent.putExtra("ERRORTEXT", errorText);
      intent.putExtra("ERROR", error);
      intent.putExtra("INCOMING", this.isIncoming());

      try {
         // Restrict the Intent to MMC Handler running within the same application
         Class aclass = Class.forName("com.cortxt.app.corelib.Services.Intents.IntentHandler");
         intent.setClass(device.getApplicationContext(), aclass);
         device.sendBroadcast(intent);
      }
      catch (ClassNotFoundException e) {
         // If the MMC class isn't here, no intent
      }
   }
}
