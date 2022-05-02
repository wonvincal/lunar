package com.lunar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.lunar.exception.StateTransitionException;

public class LifecycleUtil {
	public static void advanceTo(Lifecycle lifecycle, LifecycleState target){
		AtomicInteger expectedCalls = new AtomicInteger(0);
		AtomicInteger unexpectedCalls = new AtomicInteger(0);
		advanceTo(lifecycle, target, expectedCalls, unexpectedCalls);
		assertEquals(0, unexpectedCalls.get());
		assertEquals(target, lifecycle.state());
		assertEquals(1, expectedCalls.get());
	}

	public static void failToAdvance(Lifecycle lifecycle, LifecycleState target){
		LifecycleState origState = lifecycle.state();
		AtomicInteger expectedCalls = new AtomicInteger(0);
		AtomicInteger unexpectedCalls = new AtomicInteger(0);
		advanceTo(lifecycle, target, unexpectedCalls, expectedCalls);
		assertEquals(0, unexpectedCalls.get());
		assertNotEquals(target, lifecycle.state());
		assertEquals(origState, lifecycle.state());
		assertEquals(1, expectedCalls.get());
	}

	public static void advanceTo(Lifecycle lifecycle, 
			LifecycleState target, 
			AtomicInteger numSuccessfulFutureCount, 
			AtomicInteger numFailedFutureCount){
		// When
		CompletableFuture<LifecycleState> future;
		switch (target){
		case ACTIVE:
			future = lifecycle.active();
			break;
		case RESET:
			future = lifecycle.reset();
			break;
		case RECOVERY:
			future = lifecycle.recover();
			break;
		case STOPPED:
			future = lifecycle.stop();
			break;
		case WARMUP:
			future = lifecycle.warmup();
			break;
		default:
			throw new UnsupportedOperationException();
		}
		future.whenComplete((state, cause) -> {
			if (cause instanceof StateTransitionException){
				numFailedFutureCount.incrementAndGet();
				return;
			}
			numSuccessfulFutureCount.incrementAndGet();
		});
	}
	
}
