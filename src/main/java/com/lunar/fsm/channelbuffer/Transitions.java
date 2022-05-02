package com.lunar.fsm.channelbuffer;

import static com.lunar.fsm.channelbuffer.States.BUFFERING;
import static com.lunar.fsm.channelbuffer.States.INITIALIZATION;
import static com.lunar.fsm.channelbuffer.States.PASS_THRU;
import static com.lunar.fsm.channelbuffer.States.STOP;

class Transitions {
	static Transition NO_TRANSITION = new Transition(){
		@Override
		public final void proceed(ChannelBufferContext context) {}
	};
	static Transition in_INIT_receive_START_SEQ_DETECTED = Transition.of(INITIALIZATION, StateTransitionEvent.START_SEQ_DETECTED, PASS_THRU);
	static Transition in_PASS_THRU_receive_GAP_DETECTED = Transition.of(PASS_THRU, StateTransitionEvent.GAP_DETECTED, BUFFERING);
	static Transition in_PASS_THRU_receive_MESSAGE_NOT_RECEIVED = Transition.of(PASS_THRU, StateTransitionEvent.MESSAGE_NOT_RECEIVED, BUFFERING);
	static Transition in_BUFFERING_receive_RECOVERED = Transition.of(BUFFERING, StateTransitionEvent.RECOVERED, PASS_THRU);
	static Transition in_ANY_STATE_receive_FAIL = Transition.of(null, StateTransitionEvent.FAIL, STOP);
	static Transition in_ANY_STATE_caught_nonrecoverable_exception = Transition.of(null, StateTransitionEvent.FAIL, STOP);
}
