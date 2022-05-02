package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_WARMUP_receive_WAIT;
import static com.lunar.fsm.service.lunar.Transitions.in_WARMUP_receive_WARMUP_COMPLETE;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class WarmupState extends State{
	static final Logger LOG = LogManager.getLogger(WarmupState.class);

	@Override
	Transition enter(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.WARMUP){
			return onEvent(service, service.serviceWarmupEnter());
		} 
		LOG.error("received unexpected event {} on enter", event);
		return NO_TRANSITION;
	}
	
	@Override
	State exit(LunarService service, StateTransitionEvent event) {
		service.serviceWarmupExit();
		return this;
	}

	@Override
	Transition onEvent(LunarService service, StateTransitionEvent event) {
		if (event == StateTransitionEvent.NULL){
			return NO_TRANSITION;
		}
		else if (event == StateTransitionEvent.WARMUP_COMPLETE){
			return in_WARMUP_receive_WARMUP_COMPLETE;
		}
		else if (event == StateTransitionEvent.WAIT){
			return in_WARMUP_receive_WAIT;
		}
		LOG.error("Unexpected event {}, treat this as FAIL", event);
		return in_ANY_STATE_receive_FAIL;
	}
}