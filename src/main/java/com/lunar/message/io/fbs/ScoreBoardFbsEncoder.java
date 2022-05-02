package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder.DetailsDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class ScoreBoardFbsEncoder {
	static final Logger LOG = LogManager.getLogger(ScoreBoardFbsEncoder.class);

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, ScoreBoardSbeDecoder scoreBoard){
		int limit = scoreBoard.limit();
	    final DetailsDecoder fields = scoreBoard.details(); 
	    final int numFields = fields.count();
	    final int[] fieldEndOffsets = new int[numFields];
		int i = 0;
		for (DetailsDecoder field : fields) {
		    fieldEndOffsets[i] = ScoreBoardDetailFbs.createScoreBoardDetailFbs(builder, field.fieldId(), field.fieldValueLong());
		    i++;
		}
		final int fieldsOffset = ScoreBoardFbs.createDetailsVector(builder, fieldEndOffsets);
        ScoreBoardFbs.startScoreBoardFbs(builder);
        ScoreBoardFbs.addSecSid(builder, scoreBoard.secSid());
        ScoreBoardFbs.addScore(builder, scoreBoard.score());
        ScoreBoardFbs.addDetails(builder, fieldsOffset);
        scoreBoard.limit(limit);
		return ScoreBoardFbs.endScoreBoardFbs(builder);
	}
}
