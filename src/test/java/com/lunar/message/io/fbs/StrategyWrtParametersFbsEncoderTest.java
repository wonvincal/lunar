package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyStatusType;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeEncoder;
import com.lunar.message.sender.StrategySender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.GenericStrategySchema;
import com.lunar.strategy.parameters.GenericWrtParams;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class StrategyWrtParametersFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(StrategyWrtParametersFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final StrategyWrtParamsSbeEncoder sbeEncoder = new StrategyWrtParamsSbeEncoder();
	private final StrategyWrtParamsSbeDecoder sbeDecoder = new StrategyWrtParamsSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    GenericWrtParams params = new GenericWrtParams();
	    params.status(StrategyStatusType.ACTIVE);
	    params.mmBidSize(10000);
	    params.mmAskSize(10);
	    params.numMPrcUpVols(123);
	    StrategySender.encodeWrtParamsWithoutHeader(buffer, 0, sbeEncoder, params);
		sbeDecoder.wrap(buffer, 0, StrategyWrtParamsSbeDecoder.BLOCK_LENGTH, StrategyWrtParamsSbeDecoder.SCHEMA_VERSION);
		
		int offset = StrategyWrtParametersFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		StrategyWrtParametersFbs strategyParamsFbs = StrategyWrtParametersFbs.getRootAsStrategyWrtParametersFbs(builder.dataBuffer());
		assertStrategyParams(params, strategyParamsFbs);
	}
	
	public static void assertStrategyParams(GenericWrtParams params, StrategyWrtParametersFbs paramsFbs){
	    assertEquals(60, paramsFbs.parametersLength());
        assertEquals(GenericStrategySchema.MM_BID_SIZE_ID, paramsFbs.parameters(0).fieldSid());
        assertEquals(10000, paramsFbs.parameters(0).longValue());
        assertEquals(GenericStrategySchema.MM_ASK_SIZE_ID, paramsFbs.parameters(1).fieldSid());
        assertEquals(10, paramsFbs.parameters(1).longValue());
        assertEquals(GenericStrategySchema.OUT_NUM_MPRC_UPVOLS_ID, paramsFbs.parameters(55).fieldSid());
        assertEquals(123, paramsFbs.parameters(55).longValue());
	}
}

