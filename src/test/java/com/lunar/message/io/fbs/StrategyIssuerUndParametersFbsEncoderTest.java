package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeEncoder;
import com.lunar.message.sender.StrategySender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.GenericStrategySchema;
import com.lunar.strategy.parameters.GenericIssuerUndParams;

public class StrategyIssuerUndParametersFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(StrategyIssuerUndParametersFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final StrategyIssuerUndParamsSbeEncoder sbeEncoder = new StrategyIssuerUndParamsSbeEncoder();
	private final StrategyIssuerUndParamsSbeDecoder sbeDecoder = new StrategyIssuerUndParamsSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);

	@Test
	public void test(){
	    GenericIssuerUndParams params = new GenericIssuerUndParams();
	    params.undTradeVol(123456);
	    params.undTradeVolThreshold(200000);
	    StrategySender.encodeIssuerUndParamsWithoutHeader(buffer, 0, sbeEncoder, params);
		sbeDecoder.wrap(buffer, 0, StrategyIssuerUndParamsSbeDecoder.BLOCK_LENGTH, StrategyIssuerUndParamsSbeDecoder.SCHEMA_VERSION);
		
		int offset = StrategyIssuerUndParametersFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		StrategyIssuerUndParametersFbs strategyParamsFbs = StrategyIssuerUndParametersFbs.getRootAsStrategyIssuerUndParametersFbs(builder.dataBuffer());
		assertStrategyParams(params, strategyParamsFbs);
	}

	public static void assertStrategyParams(GenericIssuerUndParams params, StrategyIssuerUndParametersFbs paramsFbs){
	    assertEquals(2, paramsFbs.parametersLength());
        assertEquals(GenericStrategySchema.UND_TRADE_VOL_THRESHOLD_ID, paramsFbs.parameters(0).fieldSid());
        assertEquals(200000, paramsFbs.parameters(0).longValue());
        assertEquals(GenericStrategySchema.OUT_UND_TRADE_VOL_ID, paramsFbs.parameters(1).fieldSid());
        assertEquals(123456, paramsFbs.parameters(1).longValue());
	}
}
