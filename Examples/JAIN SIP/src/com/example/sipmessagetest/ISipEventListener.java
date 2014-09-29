package com.example.sipmessagetest;

import java.util.EventListener;

public interface ISipEventListener extends EventListener {

	public void onSipMessage(SipEvent sipEvent);
}
