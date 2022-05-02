package performance.baseline2;

import static performance.baseline2.States.ACTIVE;
import static performance.baseline2.States.IDLE;
import static performance.baseline2.States.STOP;
import static performance.baseline2.States.STOPPED;
import static performance.baseline2.States.WAITING_FOR_SERVICES;

public class Transitions {
	static Transition NO_TRANSITION = new Transition(){
		@Override
		public final void proceed(BaseStateService service) {}
	};

	// To start, enter IDLE with THREAD_START event
	// IDLE - enter with either START or RECOVER
	static Transition in_IDLE_receive_THREAD_START = Transition.of(IDLE, StateTransitionEvent.THREAD_START, ACTIVE);
	
	// WAITING_FOR_SERVICES
	static Transition in_WAITING_FOR_SERVICES_receive_ACTIVATE = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.ACTIVATE, ACTIVE);
	static Transition in_WAITING_FOR_SERVICES_receive_FAIL = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.FAIL, STOP);
	static Transition in_WAITING_FOR_SERVICES_receive_TIMEOUT = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.TIMEOUT, STOP);
	static Transition in_WAITING_FOR_SERVICES_receive_THREAD_STOP = Transition.of(WAITING_FOR_SERVICES, StateTransitionEvent.THREAD_STOP, STOPPED);
	static Transition in_WAITING_FOR_SERVICES_receive_WAIT	= NO_TRANSITION;
	// ACTIVE
	static Transition in_ACTIVE_receive_FAIL = Transition.of(ACTIVE, StateTransitionEvent.FAIL, STOP);
	static Transition in_ACTIVE_receive_WAIT = Transition.of(ACTIVE, StateTransitionEvent.WAIT, WAITING_FOR_SERVICES);
	static Transition in_ACTIVE_receive_THREAD_STOP = Transition.of(ACTIVE, StateTransitionEvent.THREAD_STOP, STOPPED);

	// STOP
	static Transition in_STOP_receive_THREAD_STOP = Transition.of(STOP, StateTransitionEvent.THREAD_STOP, STOPPED);

	// STOPPED - no transition away from STOPPED
	
	static Transition in_ANY_STATE_receive_FAIL = Transition.of(null, StateTransitionEvent.FAIL, STOP);
	static Transition in_ANY_STATE_caught_nonrecoverable_exception = Transition.of(null, StateTransitionEvent.FAIL, STOP);
}
