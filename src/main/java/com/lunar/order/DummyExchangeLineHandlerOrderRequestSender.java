package com.lunar.order;

import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;

/**
 * Send messages to DummyExchangeService thru its Disruptor ring buffer
 * Thread Safety: No
 * @author wongca
 *
 */
public class DummyExchangeLineHandlerOrderRequestSender implements LineHandlerOrderRequestSender {
	private MessageSinkRef exchangeSinkRef;
	private OrderSender plainSender;

	/**
	 * 
	 * @param exchangeSinkRef
	 * @param orderSender Please create from a fresh new Messenger (hint: Messenger.createChildMessenger)
	 * @return
	 */
	public static DummyExchangeLineHandlerOrderRequestSender of(MessageSinkRef exchangeSinkRef, OrderSender orderSender){
		return new DummyExchangeLineHandlerOrderRequestSender(exchangeSinkRef, orderSender);
	}

	DummyExchangeLineHandlerOrderRequestSender(MessageSinkRef exchangeSinkRef, OrderSender orderSender){
		this.exchangeSinkRef = exchangeSinkRef;
		this.plainSender = orderSender;
	}
	
	@Override
	public long send(OrderRequest request) {
		if (request instanceof NewOrderRequest){
			// We don't want to use Messenger.sendNewOrder which keeps track of the
			// lifecycle of the order request using OrderReqAndExecTracker internally
			return plainSender.sendNewOrder(exchangeSinkRef, (NewOrderRequest)request);
		}
		else if (request instanceof CancelOrderRequest){
			return plainSender.sendCancelOrder(exchangeSinkRef, (CancelOrderRequest)request);
		}
		return MessageSink.FAILURE;
	}
}
