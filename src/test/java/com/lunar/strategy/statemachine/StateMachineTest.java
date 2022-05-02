package com.lunar.strategy.statemachine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StateMachineTest {
	static final int STATE1 = 1;
	static final int STATE2 = 5;
	static final int STATE3 = 10;
	static final int INVALID_STATE = 20;
	
	static final int TRANSITION1 = 1;
	static final int TRANSITION2 = 10;
	
	static final int EVENT1 = 5;
	static final int EVENT2 = 10;
	static final int INVALID_EVENT = 7;
	
	@Test
	public void sanityTest() throws Exception {
		final AtomicInteger counter = new AtomicInteger();
		final StateMachineBuilder smBuilder = new StateMachineBuilder("Test");
		assertTrue(smBuilder.registerState(new ConcreteState(STATE1)));
		assertFalse(smBuilder.registerState(new ConcreteState(STATE1)));
		assertTrue(smBuilder.registerState(new ConcreteState(STATE2, new ConcreteState.BeginStateRunnable() {
			@Override
			public void run(int prevState, int transitionId) {
				assertEquals(1, prevState);
				counter.addAndGet(1);
			}
		})));
		assertTrue(smBuilder.registerState(new ConcreteState(STATE3, new ConcreteState.BeginStateRunnable() {
			@Override
			public void run(int prevState, int transitionId) {
				counter.decrementAndGet();
			}
		})));

		assertTrue(smBuilder.linkStates(STATE1, TRANSITION1, STATE2));
		assertFalse(smBuilder.linkStates(INVALID_STATE, TRANSITION1, STATE1));
		assertFalse(smBuilder.linkStates(STATE1, TRANSITION1, INVALID_STATE));
		assertTrue(smBuilder.linkStates(STATE1, TRANSITION2, STATE3));
		assertTrue(smBuilder.linkStates(STATE2, TRANSITION1, STATE3));
		assertTrue(smBuilder.linkStates(STATE3, TRANSITION1, STATE1));

		smBuilder.translateEvent(STATE1, EVENT1, new EventTranslator() {
			@Override
			public int translateToTransitionId(int eventId) {
				return TRANSITION1;
			}			
		});
		smBuilder.translateEvent(STATE1, EVENT2, new EventTranslator() {
			@Override
			public int translateToTransitionId(int eventId) {
				return TRANSITION2;
			}			
		});
		smBuilder.translateEvent(STATE2, EVENT1, new EventTranslator() {
			@Override
			public int translateToTransitionId(int eventId) {
				return TRANSITION1;
			}			
		});		
		smBuilder.translateEvent(STATE3, EVENT1, new EventTranslator() {
			@Override
			public int translateToTransitionId(int eventId) {
				return TRANSITION1;
			}			
		});
		
		final StateMachine sm = smBuilder.buildMachine();
		sm.start(STATE1);
		assertEquals(STATE1, sm.getCurrentStateId());
		sm.onEventReceived(INVALID_EVENT);
		assertEquals(STATE1, sm.getCurrentStateId());
		sm.onEventReceived(EVENT1);
		assertEquals(STATE2, sm.getCurrentStateId());
		assertEquals(1, counter.get());
		sm.onEventReceived(EVENT2);
		assertEquals(STATE2, sm.getCurrentStateId());
		assertEquals(1, counter.get());
		sm.onEventReceived(EVENT1);
		assertEquals(STATE3, sm.getCurrentStateId());
		sm.onEventReceived(EVENT1);
		assertEquals(STATE1, sm.getCurrentStateId());
		sm.onEventReceived(EVENT2);
		assertEquals(STATE3, sm.getCurrentStateId());
	}
	
	@Ignore
	@Test
	public void wthTest() throws Exception {
		for (int i = 0; i < 10; i++) {
			performanceTest();
		}
	}
	
	public void performanceTest() throws Exception {		
		final int numMachines = 200;
		final int warmups = 2000;
		final int numRuns = 1000000;
		final int numStates = 5;
		final int numTransitions = 10;
		final Random rn = new Random();
		final StateMachineEventBus bus = new StateMachineEventBus.DynamicStateMachineEventBus();
		final EventTranslator translator = new EventTranslator() {
			@Override
			public int translateToTransitionId(int eventId) {
				return eventId;
			}			
		};
		for (int n = 0; n < numMachines; n++) {			
			final StateMachineBuilder smBuilder = new StateMachineBuilder("Test");
			for (int i = 0; i < numStates; i++) {
				smBuilder.registerState(new ConcreteState(i));
			}
			for (int i = 0; i < numStates; i++) {
				for (int j = 0; j < numTransitions; j++) {
					smBuilder.linkStates(i, j, rn.nextInt(numStates));
					//smBuilder.translateEvent(i, j, (int eventId) -> eventId);
					smBuilder.translateEvent(i, j, translator);
				}
			}
			final StateMachine sm = smBuilder.buildMachine();
			sm.start(0);
			bus.subscribeStateMachine(n, sm);
		}
		for (int n = 0; n < warmups; n++) {
			bus.fireEvent(rn.nextInt(numTransitions));
		}
		long minTime = 0;
		long maxTime = 0;
		long totalTime = 0;
		for (int n = 0; n < numRuns; n++) {
			final long startTime = System.nanoTime();
			bus.fireEvent(rn.nextInt(numTransitions));
			final long endTime = System.nanoTime();
			final long delay = endTime - startTime;
			//System.out.println(delay);
			if (n == 0) {
				minTime = maxTime = totalTime = delay;
			}
			else {
				minTime = Math.min(minTime, delay);
				maxTime = Math.max(maxTime, delay);
				totalTime += delay;
			}
		}
		System.out.println("***** Stats *****");
		System.out.println(minTime);
		System.out.println(maxTime);
		System.out.println(totalTime / numRuns);
	}
	
}
