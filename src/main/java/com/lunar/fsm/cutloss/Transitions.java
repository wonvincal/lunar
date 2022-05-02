package com.lunar.fsm.cutloss;

import static com.lunar.fsm.cutloss.States.WAIT;
import static com.lunar.fsm.cutloss.States.READY;
import static com.lunar.fsm.cutloss.States.STOP;
import static com.lunar.fsm.cutloss.States.UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS;
import static com.lunar.fsm.cutloss.States.UND_TIGHT_SEC_NO_MM_CHANGE;
import static com.lunar.fsm.cutloss.States.UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS;
import static com.lunar.fsm.cutloss.States.UND_WIDE_SEC_NO_MM_CHANGE;

class Transitions {
	static Transition NO_TRANSITION = new Transition(){
		@Override
		public final void proceed(TradeContext context) {}
	};

	// To start, enter IDLE with THREAD_START event
	// IDLE - enter with either START or RECOVER
	static Transition in_READY_receive_START_UNDERLYING_TIGHT = Transition.of(READY, StateTransitionEvent.START_UND_TIGHT, UND_TIGHT_SEC_NO_MM_CHANGE);
	static Transition in_READY_receive_START_UNDERLYING_WIDE = Transition.of(READY, StateTransitionEvent.START_UND_WIDE, UND_WIDE_SEC_NO_MM_CHANGE);
	
	// UND_WIDE_SEC_NO_MM_CHANGE
	static Transition in_UND_WIDE_SEC_NO_MM_CHANGE_receive_SELL_DETECTED = Transition.of(UND_WIDE_SEC_NO_MM_CHANGE, StateTransitionEvent.SELL_DETECTED, WAIT);
	static Transition in_UND_WIDE_SEC_NO_MM_CHANGE_receive_DERIV_MM_CHANGE = Transition.of(UND_WIDE_SEC_NO_MM_CHANGE, StateTransitionEvent.DERIV_MM_CHANGE, UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS);
	static Transition in_UND_WIDE_SEC_NO_MM_CHANGE_receive_UND_TIGHT = Transition.of(UND_WIDE_SEC_NO_MM_CHANGE, StateTransitionEvent.UND_TIGHT, UND_TIGHT_SEC_NO_MM_CHANGE);
	
	// UND_TIGHT_SEC_NO_MM_CHANGE
	static Transition in_UND_TIGHT_SEC_NO_MM_CHANGE_receive_SELL_DETECTED = Transition.of(UND_TIGHT_SEC_NO_MM_CHANGE, StateTransitionEvent.SELL_DETECTED,  WAIT);
	static Transition in_UND_TIGHT_SEC_NO_MM_CHANGE_receive_DERIV_MM_CHANGE = Transition.of(UND_TIGHT_SEC_NO_MM_CHANGE, StateTransitionEvent.DERIV_MM_CHANGE, UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS);
	static Transition in_UND_TIGHT_SEC_NO_MM_CHANGE_receive_UND_WIDE = Transition.of(UND_TIGHT_SEC_NO_MM_CHANGE, StateTransitionEvent.UND_WIDE, UND_WIDE_SEC_NO_MM_CHANGE);

	// UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS 
	static Transition in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_DERIV_MM_CHANGE_COMPLETE = Transition.of(UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.DERIV_MM_CHANGE_COMPLETE, UND_WIDE_SEC_NO_MM_CHANGE);
	static Transition in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_SELL_DETECTED = Transition.of(UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.SELL_DETECTED,  WAIT);
	static Transition in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_UND_WIDE = Transition.of(UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.UND_TIGHT,  UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS);
	static Transition in_UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS_receive_WAIT = Transition.of(UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.WAIT,  WAIT);

	// UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS 
	static Transition in_UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS_receive_DERIV_MM_CHANGE_COMPLETE = Transition.of(UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.DERIV_MM_CHANGE_COMPLETE, UND_TIGHT_SEC_NO_MM_CHANGE);
	static Transition in_UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS_receive_SELL_DETECTED = Transition.of(UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.SELL_DETECTED,  WAIT);
	static Transition in_UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS_receive_UND_WIDE = Transition.of(UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.UND_WIDE,  UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS);
	static Transition in_UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS_receive_WAIT = Transition.of(UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS, StateTransitionEvent.WAIT,  WAIT);

	//	// STOP
	static Transition in_STOP_receive_WAIT = Transition.of(STOP, StateTransitionEvent.WAIT, WAIT);

	// WAIT
	static Transition in_WAIT_receive_READY = Transition.of(WAIT, StateTransitionEvent.READY, READY);
	
	static Transition in_ANY_STATE_receive_FAIL = Transition.of(null, StateTransitionEvent.FAIL, STOP);
	static Transition in_ANY_STATE_caught_nonrecoverable_exception = Transition.of(null, StateTransitionEvent.FAIL, STOP);
}
