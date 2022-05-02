package com.lunar.fsm.channelbuffer;

public class States {
	public static State INITIALIZATION = new InitState(); 
	public static State PASS_THRU = new PassThruState();
	public static State BUFFERING = new BufferingState();
	public static State STOP = new StopState();
}
