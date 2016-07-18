package org.mobicents.restcomm.android.client.sdk;

import android.app.Dialog;
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
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.UserAgentHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

import org.mobicents.restcomm.android.sipua.RCLogger;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JainSipMessageBuilder {
   private HeaderFactory jainSipHeaderFactory;
   private AddressFactory jainSipAddressFactory;
   private MessageFactory jainSipMessageFactory;
   private SipProvider jainSipProvider;

   private static final String TAG = "JainSipMessageBuilder";
   private static final int MAX_FORWARDS = 70;

   // TODO: put this in a central place
   public static String USERAGENT_STRING = "TelScale Restcomm Android Client 1.0.0 BETA4";

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
         Address fromAddress = jainSipAddressFactory.createAddress("sip:" + username + "@" + sipUri2IpAddress(domain));
         fromAddress.setDisplayName(username);

         Address toAddress;
         URI requestUri;
         if (!method.equals(Request.REGISTER)) {
            // non register
            toAddress = jainSipAddressFactory.createAddress(toSipUri);
            requestUri = jainSipAddressFactory.createURI(toSipUri);
         }
         else {
            // register
            toAddress = fromAddress;
            requestUri = jainSipAddressFactory.createAddress(domain).getURI();
         }

         Request request =  jainSipMessageFactory.createRequest(requestUri,
               method,
               jainSipProvider.getNewCallId(),
               jainSipHeaderFactory.createCSeqHeader(1l, method),
               jainSipHeaderFactory.createFromHeader(fromAddress, Long.toString(System.currentTimeMillis())),
               jainSipHeaderFactory.createToHeader(toAddress, null),
               createViaHeaders(listeningPoint),
               jainSipHeaderFactory.createMaxForwardsHeader(MAX_FORWARDS));

         // Add route header with the proxy first, if proxy exists (i.e. non-registrarless)
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

         return request;
      }
      catch (ParseException e) {
         e.printStackTrace();
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID));
      }
      catch (Exception e) {
         e.printStackTrace();
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }
   }

   public Request buildRegisterRequest(ListeningPoint listeningPoint, int expires, HashMap<String, Object> parameters)  throws JainSipException
   {
      try {
         Request request = buildBaseRequest(Request.REGISTER, (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
               (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN),
               null, listeningPoint, null);

         ExpiresHeader expiresHeader = jainSipHeaderFactory.createExpiresHeader(expires);
         request.addHeader(expiresHeader);

         request.addHeader(createUserAgentHeader());

         return request;
      }
      catch (Exception e) {
         //throw new RuntimeException("Error building Register request");
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }
   }

   public Request buildInviteRequest(ListeningPoint listeningPoint, HashMap<String, Object> parameters,
                              HashMap<String, Object> clientConfiguration, HashMap<String, Object> clientContext) throws JainSipException
   {
      try {
         Request request = buildBaseRequest(Request.INVITE, (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
               (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN),
               (String) parameters.get("username"), listeningPoint, clientContext);

         SupportedHeader supportedHeader = jainSipHeaderFactory.createSupportedHeader("replaces, outbound");
         request.addHeader(supportedHeader);

         // Create ContentTypeHeader
         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("application", "sdp");
         byte[] contents = ((String) parameters.get("sdp")).getBytes();
         request.setContent(contents, contentTypeHeader);

         // TODO: what should that have?
         Header callInfoHeader = jainSipHeaderFactory.createHeader("Call-Info", "<http://www.antd.nist.gov>");
         request.addHeader(callInfoHeader);

         request.addHeader(createUserAgentHeader());

         // add custom sip headers if applicable
         if (parameters.containsKey("sip-headers")) {
            try {
               addCustomHeaders(request, (HashMap<String, String>) parameters.get("sip-headers"));
            }
            catch (ParseException e) {
               throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_PARSE_CUSTOM_SIP_HEADERS,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_PARSE_CUSTOM_SIP_HEADERS));
            }
         }

         return request;
      }
      catch (ParseException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID));
      }
      catch (Exception e) {
         e.printStackTrace();
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }
   }

   public Request buildMessageRequest(String id, String toSipUri, String message, ListeningPoint listeningPoint, HashMap<String, Object> clientConfiguration) throws JainSipException
   {
      try {
         Request request = buildBaseRequest(Request.MESSAGE, (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
               (String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN),
               toSipUri, listeningPoint, null);

         SupportedHeader supportedHeader = jainSipHeaderFactory.createSupportedHeader("replaces, outbound");
         request.addHeader(supportedHeader);

         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("text", "plain");
         request.setContent(message, contentTypeHeader);

         request.addHeader(createUserAgentHeader());

         return request;
      }
      catch (ParseException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID));
      }
      catch (Exception e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }
   }

   public Request buildByeRequest(android.javax.sip.Dialog dialog, HashMap<String, Object> clientConfiguration) throws JainSipException
   {
      try {
         Request request = dialog.createRequest(Request.BYE);
         if (clientConfiguration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
               !clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
            // we only need this for non-registrarless calls since the problem is only for incoming calls,
            // and when working in registrarless mode there are no incoming calls
            RouteHeader routeHeader = createRouteHeader((String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));
            request.addFirst(routeHeader);
         }

         return request;
      }
      catch (SipException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_HANGUP_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_HANGUP_FAILED));
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
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_DTMF_DIGITS_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_DTMF_DIGITS_FAILED));
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
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_ACCEPT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_ACCEPT_FAILED));
      }
   }

   public Response buildResponse(int responseType, Request request)
   {
      Response response;
      try {
         response = jainSipMessageFactory.createResponse(responseType, request);
         RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
         return response;
      } catch (ParseException e) {
         throw new RuntimeException("Error creating Decline response");
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
   private RouteHeader createRouteHeader(String route)
   {
      try {
         SipURI routeUri = (SipURI) jainSipAddressFactory.createURI(route);
         routeUri.setLrParam();
         Address routeAddress = jainSipAddressFactory.createAddress(routeUri);
         return jainSipHeaderFactory.createRouteHeader(routeAddress);
      }
      catch (ParseException e) {
         throw new RuntimeException("Error creating SIP Route header");
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
   public String sipUri2IpAddress(String sipUri) throws ParseException
   {
      Address address = jainSipAddressFactory.createAddress(sipUri);
      return ((SipURI) address.getURI()).getHost();
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
         e.printStackTrace();
      }
      return viaHeaders;
   }


   public Address createContactAddress(ListeningPoint listeningPoint, String domain, HashMap<String, Object> clientContext) throws ParseException
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

   public String getContactString(ListeningPoint listeningPoint, String domain) throws ParseException
   {
      return getContactString(listeningPoint.getIPAddress(), listeningPoint.getPort(), listeningPoint.getTransport(), domain);
   }

   public String getContactString(String ipAddress, int port, String transport, String domain) throws ParseException
   {
      String contactString = "sip:" + ipAddress + ':' + port + ";transport=" + transport;

      if (domain != null && !domain.equals("")) {
         contactString += ";registering_acc=" + sipUri2IpAddress(domain);
      }

      return contactString;
   }

   // TODO: properly handle exception
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
         throw new RuntimeException("Error creating User Agent header");
      }

      return header;
   }

}
