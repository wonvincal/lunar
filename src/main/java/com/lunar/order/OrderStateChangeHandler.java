package com.lunar.order;

import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;

/**
 * Handler for order state changes that impacts an order
 * 
 * Listener to this interface would be able to construct an order book of a particular security
 *  
 * @author Calvin
 *
 */
public interface OrderStateChangeHandler {
	void onNew(OrderAcceptedSbeDecoder accepted);
	void onAmended(OrderAmendedSbeDecoder amended);
	
	/**
	 * Failed order due to internal system problem will be treated as rejected as well
	 * @param rejected
	 */
	void onRejected(OrderRejectedSbeDecoder rejected);
	void onCancelled(OrderCancelledSbeDecoder cancelled);
	void onTrade(TradeSbeDecoder trade);
	void onExpired(OrderExpiredSbeDecoder expired);
	
	/**
	 * AmendRejected may move an order into another state 
	 * @param amendRejected
	 */
	void onAmendRejected(OrderAmendRejectedSbeDecoder amendRejected);
	
	public static OrderStateChangeHandler NULL_HANDLER = new OrderStateChangeHandler() {

		@Override
		public void onNew(OrderAcceptedSbeDecoder accepted) {
		}

		@Override
		public void onAmended(OrderAmendedSbeDecoder amended) {
		}

		@Override
		public void onRejected(OrderRejectedSbeDecoder rejected) {
		}

		@Override
		public void onCancelled(OrderCancelledSbeDecoder cancelled) {
		}

		@Override
		public void onTrade(TradeSbeDecoder trade) {
		}

		@Override
		public void onExpired(OrderExpiredSbeDecoder expired) {
		}

		@Override
		public void onAmendRejected(OrderAmendRejectedSbeDecoder amendRejected) {
		}
	};
}
