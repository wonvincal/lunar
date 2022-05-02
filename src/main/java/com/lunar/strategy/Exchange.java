package com.lunar.strategy;

import com.lunar.message.sink.MessageSinkRef;

public class Exchange {
	private final int sid;
	private String exchangeCode;
	private MessageSinkRef omesSink;
	private MessageSinkRef mdsSink;

	public static Exchange of(int sid, String exchangeCode, MessageSinkRef omesSink, MessageSinkRef mdsSink){
		return new Exchange(sid, exchangeCode, omesSink, mdsSink);
	}
	private Exchange(int sid, String exchangeCode, MessageSinkRef omesSink, MessageSinkRef mdsSink){
		this.sid = sid;
		this.exchangeCode = exchangeCode;
		this.omesSink = omesSink;
		this.mdsSink = mdsSink;
	}

	public int sid(){
		return sid;
	}

	public String exchangeCode(){
		return exchangeCode;
	}

	public Exchange exchangeCode(String exchangeCode){
		this.exchangeCode = exchangeCode;
		return this;
	}

	public MessageSinkRef omesSink(){
		return omesSink;
	}

	public Exchange omesSink(MessageSinkRef value){
		omesSink = value;
		return this;
	}

	public MessageSinkRef mdsSink(){
		return mdsSink;
	}

	public Exchange mdsSink(MessageSinkRef value){
		mdsSink = value;
		return this;
	}
}
