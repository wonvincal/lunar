package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder.ControlsDecoder;
import com.lunar.service.ServiceConstant;

public class RiskStateDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(RiskStateDecoder.class);
	private final RiskStateSbeDecoder sbe = new RiskStateSbeDecoder();
	private final HandlerList<RiskStateSbeDecoder> handlerList;
	
	RiskStateDecoder(Handler<RiskStateSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<RiskStateSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, RiskStateSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(RiskStateSbeDecoder sbe){
		int origLimit = sbe.limit();
		ControlsDecoder controls = sbe.controls();
		StringBuilder sb = new StringBuilder(String.format("entityType:%s, entitySid:%s, controlCount:%d",
				sbe.entityType().name(),
				(sbe.entitySid() != RiskStateSbeDecoder.entitySidNullValue()) ? sbe.entitySid() : "null",
				controls.count()));
		for (ControlsDecoder control : controls){
			sb.append(", type:").append(control.type().name())
			.append(", current:").append(control.current())
			.append(", previous:").append(control.previous())
			.append(", exceeded:").append(control.exceeded().name());
		}
		sbe.limit(origLimit);					
		return sb.toString();			
	}

	public RiskStateSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<RiskStateSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static RiskStateDecoder of(){
		return new RiskStateDecoder(NULL_HANDLER);
	}

	static final Handler<RiskStateSbeDecoder> NULL_HANDLER = new Handler<RiskStateSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder codec) {
			LOG.warn("message sent to null handler of RiskEventDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
