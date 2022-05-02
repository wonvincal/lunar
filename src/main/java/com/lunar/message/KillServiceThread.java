package com.lunar.message;

public class KillServiceThread extends Message {
	private final int senderSinkId;

	public static KillServiceThread of(int senderSinkId){
		return new KillServiceThread(senderSinkId);
	}
	
	public KillServiceThread(int senderSinkId){
		this.senderSinkId = senderSinkId;
	}
	
	@Override
	public int senderSinkId() {
		return senderSinkId;
	}

//	@Override
//	public void encode(int dstSinkId, MessageCodec codec, Frame frame) {
//		throw new UnsupportedOperationException();
//	}

}
