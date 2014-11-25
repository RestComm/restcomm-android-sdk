package org.mobicents.restcomm.android.sdk;

import java.util.EventListener;

import org.mobicents.restcomm.android.sdk.impl.SipEvent;

public interface ISipEventListener extends EventListener {

	public void onSipMessage(SipEvent sipEvent);
}
