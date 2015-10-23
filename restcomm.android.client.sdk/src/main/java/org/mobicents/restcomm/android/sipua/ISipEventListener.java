package org.mobicents.restcomm.android.sipua;

import java.util.EventListener;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

public interface ISipEventListener extends EventListener {

	public void onSipMessage(SipEvent sipEvent);
	public void onSipError(RCClient.ErrorCodes errorCode, String errorText);
}
