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

import android.gov.nist.javax.sip.header.ExtensionHeaderImpl;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipProvider;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ReasonHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.UserAgentHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.Message;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;


import org.restcomm.android.sdk.BuildConfig;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.util.RCLogger;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

class JainSipMessageBuilder {
   private HeaderFactory jainSipHeaderFactory;
   private AddressFactory jainSipAddressFactory;
   private MessageFactory jainSipMessageFactory;
   private SipProvider jainSipProvider;

   private static final String TAG = "JainSipMessageBuilder";
   private static final int MAX_FORWARDS = 70;
   private static final String USERAGENT_STRING = "TelScale Restcomm Android Client " + BuildConfig.VERSION_NAME + "#" + BuildConfig.VERSION_CODE; //"TelScale Restcomm Android Client 1.0.0-BETA4#20";

   void initialize(SipFactory sipFactory, SipProvider provider) throws PeerUnavailableException
   {
      jainSipHeaderFactory = sipFactory.createHeaderFactory();
      jainSipAddressFactory = sipFactory.createAddressFactory();
      jainSipMessageFactory = sipFactory.createMessageFactory();
      jainSipProvider = provider;
   }

   void shutdown()
   {
      jainSipHeaderFactory = null;
      jainSipAddressFactory = null;
      jainSipMessageFactory = null;
   }

   public HeaderFactory getHeaderFactory()
   {
      return jainSipHeaderFactory;
   }

   // Base request builder common for all requests
   private Request buildBaseRequest(String method, String username, String domain, String toSipUri, ListeningPoint listeningPoint, HashMap<String, Object> clientContext) throws JainSipException
   {
      try {
         String fromSipUri;
         // Add route header with the proxy first, if proxy exists (i.e. )
         if (domain != null && !domain.equals("")) {
            // non registrar-less; use username@domain logic
            fromSipUri = "sip:" + username + "@" + sipUri2IpAddress(domain);
         }
         else {
            // registrar-less
            fromSipUri = "sip:" + username + "@" + listeningPoint.getIPAddress();
         }

         Address fromAddress = jainSipAddressFactory.createAddress(fromSipUri);
         fromAddress.setDisplayName(username);

         Address toAddress;
         URI requestUri;
         if (method.equals(Request.REGISTER)) {
            // register
            toAddress = fromAddress;
            requestUri = jainSipAddressFactory.createAddress(domain).getURI();
         }
         else {
            // non register
            toAddress = jainSipAddressFactory.createAddress(toSipUri);
            requestUri = jainSipAddressFactory.createURI(toSipUri);
         }

         Request request = jainSipMessageFactory.createRequest(requestUri,
               method,
               jainSipProvider.getNewCallId(),
               jainSipHeaderFactory.createCSeqHeader(1l, method),
               jainSipHeaderFactory.createFromHeader(fromAddress, Long.toString(System.currentTimeMillis())),
               jainSipHeaderFactory.createToHeader(toAddress, null),
               createViaHeaders(listeningPoint),
               jainSipHeaderFactory.createMaxForwardsHeader(MAX_FORWARDS));

         // Add route header with the proxy first, if proxy exists (i.e. non registrar-less)
         if (domain != null && !domain.equals("")) {
            RouteHeader routeHeader = createRouteHeader(domain);
            request.addFirst(routeHeader);
         }

         // Only pass registering domain non null in register requests
         String registeringDomain = null;
         if (method.equals(Request.REGISTER)) {
            registeringDomain = domain;
         }

         Address contactAddress = createContactAddress(listeningPoint, registeringDomain, clientContext);
         ContactHeader contactHeader = jainSipHeaderFactory.createContactHeader(contactAddress);

         request.addHeader(contactHeader);
         request.addHeader(createUserAgentHeader());

         return request;
      }
      catch (ParseException e) {
         if (method.equals(Request.REGISTER)) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_URI_INVALID,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_URI_INVALID), e);
         }
         else if (method.equals(Request.INVITE)) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_URI_INVALID,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_URI_INVALID), e);
         }
         else {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_MESSAGE_URI_INVALID,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_URI_INVALID), e);
         }
      }
      catch (JainSipException e) {
         throw e;
      }
      catch (Exception e) {
         // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
         // we can adjust and only do a e.printStackTrace()
         throw new RuntimeException("Failed to build base SIP request", e);
      }
   }

   public Request buildRegisterRequest(ListeningPoint listeningPoint, int expires, HashMap<String, Object> parameters) throws JainSipException
   {
      try {
         Request request = buildBaseRequest(Request.REGISTER, (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
               (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN),
               null, listeningPoint, null);

         ExpiresHeader expiresHeader = jainSipHeaderFactory.createExpiresHeader(expires);
         request.addHeader(expiresHeader);

         return request;
      }
      catch (JainSipException e) {
         throw e;
      }
      catch (Exception e) {
         // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
         // we can adjust and only do a e.printStackTrace()
         throw new RuntimeException("Failed to build SIP Register request", e);
      }
   }

   public Request buildInviteRequest(ListeningPoint listeningPoint, HashMap<String, Object> parameters,
                                     HashMap<String, Object> clientConfiguration, HashMap<String, Object> clientContext) throws JainSipException
   {
      try {
         Request request = buildBaseRequest(Request.INVITE, (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
               (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN),
               (String) parameters.get(RCConnection.ParameterKeys.CONNECTION_PEER), listeningPoint, clientContext);

         SupportedHeader supportedHeader = jainSipHeaderFactory.createSupportedHeader("replaces, outbound");
         request.addHeader(supportedHeader);

         // Create ContentTypeHeader
         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("application", "sdp");
         byte[] contents = ((String) parameters.get("sdp")).getBytes();
         request.setContent(contents, contentTypeHeader);

         // add custom sip headers if applicable
         if (parameters.containsKey(RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS)) {
            try {
               addCustomHeaders(request, (HashMap<String, String>) parameters.get(RCConnection.ParameterKeys.CONNECTION_CUSTOM_SIP_HEADERS));
            }
            catch (ParseException e) {
               throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_PARSE_CUSTOM_SIP_HEADERS,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_PARSE_CUSTOM_SIP_HEADERS), e);
            }
         }

         return request;
      }
      catch (JainSipException e) {
         throw e;
      }
      catch (ParseException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_URI_INVALID,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_URI_INVALID), e);
      }
      catch (Exception e) {
         // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
         // we can adjust and only do a e.printStackTrace()
         throw new RuntimeException("Failed to build SIP Invite request", e);
      }
   }

   public Request buildMessageRequest(String toSipUri, String message, ListeningPoint listeningPoint, HashMap<String, Object> clientConfiguration) throws JainSipException
   {
      try {
         Request request = buildBaseRequest(Request.MESSAGE, (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
               (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN),
               toSipUri, listeningPoint, null);

         SupportedHeader supportedHeader = jainSipHeaderFactory.createSupportedHeader("replaces, outbound");
         request.addHeader(supportedHeader);

         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("text", "plain");
         request.setContent(message, contentTypeHeader);

         return request;
      }
      catch (JainSipException e) {
         throw e;
      }
      catch (ParseException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_MESSAGE_URI_INVALID,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_MESSAGE_URI_INVALID), e);
      }
      catch (Exception e) {
         // TODO: let's emit a RuntimeException for now so that we get a loud and clear indication of issues involved in the field and then
         // we can adjust and only do a e.printStackTrace()
         throw new RuntimeException("Failed to build SIP Message request", e);
      }
   }

   public Request buildByeRequest(android.javax.sip.Dialog dialog, String reason, HashMap<String, Object> clientConfiguration) throws JainSipException
   {
      try {
         Request request = dialog.createRequest(Request.BYE);
         request.addHeader(createUserAgentHeader());
         if (clientConfiguration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
               !clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
            // we only need this for non-registrarless calls since the problem is only for incoming calls,
            // and when working in registrarless mode there are no incoming calls
            RouteHeader routeHeader = createRouteHeader((String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));
            request.addFirst(routeHeader);
         }

         int reasonCode;
         if (reason == null || reason.isEmpty()) {
            // normal hangup
            reason = "Normal-Hangup";
            reasonCode = 0;
         }
         else {
            // error, the only error that we convey right now is the connectivity drop
            reasonCode = 1;
         }

         ReasonHeader reasonHeader = jainSipHeaderFactory.createReasonHeader("SIP", reasonCode, reason);
         request.addHeader(reasonHeader);

         return request;
      }
      catch (ParseException|InvalidArgumentException e) {
         // these exceptions occur only in reason header generation, so if it happens it means there's programming error we need to fix
         throw new RuntimeException("Error generating Reason Header for request", e);
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DISCONNECT_FAILED), e);
      }
   }

   public Request buildDtmfInfoRequest(android.javax.sip.Dialog dialog, String digits) throws JainSipException
   {
      try {
         Request request = dialog.createRequest(Request.INFO);

         /*
         // increase Cseq
         CSeqHeader cseq = (CSeqHeader) jainSipJob.transaction.getRequest().getHeader(CSeqHeader.NAME);
         long seqNumber = cseq.getSeqNumber();
         cseq.setSeqNumber(++seqNumber);
         request.setHeader(cseq);
         */

         request.setContent("Signal=" + digits + "\r\nDuration=100\r\n",
               jainSipHeaderFactory.createContentTypeHeader("application", "dtmf-relay"));
         return request;
      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_DTMF_DIGITS_FAILED), e);
      }
   }

   public Response buildInvite200OKResponse(ServerTransaction transaction, String sdp, ListeningPoint listeningPoint, HashMap<String, Object> clientContext) throws JainSipException
   {
      try {
         Response response = jainSipMessageFactory.createResponse(Response.OK, transaction.getRequest());
         Address contactAddress = createContactAddress(listeningPoint, null, clientContext);
         ContactHeader contactHeader = jainSipHeaderFactory.createContactHeader(contactAddress);
         response.addHeader(contactHeader);

         // Not needed as it is being set when sending 180 Ringing
         //ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
         //toHeader.setTag(Long.toString(System.currentTimeMillis()));
         //response.addHeader(contactHeader);

         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("application", "sdp");
         response.setContent(sdp.getBytes(), contentTypeHeader);
         return response;
      }
      catch (ParseException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_ACCEPT_FAILED), e);
      }
   }

   public Response buildResponse(int responseType, Request request)
   {
      Response response;
      try {
         response = jainSipMessageFactory.createResponse(responseType, request);
         //RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
         return response;
      }
      catch (ParseException e) {
         throw new RuntimeException("Error creating Decline response", e);
      }
   }

   /*
   public Response build200OK(Request request)
   {
      Response response;
      try {
         response = jainSipMessageFactory.createResponse(200, request);
         RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
         return response;
      } catch (ParseException e) {
         throw new RuntimeException("Error creating 200 OK");
      }
   }
   */

   // -- Helpers
   // Take a short destination of the form 'bob' and create full SIP URI out of it: 'sip:bob@cloud.restcomm.com'
   public String convert2FullUri(String usernameOrUri, String domain) throws JainSipException
   {
      String fullUri = usernameOrUri;
      if (!usernameOrUri.contains("sip:")) {
         if (domain == null || domain.equals("")) {
            throw new JainSipException(RCClient.ErrorCodes.ERROR_CONNECTION_REGISTRARLESS_FULL_URI_REQUIRED,
                  RCClient.errorText(RCClient.ErrorCodes.ERROR_CONNECTION_REGISTRARLESS_FULL_URI_REQUIRED));
         }

         fullUri = "sip:" + usernameOrUri + "@" + domain.replaceAll("sip:", "");
         RCLogger.i(TAG, "convert2FullUri(): normalizing username to: " + fullUri);
      }
      else {
         RCLogger.i(TAG, "convert2FullUri(): no need for normalization, URI already normalized: " + fullUri);
      }
      return fullUri;
   }

   // Take a short domain of the form 'cloud.restcomm.com' and create full SIP domain out of it: 'sip:cloud.restcomm.com'
   public String convertDomain2Uri(String domain)
   {
      String domainUri = domain;

      // when domain is empty (i.e. registrar-less we don't want to touch it)
      if (!domain.isEmpty() && !domain.contains("sip:")) {
         domainUri = "sip:" + domain;
         RCLogger.i(TAG, "convertDomain2Uri(): normalizing domain to: " + domainUri);
      }
      else {
         RCLogger.i(TAG, "convertDomain2Uri(): no need for normalization, URI already normalized: " + domainUri);
      }

      return domainUri;
   }

   // Normalize domain and SIP URIs
   public void normalizeDomain(HashMap<String, Object> parameters)
   {
      if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN)) {
         parameters.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN,
               convertDomain2Uri((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN)));
      }
   }

   public void normalizePeer(HashMap<String, Object> peerParameters, HashMap<String, Object> clientParameters) throws JainSipException
   {
      if (peerParameters.containsKey(RCConnection.ParameterKeys.CONNECTION_PEER)) {
         peerParameters.put(RCConnection.ParameterKeys.CONNECTION_PEER,
               convert2FullUri((String) peerParameters.get(RCConnection.ParameterKeys.CONNECTION_PEER),
                     (String) clientParameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN)));
      }
   }

   private RouteHeader createRouteHeader(String route)
   {
      try {
         SipURI routeUri = (SipURI) jainSipAddressFactory.createURI(route);
         routeUri.setLrParam();
         Address routeAddress = jainSipAddressFactory.createAddress(routeUri);
         return jainSipHeaderFactory.createRouteHeader(routeAddress);
      }
      catch (ParseException e) {
         throw new RuntimeException("Error creating SIP Route header", e);
      }
   }

   private void addCustomHeaders(Request callRequest, HashMap<String, String> sipHeaders) throws ParseException
   {
      if (sipHeaders != null) {
         // Get a set of the entries
         Set set = sipHeaders.entrySet();
         // Get an iterator
         Iterator i = set.iterator();
         // Display elements
         while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            Header customHeader = jainSipHeaderFactory.createHeader(me.getKey().toString(), me.getValue().toString());
            callRequest.addHeader(customHeader);
         }
      }
   }

   // convert sip uri, like  sip:cloud.restcomm.com:5060 -> cloud.restcomm.com
   public String sipUri2IpAddress(String sipUri) throws ParseException, JainSipException
   {
      try {
         Address address = jainSipAddressFactory.createAddress(sipUri);
         return ((SipURI) address.getURI()).getHost();
      }
      catch (ClassCastException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_URI_INVALID,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_DEVICE_REGISTER_URI_INVALID), e);
      }
   }

   public ArrayList<ViaHeader> createViaHeaders(ListeningPoint listeningPoint) throws ParseException
   {
      ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
      try {
         ViaHeader viaHeader = jainSipHeaderFactory.createViaHeader(listeningPoint.getIPAddress(),
               listeningPoint.getPort(),
               listeningPoint.getTransport(),
               null);
         viaHeader.setRPort();
         viaHeaders.add(viaHeader);
      }
      catch (InvalidArgumentException e) {
         throw new RuntimeException("Failed to create Via headers", e);
      }
      return viaHeaders;
   }


   public Address createContactAddress(ListeningPoint listeningPoint, String domain, HashMap<String, Object> clientContext) throws ParseException, JainSipException
   {
      RCLogger.i(TAG, "createContactAddress()");
      int contactPort = listeningPoint.getPort();
      String contactIPAddress = listeningPoint.getIPAddress();

      // Create the contact address. If we have Via received/rport populated we need to use those, otherwise just use the local listening point's
      if (clientContext != null) {
         if (clientContext.containsKey("via-rport")) {
            contactPort = (int) clientContext.get("via-rport");
         }
         if (clientContext.containsKey("via-received")) {
            contactIPAddress = (String) clientContext.get("via-received");
         }
      }

      String contactString = getContactString(contactIPAddress, contactPort, listeningPoint.getTransport(), domain);

      return jainSipAddressFactory.createAddress(contactString);
   }

   public String getContactString(ListeningPoint listeningPoint, String domain) throws ParseException, JainSipException
   {
      return getContactString(listeningPoint.getIPAddress(), listeningPoint.getPort(), listeningPoint.getTransport(), domain);
   }

   public String getContactString(String ipAddress, int port, String transport, String domain) throws ParseException, JainSipException
   {
      String contactString = "sip:" + ipAddress + ':' + port + ";transport=" + transport;

      if (domain != null && !domain.equals("")) {
         contactString += ";registering_acc=" + sipUri2IpAddress(domain);
      }

      return contactString;
   }

   public UserAgentHeader createUserAgentHeader()
   {
      RCLogger.i(TAG, "createUserAgentHeader()");
      List<String> userAgentTokens = new LinkedList<String>();
      UserAgentHeader header = null;
      userAgentTokens.add(USERAGENT_STRING);
      try {
         header = jainSipHeaderFactory.createUserAgentHeader(userAgentTokens);
      }
      catch (ParseException e) {
         throw new RuntimeException("Error creating User Agent header", e);
      }

      return header;
   }

   /*
    * Parse a SIP request/response (i.e. Message) and extract any custom sip headers inside a HashMap where key is the header name and value is the header value
    */
   static public HashMap<String, String> parseCustomHeaders(Message message)
   {
      // See if there are any custom SIP headers and expose them. Custom headers are headers starting with 'X-'
      HashMap<String,String> customHeaders = new HashMap<>();
      ListIterator iterator =  message.getHeaderNames();
      while (iterator.hasNext()) {
         String headerName = (String)iterator.next();
         if (headerName.matches("(?s)^X-.*")) {
            ExtensionHeaderImpl header = (ExtensionHeaderImpl)message.getHeader(headerName);
            customHeaders.put(header.getName(), header.getHeaderValue());
         }
      }

      if (customHeaders.isEmpty()) {
         customHeaders = null;
      }

      return customHeaders;
   }

}
