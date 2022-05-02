package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderCancelledWithOrderInfoDecoder implements Decoder {
	private final OrderCancelledWithOrderInfoSbeDecoder sbe = new OrderCancelledWithOrderInfoSbeDecoder();
	private Handler<OrderCancelledWithOrderInfoSbeDecoder> interceptor;
	private final HandlerList<OrderCancelledWithOrderInfoSbeDecoder> handlerList;
	
	OrderCancelledWithOrderInfoDecoder(Handler<OrderCancelledWithOrderInfoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderCancelledWithOrderInfoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderCancelledWithOrderInfoDecoder interceptor(Handler<OrderCancelledWithOrderInfoSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderCancelledWithOrderInfoDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderCancelledWithOrderInfoSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderCancelledWithOrderInfoSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, origOrderSid:%d, orderType:%s, status:%s",
						sbe.orderSid(),
						sbe.origOrderSid(),
						sbe.orderType(),
						sbe.status().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderCancelledWithOrderInfoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderCancelledWithOrderInfoDecoder of(){
		return new OrderCancelledWithOrderInfoDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderCancelledWithOrderInfoSbeDecoder> NULL_HANDLER = new Handler<OrderCancelledWithOrderInfoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledWithOrderInfoSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderCancelledWithOrderInfoDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

	public static int length(OrderCancelledWithOrderInfoSbeDecoder sbe){
		return OrderCancelledWithOrderInfoSbeDecoder.BLOCK_LENGTH;
	}
}
