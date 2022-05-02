package com.lunar.strategy;

public interface CompositeOrderStatusReceivedHandler extends OrderStatusReceivedHandler {
	void registerOrderStatusReceivedHandler(final OrderStatusReceivedHandler obUpdateHandler);
	void unregisterOrderStatusReceivedHandler(final OrderStatusReceivedHandler obUpdateHandler);

}
