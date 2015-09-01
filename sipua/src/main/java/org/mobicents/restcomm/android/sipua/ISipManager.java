package org.mobicents.restcomm.android.sipua;

import java.util.HashMap;

public interface ISipManager {

	public void SendMessage(String to, String message) throws NotInitializedException;
	public void SendDTMF(String digit) throws NotInitializedException;
	public void Register(int expiry);
	public void Call(String to, int localRtpPort, HashMap<String, String> sipHeaders) throws NotInitializedException;
	public void Hangup() throws NotInitializedException;
}
