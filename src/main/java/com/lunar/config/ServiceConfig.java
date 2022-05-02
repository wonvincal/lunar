package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.lunar.service.ServiceConstant;
import com.typesafe.config.Config;

public class ServiceConfig {
	private static String LUNAR_SERVICE_PATH_PREFIX = "lunar.service.";
	private static String TYPE_KEY = "type";
	private static String CLASS_KEY = "class";
	private static String DESC_KEY = "description";
	private static String CREATE_KEY = "create";
	private static String NAME_KEY = "name";
	private static String SINK_ID_KEY = "sinkId";
	private static String QUEUE_SIZE_KEY = "queueSize";
	private static String ENTITY_INIT_CAPACITY_KEY = "entityInitialCapacity";
	private static String REQUIRED_NUM_THREAD_KEY = "requiredNumThread";
	private static String BOUND_TO_CPU = "boundToCpu";
	private static String STOP_TIMEOUT_KEY = "stopTimeout";
	private static String WARMUP_KEY = "warmup";
	private static String JOURNAL_KEY = "journal";
	private static String JOURNAL_FILE_NAME_KEY = "journalFileName";
	private final int systemId;
	private final int sinkId;
	private final String service;
	private final String name;
	private final String desc;
	private final Optional<Integer> queueSize;
	private final Optional<Integer> entityInitialCapacity;
	private final Optional<Integer> requiredNumThread;
	private final Optional<Integer> boundToCpu;
	private final ServiceType serviceType;
	private final Optional<Class<?>> serviceClass;
	private final Duration stopTimeout;
	private final boolean create;
	private final boolean warmup;
	private final boolean journal;
	private final String journalFileName;
	
	public ServiceConfig(int systemId, String service, Config config) {
		this(systemId, 
				service, 
				config.getString(NAME_KEY),
				config.getString(DESC_KEY),
				Enum.valueOf(ServiceType.class, config.getString(TYPE_KEY)),
				config.hasPath(CLASS_KEY) ? Optional.of(config.getString(CLASS_KEY)) : Optional.empty(),
				config.getInt(SINK_ID_KEY),
				config.hasPath(QUEUE_SIZE_KEY) ? Optional.of(config.getInt(QUEUE_SIZE_KEY)) : Optional.empty(),
				config.hasPath(ENTITY_INIT_CAPACITY_KEY) ? Optional.of(config.getInt(ENTITY_INIT_CAPACITY_KEY)) : Optional.empty(),
				config.hasPath(REQUIRED_NUM_THREAD_KEY) ? Optional.of(config.getInt(REQUIRED_NUM_THREAD_KEY)) : Optional.empty(),
				config.hasPath(BOUND_TO_CPU) ? Optional.of(config.getInt(BOUND_TO_CPU)) : Optional.empty(),
				(boolean)config.getBoolean(CREATE_KEY),
				(boolean)config.getBoolean(WARMUP_KEY),
				config.hasPath(STOP_TIMEOUT_KEY) ? config.getDuration(STOP_TIMEOUT_KEY) : Duration.ofSeconds(ServiceConstant.DEFAULT_STOP_TIMEOUT_IN_SECOND),
				(boolean)config.getBoolean(JOURNAL_KEY),
				config.getString(JOURNAL_FILE_NAME_KEY));
	}
	
	public ServiceConfig(int systemId,
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
			String journalFileName) {
		this.systemId = systemId;
		this.service = service;
		this.name = name;
		this.desc = desc;
		this.serviceType = serviceType;
		if (serviceClass.isPresent()){
			try {
				this.serviceClass = Optional.of(Class.forName(serviceClass.get()));
			} 
			catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("Invalid service class name: " + serviceClass.get(), e);
			}		
		}
		else{
			this.serviceClass = Optional.empty();
		}
		this.create = create;
		this.sinkId = sinkId;
		this.queueSize = queueSize;
		this.entityInitialCapacity = entityInitialCapacity;
		this.requiredNumThread = requiredNumThread;
		this.boundToCpu = boundToCpu;
		this.stopTimeout = stopTimeout;
		this.warmup = warmup;
		this.journal = journal;
		this.journalFileName = journalFileName;
	}
	public int systemId(){
		return systemId;
	}
	public int sinkId(){
		return sinkId;
	}
	public Optional<Integer> entityInitialCapacity(){
		return entityInitialCapacity;
	}
	public ServiceType serviceType(){
		return serviceType;
	}
	public Optional<Class<?>> serviceClass(){
		return serviceClass;
	}
	public String desc(){
		return desc;
	}
	public String service(){
		return service;
	}
	public String name(){
		return name;
	}
	public Optional<Integer> queueSize(){
		return queueSize;
	}
	public Optional<Integer> requiredNumThread(){
		return requiredNumThread;
	}
	public Optional<Integer> boundToCpu(){
		return boundToCpu;
	}
	public boolean create(){
		return create;
	}
	public boolean warmup(){
		return warmup;
	}
	public boolean journal(){
		return journal;
	}
	public String journalFileName(){
		return journalFileName;
	}
	public Duration stopTimeout(){
		return stopTimeout;
	}

	public static Config getSpecificConfig(String service, Config wholeConfig){
		return wholeConfig.getConfig(LUNAR_SERVICE_PATH_PREFIX + service);
	}
	public static boolean shouldCreate(Config config){
		return config.getBoolean(CREATE_KEY);
	}
	public static ServiceType getServiceType(Config config){
		return ServiceType.valueOf(config.getString(TYPE_KEY));
	}
}
