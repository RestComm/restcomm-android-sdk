package org.mobicents.restcomm.android.sdk;

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
