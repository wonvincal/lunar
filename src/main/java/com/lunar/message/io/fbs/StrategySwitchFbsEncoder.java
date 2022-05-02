package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class StrategySwitchFbsEncoder {
	static final Logger LOG = LogManager.getLogger(StrategySwitchFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, int senderSinkId, StrategySwitchSbeDecoder strategySwitch){
		int limit = strategySwitch.limit();
        StrategySwitchFbs.startStrategySwitchFbs(builder);
        StrategySwitchFbs.addSenderSinkId(builder, senderSinkId);
        StrategySwitchFbs.addSwitchType(builder, strategySwitch.switchType().value());
        StrategySwitchFbs.addSwitchSource(builder, strategySwitch.switchSource().value());
        StrategySwitchFbs.addSourceSid(builder, strategySwitch.sourceSid());
        StrategySwitchFbs.addOnOff(builder, strategySwitch.onOff() == BooleanType.TRUE);
        strategySwitch.limit(limit);
		return StrategySwitchFbs.endStrategySwitchFbs(builder);
	}
}
