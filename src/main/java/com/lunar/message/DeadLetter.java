package com.lunar.message;

public class DeadLetter extends Message {
	private final String message;
	
	public DeadLetter(String message){
		this.message = message;
	}
	
	@Override
	public int senderSinkId() {
		return 0;
	}

//	@Override
//	public void encode(int dstSinkId, MessageCodec codec, Frame frame) {
//		throw new UnsupportedOperationException();
//	}

	@Override
	public String toString() {
		return message;
	}
	
	public static DeadLetter of(String message){
		return new DeadLetter(message);
	}
}
