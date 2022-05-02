package com.lunar.perf.latency;

import java.time.LocalDateTime;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import org.HdrHistogram.Histogram;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lunar.perf.ThreadArrangementType;
import com.lunar.perf.latency.LatencyTestCommand.WaitStrategyType;

public class LatencyTestContext {
	private static long HIGHEST_TRACKABLE_VALUE = 10000000000L;
	private static int NUM_SIGNIFICANT_DIGITS = 4;
	private final String name;
	private final String build;
	private final int runs;
	private final long iterations;
	private final long pauseTimeNs;
	private final int messageSize;
	private int actualMessageSize;
	private final Histogram histogram;
	private final ThreadArrangementType threadArrangementType;
	private CyclicBarrier startBarrier;
	private CountDownLatch completionLatch;
	private WaitStrategyType waitStrategyType;  
	
	public static LatencyTestContext of(LatencyTestCommand command){
		return new LatencyTestContext(command.name(), 
				command.build(), 
				command.runs(), 
				command.iterations(), 
				command.pauseTimeNs(),
				command.threadArrangmentType(),
				command.messageSize(),
				command.waitStrategyType());
	}
	LatencyTestContext(String name, String build, int runs, long iterations, long pauseTimeNs, ThreadArrangementType threadArrangementType, int messageSize, WaitStrategyType waitStrategyType){
		this.name = name;
		this.build = build;
		this.runs = runs;
		this.iterations = iterations;
		this.pauseTimeNs = pauseTimeNs;
		this.histogram = new Histogram(HIGHEST_TRACKABLE_VALUE, NUM_SIGNIFICANT_DIGITS);
		this.threadArrangementType = threadArrangementType;
		this.messageSize = messageSize;
		this.waitStrategyType = waitStrategyType;
	}

	public void startupBarrier(CyclicBarrier startBarrier){
		this.startBarrier = startBarrier;
	}
	
	public void completionLatch(CountDownLatch completionLatch){
		this.completionLatch = completionLatch;
	}
	
	public void readyToStart() throws InterruptedException, BrokenBarrierException{
		this.startBarrier.await();
	}
	
	public void completeOnePass(){
		this.completionLatch.countDown();
	}
	
	public Histogram histogram(){
		return this.histogram;
	}
	
	public int runs(){
		return runs;
	}
	
	public long iterations(){
		return iterations;
	}
	
	public long pauseTimeNs(){
		return pauseTimeNs;
	}

	public ThreadArrangementType threadArrangementType(){
		return threadArrangementType;
	}
	
	public WaitStrategy waitStrategy(){
		if (waitStrategyType == null){
			waitStrategyType = WaitStrategyType.BLOCKING_WAIT;
		}
		switch (waitStrategyType){
		case BLOCKING_WAIT:
			return new BlockingWaitStrategy();
		case BUSY_SPIN:
			return new BusySpinWaitStrategy();
		case LITE_BLOCKING:
			return new LiteBlockingWaitStrategy();
		case YIELDING_WAIT:
			return new YieldingWaitStrategy();
		default:
			throw new UnsupportedOperationException("Unsupported wait strategy: " + waitStrategyType.name());
		}
	}
	
	public int messageSize(){
		return messageSize;
	}
	
	public LatencyTestContext actualMessageSize(int size){
		this.actualMessageSize = size;
		return this;
	}

	public void dump(){
		System.out.println("Name: " + name);
		System.out.println("Build: " + build);
		System.out.println("Date: " + LocalDateTime.now());
		System.out.println("Actual Message Size: " + actualMessageSize);
		System.out.println("Wait Strategy: " + waitStrategyType.name());
		System.out.println("Thread Arrangement: " + threadArrangementType.name());
    	System.out.println("Histogram in us");
        histogram.outputPercentileDistribution(System.out, 1, 1000.0);
	}
	
	public void recordValue(long elapsedTimeNs){
		this.histogram.recordValueWithExpectedInterval(elapsedTimeNs, pauseTimeNs);
	}
}
