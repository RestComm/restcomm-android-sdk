package org.mobicents.restcomm.android.sipua.impl;

import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TooManyListenersException;

import org.apache.http.conn.util.InetAddressUtils;
import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.sipua.ISipEventListener;
import org.mobicents.restcomm.android.sipua.ISipManager;
import org.mobicents.restcomm.android.sipua.NotInitializedException;
import org.mobicents.restcomm.android.sipua.SipManagerState;
import org.mobicents.restcomm.android.sipua.impl.SipEvent.SipEventType;
import org.mobicents.restcomm.android.sipua.impl.sipmessages.Invite;
import org.mobicents.restcomm.android.sipua.impl.sipmessages.Message;
import org.mobicents.restcomm.android.sipua.impl.sipmessages.*;
import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.RCLogger;

import android.gov.nist.javax.sdp.SessionDescriptionImpl;
import android.gov.nist.javax.sdp.parser.SDPAnnounceParser;
import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.SdpException;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogState;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.Transaction;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionDoesNotExistException;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.UserAgentHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.text.format.Formatter;
import android.content.Context;

import android.util.Log;

import EDU.oswego.cs.dl.util.concurrent.FJTask;

public class SipManager implements SipListener, ISipManager, Serializable {
	enum CallDirection {
		NONE,
		INCOMING,
		OUTGOING,
	};

	/**
	 * Device capability (<b>Not Implemented yet</b>)
	 */
	public enum NetworkInterfaceType {
		NetworkInterfaceTypeWifi,
		NetworkInterfaceTypeCellularData,
	}

	private static SipStack sipStack;
	public static String USERAGENT_STRING = "TelScale Restcomm Android Client 1.0.0 BETA3";
	public SipProvider sipProvider;
	public HeaderFactory headerFactory;
	public AddressFactory addressFactory;
	public MessageFactory messageFactory;
	public SipFactory sipFactory;
	private static final String TAG = "SipManager";

	private ListeningPoint listeningPoint;
	private SipProfile sipProfile;
	private Dialog dialog;

	private ArrayList<ISipEventListener> sipEventListenerList = new ArrayList<ISipEventListener>();
	private boolean initialized = false;
	private SipManagerState sipManagerState;
    private HashMap<String,String> customHeaders;
	private ClientTransaction currentClientTransaction = null;
	private ServerTransaction currentServerTransaction;
	private static final int MAX_AUTH_ATTEMPTS = 3;
	HashMap<String, Integer> authenticationMap = new HashMap<>();
	// Is it an outgoing call or incoming call. We're using this so that when we hit
	// hangup we know which transaction to use, the client or the server (maybe we
	// could also use dialog.isServer() flag but have found mixed opinions about it)
	CallDirection direction = CallDirection.NONE;
	private int remoteRtpPort;
	static private Context androidContext;

	// Constructors/Initializers
	public SipManager(SipProfile sipProfile, boolean connectivity, NetworkInterfaceType networkInterfaceType, Context context) {
		RCLogger.v(TAG, "SipManager()");

		this.sipProfile = sipProfile;
		initialize(connectivity, networkInterfaceType, context);
	}

	private boolean initialize(boolean connectivity, NetworkInterfaceType networkInterfaceType, Context context)
	{
		RCLogger.v(TAG, "initialize()");

		androidContext = context;
		sipManagerState = SipManagerState.REGISTERING;

		sipFactory = SipFactory.getInstance();
		sipFactory.resetFactory();
		sipFactory.setPathName("android.gov.nist");

		Properties properties = new Properties();
		// Using ROUTE instead
		/*
		if (!sipProfile.getRemoteIp().isEmpty()) {
			properties.setProperty("android.javax.sip.OUTBOUND_PROXY",
					sipProfile.getRemoteEndpoint() + "/" + sipProfile.getTransport());
		}
		*/
		properties.setProperty("android.javax.sip.STACK_NAME", "androidSip");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		//properties.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL", "32");
		//File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		//properties.setProperty("android.gov.nist.javax.sip.DEBUG_LOG", downloadPath.getAbsolutePath() + "/debug-jain.log");
		//properties.setProperty("android.gov.nist.javax.sip.SERVER_LOG", downloadPath.getAbsolutePath() + "/server-jain.log");

		// old code, just in case we need the path
		//properties.setProperty("android.gov.nist.javax.sip.DEBUG_LOG", "/mnt/sdcard/Download/debug-jain.log");
		//properties.setProperty("android.gov.nist.javax.sip.SERVER_LOG", "/mnt/sdcard/Download/server-jain.log");

		try {
			if (listeningPoint != null) {
				// Binding again
				sipStack.deleteListeningPoint(listeningPoint);
				sipProvider.removeSipListener(this);
				listeningPoint = null;
			}
			sipStack = sipFactory.createSipStack(properties);
			RCLogger.i(TAG, "createSipStack " + sipStack);
			headerFactory = sipFactory.createHeaderFactory();
			addressFactory = sipFactory.createAddressFactory();
			messageFactory = sipFactory.createMessageFactory();

			if (connectivity) {
				bind(networkInterfaceType);
			}

			initialized = true;
			sipManagerState = SipManagerState.READY;
		} catch (PeerUnavailableException e) {
			return false;
		} catch (ObjectInUseException e) {
			e.printStackTrace();
			return false;
		}

		// This is not needed afterall since all error reporting ends up on the main thread. So we can freely call dispatchError from a background thread's
		// catch block without issues. I'm keeping it as a comment cause it's an interesting pattern we might need to use in the future.
		// Keep in mind that for this to work the background thread needs to throw a RuntimeException (doesn't work for other)
		// Also, part from the code below we need to call thread.setUncaughtExceptionHandler(exceptionHandler) in the background thread prior to
		// running it
		/*
		// setup a handler to receive exceptions from other threads
		// Notice that this only works for RuntimeException type exceptions
		exceptionHandler = new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread th, Throwable e) {
				// this currently can only be called as a result of a failed REGISTER unREGISTER due to the fact that the provided
				// host/port for registrar are invalid. In the future we could use the RuntimeException text to differentiate errors
				// or use another means of thread communication to convey the error from JAIN thread to main thread.
				if (e.getMessage().equals("Register failed")) {
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_DNS_ERROR,
							"Error resolving destination SIP URI for REGISTER");
				}
				else {
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_CALL, RCClient.ErrorCodes.SIGNALLING_DNS_ERROR,
							"Error resolving destination SIP URI for REGISTER");
				}
			}
		};
		*/

		return true;
	}

	// shutdown SIP stack
	public boolean shutdown()
	{
		RCLogger.v(TAG, "shutdown");

		if (sipManagerState != SipManagerState.STACK_STOPPED) {
			RCLogger.v(TAG, "shutdown while stack is started");
			unbind();
			sipStack.stop();
			sipManagerState = SipManagerState.STACK_STOPPED;
		}

		return true;
	}

	// release JAIN networking facilities
	public void unbind()
	{
		RCLogger.w(TAG, "unbind()");
		if (listeningPoint != null) {
			try {
				sipProvider.removeSipListener(this);
				ListeningPoint[] listeningPoints = sipProvider.getListeningPoints();
				RCLogger.w(TAG, "unbind(): listening point count: " + listeningPoints.length);
				sipStack.deleteSipProvider(sipProvider);
				sipStack.deleteListeningPoint(listeningPoint);

				listeningPoint = null;
			} catch (ObjectInUseException e) {
				e.printStackTrace();
			}
		}
	}

	// setup JAIN networking facilities
	public void bind(NetworkInterfaceType networkInterfaceType)
	{
		RCLogger.w(TAG, "bind()");
		if (listeningPoint == null) {
			// new network interface is up, let's retrieve its ip address
			this.sipProfile.setLocalIp(getIPAddress(true, networkInterfaceType));
			try {
				RCLogger.v(TAG, "Binding to: " +
						sipProfile.getTransport() + ":" +
						sipProfile.getLocalIp() + ":" +
						sipProfile.getLocalPort());
				listeningPoint = sipStack.createListeningPoint(
						sipProfile.getLocalIp(), sipProfile.getLocalPort(),
						sipProfile.getTransport());
				sipProvider = sipStack.createSipProvider(listeningPoint);
				sipProvider.addSipListener(this);
			} catch (TransportNotSupportedException e) {
				e.printStackTrace();
			} catch (InvalidArgumentException e) {
				e.printStackTrace();
			} catch (ObjectInUseException e) {
				e.printStackTrace();
			} catch (TooManyListenersException e) {
				e.printStackTrace();
			}
		}
	}

	public void refreshNetworking(int expiry, NetworkInterfaceType networkInterfaceType) throws ParseException, TransactionUnavailableException
	{
		RCLogger.v(TAG, "refreshNetworking()");

		// keep the old contact around to use for unregistration
		Address oldAddress = createContactAddress();

		unbind();
		bind(networkInterfaceType);

		// unregister the old contact (keep in mind that the new interface will be used to send this request)
		Unregister(oldAddress);
		// register the new contact with the given expiry
		Register(expiry);
	}

	// *** Setters/Getters *** //
	public boolean isInitialized() {
		return initialized;
	}

	public SipProfile getSipProfile() {

		return sipProfile;
	}

	public void setSipProfile(SipProfile sipProfile) {
		this.sipProfile = sipProfile;
	}

	public SipManagerState getSipManagerState() {
		return sipManagerState;
	}

	public HashMap<String, String> getCustomHeaders() {
		return customHeaders;
	}

	public void setCustomHeaders(HashMap<String, String> customHeaders) {
		this.customHeaders = customHeaders;
	}

	// *** Client API (used by DeviceImpl) *** //
	// Accept incoming call
	public void AcceptCall(final int port) {
		RCLogger.v(TAG, "AcceptCall()");

		if (currentServerTransaction == null)
			return;
		Thread thread = new Thread() {
			public void run() {
				try {
					SIPMessage sm = (SIPMessage) currentServerTransaction
							.getRequest();
					Response responseOK = messageFactory.createResponse(
							Response.OK, currentServerTransaction.getRequest());
					Address address = createContactAddress();
					ContactHeader contactHeader = headerFactory
							.createContactHeader(address);
					responseOK.addHeader(contactHeader);
					ToHeader toHeader = (ToHeader) responseOK
							.getHeader(ToHeader.NAME);
					toHeader.setTag("4321"); // Application is supposed to set.
					responseOK.addHeader(contactHeader);


					String sdpData = "v=0\r\n"
							+ "o=4855 13760799956958020 13760799956958020"
							+ " IN IP4 " + sipProfile.getLocalIp() + "\r\n"
							+ "s=mysession session\r\n"
							+ "p=+46 8 52018010\r\n" + "c=IN IP4 "
							+ sipProfile.getLocalIp() + "\r\n" + "t=0 0\r\n"
							+ "m=audio " + String.valueOf(port)
							+ " RTP/AVP 0 4 18\r\n"
							+ "a=rtpmap:0 PCMU/8000\r\n"
							+ "a=rtpmap:4 G723/8000\r\n"
							+ "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
					byte[] contents = sdpData.getBytes();

					ContentTypeHeader contentTypeHeader = headerFactory
							.createContentTypeHeader("application", "sdp");
					responseOK.setContent(contents, contentTypeHeader);

					RCLogger.v(TAG, "Sending SIP response: \n" + responseOK.toString());
					currentServerTransaction.sendResponse(responseOK);
					dispatchSipEvent(new SipEvent(this,
							SipEventType.CALL_CONNECTED, "", sm.getFrom()
							.getAddress().toString(), remoteRtpPort, ""));
					sipManagerState = SipManagerState.ESTABLISHED;
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (SipException e) {
					e.printStackTrace();
				} catch (InvalidArgumentException e) {
					e.printStackTrace();
				}
			}
		};
		thread.start();
		sipManagerState = SipManagerState.ESTABLISHED;
	}

	public void AcceptCallWebrtc(final String sdp) {
		RCLogger.v(TAG, "AcceptCallWebrtc()");

		if (currentServerTransaction == null)
			return;
		Thread thread = new Thread() {
			public void run() {
				try {
					SIPMessage sm = (SIPMessage) currentServerTransaction
							.getRequest();
					Response responseOK = messageFactory.createResponse(
							Response.OK, currentServerTransaction.getRequest());
					Address address = createContactAddress();
					ContactHeader contactHeader = headerFactory
							.createContactHeader(address);
					responseOK.addHeader(contactHeader);
					ToHeader toHeader = (ToHeader) responseOK
							.getHeader(ToHeader.NAME);
					toHeader.setTag("4321"); // Application is supposed to set.
					responseOK.addHeader(contactHeader);

					byte[] contents = sdp.getBytes();

					ContentTypeHeader contentTypeHeader = headerFactory
							.createContentTypeHeader("application", "sdp");
					responseOK.setContent(contents, contentTypeHeader);

					RCLogger.v(TAG, "Sending SIP response: \n" + responseOK.toString());
					currentServerTransaction.sendResponse(responseOK);
					dispatchSipEvent(new SipEvent(this,
							SipEventType.CALL_CONNECTED, "", sm.getFrom()
							.getAddress().toString(), remoteRtpPort, ""));
					sipManagerState = SipManagerState.ESTABLISHED;
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (SipException e) {
					e.printStackTrace();
				} catch (InvalidArgumentException e) {
					e.printStackTrace();
				}
			}
		};
		thread.start();
		sipManagerState = SipManagerState.ESTABLISHED;
	}

	public void RejectCall() {
		RCLogger.v(TAG, "RejectCall()");

		sendDecline(currentServerTransaction.getRequest());
		sipManagerState = SipManagerState.IDLE;
	}

	@Override
	public void Register(int expiry) throws ParseException, TransactionUnavailableException {
		RCLogger.v(TAG, "Register()");
		if (sipProvider == null) {
			return;
		}
		Register registerRequest = new Register();
		try {
			final Request r = registerRequest.MakeRequest(this, expiry, null);
			RCLogger.v(TAG, "Sending SIP request: \n" + r.toString());

			final SipProvider sipProvider = this.sipProvider;
			// Send the request statefully, through the client transaction.
			Thread thread = new Thread() {
				public void run() {
					try {
						final ClientTransaction transaction = sipProvider.getNewClientTransaction(r);
						transaction.sendRequest();
					} catch (Exception e) {
						// DNS error (error resolving registrar URI)
						dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_REGISTER_ERROR,
								e.getMessage());
					}
				}
			};
			thread.start();

		} catch (ParseException e) {
			throw e;
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	public void Unregister(Address contact) throws ParseException, TransactionUnavailableException {
		RCLogger.v(TAG, "Unregister()");
		if (sipProvider == null) {
			return;
		}

		Register registerRequest = new Register();
		try {
			final Request r = registerRequest.MakeRequest(this, 0, contact);
			RCLogger.v(TAG, "Sending SIP request: \n" + r.toString());
			final SipProvider sipProvider = this.sipProvider;
			// Send the request statefully, through the client transaction.
			Thread thread = new Thread() {
				public void run() {
					try {
						final ClientTransaction transaction = sipProvider.getNewClientTransaction(r);
						transaction.sendRequest();
					} catch (Exception e) {
						// DNS error (error resolving registrar URI)
						dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_REGISTER_ERROR,
								e.getMessage());
					}
				}
			};
			thread.start();

		} catch (ParseException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void Call(String to, int localRtpPort, HashMap<String, String> sipHeaders)
			throws NotInitializedException, ParseException {
		RCLogger.v(TAG, "Call()");
		if (!initialized)
			throw new NotInitializedException("Sip Stack not initialized");
		this.sipManagerState = SipManagerState.CALLING;
		Invite inviteRequest = new Invite();
		final Request r = inviteRequest.MakeRequest(this, to, localRtpPort, sipHeaders);
		RCLogger.v(TAG, "Sending SIP request: \n" + r.toString());
		final SipProvider sipProvider = this.sipProvider;
		Thread thread = new Thread() {
			public void run() {
				try {
					final ClientTransaction transaction = sipProvider.getNewClientTransaction(r);
					// note: we might need to make this 'syncrhonized' to avoid race at some point
					currentClientTransaction = transaction;
					transaction.sendRequest();
					dialog = transaction.getDialog();
				} catch (Exception e) {
					// DNS error (error resolving registrar URI)
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_CALL, RCClient.ErrorCodes.SIGNALLING_CALL_ERROR,
							e.getMessage());
				}
			}
		};
		thread.start();
		direction = CallDirection.OUTGOING;
	}

	public void CallWebrtc(String to, String sdp, HashMap<String, String> sipHeaders)
			throws NotInitializedException, ParseException {
		RCLogger.v(TAG, "CallWebrtc()");
		if (!initialized)
			throw new NotInitializedException("Sip Stack not initialized");
		this.sipManagerState = SipManagerState.CALLING;
		Invite inviteRequest = new Invite();
		final Request r = inviteRequest.MakeRequestWebrtc(this, to, sdp, sipHeaders);
		RCLogger.v(TAG, "Sending SIP request: \n" + r.toString());
		final SipProvider sipProvider = this.sipProvider;
		Thread thread = new Thread() {
			public void run() {
				try {
					final ClientTransaction transaction = sipProvider.getNewClientTransaction(r);
					// note: we might need to make this 'syncrhonized' to avoid race at some point
					currentClientTransaction = transaction;
					transaction.sendRequest();
					dialog = transaction.getDialog();
				} catch (Exception e) {
					// DNS error (error resolving registrar URI)
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_CALL, RCClient.ErrorCodes.SIGNALLING_CALL_ERROR,
							e.getMessage());
				}
			}
		};
		thread.start();
		direction = CallDirection.OUTGOING;
	}

	@Override
	public void SendMessage(String to, String message)
			throws NotInitializedException {
		RCLogger.v(TAG, "SendMessage()");
		if (!initialized)
			throw new NotInitializedException("Sip Stack not initialized");
		Message inviteRequest = new Message();
		try {
			final Request r = inviteRequest.MakeRequest(this, to, message);
			RCLogger.v(TAG, "Sending SIP request: \n" + r.toString());
			final SipProvider sipProvider = this.sipProvider;
			Thread thread = new Thread() {
				public void run() {
					try {
						// note: we might need to make this 'syncrhonized' to avoid race at some point
						ClientTransaction transaction = sipProvider.getNewClientTransaction(r);
						transaction.sendRequest();
					} catch (SipException e) {
						// DNS error (error resolving registrar URI)
						dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_INSTANT_MESSAGE_ERROR,
								e.getMessage());
					}
				}
			};
			thread.start();
		} catch (ParseException e1) {
			e1.printStackTrace();
		} catch (InvalidArgumentException e1) {
			e1.printStackTrace();
		}

	}

	@Override
	public void Hangup() throws NotInitializedException
	{
		RCLogger.v(TAG, "Hangup()");
		if (!initialized)
			throw new NotInitializedException("Sip Stack not initialized");

		if (direction == CallDirection.OUTGOING) {
			if (currentClientTransaction != null) {
				sendByeClient(currentClientTransaction);
			}
		}
		else if (direction == CallDirection.INCOMING) {
			if (currentServerTransaction != null) {
				sendByeClient(currentServerTransaction);
			}
		}
	}

	public void Cancel() throws NotInitializedException
	{
		RCLogger.v(TAG, "Cancel");
		if (!initialized)
			throw new NotInitializedException("Sip Stack not initialized");

		if (direction == CallDirection.OUTGOING) {
			if (currentClientTransaction != null) {
				sendCancel(currentClientTransaction);
				sipManagerState = SipManagerState.IDLE;
			}
		}
	}

	@Override
	public void SendDTMF(String digit) throws NotInitializedException {
		RCLogger.v(TAG, "SendDTMF()");
		if (!initialized)
			throw new NotInitializedException("Sip Stack not initialized");

		Transaction transaction = null;
		if (direction == CallDirection.OUTGOING) {
			if (currentClientTransaction != null) {
				transaction = currentClientTransaction;
			}
		}
		else if (direction == CallDirection.INCOMING) {
			if (currentServerTransaction != null) {
				transaction = currentServerTransaction;
			}
		}

		if (transaction == null) {
			RCLogger.e(TAG, "Transaction is null");
			return;
		}

		final Dialog dialog = transaction.getDialog();
		if (dialog == null) {
			RCLogger.e(TAG, "Dialog is already terminated");
		}
		else {
			Request request = null;
			try {
				request = dialog.createRequest(Request.INFO);
				request.setContent("Signal=" + digit + "\r\nDuration=100\r\n",
						headerFactory.createContentTypeHeader("application","dtmf-relay"));
				RCLogger.v(TAG, "Sending SIP request: \n" + request.toString());
			} catch (SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}

			final Request r = request;

			Thread thread = new Thread() {
				public void run() {
					try {
						ClientTransaction ct = sipProvider.getNewClientTransaction(r);
						dialog.sendRequest(ct);
					} catch (TransactionUnavailableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (TransactionDoesNotExistException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			thread.start();
		}
	}

	// *** JAIN SIP: Incoming request *** //
	@Override
	public void processRequest(RequestEvent requestEvent) {
		//RCLogger.v(TAG, "processRequest()");
		Request request = requestEvent.getRequest();
		RCLogger.v(TAG, "Received SIP request: \n" + request.toString());
		ServerTransaction serverTransactionId = requestEvent.getServerTransaction();
		SIPMessage sp = (SIPMessage) request;
		RCLogger.i(TAG, request.getMethod());
		if (request.getMethod().equals("MESSAGE")) {
			sendOk(requestEvent);

			try {
				String message = sp.getMessageContent();
				dispatchSipEvent(new SipEvent(this, SipEventType.MESSAGE,
						message, sp.getFrom().getAddress().toString()));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} else if (request.getMethod().equals(Request.BYE)) {
			sipManagerState = SipManagerState.IDLE;
			incomingBye(request, serverTransactionId);
			dispatchSipEvent(new SipEvent(this, SipEventType.INCOMING_BYE_REQUEST, "", sp
					.getFrom().getAddress().toString()));
			direction = CallDirection.NONE;
		}
		if (request.getMethod().equals("INVITE")) {
			direction = CallDirection.INCOMING;
			incomingInvite(requestEvent, serverTransactionId);
		}
		if (request.getMethod().equals("CANCEL")) {
			sipManagerState = SipManagerState.IDLE;
			incomingCancel(request, serverTransactionId);
			dispatchSipEvent(new SipEvent(this, SipEventType.REMOTE_CANCEL, "", sp
					.getFrom().getAddress().toString()));
			direction = CallDirection.NONE;
		}
	}

	// *** JAIN SIP: Incoming response *** //
	@Override
	public void processResponse(ResponseEvent responseEvent) {
		ResponseEventExt responseEventExt = (ResponseEventExt)responseEvent;
		Response response = (Response) responseEvent.getResponse();
		RCLogger.v(TAG, "Received SIP response: \n" + response.toString());

		//RCLogger.i(TAG, "processResponse(), status code: " + response.getStatusCode());

		ClientTransaction tid = responseEvent.getClientTransaction();
		CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
		if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED
				|| response.getStatusCode() == Response.UNAUTHORIZED) {
			try {
				AuthenticationHelper authenticationHelper = ((SipStackExt) sipStack)
						.getAuthenticationHelper(
								new AccountManagerImpl(sipProfile.getSipAuthUserName(),
										responseEventExt.getRemoteIpAddress(), sipProfile
										.getSipPassword()), headerFactory);
				CallIdHeader callId = (CallIdHeader)response.getHeader("Call-ID");
				int attempts = 0;
				if (authenticationMap.containsKey(callId.toString())) {
					attempts = authenticationMap.get(callId.toString()).intValue();
				}

				// we 're subtracting one since the first attempt has already taken place
				// (that way we are enforcing MAX_AUTH_ATTEMPTS at most)
				if (attempts < MAX_AUTH_ATTEMPTS - 1) {
					ClientTransaction inviteTid = authenticationHelper
							.handleChallenge(response, tid, sipProvider, 5, true);
					currentClientTransaction = inviteTid;
					RCLogger.v(TAG, "Sending SIP request: \n" + inviteTid.getRequest().toString());

					inviteTid.sendRequest();
					if (cseq.getMethod().equals(Request.INVITE)) {
						// only update the dialog if we are responding to INVITE with new invite
						dialog = inviteTid.getDialog();
					}
					authenticationMap.put(callId.toString(), attempts + 1);
				}
				else {
					if (tid != null && tid.getRequest() != null) {
						dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_REGISTER_AUTH_ERROR,
								"Error authenticating " + tid.getRequest().getMethod());
					}
				}
			} catch (NullPointerException e) {
				e.printStackTrace();
			} catch (SipException e) {
				e.printStackTrace();
			}
		} else if (response.getStatusCode() == Response.OK) {
			if (cseq.getMethod().equals(Request.INVITE)) {
				//RCLogger.i(TAG, "Dialog after 200 OK  " + dialog);
				try {
					//RCLogger.i(TAG, "Sending ACK");
					Request ackRequest = dialog.createAck(((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getSeqNumber());
					RCLogger.v(TAG, "Sending SIP request: \n" + ackRequest.toString());
					dialog.sendAck(ackRequest);
					byte[] rawContent = response.getRawContent();
					String sdpContent = new String(rawContent, "UTF-8");
					SDPAnnounceParser parser = new SDPAnnounceParser(sdpContent);
					SessionDescriptionImpl sessiondescription = parser.parse();
					MediaDescription incomingMediaDescriptor = (MediaDescription) sessiondescription
							.getMediaDescriptions(false).get(0);
					int rtpPort = incomingMediaDescriptor.getMedia()
							.getMediaPort();

					// if its a webrtc call we need to send back the full SDP
					dispatchSipEvent(new SipEvent(this,
							SipEventType.CALL_CONNECTED, "", "", rtpPort, sdpContent));
				} catch (InvalidArgumentException e) {
					e.printStackTrace();
				} catch (SipException e) {
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (SdpException e) {
					e.printStackTrace();
				}

			}
			else if (cseq.getMethod().equals(Request.REGISTER)) {
				// we got 200 OK to register request, clear the map
				authenticationMap.clear();
				dispatchSipEvent(new SipEvent(this,
						SipEventType.REGISTER_SUCCESS, "", ""));

			}
			else if (cseq.getMethod().equals(Request.CANCEL)) {
				if (dialog.getState() == DialogState.CONFIRMED) {
					// oops cancel went in too late. Need to hang up the
					// dialog.
					RCLogger.i(TAG, "Sending BYE; CANCEL went in too late");
					Request byeRequest = null;
					try {
						byeRequest = dialog.createRequest(Request.BYE);

					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					ClientTransaction ct = null;
					try {
						ct = sipProvider.getNewClientTransaction(byeRequest);
					} catch (TransactionUnavailableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					try {
						RCLogger.v(TAG, "Sending SIP request: \n" + ct.getRequest().toString());
						dialog.sendRequest(ct);
					} catch (TransactionDoesNotExistException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

			} else if (cseq.getMethod().equals(Request.BYE)) {
				sipManagerState = SipManagerState.IDLE;
				//RCLogger.i(TAG, "--- Got 200 OK in UAC outgoing BYE");
				dispatchSipEvent(new SipEvent(this, SipEventType.INCOMING_BYE_RESPONSE, "", ""));
			}

		} else if (response.getStatusCode() == Response.DECLINE || response.getStatusCode() == Response.TEMPORARILY_UNAVAILABLE ||
				(response.getStatusCode() == Response.BUSY_HERE)) {
			//RCLogger.i(TAG, "CALL DECLINED");
			sipManagerState = SipManagerState.IDLE;
			dispatchSipEvent(new SipEvent(this, SipEventType.DECLINED, "", ""));
		} else if (response.getStatusCode() == Response.NOT_FOUND) {
			//RCLogger.i(TAG, "NOT FOUND");
			dispatchSipEvent(new SipEvent(this, SipEventType.NOT_FOUND, "Destination not found", response.getHeader(ToHeader.NAME).toString()));
		} else if (response.getStatusCode() == Response.ACCEPTED) {
			//RCLogger.i(TAG, "ACCEPTED");
		}
		else if (response.getStatusCode() == Response.RINGING) {
			//RCLogger.i(TAG, "RINGING");
			dispatchSipEvent(new SipEvent(this, SipEventType.REMOTE_RINGING, "", ""));
		} else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
			//RCLogger.i(TAG, "BUSY");
			dispatchSipEvent(new SipEvent(this,
					SipEventType.SERVICE_UNAVAILABLE, "", ""));
		} else if (response.getStatusCode() == Response.FORBIDDEN) {
			if (tid != null && tid.getRequest() != null) {
				String method;

				if (tid.getRequest().getMethod() != null) {
					method = tid.getRequest().getMethod();
				}
				else {
					method = "";
				}

				if (method.equals(Request.REGISTER)) {
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_REGISTER_AUTH_ERROR,
							"Error authenticating " + tid.getRequest().getMethod());
				}
				else if (method.equals(Request.MESSAGE)) {
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_INSTANT_MESSAGE_ERROR,
							"Error authenticating " + tid.getRequest().getMethod());
				}
				else if (method.equals(Request.INVITE)) {
					dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_CALL, RCClient.ErrorCodes.SIGNALLING_CALL_ERROR,
							"Error authenticating " + tid.getRequest().getMethod());
				}
			}
		}
	}

	// *** JAIN SIP: Exception *** //
	public void processIOException(IOExceptionEvent exceptionEvent) {
		RCLogger.e(TAG, "SipManager.processIOException: " + exceptionEvent.toString() + "\n" +
				"\thost: " + exceptionEvent.getHost() + "\n" +
				"\tport: " + exceptionEvent.getPort());
	}

	// *** JAIN SIP: Transaction terminated *** //
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
		RCLogger.i(TAG, "SipManager.processTransactionTerminated: " + transactionTerminatedEvent.toString() + "\n" +
				"\tclient transaction: " + transactionTerminatedEvent.getClientTransaction() + "\n" +
				"\tserver transaction: " + transactionTerminatedEvent.getServerTransaction() + "\n" +
				"\tisServerTransaction: " + transactionTerminatedEvent.isServerTransaction());
	}

	// *** JAIN SIP: Dialog terminated *** //
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
		RCLogger.i(TAG, "SipManager.processDialogTerminated: " + dialogTerminatedEvent.toString() + "\n" +
				"\tdialog: " + dialogTerminatedEvent.getDialog().toString());
	}

	// *** JAIN SIP: Time out *** //
	public void processTimeout(TimeoutEvent timeoutEvent) {
		Request request;
		if (timeoutEvent.isServerTransaction()) {
			request = timeoutEvent.getServerTransaction().getRequest();
		}
		else {
			request = timeoutEvent.getClientTransaction().getRequest();
		}
		RCLogger.i(TAG, "processTimeout(): method: " + request.getMethod() + " URI: " + request.getRequestURI());
		if (request.getMethod() == Request.INVITE) {
			dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_CALL, RCClient.ErrorCodes.SIGNALLING_TIMEOUT,
					"Timed out waiting on " + request.getMethod());
		}
		else {
			dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_TIMEOUT,
					"Timed out waiting on " + request.getMethod());
		}
	}

	// *** Request/Response Helpers *** //
	// TODO: revisit dispatchSipEvent and dispatchSipError. Not sure why we need an array of listeners, plus why such synchronization is needed.
	// We could probably simplify it to one listener and no synchronization (we always take take to only send callbacks to the main application from
	// the main thread)
	// Send event to the higher level listener (i.e. DeviceImpl)
	@SuppressWarnings("unchecked")
	private void dispatchSipEvent(SipEvent sipEvent) {
		RCLogger.i(TAG, "dispatchSipEvent():" + sipEvent.type);
		ArrayList<ISipEventListener> tmpSipListenerList;

		synchronized (this) {
			if (sipEventListenerList.size() == 0)
				return;
			tmpSipListenerList = (ArrayList<ISipEventListener>) sipEventListenerList
					.clone();
		}

		for (ISipEventListener listener : tmpSipListenerList) {
			listener.onSipMessage(sipEvent);
		}
	}

	// caller needs to run on main thread
	private void dispatchSipError(ISipEventListener.ErrorContext errorContext, RCClient.ErrorCodes errorCode, String errorText) {
		RCLogger.i(TAG, "dispatchSipError():" + errorText);
		ArrayList<ISipEventListener> tmpSipListenerList;

		synchronized (this) {
			if (sipEventListenerList.size() == 0)
				return;
			tmpSipListenerList = (ArrayList<ISipEventListener>) sipEventListenerList
					.clone();
		}

		for (ISipEventListener listener : tmpSipListenerList) {
			listener.onSipError(errorContext, errorCode, errorText);
		}
	}

	private void incomingBye(Request request,
							 ServerTransaction serverTransactionId) {
		RCLogger.i(TAG, "incomingBye()");
		try {
			if (serverTransactionId == null) {
				RCLogger.i(TAG, "shootist:  null TID.");
				return;
			}
			Dialog dialog = serverTransactionId.getDialog();
			RCLogger.i(TAG, "Dialog State = " + dialog.getState());
			Response response = messageFactory.createResponse(200, request);
			RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
			serverTransactionId.sendResponse(response);
			//RCLogger.i(TAG, "Sending OK");
			RCLogger.i(TAG, "Dialog State = " + dialog.getState());

		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);

		}
	}

	private void incomingInvite(RequestEvent requestEvent,
								ServerTransaction serverTransaction) {
		RCLogger.i(TAG, "incomingInvite()");

		if (sipManagerState != SipManagerState.IDLE
				&& sipManagerState != SipManagerState.READY
				&& sipManagerState != SipManagerState.INCOMING
				) {
			RCLogger.i(TAG, "incomingInvite(): invalid state: " + sipManagerState + " -bailing");
			// sendDecline(requestEvent.getRequest());// Already in a call
			return;
		}
		sipManagerState = SipManagerState.INCOMING;
		Request request = requestEvent.getRequest();
		SIPMessage sm = (SIPMessage) request;

		try {
			ServerTransaction st = requestEvent.getServerTransaction();

			if (st == null) {
				st = sipProvider.getNewServerTransaction(request);

			}
			if (st == null) {
				RCLogger.i(TAG, "incomingInvite(): server transaction still null");
				return;
			}
			dialog = st.getDialog();
			currentServerTransaction = st;

			//RCLogger.i(TAG, "INVITE: sending Trying");
			Response response = messageFactory.createResponse(Response.TRYING,
					request);
			RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
			st.sendResponse(response);
			//RCLogger.i(TAG, "INVITE:Trying Sent");

			byte[] rawContent = sm.getRawContent();
			String sdpContent = new String(rawContent, "UTF-8");
			SDPAnnounceParser parser = new SDPAnnounceParser(sdpContent);
			SessionDescriptionImpl sessiondescription = parser.parse();
			MediaDescription incomingMediaDescriptor = (MediaDescription) sessiondescription
					.getMediaDescriptions(false).get(0);
			remoteRtpPort = incomingMediaDescriptor.getMedia().getMediaPort();
			RCLogger.i(TAG, "Remote RTP port from incoming SDP:"
					+ remoteRtpPort);
			dispatchSipEvent(new SipEvent(this, SipEventType.LOCAL_RINGING, "",
					sm.getFrom().getAddress().toString(), 0, sdpContent));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void incomingCancel(Request request,
								ServerTransaction serverTransactionId) {
		RCLogger.i(TAG, "incomingCancel()");

		try {
			RCLogger.i(TAG, "CANCEL received");
			if (serverTransactionId == null) {
				RCLogger.i(TAG, "shootist:  null TID.");
				return;
			}
			Dialog dialog = serverTransactionId.getDialog();
			//RCLogger.i(TAG, "Dialog State = " + dialog.getState());
			Response response = messageFactory.createResponse(200, request);
			RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
			serverTransactionId.sendResponse(response);
			//RCLogger.i(TAG, "Sending 200 Canceled Request");
			RCLogger.i(TAG, "Dialog State = " + dialog.getState());

			if (currentServerTransaction != null) {
				// also send a 487 Request Terminated response to the original INVITE request
				Request originalInviteRequest = currentServerTransaction.getRequest();
				Response originalInviteResponse = messageFactory.createResponse(Response.REQUEST_TERMINATED, originalInviteRequest);
				currentServerTransaction.sendResponse(originalInviteResponse);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);

		}
	}

	private void sendDecline(Request request) {
		RCLogger.i(TAG, "sendDecline()");

		Thread thread = new Thread() {
			public void run() {

				Response responseBye;
				try {
					responseBye = messageFactory.createResponse(
							Response.DECLINE,
							currentServerTransaction.getRequest());
					// TODO: we are logging in a secondary thread, maybe dangerous?
					RCLogger.v(TAG, "Sending SIP response: \n" + responseBye.toString());
					currentServerTransaction.sendResponse(responseBye);

				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SipException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		thread.start();
		direction = CallDirection.NONE;
	}

	private void sendOk(RequestEvent requestEvt) {
		RCLogger.i(TAG, "sendOk()");

		Response response;
		try {
			response = messageFactory.createResponse(200,
					requestEvt.getRequest());
			ServerTransaction serverTransaction = requestEvt
					.getServerTransaction();
			if (serverTransaction == null) {
				serverTransaction = sipProvider
						.getNewServerTransaction(requestEvt.getRequest());
			}
			RCLogger.v(TAG, "Sending SIP response: \n" + response.toString());
			serverTransaction.sendResponse(response);

		} catch (ParseException e) {
			e.printStackTrace();
		} catch (SipException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// introduced separate sendByeClient method because the client initiated BYE
	// is different -at some point we should merge those methods
	private void sendByeClient(Transaction transaction) {
		RCLogger.i(TAG, "sendByeClient()");
		if (dialog == null) {
			RCLogger.i(TAG, "Hmm, weird: dialog is already terminated -avoiding BYE");
		}
		else {
			Request byeRequest = null;
			try {
				byeRequest = dialog.createRequest(Request.BYE);
				if (!getSipProfile().getRemoteEndpoint().isEmpty()) {
					// we only need this for non-registrarless calls since the problem is only for incoming calls,
					// and when working in registrarless mode there are no incoming calls
					SipURI routeUri = (SipURI) addressFactory.createURI(getSipProfile().getRemoteEndpoint());
					routeUri.setLrParam();
					Address routeAddress = addressFactory.createAddress(routeUri);
					RouteHeader routeHeader = headerFactory.createRouteHeader(routeAddress);
					byeRequest.addFirst(routeHeader);
				}
				RCLogger.v(TAG, "Sending SIP request: \n" + byeRequest.toString());

			} catch (SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			final Request r = byeRequest;

			Thread thread = new Thread() {
				public void run() {
					try {
						ClientTransaction ct = sipProvider.getNewClientTransaction(r);
						dialog.sendRequest(ct);
					} catch (TransactionUnavailableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (TransactionDoesNotExistException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			thread.start();
		}

		direction = CallDirection.NONE;
	}

	private void sendCancel(ClientTransaction transaction) {
		RCLogger.i(TAG, "sendCancel()");
		try {
			final Request request = transaction.createCancel();
			RCLogger.v(TAG, "Sending SIP response: \n" + request.toString());

			Thread thread = new Thread() {
				public void run() {
					try {
						final ClientTransaction cancelTransaction = sipProvider.getNewClientTransaction(request);
						cancelTransaction.sendRequest();
					} catch (TransactionDoesNotExistException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SipException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			thread.start();
		} catch (SipException e) {
			e.printStackTrace();
		}
	}

	// *** Various Helpers *** //
	public static String getIPAddress(boolean useIPv4, NetworkInterfaceType networkInterfaceType) {
		RCLogger.i(TAG, "getIPAddress()");
		try {
			if (networkInterfaceType == NetworkInterfaceType.NetworkInterfaceTypeWifi) {
				WifiManager wifiMgr = (WifiManager) androidContext.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
				int ip = wifiInfo.getIpAddress();
				return Formatter.formatIpAddress(ip);
			}

			if (networkInterfaceType == NetworkInterfaceType.NetworkInterfaceTypeCellularData) {
				List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
				for (NetworkInterface intf : interfaces) {
					if (!intf.getName().matches("wlan.*")) {
						List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
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
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public ArrayList<ViaHeader> createViaHeader() {
		RCLogger.i(TAG, "createViaHeader()");
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader myViaHeader;
		try {
			myViaHeader = this.headerFactory.createViaHeader(
					sipProfile.getLocalIp(), sipProfile.getLocalPort(),
					sipProfile.getTransport(), null);
			myViaHeader.setRPort();
			viaHeaders.add(myViaHeader);
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
		}
		return viaHeaders;
	}

	public Address createContactAddress() {
		RCLogger.i(TAG, "createContactAddress()");
		try {
			return this.addressFactory.createAddress("sip:"
					+ getSipProfile().getSipUserName() + "@"
					+ getSipProfile().getLocalEndpoint() + ";transport=" + getSipProfile().getTransport()
					+ ";registering_acc=" + getSipProfile().getRemoteIp(addressFactory));
		} catch (ParseException e) {
			return null;
		}
	}

	public synchronized void addSipListener(ISipEventListener listener) {
		RCLogger.i(TAG, "addSipListener()");
		if (!sipEventListenerList.contains(listener)) {
			sipEventListenerList.add(listener);
		}
	}

	public synchronized void removeSipListener(ISipEventListener listener) {
		RCLogger.i(TAG, "createContactAddress()");
		if (sipEventListenerList.contains(listener)) {
			sipEventListenerList.remove(listener);
		}
	}

	public UserAgentHeader generateUserAgentHeader()
	{
		RCLogger.i(TAG, "generateUserAgentHeader()");
		List<String> userAgentTokens = new LinkedList<String>();
 		UserAgentHeader header = null;
		userAgentTokens.add(USERAGENT_STRING);
		try {
			header = this.headerFactory.createUserAgentHeader(userAgentTokens);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return header;
	}

}
