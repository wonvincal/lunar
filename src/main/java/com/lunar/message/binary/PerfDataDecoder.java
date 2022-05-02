package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PerfDataSbeDecoder;
import com.lunar.service.ServiceConstant;

public class PerfDataDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(PerfDataDecoder.class);
	private final PerfDataSbeDecoder sbe = new PerfDataSbeDecoder();
	private final HandlerList<PerfDataSbeDecoder> handlerList;
	
	PerfDataDecoder(Handler<PerfDataSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<PerfDataSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(PerfDataSbeDecoder sbe){
		return String.format("sinkId:%d, roundTripNs:%d", sbe.sinkId(), sbe.roundTripNs());
	}
	
	public static PerfDataDecoder of(){
		return new PerfDataDecoder(NULL_HANDLER);
	}

	static final Handler<PerfDataSbeDecoder> NULL_HANDLER = new Handler<PerfDataSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PerfDataSbeDecoder codec) {
			LOG.warn("message sent to null handler of PerfDataDecoder");
		}
	};

}
