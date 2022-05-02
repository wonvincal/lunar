package com.lunar.core;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;

import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSinkRef;

public class SlidingWindowThrottleTrackerTest {
	private static TimerEventSender sender;
	private static UserControlledTimerService timerService;
	
	@BeforeClass
	public static void setup(){
		sender = TimerEventSender.of(MessageSender.of(1024, MessageSinkRef.createNaSinkRef()));
		timerService = new UserControlledTimerService(
				System.nanoTime(),
				LocalDateTime.now(),
				sender);		
	}
	
	@Test
	public void test(){
		int numThrottles = 2;
		SlidingWindowThrottleTracker tracker = new SlidingWindowThrottleTracker(
				numThrottles, 
				Duration.ofSeconds(1), 
				timerService);
		
		testThrottle(tracker, numThrottles);
	}

	private static void testThrottle(SlidingWindowThrottleTracker tracker, int expectNumThrottles){
		for (int i = 0; i < expectNumThrottles; i++){
			assertTrue(tracker.getThrottle());
		}		
		assertFalse(tracker.getThrottle());
		
		timerService.advance(900, TimeUnit.MILLISECONDS);
		assertFalse(tracker.getThrottle());

		timerService.advance(99999, TimeUnit.MICROSECONDS);
		assertFalse(tracker.getThrottle());
		
		timerService.advance(1, TimeUnit.MICROSECONDS);
		assertTrue(tracker.getThrottle());		
	}
	
	@Test
	public void testNumThrottlesNotPowerOfTwo(){
		int numThrottles = 6;
		SlidingWindowThrottleTracker tracker = new SlidingWindowThrottleTracker(
				numThrottles, 
				Duration.ofSeconds(1), 
				timerService);
		
		for (int i = 0; i < numThrottles; i++){
			assertTrue(tracker.getThrottle());
		}
		assertFalse(tracker.getThrottle());
		assertFalse(tracker.getThrottle());
		
		timerService.advance(900, TimeUnit.MILLISECONDS);
		assertFalse(tracker.getThrottle());

		timerService.advance(99999, TimeUnit.MICROSECONDS);
		assertFalse(tracker.getThrottle());
		
		timerService.advance(1, TimeUnit.MICROSECONDS);
		for (int i = 0; i < numThrottles; i++){
			assertTrue(tracker.getThrottle());
		}
		assertFalse(tracker.getThrottle());
		assertFalse(tracker.getThrottle());
	}
	
	@Test
	public void testChangeThrottle(){
		int numThrottles = 2;
		SlidingWindowThrottleTracker tracker = new SlidingWindowThrottleTracker(
				numThrottles, 
				Duration.ofSeconds(1), 
				timerService);
		
		numThrottles = 16;
		tracker.changeNumThrottles(numThrottles);
		assertEquals(numThrottles, tracker.numThrottles());
		testThrottle(tracker, numThrottles);

		numThrottles = 6;
		tracker.changeNumThrottles(numThrottles);
		assertEquals(numThrottles, tracker.numThrottles());
		testThrottle(tracker, numThrottles);
	}
	
	@Test
	public void testNextAvailNs(){
		int numThrottles = 2;
		SlidingWindowThrottleTracker tracker = new SlidingWindowThrottleTracker(
				numThrottles, 
				Duration.ofSeconds(1), 
				timerService);
		long expectedTime = timerService.nanoTime();
		assertEquals(expectedTime, tracker.peekNextTime());
		assertEquals(expectedTime, tracker.nextAvailNs());		
		assertTrue(tracker.getThrottle());
		assertTrue(tracker.getThrottle());
		timerService.advance(1, TimeUnit.SECONDS);
		assertEquals(expectedTime + TimeUnit.SECONDS.toNanos(1), tracker.peekNextTime());
		assertEquals(expectedTime + TimeUnit.SECONDS.toNanos(1), tracker.nextAvailNs());		
	}

	@Test
	public void testNextAvailNsForNumThrottleNotPowerOfTwo(){
		int numThrottles = 6;
		SlidingWindowThrottleTracker tracker = new SlidingWindowThrottleTracker(
				numThrottles, 
				Duration.ofSeconds(1), 
				timerService);
		long expectedTime = timerService.nanoTime();
		for (int i = 0; i < numThrottles; i++){			
			assertEquals(expectedTime, tracker.peekNextTime());
			assertEquals(expectedTime, tracker.nextAvailNs());
			assertTrue(tracker.getThrottle());
		}
		assertEquals(SlidingWindowThrottleTracker.NO_OP, tracker.peekNextTime());
		assertEquals(expectedTime + TimeUnit.SECONDS.toNanos(1), tracker.nextAvailNs());
//		assertTrue(tracker.getThrottle());
//		timerService.advance(1, TimeUnit.SECONDS);
//		assertEquals(expectedTime + TimeUnit.SECONDS.toNanos(1), tracker.peekNextTime());
//		assertEquals(expectedTime + TimeUnit.SECONDS.toNanos(1), tracker.nextAvailNs());		
	}

//	@Test(expected=IllegalArgumentException.class)
//	public void testInvalidSize(){
//		// Number of throttles must be a power of two
//		int numThrottles = 20;
//		new SlidingWindowThrottleTracker(
//				numThrottles, 
//				Duration.ofSeconds(1), 
//				timerService);		
//	}
	
//	@Test(expected=IllegalArgumentException.class)
//	public void testInvalidNewNumThrottle(){
//		int numThrottles = 2;
//		ThrottleTracker tracker = new SlidingWindowThrottleTracker(
//				numThrottles, 
//				Duration.ofSeconds(1), 
//				timerService);
//		numThrottles = 20;
//		tracker.changeNumThrottles(numThrottles);
//	}

	@Test
	public void testThrottleTrackerWithGetAndConsumeOfDifferentValue(){
		int numThrottles = 2;
		ThrottleTracker tracker = new SlidingWindowThrottleTracker(
				numThrottles, 
				Duration.ofSeconds(1), 
				timerService);
		
		assertFalse(tracker.getThrottle(3));
		assertTrue(tracker.getThrottle(2));
		assertFalse(tracker.getThrottle(2));
		assertTrue(tracker.getThrottle(1));
		timerService.advance(900, TimeUnit.MILLISECONDS);
		assertFalse(tracker.getThrottle());
		timerService.advance(100, TimeUnit.MILLISECONDS);
		assertTrue(tracker.getThrottle(2));
		assertTrue(tracker.getThrottle());
	}
}
