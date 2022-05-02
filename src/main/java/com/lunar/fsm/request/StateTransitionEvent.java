package com.lunar.fsm.request;

public enum StateTransitionEvent {
	SEND,
	RETRY,
	FAIL,
	COMPLETE,
	RECEIVE_RESPONSE,
	TIMEOUT,
	NULL
}