package com.lunar.order;

import com.lunar.core.SubscriberList;
import com.lunar.core.TimerService;
import com.lunar.core.TriggerInfo;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sender.PerformanceSender;
import com.lunar.message.sink.MessageSinkRefMgr;

public class OrderRequestCompletionSender implements OrderRequestCompletionHandler {
	private final Messenger childMessenger;
	private final int targetSinkId;
	private final MessageSinkRefMgr refMgr;
	private final OrderSender orderSender;
	private final PerformanceSender performanceSender;
	private final SubscriberList performanceSubscribers;
	private final TimerService timerService;

	public static OrderRequestCompletionSender createWithChildMessenger(Messenger messenger, int targetSinkId, SubscriberList performanceSubscribers){
		return new OrderRequestCompletionSender(messenger.createChildMessenger(), targetSinkId, performanceSubscribers);
	}
	
	OrderRequestCompletionSender(Messenger messenger, int targetSinkId, SubscriberList performanceSubscribers){
		this.childMessenger = messenger;
		this.refMgr = messenger.referenceManager();
		this.orderSender = childMessenger.orderSender();
		this.targetSinkId = targetSinkId;
		this.performanceSender = childMessenger.performanceSender();
		this.performanceSubscribers = performanceSubscribers;
		this.timerService = messenger.timerService();
	}
	
	@Override
	public void complete(OrderRequest request) {
		orderSender.sendOrderRequestCompletion(refMgr.get(targetSinkId), request.clientKey(), request.orderSid(), request.completionType());
	}

	@Override
	public void completeWithOrdSidOnly(int orderSid) {
		orderSender.sendOrderRequestCompletionWithOrdSidOnly(refMgr.get(targetSinkId), orderSid, OrderRequestCompletionType.OK);		}

	@Override
	public void rejectWithOrdSidOnly(int orderSid, OrderRequestRejectType rejectType, byte[] reason) {
		orderSender.sendOrderRequestCompletionWithOrdSidOnly(refMgr.get(targetSinkId), 
				orderSid, 
				OrderRequestCompletionType.REJECTED, 
				rejectType,
				reason);
	}

	@Override
	public void timeout(OrderRequest request) {
		orderSender.sendOrderRequestCompletion(refMgr.get(targetSinkId), request.clientKey(), request.orderSid(), OrderRequestCompletionType.REJECTED_INTERNALLY, OrderRequestRejectType.TIMEOUT_BEFORE_THROTTLE);			
		if (request instanceof NewOrderRequest){
			NewOrderRequest newOrderRequest = (NewOrderRequest)request;
			childMessenger.trySendEventWithThreeValues(EventCategory.EXECUTION,
					EventLevel.INFO, 
					EventType.THROTTLED,
					targetSinkId, 
					childMessenger.timerService().toNanoOfDay(),
					"Order timeout before throttle",
					EventValueType.ORDER_SID,
					request.orderSid(),
					EventValueType.SECURITY_SID,
					request.secSid(),
					EventValueType.SIDE,
					newOrderRequest.side().value());
		}
		else{
			childMessenger.trySendEventWithTwoValues(EventCategory.EXECUTION,
					EventLevel.INFO, 
					EventType.THROTTLED,
					targetSinkId, 
					childMessenger.timerService().toNanoOfDay(),
					"Order timeout before throttle",
					EventValueType.ORDER_SID,
					request.orderSid(),
					EventValueType.SECURITY_SID,
					request.secSid());			
		}
	}

	@Override
	public void timeoutAfterThrottled(OrderRequest request) {
		orderSender.sendOrderRequestCompletion(refMgr.get(targetSinkId), request.clientKey(), request.orderSid(), OrderRequestCompletionType.REJECTED_INTERNALLY, OrderRequestRejectType.TIMEOUT_AFTER_THROTTLED);
		if (request instanceof NewOrderRequest){
			NewOrderRequest newOrderRequest = (NewOrderRequest)request;
			childMessenger.trySendEventWithThreeValues(EventCategory.EXECUTION,
					EventLevel.INFO,
					EventType.THROTTLED,
					targetSinkId, 
					childMessenger.timerService().toNanoOfDay(),
					"Order timeout after throttled",
					EventValueType.ORDER_SID,
					request.orderSid(),
					EventValueType.SECURITY_SID,
					request.secSid(),
					EventValueType.SIDE,
					newOrderRequest.side().value());
		}
		else {
			childMessenger.trySendEventWithTwoValues(EventCategory.EXECUTION,
					EventLevel.INFO,
					EventType.THROTTLED,
					targetSinkId, 
					childMessenger.timerService().toNanoOfDay(),
					"Order timeout after throttled",
					EventValueType.ORDER_SID,
					request.orderSid(),
					EventValueType.SECURITY_SID,
					request.secSid());			
		}
	}
	
	@Override
	public void throttled(OrderRequest request) {
		orderSender.sendOrderRequestCompletion(refMgr.get(targetSinkId), request.clientKey(), request.orderSid(), OrderRequestCompletionType.REJECTED_INTERNALLY, OrderRequestRejectType.THROTTLED);
		
		if (request instanceof NewOrderRequest){
			NewOrderRequest newOrderRequest = (NewOrderRequest)request;
			childMessenger.trySendEventWithThreeValues(EventCategory.EXECUTION,
				EventLevel.INFO,
				EventType.THROTTLED,
				targetSinkId, 
				childMessenger.timerService().toNanoOfDay(),
				"Order throttled",
				EventValueType.ORDER_SID,
				request.orderSid(),
				EventValueType.SECURITY_SID,
				request.secSid(),
				EventValueType.SIDE,
				newOrderRequest.side().value());
		}
		else{
			childMessenger.trySendEventWithTwoValues(EventCategory.EXECUTION,
					EventLevel.INFO,
					EventType.THROTTLED,
					targetSinkId, 
					childMessenger.timerService().toNanoOfDay(),
					"Order throttled",
					EventValueType.ORDER_SID,
					request.orderSid(),
					EventValueType.SECURITY_SID,
					request.secSid());			
		}
	}

	@Override
	public void fail(OrderRequest request, Throwable e) {
		orderSender.sendOrderRequestCompletion(refMgr.get(targetSinkId), request.clientKey(), request.orderSid(), OrderRequestCompletionType.FAILED);
	}

	@Override
	public void fail(OrderRequest request, String description, Throwable e) {
		orderSender.sendOrderRequestCompletion(refMgr.get(targetSinkId), request.clientKey(), request.orderSid(), OrderRequestCompletionType.FAILED);
	}
	
	@Override
	public void sendToExchange(OrderRequest request, long timestamp) {
        final TriggerInfo triggerInfo = request.triggerInfo();
        final byte triggeredBy = triggerInfo.triggeredBy();
        final int triggerSeqNum = triggerInfo.triggerSeqNum();
        final long triggerNanoOfDay = triggerInfo.triggerNanoOfDay();
        if (triggeredBy > 0) {
            performanceSender.sendGenericTracker(performanceSubscribers.elements()[0], 
            		(byte)childMessenger.self().sinkId(), 
            		TrackerStepType.SENDING_ORDER, 
            		triggeredBy, 
            		triggerSeqNum, 
            		triggerNanoOfDay, timestamp);
        }
	}
	
	@Override
	public void receivedFromExchange(OrderRequest request) {
//        final TriggerInfo triggerInfo = request.triggerInfo();
//        final byte triggeredBy = triggerInfo.triggeredBy();
//        final int triggerSeqNum = triggerInfo.triggerSeqNum();
//        final long triggerNanoOfDay = triggerInfo.triggerNanoOfDay();
//        if (triggeredBy > 0) {
//            performanceSender.sendGenericTracker(performanceSubscribers.elements()[0], 
//            		(byte)childMessenger.self().sinkId(), 
//            		TrackerStepType.RECEIVED_EXECUTIONREPORT, 
//            		triggeredBy,
//            		triggerSeqNum, 
//            		triggerNanoOfDay,
//            		timerService.nanoTime());
//        }
	}
}
