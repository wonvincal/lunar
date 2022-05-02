package com.lunar.message.sender;

import java.util.Optional;

import com.lunar.core.RequestTracker;
import com.lunar.core.TimerService;
import com.lunar.message.MessageFactory;
import com.lunar.message.sink.MessageSinkRef;

public class TestSenderBuilder implements SenderBuilder {
	private Optional<ServiceStatusSender> serviceStatusSender = Optional.empty();
	private Optional<RequestSender> requestSender = Optional.empty();
	private Optional<RequestTracker> requestTracker = Optional.empty();
	private Optional<OrderSender> orderSender = Optional.empty();
	
	public TestSenderBuilder() {}
	public SenderBuilder serviceStatusSender(ServiceStatusSender value){
		serviceStatusSender = Optional.of(value);
		return this;
	}
	public SenderBuilder requestSender(RequestSender value){
		requestSender = Optional.of(value);
		return this;
	}
	public SenderBuilder requestTracker(RequestTracker value){
		requestTracker = Optional.of(value);
		return this;
	}
	public SenderBuilder serviceStatusSender(OrderSender value){
		orderSender = Optional.of(value);
		return this;
	}
	@Override
	public ServiceStatusSender buildServiceStatusSender(int systemId, MessageSender messageSender) {
		return serviceStatusSender.isPresent() ? serviceStatusSender.get() : ServiceStatusSender.of(systemId, messageSender); 
	}
	@Override
	public RequestTracker buildRequestTracker(MessageSinkRef self, TimerService timerService,
			MessageFactory messageFactory, RequestSender messageSender) {
		return requestTracker.isPresent() ? requestTracker.get() : RequestTracker.of(self, timerService, messageFactory, messageSender);
	}
	@Override
	public RequestSender buildRequestSender(MessageSender messageSender) {
		return requestSender.isPresent() ? requestSender.get() : RequestSender.of(messageSender);
	}
	@Override
	public OrderSender buildOrderSender(MessageSender messageSender) {
		return orderSender.isPresent() ? orderSender.get() : OrderSender.of(messageSender);
	}
}
