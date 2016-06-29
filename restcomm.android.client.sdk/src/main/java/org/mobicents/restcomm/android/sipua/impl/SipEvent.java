/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

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
