package com.example.sipmessagetest;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.http.conn.util.InetAddressUtils;

import gov.nist.android.core.InternalErrorHandler;
import gov.nist.android.javaxx.sip.SipStackExt;
import gov.nist.android.javaxx.sip.clientauthutils.AuthenticationHelper;
import gov.nist.android.javaxx.sip.message.SIPMessage;

import android.os.AsyncTask;
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
import javaxx.sip.address.AddressFactory;
import javaxx.sip.header.ContactHeader;
import javaxx.sip.header.HeaderFactory;
import javaxx.sip.message.MessageFactory;
import javaxx.sip.message.Request;
import javaxx.sip.message.Response;

public class SipStackAndroid extends AsyncTask<Object, Object, Object>
		implements SipListener {
	private static SipStackAndroid instance = null;
	public static SipStack sipStack;
	public static SipProvider sipProvider;
	public static HeaderFactory headerFactory;
	public static AddressFactory addressFactory;
	public static MessageFactory messageFactory;
	public static SipFactory sipFactory;

	public static ListeningPoint udpListeningPoint;
	
	public static String localIp;// = "10.0.3.15";
	public static int localPort = 5080;
	public static String localEndpoint = localIp + ":" + localPort;
	public static String transport = "udp";

	public static String remoteIp = "23.23.228.238";
	public static int remotePort = 5080;
	public static String remoteEndpoint = remoteIp + ":" + remotePort;
	public static String sipUserName;
	public String sipPassword;
	
	
	private ArrayList<ISipEventListener> sipEventListenerList = new ArrayList<ISipEventListener>();
	public synchronized void addSipListener(ISipEventListener listener) {
		if (!sipEventListenerList.contains(listener)) {
			sipEventListenerList.add(listener);
		}
	}
	private void dispatchSipEvent(SipEvent sipEvent) {
		ArrayList<ISipEventListener> tmpSipListenerList;

		synchronized (this) {
			if (sipEventListenerList.size() == 0)
				return;
			tmpSipListenerList = (ArrayList<ISipEventListener>) sipEventListenerList.clone();
		}

		for (ISipEventListener listener : tmpSipListenerList) {
			listener.onSipMessage(sipEvent);
		}
	}

	protected SipStackAndroid() {
		initialize();
	}

	public static SipStackAndroid getInstance() {
		if (instance == null) {
			instance = new SipStackAndroid();

		}
		// initialize();
		return instance;
	}

	private static void initialize() {
		localIp = getIPAddress(true);
		localEndpoint = localIp + ":" + localPort;
		remoteEndpoint = remoteIp + ":" + remotePort;
		sipStack = null;
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist.android");

		Properties properties = new Properties();
		properties.setProperty("javaxx.sip.OUTBOUND_PROXY", remoteEndpoint + "/"
				+ transport);
		properties.setProperty("javaxx.sip.STACK_NAME", "androidSip");

		try {
			// Create SipStack object
			sipStack = sipFactory.createSipStack(properties);
			System.out.println("createSipStack " + sipStack);
		} catch (PeerUnavailableException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(0);
		}
		try {
			headerFactory = sipFactory.createHeaderFactory();
			addressFactory = sipFactory.createAddressFactory();
			messageFactory = sipFactory.createMessageFactory();
			udpListeningPoint = sipStack.createListeningPoint(localIp,
					localPort, transport);
			sipProvider = sipStack.createSipProvider(udpListeningPoint);
			sipProvider.addSipListener(SipStackAndroid.getInstance());
			// this.send_register();
		} catch (PeerUnavailableException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(0);
		} catch (Exception e) {
			System.out.println("Creating Listener Points");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	protected Object doInBackground(Object... params) {

		initialize();
		return null;
	}



	@Override
	public void processRequest(RequestEvent arg0) {
		sendOk(arg0);
		Request request = (Request) arg0.getRequest();

		System.out.println(request.getMethod());
		if(request.getMethod().equals("MESSAGE")){
			SIPMessage sp = (SIPMessage)request;
			try {
				String message = sp.getMessageContent();
				//System.out.println(sp.getFrom().getAddress());
				dispatchSipEvent(new SipEvent(this, "MESSAGE", message,sp.getFrom().getAddress().toString()));
				
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			

			
		}
	}
	public void sendOk(RequestEvent requestEvt){
		Response response;
		try {
			response = messageFactory.createResponse(
					200, requestEvt.getRequest());
			ServerTransaction serverTransaction = requestEvt.getServerTransaction();
			if (serverTransaction == null) { 
		        serverTransaction = sipProvider.getNewServerTransaction(requestEvt.getRequest()); 
			}
			serverTransaction.sendResponse(response);
			
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SipException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
		}
	
	}
	
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
				ClientTransaction inviteTid = authenticationHelper
						.handleChallenge(response, tid, sipProvider, 5,true);
				inviteTid.sendRequest();
			} catch (NullPointerException e) {
				e.printStackTrace();
			} catch (SipException e) {
				e.printStackTrace();
			}
			
		}

	}

	
	public static String getIPAddress(boolean useIPv4) {
		try {
			List<NetworkInterface> interfaces = Collections
					.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				List<InetAddress> addrs = Collections.list(intf
						.getInetAddresses());
				for (InetAddress addr : addrs) {
					if (!addr.isLoopbackAddress()) {
						String sAddr = addr.getHostAddress().toUpperCase();
						boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
						if (useIPv4) {
							if (isIPv4)
								return sAddr;
						} else {
							if (!isIPv4) {
								int delim = sAddr.indexOf('%'); // drop ip6 port
																// suffix
								return delim < 0 ? sAddr : sAddr.substring(0,
										delim);
							}
						}
					}
				}
			}
		} catch (Exception ex) {
		} // for now eat exceptions
		return "";
	}

}