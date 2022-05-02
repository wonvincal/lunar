package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class MarketDataServiceConfig extends ServiceConfig {
    private static String CONNECTOR_FILE_KEY = "connectorFile";
    private static String CONFIG_FILE_KEY = "configFile";   
    private static String OMDC_CONFIG_FILE_KEY = "omdcConfigFile";
    private static String OMDD_CONFIG_FILE_KEY = "omddConfigFile";
	private static String NUM_SECURITIES_KEY = "numSecurities";
	
	private final int numSecurities;
    private final Optional<String> connectorFile;   
    private final Optional<String> omdcConfigFile;
    private final Optional<String> omddConfigFile;

	public MarketDataServiceConfig(int systemId,
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
			int numSecurities,
            Optional<String> connectorFile,
            Optional<String> omdcConfigFile,
            Optional<String> omddConfigFile){
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
		this.numSecurities = numSecurities;
        this.connectorFile = connectorFile;
        this.omdcConfigFile = omdcConfigFile;
        this.omddConfigFile = omddConfigFile;
	}
	
	private MarketDataServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.numSecurities = config.getInt(NUM_SECURITIES_KEY);
        this.connectorFile = config.hasPath(CONNECTOR_FILE_KEY) ? Optional.of(config.getString(CONNECTOR_FILE_KEY)) : Optional.empty();
        this.omdcConfigFile = config.hasPath(OMDC_CONFIG_FILE_KEY) ? Optional.of(config.getString(OMDC_CONFIG_FILE_KEY)) : (config.hasPath(CONFIG_FILE_KEY) ? Optional.of(config.getString(CONFIG_FILE_KEY) + ".omdc") : Optional.empty());
        this.omddConfigFile = config.hasPath(OMDD_CONFIG_FILE_KEY) ? Optional.of(config.getString(OMDD_CONFIG_FILE_KEY)) : (config.hasPath(CONFIG_FILE_KEY) ? Optional.of(config.getString(CONFIG_FILE_KEY) + ".omdd") : Optional.empty());
	}
	
	public static MarketDataServiceConfig of(int systemId, String service, Config config) {
		return new MarketDataServiceConfig(systemId, service, config);
	}
	
    public Optional<String> connectorFile() {
        return connectorFile;
    }
    
    public Optional<String> omdcConfigFile() {
        return omdcConfigFile;
    }
    
    public Optional<String> omddConfigFile() {
        return omddConfigFile;
    }   	


	public int numSecurities(){
		return numSecurities;
	}

}
