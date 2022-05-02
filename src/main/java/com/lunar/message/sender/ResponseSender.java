package com.lunar.message.sender;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SbeEncodable;
import com.lunar.message.Response;
import com.lunar.message.binary.CodecUtil;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EventSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeEncoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class ResponseSender {
	static final Logger LOG = LogManager.getLogger(ResponseSender.class);
	public static int EXPECTED_LENGTH_EXCLUDE_HEADER;
	static {
		EXPECTED_LENGTH_EXCLUDE_HEADER = ResponseSbeEncoder.BLOCK_LENGTH;
	}
	private final MessageSender msgSender;
	private final ResponseSbeEncoder sbe = new ResponseSbeEncoder();
	private final EntityEncoder entityEncoder = new EntityEncoder();
	
	public static ResponseSender of(MessageSender msgSender){
		return new ResponseSender(msgSender);
	}
	
	ResponseSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public void sendResponseForRequest(MessageSinkRef sink, RequestSbeDecoder request, ResultType resultType){
		sendResponse(sink, request.clientKey(), BooleanType.TRUE, 0, resultType);
	}
	
	public void sendResponse(MessageSinkRef sink, Response response){
		sendResponse(sink, response.clientKey(), response.isLast(), response.responseMsgSeq(), response.resultType());
	}

	public int encodeResponse(MutableDirectBuffer dstBuffer, int sinkId, int clientKey, int responseMsgSeq, ResultType resultType){
		int size = encodeResponse(msgSender,
				sinkId,
				dstBuffer,
				0,
				sbe,
				clientKey,
				BooleanType.FALSE,
				responseMsgSeq,
				resultType);
		return size;
	}
	
	public long sendResponse(MessageSinkRef sink, int clientKey, int responseMsgSeq, DirectBuffer entityInBuffer, int entityInBufferLength, int templateId, int blockLength){
		int size = encodeResponse(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				BooleanType.FALSE,
				responseMsgSeq,
				ResultType.OK,
				templateId,
				blockLength,
				entityInBuffer,
				0,
				entityInBufferLength);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);		
	}
	
	public long sendEvent(MessageSinkRef sink, int clientKey, int responseMsgSeq, DirectBuffer entityInBuffer, int entityInBufferLength){
		int size = encodeResponse(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				BooleanType.FALSE,
				responseMsgSeq,
				ResultType.OK,
				EventSbeEncoder.TEMPLATE_ID,
				EventSbeEncoder.BLOCK_LENGTH,
				entityInBuffer,
				0,
				entityInBufferLength);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendTrade(MessageSinkRef sink, int clientKey, int responseMsgSeq, DirectBuffer entityInBuffer){
		int size = encodeResponse(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				BooleanType.FALSE,
				responseMsgSeq,
				ResultType.OK,
				TradeSbeEncoder.TEMPLATE_ID,
				TradeSbeEncoder.BLOCK_LENGTH,
				entityInBuffer,
				0,
				TradeSbeEncoder.BLOCK_LENGTH);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	public long sendOrder(MessageSinkRef sink, int clientKey, int responseMsgSeq, DirectBuffer entityInBuffer){
		int size = encodeResponse(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				BooleanType.FALSE,
				responseMsgSeq,
				ResultType.OK,
				OrderSbeEncoder.TEMPLATE_ID,
				OrderSbeEncoder.BLOCK_LENGTH,
				entityInBuffer,
				0,
				OrderSbeEncoder.BLOCK_LENGTH);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendSbeEncodable(MessageSinkRef[] sinks, int numSinks, int clientKey, BooleanType isLast, int responseMsgSeq, ResultType resultType, SbeEncodable entity, long[] results){
		int size = encodeResponse(msgSender,
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE,
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				isLast,
				responseMsgSeq,
				resultType,
				entity,
				entityEncoder);
		return msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, size, results);
	}
	
	public void sendSbeEncodable(MessageSinkRef sink, int clientKey, BooleanType isLast, int responseMsgSeq, ResultType resultType, SbeEncodable entity){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(entity), sink, bufferClaim) == MessageSink.OK){
			encodeResponse(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					clientKey,
					isLast,
					responseMsgSeq,
					resultType, 
					entity,
					entityEncoder);
			bufferClaim.commit();
			return;
		}
		int size = encodeResponse(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				isLast,
				responseMsgSeq,
				resultType,
				entity,
				entityEncoder);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}
	
	public void sendResponse(MessageSinkRef sink, int clientKey, BooleanType isLast, int responseMsgSeq, ResultType resultType){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) == MessageSink.OK){
			encodeResponse(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					clientKey,
					isLast,
					responseMsgSeq,
					resultType);
			bufferClaim.commit();
			return;
		}
		int size = encodeResponse(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				clientKey,
				isLast,
				responseMsgSeq,
				resultType);
		msgSender.send(sink, msgSender.buffer(), 0, size);
	}

	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		final int totalLength = MessageHeaderEncoder.ENCODED_LENGTH + ResponseSbeEncoder.BLOCK_LENGTH;
		if (totalLength > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + totalLength + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + ResponseSbeEncoder.BLOCK_LENGTH;
	}

	private int expectedEncodedLength(SbeEncodable entity){
		final int totalLengthExcludeEntity = MessageHeaderEncoder.ENCODED_LENGTH + ResponseSbeEncoder.BLOCK_LENGTH;
		if (totalLengthExcludeEntity + entity.expectedEncodedLength() > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + totalLengthExcludeEntity + entity.expectedEncodedLength() +
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + ResponseSbeEncoder.BLOCK_LENGTH + entity.expectedEncodedLength();
	}

	// Vanilla response
	static int encodeResponse(final MessageSender sender, 
			int dstSinkId, 
			final MutableDirectBuffer buffer, 
			int offset, 
			ResponseSbeEncoder sbe, 
			int clientKey, 
			BooleanType isLast, 
			int responseMsgSeq,
			ResultType resultType){
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(clientKey)
		.responseMsgSeq(responseMsgSeq)
		.isLast(isLast)
		.resultType(resultType)
		.embeddedBlockLength((short)0)
		.embeddedTemplateId(ResponseSbeEncoder.embeddedTemplateIdNullValue());

		// write zero length
        CodecUtil.uint8Put(buffer, sbe.limit(), (short)0);
        
		sender.encodeHeader(dstSinkId,
				buffer, 
				offset, 
				ResponseSbeEncoder.BLOCK_LENGTH, 
				ResponseSbeEncoder.TEMPLATE_ID, 
				ResponseSbeEncoder.SCHEMA_ID, 
				ResponseSbeEncoder.SCHEMA_VERSION,
				sbe.encodedLength());

		return sbe.encodedLength() + sender.headerSize();
	}

	static int encodeResponse(final MessageSender sender, 
			  int dstSinkId, 
			  final MutableDirectBuffer buffer, 
			  int offset, 
			  ResponseSbeEncoder sbe, 
			  int clientKey, 
			  BooleanType isLast, 
			  int responseMsgSeq, 
			  ResultType resultType,
			  int templateId,
			  int templateBlockLength,
			  final DirectBuffer entityInBuffer,
			  int entityInBufferOffset,
			  int entityInBufferLength){
		
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(clientKey)
		.responseMsgSeq(responseMsgSeq)
		.isLast(isLast)
		.resultType(resultType)
		.embeddedTemplateId(templateId)
		.embeddedBlockLength(templateBlockLength)
		.putResultData(entityInBuffer, entityInBufferOffset, entityInBufferLength);

		sender.encodeHeader(dstSinkId,
				 buffer, 
				 offset, 
				 ResponseSbeEncoder.BLOCK_LENGTH, 
				 ResponseSbeEncoder.TEMPLATE_ID, 
				 ResponseSbeEncoder.SCHEMA_ID, 
				 ResponseSbeEncoder.SCHEMA_VERSION,
				 sbe.encodedLength());
		
		return sender.headerSize() + sbe.encodedLength();
	}
	
	// Vanilla response with Entity
	static int encodeResponse(final MessageSender sender, 
			  int dstSinkId, 
			  final MutableDirectBuffer buffer, 
			  int offset, 
			  ResponseSbeEncoder sbe, 
			  int clientKey, 
			  BooleanType isLast, 
			  int responseMsgSeq, 
			  ResultType resultType,
			  SbeEncodable entity,
			  EntityEncoder entityEncoder){
		sbe.wrap(buffer, offset + sender.headerSize())
		.clientKey(clientKey)
		.responseMsgSeq(responseMsgSeq)
		.isLast(isLast)
		.resultType(resultType)
		.embeddedTemplateId(entity.templateType().value())
		.embeddedBlockLength(entity.blockLength());
		// [header (headerSize)][ response ... embedded template id | embedded block length (sbe.encodedLength) | var data length | var data ]
		int limit = sbe.limit();
		// Write entity into variable length
		int origLength = sbe.encodedLength(); /* Should be ResponseSbeEncoder.BLOCK_LENGTH */
		int embeddedActualLength = entity.encode(entityEncoder, 
						buffer, 
						offset + sender.headerSize() + sbe.encodedLength() + ResponseSbeEncoder.resultDataHeaderLength() /* encoded length does not include varData, 1 is the size of the varData length field*/,
						sender.stringBuffer());
        CodecUtil.uint8Put(buffer, limit, (short)embeddedActualLength);

        int payloadLength = origLength + ResponseSbeEncoder.resultDataHeaderLength() + embeddedActualLength;
		sender.encodeHeader(dstSinkId,
				 buffer, 
				 offset, 
				 ResponseSbeEncoder.BLOCK_LENGTH, 
				 ResponseSbeEncoder.TEMPLATE_ID, 
				 ResponseSbeEncoder.SCHEMA_ID, 
				 ResponseSbeEncoder.SCHEMA_VERSION,
				 payloadLength);

		return sender.headerSize() + payloadLength;
	}
	
	public static int encodeResponseOnly(final MutableDirectBuffer buffer, 
			int offset, 
			ResponseSbeEncoder sbe,
			int clientKey, 
			BooleanType isLast, 
			int responseMsgSeq, 
			ResultType resultType){
		sbe.wrap(buffer, offset)
		.clientKey(clientKey)
		.responseMsgSeq(responseMsgSeq)
		.isLast(isLast)
		.resultType(resultType)
		.embeddedBlockLength((short)0)
		.embeddedTemplateId(ResponseSbeEncoder.embeddedTemplateIdNullValue());

		// write zero length
        CodecUtil.uint8Put(buffer, sbe.limit(), (short)0);

		return sbe.encodedLength();
	}
	
	public static int encodeResponseOnly(final MutableDirectBuffer buffer, 
			int offset, 
			ResponseSbeEncoder sbe,
			int clientKey, 
			BooleanType isLast, 
			int responseMsgSeq, 
			ResultType resultType,
			int templateId,
			int templateBlockLength,
			final DirectBuffer entityInBuffer,
			int entityInBufferOffset,
			int entityInBufferLength){
		sbe.wrap(buffer, offset)
		.clientKey(clientKey)
		.responseMsgSeq(responseMsgSeq)
		.isLast(isLast)
		.resultType(resultType)
		.embeddedTemplateId(templateId)
		.embeddedBlockLength(templateBlockLength)
		.putResultData(entityInBuffer, entityInBufferOffset, entityInBufferLength);
		return sbe.encodedLength();
	}
}
