package com.lunar.order;

import org.agrona.DirectBuffer;

import com.lunar.core.SequencingOnlyChannel;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;

/**
 * Non thread safe 
 * 
 * How to determine if an order hasn't been created!
 * 
 * 1) OrderAccepted - create order if an order hasn't been created (e.g. A trade may come first before the OrderAccepted)
 * 2) Trade - create order if an order hasn't been created (e.g. An order that hits bid or lift offer may have only one Trade back)
 * 3) OrderCancelled - create order if an order hasn't been created (e.g. An Immediate-Or-Cancel with nothing out there, only one OrderCancelled will be back)
 * 4) OrderRejected - create order, then set it as rejected
 * 5) OrderExpired - create order if an order hasn't been created (e.g. An order arrives into the market just after the market closes)
 * 
 * 'create order if an order hasn't been created' - this involves a look up that we try to avoid
 * 
 * @author Calvin
 *
 */
public class OrderContext {
	/**
	 * Modify by OrderContextManager only
	 */
	private final SequencingOnlyChannel channel;
	private Order order;
	private OrderUpdateHandler orderStateChangeHandler;

	public static OrderContext of(OrderUpdateHandler orderStateChangeHandler, SequencingOnlyChannel channel){
		return new OrderContext(null, orderStateChangeHandler, channel);
	}
	
	public static OrderContext of(Order order, OrderUpdateHandler orderStateChangeHandler, SequencingOnlyChannel channel){
		return new OrderContext(order, orderStateChangeHandler, channel);
	}
	
	OrderContext(Order order, OrderUpdateHandler orderStateChangeHandler, SequencingOnlyChannel channel){
		this.order = order;
		this.orderStateChangeHandler = orderStateChangeHandler;
		this.channel = channel;
	}
	
	SequencingOnlyChannel channel(){
		return channel;
	}

	Order order(){
		return order;
	}
	
	OrderContext order(Order order){
		this.order = order;
		return this;
	}

	OrderUpdateHandler orderStateChangeHandler(){
		return orderStateChangeHandler;
	}
	
	OrderContext orderStateChangeHandler(OrderUpdateHandler value){
		this.orderStateChangeHandler = value;
		return this;
	}

	void onOrderAcceptedAsFirstUpdate(NewOrderRequest request, DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted, long updateTime){
		this.order = Order.of(request, 
				accepted.status(), 
				accepted.leavesQty(),
				accepted.cumulativeQty(),
				updateTime,
				updateTime ).orderId(accepted.orderId());
		this.orderStateChangeHandler.onAcceptedAsFirstUpdate(this, buffer, offset, accepted);		
	}
	
	/**
	 * Order Accepted
	 * 
	 * @param request
	 * @param accepted
	 */
	void onOrderAccepted(DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder accepted){
		// Not sure for the case of hitting bid or lifting offer, whether
		// OrderAccepted or Trade would come first
		this.order.status(accepted.status())
			.leavesQty(accepted.leavesQty())
			.cumulativeExecQty(accepted.cumulativeQty()).orderId(accepted.orderId());
		this.orderStateChangeHandler.onAccepted(this, buffer, offset, accepted);			
	}

	void onOrderRejectedAsFirstUpdate(NewOrderRequest request, DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected, String reason){
		this.order = Order.of(request, 
				rejected.status(), 
				rejected.leavesQty(), 
				rejected.cumulativeQty()).orderId(rejected.orderId());
		this.order.reason(reason);
		this.orderStateChangeHandler.onRejectedAsFirstUpdate(this, buffer, offset, rejected);
	}

	/**
	 * Order Rejected
	 * @param request
	 * @param rejected
	 */
	void onOrderRejected(DirectBuffer buffer, int offset, OrderRejectedSbeDecoder rejected){
		this.order.status(rejected.status())
		.leavesQty(rejected.leavesQty())
		.cumulativeExecQty(rejected.cumulativeQty());
		this.orderStateChangeHandler.onRejected(this, buffer, offset, rejected);
	}
	
	void onOrderCancelledUnsolicated(){
		
	}
	
	void onOrderCancelledAsFirstUpdate(CancelOrderRequest cancelRequest, NewOrderRequest newOrderRequest, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled){
		this.order = Order.of(newOrderRequest, 
				cancelled.status(), 
				cancelled.leavesQty(),
				cancelled.cumulativeQty()).orderId(cancelled.orderId());
		this.orderStateChangeHandler.onCancelledAsFirstUpdate(this, buffer, offset, cancelled);
	}
	
	/**
	 * TODO A guessed interface to handle order cancelled....it will be very different once
	 * we have the actual market connectivity
	 * @param cancelOrderRequest
	 * @param newOrderRequest
	 * @param buffer
	 * @param offset
	 * @param cancelled
	 */
	void onOrderCancelled(DirectBuffer buffer, 
			int offset, 
			OrderCancelledSbeDecoder cancelled,
			int overideOrderSid){
		this.order.status(cancelled.status());
		this.orderStateChangeHandler.onCancelled(this, buffer, offset, cancelled, overideOrderSid);
	}
	
	void onOrderExpiredAsFirstUpdate(NewOrderRequest request, DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired){
		this.order = Order.of(request, 
				expired.status(), 
				expired.leavesQty(), 
				expired.cumulativeQty()).orderId(expired.orderId());
		this.orderStateChangeHandler.onExpiredAsFirstUpdate(this, buffer, offset, expired);
	}

	/**
	 * Special method for the case of receiving order expired as an order cancelled
	 * @param request
	 * @param buffer
	 * @param offset
	 * @param cancelled
	 */
	void onOrderExpiredAsFirstUpdate(NewOrderRequest request, DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled){
		this.order = Order.of(request, 
				cancelled.status(), 
				cancelled.leavesQty(), 
				cancelled.cumulativeQty()).orderId(cancelled.orderId());
		this.orderStateChangeHandler.onExpiredAsFirstUpdate(this, buffer, offset, cancelled);
	}

	void onOrderExpired(DirectBuffer buffer, int offset, OrderExpiredSbeDecoder expired){
		this.order.status(expired.status());
		this.orderStateChangeHandler.onExpired(this, buffer, offset, expired);
	}
	
	void onOrderExpired(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder cancelled){
		this.order.status(cancelled.status());
		this.orderStateChangeHandler.onExpired(this, buffer, offset, cancelled);
	}

	void onOrderAmended(AmendOrderRequest request, DirectBuffer buffer, int offset, OrderAmendedSbeDecoder amended){
		// TODO: something special about amend, it may have more than one client order ID
		// TODO: revisit when we can test
		this.order.status(amended.status());
		this.orderStateChangeHandler.onAmended(this, buffer, offset, amended);
	}

	void onOrderCancelRejected(CancelOrderRequest request, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected){
		this.order.status(cancelRejected.status());
		this.orderStateChangeHandler.onCancelRejected(this, buffer, offset, cancelRejected);;
	}

	void onOrderCancelRejectedAsFirstUpdate(CancelOrderRequest request, DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder cancelRejected){
		this.order.status(cancelRejected.status());
		this.orderStateChangeHandler.onCancelRejectedAsFirstUpdate(this, buffer, offset, cancelRejected);;
	}

	/**
	 * No need to notify OrderStateChangeHandler because no state will be changed.
	 * @param request
	 * @param amendRejected
	 */
	void onOrderAmendRejected(AmendOrderRequest request, DirectBuffer buffer, int offset, OrderAmendRejectedSbeDecoder amendRejected){
		this.order.status(amendRejected.status());
	}
	
	void onTradeCreatedAsFirstUpdate(NewOrderRequest request, DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int overrideTradeSid){
		this.order = Order.of(request, 
				trade.status(), 
				trade.leavesQty(), 
				trade.cumulativeQty()).orderId(trade.orderId());
		this.orderStateChangeHandler.onTradeCreatedAsFirstUpdate(this, buffer, offset, trade, overrideTradeSid);
	}

	void onTradeCreated(DirectBuffer buffer, int offset, TradeCreatedSbeDecoder trade, int overrideTradeSid){
		// TODO Change executed and leaves quantity
		this.order.status(trade.status());
		this.orderStateChangeHandler.onTradeCreated(this, buffer, offset, trade, overrideTradeSid);
	}
}
