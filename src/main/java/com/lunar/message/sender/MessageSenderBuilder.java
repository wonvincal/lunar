package com.lunar.message.sender;

import com.lunar.core.RequestTracker;
import com.lunar.core.TimerService;
import com.lunar.message.MessageFactory;
import com.lunar.message.sink.MessageSinkRef;

public class MessageSenderBuilder implements SenderBuilder {
	public static SenderBuilder of(){
		return new MessageSenderBuilder();
	}
	MessageSenderBuilder(){
	}
	@Override
	public ServiceStatusSender buildServiceStatusSender(int systemId, MessageSender messageSender){
		return ServiceStatusSender.of(systemId, messageSender);
	}
	@Override
	public RequestTracker buildRequestTracker(MessageSinkRef self, TimerService timerService, MessageFactory messageFactory, RequestSender messageSender) {
		return RequestTracker.of(self, timerService, messageFactory, messageSender);
	}
	@Override
	public RequestSender buildRequestSender(MessageSender messageSender) {
		return RequestSender.of(messageSender);
	}
	@Override
	public OrderSender buildOrderSender(MessageSender messageSender) {
		return OrderSender.of(messageSender);
	}
}
