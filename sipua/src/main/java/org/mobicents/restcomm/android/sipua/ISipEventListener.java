package org.mobicents.restcomm.android.sipua;

import java.util.EventListener;

import org.mobicents.restcomm.android.sipua.impl.SipEvent;

public interface ISipEventListener extends EventListener {

	public void onSipMessage(SipEvent sipEvent);
}
