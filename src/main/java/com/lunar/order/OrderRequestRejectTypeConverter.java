package com.lunar.order;

import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRequestRejectType;

public class OrderRequestRejectTypeConverter {
	/**
	 * Made some special arrangement on the actual values, so that
	 * OrderRejectType.value() == OrderRequestRejectType.value()
	 * @param rejectType
	 * @return
	 */
	public static OrderRequestRejectType from(OrderRejectType rejectType){
		return OrderRequestRejectType.get(rejectType.value());
	}
	/**
	 * Made some special arrangement on the actual values, so that
	 * OrderCancelRejectType.value() == OrderRequestRejectType.value()
	 * @param rejectType
	 * @return
	 */
	public static OrderRequestRejectType from(OrderCancelRejectType rejectType){
		return OrderRequestRejectType.get(rejectType.value());
	}
}
