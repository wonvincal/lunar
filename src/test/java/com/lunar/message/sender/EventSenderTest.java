package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalTime;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.EventSbeDecoder.EventValuesDecoder;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.service.ServiceConstant;
import com.lunar.util.ServiceTestHelper;

public class EventSenderTest {
	static final Logger LOG = LogManager.getLogger(EventSenderTest.class);
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	
	private MessageSender sender;
	private EventSender eventSender;
	private final int testSinkId = 1;
	private RingBufferMessageSinkPoller testSinkPoller;
	private MessageSinkRef testSinkRef;
	private Messenger testMessenger;

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.ClientService, "test-client");
		sender = MessageSender.of(ServiceConstant.MAX_MESSAGE_SIZE, refSenderSink);
		eventSender = EventSender.of(sender);
		
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		testSinkPoller = testHelper.createRingBufferMessageSinkPoller(testSinkId, 
				ServiceType.DashboardService, 256, "testDashboard");
		RingBufferMessageSink sink = testSinkPoller.sink();
		testSinkRef = MessageSinkRef.of(sink, "testSink");
		testMessenger = testHelper.createMessenger(sink, "testSinkMessenger");
		testMessenger.registerEvents();
		testSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				testMessenger.receive(event, 0);
				return false;
			}
		});
	}
	
	@Test
	public void testEncodeMessageAndDecode(){
		EventCategory category = EventCategory.CORE;
		String description = "fuxk you";
		EventLevel level = EventLevel.CRITICAL;
		long eventTime = LocalTime.now().toNanoOfDay();
		int sinkId = 1;
		long result = eventSender.sendEvent(testSinkRef, category, level, sinkId, eventTime, description);
		testMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				assertEquals(category.value(), codec.category().value());
				assertEquals(level.value(), codec.level().value());
				assertEquals(eventTime, codec.time());
				assertEquals(EventType.NULL_VAL, codec.eventType());
				assertEquals(sinkId, codec.sinkId());
				assertEquals(0, codec.eventValues().count());
				String receivedDescription = codec.description();
				assertEquals(0, description.compareTo(receivedDescription));
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
	
	@Test
	public void testEncodeMessageAndDecodeWithEventType(){
		EventCategory category = EventCategory.CORE;
		String description = "fuxk you";
		EventLevel level = EventLevel.CRITICAL;
		EventType eventType = EventType.THROTTLED;
		long eventTime = LocalTime.now().toNanoOfDay();
		int sinkId = 1;
		long result = eventSender.sendEvent(testSinkRef, category, level, eventType, sinkId, eventTime, description);
		testMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				assertEquals(category.value(), codec.category().value());
				assertEquals(level.value(), codec.level().value());
				assertEquals(eventTime, codec.time());
				assertEquals(eventType, codec.eventType());
				assertEquals(sinkId, codec.sinkId());
				assertEquals(0, codec.eventValues().count());
				String receivedDescription = codec.description();
				assertEquals(0, description.compareTo(receivedDescription));
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}

	@Test
	public void testEncodeMessageAndDecodeWithOneValue(){
		EventCategory category = EventCategory.CORE;
		String description = "fuxk you";
		EventLevel level = EventLevel.CRITICAL;
		long eventTime = LocalTime.now().toNanoOfDay();
		int sinkId = 1;
		EventValueType eventValueType = EventValueType.ANY;
		long eventValue = 1234567890123456789l;
		long result = eventSender.sendEventWithOneValue(testSinkRef, category, level, sinkId, eventTime, description, eventValueType, eventValue);
		testMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				assertEquals(category.value(), codec.category().value());
				assertEquals(level.value(), codec.level().value());
				assertEquals(eventTime, codec.time());
				assertEquals(sinkId, codec.sinkId());
				EventValuesDecoder eventValues = codec.eventValues();
				assertEquals(1, eventValues.count());
				for (EventValuesDecoder item : eventValues){
					assertEquals(eventValueType.value(), item.type().value());
					assertEquals(eventValue, item.value());
				}
				assertEquals(0, description.compareTo(codec.description()));
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
	
	@Test
	public void testEncodeMessageAndDecodeWithTwoValues(){
		EventCategory category = EventCategory.CORE;
		String description = "fuxk you";
		EventLevel level = EventLevel.CRITICAL;
		EventType eventType = EventType.THROTTLED; 
		long eventTime = LocalTime.now().toNanoOfDay();
		int sinkId = 1;
		EventValueType eventValueType = EventValueType.ANY;
		long eventValue = 1234567890123456789l;
		EventValueType eventValueType2 = EventValueType.SECURITY_SID;
		long eventValue2 = 1000001l;
		long result = eventSender.sendEventWithTwoValues(testSinkRef, category, level, eventType, sinkId, eventTime, description, eventValueType, eventValue, eventValueType2, eventValue2);
		testMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				assertEquals(category.value(), codec.category().value());
				assertEquals(level.value(), codec.level().value());
				assertEquals(eventTime, codec.time());
				assertEquals(sinkId, codec.sinkId());
				EventValuesDecoder eventValues = codec.eventValues();
				assertEquals(2, eventValues.count());
				int i = 0;
				for (EventValuesDecoder item : eventValues){
					if (i == 0){
						assertEquals(eventValueType.value(), item.type().value());
						assertEquals(eventValue, item.value());
					}
					else {
						assertEquals(eventValueType2.value(), item.type().value());
						assertEquals(eventValue2, item.value());						
					}
					i++;
				}
				assertEquals(0, description.compareTo(codec.description()));
			}
		});
		testSinkPoller.pollAll();
		assertEquals(MessageSink.OK, result);
	}
}
