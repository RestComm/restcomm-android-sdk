package org.mobicents.restcomm.android.client.sdk;

import android.gov.nist.javax.sdp.SessionDescriptionImpl;
import android.gov.nist.javax.sdp.parser.SDPAnnounceParser;
import android.gov.nist.javax.sip.ResponseEventExt;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.SdpException;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.Transaction;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.os.SystemClock;

import org.mobicents.restcomm.android.sipua.RCLogger;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.HashMap;

// Represents a call
public class JainSipCall {
    // Interface the JainSipCall listener needs to implement, to get events from us
    public interface JainSipCallListener {
        void onCallRingingEvent(String callId);  // onPrivateCallConnectorCallRingingEvent

        void onCallPeerHangupEvent(String callId);  // either when we receive 200 OK in our BYE, or we send 200 OK to peer's BYE, onPrivateCallConnectorCallHangupEvent

        void onCallPeerRingingEvent(String callId);  // ringback, onPrivateCallConnectorCallRingingBackEvent

        void onCallConnectedEvent(String callId, String sdpAnswer);

        void onCallInProgressEvent(String callId);  // SIP code 183, onPrivateCallConnectorCallInProgressEvent

        void onCallPeerSdpAnswerEvent(String callId);  // onPrivateCallConnectorRemoteSdpAnswerEvent

        void onCallPeerSdpOfferEvent(String callId);  // onPrivateCallConnectorRemoteSdpOfferEvent

        void onCallCancelledEvent(String callId);  // cancel was received and answered, onPrivateCallConnectorCallCanceledEvent

        void onCallIgnoredEvent(String callId);  // we ignored the call, onPrivateCallConnectorCallClosedEvent

        void onCallErrorEvent(String callId, RCClient.ErrorCodes status, String text);  // onPrivateCallConnectorCallOpenErrorEvent
    }

    JainSipClient jainSipClient;
    JainSipCallListener listener;
    String jobId;
    static final String TAG = "JainSipCall";

    JainSipCall(JainSipClient jainSipClient, JainSipCallListener listener)
    {
        this.jainSipClient = jainSipClient;
        this.listener = listener;
    }

    // make a call with the given jobId, using given parameters
    public void open(String jobId, HashMap<String, Object> parameters)
    {
        try {
            this.jobId = jobId;
            Transaction transaction = jainSipCallInvite(jobId, parameters);
            jainSipClient.jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_CALL, transaction, parameters, this);
        }
        catch (JainSipException e) {
            listener.onCallErrorEvent(jobId, e.errorCode, e.errorText);
            jainSipClient.jainSipJobManager.remove(jobId);
        }
    }

    public ClientTransaction jainSipCallInvite(String id, final HashMap<String, Object> parameters) throws JainSipException
    {
        RCLogger.v(TAG, "jainSipCallInvite()");

        if (!jainSipClient.notificationManager.haveConnectivity()) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY,
                    RCClient.errorText(RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY));
        }

        ClientTransaction transaction = null;

        try {
            Request inviteRequest = jainSipClient.jainSipMessageBuilder.buildInvite(id, listener, jainSipClient.jainSipListeningPoint, parameters, jainSipClient.configuration, jainSipClient.jainSipClientContext);
            RCLogger.v(TAG, "Sending SIP request: \n" + inviteRequest.toString());
            transaction = jainSipClient.jainSipProvider.getNewClientTransaction(inviteRequest);
            // note: we might need to make this 'syncrhonized' to avoid race at some point
            transaction.sendRequest();
            //dialog = transaction.getDialog();
        }
        catch (JainSipException e) {
            throw new JainSipException(e.errorCode, e.errorText);
        }
        catch (Exception e) {
            // DNS error (error resolving registrar URI)
            RCLogger.e(TAG, "jainSipCallInvite(): " + e.getMessage());
            e.printStackTrace();
            throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT,
                    RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
        }

        return transaction;
    }

    //
    public void processResponse(JainSipJob jainSipJob, final ResponseEvent responseEvent)
    {
        ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
        Response response = responseEvent.getResponse();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        String method = cseq.getMethod();

        if (method.equals(Request.INVITE)) {
            if (response.getStatusCode() == Response.RINGING) {
                listener.onCallPeerRingingEvent(jobId);
            }
            else if (response.getStatusCode() == Response.OK) {
                try {
                    // create and send out ACK
                    Dialog dialog = jainSipJob.transaction.getDialog();
                    Request ackRequest = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
                    RCLogger.v(TAG, "Sending SIP request: \n" + ackRequest.toString());
                    dialog.sendAck(ackRequest);

                    // filter out SDP to return to UI thread
                    byte[] rawContent = response.getRawContent();
                    String sdpContent = new String(rawContent, "UTF-8");
                    SDPAnnounceParser parser = new SDPAnnounceParser(sdpContent);
                    SessionDescriptionImpl sessiondescription = parser.parse();
                    MediaDescription incomingMediaDescriptor = (MediaDescription) sessiondescription.getMediaDescriptions(false).get(0);
                    //int rtpPort = incomingMediaDescriptor.getMedia().getMediaPort();

                    // if its a webrtc call we need to send back the full SDP
                    listener.onCallConnectedEvent(jobId, sdpContent);
                    //dispatchSipEvent(new SipEvent(this, SipEventType.CALL_CONNECTED, "", "", rtpPort, sdpContent));
                }
                catch (Exception e) {
                    RCLogger.e(TAG, "processResponse(): " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        else if (response.getStatusCode() == Response.DECLINE || response.getStatusCode() == Response.TEMPORARILY_UNAVAILABLE ||
                (response.getStatusCode() == Response.BUSY_HERE)) {
            listener.onCallPeerHangupEvent(jobId);
        }
        else if (response.getStatusCode() == Response.NOT_FOUND) {
            listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_SIGNALING_CALL_SERVICE_UNAVAILABLE,
                    RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_SERVICE_UNAVAILABLE));
        }
        else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
            listener.onCallErrorEvent(jobId, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED,
                    RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
        }
    }

}
