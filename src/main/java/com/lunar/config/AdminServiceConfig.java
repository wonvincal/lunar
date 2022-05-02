package com.lunar.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class AdminServiceConfig extends ServiceConfig {
	private static int EXPECTED_NUM_CHILDREN = 16;
	private static int EXPECTED_NUM_REMOTE_SYSTEMS = 2;
	private static final String ENABLE_AERON_KEY = "enableAeron";
	private static final String AERON_START_TIMEOUT_KEY = "aeronStartTimeout";
	private final List<ServiceConfig> childConfigs;
	private CommunicationConfig localAeronConfig;
	private final List<CommunicationConfig> remoteAeronConfigs;
	private final boolean enableAeron;
	private final Duration aeronStartTimeout; 

	public AdminServiceConfig(int systemId,
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
			boolean enableAeron,
			Duration aeronStartTimeout) {
		super(systemId, service, name, desc, serviceType, serviceClass, sinkId, queueSize, entityInitialCapacity, requiredNumThread, boundToCpu, create, warmup, stopTimeout, journal, journalFileName);
		this.childConfigs = new ArrayList<ServiceConfig>(EXPECTED_NUM_CHILDREN);
		this.remoteAeronConfigs = new ArrayList<CommunicationConfig>(EXPECTED_NUM_REMOTE_SYSTEMS);
		this.enableAeron = enableAeron;
		this.aeronStartTimeout = aeronStartTimeout;
		if (serviceType() != ServiceType.AdminService){
			throw new IllegalArgumentException("invalid service type in config, must be AdminService, cannot create AdminServiceConfig");
		}
	}

	public AdminServiceConfig(int systemId, String name, Config config) {
		super(systemId, name, config);
		this.childConfigs = new ArrayList<ServiceConfig>(EXPECTED_NUM_CHILDREN);
		this.remoteAeronConfigs = new ArrayList<CommunicationConfig>(EXPECTED_NUM_REMOTE_SYSTEMS);
		this.enableAeron = config.getBoolean(ENABLE_AERON_KEY);
		if (this.enableAeron){
			this.aeronStartTimeout = config.getDuration(AERON_START_TIMEOUT_KEY);
		}
		else if (config.hasPath(AERON_START_TIMEOUT_KEY)){
			this.aeronStartTimeout = config.getDuration(AERON_START_TIMEOUT_KEY);
		}
		else {
			this.aeronStartTimeout = Duration.ZERO;
		}
		if (serviceType() != ServiceType.AdminService){
			throw new IllegalArgumentException("invalid service type in config, must be AdminService, cannot create AdminServiceConfig");
		}
	}
	
	List<ServiceConfig> addChildServiceConfig(ServiceConfig config){
		childConfigs.add(config);
		return childConfigs;
	}
	List<CommunicationConfig> addRemoteAeronConfig(CommunicationConfig config){
		remoteAeronConfigs.add(config);
		return remoteAeronConfigs;
	}
	public List<ServiceConfig> childServiceConfigs(){
		return childConfigs;
	}
	public List<CommunicationConfig> remoteAeronConfigs(){
		return remoteAeronConfigs;
	}
	public CommunicationConfig localAeronConfig(){
		return localAeronConfig;
	}	
	public AdminServiceConfig localAeronConfig(CommunicationConfig localAeronConfig){
		this.localAeronConfig = localAeronConfig;
		return this;
	}
	public boolean enableAeron(){
		return enableAeron;
	}
	public Duration aeronStartTimeout(){
		return aeronStartTimeout;
	}
}
