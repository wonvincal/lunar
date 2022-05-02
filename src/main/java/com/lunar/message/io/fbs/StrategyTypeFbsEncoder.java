package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder.FieldsDecoder;
import com.lunar.strategy.GenericStrategySchema;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class StrategyTypeFbsEncoder {
	static final Logger LOG = LogManager.getLogger(StrategyTypeFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, StrategyTypeSbeDecoder strategyType){
        stringBuffer.clear();
        stringBuffer.limit(strategyType.getName(stringBuffer.array(), 0));
        final int codeOffset = builder.createString(stringBuffer);
	    
        final int limit = strategyType.limit();
        FieldsDecoder fields = strategyType.fields();
        final int numFields = fields.count();
        final int[] nameOffsets = new int[numFields];
	    int i = 0;	    
		for (FieldsDecoder field : fields) {
	        nameOffsets[i] = builder.createString(getFieldDescription(strategyType.strategyId(), field.parameterId()));
	        i++;
		}
		strategyType.limit(limit);
		fields = strategyType.fields();
		final int[] fieldEndOffsets = new int[numFields];
		i = 0;		
		for (FieldsDecoder field : fields) {
		    fieldEndOffsets[i] = StrategyFieldFbs.createStrategyFieldFbs(builder,
		        field.parameterId(),
		        nameOffsets[i],
		        field.parameterSource().value(),
		        field.parameterType().value(),
		        field.parameterReadOnly() == BooleanType.TRUE);
		    i++;
		}
		int fieldsOffset = StrategyTypeFbs.createFieldsVector(builder, fieldEndOffsets);
        StrategyTypeFbs.startStrategyTypeFbs(builder);
        StrategyTypeFbs.addSid(builder, strategyType.strategyId());
        StrategyTypeFbs.addName(builder, codeOffset);
		StrategyTypeFbs.addFields(builder, fieldsOffset);
		strategyType.limit(limit);
		return StrategyTypeFbs.endStrategyTypeFbs(builder);
	}
	
	static private String getFieldDescription(final long strategyId, final int parameterId) {
	    return GenericStrategySchema.getFieldDescription(parameterId);
	}
}
