package com.example.sipmessagetest;

import java.util.EventObject;


public class SipEvent extends EventObject{

	public String type;
	public String content;
	public String from;
	public SipEvent(Object source,String type,String content,String from) {
		super(source);
		this.type = type;
		this.content = content;
		this.from = from;
	}

}
