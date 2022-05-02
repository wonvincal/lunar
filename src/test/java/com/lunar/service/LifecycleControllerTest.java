package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.Lifecycle.LifecycleStateHook;
import com.lunar.core.LifecycleState;
import com.lunar.exception.StateTransitionException;

@RunWith(MockitoJUnitRunner.class)
public class LifecycleControllerTest {
	static final Logger LOG = LogManager.getLogger(LifecycleControllerTest.class);

	private LifecycleController controller;
	
	@Test
	public void testCreate(){
		controller = LifecycleController.of("test-controller");
		assertEquals(LifecycleState.INIT, controller.state());
	}
	
	@Test
	public void testCreateWithHandler(){
		LifecycleStateHook handler = new LifecycleStateHook(){
			@Override
			public CompletableFuture<Boolean> onPendingActive() {
				return CompletableFuture.completedFuture(true);
			}
		};
		controller = LifecycleController.of("test-controller", handler);
		assertEquals(LifecycleState.INIT, controller.state());
		controller.active();
	}
	
	@Test
	public void givenInitWithDifferentTransitionThenInitOnceOnly(){
		final AtomicInteger count = new AtomicInteger(0);
		controller = LifecycleController.of("test-controller", new LifecycleStateHook() {
			@Override
			public void onInit() {
				count.incrementAndGet();
			}
		});
		assertEquals(LifecycleState.INIT, controller.state());
		assertTransition(controller.warmup(), LifecycleState.WARMUP, controller);
		assertTransition(controller.reset(), LifecycleState.RESET, controller);
		assertTransition(controller.recover(), LifecycleState.RECOVERY, controller);
		assertTransition(controller.active(), LifecycleState.ACTIVE, controller);
		assertTransition(controller.reset(), LifecycleState.RESET, controller);
		assertTransition(controller.stop(), LifecycleState.STOPPED, controller);
		
		assertEquals(1, count.get());
	}
	
	@Test
	public void givenInitWhenDifferentTransition(){
		controller = LifecycleController.of("test-controller");
		assertEquals(LifecycleState.INIT, controller.state());
		assertTransition(controller.active(), LifecycleState.ACTIVE, controller);
		
		// Reset to INIT
		controller.state(LifecycleState.INIT);
		assertTransition(controller.recover(), LifecycleState.RECOVERY, controller);

		// Reset to INIT
		controller.state(LifecycleState.INIT);
		assertTransition(controller.warmup(), LifecycleState.WARMUP, controller);

		// Reset to INIT
		controller.state(LifecycleState.INIT);
		assertInvalidTransition(controller.reset(), LifecycleState.INIT, controller);

		// Reset to INIT
		controller.state(LifecycleState.INIT);
		assertInvalidTransition(controller.stop(), LifecycleState.INIT, controller);
	}
	
	@Test
	public void givenWarmupWhenDifferentTransition(){
		controller = LifecycleController.of("test-controller");
		controller.state(LifecycleState.WARMUP);		
		assertEquals(LifecycleState.WARMUP, controller.state());
		assertInvalidTransition(controller.active(), LifecycleState.WARMUP, controller);

		controller.state(LifecycleState.WARMUP);
		assertTransition(controller.warmup(), LifecycleState.WARMUP, controller);

		controller.state(LifecycleState.WARMUP);
		assertInvalidTransition(controller.recover(), LifecycleState.WARMUP, controller);

		controller.state(LifecycleState.WARMUP);
		assertTransition(controller.reset(), LifecycleState.RESET, controller);

		controller.state(LifecycleState.WARMUP);
		assertInvalidTransition(controller.stop(), LifecycleState.WARMUP, controller);
	}
	
	@Test
	public void givenRecoveryWhenDifferentTransition(){
		controller = LifecycleController.of("test-controller");
		controller.state(LifecycleState.RECOVERY);		
		assertEquals(LifecycleState.RECOVERY, controller.state());
		assertTransition(controller.active(), LifecycleState.ACTIVE, controller);

		controller.state(LifecycleState.RECOVERY);
		assertTransition(controller.recover(), LifecycleState.RECOVERY, controller);

		controller.state(LifecycleState.RECOVERY);
		assertTransition(controller.reset(), LifecycleState.RESET, controller);

		controller.state(LifecycleState.RECOVERY);
		assertInvalidTransition(controller.warmup(), LifecycleState.RECOVERY, controller);

		controller.state(LifecycleState.RECOVERY);
		assertInvalidTransition(controller.stop(), LifecycleState.RECOVERY, controller);
	}

	@Test
	public void givenActiveWhenDifferentTransition(){
		controller = LifecycleController.of("test-controller");
		controller.state(LifecycleState.ACTIVE);		
		assertEquals(LifecycleState.ACTIVE, controller.state());
		assertTransition(controller.reset(), LifecycleState.RESET, controller);

		controller.state(LifecycleState.ACTIVE);
		assertInvalidTransition(controller.recover(), LifecycleState.ACTIVE, controller);

		controller.state(LifecycleState.ACTIVE);
		assertInvalidTransition(controller.stop(), LifecycleState.ACTIVE, controller);

		controller.state(LifecycleState.ACTIVE);
		assertInvalidTransition(controller.warmup(), LifecycleState.ACTIVE, controller);

		controller.state(LifecycleState.ACTIVE);
		assertTransition(controller.active(), LifecycleState.ACTIVE, controller);
	}

	@Test
	public void givenIdleWhenDifferentTransition(){
		controller = LifecycleController.of("test-controller");
		controller.state(LifecycleState.RESET);		
		assertEquals(LifecycleState.RESET, controller.state());
		assertTransition(controller.reset(), LifecycleState.RESET, controller);

		controller.state(LifecycleState.RESET);
		assertTransition(controller.recover(), LifecycleState.RECOVERY, controller);

		controller.state(LifecycleState.RESET);
		assertTransition(controller.stop(), LifecycleState.STOPPED, controller);

		controller.state(LifecycleState.RESET);
		assertInvalidTransition(controller.warmup(), LifecycleState.RESET, controller);

		controller.state(LifecycleState.RESET);
		assertTransition(controller.active(), LifecycleState.ACTIVE, controller);
	}

	@Test
	public void givenStoppedWhenDifferentTransition(){
		controller = LifecycleController.of("test-controller");
		controller.state(LifecycleState.STOPPED);		
		assertEquals(LifecycleState.STOPPED, controller.state());
		assertInvalidTransition(controller.reset(), LifecycleState.STOPPED, controller);

		controller.state(LifecycleState.STOPPED);
		assertInvalidTransition(controller.recover(), LifecycleState.STOPPED, controller);

		controller.state(LifecycleState.STOPPED);
		assertTransition(controller.stop(), LifecycleState.STOPPED, controller);

		controller.state(LifecycleState.STOPPED);
		assertInvalidTransition(controller.warmup(), LifecycleState.STOPPED, controller);

		controller.state(LifecycleState.STOPPED);
		assertInvalidTransition(controller.active(), LifecycleState.STOPPED, controller);
	}
	
	@Test
	public void givenInitWhenOnPendingReturnFalseThenNoTransition(){
		LifecycleStateHook handler = new LifecycleStateHook(){
			@Override
			public CompletableFuture<Boolean> onPendingActive() {
				return CompletableFuture.completedFuture(false);
			}
			@Override
			public CompletableFuture<Boolean> onPendingReset() {
				return CompletableFuture.completedFuture(false);
			}
			@Override
			public CompletableFuture<Boolean> onPendingRecovery() {
				return CompletableFuture.completedFuture(false);
			}
			@Override
			public CompletableFuture<Boolean> onPendingStop() {
				return CompletableFuture.completedFuture(false);
			}
			@Override
			public CompletableFuture<Boolean> onPendingWarmup() {
				return CompletableFuture.completedFuture(false);
			}
		};

		controller = LifecycleController.of("test-controller", handler);
		assertInvalidTransition(controller.warmup(), LifecycleState.INIT, controller);
		assertInvalidTransition(controller.reset(), LifecycleState.INIT, controller);
		assertInvalidTransition(controller.recover(), LifecycleState.INIT, controller);
		assertInvalidTransition(controller.stop(), LifecycleState.INIT, controller);
		assertInvalidTransition(controller.warmup(), LifecycleState.INIT, controller);
		
	}

	private static void assertTransition(CompletableFuture<LifecycleState> transitionFuture, LifecycleState expected, LifecycleController controller){
		AtomicInteger numCalls = new AtomicInteger(0);
		CompletableFuture<LifecycleState> nextFuture = transitionFuture.whenComplete((state, cause) -> {
			if (cause != null){
				LOG.error("Caught exception", cause);
				throw new IllegalStateException(cause);
			}
			numCalls.incrementAndGet();
		});
		assertFalse("Caught exception when transitioning to " + expected.name(), nextFuture.isCompletedExceptionally());
		assertEquals(1, numCalls.get());
		assertEquals(expected, controller.state());
	}
	
	private static void assertInvalidTransition(CompletableFuture<LifecycleState> transitionFuture, LifecycleState expected, LifecycleController controller){
		AtomicInteger numCalls = new AtomicInteger(0);
		transitionFuture.whenComplete((state, cause) -> {
			if (cause != null){
				assertTrue(cause instanceof StateTransitionException);
				numCalls.incrementAndGet();
			}
		});
		assertEquals(1, numCalls.get());
		assertEquals(expected, controller.state());
	}
}
