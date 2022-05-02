package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ACTIVE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_ACTIVE_receive_READY;
import static com.lunar.fsm.service.lunar.Transitions.in_ACTIVE_receive_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_ACTIVE_receive_THREAD_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_ACTIVE_receive_WAIT;
import static com.lunar.fsm.service.lunar.Transitions.in_ACTIVE_receive_RESET;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class ActiveState extends State{
	static final Logger LOG = LogManager.getLogger(ActiveState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.ACTIVATE){
			return onEvent(service, service.serviceActiveEnter());
		} 
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceActiveExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		else if (event == StateTransitionEvent.FAIL){
			return in_ACTIVE_receive_FAIL;
		}
		else if (event == StateTransitionEvent.WAIT){
			return in_ACTIVE_receive_WAIT;
		}
		else if (event == StateTransitionEvent.THREAD_STOP){
			return in_ACTIVE_receive_THREAD_STOP;
		}
		else if (event == StateTransitionEvent.RESET){
			return in_ACTIVE_receive_RESET;
		}
		else if (event == StateTransitionEvent.READY){
			return in_ACTIVE_receive_READY;
		}
		else if (event == StateTransitionEvent.STOP){
			return in_ACTIVE_receive_STOP;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
}
