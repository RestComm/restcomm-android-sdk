package org.mobicents.restcomm.android.sipua.impl.sipmessages;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

import android.javax.sip.address.Address;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.Request;

public class Invite  {
	private static final String TAG = "Invite";

    public Request MakeRequest(SipManager sipManager,String to, int port, HashMap<String, String> sipHeaders) throws  ParseException {
    	
    	try {
			SipURI from = sipManager.addressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), sipManager.getSipProfile().getLocalEndpoint());
			Address fromNameAddress = sipManager.addressFactory.createAddress(from);
			//fromNameAddress.setDisplayName(sipUsername);
			FromHeader fromHeader = sipManager.headerFactory.createFromHeader(fromNameAddress,
                    "Tzt0ZEP92");
			URI toAddress = sipManager.addressFactory.createURI(to);
			Address toNameAddress = sipManager.addressFactory.createAddress(toAddress);
			// toNameAddress.setDisplayName(username);
			ToHeader toHeader = sipManager.headerFactory.createToHeader(toNameAddress, null);

			URI requestURI = sipManager.addressFactory.createURI(to);
			// requestURI.setTransportParam("udp");

			ArrayList<ViaHeader> viaHeaders = sipManager.createViaHeader();

			CallIdHeader callIdHeader = sipManager.sipProvider.getNewCallId();

			CSeqHeader cSeqHeader = sipManager.headerFactory.createCSeqHeader(1l,
					Request.INVITE);

			MaxForwardsHeader maxForwards = sipManager.headerFactory
					.createMaxForwardsHeader(70);

			Request callRequest = sipManager.messageFactory.createRequest(requestURI,
					Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
					toHeader, viaHeaders, maxForwards);
			SupportedHeader supportedHeader = sipManager.headerFactory
					.createSupportedHeader("replaces, outbound");
			callRequest.addHeader(supportedHeader);
            addCustomHeaders(callRequest, sipManager, sipHeaders);

			/*
			SipURI routeUri = sipManager.addressFactory.createSipURI(null, sipManager.getSipProfile().getRemoteIp(sipManager.addressFactory));
			routeUri.setTransportParam(sipManager.getSipProfile().getTransport());
			routeUri.setLrParam();
			routeUri.setPort(sipManager.getSipProfile().getRemotePort(sipManager.addressFactory));
			*/

			if (!sipManager.getSipProfile().getRemoteEndpoint().isEmpty()) {
				// we want to add the ROUTE header only on regular calls (i.e. non-registrarless)
				Address routeAddress = sipManager.addressFactory.createAddress(sipManager.getSipProfile().getRemoteEndpoint());
				RouteHeader route = sipManager.headerFactory.createRouteHeader(routeAddress);
				callRequest.addHeader(route);
			}

			// Create ContentTypeHeader
			ContentTypeHeader contentTypeHeader = sipManager.headerFactory
					.createContentTypeHeader("application", "sdp");

			// Create the contact name address.
			SipURI contactURI = sipManager.addressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), sipManager.getSipProfile().getLocalIp());
			contactURI.setPort(sipManager.sipProvider.getListeningPoint(sipManager.getSipProfile().getTransport())
					.getPort());

			Address contactAddress = sipManager.addressFactory.createAddress(contactURI);

			// Add the contact address.
			//contactAddress.setDisplayName(fromName);

			ContactHeader contactHeader = sipManager.headerFactory.createContactHeader(contactAddress);
			callRequest.addHeader(contactHeader);

			// You can add extension headers of your own making
			// to the outgoing SIP request.
			// Add the extension header.
			//Header extensionHeader = sipManager.headerFactory.createHeader("My-Header", "my header value");
			//callRequest.addHeader(extensionHeader);
			
			String sdpData= "v=0\r\n" +
					"o=- 13760799956958020 13760799956958020" + " IN IP4 " + sipManager.getSipProfile().getLocalIp() +"\r\n" +
					//"s=mysession session\r\n" +
					"s=-\r\n" +
					//"p=+46 8 52018010\r\n" +
					"c=IN IP4 " + sipManager.getSipProfile().getLocalIp()+"\r\n" +
					"t=0 0\r\n" +
					"m=audio " + port + " RTP/AVP 0\r\n" +
					//"m=audio " + port + " RTP/AVP 0 4 18\r\n" +
					"a=rtpmap:0 PCMU/8000\r\n" +
					//"a=rtpmap:4 G723/8000\r\n" +
					//"a=rtpmap:18 G729A/8000\r\n" +
					"a=ptime:20\r\n";
			byte[] contents = sdpData.getBytes();

			callRequest.setContent(contents, contentTypeHeader);
			Header callInfoHeader = sipManager.headerFactory.createHeader("Call-Info",
					"<http://www.antd.nist.gov>");
			callRequest.addHeader(callInfoHeader);
			return callRequest;
		}
		catch (ParseException e) {
			// we want to be able to catch the parse exception from upper layers
			throw e;
		}
		catch (Exception ex) {
			RCLogger.i(TAG, ex.getMessage());
			ex.printStackTrace();
		
		}
		return null;
	}

	public Request MakeRequestWebrtc(SipManager sipManager, String to, String sdp, HashMap<String, String> sipHeaders) throws  ParseException {

		try {
			SipURI from = sipManager.addressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), sipManager.getSipProfile().getLocalEndpoint());
			Address fromNameAddress = sipManager.addressFactory.createAddress(from);
			//fromNameAddress.setDisplayName(sipUsername);
			FromHeader fromHeader = sipManager.headerFactory.createFromHeader(fromNameAddress,
					"Tzt0ZEP92");
			SipURI toAddress = (SipURI) sipManager.addressFactory.createURI(to);
			Address toNameAddress = sipManager.addressFactory.createAddress(toAddress);
			// toNameAddress.setDisplayName(username);
			ToHeader toHeader = sipManager.headerFactory.createToHeader(toNameAddress, null);

			URI requestURI = sipManager.addressFactory.createURI(to);
			// requestURI.setTransportParam("udp");

			ArrayList<ViaHeader> viaHeaders = sipManager.createViaHeader();

			CallIdHeader callIdHeader = sipManager.sipProvider.getNewCallId();

			CSeqHeader cSeqHeader = sipManager.headerFactory.createCSeqHeader(1l,
					Request.INVITE);

			MaxForwardsHeader maxForwards = sipManager.headerFactory
					.createMaxForwardsHeader(70);

			Request callRequest = sipManager.messageFactory.createRequest(requestURI,
					Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
					toHeader, viaHeaders, maxForwards);
			SupportedHeader supportedHeader = sipManager.headerFactory
					.createSupportedHeader("replaces, outbound");
			callRequest.addHeader(supportedHeader);
			addCustomHeaders(callRequest, sipManager, sipHeaders);

			if (!sipManager.getSipProfile().getRemoteEndpoint().isEmpty()) {
				// Add route header with the proxy first
				SipURI routeUri = (SipURI) sipManager.addressFactory.createURI(sipManager.getSipProfile().getRemoteEndpoint());
				routeUri.setLrParam();
				Address routeAddress = sipManager.addressFactory.createAddress(routeUri);
				RouteHeader routeHeader = sipManager.headerFactory.createRouteHeader(routeAddress);
				callRequest.addFirst(routeHeader);
			}

			// Create ContentTypeHeader
			ContentTypeHeader contentTypeHeader = sipManager.headerFactory
					.createContentTypeHeader("application", "sdp");

			// Create the contact name address.
			SipURI contactURI = sipManager.addressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), sipManager.getSipProfile().getLocalIp());
			contactURI.setPort(sipManager.sipProvider.getListeningPoint(sipManager.getSipProfile().getTransport())
					.getPort());

			Address contactAddress = sipManager.addressFactory.createAddress(contactURI);

			// Add the contact address.
			//contactAddress.setDisplayName(fromName);

			ContactHeader contactHeader = sipManager.headerFactory.createContactHeader(contactAddress);
			callRequest.addHeader(contactHeader);

			byte[] contents = sdp.getBytes();

			callRequest.setContent(contents, contentTypeHeader);
			Header callInfoHeader = sipManager.headerFactory.createHeader("Call-Info",
					"<http://www.antd.nist.gov>");
			callRequest.addHeader(callInfoHeader);
			callRequest.addHeader(sipManager.generateUserAgentHeader());
			RCLogger.v(TAG, callRequest.toString());

			return callRequest;

		}
		catch (ParseException e) {
			// we want to be able to catch the parse exception from upper layers
			throw e;
		}
		catch (Exception ex) {
			RCLogger.i(TAG, ex.getMessage());
			ex.printStackTrace();
		}
		return null;
	}

    private void addCustomHeaders(Request callRequest, SipManager sipManager, HashMap<String, String> sipHeaders)
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
					Header customHeader = sipManager.headerFactory.createHeader(me.getKey().toString(), me.getValue().toString());
					callRequest.addHeader(customHeader);
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		}
    }


}
