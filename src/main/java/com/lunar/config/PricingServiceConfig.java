package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class PricingServiceConfig extends ServiceConfig {
    private static String NUM_UNDERLYINGS_KEY = "numUnds";
    private static String NUM_WARRANTS_KEY = "numWrts";

    private final int numUnderlyings;
    private final int numWarrants;

	public PricingServiceConfig(int systemId, 
			String service, String name, String desc, 
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
            int numUnderlyings,
            int numWarrants,
            int numWrtsPerUnd,
			String exchangeCode,
			String securityCode){
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
        this.numUnderlyings = numUnderlyings;
        this.numWarrants = numWarrants;
	}

	private PricingServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
        this.numUnderlyings = config.getInt(NUM_UNDERLYINGS_KEY);
        this.numWarrants = config.getInt(NUM_WARRANTS_KEY);
	}
	
    public int numUnderlyings() {
        return numUnderlyings;
    }
    
    public int numWarrants() {
        return numWarrants;
    }

	public static PricingServiceConfig of(int systemId, String service, Config config) {
		return new PricingServiceConfig(systemId, service, config);
	}

}
