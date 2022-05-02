package com.lunar.message.binary;

import org.agrona.DirectBuffer;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.service.ServiceConstant;

/**
 * 
 * @author wongca
 */
public class OrderAmendedDecoder implements Decoder {
	private final OrderAmendedSbeDecoder sbe = new OrderAmendedSbeDecoder();
	private Handler<OrderAmendedSbeDecoder> interceptor;
	private final HandlerList<OrderAmendedSbeDecoder> handlerList;
	
	OrderAmendedDecoder(Handler<OrderAmendedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderAmendedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.interceptor = this.handlerList;
	}

	public OrderAmendedDecoder interceptor(Handler<OrderAmendedSbeDecoder> interceptor){
		this.interceptor = interceptor;
		return this;
	}

	public OrderAmendedDecoder clearInterceptor(){
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
		sbe.wrap(buffer, embeddedOffset, embeddedBlockLength, OrderAmendedSbeDecoder.SCHEMA_VERSION);
		interceptor.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderAmendedSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("status:%s",
						sbe.status().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderAmendedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderAmendedDecoder of(){
		return new OrderAmendedDecoder(NULL_HANDLER);
	}

	static final Handler<OrderAmendedSbeDecoder> NULL_HANDLER = new Handler<OrderAmendedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendedSbeDecoder codec) {
			LOG.warn("message sent to null handler of OrderAmendedDecoder");
		}
	};

	public static int length(OrderAmendedSbeDecoder codec){
		return OrderAmendedSbeDecoder.BLOCK_LENGTH;
	}
}
