package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class PingServiceConfig extends ServiceConfig {
	public PingServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
	}

	public PingServiceConfig(int systemId, String service, String name, String desc, 
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
	}
	
	public static PingServiceConfig of(int systemId, String service, Config config) {
		return new PingServiceConfig(systemId, service, config);
	}

}
