package com.lunar.core;

import com.lunar.message.sender.TimerEventSender;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * 
 * @author wongca
 *
 */
public class TimeoutHandlerTimerTask implements TimerTask {
	private final TimeoutHandler handler;
	private final TimerEventSender timerEventSender;
	private final String taskDesc;
	
	public static TimeoutHandlerTimerTask of(TimerEventSender timerEventSender, TimeoutHandler handler, String taskDesc){
		return new TimeoutHandlerTimerTask(timerEventSender, handler, taskDesc);
	}
	
	TimeoutHandlerTimerTask(TimerEventSender timerEventSender, TimeoutHandler handler, String taskDesc){
		this.timerEventSender = timerEventSender;
		this.handler = handler;
		this.taskDesc = taskDesc;
	}
	
	@Override
	public void run(Timeout timeout) throws Exception {
		try {
			handler.handleTimeout(timerEventSender);
		}
		catch (Throwable ex){
			handler.handleTimeoutThrowable(ex);
		}
	}

	@Override
	public String toString() {
		return taskDesc;
	}
}
