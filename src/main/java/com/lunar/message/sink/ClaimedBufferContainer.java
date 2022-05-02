package com.lunar.message.sink;

public class ClaimedBufferContainer {
	private MessageSinkBufferClaim buffer;
	ClaimedBufferContainer(){}
	public MessageSinkBufferClaim buffer(){
		return this.buffer;
	}
	public void wrap(MessageSinkBufferClaim buffer){
		this.buffer = buffer;
	}
}
