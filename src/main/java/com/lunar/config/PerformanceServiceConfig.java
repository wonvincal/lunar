package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class PerformanceServiceConfig extends ServiceConfig {
	private static String STAT_GATHERING_FREQ_KEY = "statGatheringFreq";

	private final long statGatheringFreqNs;

	public PerformanceServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.statGatheringFreqNs = config.getDuration(STAT_GATHERING_FREQ_KEY).toNanos();
	}

	public PerformanceServiceConfig(int systemId, String service, String name, String desc, 
			ServiceType serviceType,
			Optional<String> serviceClass,
			int sinkId,
			Optional<Integer> queueSize,
			Optional<Integer> entityInitialCapacity,
			Optional<Integer> requiredNumThread,
			Optional<Integer> boundToCpu,
			boolean create,
			boolean warmup,
			Duration stopTimeout,
			boolean journal,
			String journalFileName,
			long statGatheringFreqNs){
		super(systemId, service, name, desc, serviceType, serviceClass, 
				sinkId,
				queueSize,
				entityInitialCapacity,
				requiredNumThread,
				boundToCpu,
				create,
				warmup,
				stopTimeout,
				journal,
				journalFileName);
		this.statGatheringFreqNs = statGatheringFreqNs;
	}

	public long statGatheringFreqNs(){
		return this.statGatheringFreqNs;
	}

}
