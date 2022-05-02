package com.lunar.fsm.cutloss;

public class States {
	public static State READY = new ReadyState();
	public static State WAIT = new WaitState();
	public static State UND_TIGHT_SEC_MM_CHANGE_IN_PROGRESS = new UndTightSecMmChangeInProgressState();
	public static State UND_TIGHT_SEC_NO_MM_CHANGE = new UndTightSecNoMmChangeState();
	public static State UND_WIDE_SEC_MM_CHANGE_IN_PROGRESS = new UndWideSecMmChangeInProgressState();
	public static State UND_WIDE_SEC_NO_MM_CHANGE = new UndWideSecNoMmChangeState();
	public static State STOP = new StopState();
}
