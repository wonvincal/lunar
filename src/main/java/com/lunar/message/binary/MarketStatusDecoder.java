package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MarketStatusSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class MarketStatusDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(MarketStatusDecoder.class);
	private final MarketStatusSbeDecoder sbe = new MarketStatusSbeDecoder();
	private final HandlerList<MarketStatusSbeDecoder> handlerList;

	MarketStatusDecoder(Handler<MarketStatusSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<MarketStatusSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(MarketStatusSbeDecoder sbe){
        final StringBuilder sb = new StringBuilder(String.format("exchangeSid:%d, status:%d", sbe.exchangeSid(), sbe.status().value()));
		return sb.toString();
	}
	
	public MarketStatusSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<MarketStatusSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static MarketStatusDecoder of(){
		return new MarketStatusDecoder(NULL_HANDLER);
	}

	static final Handler<MarketStatusSbeDecoder> NULL_HANDLER = new Handler<MarketStatusSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatusSbeDecoder codec) {
			LOG.warn("message sent to null handler of MarketStatusSbeDecoder");
		}
	};

}
