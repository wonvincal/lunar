package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeEncoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.scoreboard.ScoreBoardSchema;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class ScoreBoardSchemaFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(ScoreBoardSchemaFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
    private final UnsafeBuffer sbeStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	private final ScoreBoardSchemaSbeEncoder sbeEncoder = new ScoreBoardSchemaSbeEncoder();
	private final ScoreBoardSchemaSbeDecoder sbeDecoder = new ScoreBoardSchemaSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    ScoreBoardSchema schema = new ScoreBoardSchema();
	    ScoreBoardSender.encodeScoreBoardSchemaOnly(buffer, 0, sbeStringBuffer, sbeEncoder, schema);
		sbeDecoder.wrap(buffer, 0, ScoreBoardSchemaSbeDecoder.BLOCK_LENGTH, ScoreBoardSchemaSbeDecoder.SCHEMA_VERSION);
		
		int offset = ScoreBoardSchemaFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		ScoreBoardSchemaFbs scoreBoardSchemaFbs = ScoreBoardSchemaFbs.getRootAsScoreBoardSchemaFbs(builder.dataBuffer());
		assertScoreBoard(schema, scoreBoardSchemaFbs);
	}
	
	public static void assertScoreBoard(ScoreBoardSchema schema, ScoreBoardSchemaFbs scoreBoardSchemaFbs){
	    assertEquals(schema.fields().size(), scoreBoardSchemaFbs.fieldsLength());
        assertEquals(ScoreBoardSchema.OUR_PREV_SCORE_WITH_PUNTERS_ID, scoreBoardSchemaFbs.fields(2).fieldId());
        assertEquals("Our Previous Score With Punter Trades", scoreBoardSchemaFbs.fields(2).fieldName());

        assertEquals(ScoreBoardSchema.NUM_OF_OUR_WINS_WITH_PUNTERS_ID, scoreBoardSchemaFbs.fields(6).fieldId());
        assertEquals("Num Our Wins with Punter Trades", scoreBoardSchemaFbs.fields(6).fieldName());

        assertEquals(ScoreBoardSchema.NUM_OF_OUR_BREAKEVENS_ID, scoreBoardSchemaFbs.fields(7).fieldId());
        assertEquals("Num Our Breakevens", scoreBoardSchemaFbs.fields(7).fieldName());

	}
}

