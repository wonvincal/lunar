package com.lunar.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map.Entry;

import com.lunar.core.GrowthFunction;
import com.lunar.message.RequestTypeSetting;
import com.lunar.message.io.sbe.RequestType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

/**
 * Stores all messaging related configurations.  It can be accessed by multiple threads
 * at the same time.
 * 
 * Thread safety: Yes
 * 
 * @author Calvin
 *
 */
public final class MessagingConfig {
	private static String LUNAR_MESSAGING_PATH = "lunar.messaging";
	private static String FRAME_SIZE_KEY = "frameSize";
	private static String TIMEOUT_KEY = "timeout";
	private static String RETRY_INIT_DELAY_KEY = "retry.initDelay";
	private static String RETRY_DELAY_GROWTH_KEY = "retry.delayGrowth";
	private static String RETRY_MAX_RETRY_ATTEMPTS_KEY = "retry.maxRetryAttempts";
	private static String COMMAND_TIMEOUT = "command.timeout";
	private static String REQUEST_PATH = "request";
	private static String REQUEST_TYPE_PATH = "request.requestType";
	
	private final int frameSize;
	private final Duration commandTimeout;
	private final EnumMap<RequestType, RequestTypeSetting> reqTypeSettings; 
	
	private MessagingConfig(int frameSize, Duration commandTimeout, EnumMap<RequestType, RequestTypeSetting> reqTypeSettings){
		this.frameSize = frameSize;
		this.commandTimeout = commandTimeout;
		this.reqTypeSettings = reqTypeSettings;		
	}

	public static MessagingConfig of(Config config){
		Config msgConfig = config.getConfig(LUNAR_MESSAGING_PATH);
		int frameSize = msgConfig.getInt(FRAME_SIZE_KEY);
		Duration commandTimeout = msgConfig.getDuration(COMMAND_TIMEOUT);

		Config requestConfig = msgConfig.getConfig(REQUEST_PATH);
		RequestTypeSetting commonReqSetting = createRequestTypeSetting(requestConfig);

		// create individual object for each request type so that we can get 
		// future updates transparently
		EnumMap<RequestType, RequestTypeSetting> reqTypeSettings = new EnumMap<RequestType, RequestTypeSetting>(RequestType.class);
		for (Entry<String, ConfigValue> entry : msgConfig.getObject(REQUEST_TYPE_PATH).entrySet()){
			String key = entry.getKey();
			RequestType requestType = RequestType.valueOf(key);
			reqTypeSettings.put(requestType, createRequestTypeSetting(msgConfig.getConfig(REQUEST_TYPE_PATH + "." + key)));
		}
		for (RequestType requestType : RequestType.values()){
			if (!reqTypeSettings.containsKey(requestType)){
				reqTypeSettings.put(requestType, commonReqSetting);
				commonReqSetting = commonReqSetting.clone();
			}
		}
		return new MessagingConfig(frameSize, commandTimeout, reqTypeSettings);
	}

	private static RequestTypeSetting createRequestTypeSetting(Config config){
		return RequestTypeSetting.of(config.getDuration(TIMEOUT_KEY),
									 config.getDuration(RETRY_INIT_DELAY_KEY),
									 config.getInt(RETRY_MAX_RETRY_ATTEMPTS_KEY),
									 GrowthFunction.valueOf(config.getString(RETRY_DELAY_GROWTH_KEY)));
	}
	
	public static MessagingConfig of(int frameSize, Duration commandTimeout, EnumMap<RequestType, RequestTypeSetting> reqTypeSettings){
		return new MessagingConfig(frameSize, commandTimeout, reqTypeSettings);
	}
	
	public EnumMap<RequestType, RequestTypeSetting> reqTypeSettings(){
		return reqTypeSettings;
	}
	
	public int frameSize(){
		return frameSize;
	}

	public Duration commandTimeout(){
		return commandTimeout;
	}	
}
