package com.lunar.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import org.agrona.MutableDirectBuffer;

import com.lmax.disruptor.RingBuffer;
import com.lunar.config.AdminServiceConfig;
import com.lunar.config.CommunicationConfig;
import com.lunar.config.MessagingConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.config.SystemConfig;
import com.lunar.config.TimerServiceConfig;
import com.lunar.core.GrowthFunction;
import com.lunar.core.StringConstant;
import com.lunar.core.SystemClock;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.core.UserControlledTimerService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.message.MessageFactory;
import com.lunar.message.RequestTypeSetting;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.MessageSenderBuilder;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.message.sink.NormalMessageSinkRefMgr;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.message.sink.TestMessageSinkBuilder;
import com.lunar.service.ServiceBuilder;
import com.lunar.service.ServiceFactory;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

public class ServiceTestHelper {
	private final int frameSize = 1024;
	private final Duration commandTimeout = Duration.ofMillis(5000);
	private final int numServiceInstances = 16;
	private final int systemId = 1;
	private final SystemConfig systemConfig;
	private EnumMap<RequestType, RequestTypeSetting> reqTypeSettings; 
	private MessagingConfig messagingConfig;
	private MessageFactory messageFactory;
	private UserControlledTimerService timerService;
	private MessageSinkRefMgr messageSinkRefMgr;
	private SystemClock systemClock;
	
	public static ServiceTestHelper of(){
		return new ServiceTestHelper();
	}
	
	public ServiceTestHelper(){
		final Duration requestTimeout = Duration.ofSeconds(5l);
		final Duration requestInitRetryDelay = Duration.ofSeconds(4l);
		final int requestMaxRetryAttempts = 0;
		final GrowthFunction requestRetryGrowth = GrowthFunction.CONSTANT;

		reqTypeSettings = new EnumMap<>(RequestType.class);
		for (RequestType requestType : RequestType.values()){
			reqTypeSettings.put(requestType, RequestTypeSetting.of(requestTimeout, requestInitRetryDelay, requestMaxRetryAttempts, requestRetryGrowth));
		}
		messagingConfig = MessagingConfig.of(frameSize, commandTimeout, reqTypeSettings);
		messageFactory = new MessageFactory(messagingConfig);
        systemClock = new UserControlledSystemClock();
		timerService = new UserControlledTimerService(LocalTime.now().toNanoOfDay(),
				LocalDateTime.now(),
				messageFactory.createStandaloneTimerEventSender());
		messageSinkRefMgr = new NormalMessageSinkRefMgr(systemId,
				numServiceInstances,
				timerService);
		String journalArchiveFolder = StringConstant.EMPTY;
		String journalFolder = StringConstant.EMPTY;
		String loggingArchiveFolder = StringConstant.EMPTY;
		String loggingFolder = StringConstant.EMPTY;
		boolean loggingArchiveOnStart = false;
		Int2ObjectArrayMap<CommunicationConfig> remoteCommunicationConfigs = null;
		CommunicationConfig communicationConfig = null;
		List<ServiceConfig> serviceConfigs = null;
		AdminServiceConfig adminServiceConfig = null;
		TimerServiceConfig tsConfig = null;
		MessagingConfig msgConfig = null;
		boolean isJournalRequired = false;
		int port = 8118;
		String host = "localhost";
		String name = "test-system";
		systemConfig = SystemConfig.of(name, host, port, msgConfig, tsConfig, requestMaxRetryAttempts, adminServiceConfig, serviceConfigs, communicationConfig, remoteCommunicationConfigs, loggingArchiveOnStart, loggingFolder, loggingArchiveFolder, isJournalRequired, journalFolder, journalArchiveFolder);
	}
	
	public UserControlledTimerService timerService(){
		return timerService;
	}
	
	public MessageSinkRefMgr refMgr(){
		return messageSinkRefMgr;
	}
	
	public Messenger createMessenger(MessageSink sinkMock, String name){
		// create and register message sink
		MessageSinkRef sinkRef = MessageSinkRef.of(sinkMock, name);
		MessageSender messageSender = MessageSender.of(messageFactory.frameSize(), sinkRef);
		return Messenger.of(messageSinkRefMgr, 
				sinkRef, 
				timerService, 
				messageFactory,
				messageSender,
				MessageReceiver.of(),
				MessageSenderBuilder.of());
	}

	public LunarServiceTestWrapper createService(ServiceConfig config, ServiceBuilder builder, SenderBuilder senderBuilder){
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(messageFactory.eventFactory(), config.queueSize().orElseThrow(IllegalArgumentException::new));
		LunarServiceTestWrapper wrapper = new LunarServiceTestWrapper(ServiceFactory.create(config, timerService, systemClock, messageSinkRefMgr, messageFactory, ringBuffer, builder, senderBuilder),
				ringBuffer);
		return wrapper;
	}

	public LunarServiceTestWrapper createService(ServiceConfig config, ServiceBuilder builder){
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(messageFactory.eventFactory(), config.queueSize().orElseThrow(IllegalArgumentException::new));
		LunarServiceTestWrapper wrapper = new LunarServiceTestWrapper(ServiceFactory.create(config, timerService, systemClock, messageSinkRefMgr, messageFactory, ringBuffer, builder, Optional.empty()),
				ringBuffer);
		return wrapper;
	}

	public LunarServiceTestWrapper createService(ServiceConfig config){
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(messageFactory.eventFactory(), config.queueSize().orElseThrow(IllegalArgumentException::new));
		LunarServiceTestWrapper wrapper = new LunarServiceTestWrapper(ServiceFactory.create(systemConfig, config, timerService, systemClock, messageSinkRefMgr, messageFactory, ringBuffer, Optional.empty()),
				ringBuffer);
		return wrapper;
	}
	
	public RingBufferMessageSinkPoller createRingBufferMessageSinkPoller(int sinkId, ServiceType serviceType, int size, String name){
		RingBuffer<MutableDirectBuffer> ringBuffer = RingBuffer.createMultiProducer(messageFactory.eventFactory(), size);
		return TestMessageSinkBuilder.createRingBufferMessageSinkPoller(systemId, sinkId, serviceType, name, ringBuffer);
	}
}
