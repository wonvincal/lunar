package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeEncoder;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.scoreboard.ScoreBoard;
import com.lunar.strategy.scoreboard.ScoreBoardSchema;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class ScoreBoardFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(ScoreBoardFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
    private final UnsafeBuffer sbeStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	private final ScoreBoardSbeEncoder sbeEncoder = new ScoreBoardSbeEncoder();
	private final ScoreBoardSbeDecoder sbeDecoder = new ScoreBoardSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    ScoreBoard scoreBoard = ScoreBoard.of();
	    scoreBoard.setSecSid(12345);
	    scoreBoard.speedArbHybridStats().incrementOurPnlTicksWithPunter(1000);
	    ScoreBoardSender.encodePunterTradeSetStats(buffer, 0, sbeStringBuffer, sbeEncoder, scoreBoard.punterTradeSetStats());
		sbeDecoder.wrap(buffer, 0, ScoreBoardSbeDecoder.BLOCK_LENGTH, ScoreBoardSbeDecoder.SCHEMA_VERSION);
		
		int offset = ScoreBoardFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		ScoreBoardFbs scoreBoardFbs = ScoreBoardFbs.getRootAsScoreBoardFbs(builder.dataBuffer());
		assertScoreBoard(scoreBoard, scoreBoardFbs);
	}
	
	public static void assertScoreBoard(ScoreBoard scoreBoard, ScoreBoardFbs scoreBoardFbs){
	    assertEquals(scoreBoard.getSecSid(), scoreBoardFbs.secSid());
        assertEquals(8, scoreBoardFbs.detailsLength());
        for (int i = 0; i < scoreBoardFbs.detailsLength(); i++) {
            ScoreBoardDetailFbs detail = scoreBoardFbs.details(i);
            if (detail.fieldId() == ScoreBoardSchema.OUR_PNL_TICKS_WITH_PUNTERS_ID) {
                assertEquals(scoreBoard.speedArbHybridStats().getOurPnlTicksWithPunter(), detail.fieldValueLong());
            }
        }

	}
}

