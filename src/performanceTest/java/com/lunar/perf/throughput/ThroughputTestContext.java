package com.lunar.perf.throughput;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lunar.perf.ThreadArrangementType;
import com.lunar.perf.latency.LatencyTestCommand.WaitStrategyType;

public class ThroughputTestContext {
	private final String name;
	private final String build;
	private int burstIntervalNs;
	private int burstSize;
	private int iterations;
	
	private final int messageSize;
	private int actualMessageSize;
	private final int messageQueueSize;
	private final ThreadArrangementType threadArrangementType;
	private CyclicBarrier startBarrier;
	private CountDownLatch completionLatch;
	private CountDownLatch warmupCompletionLatch;
	private WaitStrategyType waitStrategyType;  
	
	public static ThroughputTestContext of(ThroughputTestCommand command){
		return new ThroughputTestContext(command.name(), 
				command.build(),
				command.burstIntervalNs(),
				command.threadArrangmentType(),
				command.messageSize(),
				command.messageQueueSize(),
				command.iterations(),
				command.waitStrategyType());
	}
	
	ThroughputTestContext(String name, 
			String build, 
			int burstIntervalUs,
			ThreadArrangementType threadArrangementType, 
			int messageSize,
			int messageQueueSize,
			int iterations, 
			WaitStrategyType waitStrategyType){
		this.name = name;
		this.build = build;
		this.burstIntervalNs = burstIntervalUs;
		this.messageSize = messageSize;
		this.messageQueueSize = messageQueueSize;
		this.iterations = iterations;
		this.threadArrangementType = threadArrangementType;
		this.waitStrategyType = waitStrategyType;
	}

	public ThroughputTestContext iterations(int iterations){
		this.iterations = iterations;
		return this;
	}
	
	public ThroughputTestContext burstIntervalNs(int burstIntervalNs){
		this.burstIntervalNs = burstIntervalNs;
		return this;
	}

	public ThroughputTestContext burstSize(int burstSize){
		this.burstSize = burstSize;
		return this;
	}

	public void startupBarrier(CyclicBarrier startBarrier){
		this.startBarrier = startBarrier;
	}
	
	public void warmupCompletionLatch(CountDownLatch warmupCompletionLatch){
		this.warmupCompletionLatch = warmupCompletionLatch;
	}
	
	public void completionLatch(CountDownLatch completionLatch){
		this.completionLatch = completionLatch;
	}
	
	public void readyToStart() throws InterruptedException, BrokenBarrierException{
		this.startBarrier.await();
	}
	
	public void completeWarmup(){
		this.warmupCompletionLatch.countDown();
	}
	
	public void completeOnePass(){
		this.completionLatch.countDown();
	}
	
	public int burstIntervalNs(){
		return burstIntervalNs;
	}

	public int burstSize(){
		return burstSize;
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
	
	public int messageQueueSize(){
		return messageQueueSize;
	}

	public int iterations(){
		return iterations;
	}

	public ThroughputTestContext actualMessageSize(int size){
		this.actualMessageSize = size;
		return this;
	}

	public void dump(){
		System.out.println("Name: " + name);
		System.out.println("Build: " + build);
		System.out.println("Date: " + LocalDateTime.now());
		System.out.println("Message Queue Size: " + messageQueueSize);
		System.out.println("Actual Message Size: " + actualMessageSize);
		System.out.println("Wait Strategy: " + waitStrategyType.name());
		System.out.println("Thread Arrangement: " + threadArrangementType.name());
		System.out.println("+--------+--------------+-----------+-----------+----------+----------+--------------+---------+-----------------+-----------+-----------------+------------------+--------+");
		System.out.println("+-sinkId-+---elapsed----+-iteration-+-remaining-+-overflow-+-tooShort-+-msgQueueSize-+-msgSize-+-burstIntervalNs-+-burstSize-+-throughputPerMs-+-throughputPerSec-+-passed-+");
		System.out.println("+--------+--------------+-----------+-----------+----------+----------+--------------+---------+-----------------+-----------+-----------------+------------------+--------+");
		for (Result result : results){
			System.out.println(result.toString());
		}
		System.out.println("+--------+--------------+-----------+-----------+----------+----------+--------------+---------+-----------------+-----------+-----------------+------------------+--------+");
	}

	private List<Result> results = new ArrayList<Result>(20);
	public void recordValue(int sinkId, long elaspedNs, long remaining, boolean overflow, boolean intervalTooShort){
		results.add(new Result(sinkId, elaspedNs, iterations, remaining, overflow, intervalTooShort, actualMessageSize, messageSize, burstIntervalNs, burstSize));
	}

	public List<Result> results(){
		return this.results;
	}

	public void clearResults(){
		this.results.clear();
	}
	
	public boolean passed(){
		for (Result result : results){
			if (!result.isPassed()){
				return false;
			}
		}
		return true;
	}
}
