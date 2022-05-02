package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class BoobsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(BoobsDecoder.class);
	private final BoobsSbeDecoder sbe = new BoobsSbeDecoder();
	private final HandlerList<BoobsSbeDecoder> handlerList;

	BoobsDecoder(Handler<BoobsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<BoobsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(BoobsSbeDecoder sbe){
        final StringBuilder sb = new StringBuilder(String.format("secSid:%d, bid:%d, ask:%d, last:%d", sbe.secSid(), sbe.bid(), sbe.ask(), sbe.last()));
		return sb.toString();
	}
	
	public BoobsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<BoobsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static BoobsDecoder of(){
		return new BoobsDecoder(NULL_HANDLER);
	}

	static final Handler<BoobsSbeDecoder> NULL_HANDLER = new Handler<BoobsSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, BoobsSbeDecoder codec) {
			LOG.warn("message sent to null handler of BoobsSbeDecoder");
		}
	};

}
