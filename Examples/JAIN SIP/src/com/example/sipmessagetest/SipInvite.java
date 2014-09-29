package com.example.sipmessagetest;

import java.text.ParseException;
import java.util.*;

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
import javaxx.sip.SipFactory;
import javaxx.sip.SipListener;
import javaxx.sip.SipProvider;
import javaxx.sip.SipStack;
import javaxx.sip.TimeoutEvent;
import javaxx.sip.TransactionTerminatedEvent;
import javaxx.sip.address.Address;
import javaxx.sip.address.AddressFactory;
import javaxx.sip.address.SipURI;
import javaxx.sip.header.CSeqHeader;
import javaxx.sip.header.CallIdHeader;
import javaxx.sip.header.ContactHeader;
import javaxx.sip.header.ContentTypeHeader;
import javaxx.sip.header.FromHeader;
import javaxx.sip.header.Header;
import javaxx.sip.header.HeaderFactory;
import javaxx.sip.header.MaxForwardsHeader;
import javaxx.sip.header.ToHeader;
import javaxx.sip.header.ViaHeader;
import javaxx.sip.message.MessageFactory;
import javaxx.sip.message.Request;
import javaxx.sip.message.Response;
import android.os.AsyncTask;


public class SipInvite extends
		AsyncTask<Object, Object, Object> implements SipListener {
	private SipStack sipStack;
	private SipProvider sipProvider;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;

	private MessageFactory messageFactory;

	
	
	private ListeningPoint udpListeningPoint;
	private ContactHeader contactHeader;
	private ClientTransaction inviteTid;
	private Dialog dialog;
	long invco = 1;
	private String localIp = "10.0.3.15";
	
	private int localPort = 5060;
	private String localEndpoint = localIp + ":" + localPort;
	private String transport="udp";
	
	private String remoteIp = "23.23.228.238";
	private int remotePort = 5080;
	private String remoteEndpoint = remoteIp + ":" + remotePort;

	public void register() throws Exception {

		SipFactory sipFactory = null;
		sipStack = null;
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("com.telestax");

		Properties properties = new Properties();
		properties.setProperty("javaxx.sip.OUTBOUND_PROXY", remoteEndpoint
				+ "/" + transport);
		properties.setProperty("javaxx.sip.STACK_NAME", "androidSip");

		try {
			// Create SipStack object
			sipStack = sipFactory.createSipStack(properties);
			System.out.println("createSipStack " + sipStack);
		} catch (PeerUnavailableException e) {
			// could not find gov.nist.jain.protocol.ip.sip.SipStackImpl in the
			// classpath
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

			sipProvider.addSipListener(this);

		} catch (PeerUnavailableException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(0);
		} catch (Exception e) {
			System.out.println("Creating Listener Points");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}

		try {
			System.out.println("ShootistAuth Process ");
			Request request = this.createInvite("");
			// Create the client transaction.
			inviteTid = sipProvider.getNewClientTransaction(request);
			// send the request out.
			inviteTid.sendRequest();
			System.out
					.println("INVITE with no Authorization sent:\n" + request);
			dialog = inviteTid.getDialog();

		} catch (Exception e) {
			System.out.println("Creating call CreateInvite()");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public Request createInvite(String callId) throws ParseException,
			InvalidArgumentException {
		String fromName = "Bob";
		String fromSipAddress = localIp;
		String fromDisplayName = "Bob";

		String toSipAddress = remoteIp;
		String toUser = "alice";
		String toDisplayName = "alice";

		// create >From Header
		SipURI fromAddress = addressFactory.createSipURI(fromName,
				fromSipAddress);

		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(fromDisplayName);
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress,
				"12345");

		// create To Header
		SipURI toAddress = addressFactory.createSipURI(toUser, toSipAddress);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(toDisplayName);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

		// create Request URI
		SipURI requestURI = addressFactory.createSipURI(toUser, remoteEndpoint);

		// Create ViaHeaders
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(localIp,
				sipProvider.getListeningPoint(transport).getPort(), transport,
				null);
		// add via headers
		viaHeaders.add(viaHeader);

		// Create ContentTypeHeader
		ContentTypeHeader contentTypeHeader = headerFactory
				.createContentTypeHeader("application", "sdp");

		// Create a new CallId header
		CallIdHeader callIdHeader;
		callIdHeader = sipProvider.getNewCallId();
		if (callId.trim().length() > 0)
			callIdHeader.setCallId(callId);

		// Create a new Cseq header
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(invco,
				Request.INVITE);

		// Create a new MaxForwardsHeader
		MaxForwardsHeader maxForwards = headerFactory
				.createMaxForwardsHeader(70);

		// Create the request.
		Request request = messageFactory.createRequest(requestURI,
				Request.INVITE, callIdHeader, cSeqHeader, fromHeader, toHeader,
				viaHeaders, maxForwards);
		// Create contact headers
		

		SipURI contactUrl = addressFactory.createSipURI(fromName, localIp);
		contactUrl.setPort(udpListeningPoint.getPort());

		// Create the contact name address.
		SipURI contactURI = addressFactory.createSipURI(fromName, localIp);
		contactURI.setPort(sipProvider.getListeningPoint("udp").getPort());

		Address contactAddress = addressFactory.createAddress(contactURI);

		// Add the contact address.
		contactAddress.setDisplayName(fromName);

		contactHeader = headerFactory.createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		String sdpData = "v=0\r\n"
				+ "o=4855 13760799956958020 13760799956958020"
				+ " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n"
				+ "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
				+ "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n"
				+ "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
				+ "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
		byte[] contents = sdpData.getBytes();

		request.setContent(contents, contentTypeHeader);

		Header callInfoHeader = headerFactory.createHeader("Call-Info",
				"<http://www.antd.nist.gov>");
		request.addHeader(callInfoHeader);

		return request;
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent arg0) {
		System.out.println(arg0);

	}

	@Override
	public void processIOException(IOExceptionEvent arg0) {
		System.out.println(arg0);

	}

	@Override
	public void processRequest(RequestEvent arg0) {
		Request request = arg0.getRequest();
        ServerTransaction serverTransactionId = arg0
                .getServerTransaction();
        System.out.println(request);

	}

	@Override
	public void processResponse(ResponseEvent arg0) {
        Response response = (Response) arg0.getResponse();
        System.out.print(response);

	}

	@Override
	public void processTimeout(TimeoutEvent arg0) {
		System.out.println(arg0);

	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	protected Object doInBackground(Object... params) {
		try {
			register();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}