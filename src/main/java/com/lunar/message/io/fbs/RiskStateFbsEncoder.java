package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder.ControlsDecoder;

public class RiskStateFbsEncoder {
	static final Logger LOG = LogManager.getLogger(RiskStateFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, RiskStateSbeDecoder riskState){
		int limit = riskState.limit();
		ControlsDecoder controlGroup = riskState.controls();
		int numControls = controlGroup.count();
		int[] individualEndOffsets = new int[numControls];
		int i = 0;
		for (ControlsDecoder control : controlGroup){
			individualEndOffsets[i] = RiskStateDetailsFbs.createRiskStateDetailsFbs(builder, 
					control.type().value(), 
					control.current(), 
					control.previous(),
					control.exceeded().equals(BooleanType.TRUE) ? true : false);
			i++;
		}

		int allDetailsOffset = RiskStateFbs.createDetailsVector(builder, individualEndOffsets);
		RiskStateFbs.startRiskStateFbs(builder);
		RiskStateFbs.addDetails(builder, allDetailsOffset);
		RiskStateFbs.addEntitySid(builder, riskState.entitySid());
		RiskStateFbs.addEntityType(builder, riskState.entityType().value());
		riskState.limit(limit);
	    return RiskStateFbs.endRiskStateFbs(builder);
	}

}
