package com.example.sipmessagetest;

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

public class SipRegister extends AsyncTask<Object, Object, Object> {

	private void send_register() {
		try {
			System.out.println();
			SipStackAndroid.getInstance();
			AddressFactory addressFactory = SipStackAndroid.addressFactory;
			SipStackAndroid.getInstance();
			SipProvider sipProvider = SipStackAndroid.sipProvider;
			SipStackAndroid.getInstance();
			MessageFactory messageFactory = SipStackAndroid.messageFactory;
			SipStackAndroid.getInstance();
			HeaderFactory headerFactory = SipStackAndroid.headerFactory;
			// Create addresses and via header for the request
			Address fromAddress = addressFactory.createAddress("sip:"
					+ SipStackAndroid.sipUserName + "@"
					+ SipStackAndroid.getRemoteIp());
			fromAddress.setDisplayName(SipStackAndroid.sipUserName);
			Address toAddress = addressFactory.createAddress("sip:"
					+ SipStackAndroid.sipUserName + "@"
					+ SipStackAndroid.getRemoteIp());
			toAddress.setDisplayName(SipStackAndroid.sipUserName);

			Address contactAddress = createContactAddress();
			ArrayList<ViaHeader> viaHeaders = createViaHeader();
			URI requestURI = addressFactory.createAddress(
					"sip:" + SipStackAndroid.getRemoteEndpoint()).getURI();
			// Build the request
			final Request request = messageFactory.createRequest(requestURI,
					Request.REGISTER, sipProvider.getNewCallId(),
					headerFactory.createCSeqHeader(1l, Request.REGISTER),
					headerFactory.createFromHeader(fromAddress, "c3ff411e"),
					headerFactory.createToHeader(toAddress, null), viaHeaders,
					headerFactory.createMaxForwardsHeader(70));

			// Add the contact header
			request.addHeader(headerFactory.createContactHeader(contactAddress));
			ExpiresHeader eh = headerFactory.createExpiresHeader(300);
			request.addHeader(eh);
			// Print the request
			System.out.println(request.toString());

			// Send the request --- triggers an IOException
			// sipProvider.sendRequest(request);
			ClientTransaction transaction = sipProvider
					.getNewClientTransaction(request);
			// Send the request statefully, through the client transaction.
			transaction.sendRequest();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Address createContactAddress() {
		try {
			SipStackAndroid.getInstance();
			return SipStackAndroid.addressFactory.createAddress("sip:"
					+ SipStackAndroid.sipUserName + "@"
					+ SipStackAndroid.getLocalEndpoint() + ";transport=udp"
					+ ";registering_acc="+SipStackAndroid.getRemoteIp());
		} catch (ParseException e) {
			return null;
		}
	}

	private ArrayList<ViaHeader> createViaHeader() {
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader myViaHeader;
		try {
			SipStackAndroid.getInstance();
			myViaHeader = SipStackAndroid.headerFactory.createViaHeader(
					SipStackAndroid.getLocalIp(), SipStackAndroid.getLocalPort(),
					SipStackAndroid.transport, null);
			myViaHeader.setRPort();
			viaHeaders.add(myViaHeader);
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		}
		return viaHeaders;

	}

	
	@Override
	protected Object doInBackground(Object... params) {
		try {
			String sipUsername = (String) params[0];
			String sipPassword = (String) params[1];
			String sipDomain = (String) params[2];

			SipStackAndroid.getInstance().sipUserName = sipUsername;
			SipStackAndroid.getInstance().sipPassword = sipPassword;
			SipStackAndroid.setRemoteIp(sipDomain);

			send_register();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}