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

package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mobicents.restcomm.android.sipua.SipUAConnectionListener;
import org.mobicents.restcomm.android.sipua.impl.DeviceImpl;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoTrack;

/**
 *  RCConnection represents a call. An RCConnection can be either incoming or outgoing. RCConnections are not created by themselves but
 *  as a result on an action on RCDevice. For example to initiate an outgoing connection you call RCDevice.connect() which instantiates
 *  and returns a new RCConnection. On the other hand when an incoming connection arrives the RCDevice delegate is notified with
 *  RCDeviceListener.onIncomingConnection() and passes the new RCConnection object that is used by the delegate to
 *  control the connection.
 *
 *  When an incoming connection arrives through RCDeviceListener.onIncomingConnection() it is considered RCConnectionStateConnecting until it is either
 *  accepted with RCConnection.accept() or rejected with RCConnection.reject(). Once the connection is accepted the RCConnection transitions to RCConnectionStateConnected
 *  state.
 *
 *  When an outgoing connection is created with RCDevice.connect() it starts with state RCConnectionStatePending. Once it starts ringing on the remote party it
 *  transitions to RCConnectionStateConnecting. When the remote party answers it, the RCConnection state transitions to RCConnectionStateConnected.
 *
 *  Once an RCConnection (either incoming or outgoing) is established (i.e. RCConnectionStateConnected) media can start flowing over it. DTMF digits can be sent over to
 *  the remote party using RCConnection.sendDigits() (<b>Not implemented yet</b>). When done with the RCConnection you can disconnect it with RCConnection.disconnect().
 */
public class RCConnection implements SipUAConnectionListener, PeerConnectionClient.PeerConnectionEvents {
    /**
     * Connection State
     */
    public enum ConnectionState {
        PENDING,  /** Connection is in state pending */
        CONNECTING,  /** Connection is in state connecting */
        CONNECTED,  /** Connection is in state connected */
        DISCONNECTED,  /** Connection is in state disconnected */
    };

    String IncomingParameterFromKey = "RCConnectionIncomingParameterFromKey";
    String IncomingParameterToKey = "RCConnectionIncomingParameterToKey";
    String IncomingParameterAccountSIDKey ="RCConnectionIncomingParameterAccountSIDKey";
    String IncomingParameterAPIVersionKey = "RCConnectionIncomingParameterAPIVersionKey";
    String IncomingParameterCallSIDKey = "RCConnectionIncomingParameterCallSIDKey";

    /**
     *  @abstract State of the connection.
     *
     *  @discussion A new connection created by RCDevice starts off RCConnectionStatePending. It transitions to RCConnectionStateConnecting when it starts ringing. Once the remote party answers it it transitions to RCConnectionStateConnected. Finally, when disconnected it resets to RCConnectionStateDisconnected.
     */
    ConnectionState state;

    /**
     *  @abstract Direction of the connection. True if connection is incoming; false otherwise
     */
    boolean incoming;

   /**
     *  @abstract Connection parameters (**Not Implemented yet**)
     */
    HashMap<String, String> parameters;

    /**
     *  @abstract Listener that will be called on RCConnection events described at RCConnectionListener
     */
    RCConnectionListener listener;

    /**
     *  @abstract Is connection currently muted? If a connection is muted the remote party cannot hear the local party
     */
    boolean muted;

    public String incomingCallSdp = "";
    private PeerConnectionClient peerConnectionClient = null;
    private SignalingParameters signalingParameters;
    private AppRTCAudioManager audioManager = null;
    private VideoRenderer.Callbacks localRender = null;
    private VideoRenderer.Callbacks remoteRender = null;
    private VideoRendererGui.ScalingType scalingType;
    private Toast logToast;
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private long callStartedTimeMs = 0;
    private GLSurfaceView videoView;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET"
    };
    private static final String TAG = "RCConnection";

    /**
     *  Initialize a new RCConnection object. <b>Important</b>: this is used internally by RCDevice and is not meant for application use
     *
     *  @param connectionListener RCConnection listener that will be receiving RCConnection events (@see RCConnectionListener)
     *
     *  @return Newly initialized object
     */
    public RCConnection(RCConnectionListener connectionListener)
    {
        this.listener = connectionListener;
    }

    // could not use the previous constructor with connectionListener = null, hence created this:
    public RCConnection()
    {
        this.listener = null;
    }

    // 'Copy' constructor
    public RCConnection(RCConnection connection)
    {
        this.incoming = connection.incoming;
        this.muted = connection.muted;

        this.state = connection.state;
        // not used yet
        this.parameters = null;  //new HashMap<String, String>(connection.parameters);
        this.listener = connection.listener;
    }

    /**
     * Retrieves the current state of the connection
     */
    public ConnectionState getState()
    {
        return this.state;
    }

    /**
     * Retrieves the set of application parameters associated with this connection <b>Not Implemented yet</b>
     * @return Connection parameters
     */
    public Map<String, String> getParameters()
    {
        return parameters;
    }

    /**
     * Returns whether the connection is incoming or outgoing
     * @return True if incoming, false otherwise
     */
    public boolean isIncoming()
    {
        return this.incoming;
    }

    /**
     * Accept the incoming connection
     */
    public void accept(Map<String, Object> parameters)
    {
        if (haveConnectivity()) {
            //  DeviceImpl.GetInstance().Accept(
            Boolean enableVideo = (Boolean)parameters.get("video-enabled");
            initializeWebrtc(enableVideo.booleanValue());

            LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
            iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302", "", ""));
            this.signalingParameters = new SignalingParameters(iceServers, false, "", "", "", null, null, null, enableVideo.booleanValue());
            SignalingParameters params = SignalingParameters.extractCandidates(new SessionDescription(SessionDescription.Type.OFFER, incomingCallSdp));
            this.signalingParameters.offerSdp = params.offerSdp;
            this.signalingParameters.iceCandidates = params.iceCandidates;
            startCall(this.signalingParameters);
        }
    }

    private void acceptWebrtc(final String sdp)
    {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().AcceptWebrtc(sdp);
            this.state = state.CONNECTED;
        }
    }

    /**
     * Ignore incoming connection (<b>Not Implemented yet</b>)
     */
    public void ignore()
    {

    }

    /**
     * Reject incoming connection
     */
    public void reject()
    {
        if (haveConnectivity()) {
            DeviceImpl.GetInstance().Reject();
            this.state = state.DISCONNECTED;

            // also update RCDevice state
            RCDevice device = RCClient.listDevices().get(0);
            if (device.state == RCDevice.DeviceState.BUSY) {
                device.state = RCDevice.DeviceState.READY;
            }
        }
    }

    /**
     * Disconnect the established connection
     */
    public void disconnect()
    {
        if (haveConnectivity()) {
            if (state == ConnectionState.CONNECTING) {
                DeviceImpl.GetInstance().Cancel();
            } else if (state == ConnectionState.CONNECTED) {
                DeviceImpl.GetInstance().Hangup();
            }
        }
        // also update RCDevice state
        RCDevice device = RCClient.listDevices().get(0);
        if (device.state == RCDevice.DeviceState.BUSY) {
            device.state = RCDevice.DeviceState.READY;
        }

        disconnectWebrtc();
    }

    /**
     * Mute connection so that the other party cannot local audio
     * @param muted True to mute and false in order to unmute
     */
    public void setMuted(boolean muted)
    {
        if (audioManager != null) {
            audioManager.setMute(muted);
        }
    }

    /**
     * Retrieve whether connection is muted or not
     * @return True connection is muted and false otherwise
     */
    public boolean isMuted()
    {
        if (audioManager != null) {
            return audioManager.getMute();
        }
        else {
            Log.e(TAG, "isMuted called on null audioManager -check memory management");
        }
        return false;
    }

    /**
     * Send DTMF digits over the connection (<b>Not Implemented yet</b>)
     * @param digits A string of DTMF digits to be sent
     */
    public void sendDigits(String digits)
    {

    }

    /**
     * Update connection listener to be receiving Connection related events. This is
     * usually needed when we switch activities and want the new activity to receive
     * events
     * @param listener  New connection listener
     */
    public void setConnectionListener(RCConnectionListener listener)
    {
        this.listener = listener;
        DeviceImpl.GetInstance().sipuaConnectionListener = this;
    }

    // SipUA Connection Listeners
    public void onSipUAConnecting(SipEvent event)
    {
        this.state = ConnectionState.CONNECTING;
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onConnecting(finalConnection);
            }
        };
        mainHandler.post(myRunnable);
        // Phone state Intents to capture connecting event
        sendConnectionIntent("connecting");
    }

    public void onSipUAConnected(SipEvent event)
    {
        this.state = ConnectionState.CONNECTED;
        final RCConnection finalConnection = new RCConnection(this);

        // we want to notify webrtc onRemoteDescription *only* on an outgoing call
        if (!this.isIncoming()) {
            onRemoteDescription(event.sdp);
        }

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                listener.onConnected(finalConnection);
            }
        };
        mainHandler.post(myRunnable);
        // Phone state Intents to capture call connected event
        sendConnectionIntent("connected");
    }

    public void onSipUADisconnected(final SipEvent event)
    {
        // we 're first notifying listener and then setting new state because we want the listener to be able to
        // differentiate between disconnect and remote cancel events with the same listener method: onDisconnected.
        // In the first case listener will see stat CONNECTED and in the second CONNECTING
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // TODO: we need to move this below so that it is only executed on incoming
                // also update RCDevice state
                RCDevice device = RCClient.listDevices().get(0);
                if (event.type == SipEvent.SipEventType.INCOMING_BYE_REQUEST && device.state == RCDevice.DeviceState.BUSY) {
                    // for outgoing disconnect we are handling it in RCConnection.disconnect()
                    disconnectWebrtc();
                    device.state = RCDevice.DeviceState.READY;
                }
                listener.onDisconnected(finalConnection);
            }
        };
        mainHandler.post(myRunnable);

        this.state = ConnectionState.DISCONNECTED;
        // Phone state Intents to capture normal disconnect event
        sendConnectionIntent("disconnected");
    }

    public void onSipUACancelled(SipEvent event)
    {
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // also update RCDevice state
                RCDevice device = RCClient.listDevices().get(0);
                if (device.state == RCDevice.DeviceState.BUSY) {
                    device.state = RCDevice.DeviceState.READY;
                }

                listener.onCancelled(finalConnection);
            }
        };
        mainHandler.post(myRunnable);

        this.state = ConnectionState.DISCONNECTED;
        // Phone state Intents to capture cancelled event
        sendConnectionIntent("cancelled");
    }

    public void onSipUADeclined(SipEvent event)
    {
        final RCConnection finalConnection = new RCConnection(this);

        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                // also update RCDevice state
                RCDevice device = RCClient.listDevices().get(0);
                if (device.state == RCDevice.DeviceState.BUSY) {
                    device.state = RCDevice.DeviceState.READY;
                }
                listener.onDeclined(finalConnection);
            }
        };
        mainHandler.post(myRunnable);

        this.state = ConnectionState.DISCONNECTED;
        // Phone state Intents to capture declined event
        sendConnectionIntent("declined");
    }

    // Helpers
    private boolean haveConnectivity()
    {
        RCDevice device = RCClient.listDevices().get(0);
        if (device == null) {
            return false;
        }

        // get reachability state from RCDevice
        DeviceImpl.ReachabilityState state = device.getReachability();

        if (state == DeviceImpl.ReachabilityState.REACHABILITY_WIFI ||
                state == DeviceImpl.ReachabilityState.REACHABILITY_MOBILE) {
            return true;
        }
        else {
            if (this.listener != null) {
                this.listener.onDisconnected(this, RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal(), RCClient.errorText(RCClient.ErrorCodes.NO_CONNECTIVITY));
            }
            // Phone state Intents to capture dropped call due to no connectivity
            sendDisconnectErrorIntent (RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal(), RCClient.errorText(RCClient.ErrorCodes.NO_CONNECTIVITY));
            return false;
        }
    }

    // -- WebRTC stuff:
    public void setupWebrtcAndCall(String sipUri, HashMap<String, String> sipHeaders, boolean videoEnabled)
    {
        initializeWebrtc(videoEnabled);

        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302", "", ""));
        this.signalingParameters = new SignalingParameters(iceServers, true, "", sipUri, "", null, null, sipHeaders, videoEnabled);

        startCall(this.signalingParameters);
    }

    // initialize webrtc facilities for the call
    void initializeWebrtc(boolean videoEnabled)
    {
        Log.e(TAG, "@@@@@ initializeWebrtc  ");
        Context context = RCClient.getContext();

        iceConnected = false;
        signalingParameters = null;
        scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;

        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                // TODO: return error to RCConnection listener
                return;
            }
        }

        peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
                videoEnabled,
                false,
                0,
                0,
                0,
                0,
                "VP8",
                true,
                0,
                "OPUS",
                false,
                true);

        createPeerConnectionFactory();
    }

    private void startCall(SignalingParameters signalingParameters)
    {
        Log.e(TAG, "@@@@@ startCall");
        callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        logAndToast("Preparing call");

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(RCClient.getContext(), new Runnable() {
                    // This method will be called each time the audio state (number and
                    // type of devices) has been changed.
                    @Override
                    public void run() {
                        onAudioManagerChangedState();
                    }
                }
        );

        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Initializing the audio manager...");
        audioManager.init();

        // we don't have room functionality to notify us when ready; instead, we start connecting right now
        this.onConnectedToRoom(signalingParameters);
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    public void disconnectWebrtc() {
        Log.e(TAG, "@@@@@ disconnectWebrtc");

        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.close();
            audioManager = null;
        }
    }

    private void onAudioManagerChangedState() {
        // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
        // is active.
    }


    // Create peer connection factory when EGL context is ready.
    private void createPeerConnectionFactory() {
        final RCConnection connection = this;
        // Important: need to fire the event in UI context cause currently we 're in JAIN SIP thread
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ createPeerConnectionFactory");
                if (peerConnectionClient == null) {
                    final long delta = System.currentTimeMillis() - callStartedTimeMs;
                    Log.d(TAG, "Creating peer connection factory, delay=" + delta + "ms");
                    peerConnectionClient = PeerConnectionClient.getInstance();
                    peerConnectionClient.createPeerConnectionFactory(RCClient.getContext(),
                            VideoRendererGui.getEGLContext(), peerConnectionParameters,
                            connection);
                    logAndToast("Created PeerConnectionFactory");
                }
                if (signalingParameters != null) {
                    Log.w(TAG, "EGL context is ready after room connection.");
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
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(RCClient.getContext(), msg, Toast.LENGTH_SHORT);
        logToast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
        logToast.show();
    }

    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    @Override
    public void onLocalDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        final RCConnection connection = this;
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onLocalDescription");
                if (signalingParameters != null) {  // && !signalingParameters.sipUrl.isEmpty()) {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
                    if (signalingParameters.initiator) {
                        // keep it around so that we combine it with candidates before sending it over
                        connection.signalingParameters.offerSdp = sdp;
                        //appRtcClient.sendOfferSdp(sdp);
                    } else {
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
    public void onIceCandidate(final IceCandidate candidate) {
        final RCConnection connection = this;
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onIceCandidate:");
                connection.signalingParameters.addIceCandidate(candidate);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onIceGatheringComplete()
    {
        final RCConnection connection = this;

        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onIceGatheringComplete");
                if (peerConnectionClient == null) {
                    // if the user hangs up the call before its setup we need to bail
                    return;
                }
                if (signalingParameters.initiator) {
                    // we have gathered all candidates and SDP. Combine then in SIP SDP and send over to JAIN SIP
                    DeviceImpl.GetInstance().CallWebrtc(signalingParameters.sipUrl,
                            connection.signalingParameters.generateSipSdp(connection.signalingParameters.offerSdp,
                                    connection.signalingParameters.iceCandidates), connection.signalingParameters.sipHeaders);
                }
                else {
                    DeviceImpl.GetInstance().AcceptWebrtc(connection.signalingParameters.generateSipSdp(connection.signalingParameters.answerSdp,
                            connection.signalingParameters.iceCandidates));
                    connection.state = state.CONNECTED;
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onIceConnected");
                logAndToast("ICE connected, delay=" + delta + "ms");
                iceConnected = true;
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onIceDisconnected() {
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onIceDisconnected");
                logAndToast("ICE disconnected");
                iceConnected = false;
                disconnect();
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.e(TAG, "@@@@@ onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isError && iceConnected) {
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
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "PeerConnection error: " + description);
                disconnect();
                if (connection.listener != null) {
                    connection.listener.onDisconnected(connection, RCClient.ErrorCodes.WEBRTC_PEERCONNECTION_ERROR.ordinal(), description);
                }
                // Phone state Intents to capture dropped call event
                sendDisconnectErrorIntent(RCClient.ErrorCodes.WEBRTC_PEERCONNECTION_ERROR.ordinal(), description);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onLocalVideo(VideoTrack videoTrack)
    {
        final VideoTrack finalVideoTrack = videoTrack;
        final RCConnection connection = this;

        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onLocalVideo");
                listener.onReceiveLocalVideo(connection, finalVideoTrack);
            }
        };
        mainHandler.post(myRunnable);
    }

    public void onRemoteVideo(VideoTrack videoTrack)
    {
        final VideoTrack finalVideoTrack = videoTrack;
        final RCConnection connection = this;

        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onRemoteVideo");
                listener.onReceiveRemoteVideo(connection, finalVideoTrack);
            }
        };
        mainHandler.post(myRunnable);
    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.
    //@Override
    public void onConnectedToRoom(final SignalingParameters params) {
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        };
        mainHandler.post(myRunnable);
        // Phone state Intents to capture dialing or answering event
        if (signalingParameters.initiator)
            sendConnectionIntent("dialing");
        else
            sendConnectionIntent("answering");
    }

    private void onConnectedToRoomInternal(final SignalingParameters params) {
        Log.e(TAG, "@@@@@ onConnectedToRoomInternal");
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        if (peerConnectionClient == null) {
            Log.w(TAG, "Room is connected, but EGL context is not ready yet.");
            return;
        }
        logAndToast("Creating peer connection, delay=" + delta + "ms");
        peerConnectionClient.createPeerConnection(
                localRender, remoteRender, signalingParameters);

        if (signalingParameters.initiator) {
            logAndToast("Creating OFFER...");
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createOffer();
        } else {
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

    public void onRemoteDescription(String sdpString) {
        onRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, sdpString));
    }

    //@Override
    public void onRemoteDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Handler mainHandler = new Handler(RCClient.getContext().getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "@@@@@ onRemoteDescription");
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
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
    public void onRemoteIceCandidates(final List<IceCandidate> candidates) {
        Log.e(TAG, "@@@@@ onRemoteIceCandidates");
        // no need to run it in UI thread it is already there due to onRemoteDescription
        if (peerConnectionClient == null) {
            Log.e(TAG,
                    "Received ICE candidates for non-initilized peer connection.");
            return;
        }
        for (IceCandidate candidate : candidates) {
            peerConnectionClient.addRemoteIceCandidate(candidate);
        }
    }

    // Phone state Intents to capture events
    private void sendConnectionIntent (String state)
    {
        SignalingParameters params = this.signalingParameters;
        Intent intent = new Intent ("org.mobicents.restcomm.android.CALL_STATE");
        intent.putExtra("STATE", state);
        intent.putExtra("INCOMING", this.isIncoming());
        if (params != null)
        {
            intent.putExtra("VIDEO", params.videoEnabled);
            intent.putExtra("REQUEST", params.sipUrl);
        }
        if (this.getState() != null)
            intent.putExtra("CONNECTIONSTATE", this.getState().toString());

        Context context = RCClient.getContext();
        context.sendBroadcast(intent);
    }

    // Phone state Intents to capture dropped call event with reason
    private void sendDisconnectErrorIntent (int error, String errorText)
    {
        Intent intent = new Intent ("org.mobicents.restcomm.android.DISCONNECT_ERROR");
        intent.putExtra("STATE", "disconnect error");
        if (errorText != null)
            intent.putExtra("ERRORTEXT", errorText);
        intent.putExtra("ERROR", error);
        intent.putExtra("INCOMING", this.isIncoming());

        Context context = RCClient.getContext();
        context.sendBroadcast(intent);
    }
}
