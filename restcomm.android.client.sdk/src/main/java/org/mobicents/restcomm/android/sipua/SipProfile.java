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

package org.mobicents.restcomm.android.sipua;

import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;

import java.text.ParseException;

public class SipProfile {
	private static final String TAG = "SipProfile";
	private  String localIp;
	private  int localPort = 5080;
	private  String transport = "udp";

	private String remoteEndpoint;
	private  String sipUserName;
	private  String sipPassword;

	public  String getLocalIp() {
		return localIp;
	}

	public  void setLocalIp(String localIp) {
		RCLogger.i(TAG, "Setting localIp:" + localIp);
		this.localIp = localIp;
	}

	public  int getLocalPort() {
		return localPort;
	}

	public  void setLocalPort(int localPort) {
		RCLogger.i(TAG, "Setting localPort:" + localPort);
		this.localPort = localPort;
	}

	public  String getLocalEndpoint() {
		return localIp + ":" + localPort;
	}

	public  void setRemoteEndpoint(String remoteEndpoint) {
		RCLogger.i(TAG, "Setting remoteEndpoint:" + remoteEndpoint);
		this.remoteEndpoint = remoteEndpoint;
	}

	public String getRemoteIp(AddressFactory addressFactory) throws ParseException {
		if (remoteEndpoint.isEmpty()) {
			return "";
		}

		String remoteIp = "";
		Address address = addressFactory.createAddress(remoteEndpoint);
		remoteIp = ((SipURI)address.getURI()).getHost();
		return remoteIp;
	}

	public  int getRemotePort(AddressFactory addressFactory) throws ParseException {
		if (remoteEndpoint.isEmpty()) {
			return 0;
		}

		int remotePort = 0;
		Address address = addressFactory.createAddress(remoteEndpoint);
		remotePort = ((SipURI)address.getURI()).getPort();
		return remotePort;
	}

	public  String getRemoteEndpoint() {
		return this.remoteEndpoint;
	}

	public String getSipUserName() {
		return sipUserName;
	}

	public void setSipUserName(String sipUserName) {
		RCLogger.i(TAG, "Setting sipUserName:" + sipUserName);
		this.sipUserName = sipUserName;
	}

	public String getSipPassword() {
		return sipPassword;
	}

	public void setSipPassword(String sipPassword) {
		RCLogger.i(TAG, "Setting sipPassword:" + sipPassword);
		this.sipPassword = sipPassword;
	}

	public String getTransport() {
		return transport;
	}

	public void setTransport(String transport) {
		RCLogger.i(TAG, "Setting transport:" + transport);
		this.transport = transport;
	}
}
