package com.lunar.order;

import com.lunar.core.SequencingOnlyChannel;
import com.lunar.core.ThrottleTracker;

public class SecurityLevelInfo {
	private final long secSid;
	private final SequencingOnlyChannel channel;
	private final ValidationOrderBook validationOrderBook;
	private ThrottleTracker throttleTracker;
	private long undSecSid;
	
	public static SecurityLevelInfo of(long secSid, SequencingOnlyChannel channel, ValidationOrderBook validationOrderBook){
		return new SecurityLevelInfo(secSid, channel, validationOrderBook, ThrottleTracker.NULL_TRACKER);
	}
	
	SecurityLevelInfo(long secSid, SequencingOnlyChannel channel, ValidationOrderBook validationOrderBook, ThrottleTracker throttleTracker){
		this.secSid = secSid;
		this.channel = channel;
		this.validationOrderBook = validationOrderBook;
		this.throttleTracker = throttleTracker;
	}

	public ThrottleTracker throttleTracker(){
		return throttleTracker;
	}

	public SecurityLevelInfo throttleTracker(ThrottleTracker tracker){
		this.throttleTracker = tracker;
		return this;
	}

	public long undSecSid(){
		return undSecSid;
	}
	
	public SecurityLevelInfo undSecSid(long undSecSid){
		this.undSecSid = undSecSid;
		return this;
	}

	public boolean hasThrottleTracker(){
		return this.throttleTracker != ThrottleTracker.NULL_TRACKER;
	}
	
	public SequencingOnlyChannel channel(){
		return channel;
	}
	public ValidationOrderBook validationOrderBook(){
		return validationOrderBook;
	}
	public long secSid(){
		return secSid;
	}
	public void clearBook(){
		this.validationOrderBook.clear();
	}
	public boolean isClearBook(){
		return this.validationOrderBook.isClear();
	}
	@Override
	public String toString() {
		return "Security level info [secSid: " + secSid + ", " + validationOrderBook.toString() + "]";
	}
}
