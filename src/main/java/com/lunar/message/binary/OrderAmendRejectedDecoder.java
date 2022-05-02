package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderAmendRejectedDecoder implements Decoder {
	private final OrderAmendRejectedSbeDecoder sbe = new OrderAmendRejectedSbeDecoder();
	private Handler<OrderAmendRejectedSbeDecoder> interceptor;
	private final HandlerList<OrderAmendRejectedSbeDecoder> handlerList;
	
	OrderAmendRejectedDecoder(Handler<OrderAmendRejectedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderAmendRejectedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderAmendRejectedDecoder interceptor(Handler<OrderAmendRejectedSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderAmendRejectedDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderAmendRejectedSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderAmendRejectedSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSid:%d, status:%s, amendRejectType:%s",
						sbe.orderSid(),
						sbe.status().name(),
						sbe.amendRejectType().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderAmendRejectedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderAmendRejectedDecoder of(){
		return new OrderAmendRejectedDecoder(NULL_HANDLER);
	}

	static final Handler<OrderAmendRejectedSbeDecoder> NULL_HANDLER = new Handler<OrderAmendRejectedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendRejectedSbeDecoder codec) {
			LOG.warn("message sent to null handler of OrderAmendRejectedDecoder");
		}
	};

	public static int length(OrderAmendRejectedSbeDecoder codec){
		return OrderAmendRejectedSbeDecoder.BLOCK_LENGTH;
	}
}
