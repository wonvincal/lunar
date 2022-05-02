package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.service.ServiceConstant;

public class StrategySwitchDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(StrategySwitchDecoder.class);
	private final StrategySwitchSbeDecoder sbe = new StrategySwitchSbeDecoder();
	private final HandlerList<StrategySwitchSbeDecoder> handlerList;

	StrategySwitchDecoder(Handler<StrategySwitchSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<StrategySwitchSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, StrategySwitchSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(StrategySwitchSbeDecoder sbe){
		return String.format("source:%d, sid:%d, value:%d", sbe.switchSource().value(), sbe.sourceSid(), sbe.onOff().value());
	}
	
	public StrategySwitchSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<StrategySwitchSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static StrategySwitchDecoder of(){
		return new StrategySwitchDecoder(NULL_HANDLER);
	}

	static final Handler<StrategySwitchSbeDecoder> NULL_HANDLER = new Handler<StrategySwitchSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder codec) {
			LOG.warn("message sent to null handler of StrategyStatus");
		}
	};

}
