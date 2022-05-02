package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderCancelRejectedDecoder implements Decoder {
	private final OrderCancelRejectedSbeDecoder sbe = new OrderCancelRejectedSbeDecoder();
	private Handler<OrderCancelRejectedSbeDecoder> interceptor;
	private final HandlerList<OrderCancelRejectedSbeDecoder> handlerList;
	
	OrderCancelRejectedDecoder(Handler<OrderCancelRejectedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderCancelRejectedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderCancelRejectedDecoder interceptor(Handler<OrderCancelRejectedSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderCancelRejectedDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderCancelRejectedSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderCancelRejectedSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, status:%s, rejectType:%s",
						sbe.orderSid(),
						sbe.status().name(),
						sbe.cancelRejectType().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderCancelRejectedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderCancelRejectedDecoder of(){
		return new OrderCancelRejectedDecoder(NULL_HANDLER);
	}

	static final Handler<OrderCancelRejectedSbeDecoder> NULL_HANDLER = new Handler<OrderCancelRejectedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder codec) {
			LOG.warn("message sent to null handler of OrderCancelRejectedDecoder");
		}
	};

	public static int length(OrderCancelRejectedSbeDecoder codec){
		return OrderCancelRejectedSbeDecoder.BLOCK_LENGTH;
	}
}
