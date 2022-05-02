package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderAcceptedDecoder implements Decoder {
	private final OrderAcceptedSbeDecoder sbe = new OrderAcceptedSbeDecoder();
	private Handler<OrderAcceptedSbeDecoder> interceptor;
	private final HandlerList<OrderAcceptedSbeDecoder> handlerList;
	
	OrderAcceptedDecoder(Handler<OrderAcceptedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderAcceptedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderAcceptedDecoder interceptor(Handler<OrderAcceptedSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderAcceptedDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderAcceptedSbeDecoder sbe){
		int limit = sbe.limit();
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, side:%s, secSid:%d, price%d, leavesQty:%d, status:%s",
						sbe.orderSid(),
						sbe.side().name(),
						sbe.secSid(),
						sbe.price(),
						sbe.leavesQty(),
						sbe.status().name()));
		sbe.limit(limit);
		return sb.toString();
	}
	
	public HandlerList<OrderAcceptedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderAcceptedDecoder of(){
		return new OrderAcceptedDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderAcceptedSbeDecoder> NULL_HANDLER = new Handler<OrderAcceptedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderAcceptedDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
	public static int length(OrderAcceptedSbeDecoder sbe){
		return OrderAcceptedSbeDecoder.BLOCK_LENGTH;
	}
}
