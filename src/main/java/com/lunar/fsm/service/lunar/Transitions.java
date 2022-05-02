package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.States.ACTIVE;
import static com.lunar.fsm.service.lunar.States.IDLE;
import static com.lunar.fsm.service.lunar.States.WARMUP;
import static com.lunar.fsm.service.lunar.States.RESET;
import static com.lunar.fsm.service.lunar.States.READY;
import static com.lunar.fsm.service.lunar.States.RECOVERY;
import static com.lunar.fsm.service.lunar.States.STOP;
import static com.lunar.fsm.service.lunar.States.STOPPED;
import static com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES;
import static com.lunar.fsm.service.lunar.States.WAITING_FOR_WARMUP_SERVICES;

class Transitions {
	static Transition NO_TRANSITION = new Transition(){
		@Override
		public final void proceed(LunarService service) {}
	};

	// To start, enter IDLE with THREAD_START event
	// IDLE - enter with either START or RECOVER
	static Transition in_IDLE_receive_THREAD_START_WARMUP = Transition.of(IDLE, StateTransitionEvent.THREAD_START_WARMUP, WAITING_FOR_WARMUP_SERVICES);
	static Transition in_IDLE_receive_THREAD_START_NO_WARMUP = Transition.of(IDLE, StateTransitionEvent.THREAD_START_NO_WARMUP, WAITING_FOR_SERVICES);

	// WAITING_FOR_WARMUP_SERVICES
	static Transition in_WAITING_FOR_WARMUP_SERVICES_receive_WARMUP = Transition.of(WAITING_FOR_WARMUP_SERVICES, StateTransitionEvent.WARMUP, WARMUP);
	
	// WARMUP
	static Transition in_WARMUP_receive_WARMUP_COMPLETE = Transition.of(WARMUP, StateTransitionEvent.WARMUP_COMPLETE, RESET);
	static Transition in_WARMUP_receive_WAIT = Transition.of(WARMUP, StateTransitionEvent.WAIT, WAITING_FOR_WARMUP_SERVICES);
	
	// RESET
	static Transition in_RESET_receive_FAIL = Transition.of(RESET, StateTransitionEvent.FAIL, STOP);
	static Transition in_RESET_receive_THREAD_STOP = Transition.of(RESET, StateTransitionEvent.THREAD_STOP, STOPPED);
	static Transition in_RESET_receive_RESET_COMPLETE = Transition.of(RESET, StateTransitionEvent.RESET_COMPLETE, WAITING_FOR_SERVICES);
	
	// WAITING_FOR_SERVICES
	static Transition in_WAITING_FOR_SERVICES_receive_RESET_COMPLETE = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.RESET_COMPLETE, READY);
	static Transition in_WAITING_FOR_SERVICES_receive_READY = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.READY, READY);
	static Transition in_WAITING_FOR_SERVICES_receive_ACTIVATE = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.ACTIVATE, ACTIVE);
	static Transition in_WAITING_FOR_SERVICES_receive_FAIL = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.FAIL, STOP);
	static Transition in_WAITING_FOR_SERVICES_receive_TIMEOUT = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.TIMEOUT, STOP);
	static Transition in_WAITING_FOR_SERVICES_receive_THREAD_STOP = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.THREAD_STOP, STOPPED);
	static Transition in_WAITING_FOR_SERVICES_receive_STOP = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.STOP, STOP);
	static Transition in_WAITING_FOR_SERVICES_receive_WAIT	= NO_TRANSITION;

	// READY
	static Transition in_READY_receive_FAIL = Transition.of(READY, StateTransitionEvent.FAIL, STOP);
	static Transition in_READY_receive_WAIT = Transition.of(READY, StateTransitionEvent.WAIT, WAITING_FOR_SERVICES);
	static Transition in_READY_receive_THREAD_STOP = Transition.of(READY, StateTransitionEvent.THREAD_STOP, STOPPED);
	static Transition in_READY_receive_RECOVER = Transition.of(READY, StateTransitionEvent.RECOVER, RECOVERY);
	static Transition in_READY_receive_STOP = Transition.of(READY, StateTransitionEvent.STOP, STOP);
	static Transition in_READY_receive_ACTIVATE = Transition.of(READY, StateTransitionEvent.ACTIVATE, ACTIVE);
	
	// RECOVERY
	static Transition in_RECOVERY_receive_FAIL = Transition.of(RECOVERY, StateTransitionEvent.FAIL, STOP);
	static Transition in_RECOVERY_receive_WAIT = Transition.of(RECOVERY, StateTransitionEvent.WAIT, WAITING_FOR_SERVICES);
	static Transition in_RECOVERY_receive_THREAD_STOP = Transition.of(RECOVERY, StateTransitionEvent.FAIL, STOP);
	static Transition in_RECOVERY_receive_STOP = Transition.of(RECOVERY, StateTransitionEvent.STOP, STOP);
	static Transition in_RECOVERY_receive_ACTIVATE = Transition.of(RECOVERY, StateTransitionEvent.ACTIVATE, ACTIVE);

	// ACTIVE
	static Transition in_ACTIVE_receive_FAIL = Transition.of(ACTIVE, StateTransitionEvent.FAIL, STOP);
	static Transition in_ACTIVE_receive_WAIT = Transition.of(ACTIVE, StateTransitionEvent.WAIT, WAITING_FOR_SERVICES);
	static Transition in_ACTIVE_receive_THREAD_STOP = Transition.of(ACTIVE, StateTransitionEvent.THREAD_STOP, STOPPED);
	static Transition in_ACTIVE_receive_READY = Transition.of(ACTIVE, StateTransitionEvent.READY, READY);
	static Transition in_ACTIVE_receive_STOP = Transition.of(ACTIVE, StateTransitionEvent.STOP, STOP);
	static Transition in_ACTIVE_receive_RESET = Transition.of(ACTIVE, StateTransitionEvent.RESET, RESET);

	// STOP
	static Transition in_STOP_receive_THREAD_STOP = Transition.of(STOP, StateTransitionEvent.THREAD_STOP, STOPPED);

	// STOPPED - no transition away from STOPPED
	
	static Transition in_ANY_STATE_receive_FAIL = Transition.of(null, StateTransitionEvent.FAIL, STOP);
	static Transition in_ANY_STATE_caught_nonrecoverable_exception = Transition.of(null, StateTransitionEvent.FAIL, STOP);
}
