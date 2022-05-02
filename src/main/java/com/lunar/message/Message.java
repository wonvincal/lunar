package com.lunar.message;

public abstract class Message {
	private int seq;
	
	public int seq(){
		return seq;
	}
	
	public Message seq(int seq){
		this.seq = seq;
		return this;
	}
	
	/**
	 * Source service id.  This is used to facilitate replying of any message
	 * TODO review to see if we really want this to be part of a message
	 * @return
	 */
	abstract int senderSinkId();
}
