package com.lunar.fsm.channelbuffer;

public enum StateTransitionEvent {
	START_SEQ_DETECTED,
	GAP_DETECTED,
	MESSAGE_NOT_RECEIVED,
	RECOVERED,
	FAIL
}
