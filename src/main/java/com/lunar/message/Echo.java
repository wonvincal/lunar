package com.lunar.message;

import com.lunar.message.io.sbe.BooleanType;

public class Echo extends Message {
	private final int srcSvcId;
	private final int key;
	private final BooleanType isResponse;
	private final long startTime;
	
	public Echo(int srcSvcId, int key, BooleanType isResponse, long startTime){
		this.srcSvcId = srcSvcId;
		this.key = key;
		this.isResponse = isResponse;
		this.startTime = startTime;
	}
	
//	@Override
//	public void encode(int dstSvcId, MessageCodec codec, Frame frame) {
//		codec.encoder().encodeEcho(frame, srcSvcId, dstSvcId, this);
//	}
//
	@Override
	public int senderSinkId() {
		return srcSvcId;
	}
	
	public int key(){
		return key;
	}
	
	public BooleanType isResponse(){
		return isResponse;
	}
	
	public long startTime(){
		return startTime;
	}
}
