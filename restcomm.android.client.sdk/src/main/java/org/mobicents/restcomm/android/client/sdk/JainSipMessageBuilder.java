package org.mobicents.restcomm.android.client.sdk;

import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.SipException;
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


    public Request buildRegister(String id, JainSipClientListener listener, ListeningPoint listeningPoint, int expires, final Address contact, HashMap<String,Object> parameters)
    {
        try {
            // Create addresses and via header for the request
            Address fromAddress = jainSipAddressFactory.createAddress("sip:" + parameters.get("pref_sip_user") + "@" +
                    sipUri2IpAddress((String) parameters.get("pref_proxy_domain")));
            fromAddress.setDisplayName((String)parameters.get("pref_sip_user"));

            Address contactAddress;
            if (contact == null) {
                contactAddress = createContactAddress(listeningPoint, parameters);
            } else {
                contactAddress = contact;
            }

            // Create callId from the user provided id, to maintain a correlation between UI and signaling thread
            CallIdHeader callIdHeader = jainSipHeaderFactory.createCallIdHeader(id);

            ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            ViaHeader viaHeader = jainSipHeaderFactory.createViaHeader(listeningPoint.getIPAddress(),
                    listeningPoint.getPort(),
                    listeningPoint.getTransport(),
                    null);
            viaHeader.setRPort();
            viaHeaders.add(viaHeader);

            URI requestURI = jainSipAddressFactory.createAddress((String)parameters.get("pref_proxy_domain")).getURI();

            // Build the request
            Request request = jainSipMessageFactory.createRequest(requestURI,
                    Request.REGISTER, callIdHeader,
                    jainSipHeaderFactory.createCSeqHeader(1l, Request.REGISTER),
                    jainSipHeaderFactory.createFromHeader(fromAddress, Long.toString(System.currentTimeMillis())),
                    jainSipHeaderFactory.createToHeader(fromAddress, null), viaHeaders,
                    jainSipHeaderFactory.createMaxForwardsHeader(70));

            // Add route header with the proxy first
            SipURI routeUri = (SipURI) jainSipAddressFactory.createURI((String)parameters.get("pref_proxy_domain"));
            routeUri.setLrParam();
            Address routeAddress = jainSipAddressFactory.createAddress(routeUri);
            RouteHeader routeHeader = jainSipHeaderFactory.createRouteHeader(routeAddress);
            request.addFirst(routeHeader);

            // Add the contact header
            request.addHeader(jainSipHeaderFactory.createContactHeader(contactAddress));
            ExpiresHeader expiresHeader = jainSipHeaderFactory.createExpiresHeader(expires);
            request.addHeader(expiresHeader);
            request.addHeader(generateUserAgentHeader());

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

    // -- Helpers
    // convert sip uri, like  sip:cloud.restcomm.com:5060 -> cloud.restcomm.com
    public String sipUri2IpAddress(String sipUri) throws ParseException {
        Address address = jainSipAddressFactory.createAddress(sipUri);
        return ((SipURI)address.getURI()).getHost();
    }

    public Address createContactAddress(ListeningPoint listeningPoint, HashMap<String,Object> parameters) throws ParseException{
        RCLogger.i(TAG, "createContactAddress()");
        // TODO: do we really need registering_acc?
        return this.jainSipAddressFactory.createAddress("sip:"
                + parameters.get("pref_sip_user") + "@"
                + listeningPoint.getIPAddress() + ':' + listeningPoint.getPort() + ";transport=" + listeningPoint.getTransport()
                + ";registering_acc=" + sipUri2IpAddress((String) parameters.get("pref_proxy_domain")));
    }

    // TODO: properly handle exception
    public UserAgentHeader generateUserAgentHeader()
    {
        RCLogger.i(TAG, "generateUserAgentHeader()");
        List<String> userAgentTokens = new LinkedList<String>();
        UserAgentHeader header = null;
        userAgentTokens.add(USERAGENT_STRING);
        try {
            header = this.jainSipHeaderFactory.createUserAgentHeader(userAgentTokens);
        }
        catch (ParseException e) {
            e.printStackTrace();
        }

        return header;
    }

}
