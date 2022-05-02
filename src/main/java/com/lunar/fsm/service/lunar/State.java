package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;

public abstract class State {
	Transition enter(LunarService service, StateTransitionEvent event){
		return in_ANY_STATE_receive_FAIL;
	}
	State exit(LunarService service, StateTransitionEvent event){
		return this;
	}
	Transition onEvent(LunarService service, StateTransitionEvent event){
		return in_ANY_STATE_receive_FAIL;
	}
	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}
