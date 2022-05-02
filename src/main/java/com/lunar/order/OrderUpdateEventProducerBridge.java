package com.lunar.order;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.lunar.core.TimerService;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.service.ServiceConstant;

public class OrderUpdateEventProducerBridge implements MatchingEngineOrderUpdateHandler{
	private final MutableDirectBuffer buffer;
	private final MutableDirectBuffer stringBuffer;
	private final int channelId;
	private final AtomicLong channelSeq;
	private final OrderUpdateEventProducer ordUpdEventProducer;
	private final TimerService timerService;
	
	// Codec
	private OrderAcceptedSbeEncoder orderAcceptedEncoder = new OrderAcceptedSbeEncoder();
	private OrderExpiredSbeEncoder orderExpiredEncoder = new OrderExpiredSbeEncoder();
	private OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
	private OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
	private OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder = new OrderCancelRejectedSbeEncoder();
	private TradeCreatedSbeEncoder tradeCreatedEncoder = new TradeCreatedSbeEncoder();

	public static OrderUpdateEventProducerBridge of(int channelId, AtomicLong channelSeq, OrderUpdateEventProducer ordUpdEventProducer, TimerService timerService){
		return new OrderUpdateEventProducerBridge(channelId, channelSeq, ordUpdEventProducer, timerService);
	}
	
	OrderUpdateEventProducerBridge(int channelId, AtomicLong channelSeq, OrderUpdateEventProducer ordUpdEventProducer, TimerService timerService){
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		this.channelId = channelId;
		this.channelSeq = channelSeq;
		this.ordUpdEventProducer = ordUpdEventProducer;
		this.timerService = timerService;
	}
	
	@Override
	public void onOrderAccepted(Order order) {
		OrderSender.encodeOrderAcceptedOnly(buffer, 
		0, 
		orderAcceptedEncoder,
		channelId,
		channelSeq.getAndIncrement(),
		order.sid(),
		order.status(),
		order.limitPrice(),
		order.cumulativeExecQty(),
		order.leavesQty(),
		order.side(),
		order.orderId(),
		order.secSid(),
		ExecutionType.NEW,
		order.createTime());
		ordUpdEventProducer.onOrderAccepted(buffer, 0);
	}

	@Override
	public void onOrderExpired(Order order) {
		OrderSender.encodeOrderExpiredOnly(buffer, 
				0, 
				orderExpiredEncoder, 
				channelId, 
				channelSeq.getAndIncrement(), 
				order.sid(), 
				order.orderId(), 
				order.secSid(), 
				order.side(), 
				order.limitPrice(), 
				order.cumulativeExecQty(), 
				order.leavesQty(), 
				order.status(), 
				order.updateTime());

		ordUpdEventProducer.onOrderExpired(buffer, 0);
	}

	@Override
	public void onTradeCreated(Order order, Trade trade) {
		OrderSender.encodeTradeCreatedOnly(buffer, 
		0, 
		tradeCreatedEncoder, 
		channelId, 
		channelSeq.getAndIncrement(), 
		trade.sid(), 
		order.sid(), 
		order.orderId(), 
		order.status(), 
		order.side(), 
		order.leavesQty(), 
		order.cumulativeExecQty(), 
		OrderSender.prepareExecutionId(trade.executionId(), stringBuffer),
		trade.executionPrice(), 
		trade.executionQty(), 
		order.secSid(), 
		trade.createTime());
		ordUpdEventProducer.onTradeCreated(buffer, 0);
	}

	@Override
	public void onOrderRejected(Order order) {
		OrderSender.encodeOrderRejectedOnly(buffer, 
		0, 
		orderRejectedEncoder,
		channelId,
		channelSeq.getAndIncrement(),
		order.sid(),
		order.orderId(),
		order.secSid(),
		order.side(),
		order.limitPrice(),
		order.cumulativeExecQty(),
		order.leavesQty(),
		order.status(),
		order.orderRejectType(),
		OrderSender.prepareRejectedReason(order.reason(), stringBuffer),
		order.createTime());

		ordUpdEventProducer.onOrderRejected(buffer, 0);
	}

	@Override
	public void onOrderCancelled(Order order) {
		OrderSender.encodeOrderCancelledOnly(buffer, 
		0, 
		orderCancelledEncoder, 
		channelId, 
		channelSeq.getAndIncrement(), 
		order.sid(), 
		order.sid(), 
		order.status(), 
		order.limitPrice(), 
		order.side(), 
		order.orderId(), 
		order.secSid(), 
		ExecutionType.CANCEL, 
		order.leavesQty(), 
		order.cumulativeExecQty(), 
		order.updateTime(),
		order.quantity());
		ordUpdEventProducer.onOrderCancelled(buffer, 0);
	}

	@Override
	public void onOrderCancelled(CancelOrderRequest request, Order order) {
		OrderSender.encodeOrderCancelledOnly(buffer, 
		0, 
		orderCancelledEncoder, 
		channelId, 
		channelSeq.getAndIncrement(), 
		request.orderSid(), 
		order.sid(), 
		order.status(), 
		order.limitPrice(), 
		order.side(), 
		order.orderId(), 
		order.secSid(), 
		ExecutionType.CANCEL, 
		order.leavesQty(), 
		order.cumulativeExecQty(), 
		order.updateTime(),
		order.quantity());
		ordUpdEventProducer.onOrderCancelled(buffer, 0);
	}

	@Override
	public void onCancelRejected(CancelOrderRequest request, OrderCancelRejectType rejectType, String reason,
			Order order) {
		OrderSender.encodeOrderCancelRejectedOnly(buffer, 
				0, 
				orderCancelRejectedEncoder,
				channelId,
				channelSeq.getAndIncrement(),
				request.orderSid(),
				OrderStatus.REJECTED,
				request.secSid(),
				rejectType,
				OrderSender.prepareRejectedReason(reason, stringBuffer),
				ExecutionType.CANCEL_REJECT,
				timerService.toNanoOfDay());
		ordUpdEventProducer.onOrderCancelRejected(buffer, 0);
	}
	
	@Override
	public void onEndOfRecovery(){
		ordUpdEventProducer.onEndOfRecovery();
	}
}
