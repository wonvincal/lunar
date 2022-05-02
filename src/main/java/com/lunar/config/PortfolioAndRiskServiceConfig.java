package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class PortfolioAndRiskServiceConfig extends ServiceConfig {
    private static String PUBLISH_FREQUENCY = "publishFrequency";
    private static String SECURITY_MAX_PROFIT = "security.maxProfit";
    private static String SECURITY_MAX_LOSS = "security.maxLoss";
    private static String SECURITY_MAX_OPEN_POSITION = "security.maxOpenPosition";
    private static String SECURITY_MAX_CAP_USED = "security.maxCapUsed";
    
    private static String UNDERLYING_MAX_PROFIT = "underlying.maxProfit";
    private static String UNDERLYING_MAX_LOSS = "underlying.maxLoss";
    private static String UNDERLYING_MAX_OPEN_POSITION = "underlying.maxOpenPosition";
    private static String UNDERLYING_MAX_CAP_USED = "underlying.maxCapUsed";

    private static String ISSUER_MAX_PROFIT = "issuer.maxProfit";
    private static String ISSUER_MAX_LOSS = "issuer.maxLoss";
    private static String ISSUER_MAX_OPEN_POSITION = "issuer.maxOpenPosition";
    private static String ISSUER_MAX_CAP_USED = "issuer.maxCapUsed";

    private static String FIRM_MAX_PROFIT = "firm.maxProfit";
    private static String FIRM_MAX_LOSS = "firm.maxLoss";
    private static String FIRM_MAX_OPEN_POSITION = "firm.maxOpenPosition";
    private static String FIRM_MAX_CAP_USED = "firm.maxCapUsed";

	public Duration publishFrequency() {
		return publishFrequency;
	}

	public RiskControlConfig defaultSecurityRiskControlConfig() {
		return defaultSecurityRiskControlConfig;
	}

	public RiskControlConfig defaultIssuerRiskControlConfig() {
		return defaultIssuerRiskControlConfig;
	}

	public RiskControlConfig defaultUndRiskControlConfig() {
		return defaultUndRiskControlConfig;
	}

	public RiskControlConfig defaultFirmRiskControlConfig() {
		return defaultFirmRiskControlConfig;
	}

	private final Duration publishFrequency;
	private final RiskControlConfig defaultSecurityRiskControlConfig;
	private final RiskControlConfig defaultIssuerRiskControlConfig;
	private final RiskControlConfig defaultUndRiskControlConfig;
	private final RiskControlConfig defaultFirmRiskControlConfig;

	public static class RiskControlConfig {
		private final Optional<Long> maxOpenPosition;
		private final Optional<Double> maxProfit;
		private final Optional<Double> maxLoss;
		private final Optional<Double> maxCapLimit;
		
		public static RiskControlConfig of(Optional<Long> maxOpenPosition, Optional<Double> maxProfit, Optional<Double> maxLoss, Optional<Double> maxCapLimit){
			return new RiskControlConfig(maxOpenPosition, maxProfit, maxLoss, maxCapLimit);
		}
		
		RiskControlConfig(Optional<Long> maxOpenPosition, Optional<Double> maxProfit, Optional<Double> maxLoss, Optional<Double> maxCapLimit){
			this.maxOpenPosition = maxOpenPosition;
			this.maxLoss = maxLoss;
			this.maxProfit = maxProfit;
			this.maxCapLimit = maxCapLimit;
		}
		
		public Optional<Long> maxOpenPosition(){ return maxOpenPosition;}
		public Optional<Double> maxProfit(){ return maxProfit;}
		public Optional<Double> maxLoss(){ return maxLoss;}
		public Optional<Double> maxCapLimit(){ return maxCapLimit;}
	}
	
	public PortfolioAndRiskServiceConfig(int systemId, String service, String name, String desc, 
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
		    Duration broadcastFrequency,
		    RiskControlConfig defaultSecurityRiskControlConfig,
		    RiskControlConfig defaultIssuerRiskControlConfig,
		    RiskControlConfig defaultUndRiskControlConfig,
		    RiskControlConfig defaultFirmRiskControlConfig){
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
		this.publishFrequency = broadcastFrequency;
		this.defaultFirmRiskControlConfig = defaultFirmRiskControlConfig;
		this.defaultIssuerRiskControlConfig = defaultIssuerRiskControlConfig;
		this.defaultSecurityRiskControlConfig = defaultSecurityRiskControlConfig;
		this.defaultUndRiskControlConfig = defaultUndRiskControlConfig;
	}

	private PortfolioAndRiskServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.publishFrequency = config.getDuration(PUBLISH_FREQUENCY);
		this.defaultSecurityRiskControlConfig = new RiskControlConfig( 
				optionalLongOf(config, SECURITY_MAX_OPEN_POSITION), 
				optionalDoubleOf(config, SECURITY_MAX_PROFIT),
				optionalDoubleOf(config, SECURITY_MAX_LOSS),
				optionalDoubleOf(config, SECURITY_MAX_CAP_USED));
		this.defaultIssuerRiskControlConfig = new RiskControlConfig(
				optionalLongOf(config, ISSUER_MAX_OPEN_POSITION), 
				optionalDoubleOf(config, ISSUER_MAX_PROFIT),
				optionalDoubleOf(config, ISSUER_MAX_LOSS),
				optionalDoubleOf(config, ISSUER_MAX_CAP_USED));
		this.defaultFirmRiskControlConfig = new RiskControlConfig(
				optionalLongOf(config, FIRM_MAX_OPEN_POSITION), 
				optionalDoubleOf(config, FIRM_MAX_PROFIT),
				optionalDoubleOf(config, FIRM_MAX_LOSS),
				optionalDoubleOf(config, FIRM_MAX_CAP_USED));
		this.defaultUndRiskControlConfig = new RiskControlConfig(
				optionalLongOf(config, UNDERLYING_MAX_OPEN_POSITION), 
				optionalDoubleOf(config, UNDERLYING_MAX_PROFIT),
				optionalDoubleOf(config, UNDERLYING_MAX_LOSS),
				optionalDoubleOf(config, UNDERLYING_MAX_CAP_USED));
	}
	
	private static Optional<Double> optionalDoubleOf(Config config, String path){
		return config.hasPath(path) ? Optional.of(config.getDouble(path)) : Optional.empty();
	}

	private static Optional<Long> optionalLongOf(Config config, String path){
		return config.hasPath(path) ? Optional.of(Long.valueOf(config.getLong(path))) : Optional.empty();
	}

	public static PortfolioAndRiskServiceConfig of(int systemId, String service, Config config) {
		return new PortfolioAndRiskServiceConfig(systemId, service, config);
	}
}
