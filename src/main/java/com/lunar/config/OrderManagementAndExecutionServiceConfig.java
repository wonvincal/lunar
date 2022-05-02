package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.exception.ConfigurationException;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.service.ServiceConstant;
import com.typesafe.config.Config;

public class OrderManagementAndExecutionServiceConfig extends ServiceConfig {
	private static String NUM_OUTSTANDING_ORDER_REQUESTS = "numOutstandingOrderRequests";
	private static String NUM_OUTSTANDING_ORDERS = "numOutstandingOrders";
	private static String NUM_OUTSTANDING_ORDER_BOOKS = "numOutstandingOrderBooks";
	private static String NUM_OUTSTANDING_ORDERS_PER_SECURITY = "numOutstandingOrdersPerSecurity";
	private static String NUM_CHANNELS = "numChannels";
	private static String AVOID_MULTI_CANCEL_OR_AMEND = "avoidMultiCancelOrAmend";
	private static String EXPECTED_TRADABLE_SECURITIES = "expectedTradableSecurities";
	private static String LINE_HANDLER_KEY = "lineHandler";
	private static String START_ORDER_SID_SEQUENCE = "startOrderSidSequence";
	private static String START_TRADE_SID_SEQUENCE = "startTradeSidSequence";
	private static String INIT_PURCHASING_POWER = "initPurchasingPower";
	private static String EXISTING_POSITION = "existingPositions";
	private static String NUM_THROTTLE_REQUIRED_TO_PROCEED_FOR_BUY = "numThrottleRequiredToProceedForBuy";
    private static String NUM_THROTTLES_PER_UNDERLYING = "numThrottlePerUnderlying";

	private final int numOutstandingOrderRequests;
	private final int numOutstandingOrders;
	private final int numOutstandingOrderBooks;
	private final int numOutstandingOrdersPerSecurity;
	private final int numChannels;
	private final boolean avoidMultiCancelOrAmend;
	private final int expectedTradableSecurities;
	private final LineHandlerConfig lineHandlerConfig;
	private final int startOrderSidSequence;
	private final int startTradeSidSequence;
	private final Optional<Integer> initPurchasingPowerInDollar;
	private final Optional<String> existingPositions;
	private final Optional<Integer> numThrottleRequiredToProceedForBuy;
	private final Optional<Integer> numThrottlePerUnderlying;

	public OrderManagementAndExecutionServiceConfig(int systemId, String service, 
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
			int startOrderSidSequence,
			int startTradeSidSequence,
			int numOutstandingOrderRequests,
			int numOutstandingOrders,
			int numOutstandingOrderBooks,
			int numOutstandingOrdersPerSecurity,
			int numChannels,
			boolean avoidMultiCancelOrAmend,
			int expectedTradableSecurities,
			Optional<Integer> initPurchasingPowerInDollar,
			Optional<String> existingPositions,
			Optional<Integer> numThrottleRequiredToProceedForBuy,
			Optional<Integer> numThrottlePerUnderlying,
			LineHandlerConfig lineHandlerConfig){
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
		this.startOrderSidSequence = startOrderSidSequence;
		this.startTradeSidSequence = startTradeSidSequence;
		this.numOutstandingOrderRequests = numOutstandingOrderRequests;
		this.numOutstandingOrders = numOutstandingOrders;
		this.numOutstandingOrderBooks = numOutstandingOrderBooks;
		this.numOutstandingOrdersPerSecurity = numOutstandingOrdersPerSecurity;
		this.numChannels = numChannels;
		this.avoidMultiCancelOrAmend = avoidMultiCancelOrAmend;
		this.expectedTradableSecurities = expectedTradableSecurities;
		this.lineHandlerConfig = lineHandlerConfig;
		this.initPurchasingPowerInDollar = initPurchasingPowerInDollar;
		this.numThrottleRequiredToProceedForBuy = numThrottleRequiredToProceedForBuy;
		this.existingPositions = existingPositions;
		this.numThrottlePerUnderlying = numThrottlePerUnderlying; 
	}
	
	private OrderManagementAndExecutionServiceConfig(int systemId, String service, Config config) throws ConfigurationException {
		super(systemId, service, config);
		this.startOrderSidSequence = config.hasPath(START_ORDER_SID_SEQUENCE) ? config.getInt(START_ORDER_SID_SEQUENCE) : ServiceConstant.START_ORDER_SID_SEQUENCE;
		this.startTradeSidSequence = config.hasPath(START_TRADE_SID_SEQUENCE) ? config.getInt(START_TRADE_SID_SEQUENCE) : ServiceConstant.START_TRADE_SID_SEQUENCE;
		this.numOutstandingOrderRequests = config.getInt(NUM_OUTSTANDING_ORDER_REQUESTS);
		this.numOutstandingOrders = config.getInt(NUM_OUTSTANDING_ORDERS);
		this.numOutstandingOrderBooks = config.getInt(NUM_OUTSTANDING_ORDER_BOOKS);
		this.numOutstandingOrdersPerSecurity = config.getInt(NUM_OUTSTANDING_ORDERS_PER_SECURITY);
		this.numChannels = config.getInt(NUM_CHANNELS);
		this.avoidMultiCancelOrAmend = config.getBoolean(AVOID_MULTI_CANCEL_OR_AMEND);
		this.expectedTradableSecurities = config.getInt(EXPECTED_TRADABLE_SECURITIES);
		this.lineHandlerConfig = LineHandlerConfig.of(config.getConfig(LINE_HANDLER_KEY));
		this.initPurchasingPowerInDollar = config.hasPath(INIT_PURCHASING_POWER) ? Optional.of(config.getInt(INIT_PURCHASING_POWER)) : Optional.empty();
		this.numThrottleRequiredToProceedForBuy = config.hasPath(NUM_THROTTLE_REQUIRED_TO_PROCEED_FOR_BUY) ? Optional.of(config.getInt(NUM_THROTTLE_REQUIRED_TO_PROCEED_FOR_BUY)) : Optional.empty();
		this.existingPositions = config.hasPath(EXISTING_POSITION) ? Optional.of(config.getString(EXISTING_POSITION)) : Optional.empty();
		this.numThrottlePerUnderlying = config.hasPath(NUM_THROTTLES_PER_UNDERLYING) ? Optional.of(config.getInt(NUM_THROTTLES_PER_UNDERLYING)) : Optional.empty();
	}

	public static OrderManagementAndExecutionServiceConfig of(int systemId, String service, Config config) throws ConfigurationException {
		return new OrderManagementAndExecutionServiceConfig(systemId, service, config);
	}

	public int startOrderSidSequence(){
		return startOrderSidSequence;
	}

	public int startTradeSidSequence(){
	    return startTradeSidSequence;
	}

	public int expectedTradableSecurities(){
		return expectedTradableSecurities;
	}
	
	public int numOutstandingOrdersPerSecurity() {
		return numOutstandingOrdersPerSecurity;
	}

	public int numOutstandingOrderRequests() {
		return numOutstandingOrderRequests;
	}

	public int numOutstandingOrders() {
		return numOutstandingOrders;
	}

	public int numChannels() {
		return numChannels;
	}

	public int numOutstandingOrderBooks() {
		return numOutstandingOrderBooks;
	}

	public boolean avoidMultiCancelOrAmend() {
		return avoidMultiCancelOrAmend;
	}
	
	public LineHandlerConfig lineHandlerConfig(){
		return lineHandlerConfig;
	}
	
	public Optional<Integer> initPurchasingPower(){
		return initPurchasingPowerInDollar;
	}
	
	public Optional<Integer> numThrottleRequiredToProceedForBuy(){
		return numThrottleRequiredToProceedForBuy;
	}
	
	public Optional<String> existingPositions(){
		return existingPositions;
	}

    public Optional<Integer> numThrottlePerUnderlying() {
        return numThrottlePerUnderlying;
    }
    
}
