package com.lunar.message.sender;

import com.lunar.core.RequestTracker;
import com.lunar.core.TimerService;
import com.lunar.message.MessageFactory;
import com.lunar.message.sink.MessageSinkRef;

public interface SenderBuilder {
	ServiceStatusSender buildServiceStatusSender(int systemId, MessageSender messageSender);
	RequestTracker buildRequestTracker(MessageSinkRef self, TimerService timerService, MessageFactory messageFactory, RequestSender messageSender);
	RequestSender buildRequestSender(MessageSender messageSender);
	OrderSender buildOrderSender(MessageSender messageSender);
}
