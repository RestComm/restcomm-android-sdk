package org.mobicents.restcomm.android.sdk;

import org.mobicents.restcomm.android.sdk.impl.SipManager;
import org.mobicents.restcomm.android.sdk.impl.SoundManager;

import android.media.AudioManager;

public interface IDevice  extends ISipEventListener{

	void Register();

	void Call(String to);

	void SendMessage(String to, String message);

	void SendDTMF(String digit);
	
	SipManager GetSipManager();
	SoundManager getSoundManager();

}
