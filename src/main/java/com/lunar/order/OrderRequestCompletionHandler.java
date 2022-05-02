package com.lunar.order;

import com.lunar.message.io.sbe.OrderRequestRejectType;

public interface OrderRequestCompletionHandler {
	public void sendToExchange(OrderRequest request, long timestamp);
	public void receivedFromExchange(OrderRequest request);
	public void complete(OrderRequest request);
	public void completeWithOrdSidOnly(int orderSid);
	public void rejectWithOrdSidOnly(int orderSid, OrderRequestRejectType rejectType, byte[] reason);
	public void timeout(OrderRequest request);
	public void throttled(OrderRequest request);
	public void timeoutAfterThrottled(OrderRequest request);
	public void fail(OrderRequest request, Throwable e);
	public void fail(OrderRequest request, String description, Throwable e);
	
	public static OrderRequestCompletionHandler NULL_HANDLER = new OrderRequestCompletionHandler() {

		@Override
		public void sendToExchange(OrderRequest request, long sendTime) {
		}

		@Override
		public void receivedFromExchange(OrderRequest request) {
		}

		@Override
		public void timeout(OrderRequest request) {
		}
		
		@Override
		public void throttled(OrderRequest request) {
		}
		
		@Override
		public void rejectWithOrdSidOnly(int orderSid, OrderRequestRejectType rejectType, byte[] reason) {
		}
		
		@Override
		public void fail(OrderRequest request, String description, Throwable e) {
		}
		
		@Override
		public void fail(OrderRequest request, Throwable e) {
		}
		
		@Override
		public void completeWithOrdSidOnly(int orderSid) {
		}
		
		@Override
		public void complete(OrderRequest request) {
		}

		@Override
		public void timeoutAfterThrottled(OrderRequest request) {
		}
	}; 
}
