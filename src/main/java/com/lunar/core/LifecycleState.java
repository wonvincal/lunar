package com.lunar.core;

public enum LifecycleState {
	/**
	 * Initialize
	 * Next state: RECOVERY, WARMUP, ACTIVE 
	 */
	INIT,

	/**
	 * Switch into RECOVERY mode
	 * Next state: WARMUP, ACTIVE, IDLE 
	 */
	RECOVERY,

	/**
	 * Switch into WARMUP mode
	 * Next state: ACTIVE, IDLE
	 */
	WARMUP,

	/**
	 * Switch into ACTIVE mode
	 * Next state: IDLE
	 */
	ACTIVE,

	/**
	 * A transition state between ACTIVE, RECOVERY, STOPPED
	 * A state for resetting internal states of the object
	 */
	RESET,

	/**
	 * Stopped
	 * Next state: RECOVERY, ACTIVE
	 */
	STOPPED
}
