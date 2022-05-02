package com.lunar.message;

import com.lunar.message.io.sbe.TimerEventType;

public class TimerEvent extends Message {
	private final int senderSinkId;
	private final int key;
	private final TimerEventType timerEventType;
	private final long startTimeNs;
	private final long expiryTimeNs;
	
	public TimerEvent(int senderSinkId, int key, TimerEventType timerEventType, long startTimeNs, long expiryTimeNs) {
		this.senderSinkId = senderSinkId;
		this.key = key;
		this.startTimeNs = startTimeNs;
		this.expiryTimeNs = expiryTimeNs;
		this.timerEventType = timerEventType;
	}
	
	public int senderSinkId(){
		return senderSinkId;
	}
	
	public int key(){
		return key;
	}

	public long startTimeNs(){
		return startTimeNs;
	}
	
	public long expiryTimeNs(){
		return expiryTimeNs;
	}
	
	public TimerEventType timerEventType(){
		return timerEventType;
	}

/*	@Override
	public void encode(int dstSinkId, MessageCodec codec, Frame frame) {
		codec.encoder().encodeTimerEvent(frame, senderSinkId, dstSinkId, this);
	}
*/	
	public static TimerEvent of(Request request, TimerEventType timerEventType, long startTimeNs, long expiryTimeNs){
		return new TimerEvent(request.senderSinkId(), request.seq(), timerEventType, startTimeNs, expiryTimeNs);
	}

	public static TimerEvent of(Command command, long startTimeNs, long expiryTimeNs){
		return new TimerEvent(command.senderSinkId(), command.seq(), TimerEventType.COMMAND_TIMEOUT, startTimeNs, expiryTimeNs);
	}
}
