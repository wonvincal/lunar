package com.lunar.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.lunar.core.WaitStrategy;
import com.lunar.exception.ConfigurationException;
import com.typesafe.config.Config;

public class LineHandlerConfig {
	private static String ID_KEY = "id";
	private static String CLASS_KEY = "class";
	private static String NAME_KEY = "name";
	private static String DESC_KEY = "description";
	private static String NUM_OUTSTANDING_ORDERS = "numOutstandingOrders";
	private static String ORDER_EXECUTOR_QUEUE_SIZE_KEY = "ordExecQueueSize";
	private static String ORDER_EXECUTOR_WAIT_STRATEGY_KEY = "ordExecWaitStrategy";
	private static String SINGLE_PRODUCER_TO_ORDER_EXECUTOR_KEY = "singleProducerToOrdExec";
	private static String ORDER_UPDATE_RECEIVER_QUEUE_SIZE_KEY = "ordUpdRecvQueueSize";
	private static String ORDER_UPDATE_RECEIVER_WAIT_STRATEGY_KEY = "ordUpdRecvWaitStrategy";
	private static String SINGLE_PRODUCER_TO_ORDER_UPDATE_RECEIVER_KEY = "singleProducerToOrdUpdRecv";
	private static String THROTTLE = "throttle";
	private static String THROTTLE_ARRANGEMENT = "throttleArrangement";
	private static String THROTTLE_DURATION = "throttleDuration";
	private static String CONNECTOR_FILE_KEY = "connectorFile";
	private static String CONFIG_FILE_KEY = "configFile";
    private static String USER_KEY = "user";
    private static String ACCOUNT_KEY = "account";
    private static String BOUND_EXECUTOR_TO_CPU = "boundExecutorToCpu";
    private static String BOUND_UPDATE_PROCESSOR_TO_CPU = "boundUpdateProcessorToCpu";
    private static String MAX_NUM_BATCH_ORDERS = "maxNumBatchOrders";

	private final int id;
	private final String name;
	private final String desc;
	private final Class<?> lineHandlerEngineClass;
	private final int ordExecQueueSize;
	private final int ordUpdRecvQueueSize;
	private final boolean singleProducerToOrdExec;
	private final boolean singleProducerToOrdUpdRecv;
	private final WaitStrategy ordExecWaitStrategy;
	private final WaitStrategy ordUpdRecvWaitStrategy;
	private final int throttle;
	private final Duration throttleDuration;
	private final int numOutstandingOrders;
	private final Optional<String> connectorFile;	
	private final Optional<String> configFile;
	private final Optional<String> user;
	private final Optional<String> account;
	private final Optional<List<Integer>> throttleArrangement;
	private final Optional<Integer> boundExecutorToCpu;
	private final Optional<Integer> boundUpdateProcessorToCpu;
	private final Optional<Integer> maxNumBatchOrders;
	
	public static LineHandlerConfig of(Config config) throws ConfigurationException {
		Class<?> lineHandlerClass;
		String lineHandlerClassName = config.getString(CLASS_KEY);
		try {
			lineHandlerClass = Class.forName(lineHandlerClassName);
		} 
		catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Invalid line handler class name: " + lineHandlerClassName, e);
		}
		return new LineHandlerConfig(config.getInt(ID_KEY),
				config.getString(NAME_KEY),
				config.getString(DESC_KEY),
				lineHandlerClass,
				config.getInt(THROTTLE),
				config.hasPath(THROTTLE_ARRANGEMENT) ? Optional.of(config.getIntList(THROTTLE_ARRANGEMENT)) : Optional.empty(),
				config.getDuration(THROTTLE_DURATION),
				config.getInt(ORDER_EXECUTOR_QUEUE_SIZE_KEY),
				config.getBoolean(SINGLE_PRODUCER_TO_ORDER_EXECUTOR_KEY),
				WaitStrategy.valueOf(config.getString(ORDER_EXECUTOR_WAIT_STRATEGY_KEY)),
				config.getInt(NUM_OUTSTANDING_ORDERS),
				config.getInt(ORDER_UPDATE_RECEIVER_QUEUE_SIZE_KEY),
				config.getBoolean(SINGLE_PRODUCER_TO_ORDER_UPDATE_RECEIVER_KEY),
				WaitStrategy.valueOf(config.getString(ORDER_UPDATE_RECEIVER_WAIT_STRATEGY_KEY)),
		        config.hasPath(CONNECTOR_FILE_KEY) ? Optional.of(config.getString(CONNECTOR_FILE_KEY)) : Optional.empty(),
		        config.hasPath(CONFIG_FILE_KEY) ? Optional.of(config.getString(CONFIG_FILE_KEY)) : Optional.empty(),
		        config.hasPath(USER_KEY) ? Optional.of(config.getString(USER_KEY)) : Optional.empty(),
				config.hasPath(ACCOUNT_KEY) ? Optional.of(config.getString(ACCOUNT_KEY)) : Optional.empty(),
				config.hasPath(BOUND_EXECUTOR_TO_CPU) ? Optional.of(config.getInt(BOUND_EXECUTOR_TO_CPU)) : Optional.empty(),
				config.hasPath(BOUND_UPDATE_PROCESSOR_TO_CPU) ? Optional.of(config.getInt(BOUND_UPDATE_PROCESSOR_TO_CPU)) : Optional.empty(),
				config.hasPath(MAX_NUM_BATCH_ORDERS) ? Optional.of(config.getInt(MAX_NUM_BATCH_ORDERS)) : Optional.empty()
				);
	}
	public static LineHandlerConfig of(int id,
			String name, 
			String desc, 
			Class<?> lineHandlerClass,
			int throttle,
			Optional<List<Integer>> throttleArrangement,
			Duration throttleDuration,
			int ordExecQueueSize,
			boolean singleProducerToOrdExec,
			WaitStrategy ordExecWaitStrategy,
			int numOutstandingOrders,
			int ordUpdRecvQueueSize,
			boolean singleProducerToOrdUpdRecv,
			WaitStrategy ordUpdRecvWaitStrategy,
			Optional<String> connectorFile,
			Optional<String> configFile,
			Optional<String> user,
			Optional<String> account,
			Optional<Integer> boundExecutorToCpu,
			Optional<Integer> boundUpdateProcessorToCpu,
			Optional<Integer> maxNumBatchOrders) throws ConfigurationException {
		return new LineHandlerConfig(id, name, desc, 
				lineHandlerClass, 
				throttle,
				throttleArrangement,
				throttleDuration,
				ordExecQueueSize, 
				singleProducerToOrdExec,
				ordExecWaitStrategy,
				numOutstandingOrders,
				ordUpdRecvQueueSize, 
				singleProducerToOrdUpdRecv,
				ordUpdRecvWaitStrategy,
				connectorFile,
				configFile,
				user,
				account,
				boundExecutorToCpu,
				boundUpdateProcessorToCpu,
				maxNumBatchOrders);
	}
	
	LineHandlerConfig(int id,
			String name, 
			String desc, 
			Class<?> lineHandlerClass,
			int throttle,
			Optional<List<Integer>> throttleArrangement,
			Duration throttleDuration,
			int ordExecQueueSize,
			boolean singleProducerToOrdExec,
			WaitStrategy ordExecWaitStrategy,
			int numOutstandingOrders,
			int ordUpdRecvQueueSize,
			boolean singleProducerToOrdUpdRecv,
			WaitStrategy ordUpdRecvWaitStrategy,
			Optional<String> connectorFile,
			Optional<String> configFile,
			Optional<String> user,
			Optional<String> account,
			Optional<Integer> boundExecutorToCpu,
			Optional<Integer> boundUpdateProcessorToCpu,
			Optional<Integer> maxNumBatchOrders) throws ConfigurationException {
		this.id = id;
		this.name = name;
		this.desc = desc;
		this.lineHandlerEngineClass = lineHandlerClass;
		this.throttle = throttle;
		this.throttleArrangement = throttleArrangement;
		this.throttleDuration = throttleDuration;
		this.ordExecQueueSize = ordExecQueueSize;
		this.ordUpdRecvQueueSize = ordUpdRecvQueueSize;
		this.singleProducerToOrdExec = singleProducerToOrdExec;
		this.singleProducerToOrdUpdRecv = singleProducerToOrdUpdRecv;
		this.ordExecWaitStrategy = ordExecWaitStrategy;
		this.ordUpdRecvWaitStrategy = ordUpdRecvWaitStrategy;
		this.numOutstandingOrders = numOutstandingOrders;
		this.connectorFile = connectorFile;
		this.configFile = configFile;
		this.user = user;
		this.account = account;
		this.boundExecutorToCpu = boundExecutorToCpu;
		this.boundUpdateProcessorToCpu = boundUpdateProcessorToCpu;
		this.maxNumBatchOrders = maxNumBatchOrders;
		if (throttleArrangement.isPresent()){
			int sum = throttleArrangement.get().stream().reduce(0, Integer::sum);
			if (sum != this.throttle){
				throw new ConfigurationException("Sum of individual throttle arrangement (" + sum + ") must be equal to total throttle(" + throttle + ")");
			}
		}
	}
	
	public int id(){
		return id;
	}
	
	public String name(){
		return name;
	}
	
	public Class<?> lineHandlerEngineClass(){
		return lineHandlerEngineClass;
	}
	
	public String desc(){
		return desc;
	}

	public int ordExecQueueSize(){
		return ordExecQueueSize;
	}
	
	public WaitStrategy ordExecWaitStrategy(){
		return ordExecWaitStrategy;
	}

	public int ordUpdRecvQueueSize(){
		return ordUpdRecvQueueSize;
	}
	
	public WaitStrategy ordUpdRecvWaitStrategy(){
		return ordUpdRecvWaitStrategy;
	}

	public boolean singleProducerToOrdExec(){
		return singleProducerToOrdExec;
	}
	
	public boolean singleProducerToOrdUpdRecv(){
		return singleProducerToOrdUpdRecv;
	}

	public int throttle() {
		return throttle;
	}

    public Optional<List<Integer>> throttleArrangement() {
        return throttleArrangement;
    }
    
	public Duration throttleDuration() {
		return throttleDuration;
	}

	public int numOutstandingOrders() {
		return numOutstandingOrders;
	}

    public Optional<String> connectorFile() {
        return connectorFile;
    }
    
    public Optional<String> configFile() {
        return configFile;
    }
    
    public Optional<String> user() {
        return user;
    }

	public Optional<String> account() {
		return account;
	}
	
	public Optional<Integer> boundExecutorToCpu() {
		return boundExecutorToCpu;
	}
	
	public Optional<Integer> maxNumBatchOrders() {
		return maxNumBatchOrders;
	}
	
	public Optional<Integer> boundUpdateProcessorToCpu() {
		return boundUpdateProcessorToCpu;
	}
}

