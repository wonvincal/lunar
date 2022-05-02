package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.EventSbeDecoder.EventValuesDecoder;

public class EventFbsEncoder {
	static final Logger LOG = LogManager.getLogger(EventFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, EventSbeDecoder event){
		int limit = event.limit();

		EventValuesDecoder eventValues = event.eventValues();
		int numValues = eventValues.count();
		int[] valueEndOffsets = new int[numValues];

		int i = 0;
		for (EventValuesDecoder eventValue : eventValues){
			valueEndOffsets[i++] = EventValueFbs.createEventValueFbs(builder, eventValue.type().value(), eventValue.value());
		}
		
		stringBuffer.clear();
		stringBuffer.limit(event.getDescription(stringBuffer.array(), 0, event.descriptionLength()));
		int descriptionOffset = builder.createString(stringBuffer);
		
		int valuesOffset = EventFbs.createValuesVector(builder, valueEndOffsets);
		
		EventFbs.startEventFbs(builder);
		EventFbs.addCategory(builder, event.category().value());
		EventFbs.addLevel(builder, event.level().value());
		EventFbs.addEventType(builder, event.eventType().value());
		EventFbs.addSinkId(builder, event.sinkId());
		EventFbs.addTime(builder, event.time());
		EventFbs.addValues(builder, valuesOffset);
		EventFbs.addDescription(builder, descriptionOffset);
		event.limit(limit);
	    return EventFbs.endEventFbs(builder);
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder,
			int sinkId,
			long time,
			byte category,
			byte level,
			byte eventType,
			String description){		
		int descriptionOffset = builder.createString(description);

		EventFbs.startEventFbs(builder);
		
		EventFbs.addCategory(builder, category);
		EventFbs.addLevel(builder, level);
		EventFbs.addEventType(builder, eventType);
		EventFbs.addSinkId(builder, sinkId);
		EventFbs.addTime(builder, time);
		EventFbs.addDescription(builder, descriptionOffset);
	    return EventFbs.endEventFbs(builder);		
	}
}
