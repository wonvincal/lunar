package com.lunar.core;

import com.lunar.service.ServiceConstant;

/**
 * Just a class that generates sequence number
 * @author wongca
 *
 */
public class SequencingOnlyChannel {
	private final long startSeq;
	private final int id;
	/** 
	 * seq keeps changing.  It will give a false-sharing cache effect 
	 * that subscribers have been changed.  */
	private long seq;
	
	
	public static SequencingOnlyChannel of(int id, long startSeq){
		return new SequencingOnlyChannel(id, startSeq);
	}

	public static SequencingOnlyChannel of(int id){
		return new SequencingOnlyChannel(id, ServiceConstant.START_CHANNEL_SEQ);
	}

	SequencingOnlyChannel(int id, long startSeq){
		this.startSeq = startSeq;
		this.id = id;
		this.seq = startSeq;
	}
	
	public int id(){
		return id;
	}

	public long peekSeq(){
		return seq;
	}
	
	public long getAndIncrementSeq(){
		return seq++;
	}

	public void reset() {
		this.seq = startSeq;
	}

	public boolean isClear(){
	    return this.seq == startSeq;
	}
}
