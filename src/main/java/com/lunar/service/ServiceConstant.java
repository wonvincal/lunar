package com.lunar.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;

public final class ServiceConstant {
	public static boolean LOAD_JNI = true;
	public static final String MATCHING_ENGINE_DELAY_NANO_PROP_NAME = "lunar.matching.engine.delay.ns"; 
	public static final String DISABLE_BOUNDS_CHECKS_PROP_NAME = "lunar.disable.bounds.checks";
	public static final String DISABLE_SAFETY_CHECKS_PROP_NAME = "lunar.disable.safety.checks";
	public static final String DISABLE_MULTITHREAD_ISSUE_CHECKS_PROP_NAME = "lunar.disable.multithread.issue.checks";
	public static final String DISABLE_ORDER_VALIDATION = "lunar.disable.order.validation";
    public static final boolean SHOULD_BOUNDS_CHECK = !Boolean.getBoolean(DISABLE_BOUNDS_CHECKS_PROP_NAME);
    public static final boolean SHOULD_MULTITHREAD_ISSUE_CHECK = !Boolean.getBoolean(DISABLE_MULTITHREAD_ISSUE_CHECKS_PROP_NAME);
    public static final boolean SHOULD_SAFETY_CHECK = !Boolean.getBoolean(DISABLE_SAFETY_CHECKS_PROP_NAME);
    public static final boolean SHOULD_ORDER_VALIDATION = !Boolean.getBoolean(DISABLE_ORDER_VALIDATION);
    
    // Temporary check
    public static final String ENABLE_OCG_API_ENABLE_DISCONNECTION_CHECK = "lunar.enable.ocg.disconnection.check";
    public static final boolean SHOULD_OCG_API_DISCONNECTION_CHECK = Boolean.getBoolean(ENABLE_OCG_API_ENABLE_DISCONNECTION_CHECK);
    
    // Temporary check
    public static final String DISABLE_OCG_API_RECOVERY = "lunar.disable.ocg.recovery";
    public static final boolean SHOULD_OCG_PERFORM_RECOVERY = !Boolean.getBoolean(DISABLE_OCG_API_RECOVERY);

    public static final int INVALID_BID = -1;
	public static final int INVALID_ASK = -1;
	public static final int SERVICE_ID_NOT_APPLICABLE = -1;
	public static final int NULL_MSG_KEY = -1;
	public static final int NULL_CLIENT_MSG_KEY = -1;
	public static final int NULL_CLIENT_ORDER_REQUEST_ID = -1;
	public static final int NULL_ORDER_SID = -1;
	public static final int NULL_TRADE_SID = -1;
	public static final long NULL_ENTITY_SID = -1;
	public static final long NULL_NOTE_SID = -1;
	public static final int NULL_SEQUENCE = -1;
	public static final long NULL_SEC_SID = -1;
	public static final int NULL_INT_DATE = -1;
	public static final long NULL_TIME_NS = OrderSbeDecoder.createTimeNullValue();
	public static final int NULL_CHART_PRICE = ChartDataSbeDecoder.openNullValue();
	public static final int SERVICE_ID_RANDOM_SEED = 10000;
	public static final String ACTOR_NAME_SUFFIX = "-actor";
	public static final String DISRUPTOR_THREAD_NAME_SUFFIX = "-disruptor";
	public static final int DEFAULT_CODEC_HANDLER_LIST_CAPACITY = 2; // move to config
	public static final int DEFAULT_REF_DATA_SUBSCRIBERS_CAPACITY = 5; // move to config
	public static final int DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY = 5; // move to config
	public static final int DEFAULT_RECEIVER_BUFFER_SIZE = 128;
	public static final int DEFAULT_STOP_TIMEOUT_IN_SECOND = 2;
	public static final int DEFAULT_ENTITY_MANAGER_SIZE = 1024;
	public static final int MAX_MESSAGE_SIZE = 1500;
	public static final int MAX_STRING_SIZE = 25;
	public static final int MAX_NOTE_SIZE = 512;
	public static final int MAX_TEMPLATE_ID = 128;
	public static final int DEFAULT_FRAGMENT_SIZE = 64;
	public static final byte SCHEMA_VERSION_NOT_APPLICABLE = 0;
	public static final int LUNAR_SERVICE_SHUTDOWN_POLL_FREQ_IN_MS = 100;
	public static final long SERVICE_SHUTDOWN_TIMEOUT_IN_NS = TimeUnit.SECONDS.toNanos(5l);
	public static final long SERVICE_SHUTDOWN_STATUS_POLL_IN_NS = TimeUnit.MILLISECONDS.toNanos(100);
	public static final long SERVICE_EXECUTOR_SHUTDOWN_TIMEOUT_IN_NS = TimeUnit.SECONDS.toNanos(1l);
	public static final String PRIMARY_EXCHANGE = "SEHK";
	public static final int MAX_MARKETDATA_QUEUE_SIZE = 100;
	public static final int MAX_SUBSCRIBERS = 16;
	public static final int DEFAULT_MAX_NUM_SINKS = 16;
	public static final double INT_PRICE_SCALING_FACTOR = 0.001;
	public static final int START_RESPONSE_SEQUENCE = 1;
	public static final int START_ORDER_SID_SEQUENCE = 7000000;
	public static final int START_TRADE_SID_SEQUENCE = 8000000;
	public static final long MATCHING_ENGINE_DELAY_NANO = Long.getLong(MATCHING_ENGINE_DELAY_NANO_PROP_NAME, 0L); 
    public static final long WEIGHTED_AVERAGE_SCALE = 1_000L;
    public static final int DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX = 0;
    public static final int DEFAULT_SCORE_BOARD_SCHEMA_ID = 1;
    public static final LocalDate NULL_LISTED_DATE = LocalDate.of(2012, 1, 1);
    public static final Duration DEFAULT_ORDER_FLOW_CLOCK_TICK_FREQ = Duration.ofSeconds(1);
    public static final long DEFAULT_ORDER_FLOW_CLOCK_TICK_FREQ_IN_NS = DEFAULT_ORDER_FLOW_CLOCK_TICK_FREQ.toNanos();
	/*
	 * Must be a power of two
	 */
	public static final int NUM_ORDER_AND_TRADE_SUBSCRIPTION_CHANNELS = 128;
	public static final int ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY = 1024;
	public static final int ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY = 4096;
	public static final long WARMUP_SECURITY_SID = 0;
	public static final long START_CHANNEL_SEQ = 1;
	public static final int DEAD_LETTERS_SINK_ID = 0;
	public static final int TRADE_BUY = 1;
	public static final int TRADE_SELL = -1;
	
	public static final String HSI_CODE = "HSI";
	public static final String HSCEI_CODE = "HSCEI";
	

}
