package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class StoppedState extends State {
	static final Logger LOG = LogManager.getLogger(StoppedState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_STOP){
			service.serviceStoppedEnter();
			return NO_TRANSITION;
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceStoppedExit();
		return this;
	}
	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		return NO_TRANSITION;
	}
}
