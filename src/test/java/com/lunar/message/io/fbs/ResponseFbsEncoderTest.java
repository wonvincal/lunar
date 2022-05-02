package com.lunar.message.io.fbs;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.service.ServiceConstant;

public class ResponseFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(ResponseFbsEncoderTest.class);
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);

	@Test
	public void test(){
		// When
		final byte resultType = ResultTypeFbs.OK;
		final int clientKey = 123466;
		final boolean isLast = false;
		int offset = ResponseFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, clientKey, isLast, resultType);
		builder.finish(offset);
		
		ResponseFbs responseFbs = ResponseFbs.getRootAsResponseFbs(builder.dataBuffer());
		assertEquals(clientKey, responseFbs.clientKey());
		assertEquals(resultType, responseFbs.resultType());
		assertEquals(isLast, responseFbs.isLast());
	}
	
	public static void assertResponse(int clientKey, boolean isLast, byte resultType, ResponseFbs responseFbs){
		assertEquals(clientKey, responseFbs.clientKey());
		assertEquals(isLast, responseFbs.isLast());
		assertEquals(resultType, responseFbs.resultType());
	}
}
