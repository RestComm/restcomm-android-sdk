package org.mobicents.restcomm.android.sipua.impl.sipmessages;

import java.text.ParseException;
import java.util.*;


import android.javax.sip.ClientTransaction;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.SipProvider;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.URI;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.os.AsyncTask;

import org.mobicents.restcomm.android.sipua.RCLogger;

public class Register {
	private static final String TAG = "Register";
	public Request MakeRequest(org.mobicents.restcomm.android.sipua.impl.SipManager sipManager, int expires, final Address contact) throws ParseException,
			InvalidArgumentException {

		AddressFactory addressFactory = sipManager.addressFactory;
		SipProvider sipProvider = sipManager.sipProvider;
		MessageFactory messageFactory = sipManager.messageFactory;
		HeaderFactory headerFactory = sipManager.headerFactory;
		// Create addresses and via header for the request
		Address fromAddress = addressFactory.createAddress("sip:"
				+ sipManager.getSipProfile().getSipUserName() + "@"
				+ sipManager.getSipProfile().getRemoteIp());
		fromAddress.setDisplayName(sipManager.getSipProfile().getSipUserName());
		Address toAddress = addressFactory.createAddress("sip:"
				+ sipManager.getSipProfile().getSipUserName() + "@"
				+ sipManager.getSipProfile().getRemoteIp());
		toAddress.setDisplayName(sipManager.getSipProfile().getSipUserName());

		Address contactAddress;
		if (contact == null) {
			contactAddress = sipManager.createContactAddress();
		}
		else {
			contactAddress = contact;
		}
		ArrayList<ViaHeader> viaHeaders = sipManager.createViaHeader();
		URI requestURI = addressFactory.createAddress(
				"sip:" + sipManager.getSipProfile().getRemoteEndpoint())
				.getURI();
		// Build the request
		final Request request = messageFactory.createRequest(requestURI,
				Request.REGISTER, sipProvider.getNewCallId(),
				headerFactory.createCSeqHeader(1l, Request.REGISTER),
				headerFactory.createFromHeader(fromAddress, "c3ff411e"),
				headerFactory.createToHeader(toAddress, null), viaHeaders,
				headerFactory.createMaxForwardsHeader(70));

		// Add the contact header
		request.addHeader(headerFactory.createContactHeader(contactAddress));
		ExpiresHeader eh = headerFactory.createExpiresHeader(expires);
		request.addHeader(eh);
		request.addHeader(sipManager.generateUserAgentHeader());

		// Print the request
		RCLogger.v(TAG, request.toString());
		return request;
		// Send the request --- triggers an IOException
		// sipProvider.sendRequest(request);
		// ClientTransaction transaction = sipProvider
		// .getNewClientTransaction(request);
		// Send the request statefully, through the client transaction.
		// transaction.sendRequest();

	}

}