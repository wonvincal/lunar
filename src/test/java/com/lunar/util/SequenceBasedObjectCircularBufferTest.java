package com.lunar.util;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lunar.exception.OutsideOfBufferRangeException;
import com.lunar.fsm.channelbuffer.ChannelBufferContext;
import com.lunar.fsm.channelbuffer.ChannelEvent;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SequenceBasedObjectCircularBufferTest {
	private EventFactory<ChannelEvent> eventFactory = ChannelBufferContext.CHANNEL_EVENT_FACTORY;

	@Test
	public void testCreate(){
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(1, eventFactory);
		assertTrue(buffer.isEmpty());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testZeroCapacity(){
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(0, eventFactory);
		assertTrue(buffer.isEmpty());
	}
	
	@Test
	public void testGetNonExist(){
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(10, eventFactory);
		assertNull(buffer.get(1));
		assertTrue(buffer.isEmpty());
	}

	@Test(expected=OutsideOfBufferRangeException.class)
	public void testClaimOutOfAvailRange() throws OutsideOfBufferRangeException{
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(10, eventFactory);
		assertNull(buffer.claim(100));
	}
	
	@Test
	public void testRegularUsage() throws Exception{
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;
		int start = 0;
		int count = 100;
		
		int bufferSize = 128;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		for (int seq = start; seq < count; seq++){
			buffer.claim(seq).merge(channelId, 
					seq, 
					seq, 
					templateId, 
					sourceBuffer, 
					0, 
					bufferSize);
		}
		assertFalse(buffer.isEmpty());
		
		// Verify contains
		for (int seq = start; seq < count; seq++){
			assertTrue(buffer.contains(seq));
		}
		
		for (int seq = start + count; seq < count + count; seq++){
			assertFalse(buffer.contains(seq));
		}

		// Flush and check
		AtomicInteger expectedNextSeq = new AtomicInteger(start);
		EventHandler<ChannelEvent> channelEventHandler = new EventHandler<ChannelEvent>() {
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				int expected = expectedNextSeq.getAndIncrement();
				assertEquals(expected, sequence);
				assertEquals(expected, event.channelSeq());
				assertEquals(channelId, event.channelId());
			}
		};
		long seqOfCurrentFirstElement = buffer.flushTillNull(channelEventHandler);
		assertEquals(start + count, seqOfCurrentFirstElement);
		
		assertTrue(buffer.isEmpty());
	}
	
	@Test
	public void testRegularUsageWithIntermittentClear() throws Exception{
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;
		int start = 0;
		int count = 100;
		
		int bufferSize = 128;
		
		buffer.clearAndStartSequenceAt(0);
		buffer.clearAndStartSequenceAt(0);
		buffer.clearAndStartSequenceAt(0);
		
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		for (int seq = start; seq < count; seq++){
			buffer.claim(seq).merge(channelId, 
					seq, 
					seq, 
					templateId, 
					sourceBuffer, 
					0, 
					bufferSize);
		}
		assertFalse(buffer.isEmpty());
		
		// Verify contains
		for (int seq = start; seq < count; seq++){
			assertTrue(buffer.contains(seq));
		}
		
		for (int seq = start + count; seq < count + count; seq++){
			assertFalse(buffer.contains(seq));
		}

		// Flush and check
		AtomicInteger expectedNextSeq = new AtomicInteger(start);
		EventHandler<ChannelEvent> channelEventHandler = new EventHandler<ChannelEvent>() {
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				int expected = expectedNextSeq.getAndIncrement();
				assertEquals(expected, sequence);
				assertEquals(expected, event.channelSeq());
				assertEquals(channelId, event.channelId());
			}
		};
		long seqOfCurrentFirstElement = buffer.flushTillNull(channelEventHandler);
		assertEquals(start + count, seqOfCurrentFirstElement);
		
		assertTrue(buffer.isEmpty());
	}
	
	@Test
	public void testClear() throws OutsideOfBufferRangeException{
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;		
		int start = 0;
		int count = 100;
		
		int bufferSize = 128;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		for (int seq = start; seq < count; seq++){
			buffer.claim(seq).merge(channelId, 
					seq, 
					seq, 
					templateId, 
					sourceBuffer, 
					0, 
					bufferSize);
		}
		assertFalse(buffer.isEmpty());
		
		buffer.clearAndStartSequenceAt(0);
		assertTrue(buffer.isEmpty());
		for (int seq = start; seq < count; seq++){
			assertFalse(buffer.contains(seq));
		}
	}
	
	@Test
	public void testClearUpTo() throws Exception{
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;		
		int start = 0;
		int count = 100;
		
		int bufferSize = 128;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		for (int seq = start; seq < count; seq++){
			buffer.claim(seq).merge(channelId, 
					seq, 
					seq, 
					templateId, 
					sourceBuffer, 
					0, 
					bufferSize);
		}
		assertFalse(buffer.isEmpty());
		
		int clearUpToSeq = 49;
		buffer.clearUpTo(clearUpToSeq);
		assertEquals(clearUpToSeq + 1, buffer.sequence());
		assertFalse(buffer.isEmpty());
		for (int seq = clearUpToSeq + 1; seq < count; seq++){
			assertTrue(buffer.contains(seq));
		}
		
		AtomicInteger expectedNextSeq = new AtomicInteger(clearUpToSeq + 1);
		EventHandler<ChannelEvent> channelEventHandler = new EventHandler<ChannelEvent>() {
			@Override
			public void onEvent(ChannelEvent event, long sequence, boolean endOfBatch) throws Exception {
				int expected = expectedNextSeq.getAndIncrement();
				assertEquals(expected, sequence);
				assertEquals(expected, event.channelSeq());
				assertEquals(channelId, event.channelId());
			}
		};
		long seqOfCurrentFirstElement = buffer.flushTillNull(channelEventHandler);
		assertEquals(start + count, seqOfCurrentFirstElement);
	}
	
	/**
	 * For example,
	 * seqOffset = 0;
	 * buffer.claim(1).merge(1, 2, 3, 4, 5); // the indexing sequence is 1 + 0 = 1
	 * buffer.clear(); // seqOffset = 16 (let say size of buffer is 16)
	 * buffer.contains(1) will return false // the indexing sequence is 1 + 16 = 17
	 * @throws OutsideOfBufferRangeException 
	 */
	@Test
	public void testElementNoLongExistAfterClear() throws OutsideOfBufferRangeException{
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;		
		int start = 0;
		
		int bufferSize = 128;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		buffer.claim(start).merge(channelId, 
				start, 
				start, 
				templateId, 
				sourceBuffer, 
				0, 
				bufferSize);
		assertTrue(buffer.contains(start));
		assertFalse(buffer.isEmpty());
		assertNotNull(buffer.get(start));
		
		buffer.clearAndStartSequenceAt(start);
		assertFalse(buffer.contains(start));
		assertTrue(buffer.isEmpty());
		assertNull(buffer.get(start));
	}
	
	@Test(expected=OutsideOfBufferRangeException.class)
	public void testOverflow() throws OutsideOfBufferRangeException{
		int capacity = 100;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;		
		int start = 0;
		int count = expectedCapacity * 2;
		buffer.clearAndStartSequenceAt(1);
		int bufferSize = 128;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		for (int seq = start; seq < count; seq++){
			buffer.claim(seq).merge(channelId, 
					seq, 
					seq, 
					templateId, 
					sourceBuffer, 
					0, 
					bufferSize);
		}
	}

	@Test(expected=OutsideOfBufferRangeException.class)
	public void testClearUpToOutOfRangeSequence() throws Exception {
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		SequenceBasedObjectCircularBuffer<ChannelEvent> buffer = SequenceBasedObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		int channelId = 11111;
		int templateId = 15;		
		int start = 0;
		int count = 100;
		
		buffer.clearAndStartSequenceAt(0);
		
		int bufferSize = 128;
		MutableDirectBuffer sourceBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
		buffer.startSequence(start);
		for (int seq = start; seq < count; seq++){
			buffer.claim(seq).merge(channelId, 
					seq, 
					seq, 
					templateId, 
					sourceBuffer, 
					0, 
					bufferSize);
		}
		assertFalse(buffer.isEmpty());
		
		buffer.clearUpTo(start + expectedCapacity + 1);
	}
}
