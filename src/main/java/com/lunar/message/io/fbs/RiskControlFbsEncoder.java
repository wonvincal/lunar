package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.RiskControlSbeDecoder;
import com.lunar.message.io.sbe.RiskControlSbeDecoder.ControlsDecoder;

public class RiskControlFbsEncoder {
	static final Logger LOG = LogManager.getLogger(RiskControlFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, RiskControlSbeDecoder riskControl){
		int limit = riskControl.limit();
		ControlsDecoder controlGroup = riskControl.controls();
		int numControls = controlGroup.count();
		int[] individualEndOffsets = new int[numControls];
		int i = 0;
		for (ControlsDecoder control : controlGroup){
			individualEndOffsets[i] = RiskControlSettingFbs.createRiskControlSettingFbs(builder, 
					control.type().value(),
					control.value());
			i++;
		}

		int settingsOffset = RiskControlFbs.createSettingsVector(builder, individualEndOffsets);
		RiskControlFbs.startRiskControlFbs(builder);
		RiskControlFbs.addSettings(builder, settingsOffset);
		RiskControlFbs.addEntityType(builder, riskControl.entityType().value());
		RiskControlFbs.addSpecificEntitySid(builder, riskControl.specificEntitySid());
		RiskControlFbs.addToAllEntity(builder, riskControl.toAllEntity() == BooleanType.TRUE ? true : false);
		riskControl.limit(limit);
		return RiskControlFbs.endRiskControlFbs(builder);
	}
}
