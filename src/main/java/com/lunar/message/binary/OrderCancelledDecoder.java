package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderCancelledDecoder implements Decoder {
	private final OrderCancelledSbeDecoder sbe = new OrderCancelledSbeDecoder();
	private Handler<OrderCancelledSbeDecoder> interceptor;
	private final HandlerList<OrderCancelledSbeDecoder> handlerList;
	
	OrderCancelledDecoder(Handler<OrderCancelledSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderCancelledSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderCancelledDecoder interceptor(Handler<OrderCancelledSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderCancelledDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderCancelledSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, origOrderSid:%d, status:%s",
						sbe.orderSid(),
						sbe.origOrderSid(),
						sbe.status().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderCancelledSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderCancelledDecoder of(){
		return new OrderCancelledDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderCancelledSbeDecoder> NULL_HANDLER = new Handler<OrderCancelledSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
			LOG.warn("Message sent to null handler of OrderCancelledDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
	
	public static int length(OrderCancelledSbeDecoder sbe){
		return OrderCancelledSbeDecoder.BLOCK_LENGTH;
	}
}
