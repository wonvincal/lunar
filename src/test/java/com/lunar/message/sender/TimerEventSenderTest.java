package com.lunar.message.sender;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TimerEventSenderTest {
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final TimerEventSbeEncoder sbeEncoder = new TimerEventSbeEncoder();

	@Before
	public void setup(){
		buffer.setMemory(0, buffer.capacity(), (byte)0);
	}
	
	@Test
	public void testEncodeDecode(){
		final int refKey = 101;
		final TimerEventType refTimerEventType = TimerEventType.COMMAND_TIMEOUT;
		final long refStartTimeNs = System.nanoTime();
		final long refExpiryTimeNs = System.nanoTime() + 1_000_000_000;
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-ref");
		final int refDstSinkId = 2;
		
		MessageSender sender = MessageSender.of(ServiceConstant.DEFAULT_FRAGMENT_SIZE,
				refSenderSink);
		final int refSeq = sender.peekNextSeq();
		
		TimerEventSender.encodeTimerEvent(sender, 
				refDstSinkId,
				buffer,
				0,
				sbeEncoder,
				refKey,
				refTimerEventType,
				refStartTimeNs,
				refExpiryTimeNs);
				
		MessageReceiver receiver = MessageReceiver.of();
		receiver.timerEventHandlerList().add(new Handler<TimerEventSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, 
							   int offset, 
							   MessageHeaderDecoder header,
							   TimerEventSbeDecoder codec) {
				assertEquals(refSenderSink.sinkId(), header.senderSinkId());
				assertEquals(refDstSinkId, header.dstSinkId());
				assertEquals(refSeq, header.seq());
				assertEquals(refKey, codec.clientKey());
				assertEquals(refExpiryTimeNs, codec.expiryTime());
				assertEquals(refStartTimeNs, codec.startTime());
				assertEquals(refTimerEventType, codec.timerEventType());				
			}
		});
		receiver.receive(buffer, 0);
	}
}
