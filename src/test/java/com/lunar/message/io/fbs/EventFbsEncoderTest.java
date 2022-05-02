package com.lunar.message.io.fbs;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.time.LocalTime;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.EventSbeEncoder;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.sender.EventSender;
import com.lunar.service.ServiceConstant;

public class EventFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(EventFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final EventSbeEncoder sbeEncoder = new EventSbeEncoder();
	private final EventSbeDecoder sbeDecoder = new EventSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);

	@Test
	public void testSbeFbs(){
		int numValues = 3;
		EventValueType[] valueTypes = new EventValueType[numValues];
		long[] values = new long[numValues];
		valueTypes[0] = EventValueType.DELTA;
		values[0] = 500;
		valueTypes[1] = EventValueType.ORDER_SID;
		values[1] = 900000000;
		valueTypes[2] = EventValueType.SECURITY_SID;
		values[2] = -1234567890123456789l;
		
		int sinkId = 4;
		long eventTime = LocalTime.now().toNanoOfDay();
		String description = "testing testing testing";
		EventCategory category = EventCategory.STRATEGY;
		EventLevel level = EventLevel.INFO;
		EventType eventType = EventType.ORDER_EXPLAIN;
		EventSender.encodeEventOnly(buffer, 
				0, 
				sbeEncoder, 
				category, 
				level, 
				eventType, 
				sinkId, 
				eventTime, 
				description, 
				valueTypes, 
				values);
		sbeDecoder.wrap(buffer, 0, EventSbeDecoder.BLOCK_LENGTH, EventSbeDecoder.SCHEMA_VERSION);
		
		// When
		int offset = EventFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);

		EventFbs eventFbs = EventFbs.getRootAsEventFbs(builder.dataBuffer());
		assertEquals(eventFbs.category(), category.value());
		assertEquals(eventFbs.level(), level.value());
		assertEquals(eventFbs.sinkId(), sinkId);
		assertEquals(eventFbs.time(), eventTime);
		assertEquals(0, eventFbs.description().compareTo(description));
		assertEquals(eventFbs.eventType(), eventType.value());
		
		int valuesLength = eventFbs.valuesLength();
		assertEquals(numValues, valuesLength);
		EventValueFbs valueFbs = eventFbs.values(0);
		assertEquals(valueTypes[0].value(), valueFbs.type());
		assertEquals(values[0], valueFbs.value());
		valueFbs = eventFbs.values(1);
		assertEquals(valueTypes[1].value(), valueFbs.type());
		assertEquals(values[1], valueFbs.value());
		valueFbs = eventFbs.values(2);
		LOG.debug("fbs value: {}", valueFbs.value());
		assertEquals(valueTypes[2].value(), valueFbs.type());
		assertEquals(values[2], valueFbs.value());
	}
}
