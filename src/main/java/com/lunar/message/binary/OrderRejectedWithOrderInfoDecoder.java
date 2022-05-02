package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderRejectedWithOrderInfoDecoder implements Decoder {
	private final OrderRejectedWithOrderInfoSbeDecoder sbe = new OrderRejectedWithOrderInfoSbeDecoder();
	private Handler<OrderRejectedWithOrderInfoSbeDecoder> interceptor;
	private final HandlerList<OrderRejectedWithOrderInfoSbeDecoder> handlerList;
	
	OrderRejectedWithOrderInfoDecoder(Handler<OrderRejectedWithOrderInfoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderRejectedWithOrderInfoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderRejectedWithOrderInfoDecoder interceptor(Handler<OrderRejectedWithOrderInfoSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderRejectedWithOrderInfoDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderRejectedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderRejectedWithOrderInfoSbeDecoder sbe){
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
	
	public HandlerList<OrderRejectedWithOrderInfoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderRejectedWithOrderInfoDecoder of(){
		return new OrderRejectedWithOrderInfoDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderRejectedWithOrderInfoSbeDecoder> NULL_HANDLER = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderRejectedWithOrderInfoDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	public static int length(OrderRejectedWithOrderInfoSbeDecoder codec){
		return OrderRejectedWithOrderInfoSbeDecoder.BLOCK_LENGTH;
	}
}
