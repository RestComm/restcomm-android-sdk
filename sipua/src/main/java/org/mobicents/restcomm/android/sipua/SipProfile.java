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

public class SipProfile {
	private  String localIp;
	private  int localPort = 5080;
	private  String transport = "udp";

	private  String remoteIp = "23.23.228.238";
	private  int remotePort = 5060;
	private  String sipUserName;
	private  String sipPassword;

	public  String getLocalIp() {
		return localIp;
	}

	public  void setLocalIp(String localIp) {
		System.out.println("Setting localIp:" + localIp);
		this.localIp = localIp;
	}

	public  int getLocalPort() {
		return localPort;
	}

	public  void setLocalPort(int localPort) {
		System.out.println("Setting localPort:" + localPort);
		this.localPort = localPort;
	}

	public  String getLocalEndpoint() {
		return localIp + ":" + localPort;
	}

	public  String getRemoteIp() {
		return remoteIp;
	}

	public  void setRemoteIp(String remoteIp) {
		System.out.println("Setting remoteIp:" + remoteIp);
		this.remoteIp = remoteIp;
	}

	public  int getRemotePort() {
		return remotePort;
	}

	public  void setRemotePort(int remotePort) {
		System.out.println("Setting remotePort:" + remotePort);
		this.remotePort = remotePort;
	}

	public  String getRemoteEndpoint() {
		return remoteIp + ":" + remotePort;
	}

	public String getSipUserName() {
		return sipUserName;
	}

	public void setSipUserName(String sipUserName) {
		System.out.println("Setting sipUserName:" + sipUserName);
		this.sipUserName = sipUserName;
	}

	public String getSipPassword() {
		return sipPassword;
	}

	public void setSipPassword(String sipPassword) {
		System.out.println("Setting sipPassword:" + sipPassword);
		this.sipPassword = sipPassword;
	}

	public String getTransport() {
		return transport;
	}

	public void setTransport(String transport) {
		System.out.println("Setting transport:" + transport);
		this.transport = transport;
	}

	
}
