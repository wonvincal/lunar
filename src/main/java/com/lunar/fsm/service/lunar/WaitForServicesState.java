package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_ACTIVATE;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_READY;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_RESET_COMPLETE;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_THREAD_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_TIMEOUT;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_SERVICES_receive_WAIT;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class WaitForServicesState extends State {
	static final Logger LOG = LogManager.getLogger(WaitForServicesState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.WAIT ||
				event == StateTransitionEvent.THREAD_START_NO_WARMUP ||
				event == StateTransitionEvent.WARMUP_COMPLETE ||
				event == StateTransitionEvent.RESET_COMPLETE){
			return onEvent(service, service.serviceWaitingForServicesEnter());
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceWaitingForServicesExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		if (event == StateTransitionEvent.RESET_COMPLETE){
			return in_WAITING_FOR_SERVICES_receive_RESET_COMPLETE;
		}
		if (event == StateTransitionEvent.ACTIVATE){
			return in_WAITING_FOR_SERVICES_receive_ACTIVATE;
		}
		else if (event == StateTransitionEvent.READY){
			return in_WAITING_FOR_SERVICES_receive_READY;
		}
		else if (event == StateTransitionEvent.FAIL){
			return in_WAITING_FOR_SERVICES_receive_FAIL;
		}
		else if (event == StateTransitionEvent.STOP){
			return in_WAITING_FOR_SERVICES_receive_STOP;
		}
		else if (event == StateTransitionEvent.TIMEOUT){
			return in_WAITING_FOR_SERVICES_receive_TIMEOUT;
		}
		else if (event == StateTransitionEvent.THREAD_STOP){
			return in_WAITING_FOR_SERVICES_receive_THREAD_STOP;
		}
		else if (event == StateTransitionEvent.WAIT){
			return in_WAITING_FOR_SERVICES_receive_WAIT;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}	
}
