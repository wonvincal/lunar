package com.lunar.analysis;

public interface SignalHandler {
	void handleSignal(Object signal);
	public static final SignalHandler NULL_SIGNAL_HANDLER = new SignalHandler() {
		
		@Override
		public void handleSignal(Object signal) {
		}
	};
}
