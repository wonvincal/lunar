package com.lunar.order;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;

public class OrderUpdateEventProducer {
	private static final Logger LOG = LogManager.getLogger(OrderUpdateEventProducer.class);
	private final RingBuffer<OrderUpdateEvent> ringBuffer;
	
	public static OrderUpdateEventProducer of(RingBuffer<OrderUpdateEvent> ringBuffer){
		return new OrderUpdateEventProducer(ringBuffer);
	}
	
	OrderUpdateEventProducer(RingBuffer<OrderUpdateEvent> ringBuffer){
		LOG.info("Create OrderUpdateEventProducer");
		this.ringBuffer = ringBuffer;
	}

	public long onOrderAccepted(DirectBuffer buffer, int offset){
//		LOG.debug("Publish order accepted to ring buffer");
		return onData(OrderUpdateEvent.ORDER_ACCEPTED, OrderAcceptedSbeEncoder.TEMPLATE_ID, OrderAcceptedSbeEncoder.BLOCK_LENGTH, buffer, offset, OrderAcceptedSbeDecoder.BLOCK_LENGTH);
	}
	
	public long onOrderAccepted(MutableDirectBuffer messageBuffer, 
	        OrderAcceptedSbeEncoder encoder,
	        int channelId,
	        long channelSeq,
	        Order order){
        OrderSender.encodeOrderAcceptedOnly(messageBuffer, 
                0, 
                encoder,
                channelId,
                channelSeq,
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
	    return onOrderAccepted(messageBuffer, 0);
	}

	public long onOrderCancelled(DirectBuffer buffer, int offset){
//		LOG.debug("Publish order cancelled to ring buffer");
		return onData(OrderUpdateEvent.ORDER_CANCELLED, OrderCancelledSbeEncoder.TEMPLATE_ID, OrderCancelledSbeEncoder.BLOCK_LENGTH, buffer, offset, OrderCancelledSbeDecoder.BLOCK_LENGTH);
	}

	public long onOrderCancelled(MutableDirectBuffer messageBuffer,
	        OrderCancelledSbeEncoder encoder,
	        int channelId,
	        long channelSeq,
	        Order order){
	    OrderSender.encodeOrderCancelledOnly(messageBuffer, 
	            0, 
	            encoder, 
	            channelId, 
	            channelSeq, 
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
	    return onOrderCancelled(messageBuffer, 0);
	}
	
	public long onOrderCancelRejected(DirectBuffer buffer, int offset){
		return onData(OrderUpdateEvent.ORDER_CANCEL_REJECTED, OrderCancelRejectedSbeEncoder.TEMPLATE_ID, OrderCancelRejectedSbeEncoder.BLOCK_LENGTH, buffer, offset, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH);
	}
	
	public long onCancelRejected(MutableDirectBuffer messageBuffer, 
	        MutableDirectBuffer stringBuffer,
	        OrderCancelRejectedSbeEncoder encoder,
	        int channelId,
	        long channelSeq,
	        CancelOrderRequest request, 
	        OrderCancelRejectType rejectType, 
	        String reason,
	        long updateTime,
	        OrderStatus status){
	    OrderSender.encodeOrderCancelRejectedOnly(messageBuffer, 
	            0, 
	            encoder,
	            channelId,
	            channelSeq,
	            request.orderSid(),
	            status,
	            request.secSid(),
	            rejectType,
	            OrderSender.prepareRejectedReason(reason, stringBuffer),
	            ExecutionType.CANCEL_REJECT,
	            updateTime);
	    return onOrderCancelled(messageBuffer, 0);
	}

	public long onOrderExpired(DirectBuffer buffer, int offset){
		return onData(OrderUpdateEvent.ORDER_EXPIRED, OrderExpiredSbeEncoder.TEMPLATE_ID, OrderExpiredSbeEncoder.BLOCK_LENGTH, buffer, offset, OrderExpiredSbeDecoder.BLOCK_LENGTH);
	}

	public long onOrderExpired(MutableDirectBuffer messageBuffer, 
	        OrderExpiredSbeEncoder encoder,
	        int channelId,
	        long channelSeq,
	        Order order){
	    OrderSender.encodeOrderExpiredOnly(messageBuffer, 
	            0, 
	            encoder, 
	            channelId, 
	            channelSeq, 
	            order.sid(), 
	            order.orderId(), 
	            order.secSid(), 
	            order.side(), 
	            order.limitPrice(), 
	            order.cumulativeExecQty(), 
	            order.leavesQty(), 
	            order.status(), 
	            order.updateTime());
	    return onOrderExpired(messageBuffer, 0);
	}
	
	public long onTradeCreated(DirectBuffer buffer, int offset){
//		LOG.debug("Publish trade created to ring buffer");
		return onData(OrderUpdateEvent.TRADE_CREATED, TradeCreatedSbeEncoder.TEMPLATE_ID, TradeCreatedSbeEncoder.BLOCK_LENGTH, buffer, offset, TradeCreatedSbeDecoder.BLOCK_LENGTH);
	}

	public long onTradeCreate(MutableDirectBuffer messageBuffer, 
	        MutableDirectBuffer stringBuffer, 
	        TradeCreatedSbeEncoder encoder,
	        int channelId,
	        long channelSeq,
	        Order order,
	        Trade trade){
	    OrderSender.encodeTradeCreatedOnly(messageBuffer, 
	            0, 
	            encoder, 
	            channelId, 
	            channelSeq, 
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
	    return onTradeCreated(messageBuffer, 0);
	}
	
	public long onTradeCancelled(DirectBuffer buffer, int offset){
		return onData(OrderUpdateEvent.TRADE_CANCELLED, TradeCancelledSbeEncoder.TEMPLATE_ID, TradeCancelledSbeEncoder.BLOCK_LENGTH, buffer, offset, TradeCancelledSbeDecoder.BLOCK_LENGTH);
	}
	
	public long onOrderRejected(DirectBuffer buffer, int offset){
		return onData(OrderUpdateEvent.ORDER_REJECTED, OrderRejectedSbeEncoder.TEMPLATE_ID, OrderRejectedSbeEncoder.BLOCK_LENGTH, buffer, offset, OrderRejectedSbeEncoder.BLOCK_LENGTH);
	}
	
	public long onOrderRejected(MutableDirectBuffer messageBuffer, 
	        MutableDirectBuffer stringBuffer, 
	        OrderRejectedSbeEncoder encoder,
	        int channelId,
	        long channelSeq,
	        Order order){
	    OrderSender.encodeOrderRejectedOnly(messageBuffer, 
	            0, 
	            encoder,
	            channelId,
	            channelSeq,
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
	    return onOrderRejected(messageBuffer, 0);
	}

	public void onEndOfRecovery(){
		onData(OrderUpdateEvent.END_OF_RECOVERY_EVENT.updateType(), 
				OrderUpdateEvent.END_OF_RECOVERY_EVENT.templateId(), 
				OrderUpdateEvent.END_OF_RECOVERY_EVENT.blockLength(), 
				OrderUpdateEvent.END_OF_RECOVERY_EVENT.buffer(), 
				0, 
				0);
	}
	
	private long onData(int updateType, int templateId, int blockLength, DirectBuffer buffer, int offset, int length){
		long sequence = -1;
		try {
			sequence = ringBuffer.tryNext();
			OrderUpdateEvent event = ringBuffer.get(sequence);
			event.merge(updateType, templateId, blockLength, buffer, offset, length);
		} 
		catch (InsufficientCapacityException e){
			return MessageSink.INSUFFICIENT_SPACE;
		}
		finally {
			if (sequence != -1){
				ringBuffer.publish(sequence);
			}
		}
		return MessageSink.OK;		
	}	
}
