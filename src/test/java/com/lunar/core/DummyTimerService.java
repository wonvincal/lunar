package com.lunar.core;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import com.lunar.message.sender.TimerEventSender;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * Timer service with these two properties
 * 1) operations reference to a single start time
 * 2) newTimeout will advance the timer time by specified delay
 * @author Calvin
 *
 */
public class DummyTimerService implements TimerService {
	private final LocalDateTime startNow;
	private final long startTimeNs;
	private long passedNs;
	private final TimerEventSender timerEventSender;
	
	public DummyTimerService(long startTimeNs, LocalDateTime startNow, TimerEventSender timerEventSender){
		this.startTimeNs = startTimeNs;
		this.startNow = startNow;
		this.passedNs = 0;
		this.timerEventSender = timerEventSender;
	}
	
	@Override
	public long nanoTime() {
		return startTimeNs + passedNs;
	}

	@Override
	public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
		passedNs += unit.toNanos(delay);
		Timeout timeout = DummyTimeout.of(task);
		try {
			task.run(timeout);
		} 
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return timeout;
	}

	@Override
	public Timeout newTimeout(TimeoutHandler handler, long delay, TimeUnit unit) {
		TimerTask task = new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				try {
					handler.handleTimeout(timerEventSender);
				}
				catch (Exception e){
					handler.handleTimeoutThrowable(e);
				}
				
			}
		};
		return newTimeout(task, delay, unit);
	}
	
	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public TimerEventSender timerEventSender() {
		return timerEventSender;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	public long toNanoOfDay() {
		return this.startNow.plusNanos(passedNs).toLocalTime().toNanoOfDay();
	}

}
