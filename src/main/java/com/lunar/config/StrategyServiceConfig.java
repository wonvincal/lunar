package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class StrategyServiceConfig extends ServiceConfig {
	// Code - external code, fixed length
	// Symbol - internal symbol, variable length
	// ID - external source specific id - String
	// SID - internal system id
	private static String EXCHANGE_CODE_KEY = "exchangeCode";
	private static String SECURITY_CODE_KEY = "secCode";
    private static String NUM_UNDERLYINGS_KEY = "numUnds";
    private static String NUM_WARRANTS_KEY = "numWrts";
    private static String NUM_WRTS_PER_UND_KEY = "numWrtsPerUnd";
    private static String AUTO_SWITCH_ON_KEY = "autoSwitchOn";
    private static String SEND_PARAMS_INTERVAL_KEY = "sendParamsInterval";
    private static String THROTTLE_TRACKER_INDEX_BY_ISSUER = "throttleTrackerIndexByIssuer";
    private static String UNDERLYING_FILTER = "underlyingFilter";
    private static String ISSUER_FILTER = "issuerFilter";
    
    private final int numUnderlyings;
    private final int numWarrants;
    private final int numWrtsPerUnd;
	private final String exchangeCode;	
	private final String securityCode;
	private final boolean canAutoSwitchOn;
	private final long sendParamsInterval;
	private final Optional<String> throttleTrackerIndexByIssuer;
	private final Optional<String> underlyingFilter;
	private final Optional<String> issuerFilter;
	
	public StrategyServiceConfig(int systemId, 
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
			String securityCode,
			boolean canAutoSwitchOn,
			long sendParamsInterval,
			Optional<String> throttleTrackerIndexByIssuer,
			Optional<String> underlyingFilter,
			Optional<String> issuerFilter){
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
        this.numWrtsPerUnd = numWrtsPerUnd;
		this.exchangeCode = exchangeCode;
		this.securityCode = securityCode;
		this.canAutoSwitchOn = canAutoSwitchOn;
		this.sendParamsInterval = sendParamsInterval;
		this.throttleTrackerIndexByIssuer = throttleTrackerIndexByIssuer;
		this.underlyingFilter = underlyingFilter;
		this.issuerFilter = issuerFilter;
	}

	protected StrategyServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
        this.numUnderlyings = config.getInt(NUM_UNDERLYINGS_KEY);
        this.numWarrants = config.getInt(NUM_WARRANTS_KEY);
        this.numWrtsPerUnd = config.getInt(NUM_WRTS_PER_UND_KEY);
		this.exchangeCode = config.getString(EXCHANGE_CODE_KEY);
		this.securityCode = config.getString(SECURITY_CODE_KEY);
		this.canAutoSwitchOn = config.getBoolean(AUTO_SWITCH_ON_KEY);
		this.sendParamsInterval = config.getDuration(SEND_PARAMS_INTERVAL_KEY).toNanos();
		this.throttleTrackerIndexByIssuer = (config.hasPath(THROTTLE_TRACKER_INDEX_BY_ISSUER)) ? Optional.of(config.getString(THROTTLE_TRACKER_INDEX_BY_ISSUER)) : Optional.empty();
		this.underlyingFilter = (config.hasPath(UNDERLYING_FILTER)) ? Optional.of(config.getString(UNDERLYING_FILTER)) : Optional.empty();
		this.issuerFilter = (config.hasPath(ISSUER_FILTER)) ? Optional.of(config.getString(ISSUER_FILTER)) : Optional.empty();
	}
	
    public int numUnderlyings() {
        return numUnderlyings;
    }
    
    public int numWarrants() {
        return numWarrants;
    }
    
    public int numWrtsPerUnd() {
        return numWrtsPerUnd;
    }

	public String exchangeCode() {
		return exchangeCode;
	}

	public String securityCode() {
		return securityCode;
	}
	
	public boolean canAutoSwitchOn() {
	    return canAutoSwitchOn;
	}
	
	public long sendParamsInterval() {
	    return sendParamsInterval;
	}

	public Optional<String> throttleTrackerIndexByIssuer() {
	    return throttleTrackerIndexByIssuer;
	}
	
    public Optional<String> underlyingFilter() {
        return underlyingFilter;
    }

    public Optional<String> issuerFilter() {
        return issuerFilter;
    }
	
	public static StrategyServiceConfig of(int systemId, String service, Config config) {
		return new StrategyServiceConfig(systemId, service, config);
	}


}
