package com.lunar.message;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.lunar.core.GrowthFunction;

public class RequestTypeSetting {
	private volatile long initRetryDelayNs;
	private int maxRetryAttempts;
	private GrowthFunction growth;
	private volatile long timeoutNs;

	private RequestTypeSetting(long timeoutNs, long initRetryDelayNs, int maxRetryAttempts, GrowthFunction growth){
		this.timeoutNs = timeoutNs;
		this.initRetryDelayNs = initRetryDelayNs;
		this.maxRetryAttempts = maxRetryAttempts;
		this.growth = growth;		
	}
	
	private RequestTypeSetting(Duration timeout, Duration initRetryDelay, int maxRetryAttempts, GrowthFunction growth){
		this(timeout.toNanos(), initRetryDelay.toNanos(), maxRetryAttempts, growth);
	}
	
	public long timeoutNs(){
		return timeoutNs;
	}
	
	public long initDelayNs(){
		return initRetryDelayNs;
	}
	
	public int maxRetryAttempts(){
		return maxRetryAttempts;
	}

	public GrowthFunction growth(){
		return growth;
	}
	
	public long computeDelay(int retryCount, TimeUnit unit){
		return unit.convert(growth.compute(initRetryDelayNs, retryCount), TimeUnit.NANOSECONDS);
	}
	
	public long computeDelayNs(int retryCount){
		return growth.compute(initRetryDelayNs, retryCount);
	}
	
	public RequestTypeSetting clone(){
		return new RequestTypeSetting(timeoutNs, initRetryDelayNs, maxRetryAttempts, growth);
	}
	
	public static RequestTypeSetting of(Duration timeout, Duration initRetryDelay, int maxRetryAttempts, GrowthFunction growth){
		return new RequestTypeSetting(timeout, initRetryDelay, maxRetryAttempts, growth);
	}
}
