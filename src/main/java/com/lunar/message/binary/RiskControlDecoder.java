package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RiskControlSbeDecoder;
import com.lunar.message.io.sbe.RiskControlSbeDecoder.ControlsDecoder;
import com.lunar.service.ServiceConstant;

public class RiskControlDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(RiskControlDecoder.class);
	private final RiskControlSbeDecoder sbe = new RiskControlSbeDecoder();
	private final HandlerList<RiskControlSbeDecoder> handlerList;
	
	RiskControlDecoder(Handler<RiskControlSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<RiskControlSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, RiskControlSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(RiskControlSbeDecoder sbe){
		int origLimit = sbe.limit();
		ControlsDecoder controls = sbe.controls();
		StringBuilder sb = new StringBuilder(String.format("entityType:%s, toAllEntity:%s, specificEntitySid:%d, controlCount:%d",
				sbe.entityType().name(),
				sbe.toAllEntity().name(),
				sbe.specificEntitySid(),
				controls.count()));
		for (ControlsDecoder control : controls){
			sb.append(", type:").append(control.type().name())
				.append(", value:").append(control.value());
		}
		sbe.limit(origLimit);					
		return sb.toString();			
	}

	public RiskControlSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<RiskControlSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static RiskControlDecoder of(){
		return new RiskControlDecoder(NULL_HANDLER);
	}

	static final Handler<RiskControlSbeDecoder> NULL_HANDLER = new Handler<RiskControlSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskControlSbeDecoder codec) {
			LOG.warn("message sent to null handler of RiskControlDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};
}
