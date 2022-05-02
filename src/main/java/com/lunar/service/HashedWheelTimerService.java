package com.lunar.service;

import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.RealSystemClock;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.core.TimerService;
import com.lunar.message.sender.TimerEventSender;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/***
 * Timer service that runs on a separate thread.  There should only be one instance per actor system.  
 * This should be owned by the Admin service and all children of Admin service should use this object 
 * to schedule their tasks.  
 * 
 * Originally, I wanted to use actor system scheduler.  However, using our own HashedWheelTimer gives us
 * more flexibility to minimize new object creations.
 * 
 * @author Calvin
 *
 */
public final class HashedWheelTimerService implements TimerService {
	static final Logger LOG = LogManager.getLogger(HashedWheelTimerService.class);
	private final HashedWheelTimer timer;
	private final AtomicBoolean started = new AtomicBoolean(false);
	/**
	 * Use this sender to send out timer event 
	 */
	private final TimerEventSender timerEventSender;
	
	public HashedWheelTimerService(ThreadFactory threadFactory, Duration tickDuration, int ticksPerWheel, TimerEventSender timerEventSender){
		timer = new HashedWheelTimer(threadFactory, tickDuration.toMillis(), TimeUnit.MILLISECONDS, ticksPerWheel);
		this.timerEventSender = timerEventSender;
	}

	/* (non-Javadoc)
	 * @see com.lunar.service.TimerService#nanoTime()
	 */
	@Override
	public long nanoTime(){
		return System.nanoTime();
	}
	
	/**
	 * Schedule a handler to be called after a specified delay.
	 * Please note that a new TimerTask is created in each call.  If you are very concern
	 * with creating new object, use the other version {@link newTimeout(TimerTask task, int delay, TimeUnit unit)}
	 * 
	 * Thread safety: Yes
	 * 
	 * @param handler
	 * @param delay
	 * @param unit
	 * @return
	 */
	public final Timeout newTimeout(TimeoutHandler handler, long delay, TimeUnit unit){
		TimerTask task = new TimerTask() {
			@Override
			public void run(Timeout timeout) throws Exception {
				try {
					handler.handleTimeout(timerEventSender);
				}
				catch (Throwable ex){
					handler.handleTimeoutThrowable(ex);
					LOG.warn("caught exception when handling timeout", ex);
				}
			}
		};
		return newTimeout(task, delay, unit);
	}
	
	/**
	 * A method that accepts TimerTask.  One possible usage is to use a same TimerTask for similar
	 * Task.  However, we are still creating a new Timeout object.
	 * 
	 * Caution: Multiple Timeout objects may be associated with the same TimerTask.  Please use this carefully.
	 * 
	 * Thread safety: Yes
	 * 
	 * @param task
	 * @param delay
	 * @param unit
	 * @return
	 */
	public final Timeout newTimeout(TimerTask task, long delay, TimeUnit unit){
		if (!started.get()){
			throw new IllegalStateException("timer service hasn't been started or has already been stopped; cannot schedule task");
		}
		return timer.newTimeout(task, delay, unit);
	}

	@Override
	public TimeoutHandlerTimerTask createTimerTask(TimeoutHandler handler, String taskDesc) {
		return TimeoutHandlerTimerTask.of(timerEventSender, handler, taskDesc);
	}
	
	@Override
	public void start(){
		if (!started.compareAndSet(false, true)){
			LOG.warn("already started");
			return;
		}
		timer.start();
		LOG.info("started");
	}
	
	@Override
	public void stop(){
		if (!started.compareAndSet(true, false)){
			LOG.warn("already stopped");
			return;
		}
		for (Timeout timeout : timer.stop()){
			LOG.info("cancelled task: {}", timeout.task().toString());
		}
		LOG.info("stopped");
	}
	
	@Override
	public boolean isActive(){
		return started.get();
	}

	@Override
	public TimerEventSender timerEventSender() {
		return timerEventSender;
	}

	@Override
	public long toNanoOfDay() {
	    return (RealSystemClock.NANO_OF_DAY_OFFSET + System.nanoTime());
	}
}
