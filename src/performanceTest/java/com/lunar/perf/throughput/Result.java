package com.lunar.perf.throughput;

import java.time.Duration;

public class Result {
	private final int sinkId;
	private final long elapsed;
	private final long iterations;
	private final long remaining;
	private final boolean overflow;
	private final boolean intervalTooShort;
	private final int messageQueueSize;
	private final int messageSize;
	private final int burstIntervalNs;
	private final int burstSize;
	public Result(int sinkId, long elapsed, long iterations, long remaining, boolean overflow, boolean intervalTooShort, 
			int messageQueueSize, int messageSize, int burstIntervalNs, int burstSize){
		this.sinkId = sinkId;
		this.elapsed = elapsed;
		this.iterations = iterations;
		this.remaining = remaining;
		this.overflow = overflow;
		this.intervalTooShort = intervalTooShort;
		this.messageQueueSize = messageQueueSize;
		this.messageSize = messageSize;
		this.burstIntervalNs = burstIntervalNs;
		this.burstSize = burstSize;
	}
	@Override
	public String toString() {
		return String.format("| %6d | %12d | %9d | %9d | %8s | %8s | %12d | %7d | %15d | %9d | %15.2f | %16.2f | %6s |", 
				sinkId, 
				elapsed,
				iterations,
				remaining, 
				(overflow) ? "true" : "false", 
				(intervalTooShort) ? "true" : "false",
				messageQueueSize,
				messageSize,
				burstIntervalNs,
				burstSize,
				(double)Duration.ofMillis(1L).toNanos() / burstIntervalNs * burstSize,
				(double)Duration.ofSeconds(1).toNanos() / burstIntervalNs * burstSize,
				(isPassed()) ? "ok" : "failed");
	}
	public boolean isPassed(){
		return (!overflow && !intervalTooShort);
	}
}
