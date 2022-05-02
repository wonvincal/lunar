package com.lunar.config;

import java.time.Duration;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class NotificationServiceConfig extends ServiceConfig {
	private static String ALL_EVENT_BUFFER_SIZE = "allEventBufferSize";
	private static String SNAPSHOT_EVENT_BUFFER_SIZE = "snapshotEventBufferSize";
    private static String PUBLISH_FREQUENCY = "publishFrequency";
    private static String ALL_EVENT_BUFFER_PURGE_COUNT_IF_FULL = "allEventBufferPurgeCountIfFull";
    private static String SNAPSHOT_EVENT_BUFFER_PURGE_COUNT_IF_FULL = "snapshotEventBufferPurgeCountIfFull";

	private final Duration publishFrequency;
    private final int allEventBufferSize;
    private final int snapshotEventBufferSize;
    private final int allEventBufferPurgeCountIfFull;
    private final int snapshotEventBufferPurgeCountIfFull;

    public NotificationServiceConfig(int systemId, String service, String name, String desc, 
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
			int allEventBufferSize,
			int allEventBufferPurgeCountIfFull,
			int snapshotEventBufferSize,
			int snapshotEventBufferPurgeCountIfFull,
		    Duration broadcastFrequency){
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
		this.allEventBufferSize = allEventBufferSize;
		this.snapshotEventBufferSize = snapshotEventBufferSize;
		this.publishFrequency = broadcastFrequency;
		this.allEventBufferPurgeCountIfFull = allEventBufferPurgeCountIfFull;
		this.snapshotEventBufferPurgeCountIfFull = snapshotEventBufferPurgeCountIfFull;
	}
	
	public int allEventBufferSize() {
		return allEventBufferSize;
	}

	public int allEventBufferPurgeCountIfFull() {
		return allEventBufferPurgeCountIfFull;
	}
	
	public int snapshotEventBufferPurgeCountIfFull(){
		return snapshotEventBufferPurgeCountIfFull;
	}

	public int snapshotEventBufferSize() {
		return snapshotEventBufferSize;
	}

	public Duration publishFrequency() {
		return publishFrequency;
	}
	
	private NotificationServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.allEventBufferSize = config.getInt(ALL_EVENT_BUFFER_SIZE);
		this.allEventBufferPurgeCountIfFull = config.getInt(ALL_EVENT_BUFFER_PURGE_COUNT_IF_FULL);
		this.snapshotEventBufferSize = config.getInt(SNAPSHOT_EVENT_BUFFER_SIZE);
		this.snapshotEventBufferPurgeCountIfFull = config.getInt(SNAPSHOT_EVENT_BUFFER_PURGE_COUNT_IF_FULL);
		this.publishFrequency = config.getDuration(PUBLISH_FREQUENCY);
	}

	public static NotificationServiceConfig of(int systemId, String service, Config config) {
		return new NotificationServiceConfig(systemId, service, config);
	}
}
