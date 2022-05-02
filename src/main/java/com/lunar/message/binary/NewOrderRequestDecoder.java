package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.service.ServiceConstant;

public class NewOrderRequestDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(NewOrderRequestDecoder.class);
	private final NewOrderRequestSbeDecoder sbe = new NewOrderRequestSbeDecoder();
	private final HandlerList<NewOrderRequestSbeDecoder> handlerList;

	NewOrderRequestDecoder(Handler<NewOrderRequestSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<NewOrderRequestSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(NewOrderRequestSbeDecoder sbe){
		int limit = sbe.limit();
		StringBuilder sb = new StringBuilder(
				String.format("clientOrdReqId:%d, secSid:%d, orderType:%s, quantity:%d, side:%s, tif:%s, isAlgoOrder:%s, limitTickLevel:%d, stopTickLevel:%d",
		sbe.clientKey(),
		sbe.secSid(),
		sbe.orderType().name(),
		sbe.quantity(),
		sbe.side().name(),
		sbe.tif().name(),
		sbe.isAlgoOrder().name(),
		sbe.limitPrice(),
		sbe.stopPrice()));
		sbe.limit(limit);
		return sb.toString();
	}
	
	public HandlerList<NewOrderRequestSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static NewOrderRequestDecoder of(){
		return new NewOrderRequestDecoder(NULL_HANDLER);
	}

	static final Handler<NewOrderRequestSbeDecoder> NULL_HANDLER = new Handler<NewOrderRequestSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder codec) {
			LOG.warn("Message sent to null handler of NewOrderRequestDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
