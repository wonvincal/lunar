package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderCancelRejectedWithOrderInfoDecoder implements Decoder {
	private final OrderCancelRejectedWithOrderInfoSbeDecoder sbe = new OrderCancelRejectedWithOrderInfoSbeDecoder();
	private Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> interceptor;
	private final HandlerList<OrderCancelRejectedWithOrderInfoSbeDecoder> handlerList;
	
	OrderCancelRejectedWithOrderInfoDecoder(Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderCancelRejectedWithOrderInfoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderCancelRejectedWithOrderInfoDecoder interceptor(Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderCancelRejectedWithOrderInfoDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderCancelRejectedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderCancelRejectedWithOrderInfoSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, status:%s, rejectType:%s",
						sbe.orderSid(),
						sbe.status().name(),
						sbe.cancelRejectType().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderCancelRejectedWithOrderInfoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderCancelRejectedWithOrderInfoDecoder of(){
		return new OrderCancelRejectedWithOrderInfoDecoder(NULL_HANDLER);
	}

	static final Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> NULL_HANDLER = new Handler<OrderCancelRejectedWithOrderInfoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedWithOrderInfoSbeDecoder codec) {
			LOG.warn("message sent to null handler of OrderCancelRejectedWithOrderInfoDecoder");
		}
	};

	public static int length(OrderCancelRejectedWithOrderInfoSbeDecoder codec){
		return OrderCancelRejectedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
	}

}
