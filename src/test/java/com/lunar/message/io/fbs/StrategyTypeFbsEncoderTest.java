package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.entity.StrategyType;
import com.lunar.entity.StrategyType.Field;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeEncoder;
import com.lunar.message.sender.StrategySender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.GenericStrategySchema;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class StrategyTypeFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(StrategyTypeFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final StrategyTypeSbeEncoder sbeEncoder = new StrategyTypeSbeEncoder();
	private final StrategyTypeSbeDecoder sbeDecoder = new StrategyTypeSbeDecoder();
	private final UnsafeBuffer sbeStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    final GenericStrategySchema schema = new GenericStrategySchema();
	    final StrategyType strategyType = StrategyType.of(1, "SpeedArb");
	    schema.populateSchema(strategyType);
	    
	    StrategySender.encodeStrategyTypeWithoutHeader(buffer, 0, sbeStringBuffer, sbeEncoder, strategyType);
		sbeDecoder.wrap(buffer, 0, StrategyTypeSbeDecoder.BLOCK_LENGTH, StrategyTypeSbeDecoder.SCHEMA_VERSION);
		
		int offset = StrategyTypeFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		StrategyTypeFbs strategyTypeFbs = StrategyTypeFbs.getRootAsStrategyTypeFbs(builder.dataBuffer());
		assertStrategyType(strategyType, strategyTypeFbs);
	}
	
	public static void assertStrategyType(StrategyType strategyType, StrategyTypeFbs strategyTypeFbs){
	    assertEquals(strategyType.getFields().size(), strategyTypeFbs.fieldsLength());
	    int i = 0;
	    for (Field field : strategyType.getFields()) {
	        StrategyFieldFbs fbsField = strategyTypeFbs.fields(i);
	        assertEquals(field.isReadOnly() == BooleanType.TRUE, fbsField.readOnly());
	        i++;
	    }
	}
}
