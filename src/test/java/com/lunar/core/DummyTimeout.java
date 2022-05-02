package com.lunar.core;

import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

public class DummyTimeout implements Timeout {
	private final TimerTask task;
	private DummyTimeout(TimerTask task){
		this.task = task;
	}
	
	public static DummyTimeout withDummyTimerTask(){
		return new DummyTimeout(new DummyTimerTask());		
	}
	
	public static DummyTimeout of(TimerTask task){
		return new DummyTimeout(task);
	}
	
	@Override
	public Timer timer() {
		throw new UnsupportedOperationException();
	}

	@Override
	public TimerTask task() {
		return task;
	}

	@Override
	public boolean isExpired() {
		return true;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean cancel() {
		return true;
	}
}
