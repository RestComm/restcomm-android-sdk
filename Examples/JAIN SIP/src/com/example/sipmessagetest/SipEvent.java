package com.example.sipmessagetest;

import java.util.EventObject;


public class SipEvent extends EventObject{
	public enum SipEventType {
	    MESSAGE,BYE,CALL
	}

	public String content;
	public String from;
	public SipEventType type;
	public SipEvent(Object source,SipEventType type,String content,String from) {
		super(source);
		this.type = type;
		this.content = content;
		this.from = from;
	}

}
