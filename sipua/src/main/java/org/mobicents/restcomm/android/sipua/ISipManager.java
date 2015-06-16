package org.mobicents.restcomm.android.sipua;

public interface ISipManager {

	public void SendMessage(String to, String message) throws NotInitializedException;
	public void SendDTMF(String digit) throws NotInitializedException;
	public void Register();
	public void Call(String to, int localRtpPort) throws NotInitializedException;
	public void Hangup() throws NotInitializedException;;
}
