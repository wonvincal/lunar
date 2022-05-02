package com.lunar.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.TreeMultimap;
import com.lunar.message.sender.TimerEventSender;

import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;


public class UserControlledTimerService implements TimerService {
	static final Logger LOG = LogManager.getLogger(UserControlledTimerService.class);

	private final LocalDateTime startNow;
	private final long startTimeNs;
	private volatile long passedNs;
	private TreeMultimap<Long, TimestampedTimeout> timeouts;
	private final TimerEventSender timerEventSender;

	public UserControlledTimerService(long startTimeNs, LocalDateTime startNow, TimerEventSender timerEventSender){
		this.startNow = startNow;
		this.startTimeNs = startTimeNs;
		this.passedNs = 0;
		this.timerEventSender = timerEventSender;
		this.timeouts = TreeMultimap.create(new Comparator<Long>() {

			@Override
			public int compare(Long o1, Long o2) {
				return (o1 < o2) ? -1 : ((o2 < o1) ? 1 : 0);
			}
		}, new Comparator<TimestampedTimeout>() {

			@Override
			public int compare(TimestampedTimeout o1, TimestampedTimeout o2) {
				return (o1.task() == o2.task()) ? 0 : 1;
			}
		});
	}
	
	public int numOutstanding(){
		return timeouts.size();
	}
	
	public TreeMultimap<Long, TimestampedTimeout> timeouts() {
		return timeouts;
	}

	@Override
	public long nanoTime() {
		return startTimeNs + passedNs;
	}

	@Override
	public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
		long expireNs = nanoTime() + unit.toNanos(delay);
		TimestampedTimeout timeout = new TimestampedTimeout(expireNs, task, false, false);
		timeouts.put(expireNs, timeout);
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
				catch (Throwable ex){
					handler.handleTimeoutThrowable(ex);
					LOG.warn("caught exception when handling timeout", ex);
				}
			}
		};
		return newTimeout(task, delay, unit);
	}

	private boolean remove(TimestampedTimeout timeout){
		boolean result = false;
		result = timeouts.remove(timeout.timestamp, timeout);
		return result;
	}
	
	public boolean advance(Duration duration){
		return advance(duration.toNanos(), TimeUnit.NANOSECONDS);
	}
	
	public UserControlledTimerService nanoOfDay(final long nanoOfDay) {
		if (this.startTimeNs != 0){
			throw new IllegalStateException("startTimeNs must be 0 in order to hard set nanoOfDay [startTimeNs:" + startTimeNs + "]");
		}
	    this.passedNs = nanoOfDay;
		long currentNs = nanoTime();
		ArrayList<Long> remove = new ArrayList<Long>(); 
		
		
		for (Long expireNs : timeouts.keys()){
			if (currentNs >= expireNs){
				remove.add(expireNs);
			}
		}
		
		for (int i = 0; i < remove.size(); i++){
			timeouts.removeAll(remove.get(i)).forEach(t -> {
				try {
					t.expire();
					LOG.debug("Task ran once [{}]", t);
				}
				catch (Exception e){
					throw new RuntimeException();
				}
			});
		}
		
		return this;
	}
	

	/**
	 * @param time
	 * @param unit
	 * @return true if any task is run
	 */
	public boolean advance(long time, TimeUnit unit){
		passedNs += unit.toNanos(time);
		long currentNs = nanoTime();
		ArrayList<Long> remove = new ArrayList<Long>(); 
		
		for (Long expireNs : timeouts.keys()){
			if (currentNs >= expireNs){
				remove.add(expireNs);
			}
		}
		
		for (int i = 0; i < remove.size(); i++){
			timeouts.removeAll(remove.get(i)).forEach(t -> {
				try {
					t.expire();
					LOG.debug("Task ran once [{}]", t);
				}
				catch (Exception e){
					throw new RuntimeException();
				}
			});
		}
		return !remove.isEmpty();
	}
	
	class TimestampedTimeout implements Timeout {
		private final long timestamp;
		private final TimerTask task;
		private boolean isExpired;
		private boolean isCancelled;
		
		private TimestampedTimeout(long timestamp, TimerTask task){
			this(timestamp, task, true, true);
		}

		private TimestampedTimeout(long timestamp, TimerTask task, boolean isExpired, boolean inCancelled){
			this.timestamp = timestamp;
			this.task = task;
			this.isExpired = isExpired;
			this.isCancelled = inCancelled;
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
  			return isExpired;
		}

		@Override
		public boolean isCancelled() {
			return isCancelled;
		}

		@Override
		public boolean cancel() {
			isCancelled = true;
			return remove(this);
		}
		
		@Override
		public String toString() {
			return "TimerTask: " + task.toString() + "; isExpired: " + isExpired  + "; isCancelled: " + isCancelled;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((task == null) ? 0 : task.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimestampedTimeout other = (TimestampedTimeout) obj;
			if (task == null) {
				if (other.task != null)
					return false;
			} else if (!task.equals(other.task))
				return false;
			return true;
		}
		
		void expire() throws Exception{
			isExpired = true;
			task.run(this);
		}
	}

	@Override
	public void start() {}

	@Override
	public void stop() {}

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

	@Override
	public TimeoutHandlerTimerTask createTimerTask(TimeoutHandler handler, String taskDesc) {
		return TimeoutHandlerTimerTask.of(timerEventSender, handler, taskDesc);
	}
}
