package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.sender.RequestSender;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RequestFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(RequestFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final RequestSbeEncoder sbeEncoder = new RequestSbeEncoder();
	private final RequestSbeDecoder sbeDecoder = new RequestSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test 
	public void testObjectToFbs(){
		// Given
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;
		Request request = Request.of(anySinkId,
									 refRequestType,
									 refParameters).clientKey(refClientKey);
		
		// When
		int offset = RequestFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, request);
		builder.finish(offset);

		RequestFbs requestFbs = RequestFbs.getRootAsRequestFbs(builder.dataBuffer());
		assertRequest(request, requestFbs);
	}
	
	@Test
	public void testSbeToFbs(){
		// Given
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;
		Request request = Request.of(anySinkId,
									 refRequestType,
									 refParameters).clientKey(refClientKey);
		RequestSender.encodeRequestOnly(buffer, 0, sbeEncoder, request, null, null, null);
		sbeDecoder.wrap(buffer, 0, RequestSbeDecoder.BLOCK_LENGTH, RequestSbeDecoder.SCHEMA_VERSION);
		
		// When
		int offset = RequestFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		RequestFbs requestFbs = RequestFbs.getRootAsRequestFbs(builder.dataBuffer());
		assertRequest(request, requestFbs);
	}
	
	public static void assertRequest(Request request, RequestFbs requestFbs){
		assertEquals(request.requestType().value(), requestFbs.requestType());
		assertEquals(request.clientKey(), requestFbs.clientKey());
		
		int parameterCount = requestFbs.parametersLength();
		assertEquals(parameterCount, request.parameters().size());
		for (int i = 0; i < parameterCount; i++){
			ParameterFbs parameterFbs = requestFbs.parameters(i);
			Parameter refParameter = request.parameters().get(i);
			assertEquals(refParameter.type().value(), parameterFbs.parameterType());
			if (refParameter.isLongValue()){
				// make sure fbs has same long value
				assertEquals(refParameter.valueLong().longValue(), parameterFbs.parameterValueLong());
				// make sure fbs has no string value
				assertNull(parameterFbs.parameterValue());
			}
			else{
				// make sure fbs has no long value
				// make sure fbs has same string value
				assertEquals(refParameter.value(), parameterFbs.parameterValue());
				assertEquals(0, parameterFbs.parameterValueLong());
			}
		}		
	}
}
