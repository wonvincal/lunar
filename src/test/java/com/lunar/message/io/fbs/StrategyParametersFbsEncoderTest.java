package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeEncoder;
import com.lunar.message.sender.StrategySender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class StrategyParametersFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(StrategyParametersFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final StrategyParamsSbeEncoder sbeEncoder = new StrategyParamsSbeEncoder();
	private final StrategyParamsSbeDecoder sbeDecoder = new StrategyParamsSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    GenericStrategyTypeParams params = new GenericStrategyTypeParams();
	    params.exitMode(StrategyExitMode.NO_CHECK_EXIT);

	    StrategySender.encodeStrategyTypeParamsWithoutHeader(buffer, 0, sbeEncoder, params);
		sbeDecoder.wrap(buffer, 0, StrategyParamsSbeDecoder.BLOCK_LENGTH, StrategyParamsSbeDecoder.SCHEMA_VERSION);
		
		int offset = StrategyParametersFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		StrategyParametersFbs strategyParamsFbs = StrategyParametersFbs.getRootAsStrategyParametersFbs(builder.dataBuffer());
		assertStrategyParams(params, strategyParamsFbs);
	}
	
	public static void assertStrategyParams(GenericStrategyTypeParams params, StrategyParametersFbs paramsFbs){
	    assertEquals(1, paramsFbs.parametersLength());
        assertEquals(StrategyExitMode.NO_CHECK_EXIT.value(), (byte)paramsFbs.parameters(0).longValue());
	}
}

