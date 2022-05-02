package com.lunar.core;

public enum WaitStrategy {
	BUSY_SPIN,
	BLOCKING_WAIT,
	YIELDING_WAIT,
	LITE_BLOCKING
}
