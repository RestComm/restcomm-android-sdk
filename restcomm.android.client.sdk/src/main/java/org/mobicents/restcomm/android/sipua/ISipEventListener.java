package org.mobicents.restcomm.android.sipua;

import java.util.EventListener;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.sipua.impl.SipEvent;

public interface ISipEventListener extends EventListener {
	enum ErrorContext {
		ERROR_CONTEXT_NON_CALL,
		ERROR_CONTEXT_CALL
	}

	public void onSipMessage(SipEvent sipEvent);
	public void onSipError(ErrorContext errorContext, RCClient.ErrorCodes errorCode, String errorText);
}
