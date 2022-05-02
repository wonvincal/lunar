package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderCompletionSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderCompletionDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(OrderCompletionDecoder.class);
	private final OrderCompletionSbeDecoder sbe = new OrderCompletionSbeDecoder();
	private final HandlerList<OrderCompletionSbeDecoder> handlerList;
	
	OrderCompletionDecoder(Handler<OrderCompletionSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderCompletionSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(OrderCompletionSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("ordSid:%d, status:%s, filledQuantity:%d, remainingQuantity:%d, avgPrice:%f",
		sbe.orderSid(),
		sbe.status().name(),
		sbe.filledQuantity(),
		sbe.remainingQuantity(),
		sbe.avgPrice()));
		return sb.toString();
	}
	
	public HandlerList<OrderCompletionSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderCompletionDecoder of(){
		return new OrderCompletionDecoder(NULL_HANDLER);
	}

	public static final Handler<OrderCompletionSbeDecoder> NULL_HANDLER = new Handler<OrderCompletionSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCompletionSbeDecoder codec) {
			LOG.warn("message sent to null handler of OrderCompletionDecoder");
		}
	};

}
