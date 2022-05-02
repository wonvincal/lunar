package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_IDLE_receive_THREAD_START_WARMUP;
import static com.lunar.fsm.service.lunar.Transitions.in_IDLE_receive_THREAD_START_NO_WARMUP;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Idle State is when the disruptor thread is not alive.
 * @author Calvin
 *
 */
final class IdleState extends State {
	static final Logger LOG = LogManager.getLogger(IdleState.class);
	
	@Override
	final Transition enter(LunarService service, StateTransitionEvent event) {
		// the thread entering this method may not be the disruptor thread,
		// so nothing should be done when entering IdleState 
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceIdleExit();
		return this;
	}
	
	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_START_WARMUP){
			service.serviceIdleStart();
			return in_IDLE_receive_THREAD_START_WARMUP;
		}
		else if (event == StateTransitionEvent.THREAD_START_NO_WARMUP){
			service.serviceIdleStart();
			return in_IDLE_receive_THREAD_START_NO_WARMUP;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
	
	@Override
	public String toString() {
		return "IdleState";
	}
}
