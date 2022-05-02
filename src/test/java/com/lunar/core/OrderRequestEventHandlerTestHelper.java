package com.lunar.core;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventPoller.PollState;
import com.lmax.disruptor.RingBuffer;
import com.lunar.order.OrderRequest;
import com.lunar.order.OrderRequestEvent;

/**
 * A helper class to publish message into an EventHandler
 * @author wongca
 *
 */
public class OrderRequestEventHandlerTestHelper {
	private final EventHandler<OrderRequestEvent> eventHandler;
	private final RingBuffer<OrderRequestEvent> ringBuffer;
	private final EventPoller<OrderRequestEvent> poller;

	public OrderRequestEventHandlerTestHelper(EventHandler<OrderRequestEvent> eventHandler, RingBuffer<OrderRequestEvent> ringBuffer){
		this.eventHandler = eventHandler;
		this.ringBuffer = ringBuffer;
		this.poller = ringBuffer.newPoller();
	}
	public RingBuffer<OrderRequestEvent> ringBuffer(){
		return ringBuffer;
	}
	public void onData(OrderRequest request){
		ringBuffer.publishEvent(OrderRequestEvent.ORDER_REQUEST_EVENT_TRANSLATOR, request);
	}
	public boolean pushNextMessage(){
		PollState pollState = null;
		try {
			pollState = poller.poll(new  EventPoller.Handler<OrderRequestEvent>() {
				@Override
				public boolean onEvent(OrderRequestEvent event, long sequence, boolean endOfBatch) throws Exception {
					eventHandler.onEvent(event, sequence, endOfBatch);
					return true;
				}
			});
		} 
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (pollState == null || pollState != PollState.PROCESSING){
			return false;
		}
		return true;
	}
}
