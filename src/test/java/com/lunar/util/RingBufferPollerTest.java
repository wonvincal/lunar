package com.lunar.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventPoller.PollState;
import com.lmax.disruptor.RingBuffer;
import com.lunar.message.binary.MessageSinkEventFactory;

import org.agrona.MutableDirectBuffer;

public class RingBufferPollerTest {
	@Test
	public void testPollProcessingOne() throws Exception{
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(MessageSinkEventFactory.of(), 1024);
		
		long sequence = ringBuffer.next();
		try {
			MutableDirectBuffer buffer = ringBuffer.get(sequence);
			buffer.putInt(0, 1);
			buffer.putInt(4, 2);
			buffer.putInt(8, 3);
			buffer.putInt(12, 4);
		} finally {
			ringBuffer.publish(sequence);
		}
		
		EventPoller<MutableDirectBuffer> poller = ringBuffer.newPoller();
		PollState pollState = poller.poll(new EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				assertEquals(1, event.getInt(0));
				assertEquals(2, event.getInt(4));
				assertEquals(3, event.getInt(8));
				assertEquals(4, event.getInt(12));
				return false;
			}
		});
		assertEquals(PollState.PROCESSING, pollState);
	}

	@Test
	public void testPollProcessingNone() throws Exception{
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(MessageSinkEventFactory.of(), 1024);
		
		EventPoller<MutableDirectBuffer> poller = ringBuffer.newPoller();
		PollState pollState = poller.poll(new EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				assertEquals(1, event.getInt(0));
				assertEquals(2, event.getInt(4));
				assertEquals(3, event.getInt(8));
				assertEquals(4, event.getInt(12));
				return false;
			}
		});
		assertEquals(PollState.IDLE, pollState);
	}

	@Test
	public void testPollProduceOneConsumeTwo() throws Exception{
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(MessageSinkEventFactory.of(), 1024);
		
		long sequence = ringBuffer.next();
		try {
			MutableDirectBuffer buffer = ringBuffer.get(sequence);
			buffer.putInt(0, 1);
			buffer.putInt(4, 2);
			buffer.putInt(8, 3);
			buffer.putInt(12, 4);
		} finally {
			ringBuffer.publish(sequence);
		}
		
		EventPoller<MutableDirectBuffer> poller = ringBuffer.newPoller();
		PollState pollState = poller.poll(new EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				assertEquals(1, event.getInt(0));
				assertEquals(2, event.getInt(4));
				assertEquals(3, event.getInt(8));
				assertEquals(4, event.getInt(12));
				return false;
			}
		});

		assertEquals(PollState.PROCESSING, pollState);
		
		pollState = poller.poll(new EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				assertEquals(1, event.getInt(0));
				assertEquals(2, event.getInt(4));
				assertEquals(3, event.getInt(8));
				assertEquals(4, event.getInt(12));
				return false;
			}
		});
		
		assertEquals(PollState.IDLE, pollState);
	}

}
