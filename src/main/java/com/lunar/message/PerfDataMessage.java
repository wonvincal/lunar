package com.lunar.message;

public class PerfDataMessage extends Message {
	private final int senderSinkId;
	private final int sinkId;
	private final long roundTripNs;
	
	public PerfDataMessage(int senderSinkId, int sinkId, long roundTripNs){
		this.senderSinkId = senderSinkId;
		this.sinkId = sinkId;
		this.roundTripNs = roundTripNs;
	}
	
	public int sinkId() {
		return sinkId;
	}

	public long roundTripNs() {
		return roundTripNs;
	}

	@Override
	public int senderSinkId() {
		return senderSinkId;
	}

/*	@Override
	public void encode(int dstSinkId, MessageCodec codec, Frame frame) {
		// TODO Auto-generated method stub
		
	}
*/
}
