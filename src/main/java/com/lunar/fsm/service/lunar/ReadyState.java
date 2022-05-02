package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_READY_receive_RECOVER;
import static com.lunar.fsm.service.lunar.Transitions.in_READY_receive_ACTIVATE;
import static com.lunar.fsm.service.lunar.Transitions.in_READY_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_READY_receive_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_READY_receive_THREAD_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_READY_receive_WAIT;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class ReadyState extends State{
	static final Logger LOG = LogManager.getLogger(ReadyState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.READY){
			return onEvent(service, service.serviceReadyEnter());
		} 
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceReadyExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
        else if (event == StateTransitionEvent.ACTIVATE){
            return in_READY_receive_ACTIVATE;
        }
		else if (event == StateTransitionEvent.RECOVER){
			return in_READY_receive_RECOVER;
		}
		else if (event == StateTransitionEvent.FAIL){
			return in_READY_receive_FAIL;
		}
		else if (event == StateTransitionEvent.WAIT){
			return in_READY_receive_WAIT;
		}
		else if (event == StateTransitionEvent.THREAD_STOP){
			return in_READY_receive_THREAD_STOP;
		}
		else if (event == StateTransitionEvent.STOP){
			return in_READY_receive_STOP;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
}
