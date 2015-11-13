package org.mobicents.restcomm.android.sipua.impl;

import java.util.EventObject;

public class SipEvent extends EventObject {
	public String content;
	public String from;
	public SipEventType type;
	public int remoteRtpPort;
	public String sdp;

	public enum SipEventType {
		MESSAGE,
		INCOMING_BYE_REQUEST,
		INCOMING_BYE_RESPONSE,
		CALL,
		BUSY_HERE,
		ACCEPTED,
		SERVICE_UNAVAILABLE,
		CALL_CONNECTED,
		LOCAL_RINGING,
		DECLINED,
		REMOTE_RINGING,
		REMOTE_CANCEL,
		NOT_FOUND,
		REGISTER_SUCCESS,
	}

	public SipEvent(Object source, SipEventType type, String content,
			String from) {
		super(source);

		this.type = type;
		this.content = content;
		this.from = from;
	}

	public SipEvent(Object source, SipEventType type, String content,
			String from, int rtpPort, String sdp) {
		super(source);
		this.type = type;
		this.content = content;
		this.from = from;
		this.remoteRtpPort = rtpPort;
		this.sdp = sdp;
	}

}
