package com.lunar.core;

import java.time.Duration;

/**
 * Fast throttle tracking.  Minimal computation required.  Sacrifice precision for 
 * performance. 
 * 
 * Thread safety: No
 * Inter-thread data synch: No
 * 
 * @author Calvin
 *
 */
public class FixedWindowThrottleTracker implements ThrottleTracker {
	private int numThrottles;
	private long allThrottleAvailAfter;
	private int numThrottlesInUse;
	private final long durationNs;
	private final TimerService timerService;
	
	public FixedWindowThrottleTracker(int numThrottles, Duration duration, TimerService timerService){
		this.durationNs = duration.toNanos();
		this.numThrottles = numThrottles;
		this.timerService = timerService;
		this.allThrottleAvailAfter = timerService.nanoTime() + durationNs;
	}
	
	@Override
	public int numThrottles() {
		return numThrottles;
	}
	
	@Override
	public void changeNumThrottles(int numThrottles){
		this.numThrottles = numThrottles;
	}
	
	@Override
	public boolean getThrottle(){
		long currentTime = timerService.nanoTime();
		if (currentTime < allThrottleAvailAfter){
			if (numThrottlesInUse < numThrottles){
				numThrottlesInUse++;
				return true;
			}
			return false;
		}
		// start a new period for counting throttles
		allThrottleAvailAfter = currentTime + durationNs;
		numThrottlesInUse = 1;
		return true;
	}
	
//	@Override
//	public boolean peekThrottle() {
//		long currentTime = timerService.nanoTime();
//		if (currentTime < allThrottleAvailAfter){
//			if (numThrottlesInUse < numThrottles){
//				return true;
//			}
//			return false;
//		}
//		return true;
//	}
	
	@Override
	public long nextAvailNs() {
		return allThrottleAvailAfter;
	}

//	@Override
//	public long nextAvailNs(int n) {
//		return allThrottleAvailAfter;
//	}
	
	@Override
	public boolean getThrottle(int check) {
		return getThrottle();
	}

//	@Override
//	public boolean peekThrottle(int n) {
//		return peekThrottle();
//	}
}
