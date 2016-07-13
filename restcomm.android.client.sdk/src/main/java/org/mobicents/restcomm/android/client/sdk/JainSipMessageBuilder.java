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

public class JainSipMessageBuilder {
   public HeaderFactory jainSipHeaderFactory;
   public AddressFactory jainSipAddressFactory;
   public MessageFactory jainSipMessageFactory;
   private static final String TAG = "JainSipMessageBuilder";

   // TODO: put this in a central place
   public static String USERAGENT_STRING = "TelScale Restcomm Android Client 1.0.0 BETA4";

   void initialize(SipFactory sipFactory) throws PeerUnavailableException
   {
      jainSipHeaderFactory = sipFactory.createHeaderFactory();
      jainSipAddressFactory = sipFactory.createAddressFactory();
      jainSipMessageFactory = sipFactory.createMessageFactory();
   }

   void shutdown()
   {
      jainSipHeaderFactory = null;
      jainSipAddressFactory = null;
      jainSipMessageFactory = null;
   }

   public Request buildRegister(String id, JainSipClient.JainSipClientListener listener, ListeningPoint listeningPoint, int expires, HashMap<String, Object> parameters)
   {
      try {
         // Create addresses and via header for the request
         Address fromAddress = jainSipAddressFactory.createAddress("sip:" + parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME) + "@" +
               sipUri2IpAddress((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN)));
         fromAddress.setDisplayName((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME));

         Address contactAddress = createContactAddress(listeningPoint, (String)parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN), false, null);

         // Create callId from the user provided id, to maintain a correlation between UI and signaling thread
         CallIdHeader callIdHeader = jainSipHeaderFactory.createCallIdHeader(id);

         ArrayList<ViaHeader> viaHeaders = createViaHeaders(listeningPoint);
         URI requestURI = jainSipAddressFactory.createAddress((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN)).getURI();

         // Build the request
         Request request = jainSipMessageFactory.createRequest(requestURI,
               Request.REGISTER, callIdHeader,
               jainSipHeaderFactory.createCSeqHeader(1l, Request.REGISTER),
               jainSipHeaderFactory.createFromHeader(fromAddress, Long.toString(System.currentTimeMillis())),
               jainSipHeaderFactory.createToHeader(fromAddress, null), viaHeaders,
               jainSipHeaderFactory.createMaxForwardsHeader(70));

         // Add route header with the proxy first
         RouteHeader routeHeader = createRouteHeader((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));
         request.addFirst(routeHeader);

         // Add the contact header
         request.addHeader(jainSipHeaderFactory.createContactHeader(contactAddress));
         ExpiresHeader expiresHeader = jainSipHeaderFactory.createExpiresHeader(expires);
         request.addHeader(expiresHeader);
         request.addHeader(createUserAgentHeader());

         return request;
      }
      catch (ParseException e) {
         listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_URI_INVALID));
         RCLogger.e(TAG, "buildRegister(): " + e.getMessage());
         e.printStackTrace();
      }
      catch (Exception e) {
         listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
         RCLogger.e(TAG, "buildRegister(): " + e.getMessage());
         e.printStackTrace();
      }
      return null;
   }

   public Request buildInvite(String id, JainSipCall.JainSipCallListener listener, ListeningPoint listeningPoint, HashMap<String, Object> parameters,
                              HashMap<String, Object> clientConfiguration, HashMap<String, Object> clientContext) throws JainSipException
   {
      try {
         Address fromAddress = jainSipAddressFactory.createAddress("sip:" + clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_USERNAME) + "@" +
               sipUri2IpAddress((String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN)));
         fromAddress.setDisplayName((String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_USERNAME));

         Address toAddress = jainSipAddressFactory.createAddress((String) parameters.get("username"));

         URI requestURI = jainSipAddressFactory.createURI((String) parameters.get("username"));

         ArrayList<ViaHeader> viaHeaders = createViaHeaders(listeningPoint);

         // Create callId from the user provided id, to maintain a correlation between UI and signaling thread
         CallIdHeader callIdHeader = jainSipHeaderFactory.createCallIdHeader(id);

         CSeqHeader cSeqHeader = jainSipHeaderFactory.createCSeqHeader(1l, Request.INVITE);

         MaxForwardsHeader maxForwards = jainSipHeaderFactory.createMaxForwardsHeader(70);

         Request callRequest = jainSipMessageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader,
               jainSipHeaderFactory.createFromHeader(fromAddress, Long.toString(System.currentTimeMillis())),
               jainSipHeaderFactory.createToHeader(toAddress, null),
               viaHeaders,
               maxForwards);

         SupportedHeader supportedHeader = jainSipHeaderFactory.createSupportedHeader("replaces, outbound");
         callRequest.addHeader(supportedHeader);

         if (parameters.containsKey("sip-headers")) {
            try {
               addCustomHeaders(callRequest, (HashMap<String, String>) parameters.get("sip-headers"));
            }
            catch (ParseException e) {
               throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_PARSE_CUSTOM_SIP_HEADERS,
                     RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_PARSE_CUSTOM_SIP_HEADERS));
            }
         }

         if (clientConfiguration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
               !clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
            // we want to add the ROUTE header only on regular calls (i.e. non-registrarless)
            // Add route header with the proxy first
            RouteHeader routeHeader = createRouteHeader((String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));
            callRequest.addFirst(routeHeader);
         }

         // Create ContentTypeHeader
         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("application", "sdp");

         Address contactAddress = createContactAddress(listeningPoint, (String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN), true, clientContext);

         ContactHeader contactHeader = jainSipHeaderFactory.createContactHeader(contactAddress);
         callRequest.addHeader(contactHeader);

         byte[] contents = ((String) parameters.get("sdp")).getBytes();

         callRequest.setContent(contents, contentTypeHeader);
         Header callInfoHeader = jainSipHeaderFactory.createHeader("Call-Info", "<http://www.antd.nist.gov>");
         callRequest.addHeader(callInfoHeader);
         callRequest.addHeader(createUserAgentHeader());

         return callRequest;
      }
      catch (ParseException e) {
         RCLogger.e(TAG, "buildInvite(): " + e.getMessage());
         e.printStackTrace();
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID));
      }
      catch (Exception e) {
         RCLogger.e(TAG, "buildInvite(): " + e.getMessage());
         e.printStackTrace();
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
      }
   }

   public Request buildBye(android.javax.sip.Dialog dialog, HashMap<String, Object> clientConfiguration) throws JainSipException
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

   public Response buildInvite200OK(ServerTransaction transaction, String sdp, ListeningPoint listeningPoint, HashMap<String, Object> clientContext) throws JainSipException
   {
      try {
         Response response = jainSipMessageFactory.createResponse(Response.OK, transaction.getRequest());
         Address contactAddress = createContactAddress(listeningPoint, null, true, clientContext);
         ContactHeader contactHeader = jainSipHeaderFactory.createContactHeader(contactAddress);
         response.addHeader(contactHeader);
         ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
         toHeader.setTag(Long.toString(System.currentTimeMillis()));
         response.addHeader(contactHeader);

         ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("application", "sdp");
         response.setContent(sdp.getBytes(), contentTypeHeader);
         return response;
      }
      catch (ParseException e) {
         throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_ACCEPT_FAILED,
               RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_ACCEPT_FAILED));
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


   public Address createContactAddress(ListeningPoint listeningPoint, String domain, boolean rportNatify, HashMap<String, Object> clientContext) throws ParseException
   {
      RCLogger.i(TAG, "createContactAddress()");
      int contactPort = listeningPoint.getPort();
      String contactIPAddress = listeningPoint.getIPAddress();
      if (rportNatify) {
         // Create the contact address. If we have Via received/rport populated we need to use those, otherwise just use the local listening point's
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

      if (domain != null) {
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
