package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.ExchangeSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class ExchangeDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(ExchangeDecoder.class);
	private static final int BYTE_ARRAY_SIZE = 128;
	private final ExchangeSbeDecoder sbe = new ExchangeSbeDecoder();
	private final HandlerList<ExchangeSbeDecoder> handlerList;

	ExchangeDecoder(Handler<ExchangeSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<ExchangeSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe, new byte[BYTE_ARRAY_SIZE]);
	}

	public static String decodeToString(ExchangeSbeDecoder sbe, byte[] bytes){
		sbe.getCode(bytes, 0);
		String code = new String(bytes);
		sbe.getName(bytes, 0);
		String name = new String(bytes);
		return String.format("lastUpdateSeq:%d, sid:%d, code:%s, name:%s",
				sbe.lastUpdateSeq(),
				sbe.sid(),
				code,
				name);
	}
	
	public ExchangeSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<ExchangeSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static ExchangeDecoder of(){
		return new ExchangeDecoder(NULL_HANDLER);
	}

	static final Handler<ExchangeSbeDecoder> NULL_HANDLER = new Handler<ExchangeSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ExchangeSbeDecoder codec) {
			LOG.warn("message sent to null handler of ExchangeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
