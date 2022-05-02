package com.lunar.fsm.cutloss.mmbidslide;

import static com.lunar.fsm.cutloss.mmbidslide.MmBidSlideStates.*;

import com.lunar.fsm.cutloss.StateTransitionEvent;
import com.lunar.fsm.cutloss.TradeContext;
import com.lunar.fsm.cutloss.Transition;

class Transitions {
	static Transition NO_TRANSITION = new Transition(){
		@Override
		public final void proceed(TradeContext context) {}
	};

	// To start, enter IDLE with THREAD_START event
	// IDLE - enter with either START or RECOVER
	static Transition in_READY_receive_BUY_DETECTED = Transition.of(READY, StateTransitionEvent.BUY_DETECTED, HOLD_POSITION);

	// HOLD_POSITION
	static Transition in_HOLD_POSITION_receive_UNEXPLAINABLE = Transition.of(HOLD_POSITION, StateTransitionEvent.UNEXPLAINABLE_DOWNWARD_MOVE, UNEXPLAINABLE_MOVE_DETECTED);
	static Transition in_HOLD_POSITION_receive_SELL_DETECTED = Transition.of(HOLD_POSITION, StateTransitionEvent.SELL_DETECTED, WAIT);

	// UNEXPLAINABLE_DOWNWARD_MOVE
	static Transition in_UNEXPLAINABLE_DOWNWARD_MOVE_receive_SELL_DETECTED = Transition.of(UNEXPLAINABLE_MOVE_DETECTED, StateTransitionEvent.SELL_DETECTED, WAIT);
	static Transition in_UNEXPLAINABLE_DOWNWARD_MOVE_receive_EXPLAINABLE = Transition.of(UNEXPLAINABLE_MOVE_DETECTED, StateTransitionEvent.EXPLAINABLE, HOLD_POSITION);
	
	// STOP
	static Transition in_STOP_receive_WAIT = Transition.of(STOP, StateTransitionEvent.WAIT, WAIT);

	// WAIT
	static Transition in_WAIT_receive_READY = Transition.of(WAIT, StateTransitionEvent.READY, READY);

	static Transition in_ANY_STATE_receive_FAIL = Transition.of(null, StateTransitionEvent.FAIL, STOP);
}
