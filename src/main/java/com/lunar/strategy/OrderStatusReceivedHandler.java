package com.lunar.strategy;

import com.lunar.message.io.sbe.OrderRequestRejectType;

public interface OrderStatusReceivedHandler {
	void onOrderStatusReceived(final long nanoOfDay, final int price, final long quantity, final OrderRequestRejectType rejectType) throws Exception;
}
