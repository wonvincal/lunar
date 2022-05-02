package com.lunar.order;

import com.lmax.disruptor.EventTranslatorOneArg;

public class OrderRequestEvent {
	private OrderRequest request;
	public OrderRequestEvent(){}
	public void request(OrderRequest request){
		this.request = request;
	}
	public OrderRequest request(){
		return request;
	}
	@Override
	public String toString() {
		return request.toString();
	}
	public static final EventTranslatorOneArg<OrderRequestEvent, OrderRequest> ORDER_REQUEST_EVENT_TRANSLATOR = new EventTranslatorOneArg<OrderRequestEvent, OrderRequest>() {
		@Override
		public void translateTo(OrderRequestEvent event, long sequence, OrderRequest input) {
			event.request(input);
		}
	};
}
