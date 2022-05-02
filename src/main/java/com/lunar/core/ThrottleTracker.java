package com.lunar.core;

public interface ThrottleTracker {
	boolean getThrottle();
	boolean getThrottle(int numThrottleRequiredToProceed);
//	boolean peekThrottle();
//	boolean peekThrottle(int n);
	long nextAvailNs();
//	long nextAvailNs(int n);
	void changeNumThrottles(int numThrottles);
	int numThrottles();
	
	public static ThrottleTracker NULL_TRACKER = new ThrottleTracker() {
		
		@Override
		public int numThrottles() {
			return Integer.MAX_VALUE;
		}
		
		@Override
		public long nextAvailNs() {
			return 0;
		}
		
		@Override
		public boolean getThrottle(int numThrottleRequiredToProceed) {
			return true;
		}
		
		@Override
		public boolean getThrottle() {
			return true;
		}
		
		@Override
		public void changeNumThrottles(int numThrottles) {
			// TODO throw exception
		}
	};
}