package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderRejectedDecoder implements Decoder {
	private final OrderRejectedSbeDecoder sbe = new OrderRejectedSbeDecoder();
	private Handler<OrderRejectedSbeDecoder> interceptor;
	private final HandlerList<OrderRejectedSbeDecoder> handlerList;
	
	OrderRejectedDecoder(Handler<OrderRejectedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderRejectedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderRejectedDecoder interceptor(Handler<OrderRejectedSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderRejectedDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderRejectedSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderRejectedSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, secSid:%d, side:%s, price:%d, leavesQty:%d, status:%s",
						sbe.orderSid(),
						sbe.secSid(),
						sbe.side().name(),
						sbe.price(),
						sbe.leavesQty(),
						sbe.status().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderRejectedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderRejectedDecoder of(){
		return new OrderRejectedDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderRejectedSbeDecoder> NULL_HANDLER = new Handler<OrderRejectedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderRejectedDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
	public static int length(OrderRejectedSbeDecoder codec){
		return OrderRejectedSbeDecoder.BLOCK_LENGTH;
	}

}
