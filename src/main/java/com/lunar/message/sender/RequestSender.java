package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Entity;
import com.lunar.journal.io.sbe.MessageHeaderEncoder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.binary.CodecUtil;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.RequestSbeEncoder.ParametersEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

public class RequestSender {
	static final Logger LOG = LogManager.getLogger(RequestSender.class);
	private final MessageSender msgSender;
	private final RequestSbeEncoder sbe = new RequestSbeEncoder();
	private final EntityEncoder entityEncoder = new EntityEncoder();

	public static RequestSender of(MessageSender msgSender){
		return new RequestSender(msgSender);
	}
	
	RequestSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public boolean sendEntity(MessageSinkRef sink, Request request, Entity entity){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(request.parameters().size(), entity.expectedEncodedLength()), sink, bufferClaim) == MessageSink.OK){
			encodeRequest(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					request, 
					entity, 
					entityEncoder);
			bufferClaim.commit();
			return true;
		}
		int size = encodeRequest(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				request, 
				entity, 
				entityEncoder);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size) >= 0;
	}

	public boolean trySendRequest(MessageSinkRef sink, Request request){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(request.parameters().size(), (!request.entity().isPresent())? 0 : request.entity().get().expectedEncodedLength()), sink, bufferClaim) == MessageSink.OK){
			encodeRequest(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					request,
					entityEncoder);
			bufferClaim.commit();
			return true;
		}
		int size = encodeRequest(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				request,
				entityEncoder);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size) >= 0;
	}
	
	/*
	public long sendRequest(MessageSinkRef sink, DirectBuffer srcBuffer, int offset, RequestSbeDecoder request){
		ParametersDecoder parameters = request.parameters();
		request.parameterBinaryBlockLength();
		int encodedLength = expectedEncodedLength(request.parameters().count());
		msgSender.encodeHeader(
				sink.sinkId(),
				msgSender.buffer(), 
				0,
				RequestSbeEncoder.BLOCK_LENGTH,
				RequestSbeEncoder.TEMPLATE_ID,
				RequestSbeEncoder.SCHEMA_ID,
				RequestSbeEncoder.SCHEMA_VERSION,
				encodedLength);
		msgSender.buffer().putBytes(msgSender.headerSize(), srcBuffer, offset, encodedLength);
		return msgSender.trySend(sink, msgSender.buffer(), 0, msgSender.headerSize() + encodedLength);
	}*/
	
	public int encodeRequest(MutableDirectBuffer dstBuffer, int offset, int sinkId, Request request){
		return encodeRequest(msgSender,
				sinkId,
				dstBuffer,
				offset,
				sbe,
				request,
				entityEncoder); 
	}
	
	public long sendRequest(MessageSinkRef sink, Request request){
		// try to write directly to the message sink buffer
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(request.parameters().size(), (!request.entity().isPresent()) ? 0 : request.entity().get().expectedEncodedLength()), sink, bufferClaim) == MessageSink.OK){
			encodeRequest(msgSender,
					sink.sinkId(),
					bufferClaim.buffer(),
					bufferClaim.offset(),
					sbe,
					request,
					entityEncoder);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeRequest(msgSender,
				sink.sinkId(),
				msgSender.buffer(),
				0,
				sbe,
				request,
				entityEncoder);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}

	private int expectedEncodedLength(int numParameters, int embeddedEntityLength){
		int size = MessageHeaderEncoder.ENCODED_LENGTH + 
				RequestSbeEncoder.BLOCK_LENGTH + 
				RequestSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
				RequestSbeEncoder.ParametersEncoder.sbeBlockLength() * numParameters +
				RequestSbeEncoder.parameterBinaryDataHeaderLength() + 
				embeddedEntityLength;
		
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + size + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}
	
	public static int encodeRequest(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset, 
			RequestSbeEncoder sbe, 
			Request request,
			EntityEncoder entityEncoder){
		int payloadLength = encodeRequestOnly(buffer, sender.headerSize(), sbe, request, request.entity().orElse(null), entityEncoder, sender.stringBuffer());
		int seq = sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				RequestSbeEncoder.BLOCK_LENGTH,
				RequestSbeEncoder.TEMPLATE_ID,
				RequestSbeEncoder.SCHEMA_ID,
				RequestSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		request.seq(seq);
		return sender.headerSize() + payloadLength; 
	}

	/**
	 * Side effect: Message sequence number will be written to the input request
	 * @param sender
	 * @param dstSinkId
	 * @param buffer
	 * @param offset
	 * @param sbe
	 * @param request
	 * @return
	 */
	public static int encodeRequest(final MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset, 
			RequestSbeEncoder sbe, 
			Request request,
			Entity entity,
			EntityEncoder entityEncoder){
		int payloadLength = encodeRequestOnly(buffer, sender.headerSize(), sbe, request, entity, entityEncoder, sender.stringBuffer());
		int seq = sender.encodeHeader(dstSinkId,
				buffer,
				offset,
				RequestSbeEncoder.BLOCK_LENGTH,
				RequestSbeEncoder.TEMPLATE_ID,
				RequestSbeEncoder.SCHEMA_ID,
				RequestSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		request.seq(seq);
		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeRequestOnly(MutableDirectBuffer buffer, 
			int offsetToPayload, 
			RequestSbeEncoder sbe,
			Request request,
			Entity entity,
			EntityEncoder entityEncoder,
			MutableDirectBuffer stringBuffer){
		
		sbe.wrap(buffer, offsetToPayload)
		.requestType(request.requestType())
		.clientKey(request.clientKey())
		.toSend(request.toSend());

		// Parameters group and parameterBinaryData are both of variable length and both
		// would change the message's limit.
		// Processing of these must be in correct sequence.
		// Here, we choose to 
		// 1) encode parameters
		// 2) encode parameterBinaryData
		// When we decode, we must decode in the same order
		ParametersEncoder parameters = sbe.parametersCount(request.parameters().size());
		for (Parameter parameter : request.parameters()){
			ParametersEncoder p = parameters.next().parameterType(parameter.type());
			if (parameter.isLongValue()){
				p.parameterValueLong(parameter.valueLong());
				p.parameterValue(0, ParametersEncoder.parameterValueNullValue());
			}
			else {
				p.putParameterValue(parameter.value().getBytes(), 0);
				p.parameterValueLong(ParametersEncoder.parameterValueLongNullValue());
			}
		}
		
		if (entity != null){
			sbe.parameterBinaryTemplateId(entity.templateType().value())
			.parameterBinaryBlockLength(entity.blockLength());

			int limit = sbe.limit();
			// Write entity into variable length
			int length = entity.encode(entityEncoder, buffer, 
					limit
					+ RequestSbeDecoder.parameterBinaryDataHeaderLength() /* encoded length does not include varData, 1 is the size of the varData length field*/,
					stringBuffer);
	        CodecUtil.uint8Put(buffer, limit, (short)length);
	        sbe.limit(limit + RequestSbeDecoder.parameterBinaryDataHeaderLength() + length);
		}
		else {
			sbe.parameterBinaryTemplateId(RequestSbeEncoder.parameterBinaryTemplateIdNullValue())
				.parameterBinaryBlockLength(RequestSbeEncoder.parameterBinaryBlockLengthNullValue());
		}
		return sbe.encodedLength();
	}
}
