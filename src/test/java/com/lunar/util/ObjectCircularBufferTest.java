package com.lunar.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.common.math.DoubleMath;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lunar.exception.OutsideOfBufferRangeException;
import com.lunar.message.binary.MessageSinkEventFactory;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventSbeEncoder;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.sender.EventSender;
import com.lunar.service.ServiceConstant;
import com.lunar.util.ObjectCircularBuffer.CancellableEventHandler;

public class ObjectCircularBufferTest {
	private static final Logger LOG = LogManager.getLogger(ObjectCircularBufferTest.class);

	private EventFactory<MutableDirectBuffer> eventFactory = MessageSinkEventFactory.of(); 

	@Test
	public void testCreate(){
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(1, eventFactory);
		assertTrue(buffer.isEmpty());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testZeroCapacity(){
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(0, eventFactory);
		assertTrue(buffer.isEmpty());
	}

	@Test
	public void testClaim() throws OutsideOfBufferRangeException{
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(10, eventFactory);
		assertNotNull(buffer.claim());
	}

	@Test
	public void testFlushAndReturnLast(){
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());

		MutableDirectBuffer result = buffer.flushAndReturnLast();
		assertNull(result);

		// Claim
		buffer.claim().putInt(0, 55555);
		result = buffer.flushAndReturnLast();
		assertEquals(55555, result.getInt(0));
		assertEquals(0, buffer.size());
		
		buffer.claim().putInt(0, 55555);
		buffer.claim().putInt(0, 66666);
		result = buffer.flushAndReturnLast();
		assertEquals(66666, result.getInt(0));
		assertEquals(0, buffer.size());
		
		
		buffer.claim().putInt(0, 55555);
		buffer.claim().putInt(0, 66666);
		buffer.claim().putInt(0, 77777);
		buffer.claim().putInt(0, 88888);
		buffer.claim().putInt(0, 99999);
		result = buffer.flushAndReturnLast();
		assertEquals(99999, result.getInt(0));
		assertEquals(0, buffer.size());
	}
	
	@Test
	public void testNextAndSize(){
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		// Claim
		buffer.claim().putInt(0, 55555);
		buffer.claim().putInt(0, 66666);
		buffer.claim().putInt(0, 77777);
		buffer.claim().putInt(0, 88888);

		assertEquals(4, buffer.size());
		buffer.next();
		assertEquals(3, buffer.size());
		buffer.next();
		assertEquals(2, buffer.size());
		buffer.next();
		assertEquals(1, buffer.size());
		buffer.next();
		assertEquals(0, buffer.size());
		buffer.next();
		assertEquals(0, buffer.size());
		buffer.claim().putInt(0, 99999);
		assertEquals(1, buffer.size());
	}
	
	@Test
	public void testFlushThenPeek() throws Exception{
		int capacity = 4;
		ObjectCircularBuffer<MutableDirectBufferEvent> buffer = ObjectCircularBuffer.of(capacity, MutableDirectBufferEvent.FACTORY_INSTANCE);
		assertEquals(capacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		MutableDirectBufferEvent claimed = buffer.claim();
		
		MutableDirectBuffer directBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		EventSbeEncoder sbe = new EventSbeEncoder();
		int length = EventSender.encodeEventOnly(directBuffer, 0, sbe, 
				EventCategory.CORE, 
				EventLevel.CRITICAL, 
				EventType.CONNECTION, 
				1, 2, "test", EventValueType.BEST_SPOT, 3);
		claimed.size(length);
		claimed.buffer().putBytes(0, directBuffer, 0, length);
		
		buffer.claim();
		claimed.size(length);
		claimed.buffer().putBytes(0, directBuffer, 0, length);
		
		buffer.claim();
		claimed.size(length);
		claimed.buffer().putBytes(0, directBuffer, 0, length);
		
		buffer.claim();
		claimed.size(length);
		claimed.buffer().putBytes(0, directBuffer, 0, length);

		int numPeeked = 0;
		try {
			numPeeked = buffer.peekTillEmpty(PEEK);
		}
		catch (Exception e){
			System.out.println("Caught exception");
			e.printStackTrace();
		}
		assertEquals(4, numPeeked);
	}
	
    private static EventHandler<MutableDirectBufferEvent> PEEK = (MutableDirectBufferEvent event, long sequence, boolean endOfBatch) -> {
    };

	@Test
	public void testClear(){
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());

		// Claim
		buffer.claim().putInt(0, 55555);
		buffer.claim().putInt(0, 66666);
		buffer.claim().putInt(0, 77777);
		buffer.claim().putInt(0, 88888);
		
		buffer.clear();
		assertTrue(buffer.isEmpty());
	}
	
	@Test
	public void testRegularUsage() throws Exception {
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());

		// Claim
		double refDouble = 1.23456d;
		int refInt = 55555;
		MutableDirectBuffer claimed = buffer.claim();
		claimed.putDouble(0, refDouble);
		claimed.putInt(8, refInt);
		
		// Flush
		AtomicInteger expectedCount = new AtomicInteger(1);
		buffer.flushTillEmpty(new EventHandler<MutableDirectBuffer>() {
			
			@Override
			public void onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				expectedCount.getAndDecrement();
				assertTrue(DoubleMath.fuzzyEquals(refDouble, event.getDouble(0), 0.00001));
				assertEquals(refInt, event.getInt(8));
			}
		});
		assertEquals(0, expectedCount.get());
	}
	
	private static class ExpectedValue {
		double doubleValue;
		int intValue;
	}
	
	@Test
	public void testRegularUsageMultiple() throws Exception {
		int capacity = 1000;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());

		// Claim
		double refDouble = 1.23456d;
		int refInt = 55555;
		double nextDouble = 1.23456d;
		int nextInt = 55555;
		int count = 777;
		for (int i = 0; i < count; i++){
			MutableDirectBuffer claimed = buffer.claim();
			claimed.putDouble(0, nextDouble);
			claimed.putInt(8, nextInt);
			nextDouble++;
			nextInt++;
		}
		
		ExpectedValue expectedValue = new ExpectedValue();
		expectedValue.doubleValue = refDouble;
		expectedValue.intValue = refInt;
		
		// Flush
		AtomicInteger expectedCount = new AtomicInteger(0);
		buffer.flushTillEmpty(new EventHandler<MutableDirectBuffer>() {
			
			@Override
			public void onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				expectedCount.getAndIncrement();
				assertTrue(DoubleMath.fuzzyEquals(expectedValue.doubleValue, event.getDouble(0), 0.00001));
				assertEquals(expectedValue.intValue, event.getInt(8));
				expectedValue.doubleValue++;
				expectedValue.intValue++;
			}
		});
		assertEquals(count, expectedCount.get());		
	}
	
	@Test(expected=BufferOverflowException.class)
	public void testBufferOverflow(){
		int capacity = 128;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());
		
		// Claim
		double nextDouble = 1.23456d;
		int nextInt = 55555;
		int count = 777;
		for (int i = 0; i < count; i++){
			MutableDirectBuffer claimed = buffer.claim();
			claimed.putDouble(0, nextDouble);
			claimed.putInt(8, nextInt);
			nextDouble++;
			nextInt++;
		}
	}
	
	@Test 
	public void givenBufferWhenOverflowAndFlushTilCancelThenFreeOneElement()throws Exception{
		int capacity = 128;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());

		int claimedCount = 0;
		double refDouble = 1.23456d;
		int refInt = 55555;
		double nextDouble = refDouble;
		int nextInt = refInt;
		try{
			// Claim
			int count = 777;

			for (int i = 0; i < count; i++){
				MutableDirectBuffer claimed = buffer.claim();
				claimed.putDouble(0, nextDouble);
				claimed.putInt(8, nextInt);
				nextDouble++;
				nextInt++;
				claimedCount++;
			}
		}
		catch (BufferOverflowException e){
			LOG.info("Expected BufferOverflowException");
		}
		
		assertEquals(expectedCapacity, claimedCount);
		
		ExpectedValue expectedValue = new ExpectedValue();
		expectedValue.doubleValue = refDouble;
		expectedValue.intValue = refInt;
		
		// Flush
		AtomicInteger receivedCount = new AtomicInteger(0);
		int eventCount = buffer.flushTillEmptyOrCancelled(new CancellableEventHandler<MutableDirectBuffer>() {

			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				receivedCount.getAndIncrement();
				return false;
			}
		}, true);
		assertEquals(1, receivedCount.get());
		assertEquals(1, eventCount);
		assertEquals(capacity - 1, buffer.size());
	}

	
	@Test
	public void givenBufferWhenOverflowAndFlushThenOKToClaim() throws Exception{
		int capacity = 128;
		int expectedCapacity = BitUtil.nextPowerOfTwo(capacity);
		ObjectCircularBuffer<MutableDirectBuffer> buffer = ObjectCircularBuffer.of(capacity, eventFactory);
		assertEquals(expectedCapacity, buffer.capacity());
		assertTrue(buffer.isEmpty());

		int claimedCount = 0;
		double refDouble = 1.23456d;
		int refInt = 55555;
		double nextDouble = refDouble;
		int nextInt = refInt;
		try{
			// Claim
			int count = 777;

			for (int i = 0; i < count; i++){
				MutableDirectBuffer claimed = buffer.claim();
				claimed.putDouble(0, nextDouble);
				claimed.putInt(8, nextInt);
				nextDouble++;
				nextInt++;
				claimedCount++;
			}
		}
		catch (BufferOverflowException e){
			LOG.info("Expected BufferOverflowException");
		}
		
		assertEquals(expectedCapacity, claimedCount);
		
		ExpectedValue expectedValue = new ExpectedValue();
		expectedValue.doubleValue = refDouble;
		expectedValue.intValue = refInt;
		
		// Flush
		AtomicInteger receivedCount = new AtomicInteger(0);
		int eventCount = buffer.flushTillEmpty(new EventHandler<MutableDirectBuffer>() {
			
			@Override
			public void onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				receivedCount.getAndIncrement();
				assertTrue("count: " +receivedCount.get() + ", expected : " + expectedValue.doubleValue + "actual:" + event.getDouble(0), DoubleMath.fuzzyEquals(expectedValue.doubleValue, event.getDouble(0), 0.00001));
				assertEquals(expectedValue.intValue, event.getInt(8));
				expectedValue.doubleValue++;
				expectedValue.intValue++;
			}
		});
		assertEquals(claimedCount, receivedCount.get());		
		assertEquals(eventCount, claimedCount);
		
		// Claim and add again
		claimedCount = 0;
		try{
			// Claim
			int count = 777;

			for (int i = 0; i < count; i++){
				MutableDirectBuffer claimed = buffer.claim();
				claimed.putDouble(0, nextDouble);
				claimed.putInt(8, nextInt);
				nextDouble++;
				nextInt++;
				claimedCount++;
			}
		}
		catch (BufferOverflowException e){
			LOG.info("Expected BufferOverflowException");
		}
		assertEquals(expectedCapacity, claimedCount);
	}
	
	@Test
	public void testNegative(){
		int readCursor = Integer.MAX_VALUE;
		LOG.info("ReadCursor: {} - {}", readCursor, Integer.toBinaryString(readCursor));
		readCursor++;
		LOG.info("ReadCursor: {} - {}", readCursor, Integer.toBinaryString(readCursor));
		readCursor++;
		LOG.info("ReadCursor: {} - {}", readCursor, Integer.toBinaryString(readCursor));
		readCursor++;
		LOG.info("ReadCursor: {} - {}", readCursor, Integer.toBinaryString(readCursor));
	}
}
