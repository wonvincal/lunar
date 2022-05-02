package com.lunar.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.core.TimerService;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.journal.JournalService;
import com.lunar.util.LogUtil;

public class MessageServiceExecutionContext {
	static final Logger LOG = LogManager.getLogger(MessageServiceExecutionContext.class);

	private final LunarService messageService;
	private final Optional<JournalService> journalService;
	private final Disruptor<MutableDirectBuffer> disruptor;
	private final Optional<ExecutorService> executor;
	private final Duration stopTimeout;
	private final TimerService timerService;
	private final AtomicBoolean stopInProgress;

	public static MessageServiceExecutionContext of(LunarService messageService, Disruptor<MutableDirectBuffer> disruptor, Optional<ExecutorService> executor, Duration stopTimeout, TimerService timerService){
		return new MessageServiceExecutionContext(messageService, Optional.empty(), disruptor, executor, stopTimeout, timerService);
	}

	public static MessageServiceExecutionContext of(LunarService messageService, Optional<JournalService> journalService, Disruptor<MutableDirectBuffer> disruptor, Optional<ExecutorService> executor, Duration stopTimeout, TimerService timerService){
		return new MessageServiceExecutionContext(messageService, journalService, disruptor, executor, stopTimeout, timerService);
	}

	MessageServiceExecutionContext(LunarService messageService, Optional<JournalService> journalService, Disruptor<MutableDirectBuffer> disruptor, Optional<ExecutorService> executor, Duration stopTimeout, TimerService timerService){
		this.messageService = messageService;
		this.journalService = journalService;
		this.disruptor = disruptor;
		this.executor = executor;
		this.stopTimeout = stopTimeout;
		this.timerService = timerService;
		this.stopInProgress = new AtomicBoolean(false);
	}
	
	public boolean stopInProgress(){
		return this.stopInProgress.get();
	}
	
	public LunarService messageService(){
		return messageService;
	}
	
	public JournalService journalService(){
		return journalService.get();
	}
	
	public void start(){
		LOG.info("Starting {}", messageService.name());
		disruptor.start();
	}
	
	
	/**
	 * Call {@link ExecutorService#shutdownNow()} internally
	 * @return
	 */
	public List<Runnable> shutdownNow(){
		if (executor.isPresent()){
			return executor.get().shutdownNow();
		}
		return ImmutableList.of();
	}
	
	/**
	 * Stop the service with timeout of {@link stopTimeout}.
	 * 
	 * 1) Stop the disruptor.
	 * 2) Make sure the service's state is stopped.
	 * 2) Stop executor if applicable.
	 * @throws TimeoutException
	 */
	public boolean shutdown() throws TimeoutException {
		if (!this.stopInProgress.compareAndSet(false, true)){
			LOG.info("Stop in progress");
			return false;
		}
		
		LOG.info("Stopping {}", messageService.name());
		long expireAt = this.timerService.nanoTime() + stopTimeout.toNanos();
		
		// Stop disruptor
		try {
			disruptor.shutdown(stopTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} 
		catch (com.lmax.disruptor.TimeoutException e) {
			throw new TimeoutException("Timeout when shutting down disruptor for " + messageService.name(), e);
		}
		LOG.info("Shut down disruptor {}", messageService.name());

		final long pollFreq = ServiceConstant.LUNAR_SERVICE_SHUTDOWN_POLL_FREQ_IN_MS * 1_000_000;
		
		// Check status
		while (!isMessageServiceStopped()){
			LOG.info("Service {} has not been stopped, at state {}", messageService.name(), messageService.state());				
			LockSupport.parkNanos(pollFreq);
			if (this.timerService.nanoTime() > expireAt){
				throw new TimeoutException("Timeout when waiting for service to go to STOPPED state: " + messageService.name());
			}
		}
		
		LOG.info("Service {} has been stopped", messageService.name());

		if (executor.isPresent()){
			ExecutorService executorService = executor.get();
			if (!executorService.isShutdown()){
			LOG.info("Shutting down executor for {}", messageService.name());
			while (true){
				executorService.shutdown();
				try {
					executorService.awaitTermination(expireAt - this.timerService.nanoTime(), TimeUnit.NANOSECONDS);
				} 
				catch (Exception e1) {
					LOG.warn("Interrupted while shutting down executor for " + this.messageService.name());
				}
				if (executorService.isShutdown()){
					break;
				}
				if (this.timerService.nanoTime() > expireAt){
					throw new TimeoutException("Timeout when shutting down executor for: " + messageService.name());					
				}
			}	
			}
			else{
				LOG.info("Executor for {} is down already", messageService.name());
			}
		}
		LogUtil.logService(LOG, messageService, "stopped successfully");
		return true;
	}
	
	private boolean isMessageServiceStopped(){
		return this.messageService.isStopped();
	}
	
	@SuppressWarnings("unused")
	private boolean isJournalServiceStopped(){
		return this.journalService.get().isStopped();
	}
	
	public boolean isStopped(){
		if (executor.isPresent()){
			return this.messageService.isStopped() && executor.get().isShutdown(); 
		}
		else{
			return this.messageService.isStopped();			
		}
	}

}
