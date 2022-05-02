package com.lunar.core;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.UserControlledTimerService.TimestampedTimeout;
import com.lunar.message.sender.TimerEventSender;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

@RunWith(MockitoJUnitRunner.class)
public class UserControlledTimerServiceTest {
	private UserControlledTimerService timerService;
	
	@Mock
	private TimerTask task;
	
	@Mock
	private TimerTask extraTask;
	
	@Mock
	private TimerEventSender timerEventSender;
	
	@Before
	public void setup(){
		timerService = new UserControlledTimerService(System.nanoTime(),
				LocalDateTime.now(),
				timerEventSender);
	}
	
	@Test
	public void givenEmptyWhenAddThenOneTaskOutstanding(){
		timerService.newTimeout(task, 5, TimeUnit.MILLISECONDS);
		assertEquals(1, timerService.timeouts().size());
	}
	
	@Test
	public void givenOneTaskWhenAdvancePassedExpiryThenTaskIsRunAndNoneOutstanding() throws Exception{
		// given
		timerService.newTimeout(task, 5, TimeUnit.MILLISECONDS);
		assertEquals(1, timerService.timeouts().size());
		
		// when
		timerService.advance(5, TimeUnit.MILLISECONDS);
		
		// then
		verify(task, times(1)).run(any());
		assertEquals(0, timerService.timeouts().size());
	}
	
	@Test
	public void givenOneTaskWhenAdvanceNotPassExpiryThenOneOutstanding() throws Exception{
		// given
		timerService.newTimeout(task, 5, TimeUnit.MILLISECONDS);
		assertEquals(1, timerService.timeouts().size());
		
		// when
		timerService.advance(5, TimeUnit.NANOSECONDS);
		
		// then
		verify(task, times(0)).run(any());
		assertEquals(1, timerService.timeouts().size());
	}
	
	@Test
	public void whenAdvanceTimeThenTimerNanoTimeIsAdvanced(){
		long currentTimeNs = timerService.nanoTime();
		
		timerService.advance(5, TimeUnit.NANOSECONDS);		
		currentTimeNs += TimeUnit.NANOSECONDS.toNanos(5);
		assertEquals(currentTimeNs , timerService.nanoTime());

		timerService.advance(6, TimeUnit.SECONDS);		
		currentTimeNs += TimeUnit.SECONDS.toNanos(6);
		assertEquals(currentTimeNs , timerService.nanoTime());
	}
	
	@Test
	public void givenTwoTasksOfSameExpiryTimeThenAdvancePastExpiryThenNoneOutstanding() throws Throwable{
		// given
		Timeout taskTimeout = timerService.newTimeout(task, 5, TimeUnit.MILLISECONDS);
		Timeout extraTaskTimeout = timerService.newTimeout(extraTask, 5, TimeUnit.MILLISECONDS);
		assertEquals(2, timerService.timeouts().size());
		assertFalse(taskTimeout.isExpired());
		assertFalse(extraTaskTimeout.isExpired());
		
		// when
		assertTrue(timerService.advance(6, TimeUnit.MILLISECONDS));
		
		// then
		verify(task, times(1)).run(any());
		verify(extraTask, times(1)).run(any());
		assertEquals(0, timerService.timeouts().size());		
		assertTrue(taskTimeout.isExpired());
		assertTrue(extraTaskTimeout.isExpired());		
	}
	
	@Test
	public void givenOneTaskWhenCancelThenNoneOutstanding() throws Exception{
		// given
		Timeout timeout = timerService.newTimeout(task, 5, TimeUnit.MILLISECONDS);
		TimestampedTimeout tsTimeout = (TimestampedTimeout)timeout;
		assertFalse(tsTimeout.isCancelled());
		
		// when
		tsTimeout.cancel();
		
		// then
		assertTrue(tsTimeout.isCancelled());
		verify(task, times(0)).run(any());
		assertEquals(0, timerService.timeouts().size());			
	}
}
