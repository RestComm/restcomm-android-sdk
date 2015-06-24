package org.mobicents.restcomm.android.sipua.impl;

import java.util.EventObject;

public class SipEvent extends EventObject {
	public String content;
	public String from;
	public SipEventType type;
	public int remoteRtpPort;

	public enum SipEventType {
		MESSAGE, BYE, CALL, BUSY_HERE, ACCEPTED, SERVICE_UNAVAILABLE, CALL_CONNECTED, LOCAL_RINGING, DECLINED, REMOTE_RINGING, REMOTE_CANCEL
	}

	public SipEvent(Object source, SipEventType type, String content,
			String from) {
		super(source);

		this.type = type;
		this.content = content;
		this.from = from;
	}

	public SipEvent(Object source, SipEventType type, String content,
			String from, int rtpPort) {
		super(source);
		this.type = type;
		this.content = content;
		this.from = from;
		this.remoteRtpPort = rtpPort;
	}

}
