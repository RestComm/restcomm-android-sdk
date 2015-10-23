package org.mobicents.restcomm.android.sipua;

import android.javax.sip.TransactionUnavailableException;

import java.text.ParseException;
import java.util.HashMap;

public interface ISipManager {

	public void SendMessage(String to, String message) throws NotInitializedException;
	public void SendDTMF(String digit) throws NotInitializedException;
	public void Register(int expiry) throws ParseException, TransactionUnavailableException;
	public void Call(String to, int localRtpPort, HashMap<String, String> sipHeaders) throws NotInitializedException, ParseException;
	public void Hangup() throws NotInitializedException;
}
