package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderExpiredDecoder implements Decoder {
	private final OrderExpiredSbeDecoder sbe = new OrderExpiredSbeDecoder();
	private Handler<OrderExpiredSbeDecoder> interceptor;
	private final HandlerList<OrderExpiredSbeDecoder> handlerList;
	
	OrderExpiredDecoder(Handler<OrderExpiredSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderExpiredSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderExpiredDecoder interceptor(Handler<OrderExpiredSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderExpiredDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderExpiredSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, side:%s, price:%d, leavesQty:%d, status:%s",
						sbe.orderSid(),
						sbe.side().name(),
						sbe.price(),
						sbe.leavesQty(),
						sbe.status().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderExpiredSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderExpiredDecoder of(){
		return new OrderExpiredDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderExpiredSbeDecoder> NULL_HANDLER = new Handler<OrderExpiredSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderExpiredDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
	public static int length(OrderExpiredSbeDecoder codec){
		return OrderExpiredSbeDecoder.BLOCK_LENGTH;
	}
}
