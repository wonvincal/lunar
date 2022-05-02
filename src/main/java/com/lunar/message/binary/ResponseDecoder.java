package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.Message;
import com.lunar.message.Response;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.service.ServiceConstant;

public class ResponseDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(ResponseDecoder.class);
	private final ResponseSbeDecoder sbe = new ResponseSbeDecoder();
	private final HandlerList<ResponseSbeDecoder> handlerList;
	private final EntityDecoderManager entityDecoderMgr;
	
	ResponseDecoder(Handler<ResponseSbeDecoder> nullHandler, EntityDecoderManager entityDecoderMgr) {
		this.handlerList = new HandlerList<ResponseSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
		this.entityDecoderMgr = entityDecoderMgr;
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		int limit = sbe.limit();
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
		sbe.limit(limit);
		
		// Call entity decoder manager if there exists an entity in the response
		// embedded message has no header, instead, blockLength and templateID
		// are embedded within the response message.
		// TODO: Another way to do this is to 'not use embedded block length and
		// templateId'....this may be simpler.
		if (sbe.embeddedTemplateId() == ResponseSbeDecoder.TEMPLATE_ID){
			throw new IllegalArgumentException("Response cannot be embedded into another response");
		}
		
		int embeddedBlockLength = sbe.embeddedBlockLength();
        final int sizeOfLengthField = 1;
		if (embeddedBlockLength != 0){
			entityDecoderMgr.decodeWithClientKey(buffer, 
									offset,
									header,
									sbe.clientKey(),
									sbe.responseMsgSeq() /* sequence of response */,
									sbe.isLast(),
									header.encodedLength() + ResponseSbeDecoder.BLOCK_LENGTH + sizeOfLengthField,
									embeddedBlockLength,
									sbe.embeddedTemplateId()
									);
			
		}
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		
		// call entity decoder manager if there exists an entity in the response
		int embeddedBlockLength = sbe.embeddedBlockLength();
        final int sizeOfLengthField = 1;
		if (embeddedBlockLength != 0){
			return decodeToString(sbe) + 
			entityDecoderMgr.dumpWithClientKey(buffer, 
					offset,
					header,
					sbe.clientKey(),
					sbe.responseMsgSeq(), 
					sbe.isLast(),
					header.encodedLength() + ResponseSbeDecoder.BLOCK_LENGTH + sizeOfLengthField, 
					embeddedBlockLength, 
					sbe.embeddedTemplateId());
		}
		return decodeToString(sbe);
	}

	public static String decodeToString(ResponseSbeDecoder sbe){
		return String.format("type:response, clientKey:%d, responseMsgSeq:%d, isLast:%s, resultType:%s, embeddedBlockLength:%d, embeddedTemplateId:%d",
				sbe.clientKey(),
				sbe.responseMsgSeq(),
				sbe.isLast().name(),
				sbe.resultType().name(),
				sbe.embeddedBlockLength(),
				sbe.embeddedTemplateId());
	}

	@Override
	public Message decodeAsMessage(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return Response.of(header.senderSinkId(),
							sbe.clientKey(),
							sbe.isLast(), 
							sbe.responseMsgSeq(),
							sbe.resultType(),
							sbe.embeddedBlockLength(),
							sbe.embeddedTemplateId());
	}
	
	public HandlerList<ResponseSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static ResponseDecoder of(EntityDecoderManager entityDecoderMgr){
		return new ResponseDecoder(NULL_HANDLER, entityDecoderMgr);
	}

	static final Handler<ResponseSbeDecoder> NULL_HANDLER = new Handler<ResponseSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
			LOG.warn("message sent to response null handler");
		}
	};

}
