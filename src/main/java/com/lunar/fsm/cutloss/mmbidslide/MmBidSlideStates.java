package com.lunar.fsm.cutloss.mmbidslide;

import com.lunar.fsm.cutloss.State;

public class MmBidSlideStates {
	public static State READY = new ReadyState();
	public static State WAIT = new WaitState();
	public static State STOP = new StopState();
	public static State HOLD_POSITION = new HoldPositionState();
	public static State UNEXPLAINABLE_MOVE_DETECTED = new UnexplainableMoveDetectedState();
}
