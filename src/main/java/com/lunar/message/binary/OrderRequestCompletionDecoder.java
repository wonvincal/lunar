package com.lunar.message.binary;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;

public class OrderRequestCompletionDecoder implements Decoder {
	private final OrderRequestCompletionSbeDecoder sbe = new OrderRequestCompletionSbeDecoder();
	private final HandlerList<OrderRequestCompletionSbeDecoder> handlerList;
	
	OrderRequestCompletionDecoder(Handler<OrderRequestCompletionSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderRequestCompletionSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(OrderRequestCompletionSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("clientKey:%d, ordSid:%d, completionType:%s, rejectType:%s",
						sbe.clientKey(),
						sbe.orderSid(),
						sbe.completionType().name(),
						sbe.rejectType().name()));
		return sb.toString();
	}
	
	public HandlerList<OrderRequestCompletionSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderRequestCompletionDecoder of(){
		return new OrderRequestCompletionDecoder(NULL_HANDLER);
	}

	static final Handler<OrderRequestCompletionSbeDecoder> NULL_HANDLER = new Handler<OrderRequestCompletionSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
			LOG.warn("Message sent to null handler of order request completion [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
}
