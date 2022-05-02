package com.lunar.fsm.service.lunar;


public class StateTransitionEventAccessor {
	private LunarService service;
	public StateTransitionEventAccessor(LunarService service){
		this.service = service;
	}
	public static StateTransitionEventAccessor of(LunarService service){
		return new StateTransitionEventAccessor(service);
	}
	public StateTransitionEvent event(){
		return service.stateEvent();
	}
}
