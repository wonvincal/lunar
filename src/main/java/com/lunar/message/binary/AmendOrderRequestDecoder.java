package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.AmendOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class AmendOrderRequestDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(AmendOrderRequestDecoder.class);
	private final AmendOrderRequestSbeDecoder sbe = new AmendOrderRequestSbeDecoder();
	private final HandlerList<AmendOrderRequestSbeDecoder> handlerList;
	
	AmendOrderRequestDecoder(Handler<AmendOrderRequestSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<AmendOrderRequestSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(AmendOrderRequestSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("orderSidToBeAmended:%d, orderType:%s, quantity:%d, side:%s, tif:%s, limitTickLevel:%d, stopTickLevel:%d",
		sbe.orderSidToBeAmended(),
		sbe.orderType().name(),
		sbe.quantity(),
		sbe.side().name(),
		sbe.tif().name(),
		sbe.limitPrice(),
		sbe.stopPrice()));
		return sb.toString();
	}
	
	public HandlerList<AmendOrderRequestSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static AmendOrderRequestDecoder of(){
		return new AmendOrderRequestDecoder(NULL_HANDLER);
	}

	static final Handler<AmendOrderRequestSbeDecoder> NULL_HANDLER = new Handler<AmendOrderRequestSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AmendOrderRequestSbeDecoder codec) {
			LOG.warn("message sent to command null handler");
		}
	};

}
