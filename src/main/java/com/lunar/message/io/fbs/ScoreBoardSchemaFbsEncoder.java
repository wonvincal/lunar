package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder.FieldsDecoder;
import com.lunar.strategy.scoreboard.ScoreBoardSchema;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class ScoreBoardSchemaFbsEncoder {
	static final Logger LOG = LogManager.getLogger(ScoreBoardSchemaFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, ScoreBoardSchemaSbeDecoder schema){
        final int limit = schema.limit();
        FieldsDecoder fields = schema.fields();
        final int numFields = fields.count();
        final int[] nameOffsets = new int[numFields];
	    int i = 0;
		for (FieldsDecoder field : fields) {
	        nameOffsets[i] = builder.createString(getFieldDescription(field.fieldId()));
	        i++;
		}
		schema.limit(limit);
		fields = schema.fields();
		final int[] fieldEndOffsets = new int[numFields];
		i = 0;		
		for (FieldsDecoder field : fields) {
		    fieldEndOffsets[i] = ScoreBoardSchemaFieldFbs.createScoreBoardSchemaFieldFbs(builder,
		        field.fieldId(),
		        nameOffsets[i],
		        field.fieldType().value());
		    i++;
		}
		int fieldsOffset = ScoreBoardSchemaFbs.createFieldsVector(builder, fieldEndOffsets);
		ScoreBoardSchemaFbs.startScoreBoardSchemaFbs(builder);
		ScoreBoardSchemaFbs.addFields(builder, fieldsOffset);
		schema.limit(limit);
		return ScoreBoardSchemaFbs.endScoreBoardSchemaFbs(builder);
	}
	
	static private String getFieldDescription(final int parameterId) {
	    return ScoreBoardSchema.getFieldDescription(parameterId);
	}
}
