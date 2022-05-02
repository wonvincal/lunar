package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_WAITING_FOR_WARMUP_SERVICES_receive_WARMUP;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class WaitForWarmupServiceState extends State {
	static final Logger LOG = LogManager.getLogger(WaitForWarmupServiceState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.THREAD_START_WARMUP){
			return onEvent(service, service.serviceWaitingForWarmupServicesEnter());
		}
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}

	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceWaitingForWarmupServicesExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		if (event == StateTransitionEvent.WARMUP){
			return in_WAITING_FOR_WARMUP_SERVICES_receive_WARMUP;
		}
		LOG.error("unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
}
