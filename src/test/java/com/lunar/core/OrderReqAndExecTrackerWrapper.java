package com.lunar.core;

import com.lunar.order.OrderReqAndExecTracker;
import com.lunar.order.OrderRequestContext;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class OrderReqAndExecTrackerWrapper {
	private final OrderReqAndExecTracker orderTracker;
	public OrderReqAndExecTrackerWrapper(OrderReqAndExecTracker orderTracker){
		this.orderTracker = orderTracker;
	}
	public Int2ObjectOpenHashMap<OrderRequestContext> requests(){
		return orderTracker.requests();
	}
	@Override
	public String toString() {
		return orderTracker.toString();
	}
}
