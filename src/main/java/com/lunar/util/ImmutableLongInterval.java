package com.lunar.util;

public class ImmutableLongInterval {
	public static ImmutableLongInterval of(LongInterval interval){
		return new ImmutableLongInterval(interval);
	}
	private final LongInterval interval;
	ImmutableLongInterval(LongInterval interval){
		this.interval = interval;
	}
	public long begin(){
		return this.interval.begin();
	}
	
	public long endExclusive(){
		return this.interval.endExclusive();
	}

	public int data(){
		return this.interval.data();
	}
	
	public long last(){
		return this.interval.last();
	}
	
	public long theoBucketSize(){
		return this.interval.theoBucketSize();
	}
	
	public boolean isEmpty(){
		return this.interval.isEmpty();
	}
	
	public boolean hasValidRange(){
	    return this.interval.hasValidRange();
    }
	
	/**
	 * Check if embedde LongInterval is same as the input LongInterval
	 * 
	 * @param value
	 * @return true if both are empty, or
	 *              one field (except theoBucketSize) is different)
	 */
	public boolean equalsToInterval(LongInterval value){
		return (value.isEmpty() && interval.isEmpty()) || (interval.endExclusive() == value.endExclusive() && interval.begin() == value.begin() && interval.data() == value.data());
	}
	
	@Override
	public String toString() {
		return interval.toString();
	}
}
