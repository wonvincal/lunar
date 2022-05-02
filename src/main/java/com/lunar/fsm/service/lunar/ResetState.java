package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_RESET_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_RESET_receive_RESET_COMPLETE;
import static com.lunar.fsm.service.lunar.Transitions.in_RESET_receive_THREAD_STOP;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class ResetState extends State{
	static final Logger LOG = LogManager.getLogger(ResetState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.WARMUP_COMPLETE ||
				event == StateTransitionEvent.RESET){
			return onEvent(service, service.serviceResetEnter());
		} 
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceResetExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		else if (event == StateTransitionEvent.RESET_COMPLETE){
			return in_RESET_receive_RESET_COMPLETE;
		}
		else if (event == StateTransitionEvent.FAIL){
			return in_RESET_receive_FAIL;
		}
		else if (event == StateTransitionEvent.THREAD_STOP){
			return in_RESET_receive_THREAD_STOP;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
}
