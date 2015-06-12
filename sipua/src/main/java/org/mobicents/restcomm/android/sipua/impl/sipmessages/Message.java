package org.mobicents.restcomm.android.sipua.impl.sipmessages;

import java.text.ParseException;
import java.util.*;

import org.mobicents.restcomm.android.sipua.impl.SipManager;

import android.javax.sip.ClientTransaction;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.SipException;
import android.javax.sip.address.Address;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.Request;
import android.os.AsyncTask;

public class Message {

	public Request MakeRequest(SipManager sipManager, String to, String message) throws ParseException, InvalidArgumentException {
		SipURI from = sipManager.addressFactory.createSipURI(sipManager.getSipProfile().getSipUserName(), sipManager.getSipProfile().getLocalEndpoint());
		Address fromNameAddress = sipManager.addressFactory.createAddress(from);
		// fromNameAddress.setDisplayName(sipUsername);
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

		CSeqHeader cSeqHeader = sipManager.headerFactory.createCSeqHeader(50l,
				Request.MESSAGE);

		MaxForwardsHeader maxForwards = sipManager.headerFactory
				.createMaxForwardsHeader(70);

		Request request = sipManager.messageFactory.createRequest(requestURI,
				Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
				toHeader, viaHeaders, maxForwards);
		SupportedHeader supportedHeader = sipManager.headerFactory
				.createSupportedHeader("replaces, outbound");
		request.addHeader(supportedHeader);

		SipURI routeUri = sipManager.addressFactory.createSipURI(null, sipManager.getSipProfile().getRemoteIp());
		routeUri.setTransportParam(sipManager.getSipProfile().getTransport());
		routeUri.setLrParam();
		routeUri.setPort(sipManager.getSipProfile().getRemotePort());

		Address routeAddress = sipManager.addressFactory.createAddress(routeUri);
		RouteHeader route = sipManager.headerFactory.createRouteHeader(routeAddress);
		request.addHeader(route);
		ContentTypeHeader contentTypeHeader = sipManager.headerFactory
				.createContentTypeHeader("text", "plain");
		request.setContent(message, contentTypeHeader);
		System.out.println(request);
		return request;
		//ClientTransaction transaction = sipManager.sipProvider
		//		.getNewClientTransaction(request);
		// Send the request statefully, through the client transaction.
		//transaction.sendRequest();
	}






}