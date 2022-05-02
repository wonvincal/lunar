package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class OrderAndTradeSnapshotServiceConfig extends ServiceConfig {
	private static String NUM_CHANNELS_KEY = "numChannels";
	private static String EXPECTED_NUM_ORDERS_PER_CHANNEL_KEY = "expectedNumOrdersPerChannel";
    private static String EXPECTED_NUM_TRADES_PER_CHANNEL_KEY = "expectedNumTradesPerChannel";
    private static String EXPECTED_NUM_ORDERS = "expectedNumOrders";
    private static String EXPECTED_NUM_TRADES = "expectedNumTrades";
    private static String PUBLISH_FREQUENCY = "publishFrequency";
    
    public int numChannels() {
		return numChannels;
	}

	public int expectedNumOrdersPerChannel() {
		return expectedNumOrdersPerChannel;
	}

	public int expectedNumTradesPerChannel() {
		return expectedNumTradesPerChannel;
	}

	public int expectedNumOrders() {
		return expectedNumOrders;
	}

	public int expectedNumTrades() {
		return expectedNumTrades;
	}

	public Duration publishFrequency() {
		return publishFrequency;
	}

	private final int numChannels;
    private final int expectedNumOrdersPerChannel;
    private final int expectedNumTradesPerChannel;
    private final int expectedNumOrders;
    private final int expectedNumTrades;
	private final Duration publishFrequency;


	public OrderAndTradeSnapshotServiceConfig(int systemId, String service, String name, String desc, 
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
		    int numChannels,
		    int expectedNumOrdersPerChannel,
		    int expectedNumTradesPerChannel,
		    int expectedNumOrders,
		    int expectedNumTrades,
		    Duration broadcastFrequency){
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
		this.numChannels = numChannels;
        this.expectedNumOrdersPerChannel = expectedNumOrdersPerChannel;
        this.expectedNumTradesPerChannel = expectedNumTradesPerChannel;
        this.expectedNumOrders = expectedNumOrders;
		this.expectedNumTrades = expectedNumTrades;
		this.publishFrequency = broadcastFrequency;
	}

	private OrderAndTradeSnapshotServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.numChannels = config.getInt(NUM_CHANNELS_KEY);
        this.expectedNumOrdersPerChannel = config.getInt(EXPECTED_NUM_ORDERS_PER_CHANNEL_KEY);
        this.expectedNumTradesPerChannel = config.getInt(EXPECTED_NUM_TRADES_PER_CHANNEL_KEY);
        this.expectedNumOrders = config.getInt(EXPECTED_NUM_ORDERS);
		this.expectedNumTrades = config.getInt(EXPECTED_NUM_TRADES);
		this.publishFrequency = config.getDuration(PUBLISH_FREQUENCY);
	}

	public static OrderAndTradeSnapshotServiceConfig of(int systemId, String service, Config config) {
		return new OrderAndTradeSnapshotServiceConfig(systemId, service, config);
	}
}
