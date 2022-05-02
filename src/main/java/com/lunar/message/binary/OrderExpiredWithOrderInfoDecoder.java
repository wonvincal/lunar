package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderExpiredWithOrderInfoDecoder implements Decoder {
	private final OrderExpiredWithOrderInfoSbeDecoder sbe = new OrderExpiredWithOrderInfoSbeDecoder();
	private Handler<OrderExpiredWithOrderInfoSbeDecoder> interceptor;
	private final HandlerList<OrderExpiredWithOrderInfoSbeDecoder> handlerList;
	
	OrderExpiredWithOrderInfoDecoder(Handler<OrderExpiredWithOrderInfoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderExpiredWithOrderInfoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderExpiredWithOrderInfoDecoder interceptor(Handler<OrderExpiredWithOrderInfoSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderExpiredWithOrderInfoDecoder clearInterceptor(){
		this.interceptor = this.handlerList;
		return this;
	}
	
	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		interceptor.handle(buffer, offset, header, sbe);		
	}
	
	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderExpiredWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderExpiredWithOrderInfoSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, side:%s, price:%d, leavesQty:%d, orderType:%s, status:%s",
						sbe.orderSid(),
						sbe.side().name(),
						sbe.price(),
						sbe.leavesQty(),
						sbe.orderType(),
						sbe.status().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderExpiredWithOrderInfoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderExpiredWithOrderInfoDecoder of(){
		return new OrderExpiredWithOrderInfoDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderExpiredWithOrderInfoSbeDecoder> NULL_HANDLER = new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderExpiredWithOrderInfoDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	public static int length(OrderExpiredWithOrderInfoSbeDecoder codec){
		return OrderExpiredWithOrderInfoSbeDecoder.BLOCK_LENGTH;
	}
}
