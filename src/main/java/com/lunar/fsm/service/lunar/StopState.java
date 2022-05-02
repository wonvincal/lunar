package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_STOP_receive_THREAD_STOP;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Stop.  Derived class of {@link LunarService} should create another state
 * if it needs to do some asynchronous cleanup tasks before this state.
 * @author Calvin
 *
 */
final class StopState extends State {
	static final Logger LOG = LogManager.getLogger(StopState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.FAIL || event == StateTransitionEvent.TIMEOUT || event == StateTransitionEvent.STOP){
			return onEvent(service, service.serviceStopEnter());
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceStopExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		if (event == StateTransitionEvent.THREAD_STOP){
			return in_STOP_receive_THREAD_STOP;
		}
		return in_ANY_STATE_receive_FAIL;
	}
}
