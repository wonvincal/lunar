package performance.baseline2;

public enum StateTransitionEvent {
	START,
	THREAD_START, // before first event
	THREAD_STOP, // immediately after last event
	WAIT,
	ACTIVATE,
	FAIL,
	TIMEOUT,
	NULL
}
