package com.example.sipmessagetest;

import java.text.ParseException;
import java.util.*;

import gov.nist.android.javaxx.sip.SipStackExt;
import gov.nist.android.javaxx.sip.clientauthutils.AuthenticationHelper;
import gov.nist.android.javaxx.sip.header.extensions.ReplacesHeader;

import javaxx.sip.ClientTransaction;
import javaxx.sip.Dialog;
import javaxx.sip.DialogTerminatedEvent;
import javaxx.sip.IOExceptionEvent;
import javaxx.sip.InvalidArgumentException;
import javaxx.sip.ListeningPoint;
import javaxx.sip.PeerUnavailableException;
import javaxx.sip.RequestEvent;
import javaxx.sip.ResponseEvent;
import javaxx.sip.ServerTransaction;
import javaxx.sip.SipException;
import javaxx.sip.SipFactory;
import javaxx.sip.SipListener;
import javaxx.sip.SipProvider;
import javaxx.sip.SipStack;
import javaxx.sip.TimeoutEvent;
import javaxx.sip.TransactionTerminatedEvent;
import javaxx.sip.TransactionUnavailableException;
import javaxx.sip.address.Address;
import javaxx.sip.address.AddressFactory;
import javaxx.sip.address.SipURI;
import javaxx.sip.address.URI;
import javaxx.sip.header.AuthorizationHeader;
import javaxx.sip.header.CSeqHeader;
import javaxx.sip.header.CallIdHeader;
import javaxx.sip.header.ContactHeader;
import javaxx.sip.header.ContentTypeHeader;
import javaxx.sip.header.ExpiresHeader;
import javaxx.sip.header.FromHeader;
import javaxx.sip.header.Header;
import javaxx.sip.header.HeaderFactory;
import javaxx.sip.header.MaxForwardsHeader;
import javaxx.sip.header.RecordRouteHeader;
import javaxx.sip.header.RouteHeader;
import javaxx.sip.header.SupportedHeader;
import javaxx.sip.header.ToHeader;
import javaxx.sip.header.ViaHeader;
import javaxx.sip.message.MessageFactory;
import javaxx.sip.message.Request;
import javaxx.sip.message.Response;
import android.os.AsyncTask;

public class SipSendMessage extends AsyncTask<Object, Object, Object>  {

	private void sendMessage(String to, String message) throws ParseException,
			InvalidArgumentException, SipException {

		SipStackAndroid.getInstance();
		SipURI from = SipStackAndroid.addressFactory.createSipURI(SipStackAndroid.getInstance().sipUserName, SipStackAndroid.getInstance().localEndpoint);
		SipStackAndroid.getInstance();
		Address fromNameAddress = SipStackAndroid.addressFactory.createAddress(from);
		SipStackAndroid.getInstance();
		// fromNameAddress.setDisplayName(sipUsername);
		FromHeader fromHeader = SipStackAndroid.headerFactory.createFromHeader(fromNameAddress,
				"Tzt0ZEP92");

		// String username = to.substring(to.indexOf(":") + 1, to.indexOf("@"));
		// String address = to.substring(to.indexOf("@") + 1);

		SipStackAndroid.getInstance();
		URI toAddress = SipStackAndroid.addressFactory.createURI(to);
		SipStackAndroid.getInstance();
		Address toNameAddress = SipStackAndroid.addressFactory.createAddress(toAddress);
		SipStackAndroid.getInstance();
		// toNameAddress.setDisplayName(username);
		ToHeader toHeader = SipStackAndroid.headerFactory.createToHeader(toNameAddress, null);

		SipStackAndroid.getInstance();
		URI requestURI = SipStackAndroid.addressFactory.createURI(to);
		// requestURI.setTransportParam("udp");

		ArrayList<ViaHeader> viaHeaders = createViaHeader();

		SipStackAndroid.getInstance();
		CallIdHeader callIdHeader = SipStackAndroid.sipProvider.getNewCallId();

		SipStackAndroid.getInstance();
		CSeqHeader cSeqHeader = SipStackAndroid.headerFactory.createCSeqHeader(50l,
				Request.MESSAGE);

		SipStackAndroid.getInstance();
		MaxForwardsHeader maxForwards = SipStackAndroid.headerFactory
				.createMaxForwardsHeader(70);

		SipStackAndroid.getInstance();
		Request request = SipStackAndroid.messageFactory.createRequest(requestURI,
				Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
				toHeader, viaHeaders, maxForwards);
		SipStackAndroid.getInstance();
		SupportedHeader supportedHeader = SipStackAndroid.headerFactory
				.createSupportedHeader("replaces, outbound");
		request.addHeader(supportedHeader);

		SipStackAndroid.getInstance();
		SipURI routeUri = SipStackAndroid.addressFactory.createSipURI(null, SipStackAndroid.getInstance().remoteIp);
		routeUri.setTransportParam(SipStackAndroid.transport);
		routeUri.setLrParam();
		routeUri.setPort(SipStackAndroid.remotePort);

		SipStackAndroid.getInstance();
		Address routeAddress = SipStackAndroid.addressFactory.createAddress(routeUri);
		SipStackAndroid.getInstance();
		RouteHeader route =SipStackAndroid.headerFactory.createRouteHeader(routeAddress);
		request.addHeader(route);
		SipStackAndroid.getInstance();
		ContentTypeHeader contentTypeHeader = SipStackAndroid.headerFactory
				.createContentTypeHeader("text", "plain");
		request.setContent(message, contentTypeHeader);
		System.out.println(request);
		SipStackAndroid.getInstance();
		ClientTransaction transaction = SipStackAndroid.sipProvider
				.getNewClientTransaction(request);
		// Send the request statefully, through the client transaction.
		transaction.sendRequest();
	}




	private Address createContactAddress() {
		try {
			SipStackAndroid.getInstance();
			return SipStackAndroid.addressFactory.createAddress("sip:"
					+ SipStackAndroid.getInstance().sipUserName + "@"
					+ SipStackAndroid.getInstance().localEndpoint
					+ ";transport=udp" + ";registering_acc=23_23_228_238");
		} catch (ParseException e) {
			return null;
		}
	}

	private ArrayList<ViaHeader> createViaHeader() {
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader myViaHeader;
		try {
			SipStackAndroid.getInstance();
			SipStackAndroid.getInstance();
			myViaHeader = SipStackAndroid.headerFactory.createViaHeader(
					SipStackAndroid.localIp, SipStackAndroid.localPort,
					SipStackAndroid.transport, null);
			myViaHeader.setRPort();
			viaHeaders.add(myViaHeader);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return viaHeaders;

	}



	@Override
	protected Object doInBackground(Object... params) {
		try {
			String to = (String) params[0];
			String message = (String) params[1];

			sendMessage(to, message);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}