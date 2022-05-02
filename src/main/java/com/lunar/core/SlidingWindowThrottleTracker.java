package com.lunar.core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.util.NonZeroLongCircularBuffer;

/**
 * Always right with respect to a sliding window.  Use this for order throttling to the exchange.
 * Duration can be set per one {@link TimeUnit} at the moment.  We should enhance this class if 
 * there is a need to increase granularity of duration (e.g. 50 throttles per 2 seconds).  
 * 
 * @author Calvin
 *
 */
public final class SlidingWindowThrottleTracker implements ThrottleTracker {
	private static final Logger LOG = LogManager.getLogger(SlidingWindowThrottleTracker.class);
	private NonZeroLongCircularBuffer availableThrottles;
	public static long NO_OP = Long.MIN_VALUE;
	private final long durationNs;
	private final TimerService timerService;
	private int numNoopThrottles;
	private Peeker peeker;
	
	private interface Peeker {
		long peekNextAvailTimeAndRequeNoop();
		long peekNextAvailTime(int n);
		long getNextAvailTimeAndRequeNoop();
	}
	
	private class NormalPeeker implements Peeker {
		@Override
		public long peekNextAvailTimeAndRequeNoop() {
			return availableThrottles.peek();
		}
		@Override
		public long peekNextAvailTime(int n) {
			return availableThrottles.peek(n);
		}
		@Override
		public long getNextAvailTimeAndRequeNoop() {
			return availableThrottles.getNextAndClear(1);
		}
	}
	
	private class NoopSkippingPeeker implements Peeker {
		@Override
		public long peekNextAvailTimeAndRequeNoop() {
			// Skip over all NO_OP operation and add them back to the end of the queue
			int n = 1;
			long time;
			while ((time = availableThrottles.peek(n)) == NO_OP){
				n++;
			}		
			availableThrottles.unsafeClear(n - 1);
			for (int i = 1; i < n; i++){
				availableThrottles.add(NO_OP);
			}
			return time;
		}
		@Override
		public long peekNextAvailTime(int k) {
			// Read pass all NO_OP
			int n = 1;
			int found = 0;
			long time = Long.MAX_VALUE;
			while (found < k){
				time = availableThrottles.peek(n);
				if (time != NO_OP){
					found++;
				}
				n++;				
			}
			return time;
		}
		@Override
		public long getNextAvailTimeAndRequeNoop() {
			// Skip over all NO_OP operation and add them back to the end of the queue
			int n = 1;
			long time;
			while ((time = availableThrottles.peek(n)) == NO_OP){
				n++;
			}		
			availableThrottles.unsafeClear(n);
			for (int i = 1; i < n; i++){
				availableThrottles.add(NO_OP);
			}
			return time;
		}
	}
	
	public SlidingWindowThrottleTracker(int numThrottles, Duration duration, TimerService timerService){
		this.durationNs = duration.toNanos();
		this.timerService = timerService;
		init(numThrottles);		
		LOG.info("Created throttle tracker [numThrottles:{}, capacity:{}]", numThrottles, availableThrottles.capacity());
	}
	
	private void init(int numThrottles){
		long currentTime = timerService.nanoTime();
		availableThrottles = new NonZeroLongCircularBuffer(numThrottles);

		if (numThrottles < 0){
			throw new IllegalArgumentException("Number of throttles must be greater than zero");
		}
		numNoopThrottles = availableThrottles.capacity() - numThrottles;
		if (numNoopThrottles == 0){
			peeker = new NormalPeeker();
		}
		else {
			peeker = new NoopSkippingPeeker();
		}

		int i = 0;
		for (i = 0; i < numThrottles; i++){
			availableThrottles.add(currentTime);
		}
		for (; i < availableThrottles.capacity(); i++){
			availableThrottles.add(NO_OP);
		}		
	}
	
	@Override
	public int numThrottles() {
		return (availableThrottles.capacity() - numNoopThrottles);
	}

	@Override
	public void changeNumThrottles(int numThrottles){
		if (numThrottles < 0){
			throw new IllegalArgumentException("Number of throttles must be greater than zero");
		}
		int prevNumThrottles = (availableThrottles.capacity() - numNoopThrottles); 
		availableThrottles = new NonZeroLongCircularBuffer(numThrottles);
		init(numThrottles);
		LOG.info("Changed number of throttles [from:{}, to:{}]", prevNumThrottles, numThrottles);
	}
	
	@Override
	public boolean getThrottle(){
		long currentTime = timerService.nanoTime();
		long isAvailableAfter = peeker.peekNextAvailTimeAndRequeNoop();
		if (currentTime < isAvailableAfter || isAvailableAfter == NonZeroLongCircularBuffer.EMPTY_VALUE){
			return false;
		}
		availableThrottles.unsafeClear(1);
		availableThrottles.add(currentTime + durationNs);
		return true;
	}

	@Override
	public boolean getThrottle(int numThrottleRequiredToProceed){
		long currentTime = timerService.nanoTime();
		long isAvailableAfter = peeker.peekNextAvailTime(numThrottleRequiredToProceed);
		if (currentTime < isAvailableAfter || isAvailableAfter == NonZeroLongCircularBuffer.EMPTY_VALUE){
			return false;
		}		
		isAvailableAfter = peeker.getNextAvailTimeAndRequeNoop();
		availableThrottles.add(currentTime + durationNs);
		return true;		
	}
	
//	private long peekNextAvailTime(){
//		int n = 1;
//		long time;
//		while ((time = availableThrottles.peek(n)) == NO_OP){
//			n++;
//		}		
//		availableThrottles.unsafeClear(n - 1);
//		for (int i = 1; i < n; i++){
//			availableThrottles.add(NO_OP);
//		}
//		return time;
//	}
	
	long peekNextTime(){
		return availableThrottles.peek();
	}
	
//	@Override
//	public boolean getThrottle(int numThrottleRequiredToProceed, int numThrottleToBeAcquired){
//		if (numThrottleToBeAcquired > numThrottleRequiredToProceed){
//			throw new IllegalArgumentException("numThrottleToBeAcquired: " + numThrottleToBeAcquired + " must not be greater than numThrottleRequiredToProceed: " + numThrottleRequiredToProceed);
//		}
//		
//		long currentTime = timerService.nanoTime();
//		long isAvailableAfter = availableThrottles.peek(numThrottleRequiredToProceed);
//		if (currentTime < isAvailableAfter || isAvailableAfter == NonZeroLongCircularBuffer.EMPTY_VALUE){
//			return false;
//		}
//		isAvailableAfter = availableThrottles.getNextAndClear(numThrottleToBeAcquired);
//		for (int i = 0; i < numThrottleToBeAcquired; i++){
//			availableThrottles.add(currentTime + durationNs);
//		}
//		return true;		
//	}
	
//	@Override
//	public boolean peekThrottle() {
//		long currentTime = timerService.nanoTime();
//		long isAvailableAfter = availableThrottles.peek();
//		if (currentTime < isAvailableAfter || isAvailableAfter == NonZeroLongCircularBuffer.EMPTY_VALUE){
//			return false;
//		}
//		return true;
//	}
	
//	@Override
//	public boolean peekThrottle(int n) {
//		long currentTime = timerService.nanoTime();
//		long isAvailableAfter = availableThrottles.peek(n);
//		if (currentTime < isAvailableAfter || isAvailableAfter == NonZeroLongCircularBuffer.EMPTY_VALUE){
//			return false;
//		}
//		return true;
//	}

	@Override
	public long nextAvailNs() {
		return peeker.peekNextAvailTimeAndRequeNoop();
//		return availableThrottles.peek();
	}
	
//	@Override
//	public long nextAvailNs(int n) {
//		return availableThrottles.peek(n);
//	}
}
