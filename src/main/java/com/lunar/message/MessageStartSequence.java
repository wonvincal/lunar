package com.lunar.message;

public enum MessageStartSequence {
	Register(0),
	Subscribe(1_000_000);
	
	private final int startSeq;
	MessageStartSequence(int startSeq){
		this.startSeq = startSeq;
	}
	public int startSeq(){
		return this.startSeq;
	}
}
