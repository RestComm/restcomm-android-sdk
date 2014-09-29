package com.example.sipmessagetest;

import java.text.ParseException;
import java.util.*;

import javaxx.sip.ClientTransaction;
import javaxx.sip.DialogTerminatedEvent;
import javaxx.sip.IOExceptionEvent;
import javaxx.sip.InvalidArgumentException;
import javaxx.sip.SipProvider;
import javaxx.sip.TimeoutEvent;
import javaxx.sip.TransactionTerminatedEvent;
import javaxx.sip.address.Address;
import javaxx.sip.address.AddressFactory;
import javaxx.sip.address.URI;
import javaxx.sip.header.ExpiresHeader;
import javaxx.sip.header.HeaderFactory;
import javaxx.sip.header.ViaHeader;
import javaxx.sip.message.MessageFactory;
import javaxx.sip.message.Request;
import android.os.AsyncTask;

public class SipRegister extends AsyncTask<Object, Object, Object> 
		 {
	
	

	/*private String localIp = "10.0.3.15";

	private int localPort = 5080;
	private String localEndpoint = localIp + ":" + localPort;
	private String transport = "udp";

	private String remoteIp;// = "23.23.228.238";
	private int remotePort = 5080;
	private String remoteEndpoint = remoteIp + ":" + remotePort;
	private String sipUsername;// = "alice";
*/
	public void register() throws Exception {

	

	}


	/*private void sendMessage(String to, String message) throws ParseException,
			InvalidArgumentException, SipException {

		SipURI from = addressFactory.createSipURI(sipUsername, localEndpoint);
		Address fromNameAddress = addressFactory.createAddress(from);
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress,
				"Tzt0ZEP92");
		URI toAddress = addressFactory.createURI(to);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);
		URI requestURI = addressFactory.createURI(to);
		ArrayList<ViaHeader> viaHeaders = createViaHeader();
		CallIdHeader callIdHeader = sipProvider.getNewCallId();
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(50l,
				Request.MESSAGE);
		MaxForwardsHeader maxForwards = headerFactory
				.createMaxForwardsHeader(70);
		Request request = messageFactory.createRequest(requestURI,
				Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
				toHeader, viaHeaders, maxForwards);
		SupportedHeader supportedHeader = headerFactory
				.createSupportedHeader("replaces, outbound");
		request.addHeader(supportedHeader);
		SipURI routeUri = addressFactory.createSipURI(null, remoteIp);
		routeUri.setTransportParam(transport);
		routeUri.setLrParam();
		routeUri.setPort(remotePort);
		Address routeAddress = addressFactory.createAddress(routeUri);
		RouteHeader route = headerFactory.createRouteHeader(routeAddress);
		request.addHeader(route);
		ContentTypeHeader contentTypeHeader = headerFactory
				.createContentTypeHeader("text", "plain");
		request.setContent(message, contentTypeHeader);
		System.out.println(request);
		ClientTransaction transaction = this.sipProvider
				.getNewClientTransaction(request);
		// Send the request statefully, through the client transaction.
		transaction.sendRequest();
	}
*/


	public void processIOException(IOExceptionEvent exceptionEvent) {
		System.out.println("IOException happened for "
				+ exceptionEvent.getHost() + " port = "
				+ exceptionEvent.getPort());

	}

	public void processTransactionTerminated(
			TransactionTerminatedEvent transactionTerminatedEvent) {
		System.out.println("Transaction terminated event recieved");
	}

	public void processDialogTerminated(
			DialogTerminatedEvent dialogTerminatedEvent) {
		System.out.println("dialogTerminatedEvent");

	}

	public void processTimeout(TimeoutEvent timeoutEvent) {

		System.out.println("Transaction Time out");
	}

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
					+ SipStackAndroid.sipUserName + "@" + SipStackAndroid.remoteIp);
			fromAddress.setDisplayName(SipStackAndroid.sipUserName);
			Address toAddress = addressFactory.createAddress("sip:"
					+ SipStackAndroid.sipUserName + "@" + SipStackAndroid.remoteIp);
			toAddress.setDisplayName(SipStackAndroid.sipUserName);

			Address contactAddress = createContactAddress();
			ArrayList<ViaHeader> viaHeaders = createViaHeader();
			URI requestURI = addressFactory.createAddress(
					"sip:" + SipStackAndroid.remoteEndpoint).getURI();
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
			return SipStackAndroid.addressFactory.createAddress("sip:" + SipStackAndroid.sipUserName + "@"
					+ SipStackAndroid.localEndpoint + ";transport=udp"
					+ ";registering_acc=23_23_228_238");
		} catch (ParseException e) {
			return null;
		}
	}

	private ArrayList<ViaHeader> createViaHeader() {
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader myViaHeader;
		try {
			SipStackAndroid.getInstance();
			myViaHeader = SipStackAndroid.headerFactory.createViaHeader(SipStackAndroid.localIp, SipStackAndroid.localPort,
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
	
/*
	@Override
	public void processRequest(RequestEvent arg0) {
		System.out.println("Request Received");
	}

	@Override
	public void processResponse(ResponseEvent arg0) {
		Response response = (Response) arg0.getResponse();
		ClientTransaction tid = arg0.getClientTransaction();
		System.out.println(response.getStatusCode());
		if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED
				|| response.getStatusCode() == Response.UNAUTHORIZED) {
			AuthenticationHelper authenticationHelper = ((SipStackExt) sipStack)
					.getAuthenticationHelper(new AccountManagerImpl(),
							headerFactory);
			try {
				inviteTid = authenticationHelper.handleChallenge(response, tid,
						sipProvider, 5);
				inviteTid.sendRequest();
			} catch (NullPointerException e) {
				e.printStackTrace();
			} catch (SipException e) {
				e.printStackTrace();
			}
			invco++;
		} 
		/*else {
			try {
				//sendMessage("sip:bob@23.23.228.238", "hello from android");
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}*/

	@Override
	protected Object doInBackground(Object... params) {
		try {
			String sipUsername = (String) params[0];
			String sipPassword = (String) params[1];
			String sipDomain = (String) params[2];
			
			SipStackAndroid.getInstance().sipUserName = sipUsername;
			SipStackAndroid.getInstance().sipPassword = sipPassword;
			SipStackAndroid.getInstance();
			SipStackAndroid.remoteIp = sipDomain;
		
		
			send_register();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}