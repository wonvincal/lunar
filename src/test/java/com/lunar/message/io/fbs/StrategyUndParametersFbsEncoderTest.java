package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeEncoder;
import com.lunar.message.sender.StrategySender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.GenericStrategySchema;
import com.lunar.strategy.parameters.GenericUndParams;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class StrategyUndParametersFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(StrategyUndParametersFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final StrategyUndParamsSbeEncoder sbeEncoder = new StrategyUndParamsSbeEncoder();
	private final StrategyUndParamsSbeDecoder sbeDecoder = new StrategyUndParamsSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    GenericUndParams params = new GenericUndParams();
	    params.sizeThreshold(10000);
	    params.velocityThreshold(10000000);
	    params.numTotalWarrants(10000);
	    params.numActiveWarrants(90);
	    StrategySender.encodeUndParamsWithoutHeader(buffer, 0, sbeEncoder, params);
		sbeDecoder.wrap(buffer, 0, StrategyUndParamsSbeDecoder.BLOCK_LENGTH, StrategyUndParamsSbeDecoder.SCHEMA_VERSION);
		
		int offset = StrategyUndParametersFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		StrategyUndParametersFbs strategyParamsFbs = StrategyUndParametersFbs.getRootAsStrategyUndParametersFbs(builder.dataBuffer());
		assertStrategyParams(params, strategyParamsFbs);
	}
	
	public static void assertStrategyParams(GenericUndParams params, StrategyUndParametersFbs paramsFbs){
	    assertEquals(3, paramsFbs.parametersLength());
        assertEquals(GenericStrategySchema.VELOCITY_THRESHOLD_ID, paramsFbs.parameters(0).fieldSid());
        assertEquals(10000000, paramsFbs.parameters(0).longValue());
        assertEquals(GenericStrategySchema.OUT_NUM_ACTIVE_WARRANTS_ID, paramsFbs.parameters(1).fieldSid());
        assertEquals(90, paramsFbs.parameters(1).longValue());        
        assertEquals(GenericStrategySchema.OUT_NUM_TOTAL_WARRANTS_ID, paramsFbs.parameters(2).fieldSid());
        assertEquals(10000, paramsFbs.parameters(2).longValue());
	}
}

