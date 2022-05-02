package com.lunar.message;

import java.util.concurrent.TimeUnit;

import com.lunar.message.sink.MessageSink.Status;

/**
 * Wrap different sink related statuses into an object.  Each sink should periodically sends this info to
 * the admin.
 * 
 * @author Calvin
 *
 */
public class SinkInfo {
	private final int svcId;
	private final Status status;
	private final ProcessingRate processingRate;
	
	public SinkInfo(final int svcId, final Status status, final ProcessingRate processingRate){
		this.svcId = svcId;
		this.status = status;
		this.processingRate = processingRate;
	}
	public int svcId(){
		return svcId;
	}
	public Status status(){
		return status;
	}
	public ProcessingRate processingRate(){
		return this.processingRate;
	}
	
	public static SinkInfo NULL_INSTANCE = new SinkInfo(-1, null, ProcessingRate.of(0, 0, TimeUnit.MICROSECONDS));
}
