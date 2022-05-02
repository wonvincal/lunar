package com.lunar.message.binary;

import java.io.UnsupportedEncodingException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.service.ServiceConstant;

public class RequestDecoder implements Decoder {
	private static final int BYTE_ARRAY_SIZE = 25;
	static final Logger LOG = LogManager.getLogger(RequestDecoder.class);
	private final RequestSbeDecoder sbe = new RequestSbeDecoder();
	private final HandlerList<RequestSbeDecoder> handlerList;
	private final byte[] byteArrayBuffer = new byte[BYTE_ARRAY_SIZE]; // buffer for converting between sbe and string

	RequestDecoder(Handler<RequestSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<RequestSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe, byteArrayBuffer);
	}

	public static String decodeToString(RequestSbeDecoder sbe, byte[] byteArrayBuffer){
		sbe.limit(sbe.offset() + RequestSbeDecoder.BLOCK_LENGTH);
		int origLimit = sbe.limit();
		ParametersDecoder parameterGroup = sbe.parameters();
		StringBuilder sb = new StringBuilder(String.format("clientKey:%d, requestType:%s, paramBinaryTemplateId:%d, parametersCount:%d",
				sbe.clientKey(),
				sbe.requestType().name(),
				sbe.parameterBinaryTemplateId(),
				parameterGroup.count()));
		try {
			for (ParametersDecoder parameters : parameterGroup){
				parameters.getParameterValue(byteArrayBuffer, 0);
				sb.append(", parameterType:").append(parameters.parameterType().name())
					.append(", parameterValueLong:").append(parameters.parameterValueLong())
					.append(", parameterValue:");
				if (parameters.parameterValue(0) == ParametersDecoder.parameterValueNullValue()){
					sb.append("[blank]");
				}
				else {
					String string = new String(byteArrayBuffer, 0, ParametersDecoder.parameterValueLength(), ParametersDecoder.parameterValueCharacterEncoding());
					if (Strings.isBlank(string)){
						sb.append("[blank]");
					}
					else{
						sb.append(string);
					}
				}
			}
			sb.append(", paramDataLength:").append(sbe.parameterBinaryDataLength());
		} 
		catch (UnsupportedEncodingException e) {
			LOG.error("unsupported encoding", e);
		}
		finally{
			sbe.limit(origLimit);					
		}
		return sb.toString();			
	}
	
	public HandlerList<RequestSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static RequestDecoder of(){
		return new RequestDecoder(NULL_HANDLER);
	}

	static final Handler<RequestSbeDecoder> NULL_HANDLER = new Handler<RequestSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
			LOG.error("Message sent to null handler of RequestDecoder [senderSinkId:{}, dstSinkId:{}, clientKey:{}, requestType:{}]", header.senderSinkId(), header.dstSinkId(), codec.clientKey(), codec.requestType().name());
		}
	};

	public static ImmutableListMultimap<ParameterType, Parameter> generateParameterMap(MutableDirectBuffer stringByteBuffer, RequestSbeDecoder request) throws UnsupportedEncodingException{
		int origLimit = request.limit();
		ImmutableListMultimap.Builder<ParameterType, Parameter> builder = ImmutableListMultimap.<ParameterType, Parameter>builder();
		ParametersDecoder parameterGroup = request.parameters();
		for (ParametersDecoder parameter : parameterGroup){
			if (parameter.parameterValueLong() != ParametersDecoder.parameterValueLongNullValue()){
				builder.put(parameter.parameterType(), Parameter.of(parameter.parameterType(), parameter.parameterValueLong()));
			}
			else{
				parameter.getParameterValue(stringByteBuffer.byteArray(), 0);
				builder.put(parameter.parameterType(), 
						Parameter.of(parameter.parameterType(),
								new String(stringByteBuffer.byteArray(),
										0, 
										ParametersDecoder.parameterValueLength(), 
										ParametersDecoder.parameterValueCharacterEncoding())));
			}
		}
		request.limit(origLimit);
		return builder.build();
	}
	
	public static ImmutableList<Parameter> generateParameterList(MutableDirectBuffer stringByteBuffer, RequestSbeDecoder request) throws UnsupportedEncodingException{
		int origLimit = request.limit();
		ParametersDecoder parameterGroup = request.parameters();
		Builder<Parameter> builder = ImmutableList.<Parameter>builder();
		for (ParametersDecoder parameter : parameterGroup){
			if (parameter.parameterValueLong() != ParametersDecoder.parameterValueLongNullValue()){
				builder.add(Parameter.of(parameter.parameterType(), parameter.parameterValueLong()));
			}
			else{
				parameter.getParameterValue(stringByteBuffer.byteArray(), 0);
				builder.add(Parameter.of(parameter.parameterType(),
								new String(stringByteBuffer.byteArray(),
										0, 
										ParametersDecoder.parameterValueLength(), 
										ParametersDecoder.parameterValueCharacterEncoding())));
			}
		}
		request.limit(origLimit);
		return builder.build();
	}
	
	public static int getTotalLength(RequestSbeDecoder decoder){
		int limit = decoder.limit();
		ParametersDecoder parameters = decoder.parameters();
		
		int length = MessageHeaderDecoder.ENCODED_LENGTH + RequestSbeDecoder.BLOCK_LENGTH +
				RequestSbeDecoder.ParametersDecoder.sbeHeaderSize();
		for (@SuppressWarnings("unused") ParametersDecoder parameter : parameters){
			length += RequestSbeDecoder.ParametersDecoder.sbeBlockLength();
		}
		length += RequestSbeDecoder.parameterBinaryDataHeaderLength() + decoder.parameterBinaryDataLength();		
		decoder.limit(limit);
		return length;
	}
	
	public static void wrapEmbeddedNote(DirectBuffer buffer, int offsetToBufferBegin, int parametersCount, RequestSbeDecoder request, NoteSbeDecoder note){
		if (request.parameterBinaryTemplateId() != TemplateType.NOTE.value()){
			throw new IllegalArgumentException("Request does not contain a Note");
		}
		
		int offsetToEntity = offsetToBufferBegin + MessageHeaderDecoder.ENCODED_LENGTH + RequestSbeDecoder.BLOCK_LENGTH + 
				RequestSbeDecoder.ParametersDecoder.sbeHeaderSize() + 
				parametersCount * RequestSbeDecoder.ParametersDecoder.sbeBlockLength() + 
				RequestSbeDecoder.parameterBinaryDataHeaderLength();

		note.wrap(buffer, 
				offsetToEntity,
				request.parameterBinaryBlockLength(),
				NoteSbeDecoder.SCHEMA_VERSION);
		
		int limit = note.limit();
		LOG.info("Wrapped embedded note with desc: {}", note.description());
		note.limit(limit);
	}
}
