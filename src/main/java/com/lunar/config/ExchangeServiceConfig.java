package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class ExchangeServiceConfig extends ServiceConfig {
	private static String CONNECTOR_FILE_KEY = "connectorFile";
	private static String CONFIG_FILE_KEY = "configFile";
    private static String USER_KEY = "user";
    private static String ACCOUNT_KEY = "account";
    
	private final Optional<String> connectorFile;	
	private final Optional<String> configFile;
	private final Optional<String> user;
	private final Optional<String> account;
	
	public ExchangeServiceConfig(int systemId, String service, 
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
			Optional<String> connectorFile,
			Optional<String> configFile,
			Optional<String> user,
			Optional<String> account){
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
        this.connectorFile = connectorFile;
        this.configFile = configFile;
        this.user = user;
		this.account = account;		
	}
	
	private ExchangeServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
        this.connectorFile = config.hasPath(CONNECTOR_FILE_KEY) ? Optional.of(config.getString(CONNECTOR_FILE_KEY)) : Optional.empty();
        this.configFile = config.hasPath(CONFIG_FILE_KEY) ? Optional.of(config.getString(CONFIG_FILE_KEY)) : Optional.empty();
        this.user = config.hasPath(USER_KEY) ? Optional.of(config.getString(USER_KEY)) : Optional.empty();
		this.account = config.hasPath(ACCOUNT_KEY) ? Optional.of(config.getString(ACCOUNT_KEY)) : Optional.empty();;
	}

	public static ExchangeServiceConfig of(int systemId, String service, Config config) {
		return new ExchangeServiceConfig(systemId, service, config);
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
	
}
