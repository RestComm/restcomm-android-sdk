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

package org.restcomm.android.sdk.SignalingClient.JainSipClient;

import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogState;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.Transaction;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.util.RCLogger;

import java.util.HashMap;

// Represents a call
public class JainSipCall {
   // Interface the JainSipCall listener needs to implement, to get events from us
   public interface JainSipCallListener {
      void onCallOutgoingPeerRingingEvent(String jobId);

      void onCallOutgoingConnectedEvent(String jobId, String sdpAnswer, HashMap<String, String> customHeaders);

      void onCallIncomingConnectedEvent(String jobId);

      void onCallLocalDisconnectedEvent(String jobId);

      void onCallPeerDisconnectedEvent(String jobId);

      // cancel was received and answered
      void onCallIncomingCanceledEvent(String jobId);

      // TODO: for when we implement call ignore functionality
      // we ignored the call
      void onCallIgnoredEvent(String jobId);

      void onCallErrorEvent(String jobId, RCClient.ErrorCodes status, String text);

      void onCallArrivedEvent(String jobId, String peer, String sdpOffer, HashMap<String, String> customHeaders);

      void onCallDigitsEvent(String jobId, RCClient.ErrorCodes status, String text);
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
      RCLogger.i(TAG, "open(): id: " + jobId + ", parameters: " + parameters.toString());
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
      RCLogger.i(TAG, "accept(): jobId: " + jainSipJob.jobId + ", parameters: " + parameters.toString());
      try {
         jainSipCallAccept(jainSipJob, parameters);
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallErrorEvent(jainSipJob.jobId, e.errorCode, e.errorText);
         jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
      }
   }

   // Send DTMF digits over this call
   public void sendDigits(JainSipJob jainSipJob, String digits)
   {
      RCLogger.i(TAG, "sendDigits(): jobId: " + jainSipJob.jobId + ", digits: " + digits);
      if (!jainSipClient.jainSipNotificationManager.haveConnectivity()) {
         listener.onCallDigitsEvent(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY, RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_NO_CONNECTIVITY));
         return;
      }
      try {
         jainSipCallSendDigits(jainSipJob, digits);
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallDigitsEvent(jainSipJob.jobId, e.errorCode, e.errorText);
      }
   }

   // Close an existing call. The actual SIP request emitted depends on current state: a. If its an early incoming call we Decline, b. If its an early outgoing
   // call we Cancel and c. On any other case we Bye
   public void disconnect(JainSipJob jainSipJob, String reason)
   {
      RCLogger.i(TAG, "close(): jobId: " + jainSipJob.jobId);
      try {
         if (jainSipJob.transaction.getDialog().getState() == null ||
               jainSipJob.transaction.getDialog().getState() == DialogState.EARLY) {
            if (jainSipJob.transaction.getDialog().isServer()) {
               // server transaction (i.e. incoming call)
               RCLogger.v(TAG, "close(): jobId " + jainSipJob.jobId + " - Early dialog state for incoming call, sending Decline");
               jainSipCallDecline(jainSipJob);

               listener.onCallLocalDisconnectedEvent(jainSipJob.jobId);
               // we are done with this call, let's remove job
               jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
            }
            else {
               // client transaction (i.e. outgoing call)
               RCLogger.v(TAG, "close(): jobId " + jainSipJob.jobId + " - Early dialog state for outgoing call, sending Cancel");
               // if we haven't received 200 OK to our invite yet, we need to cancel
               jainSipCallCancel(jainSipJob);
            }
         }
         else {
            RCLogger.v(TAG, "close(): jobId " + jainSipJob.jobId + " - Confirmed dialog state, sending Bye");
            jainSipCallHangup(jainSipJob, jainSipClient.configuration, reason);
         }
      }
      catch (JainSipException e) {
         e.printStackTrace();
         listener.onCallErrorEvent(jainSipJob.jobId, e.errorCode, e.errorText);
         jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
      }
   }

   // ------ Internal APIs
   public ClientTransaction jainSipCallInvite(final HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallInvite()");

      ClientTransaction transaction = null;

      try {
         Request inviteRequest = jainSipClient.jainSipMessageBuilder.buildInviteRequest(jainSipClient.jainSipListeningPoint, parameters, jainSipClient.configuration, jainSipClient.jainSipClientContext);
         RCLogger.i(TAG, "Sending SIP request: \n" + inviteRequest.toString());
         transaction = jainSipClient.jainSipProvider.getNewClientTransaction(inviteRequest);
         transaction.sendRequest();
      }
      catch (JainSipException e) {
         throw e;
      }
      catch (Exception e) {
         // DNS error (error resolving registrar URI)
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_COULD_NOT_CONNECT,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_COULD_NOT_CONNECT), e);
      }

      return transaction;
   }

   public void jainSipCallAccept(JainSipJob jainSipJob, HashMap<String, Object> parameters) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallAccept(): jobId: " + jainSipJob.jobId);
      try {
         ServerTransaction transaction = (ServerTransaction) jainSipJob.transaction;
         Response response = jainSipClient.jainSipMessageBuilder.buildInvite200OKResponse(transaction, (String) parameters.get("sdp"), jainSipClient.jainSipListeningPoint,
               jainSipClient.jainSipClientContext);

         RCLogger.i(TAG, "Sending SIP response: \n" + response.toString());
         transaction.sendResponse(response);
      }
      catch (JainSipException e) {
         throw e;
      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_FAILED), e);
      }
   }

   public ClientTransaction jainSipCallHangup(JainSipJob jainSipJob, HashMap<String, Object> clientConfiguration, String reason) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallHangup(): jobId: " + jainSipJob.jobId);
      Request byeRequest = null;
      try {
         byeRequest = jainSipClient.jainSipMessageBuilder.buildByeRequest(jainSipJob.transaction.getDialog(), reason, clientConfiguration);
         RCLogger.i(TAG, "Sending SIP request: \n" + byeRequest.toString());

         ClientTransaction transaction = jainSipClient.jainSipProvider.getNewClientTransaction(byeRequest);
         jainSipJob.transaction.getDialog().sendRequest(transaction);

         // update transaction in the job to contain the latest transaction
         jainSipJob.updateTransaction(transaction);

         return transaction;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED), e);
      }
      catch (Exception e) {
         throw new RuntimeException("SIP transaction error", e);
      }
   }

   public ClientTransaction jainSipCallCancel(JainSipJob jainSipJob) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallCancel(): jobId: " + jainSipJob.jobId);
      try {
         final Request request = ((ClientTransaction) jainSipJob.transaction).createCancel();
         RCLogger.i(TAG, "Sending SIP response: \n" + request.toString());

         ClientTransaction cancelTransaction = jainSipClient.jainSipProvider.getNewClientTransaction(request);
         //jainSipJob.updateTransaction(cancelTransaction);
         cancelTransaction.sendRequest();
         return cancelTransaction;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED), e);
      }
   }

   public void jainSipCallDecline(JainSipJob jainSipJob) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallReject(): jobId: " + jainSipJob.jobId);

      try {
         Response responseDecline = jainSipClient.jainSipMessageBuilder.buildResponse(Response.DECLINE, jainSipJob.transaction.getRequest());
         RCLogger.i(TAG, "Sending SIP response: \n" + responseDecline.toString());
         ((ServerTransaction) jainSipJob.transaction).sendResponse(responseDecline);

      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_DECLINE_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DECLINE_FAILED), e);
      }
   }

   public ClientTransaction jainSipCallSendDigits(JainSipJob jainSipJob, String digits) throws JainSipException
   {
      RCLogger.v(TAG, "jainSipCallSendDigits()");

      try {
         Dialog dialog = jainSipJob.transaction.getDialog();
         Request request = jainSipClient.jainSipMessageBuilder.buildDtmfInfoRequest(dialog, digits);
         RCLogger.i(TAG, "Sending SIP request: \n" + request.toString());
         ClientTransaction transaction = jainSipClient.jainSipProvider.getNewClientTransaction(request);
         dialog.sendRequest(transaction);
         return transaction;
      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_FAILED), e);
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
            RCLogger.i(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            listener.onCallPeerDisconnectedEvent(jainSipJob.jobId);
            // we are done with this call, let's remove job
            jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
         }
         catch (Exception e) {
            // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
            // we can adjust and only do a e.printStackTrace()
            throw new RuntimeException("Failed to respond to Bye request", e);
         }
      }
      else if (method.equals(Request.CANCEL)) {
         try {
            Response response = jainSipClient.jainSipMessageBuilder.buildResponse(Response.OK, request);
            RCLogger.i(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            if (jainSipJob.transaction != null) {
               // also send a 487 Request Terminated response to the original INVITE request
               Request originalInviteRequest = jainSipJob.transaction.getRequest();
               Response originalInviteResponse = jainSipClient.jainSipMessageBuilder.buildResponse(Response.REQUEST_TERMINATED, originalInviteRequest);
               RCLogger.i(TAG, "Sending SIP response: \n" + originalInviteResponse.toString());
               ((ServerTransaction) jainSipJob.transaction).sendResponse(originalInviteResponse);
            }
            listener.onCallIncomingCanceledEvent(jainSipJob.jobId);

            jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
         }
         catch (Exception e) {
            // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
            // we can adjust and only do a e.printStackTrace()
            throw new RuntimeException("Failed to respond to Cancel request", e);
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

            RCLogger.i(TAG, "Sending SIP response: \n" + response.toString());
            serverTransaction.sendResponse(response);

            String sdpOffer = new String(request.getRawContent(), "UTF-8");
            listener.onCallArrivedEvent(jainSipJob.jobId, ((SIPMessage) request).getFrom().getAddress().toString(), sdpOffer, JainSipMessageBuilder.parseCustomHeaders(request));
         }
         catch (Exception e) {
            // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
            // we can adjust and only do a e.printStackTrace()
            throw new RuntimeException("Failed to send Ringing to incoming Invite", e);
         }
      }
      else if (method.equals(Request.ACK)) {
         // A dialog transitions to the "confirmed" state when a 2xx final response is received to the INVITE Request
         if (serverTransaction.getDialog().getState() == DialogState.CONFIRMED) {
            listener.onCallIncomingConnectedEvent(jainSipJob.jobId);
         }
         else {
            RCLogger.e(TAG, "Received ACK for dialog not in Confirmed state: \n" + serverTransaction.getDialog().getState());
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
               RCLogger.i(TAG, "Sending SIP request: \n" + ackRequest.toString());
               dialog.sendAck(ackRequest);

               // filter out SDP to return to UI thread
               String sdpAnswer = new String(response.getRawContent(), "UTF-8");
               // Let's leave this around in case we want to parse the non-webrtc media port in the future
               //SDPAnnounceParser parser = new SDPAnnounceParser(sdpContent);
               //SessionDescriptionImpl sessiondescription = parser.parse();
               //MediaDescription incomingMediaDescriptor = (MediaDescription) sessiondescription.getMediaDescriptions(false).get(0);
               //int rtpPort = incomingMediaDescriptor.getMedia().getMediaPort();

               // if its a webrtc call we need to send back the full SDP
               listener.onCallOutgoingConnectedEvent(jainSipJob.jobId, sdpAnswer, JainSipMessageBuilder.parseCustomHeaders(response));
            }
            catch (SipException e) {
               listener.onCallErrorEvent(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_CONNECTION_COULD_NOT_CONNECT,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_COULD_NOT_CONNECT));
            }
            catch (Exception e) {
               // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
               // we can adjust and only do a e.printStackTrace()
               throw new RuntimeException("Failed to Ack the 200 Ok out outgoing Invite", e);
            }
         }
         else if (method.equals(Request.BYE)) {
            listener.onCallLocalDisconnectedEvent(jainSipJob.jobId);
            // we are done with this call, let's remove job
            jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
         }
         else if (method.equals(Request.CANCEL)) {
            if (responseEvent.getClientTransaction().getDialog().getState() == DialogState.CONFIRMED) {
               RCLogger.w(TAG, "processResponse(): Cancel reached peer too late, need to send Bye");
               try {
                  jainSipCallHangup(jainSipJob, jainSipClient.configuration, null);
               }
               catch (JainSipException e) {
                  listener.onCallErrorEvent(jainSipJob.jobId, e.errorCode, e.errorText);
                  jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
               }
            }
         }
         else if (method.equals(Request.INFO)) {
            listener.onCallDigitsEvent(jainSipJob.jobId, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
         }
      }
      else if (response.getStatusCode() == Response.RINGING) {
         listener.onCallOutgoingPeerRingingEvent(jainSipJob.jobId);
      }
      if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED || response.getStatusCode() == Response.UNAUTHORIZED) {
         try {
            // important we pass the jainSipClient params in jainSipAuthenticate (instead of jainSipJob.parameters that has the call parameters), because this is where usename/pass reside
            jainSipClient.jainSipAuthenticate(jainSipJob, jainSipClient.configuration, responseEventExt);
         }
         catch (JainSipException e) {
            listener.onCallErrorEvent(jainSipJob.jobId, e.errorCode, e.errorText);
         }
      }
      else if (response.getStatusCode() == Response.FORBIDDEN) {
         listener.onCallErrorEvent(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_CONNECTION_AUTHENTICATION_FORBIDDEN,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_AUTHENTICATION_FORBIDDEN));
      }
      else if (response.getStatusCode() == Response.DECLINE || response.getStatusCode() == Response.TEMPORARILY_UNAVAILABLE ||
            (response.getStatusCode() == Response.BUSY_HERE)) {
         listener.onCallPeerDisconnectedEvent(jainSipJob.jobId);
      }
      else if (response.getStatusCode() == Response.NOT_FOUND) {
         listener.onCallErrorEvent(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_CONNECTION_PEER_NOT_FOUND,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_PEER_NOT_FOUND));
         // we don't remove job because right now the flow is such that the client disconnects after this event
         jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
      }
      else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
         listener.onCallErrorEvent(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_CONNECTION_SERVICE_UNAVAILABLE,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_SERVICE_UNAVAILABLE));
         jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
      }
      else if (response.getStatusCode() == Response.REQUEST_TERMINATED) {
         if (method.equals(Request.INVITE)) {
            // INVITE was terminated by Cancel
            listener.onCallLocalDisconnectedEvent(jainSipJob.jobId);
            // we are done with this call, let's remove job
            jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
         }
         else {
            RCLogger.e(TAG, "processResponse(): unhandled SIP response: " + response.getStatusCode());
         }
      }
      /*
      else if (response.getStatusCode() == Response.REQUEST_TERMINATED) {
         // INVITE was terminated by Cancel
         listener.onCallLocalDisconnectedEvent(jainSipJob.jobId);
         // we are done with this call, let's remove job
         jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
      }
      */

      // Notice that we 're not handling '200 Canceling' response as it doesn't add any value to the SDK, at least for now
   }

   public void processTimeout(JainSipJob jainSipJob, final TimeoutEvent timeoutEvent)
   {
      listener.onCallErrorEvent(jainSipJob.jobId, RCClient.ErrorCodes.ERROR_CONNECTION_SIGNALING_TIMEOUT,
            RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_SIGNALING_TIMEOUT));
      jainSipClient.jainSipJobManager.remove(jainSipJob.jobId);
   }

}
