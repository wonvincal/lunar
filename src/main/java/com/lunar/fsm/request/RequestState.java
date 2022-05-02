package com.lunar.fsm.request;

import java.util.Optional;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Holds all data that can be changed during a life cycle of a request
 * @author Calvin
 *
 */
public final class RequestState {
	private static final Logger LOG = LogManager.getLogger(RequestState.class);
	private int retryCount = 0;
	private long startTimeNs = -1;
	private TimerTask requestTimeoutTimerTask;
	private TimerTask retryDelayTimeoutTimerTask;
	private Optional<Timeout> activeRequestTimeout = Optional.empty();
	private Optional<Timeout> activeRetryDelayTimeout = Optional.empty();
	private Optional<Throwable> cause = Optional.empty();
	
	public RequestState(){}
	
	long startTimeNs(){
		return startTimeNs;
	}
	
	RequestState startTimeNs(long startTimeNs){
		this.startTimeNs = startTimeNs;
		return this;
	}
	
	int retryCount(){
		return retryCount;
	}
	
	int incAndGetRetryCount(){
		return ++retryCount;
	}
	
	Optional<Throwable> cause(){
		return cause;
	}
	
	RequestState cause(Throwable cause){
		this.cause = Optional.of(cause);
		return this;
	}
	
	Optional<Timeout> activeRequestTimeout(){
		return activeRequestTimeout;
	}
	
	RequestState activeRequestTimeout(Timeout timeout){
		this.activeRequestTimeout = Optional.of(timeout);
		return this;
	}
	
	Optional<Timeout> activeRetryDelayTimeout(){
		return activeRetryDelayTimeout;
	}
	
	RequestState activeRetryDelayTimeout(Timeout timeout){
		this.activeRetryDelayTimeout = Optional.of(timeout);
		return this;
	}
	
	Optional<Timeout> cancelActiveRetryDelayTimeout(){
		if (this.activeRetryDelayTimeout.isPresent()){
			LOG.debug("cancel retry delay timeout");
			this.activeRetryDelayTimeout.get().cancel();
		}
		Optional<Timeout> result = this.activeRetryDelayTimeout; 
		this.activeRetryDelayTimeout = Optional.empty();
		return result;
	}
	
	Optional<Timeout> cancelActiveRequestTimeout(){
		if (this.activeRequestTimeout.isPresent()){
			this.activeRequestTimeout.get().cancel();
		}
		Optional<Timeout> result = this.activeRequestTimeout; 
		this.activeRequestTimeout = Optional.empty();
		return result;
	}
	
	TimerTask requestTimeoutTimerTask(){
		return this.requestTimeoutTimerTask;
	}
	
	RequestState requestTimeoutTimerTask(TimerTask task){
		requestTimeoutTimerTask = task;
		return this;
	}
	
	TimerTask retryDelayTimeoutTimerTask(){
		return this.retryDelayTimeoutTimerTask;
	}
	
	RequestState retryDelayTimeoutTimerTask(TimerTask task){
		retryDelayTimeoutTimerTask = task;
		return this;
	}

}
