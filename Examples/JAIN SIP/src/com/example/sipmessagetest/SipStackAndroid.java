package com.example.sipmessagetest;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.apache.http.conn.util.InetAddressUtils;

import com.example.sipmessagetest.SipEvent.SipEventType;

import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.SipStackImpl;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.gov.nist.javax.sip.header.extensions.JoinHeader;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.os.AsyncTask;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogState;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionAlreadyExistsException;
import android.javax.sip.TransactionDoesNotExistException;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

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
	
	private static String localIp;// = "10.0.3.15";
	private static int localPort = 5080;
	//private static String localEndpoint = localIp + ":" + localPort;
	public static String transport = "udp";

	private static String remoteIp = "23.23.228.238";
	private static int remotePort = 5060;
	//private static String remoteEndpoint = remoteIp + ":" + remotePort;
	public static String getLocalIp() {
		return localIp;
	}
	public static void setLocalIp(String localIp) {
		SipStackAndroid.localIp = localIp;
	}
	public static int getLocalPort() {
		return localPort;
	}
	public static void setLocalPort(int localPort) {
		SipStackAndroid.localPort = localPort;
	}
	public static String getLocalEndpoint() {
		return localIp + ":" + localPort;
	}
	
	public static String getRemoteIp() {
		return remoteIp;
	}
	public static void setRemoteIp(String remoteIp) {
		SipStackAndroid.remoteIp = remoteIp;
	}
	public static int getRemotePort() {
		return remotePort;
	}
	public static void setRemotePort(int remotePort) {
		SipStackAndroid.remotePort = remotePort;
	}
	public static String getRemoteEndpoint() {
		return remoteIp + ":" + remotePort;
	}
	

	public static String sipUserName;
	public static String sipPassword;
	private Dialog dialog;
	// Save the created ACK request, to respond to retransmitted 2xx
    private Request ackRequest;
    private boolean ackReceived;
	
	
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
		
	}

	public static SipStackAndroid getInstance() {
		if (instance == null) {
			instance = new SipStackAndroid();

		}
		// initialize();
		return instance;
	}

	private static void initialize() {
		
		setLocalIp(getIPAddress(true));
		
		sipStack = null;
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("android.gov.nist");

		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.OUTBOUND_PROXY", getRemoteEndpoint() + "/"
				+ transport);
		properties.setProperty("android.javax.sip.STACK_NAME", "androidSip");

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
		String sipUsername = (String) params[0];
		String sippassword = (String) params[1];
		String sipDomain = (String) params[2];

		sipUserName = sipUsername;
		sipPassword = sippassword;
		setRemoteIp(sipDomain);
		initialize();
		return null;
	}



	@Override
	public void processRequest(RequestEvent arg0) {
		//sendOk(arg0);
		Request request = (Request) arg0.getRequest();
		ServerTransaction serverTransactionId = arg0
				.getServerTransaction();
		SIPMessage sp = (SIPMessage)request;
		System.out.println(request.getMethod());
		if(request.getMethod().equals("MESSAGE")){
			sendOk(arg0);
		

			try {
				String message = sp.getMessageContent();

				//System.out.println(sp.getFrom().getAddress());
				dispatchSipEvent(new SipEvent(this,SipEventType.MESSAGE, message,sp.getFrom().getAddress().toString()));
				
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else if (request.getMethod().equals(Request.BYE)) {
			processBye(request, serverTransactionId);
			dispatchSipEvent(new SipEvent(this,SipEventType.BYE,"",sp.getFrom().getAddress().toString() ));

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
		Dialog responseDialog = null;
		ClientTransaction tid = arg0.getClientTransaction();
		if(tid != null) {
			responseDialog = tid.getDialog();
		} else {
			responseDialog = arg0.getDialog();
		}
		 CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
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
		else if (response.getStatusCode() == Response.OK){
			 if (cseq.getMethod().equals(Request.INVITE)) {
					
                 System.out.println("Dialog after 200 OK  " + dialog);
                 //System.out.println("Dialog State after 200 OK  " + dialog.getState());
                 try {
                	 Request ackRequest = responseDialog.createAck(cseq.getSeqNumber());
                	  System.out.println("Sending ACK");
                	 responseDialog.sendAck(ackRequest);
				} catch (InvalidArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SipException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
               
               
                 // JvB: test REFER, reported bug in tag handling
                 // dialog.sendRequest(  sipProvider.getNewClientTransaction( dialog.createRequest("REFER") ));

             } else if (cseq.getMethod().equals(Request.CANCEL)) {
                 if (dialog.getState() == DialogState.CONFIRMED) {
                     // oops cancel went in too late. Need to hang up the
                     // dialog.
                     System.out
                             .println("Sending BYE -- cancel went in too late !!");
                     Request byeRequest = null;
					try {
						byeRequest = dialog.createRequest(Request.BYE);
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                     ClientTransaction ct = null;
					try {
						ct = sipProvider
						         .getNewClientTransaction(byeRequest);
					} catch (TransactionUnavailableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                     try {
						dialog.sendRequest(ct);
					} catch (TransactionDoesNotExistException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

                 }

             }
		}
		

	}



	

	
	  public void processBye(Request request,
	            ServerTransaction serverTransactionId) {
	        try {
	            System.out.println("shootist:  got a bye .");
	            if (serverTransactionId == null) {
	                System.out.println("shootist:  null TID.");
	                return;
	            }
	            Dialog dialog = serverTransactionId.getDialog();
	            System.out.println("Dialog State = " + dialog.getState());
	            Response response = messageFactory.createResponse(200, request);
	            serverTransactionId.sendResponse(response);
	            System.out.println("shootist:  Sending OK.");
	            System.out.println("Dialog State = " + dialog.getState());

	        } catch (Exception ex) {
	            ex.printStackTrace();
	            System.exit(0);

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
	public int ackCount = 0;




}