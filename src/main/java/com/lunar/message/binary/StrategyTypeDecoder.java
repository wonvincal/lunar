package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.service.ServiceConstant;

public class StrategyTypeDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategyTypeDecoder.class);
	private static final int BYTE_ARRAY_SIZE = 128;
	private final StrategyTypeSbeDecoder sbe = new StrategyTypeSbeDecoder();
	private final HandlerList<StrategyTypeSbeDecoder> handlerList;

	StrategyTypeDecoder(Handler<StrategyTypeSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategyTypeSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}
	
	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategyTypeSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
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

	public static String decodeToString(StrategyTypeSbeDecoder sbe){
		byte[] bytes = new byte[BYTE_ARRAY_SIZE];
		sbe.getName(bytes, 0);
		String name = new String(bytes);
		return String.format("strategyId:%d, name:%s", sbe.strategyId(), name);
	}
	
	public StrategyTypeSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategyTypeSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategyTypeDecoder of(){
		return new StrategyTypeDecoder(NULL_HANDLER);
	}

	static final Handler<StrategyTypeSbeDecoder> NULL_HANDLER = new Handler<StrategyTypeSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyTypeSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyType");
		}
	};

}
