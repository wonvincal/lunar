package com.lunar.order;

import com.lunar.message.io.sbe.OrderCancelRejectType;

public interface MatchingEngineOrderUpdateHandler {
	public void onOrderAccepted(Order order);
	public void onOrderExpired(Order order);
	public void onTradeCreated(Order order, Trade trade);
	public void onOrderRejected(Order order);
	public void onOrderCancelled(Order order);
	public void onOrderCancelled(CancelOrderRequest request, Order order);
	public void onCancelRejected(CancelOrderRequest request, OrderCancelRejectType rejectType, String reason, Order order);
	public void onEndOfRecovery();
}