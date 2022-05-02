package com.lunar.core;

import java.util.concurrent.TimeUnit;

import com.lunar.message.sender.TimerEventSender;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

public interface TimerService {

	/**
	 * This method should be used for any logic that is time dependent 
	 * @return
	 */
	public abstract long nanoTime();

	public abstract long toNanoOfDay();
	
	public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

	/**
	 * Side effect: A new TimerTask is created
	 * @param handler
	 * @param delay
	 * @param unit
	 * @return
	 */
	public Timeout newTimeout(TimeoutHandler handler, long delay, TimeUnit unit);
	
	public TimerEventSender timerEventSender();

	public void start();
	
	public void stop();
	
	public boolean isActive();
	
	default TimeoutHandlerTimerTask createTimerTask(TimeoutHandler handler, String taskDesc){
		throw new UnsupportedOperationException();
	}
}