package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class ScoreBoardServiceConfig extends StrategyServiceConfig {
    public ScoreBoardServiceConfig(int systemId, 
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
            Optional<String> throttleTrackerIndexByIssuer) {
        super(systemId, service, name, desc, serviceType, serviceClass, sinkId, queueSize, entityInitialCapacity, requiredNumThread, boundToCpu, create, warmup, stopTimeout,
                journal, journalFileName, numUnderlyings, numWarrants, numWrtsPerUnd, exchangeCode, "", true, 0, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private ScoreBoardServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
	}
	
	public static ScoreBoardServiceConfig of(int systemId, String service, Config config) {
		return new ScoreBoardServiceConfig(systemId, service, config);
	}

}
