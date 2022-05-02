package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewCompositeOrderRequestSbeDecoder;
import com.lunar.service.ServiceConstant;

public class NewCompositeOrderRequestDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(NewCompositeOrderRequestDecoder.class);
	private final NewCompositeOrderRequestSbeDecoder sbe = new NewCompositeOrderRequestSbeDecoder();
	private final HandlerList<NewCompositeOrderRequestSbeDecoder> handlerList;

	NewCompositeOrderRequestDecoder(Handler<NewCompositeOrderRequestSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<NewCompositeOrderRequestSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(NewCompositeOrderRequestSbeDecoder sbe){
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
	
	public HandlerList<NewCompositeOrderRequestSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static NewCompositeOrderRequestDecoder of(){
		return new NewCompositeOrderRequestDecoder(NULL_HANDLER);
	}

	static final Handler<NewCompositeOrderRequestSbeDecoder> NULL_HANDLER = new Handler<NewCompositeOrderRequestSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewCompositeOrderRequestSbeDecoder codec) {
			LOG.warn("Message sent to null handler of NewCompositeOrderRequestDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}

