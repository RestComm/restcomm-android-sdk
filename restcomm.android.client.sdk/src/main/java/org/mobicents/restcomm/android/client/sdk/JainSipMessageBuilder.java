package org.mobicents.restcomm.android.client.sdk;

import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
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

    void initialize(SipFactory sipFactory) throws PeerUnavailableException {
        jainSipHeaderFactory = sipFactory.createHeaderFactory();
        jainSipAddressFactory = sipFactory.createAddressFactory();
        jainSipMessageFactory = sipFactory.createMessageFactory();
    }

    void shutdown() {
        jainSipHeaderFactory = null;
        jainSipAddressFactory = null;
        jainSipMessageFactory = null;
    }

    public Request buildRegister(String id, JainSipClient.JainSipClientListener listener, ListeningPoint listeningPoint, int expires, final String contact, HashMap<String, Object> parameters) {
        try {
            // Create addresses and via header for the request
            Address fromAddress = jainSipAddressFactory.createAddress("sip:" + parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME) + "@" +
                    sipUri2IpAddress((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN)));
            fromAddress.setDisplayName((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME));

            Address contactAddress = createContactAddress(listeningPoint, contact, (String)parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));

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
            SipURI routeUri = (SipURI) jainSipAddressFactory.createURI((String) parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));
            routeUri.setLrParam();
            Address routeAddress = jainSipAddressFactory.createAddress(routeUri);
            RouteHeader routeHeader = jainSipHeaderFactory.createRouteHeader(routeAddress);
            request.addFirst(routeHeader);

            // Add the contact header
            request.addHeader(jainSipHeaderFactory.createContactHeader(contactAddress));
            ExpiresHeader expiresHeader = jainSipHeaderFactory.createExpiresHeader(expires);
            request.addHeader(expiresHeader);
            request.addHeader(createUserAgentHeader());

            return request;
        } catch (ParseException e) {
            listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_URI_INVALID));
            RCLogger.e(TAG, "buildRegister(): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
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

            /*
            SipURI from = jainSipAddressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), sipManager.getSipProfile().getLocalEndpoint());
            Address fromNameAddress = jainSipAddressFactory.createAddress(from);
            FromHeader fromHeader = jainSipHeaderFactory.createFromHeader(fromNameAddress,
                    "Tzt0ZEP92");
                    */

            /*
            SipURI toAddress = (SipURI) jainSipAddressFactory.createURI((String)parameters.get("username"));
            Address toNameAddress = jainSipAddressFactory.createAddress(toAddress);
            // toNameAddress.setDisplayName(username);
            ToHeader toHeader = jainSipHeaderFactory.createToHeader(toNameAddress, null);
            */
            Address toAddress = jainSipAddressFactory.createAddress((String)parameters.get("username"));

            URI requestURI = jainSipAddressFactory.createURI((String)parameters.get("username"));

            ArrayList<ViaHeader> viaHeaders = createViaHeaders(listeningPoint);

            //CallIdHeader callIdHeader = sipManager.sipProvider.getNewCallId();
            // Create callId from the user provided id, to maintain a correlation between UI and signaling thread
            CallIdHeader callIdHeader = jainSipHeaderFactory.createCallIdHeader(id);

            CSeqHeader cSeqHeader = jainSipHeaderFactory.createCSeqHeader(1l,
                    Request.INVITE);

            MaxForwardsHeader maxForwards = jainSipHeaderFactory.createMaxForwardsHeader(70);

            Request callRequest = jainSipMessageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader,
                    jainSipHeaderFactory.createFromHeader(fromAddress, Long.toString(System.currentTimeMillis())),
                    jainSipHeaderFactory.createToHeader(toAddress, null),
                    viaHeaders,
                    maxForwards);
            SupportedHeader supportedHeader = jainSipHeaderFactory.createSupportedHeader("replaces, outbound");
            callRequest.addHeader(supportedHeader);

            if (parameters.containsKey("sip-headers")) {
                addCustomHeaders(callRequest, (HashMap<String, String>)parameters.get("sip-headers"));
            }

            if (clientConfiguration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) &&
                    !clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                // we want to add the ROUTE header only on regular calls (i.e. non-registrarless)
                // Add route header with the proxy first
                // Add route header with the proxy first
                SipURI routeUri = (SipURI) jainSipAddressFactory.createURI((String) clientConfiguration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN));
                routeUri.setLrParam();
                Address routeAddress = jainSipAddressFactory.createAddress(routeUri);
                RouteHeader routeHeader = jainSipHeaderFactory.createRouteHeader(routeAddress);
                callRequest.addFirst(routeHeader);
            }

            // Create ContentTypeHeader
            ContentTypeHeader contentTypeHeader = jainSipHeaderFactory.createContentTypeHeader("application", "sdp");

            // Create the contact address. If we have Via received/rport populated we need to use those, otherwise just use the local listening point's
            int contactPort = listeningPoint.getPort();
            if (clientContext.containsKey("via-rport")) {
                contactPort = (int)clientContext.get("via-rport");
            }
            String contactIPAddress = listeningPoint.getIPAddress();
            if (clientContext.containsKey("via-received")) {
                contactIPAddress = (String)clientContext.get("via-received");
            }

            String contactString = getContactString(contactIPAddress, contactPort, listeningPoint.getTransport());
            Address contactAddress = jainSipAddressFactory.createAddress(contactString);

            //Address contactAddress = createContactAddress(listeningPoint, contactIPAddress + ":" + contactPort, null);

            // TODO: I don't think the username is needed for the contact, but it doesn't cause any issues either
            //SipURI contactURI = jainSipAddressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), contactIPAddress);
            //contactURI.setPort(contactPort);
            //Address contactAddress = jainSipAddressFactory.createAddress(contactURI);

            // Add the contact address.
            //contactAddress.setDisplayName(fromName);

            ContactHeader contactHeader = jainSipHeaderFactory.createContactHeader(contactAddress);
            callRequest.addHeader(contactHeader);

            byte[] contents = ((String)parameters.get("sdp")).getBytes();

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
            /*
            listener.onCallErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_CALL_URI_INVALID));
            RCLogger.e(TAG, "buildInvite(): " + e.getMessage());
            e.printStackTrace();
            */
        }
        catch (Exception e) {
            RCLogger.e(TAG, "buildInvite(): " + e.getMessage());
            e.printStackTrace();
            throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
            /*
            listener.onCallErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
            RCLogger.e(TAG, "buildInvite(): " + e.getMessage());
            e.printStackTrace();
            */
        }
    }

    private void addCustomHeaders(Request callRequest, HashMap<String, String> sipHeaders)
    {
        if (sipHeaders != null) {
            // Get a set of the entries
            Set set = sipHeaders.entrySet();
            // Get an iterator
            Iterator i = set.iterator();
            // Display elements
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                try {
                    Header customHeader = jainSipHeaderFactory.createHeader(me.getKey().toString(), me.getValue().toString());
                    callRequest.addHeader(customHeader);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // -- Helpers
    // convert sip uri, like  sip:cloud.restcomm.com:5060 -> cloud.restcomm.com
    public String sipUri2IpAddress(String sipUri) throws ParseException {
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
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        }
        return viaHeaders;
    }


    public Address createContactAddress(ListeningPoint listeningPoint, String contact, String domain) throws ParseException {
        RCLogger.i(TAG, "createContactAddress()");
        if (contact == null) {
            contact = getContactString(listeningPoint, domain);
        }
        return jainSipAddressFactory.createAddress(contact);
    }

    public String getContactString(ListeningPoint listeningPoint, String domain) throws ParseException {
        String contactString;

        contactString = getContactString(listeningPoint.getIPAddress(), listeningPoint.getPort(), listeningPoint.getTransport());
        if (domain != null) {
            contactString += ";registering_acc=" + sipUri2IpAddress(domain);
        }
        return contactString;
    }

    public String getContactString(String ipAddress, int port, String transport)
    {
        return "sip:" + ipAddress + ':' + port + ";transport=" + transport;
    }

    // TODO: properly handle exception
    public UserAgentHeader createUserAgentHeader() {
        RCLogger.i(TAG, "createUserAgentHeader()");
        List<String> userAgentTokens = new LinkedList<String>();
        UserAgentHeader header = null;
        userAgentTokens.add(USERAGENT_STRING);
        try {
            header = jainSipHeaderFactory.createUserAgentHeader(userAgentTokens);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return header;
    }

}
