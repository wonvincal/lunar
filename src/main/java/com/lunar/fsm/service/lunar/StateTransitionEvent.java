package com.lunar.fsm.service.lunar;

public enum StateTransitionEvent {
	START,
	/**
	 * before first event
	 */
	THREAD_START_WARMUP,
	THREAD_START_NO_WARMUP,
	/**
	 * warmup related states
	 */
	WARMUP,
	WARMUP_COMPLETE,
	/**
	 * immediately after last event
	 */
	THREAD_STOP,
	/**
	 * waiting for required services to come up
	 */
	WAIT,
	/**
	 * move to active state
	 */
	ACTIVATE,
	FAIL,
	TIMEOUT,
	/**
	 * ready to accept command
	 */
	READY,
	
	/**
	 * recovery state
	 */
	RECOVER,
	
	/**
	 * stop the service from command
	 */
	STOP,
	
	/**
	 * Reset internal state
	 */
	RESET,
	RESET_COMPLETE,
	NULL
}
