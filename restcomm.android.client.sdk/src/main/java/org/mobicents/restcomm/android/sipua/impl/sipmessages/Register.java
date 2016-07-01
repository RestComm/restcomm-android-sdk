package org.mobicents.restcomm.android.sipua.impl.sipmessages;

import java.text.ParseException;
import java.util.*;


import android.javax.sip.ClientTransaction;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.SipProvider;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.ExpiresHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.os.AsyncTask;

import org.mobicents.restcomm.android.client.sdk.JainSipClient;
import org.mobicents.restcomm.android.sipua.RCLogger;

public class Register {
	private static final String TAG = "Register";
	public Request MakeNewRequest(JainSipClient jainSipClient, int expires, final Address contact, HashMap<String,Object> parameters) throws ParseException,
			InvalidArgumentException
	{
		try {
			//AddressFactory addressFactory = jainSipClient.jainSipAddressFactory;
			//SipProvider sipProvider = jainSipClient.jainSipProvider;
			//MessageFactory messageFactory = jainSipClient.jainSipMessageFactory;
			//HeaderFactory headerFactory = jainSipClient.jainSipHeaderFactory;

			// Create addresses and via header for the request
			Address fromAddress = jainSipClient.jainSipAddressFactory.createAddress("sip:"
					+ parameters.get("pref_sip_user") + "@"
					+ jainSipClient.sipUri2IpAddress((String)parameters.get("pref_proxy_domain")));
			fromAddress.setDisplayName((String)parameters.get("pref_sip_user"));
			/*
			Address toAddress = jainSipClient.jainSipAddressFactory.createAddress("sip:"
					+ jainSipClient.getSipProfile().getSipUserName() + "@"
					+ jainSipClient.getSipProfile().getRemoteIp(jainSipClient.addressFactory));
			toAddress.setDisplayName(jainSipClient.getSipProfile().getSipUserName());
			*/

			Address contactAddress;
			if (contact == null) {
				// TODO: resume here
				//contactAddress = jainSipClient.createContactAddress();
			} else {
				contactAddress = contact;
			}
			//ArrayList<ViaHeader> viaHeaders = jainSipClient.createViaHeader();

			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			//jainSipViaHeader = this.jainSipListeningPoint.getViaHeader();
			ViaHeader viaHeader = jainSipClient.jainSipHeaderFactory.createViaHeader(jainSipClient.jainSipListeningPoint.getIPAddress(),
					jainSipClient.jainSipListeningPoint.getPort(),
					jainSipClient.jainSipListeningPoint.getTransport(),
					null);
			viaHeader.setRPort();
			viaHeaders.add(viaHeader);

			URI requestURI = jainSipClient.jainSipAddressFactory.createAddress(jainSipClient.getSipProfile().getRemoteEndpoint()).getURI();
			// Build the request
			final Request request = jainSipClient.jainSipMessageFactory.createRequest(requestURI,
					Request.REGISTER, jainSipClient.jainSipProvider.getNewCallId(),
					jainSipClient.jainSipHeaderFactory.createCSeqHeader(1l, Request.REGISTER),
					jainSipClient.jainSipHeaderFactory.createFromHeader(fromAddress, "c3ff411e"),
					jainSipClient.jainSipHeaderFactory.createToHeader(fromAddress, null), viaHeaders,
					jainSipClient.jainSipHeaderFactory.createMaxForwardsHeader(70));


			// Add route header with the proxy first
			SipURI routeUri = (SipURI) jainSipClient.jainSipAddressFactory.createURI(jainSipClient.getSipProfile().getRemoteEndpoint());
			routeUri.setLrParam();
			Address routeAddress = jainSipClient.jainSipAddressFactory.createAddress(routeUri);
			RouteHeader routeHeader = jainSipClient.jainSipHeaderFactory.createRouteHeader(routeAddress);
			request.addFirst(routeHeader);

			// Add the contact header
			request.addHeader(jainSipClient.jainSipHeaderFactory.createContactHeader(contactAddress));
			ExpiresHeader eh = jainSipClient.jainSipHeaderFactory.createExpiresHeader(expires);
			request.addHeader(eh);
			request.addHeader(jainSipClient.generateUserAgentHeader());

			// Print the request
			//RCLogger.v(TAG, request.toString());
			return request;

		} catch (ParseException e) {
			// we want to be able to catch the parse exception from upper layers
			throw e;
		} catch (Exception ex) {
			RCLogger.i(TAG, ex.getMessage());
			ex.printStackTrace();
		}
		return null;
	}

	public Request MakeRequest(org.mobicents.restcomm.android.sipua.impl.SipManager sipManager, int expires, final Address contact) throws ParseException,
			InvalidArgumentException
	{
		try {
			AddressFactory addressFactory = sipManager.addressFactory;
			SipProvider sipProvider = sipManager.sipProvider;
			MessageFactory messageFactory = sipManager.messageFactory;
			HeaderFactory headerFactory = sipManager.headerFactory;
			// Create addresses and via header for the request
			Address fromAddress = addressFactory.createAddress("sip:"
					+ sipManager.getSipProfile().getSipUserName() + "@"
					+ sipManager.getSipProfile().getRemoteIp(sipManager.addressFactory));
			fromAddress.setDisplayName(sipManager.getSipProfile().getSipUserName());
			Address toAddress = addressFactory.createAddress("sip:"
					+ sipManager.getSipProfile().getSipUserName() + "@"
					+ sipManager.getSipProfile().getRemoteIp(sipManager.addressFactory));
			toAddress.setDisplayName(sipManager.getSipProfile().getSipUserName());

			Address contactAddress;
			if (contact == null) {
				contactAddress = sipManager.createContactAddress();
			} else {
				contactAddress = contact;
			}
			ArrayList<ViaHeader> viaHeaders = sipManager.createViaHeader();
			URI requestURI = addressFactory.createAddress(sipManager.getSipProfile().getRemoteEndpoint()).getURI();
			// Build the request
			final Request request = messageFactory.createRequest(requestURI,
					Request.REGISTER, sipProvider.getNewCallId(),
					headerFactory.createCSeqHeader(1l, Request.REGISTER),
					headerFactory.createFromHeader(fromAddress, "c3ff411e"),
					headerFactory.createToHeader(toAddress, null), viaHeaders,
					headerFactory.createMaxForwardsHeader(70));


			// Add route header with the proxy first
			SipURI routeUri = (SipURI) sipManager.addressFactory.createURI(sipManager.getSipProfile().getRemoteEndpoint());
			routeUri.setLrParam();
			Address routeAddress = addressFactory.createAddress(routeUri);
			RouteHeader routeHeader = headerFactory.createRouteHeader(routeAddress);
			request.addFirst(routeHeader);

			// Add the contact header
			request.addHeader(headerFactory.createContactHeader(contactAddress));
			ExpiresHeader eh = headerFactory.createExpiresHeader(expires);
			request.addHeader(eh);
			request.addHeader(sipManager.generateUserAgentHeader());

			// Print the request
			//RCLogger.v(TAG, request.toString());
			return request;

		} catch (ParseException e) {
			// we want to be able to catch the parse exception from upper layers
			throw e;
		} catch (Exception ex) {
			RCLogger.i(TAG, ex.getMessage());
			ex.printStackTrace();
		}
		return null;
	}
}