package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class CancelOrderRequestDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(CancelOrderRequestDecoder.class);
	private final CancelOrderRequestSbeDecoder sbe = new CancelOrderRequestSbeDecoder();
	private final HandlerList<CancelOrderRequestSbeDecoder> handlerList;
	
	CancelOrderRequestDecoder(Handler<CancelOrderRequestSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<CancelOrderRequestSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(CancelOrderRequestSbeDecoder sbe){
		StringBuilder sb = new StringBuilder(
				String.format("ordSidToBeCancelled:%d, secSid:%d",
		sbe.orderSidToBeCancelled(),
		sbe.secSid()));
		return sb.toString();
	}
	
	public HandlerList<CancelOrderRequestSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static CancelOrderRequestDecoder of(){
		return new CancelOrderRequestDecoder(NULL_HANDLER);
	}

	static final Handler<CancelOrderRequestSbeDecoder> NULL_HANDLER = new Handler<CancelOrderRequestSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder codec) {
			LOG.warn("Message sent to null handler of CancelOrderRequestDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
