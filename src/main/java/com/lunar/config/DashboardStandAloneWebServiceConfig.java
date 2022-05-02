package com.lunar.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;

public class DashboardStandAloneWebServiceConfig extends ServiceConfig {
	private static String WS_PORT_KEY = "wsPort";
	private static String WS_PATH_KEY = "wsPath";
	private static String WORKER_IO_THREADS = "numIoThreads"; // Handle non blocking IO.  Default to 1 per core
	private static String WORKER_CORE_THREADS = "numTaskCoreThreads"; // Default to io_threads * 8
	private static String REQUEST_TIMEOUT = "requestTimeout"; // Default to 60 secs
	private static String BUFFER_SIZE = "bufferSizeInBytes"; // Default to 16K
	private static String NUM_OUTSTANDING_BUFFER_PER_CHANNEL_THRESHOLD = "numOutstandingBufferPerChannelThreshold"; // Threshold before we stop sending message to a channel
	private static String TCP_LISTENING_PORT = "tcpListeningPort";
	private static String TCP_LISTENING_URL = "tcpListeningUrl";

	private final String wsPath;
	private final int wsPort;
	private final Optional<Integer> numIoThreads;
	private final Optional<Integer> numCoreThreads;
	private final Optional<Duration> requestTimeout;
	private final Optional<Integer> bufferSizeInBytes;
	private final Optional<Integer> numOutstandingBufferPerChannelThreshold;
	private final Optional<Integer> tcpListeningPort;
	private final Optional<String> tcpListeningUrl;
	
	public DashboardStandAloneWebServiceConfig(int systemId, 
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
			String journalFileName,
			int port,
			String wsPath,
			Optional<Integer> numIoThreads,
			Optional<Integer> numCoreThreads,
			Optional<Duration> requestTimeout,
			Optional<Integer> bufferSizeInBytes,
			Optional<List<Integer>> marketDataAutoSubSecSids,
			Optional<Integer> numOutstandingBufferPerChannelThreshold,
			Optional<Integer> tcpListeningPort,
			Optional<String> tcpListeningUrl){
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
		this.wsPort = port;
		this.wsPath = wsPath;
		this.numIoThreads = numIoThreads;
		this.numCoreThreads = numCoreThreads;
		this.requestTimeout = requestTimeout;
		this.bufferSizeInBytes = bufferSizeInBytes;
		this.numOutstandingBufferPerChannelThreshold = numOutstandingBufferPerChannelThreshold;
		this.tcpListeningPort = tcpListeningPort;
		this.tcpListeningUrl = tcpListeningUrl;
	}

	private DashboardStandAloneWebServiceConfig(int systemId, String service, Config config) {
		super(systemId, service, config);
		this.wsPort = config.getInt(WS_PORT_KEY);
		this.wsPath = config.getString(WS_PATH_KEY);
		this.numIoThreads = config.hasPath(WORKER_IO_THREADS) ? Optional.of(config.getInt(WORKER_IO_THREADS)) : Optional.empty();
		this.numCoreThreads = config.hasPath(WORKER_CORE_THREADS) ? Optional.of(config.getInt(WORKER_CORE_THREADS)) : Optional.empty();
		this.requestTimeout = config.hasPath(REQUEST_TIMEOUT) ? Optional.of(config.getDuration(REQUEST_TIMEOUT)) : Optional.empty();
		this.bufferSizeInBytes = config.hasPath(BUFFER_SIZE) ? Optional.of(config.getInt(BUFFER_SIZE)) : Optional.empty();
		this.numOutstandingBufferPerChannelThreshold = config.hasPath(NUM_OUTSTANDING_BUFFER_PER_CHANNEL_THRESHOLD) ? Optional.of(config.getInt(NUM_OUTSTANDING_BUFFER_PER_CHANNEL_THRESHOLD)) : Optional.empty();
		this.tcpListeningPort = config.hasPath(TCP_LISTENING_PORT) ? Optional.of(config.getInt(TCP_LISTENING_PORT)) : Optional.empty();
		this.tcpListeningUrl = config.hasPath(TCP_LISTENING_URL) ? Optional.of(config.getString(TCP_LISTENING_URL)) : Optional.empty();
	}

	public int port(){
		return this.wsPort;
	}
	
	public Optional<Integer> tcpListeningPort(){
		return this.tcpListeningPort;
	}

	public Optional<String> tcpListeningUrl(){
		return tcpListeningUrl;
	}
	
	public String wsPath(){
		return this.wsPath;
	}
	
	public Optional<Integer> numIoThreads(){
		return numIoThreads;
	}
	
	public Optional<Integer> numCoreThreads(){
		return numCoreThreads;
	}
	
	public Optional<Duration> requestTimeout(){
		return requestTimeout;
	}
	
	public Optional<Integer> bufferSizeInBytes(){
		return bufferSizeInBytes;
	}
	
	public Optional<Integer> numOutstandingBufferPerChannelThreshold(){
		return numOutstandingBufferPerChannelThreshold;
	}
	
	public static DashboardStandAloneWebServiceConfig of(int systemId, String service, Config config) {
		return new DashboardStandAloneWebServiceConfig(systemId, service, config);
	}
}
