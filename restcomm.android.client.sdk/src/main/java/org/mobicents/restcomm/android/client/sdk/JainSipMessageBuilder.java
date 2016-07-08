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
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.UserAgentHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;

import org.mobicents.restcomm.android.sipua.RCLogger;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

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

            ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            ViaHeader viaHeader = jainSipHeaderFactory.createViaHeader(listeningPoint.getIPAddress(),
                    listeningPoint.getPort(),
                    listeningPoint.getTransport(),
                    null);
            viaHeader.setRPort();
            viaHeaders.add(viaHeader);

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

    // -- Helpers
    // convert sip uri, like  sip:cloud.restcomm.com:5060 -> cloud.restcomm.com
    public String sipUri2IpAddress(String sipUri) throws ParseException {
        Address address = jainSipAddressFactory.createAddress(sipUri);
        return ((SipURI) address.getURI()).getHost();
    }

    public Address createContactAddress(ListeningPoint listeningPoint, String contact, String domain) throws ParseException {
        RCLogger.i(TAG, "createContactAddress()");
        if (contact == null) {
            contact = getContactString(listeningPoint, domain);
        }
        return this.jainSipAddressFactory.createAddress(contact);
    }

    public String getContactString(ListeningPoint listeningPoint, String domain) throws ParseException {
        // TODO: do we really need registering_acc?
        return "sip:" + listeningPoint.getIPAddress() + ':' + listeningPoint.getPort() + ";transport=" + listeningPoint.getTransport() +
                ";registering_acc=" + sipUri2IpAddress(domain);
    }

    // TODO: properly handle exception
    public UserAgentHeader createUserAgentHeader() {
        RCLogger.i(TAG, "createUserAgentHeader()");
        List<String> userAgentTokens = new LinkedList<String>();
        UserAgentHeader header = null;
        userAgentTokens.add(USERAGENT_STRING);
        try {
            header = this.jainSipHeaderFactory.createUserAgentHeader(userAgentTokens);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return header;
    }

}
