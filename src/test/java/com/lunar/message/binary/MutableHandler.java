package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

public class MutableHandler<C> implements Handler<C> {
	private Handler<C> impl = null;
	
	public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, C codec){
		impl.handle(buffer, offset, header, codec);
	}
	
	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, C codec){
		impl.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, codec);
	}
	
	public MutableHandler<C> impl(Handler<C> impl){
		this.impl = impl;
		return this;
	}
}
