package com.lunar.order;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;

/**
 * Handler for order state changes that impacts an order
 * 
 * Listener to this interface would be able to construct an order book of a particular security
 *  
 * @author Calvin
 *
 */
public interface OrderUpdateHandler {
	void onAcceptedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted);
	void onAccepted(OrderContext context, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted);

	void onAmended(OrderContext context, DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended);
	
	/**
	 * Failed order due to internal system problem will be treated as rejected as well
	 * @param rejected
	 */
	void onRejectedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected);
	void onRejected(OrderContext context, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected);
	
	void onCancelledAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	void onCancelled(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	void onCancelled(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled, int overrideOrderSid);
	
	void onCancelRejectedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder rejected);
	void onCancelRejected(OrderContext context, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder rejected);

	void onTradeCreatedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int overrideTradeSid);
	void onTradeCreated(OrderContext context, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int overrideTradeSid);
	
	void onExpiredAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired);
	void onExpiredAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	void onExpired(OrderContext context, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired);
	void onExpired(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled);
	
	public static OrderUpdateHandler NULL_HANDLER = new OrderUpdateHandler() {

		@Override
		public void onAccepted(OrderContext context, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
		}

		@Override
		public void onAcceptedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted) {
		}

		@Override
		public void onAmended(OrderContext context, DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended) {
		}

		@Override
		public void onRejected(OrderContext context, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected) {
		}

		@Override
		public void onRejectedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected) {
		}

		@Override
		public void onCancelled(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}

        @Override
        public void onCancelled(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled, int overrideOrderSid) {
        }

        @Override
		public void onCancelledAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}

		@Override
		public void onCancelRejectedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder rejected){
		}

		@Override
		public void onCancelRejected(OrderContext context, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder rejected){
		}

		@Override
		public void onTradeCreated(OrderContext context, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int overrideTradeSid) {
		}

		@Override
		public void onTradeCreatedAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int overrideTradeSid) {
		}

		@Override
		public void onExpired(OrderContext context, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired) {
		}

		@Override
		public void onExpired(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}

		@Override
		public void onExpiredAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired) {
		}

		@Override
		public void onExpiredAsFirstUpdate(OrderContext context, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled) {
		}
	};
}
