package com.lunar.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

/**
 * assert needs to be turned on as a jvm argument in eclipse "-ea" in 
 * order to test {@link givenEmptyWhenAddEmptyValueThenThrowException}.
 * 
 * @see http://stackoverflow.com/questions/11415160/how-to-enable-the-java-keyword-assert-in-eclipse-program-wise
 * @author Calvin
 *
 */
public class NonZeroLongCircularBufferTest {

	@Test
	public void whenCreateBufferWithNonPowerOfTwoThenCapacityIsNextPowOfTwo(){
		NonZeroLongCircularBuffer buffer = new NonZeroLongCircularBuffer(100);
		
		assertEquals(128, buffer.capacity());
		assertTrue("should be empty", buffer.isEmpty());
		assertTrue("should be not be full", (!buffer.isFull()));
	}
	
	@Test
	public void givenEmptyWhenAddThenOK(){
		NonZeroLongCircularBuffer buffer = new NonZeroLongCircularBuffer(100);

		long expected = 123456;
		buffer.add(expected);
		assertTrue("should be not be empty", !buffer.isEmpty());
		
		assertEquals(expected, buffer.peek());
		assertEquals(1, buffer.size());
	}
	
	@Test
	public void givenEmptyWhenNextThenReturnFalse(){
		NonZeroLongCircularBuffer buffer = new NonZeroLongCircularBuffer(100);
		
		long value = buffer.next();
		assertEquals(value, NonZeroLongCircularBuffer.EMPTY_VALUE);
	}
	
	@Test
	public void givenEmptyWhenAddNItemsThenTakesNItems(){
		NonZeroLongCircularBuffer buffer = new NonZeroLongCircularBuffer(100);

		int n = 10;
		for (int i = 1; i <= n; i++){
			buffer.add(i);
		}
		long sum = 0;
		long actualCount = 0;
		for (int i = 1; i <= n + 50; i++){
			long value = buffer.next();
			if (value != NonZeroLongCircularBuffer.EMPTY_VALUE){
				sum += value;
				actualCount++;
			}
		}
		assertEquals(n*(n+1)/2, sum);
		assertEquals(n, actualCount);
		assertEquals(0, buffer.size());
	}
	
	@Test
	public void givenEmptyWhenAddMoreThanCapacityThenWontFail(){
		NonZeroLongCircularBuffer buffer = new NonZeroLongCircularBuffer(30);

		int n = 65;
		int failedCount = 0;
		for (int i = 1; i <= n; i++){
			if (!buffer.add(i)){
				failedCount++;
			}
		}
		
		int expectedCapacity = 32;
		assertEquals(expectedCapacity, buffer.capacity());
		assertEquals(n - expectedCapacity, failedCount);
		
		long sum = 0;
		for (int i = 1; i <= n + 50; i++){
			long value = buffer.next();
			if (value != NonZeroLongCircularBuffer.EMPTY_VALUE){
				sum += value;
			}
		}
		assertEquals(expectedCapacity*(expectedCapacity+1)/2, sum);
	}
	
	@Ignore
	@Test(expected=AssertionError.class)
	public void givenEmptyWhenAddEmptyValueThenThrowException(){
		NonZeroLongCircularBuffer buffer = new NonZeroLongCircularBuffer(30);
		buffer.add(NonZeroLongCircularBuffer.EMPTY_VALUE);
	}
}
