package com.lunar.perf;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Optional;

import org.agrona.MutableDirectBuffer;

import com.lmax.disruptor.RingBuffer;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.MessagingConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.GrowthFunction;
import com.lunar.core.RealSystemClock;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.core.UserControlledTimerService;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.message.MessageFactory;
import com.lunar.message.RequestTypeSetting;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.message.sink.NormalMessageSinkRefMgr;
import com.lunar.perf.latency.LatencyTestContext;
import com.lunar.perf.throughput.ThroughputTestContext;
import com.lunar.service.HashedWheelTimerService;
import com.lunar.service.HeadNodeService;
import com.lunar.service.NodeService;
import com.lunar.service.PoliceService;
import com.lunar.service.RobberService;
import com.lunar.service.ServiceBuilder;
import com.lunar.service.ServiceLifecycleAware;

public class PerformanceTestHelper {
	private static final Duration TICK_DURATION = Duration.ofMillis(100);
	private static final int NUM_TICKS_PER_WHEEL = 256;
	@SuppressWarnings("unused")
	private static final String DUMMY_AERON_DIR = "/tmp";
	@SuppressWarnings("unused")
	private static final String DUMMY_AERON_CHANNEL = "dummy-aeron-channel";
	private static final ServiceType NODE_SERVICE_TYPE = ServiceType.EchoService;
	private MessagingConfig messagingConfig;
	private MessageFactory messageFactory;
	private TimerService timerService;
	private MessageSinkRefMgr referenceMgr;
	
	public PerformanceTestHelper(int frameSize, boolean useRealTimerService, int numServices){
		final Duration requestTimeout = Duration.ofSeconds(5l);
		final Duration requestInitRetryDelay = Duration.ofSeconds(4l);
		final int requestMaxRetryAttempts = 0;
		final GrowthFunction requestRetryGrowth = GrowthFunction.CONSTANT;

		EnumMap<RequestType, RequestTypeSetting> reqTypeSettings = new EnumMap<>(RequestType.class);
		for (RequestType requestType : RequestType.values()){
			reqTypeSettings.put(requestType, RequestTypeSetting.of(requestTimeout, requestInitRetryDelay, requestMaxRetryAttempts, requestRetryGrowth));
		}

		// Create a default message factory
		messagingConfig = MessagingConfig.of(frameSize, 
				Duration.ofSeconds(5), 
				reqTypeSettings);
		messageFactory = new MessageFactory(messagingConfig);
		
		if (useRealTimerService){
			timerService = new HashedWheelTimerService(new NamedThreadFactory("perf-test", "perf-test-timer"),
					TICK_DURATION,
					NUM_TICKS_PER_WHEEL,
					messageFactory.createStandaloneTimerEventSender());
		}
		else{
			timerService = new UserControlledTimerService(System.nanoTime(), 
					LocalDateTime.now(),
					messageFactory.createStandaloneTimerEventSender()); 
		}
		
		referenceMgr = new NormalMessageSinkRefMgr(1,
				numServices,
				timerService);
	}
	
	public MessageFactory messageFactory(){
		return messageFactory;
	}
	
	public LunarService createThroughputMessageService(int sinkId, 
			int messageQueueSize, 
			boolean useBlockingMessageQueue, 
			boolean isRobberNode,
			RingBuffer<MutableDirectBuffer> ringBuffer,
			ThroughputTestContext context){
		String service = (isRobberNode) ? "robber" : "police";
		ServiceConfig serviceConfig = new ServiceConfig(1,
				service, 
				service, 
				service, 
				NODE_SERVICE_TYPE,
				Optional.empty(), 
				sinkId, 
				Optional.of(messageQueueSize),
				Optional.of(100),
				Optional.empty(), 
				Optional.empty(),
				true,
				true, 
				Duration.ofSeconds(5),
				false,
				"");

		if (useBlockingMessageQueue){
		}
		// create a ring buffer
		Messenger messenger = Messenger.create(referenceMgr, 
				timerService, 
				sinkId, 
				NODE_SERVICE_TYPE, 
				this.messageFactory,
				"perf-test-messenger-" + sinkId,
				ringBuffer);

		ServiceBuilder builder = null;
		if (isRobberNode){
			builder = new ServiceBuilder() {
				@Override
				public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
					return new RobberService(serviceConfig, messageService, context);
				}
			};
		}
		else{
			builder = new ServiceBuilder() {
				@Override
				public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
					return new PoliceService(serviceConfig, messageService, context);
				}
			};
		}
		
		SystemClock systemClock = new RealSystemClock();
		
		LunarService lunarService = LunarService.of(builder, 
				serviceConfig, 
				messageFactory, 
				messenger,
				systemClock);
		
		return lunarService;
	}

	public LunarService createMessageService(int sinkId, 
			int messageQueueSize, 
			boolean useBlockingMessageQueue, 
			boolean isHeadNode,
			RingBuffer<MutableDirectBuffer> ringBuffer,
			LatencyTestContext context){
		String service = (isHeadNode) ? "head" : "forwarder";
		ServiceConfig serviceConfig = new ServiceConfig(1, 
				service, 
				service, 
				service, 
				NODE_SERVICE_TYPE,
				Optional.empty(), 
				sinkId, 
				Optional.of(messageQueueSize),
				Optional.of(100),
				Optional.empty(), 
				Optional.empty(),
				true,
				true, 
				Duration.ofSeconds(5),
				false,
				"");

		if (useBlockingMessageQueue){
		}
		// create a ring buffer
		Messenger messenger = Messenger.create(referenceMgr, 
				timerService, 
				sinkId, 
				NODE_SERVICE_TYPE, 
				this.messageFactory,
				"perf-test-messenger-" + sinkId,
				ringBuffer);

		ServiceBuilder builder = null;
		if (isHeadNode){
			builder = new ServiceBuilder() {
				@Override
				public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
					return new HeadNodeService(serviceConfig, messageService, context);
				}
			};
		}
		else{
			builder = new ServiceBuilder() {
				@Override
				public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
					return new NodeService(serviceConfig, messageService, context);
				}
			};
		}

		SystemClock systemClock = new RealSystemClock();

		LunarService lunarService = LunarService.of(builder, 
				serviceConfig, 
				messageFactory, 
				messenger,
				systemClock);
		
		return lunarService;
	}
}
