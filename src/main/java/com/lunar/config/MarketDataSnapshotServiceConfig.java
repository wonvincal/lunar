package com.lunar.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class MarketDataSnapshotServiceConfig extends ServiceConfig {
	private static String NUM_SECURITIES_KEY = "numSecurities";
	private static String SEND_INTERVAL = "interval";
	private static String SEND_OB_INTERVAL = "obInterval";
	private static String SEND_STATS_INTERVAL = "statsInterval";
	private static String ORDER_FLOW_WINDOW_SIZE_NS = "orderFlowWindowSizeNs";
	private static String ORDER_FLOW_SUPPORTED_WINDOW_SIZE_MULTIPLIERS = "orderFlowSupportedWindowSizeMultipliers";
	private static String ORDER_FLOW_ENABLED_SEC_SIDS = "orderFlowEnabledSecSids";
	
	private final int numSecurities;
	private final int obInterval;	
	private final int statsInterval;
	private final long orderFlowWindowSizeNs;
	private final List<Double> orderFlowSupportedWindowSizeMultipliers;
	private final Optional<List<Integer>> orderFlowEnabledSecSids;
	
	public MarketDataSnapshotServiceConfig(int systemId, String service, String name, String desc, 
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
			int numSecurities,
			int obInterval,
			int statsInterval,
			long orderFlowWindowSizeNs,
			List<Double> orderFlowSupportedWindowSizeMultipliers,
			Optional<List<Integer>> orderFlowSecSids){
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
		this.numSecurities = numSecurities;
		this.obInterval = obInterval;
		this.statsInterval = statsInterval;
		this.orderFlowWindowSizeNs = orderFlowWindowSizeNs;
		this.orderFlowSupportedWindowSizeMultipliers = orderFlowSupportedWindowSizeMultipliers;
		this.orderFlowEnabledSecSids = orderFlowSecSids;
	}
	
	private MarketDataSnapshotServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.numSecurities = config.getInt(NUM_SECURITIES_KEY);
		final int defaultInterval = config.getInt(SEND_INTERVAL);
		this.obInterval = config.hasPath(SEND_OB_INTERVAL) ? config.getInt(SEND_OB_INTERVAL) : defaultInterval;
		this.statsInterval = config.hasPath(SEND_STATS_INTERVAL) ? config.getInt(SEND_STATS_INTERVAL) : defaultInterval;
		this.orderFlowWindowSizeNs = config.getLong(ORDER_FLOW_WINDOW_SIZE_NS);
		this.orderFlowSupportedWindowSizeMultipliers = config.getDoubleList(ORDER_FLOW_SUPPORTED_WINDOW_SIZE_MULTIPLIERS);
		this.orderFlowEnabledSecSids = config.hasPath(ORDER_FLOW_ENABLED_SEC_SIDS) ? Optional.of(config.getIntList(ORDER_FLOW_ENABLED_SEC_SIDS)) : Optional.empty();
	}
	
	public static MarketDataSnapshotServiceConfig of(int systemId, String service, Config config) {
		return new MarketDataSnapshotServiceConfig(systemId, service, config);
	}

	public int numSecurities(){
		return numSecurities;
	}

	public int obInterval() {
	    return obInterval;
	}
	
	public int statsInterval() {
	    return statsInterval;
	}

	public long orderFlowWindowSizeNs(){
		return orderFlowWindowSizeNs;
	}
	
	public List<Double> orderFlowSupportedWindowSizeMultipliers(){
		return orderFlowSupportedWindowSizeMultipliers;
	}
	
	public Optional<List<Integer>> orderFlowEnabledSecSids(){
		return orderFlowEnabledSecSids;
	}
}
