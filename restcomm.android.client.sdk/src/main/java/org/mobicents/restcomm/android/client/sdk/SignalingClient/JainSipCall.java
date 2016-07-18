package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogState;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.Transaction;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.util.RCLogger;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

// Represents a call
class JainSipCall {
   // Interface the JainSipCall listener needs to implement, to get events from us
   public interface JainSipCallListener {
      void onCallRingingEvent(String callId);  // onPrivateCallConnectorCallRingingEvent

      //void onCallPeerHangupEvent(String callId);  // either when we receive 200 OK in our BYE, or we send 200 OK to peer's BYE, onPrivateCallConnectorCallHangupEvent

      void onCallPeerRingingEvent(String callId);  // ringback, onPrivateCallConnectorCallRingingBackEvent

      void onCallOutgoingConnectedEvent(String callId, String sdpAnswer);

      void onCallIncomingConnectedEvent(String callId);

      void onCallLocalDisconnectedEvent(String callId);

      void onCallPeerDisconnectedEvent(String callId);

      //void onCallInProgressEvent(String callId);  // SIP code 183, onPrivateCallConnectorCallInProgressEvent

      //void onCallPeerSdpAnswerEvent(String callId);  // onPrivateCallConnectorRemoteSdpAnswerEvent

      //void onCallPeerSdpOfferEvent(String callId);  // onPrivateCallConnectorRemoteSdpOfferEvent

      void onCallCancelledEvent(String callId);  // cancel was received and answered, onPrivateCallConnectorCallCanceledEvent

      void onCallIgnoredEvent(String callId);  // we ignored the call, onPrivateCallConnectorCallClosedEvent

      void onCallErrorEvent(String callId, RCClient.ErrorCodes status, String text);  // onPrivateCallConnectorCallOpenErrorEvent

      void onCallArrivedEvent(String id, String peer, String sdpOffer);

      void onCallDigitsEvent(String callId, RCClient.ErrorCodes status, String text);
   }

   JainSipClient jainSipClient;
   JainSipCallListener listener;
   //String jobId;
   static final String TAG = "JainSipCall";

   JainSipCall(JainSipClient jainSipClient, JainSipCallListener listener)
   {
      this.jainSipClient = jainSipClient;
      this.listener = listener;
   }

   // make a call with the given jobId, using given parameters
   public void open(String jobId, HashMap<String, Object> parameters)
   {
      RCLogger.i(TAG, "open(): id: " + jobId);
      try {
         Transaction transaction = jainSipCallInvite(parameters);
         jainSipClient.jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_CALL, transaction, parameters, this);
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallErrorEvent(jobId, e.errorCode, e.errorText);
         jainSipClient.jainSipJobManager.remove(jobId);
      }
   }

   // make a call with the given jobId, using given parameters
   public void accept(JainSipJob jainSipJob, HashMap<String, Object> parameters)
   {
      RCLogger.i(TAG, "accept(): id: " + jainSipJob.id);
      try {
         jainSipCallAccept(jainSipJob, parameters);
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallErrorEvent(jainSipJob.id, e.errorCode, e.errorText);
      }
   }

   // Send DTMF digits over this call
   public void sendDigits(JainSipJob jainSipJob, String digits)
   {
      RCLogger.i(TAG, "sendDigits(): id: " + jainSipJob.id + ", digits: " + digits);
      if (!jainSipClient.notificationManager.haveConnectivity()) {
         listener.onCallDigitsEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_NO_CONNECTIVITY));
         return;
      }

      try {
         jainSipCallSendDigits(jainSipJob, digits);
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallDigitsEvent(jainSipJob.id, e.errorCode, e.errorText);
      }
   }

   // Close an existing call. The actual SIP request emitted depends on current state: a. If its an early incoming call we Decline, b. If its an early outgoing
   // call we Cancel and c. On any other case we Bye
   public void disconnect(JainSipJob jainSipJob)
   {
      RCLogger.i(TAG, "close(): id: " + jainSipJob.id);
      try {
         if (jainSipJob.transaction.getDialog().getState() == DialogState.EARLY) {
            if (jainSipJob.transaction.getDialog().isServer()) {
               // server transaction (i.e. incoming call)
               RCLogger.i(TAG, "close(): id " + jainSipJob.id + " - Early dialog state for incoming call, sending Decline");
               jainSipCallDecline(jainSipJob);

               listener.onCallLocalDisconnectedEvent(jainSipJob.id);
               // we are done with this call, let's remove job
               jainSipClient.jainSipJobManager.remove(jainSipJob.id);
            }
            else {
               // client transaction (i.e. outgoing call)
               RCLogger.i(TAG, "close(): id " + jainSipJob.id + " - Early dialog state for outgoing call, sending Cancel");
               // if we haven't received 200 OK to our invite yet, we need to cancel
               jainSipCallCancel(jainSipJob);
            }
         }
         else {
            RCLogger.i(TAG, "close(): id " + jainSipJob.id + " - Confirmed dialog state, sending Bye");
            jainSipCallHangup(jainSipJob, jainSipClient.configuration);
         }
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallErrorEvent(jainSipJob.id, e.errorCode, e.errorText);
         jainSipClient.jainSipJobManager.remove(jainSipJob.id);
      }
   }

   // ------ Internal APIs
   public ClientTransaction jainSipCallInvite(final HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallInvite()");

      ClientTransaction transaction = null;

      try {
         Request inviteRequest = jainSipClient.jainSipMessageBuilder.buildInviteRequest(jainSipClient.jainSipListeningPoint, parameters, jainSipClient.configuration, jainSipClient.jainSipClientContext);
         RCLogger.v(TAG, "Sending SIP request: \n" + inviteRequest.toString());
         transaction = jainSipClient.jainSipProvider.getNewClientTransaction(inviteRequest);
         transaction.sendRequest();
      }
      catch (JainSipException e) {
         throw new JainSipException(e.errorCode, e.errorText);
      }
      catch (Exception e) {
         // DNS error (error resolving registrar URI)
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
      }

      return transaction;
   }

   public void jainSipCallAccept(JainSipJob jainSipJob, HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.i(TAG, "jainSipCallAccept(): id: " + jainSipJob.id);
      try {
         ServerTransaction transaction = (ServerTransaction) jainSipJob.transaction;
         Response response = jainSipClient.jainSipMessageBuilder.buildInvite200OKResponse(transaction, (String) parameters.get("sdp"), jainSipClient.jainSipListeningPoint,
               jainSipClient.jainSipClientContext);

         RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
         transaction.sendResponse(response);
      }
      catch (JainSipException e) {
         throw new JainSipException(e.errorCode, e.errorText);
      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_ACCEPT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_ACCEPT_FAILED));
      }
   }

   public ClientTransaction jainSipCallHangup(JainSipJob jainSipJob, HashMap<String, Object> clientConfiguration) throws JainSipException
   {
      RCLogger.i(TAG, "jainSipCallHangup(): id: " + jainSipJob.id);
      Request byeRequest = null;
      try {
         byeRequest = jainSipClient.jainSipMessageBuilder.buildByeRequest(jainSipJob.transaction.getDialog(), clientConfiguration);
         RCLogger.v(TAG, "Sending SIP request: \n" + byeRequest.toString());

         ClientTransaction transaction = jainSipClient.jainSipProvider.getNewClientTransaction(byeRequest);
         jainSipJob.transaction.getDialog().sendRequest(transaction);

         // update transaction in the job to contain the latest transaction
         jainSipJob.updateTransaction(transaction);

         return transaction;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_HANGUP_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_HANGUP_FAILED));
      }
      catch (Exception e) {
         throw new RuntimeException("SIP transaction error");
      }
   }

   public ClientTransaction jainSipCallCancel(JainSipJob jainSipJob) throws JainSipException
   {
      RCLogger.i(TAG, "jainSipCallCancel(): id: " + jainSipJob.id);
      try {
         final Request request = ((ClientTransaction) jainSipJob.transaction).createCancel();
         RCLogger.v(TAG, "Sending SIP response: \n" + request.toString());

         ClientTransaction cancelTransaction = jainSipClient.jainSipProvider.getNewClientTransaction(request);
         //jainSipJob.updateTransaction(cancelTransaction);
         cancelTransaction.sendRequest();
         return cancelTransaction;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_HANGUP_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_HANGUP_FAILED));
      }
   }

   public void jainSipCallDecline(JainSipJob jainSipJob) throws JainSipException
   {
      RCLogger.i(TAG, "jainSipCallReject(): id: " + jainSipJob.id);

      try {
         Response responseDecline = jainSipClient.jainSipMessageBuilder.buildResponse(Response.DECLINE, jainSipJob.transaction.getRequest());
         RCLogger.v(TAG, "Sending SIP response: \n" + responseDecline.toString());
         ((ServerTransaction) jainSipJob.transaction).sendResponse(responseDecline);

      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_DECLINE_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_DECLINE_FAILED));
      }
   }

   public ClientTransaction jainSipCallSendDigits(JainSipJob jainSipJob, String digits) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallSendDigits()");

      try {
         Dialog dialog = jainSipJob.transaction.getDialog();
         Request request = jainSipClient.jainSipMessageBuilder.buildDtmfInfoRequest(dialog, digits);
         RCLogger.v(TAG, "Sending SIP request: \n" + request.toString());
         ClientTransaction transaction = jainSipClient.jainSipProvider.getNewClientTransaction(request);
         dialog.sendRequest(transaction);
         return transaction;
      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_DTMF_DIGITS_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_DTMF_DIGITS_FAILED));
      }
   }

   // Let's follow the naming conventions of SipListener, even though JainSipCall doesn't implement it, to keep it easier to follow
   public void processRequest(JainSipJob jainSipJob, final RequestEvent requestEvent)
   {
      ServerTransaction serverTransaction = requestEvent.getServerTransaction();
      Request request = requestEvent.getRequest();
      String method = request.getMethod();

      if (method.equals(Request.BYE)) {
         try {
            Response response = jainSipClient.jainSipMessageBuilder.buildResponse(Response.OK, request);
            RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            listener.onCallPeerDisconnectedEvent(jainSipJob.id);
            // we are done with this call, let's remove job
            jainSipClient.jainSipJobManager.remove(jainSipJob.id);
         }
         catch (Exception e) {
            RCLogger.e(TAG, "processRequest(): Error sending 200 OK to SIP Bye: " + e.getMessage());
            e.printStackTrace();
         }
      }
      else if (method.equals(Request.CANCEL)) {
         try {
            /* No need, we 'll get NPE anyways
            if (serverTransaction == null) {
               throw new RuntimeException("SIP Cancel received but server transaction is null");
            }
            */

            Response response = jainSipClient.jainSipMessageBuilder.buildResponse(Response.OK, request);
            RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            if (jainSipJob.transaction != null) {
               // also send a 487 Request Terminated response to the original INVITE request
               Request originalInviteRequest = jainSipJob.transaction.getRequest();
               Response originalInviteResponse = jainSipClient.jainSipMessageBuilder.buildResponse(Response.REQUEST_TERMINATED, originalInviteRequest);
               RCLogger.v(TAG, "Sending SIP response: \n" + originalInviteResponse.toString());
               ((ServerTransaction) jainSipJob.transaction).sendResponse(originalInviteResponse);
            }
            listener.onCallCancelledEvent(jainSipJob.id);

            jainSipClient.jainSipJobManager.remove(jainSipJob.id);
         }
         catch (Exception e) {
            e.printStackTrace();
         }
      }
      else if (method.equals(Request.INVITE)) {
         try {
            // Remember that requestEvent ServerTransaction is null for new Dialogs
            if (serverTransaction == null) {
               serverTransaction = jainSipClient.jainSipProvider.getNewServerTransaction(request);
            }

            jainSipJob.updateTransaction(serverTransaction);
            Response response = jainSipClient.jainSipMessageBuilder.buildResponse(Response.RINGING, request);

            // Important: we need set the 'tag' for the 'To' (once that happens Dialog transitions to EARLY)
            ToHeader toHeader = (ToHeader)request.getHeader(ToHeader.NAME);
            toHeader.setTag(Long.toString(System.currentTimeMillis()));
            response.setHeader(toHeader);

            RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            String sdpOffer = new String(request.getRawContent(), "UTF-8");
            listener.onCallArrivedEvent(jainSipJob.id, ((SIPMessage) request).getFrom().getAddress().toString(), sdpOffer);
         }
         catch (Exception e) {
            e.printStackTrace();
         }
      }
      else if (method.equals(Request.ACK)) {
         try {
            // A dialog transitions to the "confirmed" state when a 2xx final response is received to the INVITE Request
            if (serverTransaction.getDialog().getState() == DialogState.CONFIRMED) {
               listener.onCallIncomingConnectedEvent(jainSipJob.id);
            }
            else {
               RCLogger.e(TAG, "Received ACK for dialog not in Confirmed state: \n" + serverTransaction.getDialog().getState());
            }
         }
         catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public void processResponse(JainSipJob jainSipJob, final ResponseEvent responseEvent)
   {
      ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
      Response response = responseEvent.getResponse();
      CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
      String method = cseq.getMethod();

      if (response.getStatusCode() == Response.OK) {
         if (method.equals(Request.INVITE)) {
            try {
               // create and send out ACK
               Dialog dialog = jainSipJob.transaction.getDialog();
               Request ackRequest = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
               RCLogger.v(TAG, "Sending SIP request: \n" + ackRequest.toString());
               dialog.sendAck(ackRequest);

               // filter out SDP to return to UI thread
               String sdpAnswer = new String(response.getRawContent(), "UTF-8");
               // Let's leave this around in case we want to parse the non-webrtc media port in the future
               //SDPAnnounceParser parser = new SDPAnnounceParser(sdpContent);
               //SessionDescriptionImpl sessiondescription = parser.parse();
               //MediaDescription incomingMediaDescriptor = (MediaDescription) sessiondescription.getMediaDescriptions(false).get(0);
               //int rtpPort = incomingMediaDescriptor.getMedia().getMediaPort();

               // if its a webrtc call we need to send back the full SDP
               listener.onCallOutgoingConnectedEvent(jainSipJob.id, sdpAnswer);
            }
            catch (UnsupportedEncodingException e) {
               throw new RuntimeException("Unsupported encoding for SDP");
            }
            catch (SipException e) {
               listener.onCallErrorEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
            }
            catch (Exception e) {
               throw new RuntimeException("Error creating SIP Ack for Invite");
            }
         }
         else if (method.equals(Request.BYE)) {
            listener.onCallLocalDisconnectedEvent(jainSipJob.id);
            // we are done with this call, let's remove job
            jainSipClient.jainSipJobManager.remove(jainSipJob.id);
         }
         else if (method.equals(Request.CANCEL)) {
            if (responseEvent.getClientTransaction().getDialog().getState() == DialogState.CONFIRMED) {
               RCLogger.w(TAG, "processResponse(): Cancel reached peer too late, need to send Bye");
               try {
                  jainSipCallHangup(jainSipJob, jainSipClient.configuration);
               }
               catch (JainSipException e) {
                  listener.onCallErrorEvent(jainSipJob.id, e.errorCode, e.errorText);
                  jainSipClient.jainSipJobManager.remove(jainSipJob.id);
               }
            }
         }
         else if (method.equals(Request.INFO)) {
            listener.onCallDigitsEvent(jainSipJob.id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
         }
      }
      else if (response.getStatusCode() == Response.RINGING) {
         listener.onCallPeerRingingEvent(jainSipJob.id);
      }
      if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED || response.getStatusCode() == Response.UNAUTHORIZED) {
         try {
            // important we pass the jainSipClient params in jainSipAuthenticate (instead of jainSipJob.parameters that has the call parameters), because this is where usename/pass reside
            jainSipClient.jainSipAuthenticate(jainSipJob, jainSipClient.configuration, responseEventExt);
         }
         catch (JainSipException e) {
            listener.onCallErrorEvent(jainSipJob.id, e.errorCode, e.errorText);
         }
      }
      else if (response.getStatusCode() == Response.FORBIDDEN) {
         listener.onCallErrorEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_FORBIDDEN,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_FORBIDDEN));
      }
      else if (response.getStatusCode() == Response.DECLINE || response.getStatusCode() == Response.TEMPORARILY_UNAVAILABLE ||
            (response.getStatusCode() == Response.BUSY_HERE)) {
         listener.onCallPeerDisconnectedEvent(jainSipJob.id);
      }
      else if (response.getStatusCode() == Response.NOT_FOUND) {
         listener.onCallErrorEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_SIGNALING_CALL_SERVICE_UNAVAILABLE,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_SERVICE_UNAVAILABLE));
      }
      else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
         listener.onCallErrorEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }
      else if (response.getStatusCode() == Response.REQUEST_TERMINATED) {
         if (method.equals(Request.INVITE)) {
            // INVITE was terminated by Cancel
            listener.onCallLocalDisconnectedEvent(jainSipJob.id);
            // we are done with this call, let's remove job
            jainSipClient.jainSipJobManager.remove(jainSipJob.id);
         }
         else {
            RCLogger.e(TAG, "processResponse(): unhandled SIP response: " + response.getStatusCode());
         }
      }
      else if (response.getStatusCode() == Response.REQUEST_TERMINATED) {
         // INVITE was terminated by Cancel
         listener.onCallLocalDisconnectedEvent(jainSipJob.id);
         // we are done with this call, let's remove job
         jainSipClient.jainSipJobManager.remove(jainSipJob.id);
      }

      // Notice that we 're not handling '200 Canceling' response as it doesn't add any value to the SDK, at least for now
   }

}
