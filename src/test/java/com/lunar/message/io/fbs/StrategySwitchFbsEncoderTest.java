package com.lunar.message.io.fbs;


import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeEncoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.sender.StrategySender;
import com.lunar.service.ServiceConstant;

public class StrategySwitchFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(StrategySwitchFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final StrategySwitchSbeEncoder sbeEncoder = new StrategySwitchSbeEncoder();
	private final StrategySwitchSbeDecoder sbeDecoder = new StrategySwitchSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    StrategySender.encodeStrategySwitchWithoutHeader(buffer, 0, sbeEncoder, StrategySwitchType.STRATEGY_PERSIST, StrategyParamSource.ISSUER_SID, 123, BooleanType.TRUE);
		sbeDecoder.wrap(buffer, 0, StrategySwitchSbeDecoder.BLOCK_LENGTH, StrategySwitchSbeDecoder.SCHEMA_VERSION);
		
		int senderSinkId = 8;
		int offset = StrategySwitchFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, senderSinkId, sbeDecoder);
		builder.finish(offset);
		
		StrategySwitchFbs strategySwitchFbs = StrategySwitchFbs.getRootAsStrategySwitchFbs(builder.dataBuffer());
		assertEquals(StrategySwitchType.STRATEGY_PERSIST.value(), strategySwitchFbs.switchType());
		assertEquals(123, strategySwitchFbs.sourceSid());
		assertEquals(StrategyParamSource.ISSUER_SID.value(), strategySwitchFbs.switchSource());
		assertEquals(true, strategySwitchFbs.onOff());
		assertEquals(senderSinkId, strategySwitchFbs.senderSinkId());

	}
	
}

