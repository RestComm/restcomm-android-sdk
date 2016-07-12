package org.mobicents.restcomm.android.client.sdk;

import android.gov.nist.javax.sdp.SessionDescriptionImpl;
import android.gov.nist.javax.sdp.parser.SDPAnnounceParser;
import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.SdpException;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.Transaction;
import android.javax.sip.TransactionDoesNotExistException;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.address.Address;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.RouteHeader;
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

      void onCallLocalDisconnectedEvent(String callId);

      void onCallPeerDisconnectedEvent(String callId);

      void onCallInProgressEvent(String callId);  // SIP code 183, onPrivateCallConnectorCallInProgressEvent

      void onCallPeerSdpAnswerEvent(String callId);  // onPrivateCallConnectorRemoteSdpAnswerEvent

      void onCallPeerSdpOfferEvent(String callId);  // onPrivateCallConnectorRemoteSdpOfferEvent

      void onCallCancelledEvent(String callId);  // cancel was received and answered, onPrivateCallConnectorCallCanceledEvent

      void onCallIgnoredEvent(String callId);  // we ignored the call, onPrivateCallConnectorCallClosedEvent

      void onCallErrorEvent(String callId, RCClient.ErrorCodes status, String text);  // onPrivateCallConnectorCallOpenErrorEvent

      void onCallArrivedEvent(String id, String peer, String sdpOffer);
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
      try {
         //this.jobId = jobId;
         Transaction transaction = jainSipCallInvite(jobId, parameters);
         jainSipClient.jainSipJobManager.add(jobId, JainSipJob.Type.TYPE_CALL, transaction, parameters, this);
      }
      catch (JainSipException e) {
         listener.onCallErrorEvent(jobId, e.errorCode, e.errorText);
         jainSipClient.jainSipJobManager.remove(jobId);
      }
   }

   // close/hangup and existing call
   public void close(JainSipJob jainSipJob)
   {
      try {
         jainSipCallHangup(jainSipJob, jainSipClient.configuration);
      }
      catch (JainSipException e) {
         listener.onCallErrorEvent(jainSipJob.id, e.errorCode, e.errorText);
         jainSipClient.jainSipJobManager.remove(jainSipJob.id);
      }
   }

   public ClientTransaction jainSipCallInvite(String id, final HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallInvite()");

      ClientTransaction transaction = null;

      try {
         Request inviteRequest = jainSipClient.jainSipMessageBuilder.buildInvite(id, listener, jainSipClient.jainSipListeningPoint, parameters, jainSipClient.configuration, jainSipClient.jainSipClientContext);
         RCLogger.v(TAG, "Sending SIP request: \n" + inviteRequest.toString());
         transaction = jainSipClient.jainSipProvider.getNewClientTransaction(inviteRequest);
         transaction.sendRequest();
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

   public ClientTransaction jainSipCallHangup(JainSipJob jainSipJob, HashMap<String, Object> clientConfiguration) throws JainSipException
   {
      RCLogger.i(TAG, "jainSipCallHangup(): id: " + jainSipJob.id);
      Request byeRequest = null;
      try {
         byeRequest = jainSipClient.jainSipMessageBuilder.buildBye(jainSipJob.transaction.getDialog(), clientConfiguration);
         RCLogger.v(TAG, "Sending SIP request: \n" + byeRequest.toString());

         ClientTransaction transaction = jainSipClient.jainSipProvider.getNewClientTransaction(byeRequest);
         jainSipJob.transaction.getDialog().sendRequest(transaction);

         // update transaction in the job to contain the latest transaction
         jainSipJob.updateTransaction(transaction);

         return transaction;
      }
      catch (SipException e) {
         RCLogger.e(TAG, "jainSipCallHangup(): " + e.getMessage());
         e.printStackTrace();
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
      }
      catch (Exception e) {
         throw new RuntimeException("SIP transaction error");
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
            Response response = jainSipClient.jainSipMessageBuilder.jainSipMessageFactory.createResponse(Response.OK, request);
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

            Response response = jainSipClient.jainSipMessageBuilder.jainSipMessageFactory.createResponse(Response.OK, request);
            RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            if (jainSipJob.transaction != null) {
               // also send a 487 Request Terminated response to the original INVITE request
               Request originalInviteRequest = jainSipJob.transaction.getRequest();
               Response originalInviteResponse = jainSipClient.jainSipMessageBuilder.jainSipMessageFactory.createResponse(Response.REQUEST_TERMINATED, originalInviteRequest);
               ((ServerTransaction)jainSipJob.transaction).sendResponse(originalInviteResponse);
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

            Response response = jainSipClient.jainSipMessageBuilder.jainSipMessageFactory.createResponse(Response.TRYING, request);
            RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            String sdpOffer = new String(request.getRawContent(), "UTF-8");
            listener.onCallArrivedEvent(jainSipJob.id, ((SIPMessage) request).getFrom().getAddress().toString(), sdpOffer);
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
               //SDPAnnounceParser parser = new SDPAnnounceParser(sdpContent);
               //SessionDescriptionImpl sessiondescription = parser.parse();
               //MediaDescription incomingMediaDescriptor = (MediaDescription) sessiondescription.getMediaDescriptions(false).get(0);
               //int rtpPort = incomingMediaDescriptor.getMedia().getMediaPort();

               // if its a webrtc call we need to send back the full SDP
               listener.onCallConnectedEvent(jainSipJob.id, sdpAnswer);
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
      }
      else if (response.getStatusCode() == Response.RINGING) {
         listener.onCallPeerRingingEvent(jainSipJob.id);
      }
      if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED || response.getStatusCode() == Response.UNAUTHORIZED) {
         try {
            // important we pass the jainSipClient params in jainSipAuthenticate (instead of jainSipJob.parameters that has the call parameters), because this is where usename/pass reside
            jainSipClient.jainSipAuthenticate(jainSipJob.id, jainSipJob, jainSipClient.configuration, responseEventExt);
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
         listener.onCallPeerHangupEvent(jainSipJob.id);
      }
      else if (response.getStatusCode() == Response.NOT_FOUND) {
         listener.onCallErrorEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_SIGNALING_CALL_SERVICE_UNAVAILABLE,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_SERVICE_UNAVAILABLE));
      }
      else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
         listener.onCallErrorEvent(jainSipJob.id, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }


   }

}
