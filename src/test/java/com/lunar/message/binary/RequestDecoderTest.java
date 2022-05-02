package com.lunar.message.binary;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
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

public class RequestDecoderTest {
	@Test
	public void testEncodeDecode(){
		final int refSinkId = 11;
		final RequestType refRequestType = RequestType.GET;
		final List<Parameter> refParameters = new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.SECURITY_SID, 123456),
				Parameter.of(ParameterType.EXCHANGE_CODE, "SEHK")).build();
		Request refRequest = Request.of(refSinkId,
									 refRequestType,
									 refParameters);

		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		MutableDirectBuffer stringByteBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
		RequestSbeEncoder sbeEncoder = new RequestSbeEncoder();
		RequestSbeDecoder sbeDecoder = new RequestSbeDecoder();
		
		RequestSender.encodeRequestOnly(buffer, 0, sbeEncoder, refRequest, null, null, null);
		sbeDecoder.wrap(buffer, 0, RequestSbeDecoder.BLOCK_LENGTH, RequestSbeDecoder.SCHEMA_VERSION);
		
		ImmutableList<Parameter> generateParameterList = null;
		try {
			generateParameterList = RequestDecoder.generateParameterList(stringByteBuffer, sbeDecoder);
		} 
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		assertEquals(refParameters.size(), generateParameterList.size());
	}
}
