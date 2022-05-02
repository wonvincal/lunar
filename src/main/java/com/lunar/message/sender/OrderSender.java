package com.lunar.message.sender;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.StringConstant;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CancelOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.NewCompositeOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeEncoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeEncoder;
import com.lunar.message.io.sbe.OrderRequestAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeEncoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeEncoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.service.ServiceConstant;
import com.lunar.util.StringUtil;

/**
 * Provide message type specific methods to publish message to a 
 * message sink.  It makes use of the underlying message publisher
 * to do the actual sending.
 * @author wongca
 *
 */
public class OrderSender {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(OrderSender.class);
	private final MessageSender msgSender;
	private final NewOrderRequestSbeEncoder orderNewSbe = new NewOrderRequestSbeEncoder();
	private final CancelOrderRequestSbeEncoder cancelOrderSbe = new CancelOrderRequestSbeEncoder();
	private final OrderRejectedSbeEncoder orderRejectedSbe = new OrderRejectedSbeEncoder();
	private final OrderRejectedWithOrderInfoSbeEncoder orderRejectedWithOrderInfoSbe = new OrderRejectedWithOrderInfoSbeEncoder();
	private final OrderAmendedSbeEncoder orderAmendedSbe = new OrderAmendedSbeEncoder();
	private final OrderAcceptedSbeEncoder orderAcceptedSbe = new OrderAcceptedSbeEncoder();
	private final OrderAcceptedWithOrderInfoSbeEncoder orderAcceptedWithOrderInfoSbe = new OrderAcceptedWithOrderInfoSbeEncoder();
	private final OrderCancelledSbeEncoder orderCancelledSbe = new OrderCancelledSbeEncoder();
	private final OrderCancelledWithOrderInfoSbeEncoder orderCancelledWithOrderInfoSbe = new OrderCancelledWithOrderInfoSbeEncoder();
	private final OrderRequestCompletionSbeEncoder orderRequestCompletionSbe = new OrderRequestCompletionSbeEncoder();
	private final OrderRequestAcceptedSbeEncoder orderRequestAcceptedSbe = new OrderRequestAcceptedSbeEncoder();
	private final OrderExpiredSbeEncoder orderExpiredSbe = new OrderExpiredSbeEncoder();
	private final OrderExpiredWithOrderInfoSbeEncoder orderExpiredWithOrderInfoSbe = new OrderExpiredWithOrderInfoSbeEncoder();
	private final OrderCancelRejectedSbeEncoder orderCancelRejectedSbe = new OrderCancelRejectedSbeEncoder();
	private final OrderCancelRejectedWithOrderInfoSbeEncoder orderCancelRejectedWithOrderInfoSbe = new OrderCancelRejectedWithOrderInfoSbeEncoder();
	private final TradeCreatedSbeEncoder tradeCreatedSbe = new TradeCreatedSbeEncoder();
	private final TradeCreatedWithOrderInfoSbeEncoder tradeCreatedWithOrderInfoSbe = new TradeCreatedWithOrderInfoSbeEncoder();
	private final TradeCancelledSbeEncoder tradeCancelledSbe = new TradeCancelledSbeEncoder();
	private final NewCompositeOrderRequestSbeEncoder compositeOrderNewSbe = new NewCompositeOrderRequestSbeEncoder();
	
	public static final byte[] ORDER_REJECTED_EMPTY_REASON = new byte[OrderRejectedSbeEncoder.reasonLength()];
	public static final byte[] ORDER_CANCEL_REJECTED_EMPTY_REASON = new byte[OrderCancelRejectedSbeEncoder.reasonLength()];
	public static final byte[] ORDER_EMPTY_REASON = new byte[OrderSbeEncoder.reasonLength()];
	public static final byte[] ORDER_REQUEST_COMPLETION_EMPTY_REASON = new byte[OrderRequestCompletionSbeEncoder.reasonLength()];
	
	static{
		Arrays.fill(ORDER_REJECTED_EMPTY_REASON, StringConstant.WHITESPACE);
		Arrays.fill(ORDER_CANCEL_REJECTED_EMPTY_REASON, StringConstant.WHITESPACE);
		Arrays.fill(ORDER_EMPTY_REASON, StringConstant.WHITESPACE);
		Arrays.fill(ORDER_REQUEST_COMPLETION_EMPTY_REASON, StringConstant.WHITESPACE);
	}
	
	public static OrderSender of(MessageSender msgSender){
		return new OrderSender(msgSender);
	}
	
	OrderSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public long sendOrderRequestAccepted(MessageSinkRef sink, int clientKey, int ordSid){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderRequestAcceptedEncodedLength(), sink, bufferClaim) >= 0L){
			encodeOrderRequestAccepted(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderRequestAcceptedSbe,
					clientKey,
					ordSid);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderRequestAccepted(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				orderRequestAcceptedSbe, 
				clientKey,
				ordSid);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public void sendOrderRequestCompletionWithClientKeyOnly(MessageSinkRef sink,
			int clientKey,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType){
		sendOrderRequestCompletion(sink, clientKey, 
				OrderRequestCompletionSbeEncoder.orderSidNullValue(), 
				completionType, 
				rejectType,
				ORDER_REQUEST_COMPLETION_EMPTY_REASON);
	}
			
	public void sendOrderRequestCompletionWithOrdSidOnly(MessageSinkRef sink,
			int ordSid,
			OrderRequestCompletionType completionType){
		sendOrderRequestCompletion(sink, OrderRequestCompletionSbeEncoder.clientKeyNullValue(), ordSid, completionType, 
				OrderRequestRejectType.VALID_AND_NOT_REJECT,
				ORDER_REQUEST_COMPLETION_EMPTY_REASON);
	}

	public void sendOrderRequestCompletionWithOrdSidOnly(MessageSinkRef sink,
			int ordSid,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType){
		sendOrderRequestCompletion(sink, OrderRequestCompletionSbeEncoder.clientKeyNullValue(), ordSid, 
				completionType, 
				rejectType,
				ORDER_REQUEST_COMPLETION_EMPTY_REASON);
	}

	public void sendOrderRequestCompletionWithOrdSidOnly(MessageSinkRef sink,
			int ordSid,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType,
			byte[] reason){
		sendOrderRequestCompletion(sink, OrderRequestCompletionSbeEncoder.clientKeyNullValue(), ordSid, 
				completionType, 
				rejectType,
				reason);
	}
	
	public void sendOrderRequestCompletion(MessageSinkRef sink,
			int clientKey,
			int ordSid,
			OrderRequestCompletionType completionType){
		sendOrderRequestCompletion(sink, clientKey, ordSid, completionType, OrderRequestRejectType.VALID_AND_NOT_REJECT, ORDER_REQUEST_COMPLETION_EMPTY_REASON);
	}

	public void sendOrderRequestCompletion(MessageSinkRef sink,
			int clientKey,
			int ordSid,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType){
		sendOrderRequestCompletion(sink, clientKey, ordSid, completionType, rejectType, ORDER_REQUEST_COMPLETION_EMPTY_REASON);
	}

	public void sendOrderRequestCompletion(MessageSinkRef sink,
			int clientKey,
			int ordSid,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType,
			byte[] reason){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderRequestCompletionEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeOrderRequestCompletion(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderRequestCompletionSbe,
					clientKey,
					ordSid,
					completionType,
					rejectType,
					reason);
			bufferClaim.commit();
			return;
		}
		int size = encodeOrderRequestCompletion(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				orderRequestCompletionSbe, 
				clientKey,
				ordSid,
				completionType,
				rejectType,
				reason);
		msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendOrderAccepted(MessageSinkRef sink,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			int price,
			int cumulativeQty,
			int leavesQty,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			long updateTime){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderAcceptedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeOrderAccepted(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderAcceptedSbe, 
					channelId,
					channelSeq,
					orderSid,
					status,
					price,
					cumulativeQty,
					leavesQty,
					side,
					orderId,
					secSid,
					execType,
					updateTime); 
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderAccepted(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				orderAcceptedSbe,
				channelId,
				channelSeq,
				orderSid,
				status,
				price,
				cumulativeQty,
				leavesQty,
				side,
				orderId,
				secSid,
				execType,
				updateTime); 
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	/**
	 * 
	 * @param sinks
	 * @param orderSid
	 * @param status
	 * @param price
	 * @param cumulativeQty
	 * @param leavesQty
	 * @param side
	 * @param orderId
	 * @param secSid
	 * @param execType
	 * @return Caution - returned array is a data structure that will be reused by other send/trySend 
	 * 		   methods
	 * 		   <br>[0] - Negative means at least one message couldn't be delivered
	 *         <br>[N] - Negative means message couldn't be delivered to sinks[N - 1], otherwise positive 
	 */
	public long sendOrderAccepted(MessageSinkRef[] sinks,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			int price,
			int cumulativeQty,
			int leavesQty,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			long updateTime,
			long[] results){
		int size = encodeOrderAccepted(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				orderAcceptedSbe,
				channelId,
				channelSeq,
				orderSid,
				status,
				price,
				cumulativeQty,
				leavesQty,
				side,
				orderId,
				secSid,
				execType,
				updateTime); 
		return msgSender.trySend(sinks, msgSender.buffer(), 0, size, results);
	}

	/**
	 * 
	 * @param sinks
	 * @param srcBuffer
	 * @param offset
	 * @param orderAccepted
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long sendOrderAccepted(MessageSinkRef[] sinks, DirectBuffer srcBuffer, int offset, OrderAcceptedSbeDecoder orderAccepted, long[] results){
		// encode a header, then direct copy from source buffer to message buffer
		msgSender.encodeHeader(
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				OrderAcceptedSbeEncoder.BLOCK_LENGTH,
				OrderAcceptedSbeEncoder.TEMPLATE_ID,
				OrderAcceptedSbeEncoder.SCHEMA_ID,
				OrderAcceptedSbeEncoder.SCHEMA_VERSION,
				orderAccepted.encodedLength());
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, orderAccepted.encodedLength());
		return msgSender.trySend(sinks, msgSender.buffer(), 0, msgSender.headerSize() + orderAccepted.encodedLength(), results);
	}

	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param offset
	 * @param srcSbe
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderAccepted(MessageSinkRef owner,
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset,
			OrderAcceptedSbeDecoder srcSbe,
			MutableDirectBuffer dstBuffer,
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderAcceptedSbeEncoder.BLOCK_LENGTH,
				OrderAcceptedSbeEncoder.TEMPLATE_ID,
				OrderAcceptedSbeEncoder.SCHEMA_ID,
				OrderAcceptedSbeEncoder.SCHEMA_VERSION,
				srcSbe.encodedLength());
		dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, srcSbe.encodedLength());
		encodeChannelInfoOnly(dstBuffer, msgSender.headerSize() + dstOffset, orderAcceptedSbe, channelId, channelSeq);
		int length = msgSender.headerSize() + srcSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	static void encodeChannelInfoOnly(MutableDirectBuffer buffer, int offset, OrderAcceptedSbeEncoder sbe, int channelId, long channelSeq){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq);		
	}
	
	static void encodeChannelInfoOnly(MutableDirectBuffer buffer, int offset, OrderExpiredSbeEncoder sbe, int channelId, long channelSeq){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq);		
	}

	static void encodeChannelInfoOnly(MutableDirectBuffer buffer, int offset, OrderCancelledSbeEncoder sbe, int channelId, long channelSeq){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq);		
	}

	static void encodeChannelInfoAndQuantityOnly(MutableDirectBuffer buffer, int offset, OrderCancelledSbeEncoder sbe, int channelId, long channelSeq, int quantity){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.quantity(quantity);
	}
	
	static void encodeChannelInfoAndOverrideOrderSidOnly(MutableDirectBuffer buffer, int offset, OrderCancelledSbeEncoder sbe, int channelId, long channelSeq, int overrideOrderSid){
	    sbe.wrap(buffer, offset)
	    .channelId(channelId)
	    .channelSeq(channelSeq)
	    .orderSid(overrideOrderSid);
	}

	static void encodeChannelInfoOnly(MutableDirectBuffer buffer, int offset, OrderRejectedSbeEncoder sbe, int channelId, long channelSeq){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq);		
	}

	static void encodeChannelInfoOnly(MutableDirectBuffer buffer, int offset, TradeCreatedSbeEncoder sbe, int channelId, long channelSeq){
	    sbe.wrap(buffer, offset)
	    .channelId(channelId)
	    .channelSeq(channelSeq);
	}
	   
	static void encodeChannelInfoAndOverrideTradeSidOnly(MutableDirectBuffer buffer, int offset, TradeCreatedSbeEncoder sbe, int overrideTradeSid, int channelId, long channelSeq){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.tradeSid(overrideTradeSid);
	}

	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param srcBufferOffet
	 * @param srcSbe
	 * @param order
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderAcceptedWithOrderInfo(MessageSinkRef owner, 
			int channelId, 
			long channelSeq, 
			DirectBuffer srcBuffer, 
			int srcBufferOffset,
			OrderAcceptedSbeDecoder srcSbe,
			Order order, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		encodeOrderAcceptedWithOrderInfo(msgSender, 
				owner.sinkId(), 
				dstBuffer, 
				dstOffset, 
				orderAcceptedWithOrderInfoSbe, 
				channelId,
				channelSeq,
				srcBuffer, 
				srcBufferOffset,
				srcSbe, 
				order);
		int length = msgSender.headerSize() + orderAcceptedWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public long sendOrderCancelled(MessageSinkRef sink, OrderCancelledSbeDecoder orderCancelled){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderCancelledEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeOrderCancelled(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderCancelledSbe, 
					orderCancelled);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderCancelled(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				orderCancelledSbe, 
				orderCancelled);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	/**
	 * 
	 * @param sinks
	 * @param origOrderSid
	 * @param orderSid
	 * @param status
	 * @param price
	 * @param side
	 * @param orderId
	 * @param secSid
	 * @param execType
	 * @param leavesQty
	 * @param cumulativeQty
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long sendOrderCancelled(MessageSinkRef[] sinks,
			int channelId,
			long channelSeq,
			int origOrderSid,
			int orderSid,
			OrderStatus status,
			int price,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			int leavesQty,
			int cumulativeQty,
			long updateTime,
			int quantity,
			long[] results){
		int size = encodeOrderCancelled(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				orderCancelledSbe,
				channelId,
				channelSeq,
				origOrderSid,
				orderSid,
				status,
				price,
				side,
				orderId,
				secSid,
				execType,
				leavesQty,
				cumulativeQty,
				updateTime,
				quantity);
		return msgSender.trySend(sinks, msgSender.buffer(), 0, size, results);
	}

	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param offset
	 * @param orderCancelled
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderCancelledWithOrderQuantityOverride(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelledSbeDecoder orderCancelled,
			Order order,
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				orderCancelled.encodedLength());
		dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, orderCancelled.encodedLength());
		encodeChannelInfoAndQuantityOnly(dstBuffer, msgSender.headerSize() + dstOffset, orderCancelledSbe, channelId, channelSeq, order.quantity());
		int length = msgSender.headerSize() + orderCancelled.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}

	public int sendOrderCancelled(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelledSbeDecoder orderCancelled,
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				orderCancelled.encodedLength());
		dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, orderCancelled.encodedLength());
		encodeChannelInfoOnly(dstBuffer, msgSender.headerSize() + dstOffset, orderCancelledSbe, channelId, channelSeq);
		int length = msgSender.headerSize() + orderCancelled.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public int sendOrderCancelled(MessageSinkRef owner, 
	        int channelId,
	        long channelSeq,
	        DirectBuffer srcBuffer, 
	        int offset, 
	        OrderCancelledSbeDecoder orderCancelled,
	        int overrideOrderSid,
	        MutableDirectBuffer dstBuffer, 
	        int dstOffset){
	    msgSender.encodeHeader(
	            owner.sinkId(),
	            dstBuffer, 
	            dstOffset,
	            OrderCancelledSbeEncoder.BLOCK_LENGTH,
	            OrderCancelledSbeEncoder.TEMPLATE_ID,
	            OrderCancelledSbeEncoder.SCHEMA_ID,
	            OrderCancelledSbeEncoder.SCHEMA_VERSION,
	            orderCancelled.encodedLength());
	    dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, orderCancelled.encodedLength());
	    encodeChannelInfoOnly(dstBuffer, msgSender.headerSize() + dstOffset, orderCancelledSbe, channelId, channelSeq);
	    int length = msgSender.headerSize() + orderCancelled.encodedLength();
	    long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
	    return (int)(result == MessageSink.OK ? length : result);
	}

	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param srcBufferOffset
	 * @param srcSbe
	 * @param order
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderCancelledWithOrderInfo(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int srcBufferOffset,
			OrderCancelledSbeDecoder srcSbe, 
			Order order, 
			MutableDirectBuffer dstBuffer, int dstOffset){
		encodeOrderCancelledWithOrderInfo(msgSender, 
				owner.sinkId(), 
				dstBuffer, 
				dstOffset, 
				orderCancelledWithOrderInfoSbe, 
				channelId,
				channelSeq,
				srcBuffer, 
				srcBufferOffset, 
				srcSbe, 
				order);
		int length = msgSender.headerSize() + orderCancelledWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	/**
	 * 
	 * @param sinks
	 * @param srcBuffer
	 * @param offset
	 * @param orderCancelled
	 * @return Negative means message couldn't be delivered to at least one sink, otherwise positive 
	 */
	public long sendOrderCancelled(MessageSinkRef[] sinks, DirectBuffer srcBuffer, int offset, 
			OrderCancelledSbeDecoder orderCancelled,
			long[] results){
		msgSender.encodeHeader(
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				orderCancelled.encodedLength());
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, orderCancelled.encodedLength());
		return msgSender.trySend(sinks, msgSender.buffer(), 0, msgSender.headerSize() + orderCancelled.encodedLength(), results);
	}

	public long sendOrderCancelledWithOrderQuantityOverride(MessageSinkRef[] sinks,
			int numSinks,
	        int channelId,
	        long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelledSbeDecoder orderCancelled,
			Order order,
			long[] results){
		msgSender.encodeHeader(
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				orderCancelled.encodedLength());
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, orderCancelled.encodedLength());
		encodeChannelInfoAndQuantityOnly(msgSender.buffer(), msgSender.headerSize(), orderCancelledSbe, channelId, channelSeq, order.quantity());
		int length = msgSender.headerSize() + orderCancelled.encodedLength();
		long result = msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, length, results);
		return (result == MessageSink.OK) ? length : result;
	}
	
	public long sendOrderCancelled(MessageSinkRef[] sinks,
			int numSinks,
	        int channelId,
	        long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelledSbeDecoder orderCancelled,
			long[] results){
		msgSender.encodeHeader(
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				orderCancelled.encodedLength());
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, orderCancelled.encodedLength());
		encodeChannelInfoOnly(msgSender.buffer(), msgSender.headerSize(), orderCancelledSbe, channelId, channelSeq);
		int length = msgSender.headerSize() + orderCancelled.encodedLength();
		long result = msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, length, results);
		return (result == MessageSink.OK) ? length : result;
	}
	
	/**
	 * 
	 * @param sinks
	 * @param orderSid
	 * @param status
	 * @param secSid
	 * @param rejectType
	 * @param execType
	 * @param results
	 * @return Negative means message couldn't be delivered to at least one sink, otherwise positive 
	 */
	public long sendOrderCancelRejected(MessageSinkRef[] sinks,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			long secSid,
			OrderCancelRejectType rejectType,
			byte[] reason,
			ExecutionType execType,
			long updateTime,
			long[] results){
		int size = encodeOrderCancelRejected(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				orderCancelRejectedSbe, 
				channelId,
				channelSeq,
				orderSid,
				status,
				secSid,
				rejectType,
				reason,
				execType,
				updateTime); 
		return msgSender.trySend(sinks, msgSender.buffer(), 0, size, results);
	}
	
	/**
	 * 
	 * @param sinks
	 * @param srcBuffer
	 * @param offset
	 * @param orderCancelRejected
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long sendOrderCancelRejected(MessageSinkRef[] sinks, 
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelRejectedSbeDecoder orderCancelRejected, 
			long[] results){
		// encode a header, then direct copy from source buffer to message buffer
		msgSender.encodeHeader(
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				OrderCancelRejectedSbeEncoder.BLOCK_LENGTH,
				OrderCancelRejectedSbeEncoder.TEMPLATE_ID,
				OrderCancelRejectedSbeEncoder.SCHEMA_ID,
				OrderCancelRejectedSbeEncoder.SCHEMA_VERSION,
				orderCancelRejected.encodedLength());
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, orderCancelRejected.encodedLength());
		return msgSender.trySend(sinks, msgSender.buffer(), 0, msgSender.headerSize() + orderCancelRejected.encodedLength(), results);
	}
	
	public long sendOrderRejected(MessageSinkRef sink, OrderRejectedSbeDecoder orderRejected){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderRejectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeOrderRejected(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderRejectedSbe, 
					orderRejected);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderRejected(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				orderRejectedSbe, 
				orderRejected);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	public int sendOrderCancelRejected(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelRejectedSbeDecoder orderCancelRejected, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderCancelRejectedSbeEncoder.BLOCK_LENGTH,
				OrderCancelRejectedSbeEncoder.TEMPLATE_ID,
				OrderCancelRejectedSbeEncoder.SCHEMA_ID,
				OrderCancelRejectedSbeEncoder.SCHEMA_VERSION,
				orderCancelRejected.encodedLength());
		dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, orderCancelRejected.encodedLength());
		encodeChannelInfoOnly(dstBuffer, msgSender.headerSize() + dstOffset, orderCancelledSbe, channelId, channelSeq);
		int length = msgSender.headerSize() + orderCancelRejected.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}

	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param srcBufferOffset
	 * @param srcSbe
	 * @param order
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderCancelRejectedWithOrderInfo(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int srcBufferOffset,
			OrderCancelRejectedSbeDecoder srcSbe, 
			Order order, 
			MutableDirectBuffer dstBuffer, int dstOffset){
		encodeOrderCancelRejectedWithOrderInfo(msgSender,
				owner.sinkId(), 
				dstBuffer, 
				dstOffset, 
				orderCancelRejectedWithOrderInfoSbe, 
				channelId,
				channelSeq,
				srcBuffer, 
				srcBufferOffset, 
				srcSbe, 
				order);
		int length = msgSender.headerSize() + orderCancelRejectedWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}

	/**
	 * 
	 * @param sinks
	 * @param orderSid
	 * @param orderId
	 * @param secSid
	 * @param side
	 * @param price
	 * @param leavesQty
	 * @param status
	 * @param rejectType
	 * @return Negative means message couldn't be delivered to at least one of the sinks, otherwise positive 
	 */
	public long sendOrderRejected(MessageSinkRef[] sinks,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			OrderRejectType rejectType,
			byte[] reason,
			long updateTime,
			long[] results){
		int size = encodeOrderRejected(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				orderRejectedSbe, 
				channelId,
				channelSeq,
				orderSid,
				orderId,
				secSid,
				side,
				price,
				cumulativeQty,
				leavesQty,
				status,
				rejectType,
				reason,
				updateTime);
		return msgSender.trySend(sinks, msgSender.buffer(), 0, size, results);
	}

	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param offset
	 * @param orderRejected
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderRejected(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset,
			OrderRejectedSbeDecoder orderRejected, MutableDirectBuffer dstBuffer, int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderRejectedSbeEncoder.BLOCK_LENGTH,
				OrderRejectedSbeEncoder.TEMPLATE_ID,
				OrderRejectedSbeEncoder.SCHEMA_ID,
				OrderRejectedSbeEncoder.SCHEMA_VERSION,
				orderRejected.encodedLength());
		dstBuffer.putBytes(dstOffset + msgSender.headerSize(), srcBuffer, offset, orderRejected.encodedLength());
		int length = msgSender.headerSize() + orderRejected.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}

	/**
	 * 
	 * @param owner
	 * @param sinks
	 * @param srcBuffer
	 * @param srcBufferOffset
	 * @param srcSbe
	 * @param order
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendOrderRejectedWithOrderInfo(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int srcBufferOffset,
			OrderRejectedSbeDecoder srcSbe, 
			Order order, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		encodeOrderRejectedWithOrderInfo(msgSender, 
				owner.sinkId(), 
				dstBuffer, 
				dstOffset, 
				orderRejectedWithOrderInfoSbe, 
				channelId,
				channelSeq,
				srcBuffer, 
				srcBufferOffset,
				srcSbe, 
				order);
		int length = msgSender.headerSize() + orderRejectedWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public long sendOrderAmended(MessageSinkRef sink, OrderAmendedSbeDecoder orderAmended){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderAmendedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeOrderAmended(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderAmendedSbe, 
					orderAmended);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderAmended(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				orderAmendedSbe, 
				orderAmended);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	/**
	 * 
	 * @param owner
	 * @param sinks
	 * @param srcBuffer
	 * @param offset
	 * @param orderAmended
	 * @param dstBuffer
	 * @param dstOffset
	 * @return
	 */
	public int sendOrderAmended(MessageSinkRef owner, MessageSinkRef[] sinks, DirectBuffer srcBuffer, int offset, 
			OrderAmendedSbeDecoder orderAmended, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderAmendedSbeEncoder.BLOCK_LENGTH,
				OrderAmendedSbeEncoder.TEMPLATE_ID,
				OrderAmendedSbeEncoder.SCHEMA_ID,
				OrderAmendedSbeEncoder.SCHEMA_VERSION,
				orderAmended.encodedLength());
		dstBuffer.putBytes(dstOffset + msgSender.headerSize(), srcBuffer, offset, orderAmended.encodedLength());
		int length = msgSender.headerSize() + orderAmended.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public long sendOrderExpired(MessageSinkRef sink,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			long updateTime)
	{
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderExpiredEncodedLength(), sink, bufferClaim) >= 0L){
			encodeOrderExpired(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderExpiredSbe,
					channelId,
					channelSeq,
					orderSid,
					orderId,
					secSid,
					side,
					price,
					cumulativeQty,
					leavesQty,
					status, 
					updateTime);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderExpired(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0,
				orderExpiredSbe,
				channelId,
				channelSeq,
				orderSid,
				orderId,
				secSid,
				side,
				price,
				cumulativeQty,
				leavesQty,
				status,
				updateTime);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	/**
	 * 
	 * @param sinks
	 * @param orderSid
	 * @param orderId
	 * @param secSid
	 * @param side
	 * @param price
	 * @param leavesQty
	 * @param status
	 * @return Negative means message couldn't be delivered to at least one sink, otherwise positive 
	 */
	public long sendOrderExpired(MessageSinkRef[] sinks,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			long updateTime,
			long[] results)
	{
		int size = encodeOrderExpired(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				orderExpiredSbe,
				channelId,
				channelSeq,
				orderSid,
				orderId,
				secSid,
				side,
				price,
				cumulativeQty,
				leavesQty,
				status,
				updateTime);
		return msgSender.send(sinks, msgSender.buffer(), 0, size, results);
	}
	
	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param offset
	 * @param orderExpired
	 * @param dstBuffer
	 * @param dstOffset
	 * @return
	 */
	public int sendOrderExpired(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderExpiredSbeDecoder orderExpired, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderExpiredSbeEncoder.BLOCK_LENGTH,
				OrderExpiredSbeEncoder.TEMPLATE_ID,
				OrderExpiredSbeEncoder.SCHEMA_ID,
				OrderExpiredSbeEncoder.SCHEMA_VERSION,
				orderExpired.encodedLength());
		dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, orderExpired.encodedLength());
		encodeChannelInfoOnly(dstBuffer, msgSender.headerSize() + dstOffset, orderExpiredSbe, channelId, channelSeq);
		int length = msgSender.headerSize() + orderExpired.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public int sendOrderExpired(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			OrderCancelledSbeDecoder orderCancelled, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		orderExpiredSbe.wrap(dstBuffer, dstOffset + msgSender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.cumulativeQty(orderCancelled.cumulativeQty())
			.execType(orderCancelled.execType())
			.leavesQty(orderCancelled.leavesQty())
			.orderId(orderCancelled.orderId())
			.orderSid(orderCancelled.orderSid())
			.price(orderCancelled.price())
			.secSid(orderCancelled.secSid())
			.side(orderCancelled.side())
			.status(orderCancelled.status())
			.updateTime(orderCancelled.updateTime());
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				OrderExpiredSbeEncoder.BLOCK_LENGTH,
				OrderExpiredSbeEncoder.TEMPLATE_ID,
				OrderExpiredSbeEncoder.SCHEMA_ID,
				OrderExpiredSbeEncoder.SCHEMA_VERSION,
				orderExpiredSbe.encodedLength());
		int length = msgSender.headerSize() + orderExpiredSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	/**
	 * Merge order info into an existing OrderExpiredSbeDecoder.  I think this is only temporary and would be
	 * changed once we have more concrete info on vendor's order execution API
	 * @param owner
	 * @param sinks
	 * @param srcBuffer
	 * @param srcBufferOffset
	 * @param srcSbe
	 * @param order
	 * @return
	 */
	public int sendOrderExpiredWithOrderInfo(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderExpiredSbeDecoder srcSbe, 
			Order order,
			MutableDirectBuffer dstBuffer,
			int dstOffset){
		encodeOrderExpiredWithOrderInfo(msgSender, 
				owner.sinkId(), 
				dstBuffer, 
				dstOffset,
				orderExpiredWithOrderInfoSbe, 
				channelId,
				channelSeq,
				srcBuffer, 
				srcBufferOffset, 
				srcSbe, 
				order);
		int length = msgSender.headerSize() + orderExpiredWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}

	public int sendOrderExpiredWithOrderInfo(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderCancelledSbeDecoder srcSbe, 
			Order order,
			MutableDirectBuffer dstBuffer,
			int dstOffset){
		encodeOrderExpiredWithOrderInfo(msgSender, 
				owner.sinkId(), 
				dstBuffer, 
				dstOffset,
				orderExpiredWithOrderInfoSbe, 
				channelId,
				channelSeq,
				srcSbe, 
				order);
		int length = msgSender.headerSize() + orderExpiredWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public long sendOrderRejected(MessageSinkRef sink,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			OrderRejectType rejectType,
			byte[] reason,
			long updateTime){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderRejectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeOrderRejected(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderRejectedSbe,
					channelId,
					channelSeq,
					orderSid,
					orderId,
					secSid,
					side,
					price,
					cumulativeQty,
					leavesQty,
					status,
					rejectType,
					reason,
					updateTime);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeOrderRejected(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0,
				orderRejectedSbe,
				channelId,
				channelSeq,
				orderSid,
				orderId,
				secSid,
				side,
				price,
				cumulativeQty,
				leavesQty,
				status,
				rejectType,
				reason,
				updateTime);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	/**
	 * 
	 * @param sinks
	 * @param srcBuffer
	 * @param offset
	 * @param orderRejected
	 * @return Negative means message couldn't be delivered to at least one sink, otherwise positive 
	 */
	public long sendOrderRejected(MessageSinkRef[] sinks, DirectBuffer srcBuffer, int offset, OrderRejectedSbeDecoder orderRejected, long[] results){
		// encode a header, then direct copy from source buffer to message buffer
		msgSender.encodeHeader(
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				OrderRejectedSbeEncoder.BLOCK_LENGTH,
				OrderRejectedSbeEncoder.TEMPLATE_ID,
				OrderRejectedSbeEncoder.SCHEMA_ID,
				OrderRejectedSbeEncoder.SCHEMA_VERSION,
				orderRejected.encodedLength());
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, orderRejected.encodedLength());
		return msgSender.trySend(sinks, msgSender.buffer(), 0, msgSender.headerSize() + orderRejected.encodedLength(), results);
	}
	
	public long sendTradeCreated(MessageSinkRef sink, 
			int channelId,
			long channelSeq,
			int sid,
			int orderSid,
			int orderId,
			OrderStatus status,
			Side side,
			int leavesQty,
			int cumulativeQty,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			long updateTime){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedTradeEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeTradeCreated(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					tradeCreatedSbe,
					channelId,
					channelSeq,
					sid,
					orderSid,
					orderId,
					status,
					side,
					leavesQty,
					cumulativeQty,
					executionId,
					executionPrice,
					executionQty,
					secSid,
					updateTime);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeTradeCreated(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0,
				tradeCreatedSbe,
				channelId,
				channelSeq,
				sid,
				orderSid,
				orderId,
				status,
				side,
				leavesQty,
				cumulativeQty,
				executionId,
				executionPrice,
				executionQty,
				secSid,
				updateTime);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	/**
	 * 
	 * @param sinks
	 * @param sid
	 * @param orderSid
	 * @param orderId
	 * @param status
	 * @param side
	 * @param executionId
	 * @param executionPrice
	 * @param executionQty
	 * @param secSid
	 * @return Negative means message couldn't be delivered to at least one sink, otherwise positive 
	 */
	public long sendTradeCreated(MessageSinkRef[] sinks, 
			int channelId,
			long channelSnapshotSeq,
			int sid,
			int orderSid,
			int orderId,
			OrderStatus status,
			Side side,
			int leavesQty,
			int cumulativeQty,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			long updateTime,
			long[] results){
		int size = encodeTradeCreated(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				tradeCreatedSbe,
				channelId,
				channelSnapshotSeq,
				sid,
				orderId,
				orderSid,
				status,
				side,
				leavesQty,
				cumulativeQty,
				executionId,
				executionPrice,
				executionQty,
				secSid,
				updateTime);
		return msgSender.trySend(sinks, msgSender.buffer(), 0, size, results);
	}
	
	/**
	 * Send trade created message to both owner and sinks
	 * 
	 * Note: Please think of a better way to merge owner and sinks together nicely
	 * 
	 * @param owner
	 * @param sinks
	 * @param srcBuffer
	 * @param offset
	 * @param trade
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendTradeCreated(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int offset, 
			TradeCreatedSbeDecoder trade, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		msgSender.encodeHeader(
				owner.sinkId(),
				dstBuffer, 
				dstOffset,
				TradeCreatedSbeEncoder.BLOCK_LENGTH,
				TradeCreatedSbeEncoder.TEMPLATE_ID,
				TradeCreatedSbeEncoder.SCHEMA_ID,
				TradeCreatedSbeEncoder.SCHEMA_VERSION,
				trade.encodedLength());
		dstBuffer.putBytes(msgSender.headerSize() + dstOffset, srcBuffer, offset, trade.encodedLength());
		encodeChannelInfoOnly(dstBuffer, msgSender.headerSize() + dstOffset, tradeCreatedSbe, channelId, channelSeq);
		int length = msgSender.headerSize() + trade.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}

	public long sendTradeCreated(MessageSinkRef[] sinks,
	        int numSinks,
	        int channelId,
	        long channelSeq,
	        DirectBuffer srcBuffer, 
	        int offset, 
	        TradeCreatedSbeDecoder trade,
	        int overrideTradeSid,
	        long[] results){
	    msgSender.encodeHeader(
	            ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
	            msgSender.buffer(), 
	            0,
	            TradeCreatedSbeEncoder.BLOCK_LENGTH,
	            TradeCreatedSbeEncoder.TEMPLATE_ID,
	            TradeCreatedSbeEncoder.SCHEMA_ID,
	            TradeCreatedSbeEncoder.SCHEMA_VERSION,
	            trade.encodedLength());
	    msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, trade.encodedLength());
	    encodeChannelInfoAndOverrideTradeSidOnly(msgSender.buffer(), msgSender.headerSize(), tradeCreatedSbe, overrideTradeSid, channelId, channelSeq);
	    int length = msgSender.headerSize() + trade.encodedLength();
	    long result = msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, length, results);
	    return (result == MessageSink.OK) ? length : result;
	}

	public int sendTradeCreatedWithOrderInfo(MessageSinkRef owner, 
	        int channelId,
	        long channelSeq,
	        DirectBuffer srcBuffer, 
	        int srcBufferOffset,
	        TradeCreatedSbeDecoder srcSbe, 
	        Order order, 
	        MutableDirectBuffer dstBuffer, 
	        int dstOffset){
	    return sendTradeCreatedWithOrderInfo(owner, channelId, channelSeq, srcBuffer, srcBufferOffset, srcSbe, srcSbe.tradeSid(), order, dstBuffer, dstOffset);
	}
	
	/**
	 * 
	 * @param owner
	 * @param srcBuffer
	 * @param srcBufferOffset
	 * @param srcSbe
	 * @param order
	 * @param dstBuffer
	 * @param dstOffset
	 * @return If message is sent successfully, return a positive encoded length, otherwise negative 
	 */
	public int sendTradeCreatedWithOrderInfo(MessageSinkRef owner, 
			int channelId,
			long channelSeq,
			DirectBuffer srcBuffer, 
			int srcBufferOffset,
			TradeCreatedSbeDecoder srcSbe, 
			int overrideTradeSid,
			Order order, 
			MutableDirectBuffer dstBuffer, 
			int dstOffset){
		encodeTradeCreatedWithOrderInfo(msgSender, 
				owner.sinkId(), 
				dstBuffer, 
				dstOffset,
				tradeCreatedWithOrderInfoSbe, 
				overrideTradeSid,
				channelId,
				channelSeq,
				srcBuffer, 
				srcBufferOffset, 
				srcSbe, 
				order);
		
		int length = msgSender.headerSize() + tradeCreatedWithOrderInfoSbe.encodedLength();
		long result = msgSender.trySend(owner, dstBuffer, dstOffset, length);
		return (int)(result == MessageSink.OK ? length : result);
	}
	
	public long sendTradeCancelled(MessageSinkRef sink, 
			int channelId,
			long channelSeq,
			int tradeSid,
			int orderSid,
			OrderStatus status,
			ExecutionType execType,
			Side side,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			int leavesQty,
			int cumulativeQty,
			long updateTime){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedTradeCancelledEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeTradeCancelled(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					tradeCancelledSbe,
					channelId,
					channelSeq,
					tradeSid,
					orderSid,
					status,
					execType,
					side,
					executionId,
					executionPrice,
					executionQty,
					secSid,
					leavesQty,
					cumulativeQty,
					updateTime);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeTradeCancelled(msgSender,
				sink.sinkId(),
				msgSender.buffer(), 
				0,
				tradeCancelledSbe,
				channelId,
				channelSeq,
				tradeSid,
				orderSid,
				status,
				execType,
				side,
				executionId,
				executionPrice,
				executionQty,
				secSid,
				leavesQty,
				cumulativeQty,
				updateTime);
		return msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	/**
	 * 
	 * @param sinks
	 * @param orderSid
	 * @param status
	 * @param execType
	 * @param side
	 * @param executionPrice
	 * @param executionQty
	 * @param secSid
	 * @return Negative means message couldn't be delivered at least one sink, otherwise positive 
	 */
	public long sendTradeCancelled(MessageSinkRef[] sinks, 
			int channelId,
			long channelSeq,
			int tradeSid,
			int orderSid,
			OrderStatus status,
			ExecutionType execType,
			Side side,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			int leavesQty,
			int cumulativeQty,
			long updateTime,
			long[] results){
		int size = encodeTradeCancelled(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(), 
				0,
				tradeCancelledSbe,
				channelId,
				channelSeq,
				tradeSid,
				orderSid,
				status,
				execType,
				side,
				executionId,
				executionPrice,
				executionQty,
				secSid,
				leavesQty,
				cumulativeQty,
				updateTime);
		return msgSender.trySend(sinks, msgSender.buffer(), 0, size, results);
	}
	
	public int encodeCancelOrder(MutableDirectBuffer dstBuffer, int offset, int sinkId, CancelOrderRequest request){
		return encodeCancelOrder(msgSender,
				sinkId,
				dstBuffer, 
				offset, 
				cancelOrderSbe,
				request);
	}

	public long sendCancelOrder(MessageSinkRef omesSink, CancelOrderRequest request){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedCancelOrderEncodedLength(), omesSink, bufferClaim) == MessageSink.OK){
			encodeCancelOrder(msgSender,
					omesSink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					cancelOrderSbe,
					request);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeCancelOrder(msgSender,
				omesSink.sinkId(),
				msgSender.buffer(),
				0,
				cancelOrderSbe,
				request);
		return msgSender.trySend(omesSink, msgSender.buffer(), 0, size);
	}

	@SuppressWarnings("unused")
	private int expectedCancelOrderEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + CancelOrderRequestSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + CancelOrderRequestSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + CancelOrderRequestSbeEncoder.BLOCK_LENGTH;
	}

	public long sendNewCompositeOrder(MessageSinkRef sink, NewOrderRequest request){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedCompositeOrderNewEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeNewCompositeOrder(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					compositeOrderNewSbe,
					request);
			bufferClaim.commit();
//			LOG.info("Sent order using tryClaim [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
			return MessageSink.OK;
		}
		int size = encodeNewCompositeOrder(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				compositeOrderNewSbe,
				request);
//		LOG.info("Sent order using trySend [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	/**
	 * Send order. Message sequence number will be set in the input {@link newOrderRequest}.
	 * @param sender
	 * @param sink
	 * @param order
	 */
	public long sendNewOrder(MessageSinkRef sink, NewOrderRequest request){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedOrderNewEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeNewOrder(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					orderNewSbe,
					request);
			bufferClaim.commit();
//			LOG.info("Sent order using tryClaim [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
			return MessageSink.OK;
		}
		int size = encodeNewOrder(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				orderNewSbe,
				request);
//		LOG.info("Sent order using trySend [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	public int encodeNewOrder(MutableDirectBuffer dstBuffer, int offset, int sinkId, NewOrderRequest request){
		return encodeNewOrder(msgSender,
				sinkId,
				dstBuffer, 
				offset, 
				orderNewSbe,
				request);
	}
	
	/**
	 * @return Size of the message to be encoded, this needs to be accurate otherwise we may get
	 * segmentation fault.
	 */
	@SuppressWarnings("unused")
	private int expectedOrderNewEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + NewOrderRequestSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + NewOrderRequestSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + NewOrderRequestSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedCompositeOrderNewEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + NewCompositeOrderRequestSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + NewCompositeOrderRequestSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + NewCompositeOrderRequestSbeEncoder.BLOCK_LENGTH;
	}
	
	static int encodeCancelOrder(final MessageSender sender, 
			int dstSinkId,
			final MutableDirectBuffer buffer,
			int offset,
			CancelOrderRequestSbeEncoder sbe,
			CancelOrderRequest request){
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(request.clientKey())
		.orderSidToBeCancelled(request.ordSidToBeCancelled())
		.secSid(request.secSid())
		.timeoutAt(request.timeoutAtNanoOfDay())
		.side(request.side())
		.assignedThrottleTrackerIndex((byte)request.assignedThrottleTrackerIndex())
		.retry(request.retry() ? BooleanType.TRUE : BooleanType.FALSE);
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset,
				CancelOrderRequestSbeEncoder.BLOCK_LENGTH,
				CancelOrderRequestSbeEncoder.TEMPLATE_ID,
				CancelOrderRequestSbeEncoder.SCHEMA_ID,
				CancelOrderRequestSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	/**
	 * 
	 * @param sender
	 * @param dstSinkId
	 * @param buffer
	 * @param offset
	 * @param sbe
	 * @param request
	 * @return Sequence number of the encoded message
	 */
	public static int encodeNewOrder(final MessageSender sender, 
			int dstSinkId,
			final MutableDirectBuffer buffer,
			int offset,
			NewOrderRequestSbeEncoder sbe,
			NewOrderRequest request){
		int payloadLength = encodeNewOrderOnly(buffer, offset + sender.headerSize(), sbe, request);
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset,
				NewOrderRequestSbeEncoder.BLOCK_LENGTH,
				NewOrderRequestSbeEncoder.TEMPLATE_ID,
				NewOrderRequestSbeEncoder.SCHEMA_ID,
				NewOrderRequestSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		return sender.headerSize() + payloadLength;
	}
	
	public static int encodeNewOrderOnly(final MutableDirectBuffer buffer,
			int offset, NewOrderRequestSbeEncoder sbe, NewOrderRequest request){
		sbe.wrap(buffer, offset)
		.clientKey(request.clientKey())
		.secSid(request.secSid())
		.quantity(request.quantity())
		.orderType(request.orderType())
		.side(request.side())
		.tif(request.tif())
		.isAlgoOrder(request.isAlgoOrder())
		.limitPrice(request.limitPrice())
		.stopPrice(request.stopPrice())
		.timeoutAt(request.timeoutAtNanoOfDay())
		.portSid(request.portSid())
		.assignedThrottleTrackerIndex((byte)request.assignedThrottleTrackerIndex())
		.retry(request.retry() ? BooleanType.TRUE : BooleanType.FALSE)
		.throttleCheckRequired(request.throttleCheckRequired() ? BooleanType.TRUE : BooleanType.FALSE)
		.triggerInfo().triggeredBy(request.triggerInfo().triggeredBy()).triggerSeqNum(request.triggerInfo().triggerSeqNum()).nanoOfDay(request.triggerInfo().triggerNanoOfDay());
		return sbe.encodedLength();
	}
	
	public static int encodeNewCompositeOrder(final MessageSender sender, 
			int dstSinkId,
			final MutableDirectBuffer buffer,
			int offset,
			NewCompositeOrderRequestSbeEncoder sbe,
			NewOrderRequest request){
		int payloadLength = encodeNewCompositeOrderOnly(buffer, offset + sender.headerSize(), sbe, request);
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset,
				NewCompositeOrderRequestSbeEncoder.BLOCK_LENGTH,
				NewCompositeOrderRequestSbeEncoder.TEMPLATE_ID,
				NewCompositeOrderRequestSbeEncoder.SCHEMA_ID,
				NewCompositeOrderRequestSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		return sender.headerSize() + payloadLength;
	}
	
	public static int encodeNewCompositeOrderOnly(final MutableDirectBuffer buffer,
			int offset, NewCompositeOrderRequestSbeEncoder sbe, NewOrderRequest request){
		sbe.wrap(buffer, offset)
		.clientKey(request.clientKey())
		.secSid(request.secSid())
		.quantity(request.quantity())
		.orderType(request.orderType())
		.side(request.side())
		.tif(request.tif())
		.isAlgoOrder(request.isAlgoOrder())
		.limitPrice(request.limitPrice())
		.stopPrice(request.stopPrice())
		.timeoutAt(request.timeoutAtNanoOfDay())
		.portSid(request.portSid())
		.assignedThrottleTrackerIndex((byte)request.assignedThrottleTrackerIndex())
		.retry(request.retry() ? BooleanType.TRUE : BooleanType.FALSE)
		.triggerInfo().triggeredBy(request.triggerInfo().triggeredBy()).triggerSeqNum(request.triggerInfo().triggerSeqNum()).nanoOfDay(request.triggerInfo().triggerNanoOfDay());
		return sbe.encodedLength();
	}
	
	@SuppressWarnings("unused")
	private int expectedTradeCancelledEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + TradeCancelledSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + TradeCancelledSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + TradeCancelledSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedTradeEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + TradeSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + TradeSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + TradeSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedOrderCancelRejectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderCancelRejectedSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderCancelRejectedSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderCancelRejectedSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedOrderRejectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderRejectedSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderRejectedSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderRejectedSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedOrderExpiredEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderExpiredSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderExpiredSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderExpiredSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedOrderCancelledEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderCancelledSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderCancelledSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderCancelledSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedOrderAmendedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderAmendedSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderAmendedSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderAmendedSbeEncoder.BLOCK_LENGTH;		
	}

	@SuppressWarnings("unused")
	private int expectedOrderAcceptedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderAcceptedSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderAcceptedSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderAcceptedSbeEncoder.BLOCK_LENGTH;		
	}
	
	@SuppressWarnings("unused")
	private int expectedOrderRequestCompletionEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderRequestCompletionSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderRequestCompletionSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderRequestCompletionSbeEncoder.BLOCK_LENGTH;
	}

	@SuppressWarnings("unused")
	private int expectedOrderRequestAcceptedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + OrderRequestAcceptedSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + OrderRequestAcceptedSbeEncoder.BLOCK_LENGTH +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + OrderRequestAcceptedSbeEncoder.BLOCK_LENGTH;
	}

	static int encodeOrderAccepted(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			OrderAcceptedSbeEncoder orderAcceptedEncoder,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			int price,
			int cumulativeQty,
			int leavesQty,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			long updateTime){
		
		int payloadLength = encodeOrderAcceptedOnly(buffer, 
				offset + sender.headerSize(), 
				orderAcceptedEncoder,
				channelId,
				channelSeq,
				orderSid,
				status,
				price,
				cumulativeQty,
				leavesQty,
				side,
				orderId,
				secSid,
				execType,
				updateTime); 
		
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderAcceptedSbeEncoder.BLOCK_LENGTH,
				OrderAcceptedSbeEncoder.TEMPLATE_ID,
				OrderAcceptedSbeEncoder.SCHEMA_ID,
				OrderAcceptedSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeOrderAcceptedOnly(
			final MutableDirectBuffer buffer, 
			int offset,
			OrderAcceptedSbeEncoder orderAcceptedEncoder,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			int price,
			int cumulativeQty,
			int leavesQty,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			long updateTime){
		orderAcceptedEncoder.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.orderSid(orderSid)
		.status(status)
		.price(price)
		.cumulativeQty(cumulativeQty)
		.leavesQty(leavesQty)
		.side(side)
		.orderId(orderId)
		.secSid(secSid)
		.execType(execType)
		.updateTime(updateTime);
		return orderAcceptedEncoder.encodedLength();
	}
	
	static int encodeOrderAmended(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			OrderAmendedSbeEncoder orderAmendedEncoder,
			OrderAmendedSbeDecoder orderAmendedDecoderSource){
		
		// direct copy from sbe to buffer, too bad that sbe doesn't expose its underlying buffer
		// TODO: Find a faster way; direct copy
		orderAmendedEncoder.wrap(buffer, offset + sender.headerSize())
			.channelId(orderAmendedDecoderSource.channelId())
			.channelSeq(orderAmendedDecoderSource.channelSeq())
			.orderSid(orderAmendedDecoderSource.orderSid())
			.status(orderAmendedDecoderSource.status());
		
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderAmendedSbeEncoder.BLOCK_LENGTH,
				OrderAmendedSbeEncoder.TEMPLATE_ID,
				OrderAmendedSbeEncoder.SCHEMA_ID,
				OrderAmendedSbeEncoder.SCHEMA_VERSION,
				orderAmendedEncoder.encodedLength());
		
		return sender.headerSize() + orderAmendedEncoder.encodedLength();
	}
	
	static int encodeOrderCancelled(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			OrderCancelledSbeEncoder orderCancelledEncoder,
			OrderCancelledSbeDecoder orderCancelledDecoderSource){
		
		int payloadLength = encodeOrderCancelledOnly(buffer, 
				offset + sender.headerSize(), 
				orderCancelledEncoder, 
				orderCancelledDecoderSource.channelId(),
				orderCancelledDecoderSource.channelSeq(),
				orderCancelledDecoderSource.origOrderSid(),
				orderCancelledDecoderSource.orderSid(),
				orderCancelledDecoderSource.status(),
				orderCancelledDecoderSource.price(),
				orderCancelledDecoderSource.side(),
				orderCancelledDecoderSource.orderId(),
				orderCancelledDecoderSource.secSid(),
				orderCancelledDecoderSource.execType(),
				orderCancelledDecoderSource.leavesQty(),
				orderCancelledDecoderSource.cumulativeQty(),
				orderCancelledDecoderSource.updateTime(),
				orderCancelledDecoderSource.quantity());
		
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	static int encodeOrderCancelled(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			OrderCancelledSbeEncoder orderCancelledEncoder,
			int channelId,
			long channelSeq,
			int origOrderSid,
			int orderSid,
			OrderStatus status,
			int price,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			int leavesQty,
			int cumulativeQty,
			long updateTime,
			int quantity){
		
		int payloadLength = encodeOrderCancelledOnly(buffer, 
				offset + sender.headerSize(), 
				orderCancelledEncoder, 
				channelId,
				channelSeq,
				origOrderSid,
				orderSid,
				status,
				price,
				side,
				orderId,
				secSid,
				execType,
				leavesQty,
				cumulativeQty,
				updateTime,
				quantity);
		
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeOrderCancelledOnly(final MutableDirectBuffer buffer, 
			int offset,
			OrderCancelledSbeEncoder orderCancelledEncoder,
			int channelId,
			long channelSeq,
			int orderSid,
			int origOrderSid,
			OrderStatus status,
			int price,
			Side side,
			int orderId,
			long secSid,
			ExecutionType execType,
			int leavesQty,
			int cumulativeQty,
			long updateTime,
			int quantity){
		orderCancelledEncoder.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.origOrderSid(origOrderSid)
		.orderSid(orderSid)
		.status(status)
		.price(price)
		.side(side)
		.orderId(orderId)
		.secSid(secSid)
		.execType(execType)
		.leavesQty(leavesQty)
		.cumulativeQty(cumulativeQty)
		.updateTime(updateTime)
		.quantity(quantity);
		return orderCancelledEncoder.encodedLength();
	}
	
	static int encodeOrderExpired(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderExpiredSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			long updateTime){
		int payloadLength = encodeOrderExpiredOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				channelId,
				channelSeq,
				orderSid, 
				orderId,
				secSid, side, price, cumulativeQty, leavesQty, status, updateTime);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderExpiredSbeEncoder.BLOCK_LENGTH,
				OrderExpiredSbeEncoder.TEMPLATE_ID,
				OrderExpiredSbeEncoder.SCHEMA_ID,
				OrderExpiredSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeOrderExpiredOnly(final MutableDirectBuffer buffer, 
			int offset,
			OrderExpiredSbeEncoder orderExpiredEncoder,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			long updateTime){
		orderExpiredEncoder.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.orderSid(orderSid)
		.orderId(orderId)
		.cumulativeQty(cumulativeQty)
		.leavesQty(leavesQty)
		.price(price)
		.side(side)
		.status(status)
		.secSid(secSid)
		.execType(ExecutionType.EXPIRE)
		.updateTime(updateTime);
		return orderExpiredEncoder.encodedLength();
	}
	
	static int encodeOrderCancelRejected(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			long secSid,
			OrderCancelRejectType rejectType,
			byte[] reason,
			ExecutionType execType,
			long updateTime){
		
		int payloadLength = encodeOrderCancelRejectedOnly(buffer, 
				offset + sender.headerSize(), 
				orderCancelRejectedEncoder,
				channelId,
				channelSeq,
				orderSid,
				status,
				secSid,
				rejectType,
				reason,
				execType,
				updateTime);
		
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderCancelRejectedSbeEncoder.BLOCK_LENGTH,
				OrderCancelRejectedSbeEncoder.TEMPLATE_ID,
				OrderCancelRejectedSbeEncoder.SCHEMA_ID,
				OrderCancelRejectedSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeOrderCancelRejectedOnly(final MutableDirectBuffer buffer, 
			int offset,
			OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder,
			int channelId,
			long channelSeq,
			int orderSid,
			OrderStatus status,
			long secSid,
			OrderCancelRejectType rejectType,
			byte[] reason,
			ExecutionType execType, 
			long updateTime){
		orderCancelRejectedEncoder.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.orderSid(orderSid)
		.status(status)
		.secSid(secSid)
		.execType(execType)
		.cancelRejectType(rejectType)
		.putReason(reason, 0)
		.updateTime(updateTime);
		return orderCancelRejectedEncoder.encodedLength();
	}
	
	public static int encodeOrderCancelRejectedWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer,
			int offset, 
			OrderCancelRejectedWithOrderInfoSbeEncoder sbe, 
			int channelId,
			long channelSeq,
			final DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderCancelRejectedSbeEncoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(offset + sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	static int encodeOrderRejected(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			OrderRejectedSbeEncoder orderRejectedEncoder,
			OrderRejectedSbeDecoder orderRejectedDecoderSource){
		
		// direct copy from sbe to buffer, too bad that sbe doesn't expose its underlying buffer
		// TODO: Find a faster way; direct copy
		orderRejectedEncoder.wrap(buffer, offset + sender.headerSize())
			.channelId(orderRejectedDecoderSource.channelId())
			.channelSeq(orderRejectedDecoderSource.channelSeq())
			.orderSid(orderRejectedDecoderSource.orderSid())
			.cumulativeQty(orderRejectedDecoderSource.cumulativeQty())
			.execType(orderRejectedDecoderSource.execType())
			.leavesQty(orderRejectedDecoderSource.leavesQty())
			.orderId(orderRejectedDecoderSource.orderId())
			.price(orderRejectedDecoderSource.price())
			.rejectType(orderRejectedDecoderSource.rejectType())
			.secSid(orderRejectedDecoderSource.secSid())
			.status(orderRejectedDecoderSource.status())
			.limit(orderRejectedDecoderSource.limit());
		
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderRejectedSbeEncoder.BLOCK_LENGTH,
				OrderRejectedSbeEncoder.TEMPLATE_ID,
				OrderRejectedSbeEncoder.SCHEMA_ID,
				OrderRejectedSbeEncoder.SCHEMA_VERSION,
				orderRejectedEncoder.encodedLength());

		return sender.headerSize() + orderRejectedEncoder.encodedLength();
	}
	
	static int encodeOrderRejected(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderRejectedSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			OrderRejectType rejectType,
			byte[] reason,
			long updateTime){
		int payloadLength = encodeOrderRejectedOnly(buffer, 
				offset + sender.headerSize(), sbe, channelId, channelSeq,
				orderSid, orderId, secSid, side, price, cumulativeQty, leavesQty, status, rejectType, reason, updateTime);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderRejectedSbeEncoder.BLOCK_LENGTH,
				OrderRejectedSbeEncoder.TEMPLATE_ID,
				OrderRejectedSbeEncoder.SCHEMA_ID,
				OrderRejectedSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeOrderRejectedOnly(
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderRejectedSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int orderSid,
			int orderId,
			long secSid,
			Side side,
			int price,
			int cumulativeQty,
			int leavesQty,
			OrderStatus status,
			OrderRejectType rejectType,
			byte[] reason,
			long updateTime){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.orderSid(orderSid)
		.orderId(orderId)
		.secSid(secSid)
		.cumulativeQty(cumulativeQty)
		.leavesQty(leavesQty)
		.price(price)
		.side(side)
		.status(status)
		.rejectType(rejectType)
		.putReason(reason, 0)
		.execType(ExecutionType.REJECT)
		.updateTime(updateTime);
		return sbe.encodedLength();
	}
	
	static int encodeOrderCancelled(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderCancelledSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int orderSid,
			int origOrderSid,
			OrderStatus status){
		sbe.wrap(buffer, offset + sender.headerSize())
		.channelId(channelId)
		.channelSeq(channelSeq)
		.orderSid(orderSid)
		.origOrderSid(origOrderSid)
		.status(status);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderCancelledSbeEncoder.BLOCK_LENGTH,
				OrderCancelledSbeEncoder.TEMPLATE_ID,
				OrderCancelledSbeEncoder.SCHEMA_ID,
				OrderCancelledSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	/**
	 * 
	 * @param dstSinkId
	 * @param buffer
	 * @param offset
	 * @param clientKey
	 * @param ordSid
	 * @param completionType
	 * @param rejectType
	 * @param reason
	 * @return Total encoded length: header length + payload length
	 */
	public int encodeOrderRequestCompletion(
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			int clientKey,
			int ordSid,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType,
			byte[] reason){
		orderRequestCompletionSbe.wrap(buffer, offset + msgSender.headerSize())
		.clientKey(clientKey)
		.orderSid(ordSid)
		.completionType(completionType)
		.rejectType(rejectType)
		.putReason(reason, 0);
		msgSender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderRequestCompletionSbeEncoder.BLOCK_LENGTH,
				OrderRequestCompletionSbeEncoder.TEMPLATE_ID,
				OrderRequestCompletionSbeEncoder.SCHEMA_ID,
				OrderRequestCompletionSbeEncoder.SCHEMA_VERSION,
				orderRequestCompletionSbe.encodedLength());
		return msgSender.headerSize() + orderRequestCompletionSbe.encodedLength();
	}
	
	public static int encodeOrderRequestCompletion(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderRequestCompletionSbeEncoder sbe,
			int clientKey,
			int ordSid,
			OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType,
			byte[] reason){
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(clientKey)
		.orderSid(ordSid)
		.completionType(completionType)
		.rejectType(rejectType)
		.putReason(reason, 0);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderRequestCompletionSbeEncoder.BLOCK_LENGTH,
				OrderRequestCompletionSbeEncoder.TEMPLATE_ID,
				OrderRequestCompletionSbeEncoder.SCHEMA_ID,
				OrderRequestCompletionSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}

	public static int encodeOrderRequestAccepted(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderRequestAcceptedSbeEncoder sbe,
			int clientKey,
			int ordSid){
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(clientKey)
		.orderSid(ordSid);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				OrderRequestAcceptedSbeEncoder.BLOCK_LENGTH,
				OrderRequestAcceptedSbeEncoder.TEMPLATE_ID,
				OrderRequestAcceptedSbeEncoder.SCHEMA_ID,
				OrderRequestAcceptedSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}	

	public static int encodeTradeOnly(final MutableDirectBuffer buffer, 
			int offset, 
			TradeSbeEncoder sbe,
			int channelId,
			long channelSnapshoSeq,
			int tradeSid,
			int orderSid,
			int orderId,
			OrderStatus status,
			TradeStatus tradeStatus,
			Side side,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSnapshotSeq(channelSnapshoSeq)
		.tradeSid(tradeSid)
		.orderSid(orderSid)
		.orderId(orderId)
		.status(status)
		.tradeStatus(tradeStatus)
		.side(side)
		.executionPrice(executionPrice)
		.executionQty(executionQty)
		.secSid(secSid)
		.putExecutionId(executionId, 0);
		return sbe.encodedLength();
	}

	public static int encodeTradeCreated(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			TradeCreatedSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int sid,
			int orderSid,
			int orderId,
			OrderStatus status,
			Side side,
			int leavesQty,
			int cumulativeQty,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			long updateTime){
		int payloadLength = encodeTradeCreatedOnly(buffer, 
				offset + sender.headerSize(),
				sbe,
				channelId,
				channelSeq,
				sid,
				orderSid,
				orderId,
				status,
				side,
				leavesQty,
				cumulativeQty,
				executionId,
				executionPrice,
				executionQty,
				secSid,
				updateTime);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				TradeCreatedSbeEncoder.BLOCK_LENGTH,
				TradeCreatedSbeEncoder.TEMPLATE_ID,
				TradeCreatedSbeEncoder.SCHEMA_ID,
				TradeCreatedSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeTradeCreatedOnly(final MutableDirectBuffer buffer, 
			int offset, 
			TradeCreatedSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int tradeSid,
			int orderSid,
			int orderId,
			OrderStatus status,
			Side side,
			int leavesQty,
			int cumulativeQty,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			long updateTime){
		
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.tradeSid(tradeSid)
		.orderSid(orderSid)
		.orderId(orderId)
		.status(status)
		.side(side)
		.execType(ExecutionType.TRADE)
		.putExecutionId(executionId, 0)
		.executionPrice(executionPrice)
		.executionQty(executionQty)
		.secSid(secSid)
		.leavesQty(leavesQty)
		.cumulativeQty(cumulativeQty)
		.updateTime(updateTime);
		return sbe.encodedLength();
	}

	public static int encodeTradeCancelled(
			final MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset, 
			TradeCancelledSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int tradeSid,
			int orderSid,
			OrderStatus status,
			ExecutionType execType,
			Side side,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			int leavesQty,
			int cumulativeQty,
			long updateTime){
		int payloadLength = encodeTradeCancelledOnly(buffer, 
				offset + sender.headerSize(),
				sbe,
				channelId,
				channelSeq,
				tradeSid,
				orderSid,
				status,
				execType,
				side,
				executionId,
				executionPrice,
				executionQty,
				secSid,
				leavesQty,
				cumulativeQty,
				updateTime);
		sender.encodeHeader(
				dstSinkId,
				buffer, 
				offset,
				TradeCancelledSbeEncoder.BLOCK_LENGTH,
				TradeCancelledSbeEncoder.TEMPLATE_ID,
				TradeCancelledSbeEncoder.SCHEMA_ID,
				TradeCancelledSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		return sender.headerSize() + payloadLength;
	}
	
	public static int encodeTradeCancelledOnly(final MutableDirectBuffer buffer, 
			int offset, 
			TradeCancelledSbeEncoder sbe,
			int channelId,
			long channelSeq,
			int tradeSid,
			int orderSid,
			OrderStatus status,
			ExecutionType execType,
			Side side,
			byte[] executionId,
			int executionPrice,
			int executionQty,
			long secSid,
			int leavesQty,
			int cumulativeQty,
			long updateTime){
		sbe.wrap(buffer, offset)
		.channelId(channelId)
		.channelSeq(channelSeq)
		.orderSid(orderSid)
		.tradeSid(tradeSid)
		.status(status)
		.execType(execType)
		.side(side)
		.cancelledExecutionPrice(executionPrice)
		.cancelledExecutionQty(executionQty)
		.secSid(secSid)
		.leavesQty(leavesQty)
		.cumulativeQty(cumulativeQty)
		.updateTime(updateTime)
		.putExecutionId(executionId, 0);
		
		return sbe.encodedLength();
	}

	public static int encodeOrderCancelRejectedWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderCancelRejectedWithOrderInfoSbeEncoder sbe, 
			int channelId,
			long channelSeq,
			final DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderCancelRejectedSbeDecoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderCancelRejectedWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	public static int encodeOrderRejectedWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderRejectedWithOrderInfoSbeEncoder sbe, 
			int channelId,
			long channelSeq,
			final DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderRejectedSbeDecoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.secSid(order.secSid())
			.side(order.side())
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				OrderRejectedWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderRejectedWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderRejectedWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderRejectedWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	public static int encodeTradeCreatedWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer, 
			int offset, 
			TradeCreatedWithOrderInfoSbeEncoder sbe,
			int overrideTradeSid,
			int channelId,
			long channelSeq,
			final DirectBuffer srcBuffer, 
			int srcBufferOffset,
			TradeCreatedSbeDecoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.tradeSid(overrideTradeSid)
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				TradeCreatedWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				TradeCreatedWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				TradeCreatedWithOrderInfoSbeEncoder.SCHEMA_ID, 
				TradeCreatedWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	public static int encodeOrderAcceptedWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderAcceptedWithOrderInfoSbeEncoder sbe, 
			int channelId, 
			long channelSeq, 
			final DirectBuffer srcBuffer, 
			int srcBufferOffset,
			OrderAcceptedSbeDecoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(offset + sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		
		// OrderAcceptedWithOrderInfo needs to be structured such that it directly overlaps with OrderAccepted.
		// OrderAccepted               [ Header ][ OrderAccepted Fields]
		// OrderAcceptedWithOrderInfo  [ Header ][ OrderAccepted Fields][ Order Fields ]
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.clientKey(order.clientKey())
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice())
			.triggerInfo().triggeredBy(order.triggerInfo().triggeredBy()).triggerSeqNum(order.triggerInfo().triggerSeqNum()).nanoOfDay(order.triggerInfo().triggerNanoOfDay());
		sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				OrderAcceptedWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderAcceptedWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderAcceptedWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderAcceptedWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	public static int encodeOrderCancelledWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer, 
			int offset, 
			OrderCancelledWithOrderInfoSbeEncoder sbe, 
			int channelId,
			long channelSeq,
			final DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderCancelledSbeDecoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(offset + sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		sbe.wrap(buffer, offset + sender.headerSize())
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				OrderCancelledWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderCancelledWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderCancelledWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderCancelledWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	public static int encodeOrderExpiredWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer,
			int offset, 
			OrderExpiredWithOrderInfoSbeEncoder sbe, 
			int channelId,
			long channelSeq,
			final DirectBuffer srcBuffer, 
			int srcBufferOffset, 
			OrderExpiredSbeDecoder srcSbe, 
			Order order){
		// THIS IS FREAKING DANGEROUS to just copy part of the message...for now
		buffer.putBytes(offset + sender.headerSize(), srcBuffer, srcBufferOffset, srcSbe.encodedLength());
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				OrderExpiredWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderExpiredWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderExpiredWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderExpiredWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}
	
	public static int encodeOrderExpiredWithOrderInfo(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer,
			int offset, 
			OrderExpiredWithOrderInfoSbeEncoder sbe, 
			int channelId,
			long channelSeq,
			OrderCancelledSbeDecoder srcSbe, 
			Order order){		
		sbe.wrap(buffer, offset + sender.headerSize())
			.channelId(channelId)
			.channelSeq(channelSeq)
			.orderType(order.orderType())
			.quantity(order.quantity())
			.isAlgoOrder(order.isAlgo())
			.tif(order.timeInForce())
			.limitPrice(order.limitPrice())
			.stopPrice(order.stopPrice())
			.cumulativeQty(srcSbe.cumulativeQty())
			.execType(srcSbe.execType())
			.leavesQty(srcSbe.leavesQty())
			.orderId(srcSbe.orderId())
			.orderSid(srcSbe.orderSid())
			.price(srcSbe.price())
			.secSid(srcSbe.secSid())
			.side(srcSbe.side())
			.status(srcSbe.status())
			.updateTime(srcSbe.updateTime());
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				OrderExpiredWithOrderInfoSbeEncoder.BLOCK_LENGTH, 
				OrderExpiredWithOrderInfoSbeEncoder.TEMPLATE_ID, 
				OrderExpiredWithOrderInfoSbeEncoder.SCHEMA_ID, 
				OrderExpiredWithOrderInfoSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());
		return sender.headerSize() + sbe.encodedLength();
	}	

	public static byte[] prepareRejectedReason(String reason, MutableDirectBuffer buffer){
		return StringUtil.copyAndPadSpace(reason, OrderRejectedSbeEncoder.reasonLength(), buffer);
	}
	
	public static byte[] prepareExecutionId(String executionId, MutableDirectBuffer buffer){
		return StringUtil.copyAndPadSpace(executionId, TradeCreatedSbeEncoder.executionIdLength(), buffer);
	}

}
