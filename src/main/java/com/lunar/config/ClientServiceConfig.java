package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class ClientServiceConfig extends ServiceConfig {
	private static String EXCHANGE_SINK_ID = "exchangeSinkId";

	private final int exchangeSinkId;
	
	public ClientServiceConfig(int systemId,
			String service, 
			String name, 
			String desc, 
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
			int exchangeSinkId){
		super(systemId, 
				service, name, desc, serviceType, serviceClass, 
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
		this.exchangeSinkId = exchangeSinkId;
	}
	
	private ClientServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.exchangeSinkId = config.getInt(EXCHANGE_SINK_ID);
	}

	public static ClientServiceConfig of(int systemId, String service, Config config) {
		return new ClientServiceConfig(systemId, service, config);
	}

	public int exchangeSinkId(){
		return exchangeSinkId;
	}
}
