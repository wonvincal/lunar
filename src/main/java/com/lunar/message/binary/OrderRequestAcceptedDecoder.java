package com.lunar.message.binary;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderRequestAcceptedSbeDecoder;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;

public class OrderRequestAcceptedDecoder implements Decoder {
	private final OrderRequestAcceptedSbeDecoder sbe = new OrderRequestAcceptedSbeDecoder();
	private final HandlerList<OrderRequestAcceptedSbeDecoder> handlerList;
	
	OrderRequestAcceptedDecoder(Handler<OrderRequestAcceptedSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderRequestAcceptedSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);		
	}
	
	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(OrderRequestAcceptedSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("clientKey:%d, ordSid:%d",
						sbe.clientKey(),
						sbe.orderSid()));
		return sb.toString();
	}
	
	public HandlerList<OrderRequestAcceptedSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderRequestAcceptedDecoder of(){
		return new OrderRequestAcceptedDecoder(NULL_HANDLER);
	}

	static final Handler<OrderRequestAcceptedSbeDecoder> NULL_HANDLER = new Handler<OrderRequestAcceptedSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder codec) {
			LOG.warn("message sent to order request accepted null handler");
		}
	};
}
