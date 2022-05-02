package com.lunar.fsm.service.lunar;

import org.agrona.DirectBuffer;

public interface EventExceptionHandler {
	void onException(DirectBuffer buffer, long sequence, boolean endOfBatch, Exception e);
	
	public static EventExceptionHandler NULL_HANDLER = new EventExceptionHandler() {
		@Override
		public void onException(DirectBuffer buffer, long sequence, boolean endOfBatch, Exception e) {
		}
	};
}
