package com.lunar.perf.throughput;

import com.beust.jcommander.Parameter;
import com.lunar.perf.ThreadArrangementType;
import com.lunar.perf.latency.LatencyTestCommand.WaitStrategyType;

public class ThroughputTestCommand {
	@Parameter(names = "-name", description = "Name of test")
	private String name;
	
	@Parameter(names = "-build", description = "Build description")
	private String build;

	@Parameter(names = "-persistOutput", description = "Persist output into *something*")
	private boolean persistOutput = false;
	
	@Parameter(names = "-burstIntervalNs", description = "Burst interval in nanosecond")
	private int burstIntervalNs;

	@Parameter(names = "-thread", description = "Thread arrangement: one of SINGLE_THREAD_PER_SERVICE (default), CACHED_THREAD_POOL_FOR_ALL_SERVICE")
	private String threadArrangement = "";
	
	private ThreadArrangementType threadArrangementType = ThreadArrangementType.SINGLE_THREAD_PER_SERVICE;
	
	@Parameter(names = "-waitStrategy", description = "Wait strategy: one of BLOCKING_WAIT (default), BUSY_SPIN, LITE_BLOCKING, YIELDING_WAIT")
	private String waitStrategyCommand = "";
	
	private WaitStrategyType waitStrategyType = WaitStrategyType.BLOCKING_WAIT; 
	
	@Parameter(names = "-numNodes", description = "Number of nodes to spawn")
	private int numNodes;

	@Parameter(names = "-iterations", description = "Iterations per run")
	private int iterations;

	@Parameter(names = "-messageSize", description = "Size of message to be transmitted between services")
	private int messageSize = 64;

	@Parameter(names = "-messageQueeuSize", description = "Size of message queue")
	private int messageQueueSize = 128;

	public String name(){return name;}
	public String build(){return build;}
	public int burstIntervalNs(){return burstIntervalNs;}
	public int iterations(){return iterations;}
	public int numNodes(){return numNodes;}
	public boolean persistOutput(){return persistOutput;}
	public int messageSize(){return messageSize;}
	public int messageQueueSize(){return messageQueueSize;}
	
	public ThreadArrangementType threadArrangmentType(){
		if (!threadArrangement.isEmpty()){
			return ThreadArrangementType.valueOf(threadArrangement);
		}
		return threadArrangementType;
	}
	public WaitStrategyType waitStrategyType(){
		if (!waitStrategyCommand.isEmpty()){
			return WaitStrategyType.valueOf(waitStrategyCommand);
		}
		return waitStrategyType;
	}

	public ThroughputTestCommand name(String name){
		this.name = name;
		return this;
	}
	
	public ThroughputTestCommand build(String build){
		this.build = build;
		return this;
	}
	
	public ThroughputTestCommand burstIntervalNs(int burstIntervalNs){
		this.burstIntervalNs = burstIntervalNs;
		return this;
	}

	public ThroughputTestCommand numNodes(int numNodes){
		this.numNodes = numNodes;
		return this;
	}

	public ThroughputTestCommand iterations(int iterations){
		this.iterations = iterations;
		return this;
	}
	
	public ThroughputTestCommand messageSize(int messageSize){
		this.messageSize = messageSize;
		return this;
	}
	
	public ThroughputTestCommand messageQueueSize(int messageQueueSize){
		this.messageQueueSize = messageQueueSize;
		return this;
	}

	public ThroughputTestCommand threadArrangmentType(ThreadArrangementType type){
		this.threadArrangementType = type;
		return this;
	}
	
	public ThroughputTestCommand waitStrategyType(WaitStrategyType waitStrategyType){
		this.waitStrategyType = waitStrategyType;
		return this;
	}

	public ThroughputTestCommand persistOutput(boolean persistOutput){
		this.persistOutput = persistOutput;
		return this;
	}
	

}
