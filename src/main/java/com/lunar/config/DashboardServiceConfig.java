package com.lunar.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class DashboardServiceConfig extends ServiceConfig {
	private static String TCP_LISTENING_PORT = "tcpListeningPort";
	private static String MARKET_DATA_AUTO_SUBSCRIBE_SEC_SIDS = "marketDataAutoSubSecSids";
	
	private final Optional<Integer> tcpListeningPort;
	private final Optional<List<Integer>> marketDataAutoSubSecSids;
	
	public DashboardServiceConfig(int systemId, String service, String name, String desc, 
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
			Optional<List<Integer>> marketDataAutoSubSecSids,
			Optional<Integer> tcpListeningPort){
		super(systemId, service, name, desc, serviceType, serviceClass, sinkId,
				queueSize,
				entityInitialCapacity,
				requiredNumThread,
				boundToCpu,
				create,
				warmup,
				stopTimeout,
				journal,
				journalFileName);
		this.marketDataAutoSubSecSids = marketDataAutoSubSecSids;
		this.tcpListeningPort = tcpListeningPort;
	}
	
	private DashboardServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.marketDataAutoSubSecSids = config.hasPath(MARKET_DATA_AUTO_SUBSCRIBE_SEC_SIDS) ? Optional.of(config.getIntList(MARKET_DATA_AUTO_SUBSCRIBE_SEC_SIDS)) : Optional.empty();
		this.tcpListeningPort = config.hasPath(TCP_LISTENING_PORT) ? Optional.of(config.getInt(TCP_LISTENING_PORT)) : Optional.empty();
	}

	public Optional<Integer> tcpListeningPort(){
		return this.tcpListeningPort;
	}
		
	public Optional<List<Integer>> marketDataAutoSubSecSids(){
		return marketDataAutoSubSecSids;
	}

	public static DashboardServiceConfig of(int systemId, String service, Config config) {
		return new DashboardServiceConfig(systemId, service, config);
	}
}
