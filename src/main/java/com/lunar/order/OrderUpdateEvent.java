package com.lunar.order;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.util.MathUtil;

public class OrderUpdateEvent {
	private static final Logger LOG = LogManager.getLogger(OrderUpdateEvent.class);
	/**
	 * Use static int instead of enum to gain a tiny bit on performance
	 */
	public static final int ORDER_ACCEPTED = 0;
	public static final int ORDER_CANCELLED = 1;
	public static final int ORDER_CANCEL_REJECTED = 2;
	public static final int ORDER_REJECTED = 3;
	public static final int ORDER_EXPIRED = 4;
	public static final int TRADE_CREATED = 5;
	public static final int TRADE_CANCELLED = 6;
	
	public static final int ORDER_ACCEPTED_WITH_ORDER_INFO = 7;
	public static final int ORDER_CANCELLED_WITH_ORDER_INFO = 8;
	public static final int ORDER_CANCEL_REJECTED_WITH_ORDER_INFO = 9;
	public static final int ORDER_REJECTED_WITH_ORDER_INFO = 10;
	public static final int ORDER_EXPIRED_WITH_ORDER_INFO = 11;
	public static final int TRADE_CREATED_WITH_ORDER_INFO = 12;
	
	public static final int END_OF_RECOVERY = 13;
	
	public static final int NUM_UPDATE_TYPES = 14;
	
	public static final int MAX_ORDER_UDPATE_MESSAGE_SIZE;
	
	static {
		MAX_ORDER_UDPATE_MESSAGE_SIZE = MathUtil.max(OrderAcceptedSbeDecoder.BLOCK_LENGTH,
				OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH,
				OrderCancelledSbeDecoder.BLOCK_LENGTH,
				OrderCancelledWithOrderInfoSbeDecoder.BLOCK_LENGTH,
				OrderRejectedSbeDecoder.BLOCK_LENGTH,
				OrderRejectedWithOrderInfoSbeDecoder.BLOCK_LENGTH,
				OrderExpiredSbeDecoder.BLOCK_LENGTH,
				OrderExpiredWithOrderInfoSbeDecoder.BLOCK_LENGTH,
				OrderAmendedSbeDecoder.BLOCK_LENGTH,
				OrderAmendRejectedSbeDecoder.BLOCK_LENGTH,
				OrderCancelRejectedSbeDecoder.BLOCK_LENGTH,
				OrderCancelRejectedWithOrderInfoSbeDecoder.BLOCK_LENGTH,
				TradeCreatedSbeDecoder.BLOCK_LENGTH,
				TradeCreatedWithOrderInfoSbeDecoder.BLOCK_LENGTH,
				TradeCancelledSbeDecoder.BLOCK_LENGTH);
		LOG.info("Max order update message size [size:{}]", MAX_ORDER_UDPATE_MESSAGE_SIZE);
	}
	
	private int updateType;
	private int templateId;
	private int blockLength;
	private MutableDirectBuffer buffer;
	
	public static OrderUpdateEvent END_OF_RECOVERY_EVENT;
	
	static {
		END_OF_RECOVERY_EVENT = new OrderUpdateEvent();
		END_OF_RECOVERY_EVENT.updateType = END_OF_RECOVERY;
	}
	
	public OrderUpdateEvent(){
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_ORDER_UDPATE_MESSAGE_SIZE));
	}
	
	public OrderUpdateEvent(int size){
		if (size > 0){
			this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(size));
		}
	}
	
	public int updateType(){
		return updateType;
	}
	public int templateId(){
		return templateId;
	}
	public int blockLength(){
		return blockLength;
	}
	public MutableDirectBuffer buffer(){
		return buffer;
	}
	
	public OrderUpdateEvent merge(int updateType, int templateId, int blockLength, DirectBuffer payloadBuffer, int offset, int length){
		this.updateType = updateType;
		this.templateId = templateId;
		this.blockLength = blockLength;
		buffer.putBytes(0, payloadBuffer, offset, length);
		return this;
	}
	
//	static OrderUpdateBuffer createFrom(MessageHeaderDecoder header, DirectBuffer srcBuffer, int srcBufferOffset){
//	int length = header.payloadLength();
//	MutableDirectBuffer updateBuffer = new UnsafeBuffer(ByteBuffer.allocate(length));
//	updateBuffer.putBytes(0, srcBuffer, srcBufferOffset + header.encodedLength(), length);
//	return new OrderUpdateBuffer(header.templateId(), header.blockLength(), updateBuffer);
//}

	private static int convertTemplateIdToUpdateType(int templateId){
		switch (templateId){
		case OrderAcceptedSbeDecoder.TEMPLATE_ID:
			return ORDER_ACCEPTED;
		case OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID:
			return ORDER_ACCEPTED_WITH_ORDER_INFO;
		case OrderCancelledSbeDecoder.TEMPLATE_ID:
			return ORDER_CANCELLED;
		case OrderCancelledWithOrderInfoSbeDecoder.TEMPLATE_ID:
			return ORDER_CANCELLED_WITH_ORDER_INFO;
		case OrderRejectedSbeDecoder.TEMPLATE_ID:
			return ORDER_REJECTED;
		case OrderRejectedWithOrderInfoSbeDecoder.TEMPLATE_ID:
			return ORDER_REJECTED_WITH_ORDER_INFO;
		case OrderExpiredSbeDecoder.TEMPLATE_ID:
			return ORDER_EXPIRED;
		case OrderExpiredWithOrderInfoSbeDecoder.TEMPLATE_ID:
			return ORDER_EXPIRED_WITH_ORDER_INFO;
		case OrderCancelRejectedSbeDecoder.TEMPLATE_ID:
			return ORDER_CANCEL_REJECTED;
		case OrderCancelRejectedWithOrderInfoSbeDecoder.TEMPLATE_ID:
			return ORDER_CANCEL_REJECTED_WITH_ORDER_INFO;
		case TradeCreatedSbeDecoder.TEMPLATE_ID:
			return TRADE_CREATED;
		case TradeCreatedWithOrderInfoSbeDecoder.TEMPLATE_ID:
			return TRADE_CREATED_WITH_ORDER_INFO;
		case TradeCancelledSbeDecoder.TEMPLATE_ID:
			return TRADE_CANCELLED;
		default:
			throw new IllegalArgumentException("Unexpected template id [templateId:" + templateId + "]");
		}
	}
	
	public static OrderUpdateEvent createFrom(MessageHeaderDecoder header, DirectBuffer srcBuffer, int srcBufferOffset){
		int length = header.payloadLength();
		return new OrderUpdateEvent(length).merge(convertTemplateIdToUpdateType(header.templateId()), header.templateId(), header.blockLength(), srcBuffer, srcBufferOffset + header.encodedLength(), length);
	}
}
