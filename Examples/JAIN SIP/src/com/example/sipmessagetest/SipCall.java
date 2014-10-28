package com.example.sipmessagetest;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.TimerTask;

import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.address.Address;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.MaxForwardsHeader;
import android.javax.sip.header.RouteHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.Request;
import android.os.AsyncTask;

public class SipCall extends AsyncTask<Object, Object, Object> {

    private ContactHeader contactHeader;
    private Dialog dialog;

    private boolean byeTaskRunning;
    private ClientTransaction inviteTid;

	@Override
	protected Object doInBackground(Object... params) {
		String to = (String) params[0];
		String port = (String) params[2];
		try {
			SipURI from = SipStackAndroid.addressFactory.createSipURI(SipStackAndroid.getInstance().sipUserName, SipStackAndroid.getInstance().getLocalEndpoint());
			Address fromNameAddress = SipStackAndroid.addressFactory.createAddress(from);
			// fromNameAddress.setDisplayName(sipUsername);
			FromHeader fromHeader = SipStackAndroid.headerFactory.createFromHeader(fromNameAddress,
					"Tzt0ZEP92");

			SipStackAndroid.getInstance();
			URI toAddress = SipStackAndroid.addressFactory.createURI(to);
			Address toNameAddress = SipStackAndroid.addressFactory.createAddress(toAddress);
			// toNameAddress.setDisplayName(username);
			ToHeader toHeader = SipStackAndroid.headerFactory.createToHeader(toNameAddress, null);

			URI requestURI = SipStackAndroid.addressFactory.createURI(to);
			// requestURI.setTransportParam("udp");

			ArrayList<ViaHeader> viaHeaders = createViaHeader();

			CallIdHeader callIdHeader = SipStackAndroid.sipProvider.getNewCallId();

			SipStackAndroid.getInstance();
			CSeqHeader cSeqHeader = SipStackAndroid.headerFactory.createCSeqHeader(1l,
					Request.INVITE);

			MaxForwardsHeader maxForwards = SipStackAndroid.headerFactory
					.createMaxForwardsHeader(70);

			Request request = SipStackAndroid.messageFactory.createRequest(requestURI,
					Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
					toHeader, viaHeaders, maxForwards);
			SupportedHeader supportedHeader = SipStackAndroid.headerFactory
					.createSupportedHeader("replaces, outbound");
			request.addHeader(supportedHeader);

			SipURI routeUri = SipStackAndroid.addressFactory.createSipURI(null, SipStackAndroid.getInstance().getRemoteIp());
			routeUri.setTransportParam(SipStackAndroid.transport);
			routeUri.setLrParam();
			routeUri.setPort(SipStackAndroid.getRemotePort());

			Address routeAddress = SipStackAndroid.addressFactory.createAddress(routeUri);
			RouteHeader route =SipStackAndroid.headerFactory.createRouteHeader(routeAddress);
			request.addHeader(route);
			// Create ContentTypeHeader
			ContentTypeHeader contentTypeHeader = SipStackAndroid.headerFactory
					.createContentTypeHeader("application", "sdp");

		
			
			/*SipURI contactUrl = SipStackAndroid.addressFactory.createSipURI(fromName, host);
			contactUrl.setPort(SipStackAndroid.getInstance().udpListeningPoint.getPort());
			contactUrl.setLrParam();
*/
			// Create the contact name address.
			SipURI contactURI = SipStackAndroid.getInstance().addressFactory.createSipURI(SipStackAndroid.sipUserName, SipStackAndroid.getLocalIp());
			contactURI.setPort(SipStackAndroid.getInstance().sipProvider.getListeningPoint(SipStackAndroid.getInstance().transport)
					.getPort());

			Address contactAddress = SipStackAndroid.getInstance().addressFactory.createAddress(contactURI);

			// Add the contact address.
			//contactAddress.setDisplayName(fromName);

			contactHeader = SipStackAndroid.getInstance().headerFactory.createContactHeader(contactAddress);
			request.addHeader(contactHeader);

			// You can add extension headers of your own making
			// to the outgoing SIP request.
			// Add the extension header.
			Header extensionHeader = SipStackAndroid.getInstance().headerFactory.createHeader("My-Header",
					"my header value");
			request.addHeader(extensionHeader);
			
			//String sdpData=""; 
			
			
			String sdpData= "v=0\r\n"
					+ "o=4855 13760799956958020 13760799956958020"
					+ " IN IP4 " +SipStackAndroid.getLocalIp() +"\r\n" + "s=mysession session\r\n"
					+ "p=+46 8 52018010\r\n" + "c=IN IP4 " + SipStackAndroid.getLocalIp()+"\r\n"
					+ "t=0 0\r\n" + "m=audio "+port+" RTP/AVP 0 4 18\r\n"
					+ "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
					+ "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
			byte[] contents = sdpData.getBytes();

			request.setContent(contents, contentTypeHeader);
			// You can add as many extension headers as you
			// want.

			extensionHeader = SipStackAndroid.getInstance().headerFactory.createHeader("My-Other-Header",
					"my new header value ");
			request.addHeader(extensionHeader);

			Header callInfoHeader = SipStackAndroid.getInstance().headerFactory.createHeader("Call-Info",
					"<http://www.antd.nist.gov>");
			request.addHeader(callInfoHeader);

			// Create the client transaction.
			inviteTid = SipStackAndroid.getInstance().sipProvider.getNewClientTransaction(request);

			System.out.println("inviteTid = " + inviteTid);

			// send the request out.

			inviteTid.sendRequest();

			dialog = inviteTid.getDialog();

		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
			usage();
		}
		return null;
	}
	
	

	private ArrayList<ViaHeader> createViaHeader() {
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader myViaHeader;
		try {
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

	private static final String usageString = "java "
			+ "examples.shootist.Shootist \n"
			+ ">>>> is your class path set to the root?";

	private static void usage() {
		System.out.println(usageString);
		System.exit(0);

	}

	class ByeTask extends TimerTask {
		Dialog dialog;

		public ByeTask(Dialog dialog) {
			this.dialog = dialog;
		}

		public void run() {
			try {
				Request byeRequest = this.dialog.createRequest(Request.BYE);
				SipStackAndroid.getInstance();
				ClientTransaction ct = SipStackAndroid.sipProvider
						.getNewClientTransaction(byeRequest);
				dialog.sendRequest(ct);
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);
			}

		}
	}

}
