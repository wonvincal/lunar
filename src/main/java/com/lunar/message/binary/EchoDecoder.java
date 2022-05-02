package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Echo;
import com.lunar.message.Message;
import com.lunar.message.io.sbe.EchoSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class EchoDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(EchoDecoder.class);
	private final EchoSbeDecoder sbe = new EchoSbeDecoder();
	private final HandlerList<EchoSbeDecoder> handlerList;

	EchoDecoder(Handler<EchoSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<EchoSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer,  + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}
	
	public static String decodeToString(EchoSbeDecoder sbe){
		return String.format("key:%d", sbe.key());		
	}

	@Override
	public Message decodeAsMessage(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return new Echo(header.senderSinkId(), sbe.key(), sbe.isResponse(), sbe.startTime()).seq(header.seq());
	}
	
	public HandlerList<EchoSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static EchoDecoder of(){
		return new EchoDecoder(NULL_HANDLER);
	}

	static final Handler<EchoSbeDecoder> NULL_HANDLER = new Handler<EchoSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EchoSbeDecoder codec) {
			LOG.warn("message sent to null handler of EchoDecoder");
		}
	};

}
