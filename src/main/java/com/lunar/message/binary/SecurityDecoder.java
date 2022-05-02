package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.service.ServiceConstant;

public class SecurityDecoder implements Decoder<SecuritySbeDecoder> {
	static final Logger LOG = LogManager.getLogger(SecurityDecoder.class);
	private final SecuritySbeDecoder sbe = new SecuritySbeDecoder();
	private final HandlerList<SecuritySbeDecoder> handlerList;
	
	SecurityDecoder(Handler<SecuritySbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<SecuritySbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, SecuritySbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(SecuritySbeDecoder sbe){
		return String.format("sid:%d, securityType:%s, maturity:%d, exchangeSid:%d, lastUpdateSeq:%d",
				sbe.sid(),
				sbe.securityType().name(),
				sbe.maturity(),
				sbe.exchangeSid(),
				sbe.lastUpdateSeq());
	}

	public SecuritySbeDecoder sbe(){
		return this.sbe;
	}
	
	@Override
	public boolean registerHandler(Handler<SecuritySbeDecoder> handler){
		this.handlerList.add(handler);
		return true;
	}

	@Override
	public boolean unregisterHandler(Handler<SecuritySbeDecoder> handler){
		this.handlerList.remove(handler);
		return true;
	}
	

//	HandlerList<SecuritySbeDecoder> handlerList(){
//		return this.handlerList;
//	}
//	
	public static SecurityDecoder of(){
		return new SecurityDecoder(NULL_HANDLER);
	}

	static final Handler<SecuritySbeDecoder> NULL_HANDLER = new Handler<SecuritySbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder codec) {
			LOG.warn("message sent to null handler of SecurityDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
