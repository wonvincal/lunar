package com.lunar.service;

import com.lunar.fsm.service.lunar.StateTransitionEvent;

public interface ServiceLifecycleAware {
	// -- IDLE --
	StateTransitionEvent idleStart();
	default StateTransitionEvent idleRecover(){
		return StateTransitionEvent.NULL;
	}
	default void idleExit(){}

	// -- WAITING FOR WARMUP SERVICES -- 
	default StateTransitionEvent waitingForWarmupServicesEnter(){
		return StateTransitionEvent.NULL;
	}
	default void waitingForWarmupServicesExit(){}

	// -- WARMUP -- 
	default StateTransitionEvent warmupEnter(){
		return StateTransitionEvent.WARMUP_COMPLETE;
	}
	default void warmupExit(){}
	
	// -- RESET -- 
	default StateTransitionEvent resetEnter(){
		return StateTransitionEvent.RESET_COMPLETE;
	}
	default void resetExit(){}

	// -- WAITING_FOR_SERVICES -- 
	default StateTransitionEvent waitingForServicesEnter(){
		return StateTransitionEvent.NULL;
	}
	default void waitingForServicesExit(){}
	
	// -- READY --
	default StateTransitionEvent readyEnter(){
		return StateTransitionEvent.NULL;
	}
	default void readyExit(){}

	// -- RECOVERY --
    default StateTransitionEvent recoveryEnter(){
        return StateTransitionEvent.ACTIVATE;
    }
    default void recoveryExit(){}
    
	// -- ACTIVE --
	StateTransitionEvent activeEnter();
	void activeExit();

	// -- STOP --
	default StateTransitionEvent stopEnter(){
		return StateTransitionEvent.NULL;
	}
	default void stopExit(){}

	// -- STOPPED --
	default StateTransitionEvent stoppedEnter(){
		return StateTransitionEvent .NULL;
	}
	default void stoppedExit(){}
	
	default boolean isStopped(){
		return true;
	}
}
