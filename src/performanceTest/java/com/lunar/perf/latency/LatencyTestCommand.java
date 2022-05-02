package com.lunar.perf.latency;
import com.beust.jcommander.Parameter;
import com.lunar.perf.ThreadArrangementType;

public class LatencyTestCommand {
	public enum WaitStrategyType {
		BUSY_SPIN,
		BLOCKING_WAIT,
		YIELDING_WAIT,
		LITE_BLOCKING
	};
	
	@Parameter(names = "-name", description = "Name of test")
	private String name;
	
	@Parameter(names = "-build", description = "Build description")
	private String build;
	
	@Parameter(names = "-runs", description = "Number of runs")
	private int runs;

	@Parameter(names = "-iter", description = "Iterations per run")
	private long iterations;

	@Parameter(names = "-pause", description = "Minimum ns between consecutive messages")
	private long pauseTimeNs;
	
	@Parameter(names = "-persistOutput", description = "Persist output into *something*")
	private boolean persistOutput = false;
	
	@Parameter(names = "-thread", description = "Thread arrangement: one of SINGLE_THREAD_PER_SERVICE (default), CACHED_THREAD_POOL_FOR_ALL_SERVICE")
	private String threadArrangement = "";
	
	private ThreadArrangementType threadArrangementType = ThreadArrangementType.SINGLE_THREAD_PER_SERVICE;
	
	@Parameter(names = "-messageSize", description = "Size of message to be transmitted between services")
	private int messageSize = 64;

	@Parameter(names = "-waitStrategy", description = "Wait strategy: one of BLOCKING_WAIT (default), BUSY_SPIN, LITE_BLOCKING, YIELDING_WAIT")
	private String waitStrategyCommand = "";
	
	private WaitStrategyType waitStrategyType = WaitStrategyType.BLOCKING_WAIT; 
	
	@Parameter(names = "-numNodes", description = "Number of nodes to spawn")
	private int numNodes;

	public String name(){return name;}
	public String build(){return build;}
	public int runs(){return runs;}
	public int numNodes(){return numNodes;}
	public int messageSize(){return messageSize;}
	public long iterations(){return iterations;}
	public long pauseTimeNs(){return pauseTimeNs;}
	public boolean persistOutput(){return persistOutput;}
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

	public LatencyTestCommand name(String name){
		this.name = name;
		return this;
	}
	
	public LatencyTestCommand build(String build){
		this.build = build;
		return this;
	}
	
	public LatencyTestCommand runs(int runs){
		this.runs = runs;
		return this;
	}
	
	public LatencyTestCommand numNodes(int numNodes){
		this.numNodes = numNodes;
		return this;
	}
	
	public LatencyTestCommand messageSize(int messageSize){
		this.messageSize = messageSize;
		return this;
	}
	
	public LatencyTestCommand iterations(long iterations){
		this.iterations = iterations;
		return this;
	}
	
	public LatencyTestCommand pauseTimeNs(long pauseTimeNs){
		this.pauseTimeNs = pauseTimeNs;
		return this;
	}
	
	public LatencyTestCommand persistOutput(boolean persistOutput){
		this.persistOutput = persistOutput;
		return this;
	}
	
	public LatencyTestCommand threadArrangmentType(ThreadArrangementType type){
		this.threadArrangementType = type;
		return this;
	}
	
	public LatencyTestCommand waitStrategyType(WaitStrategyType waitStrategyType){
		this.waitStrategyType = waitStrategyType;
		return this;
	}
}
