package com.lunar.util;

import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.Mockito;

import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.CommunicationConfig;
import com.lunar.config.MessagingConfig;
import com.lunar.core.GrowthFunction;
import com.lunar.core.SystemClock;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.core.UserControlledTimerService;
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
import com.lunar.message.sink.TestMessageSinkRefMgr;
import com.lunar.service.HashedWheelTimerService;
import com.lunar.service.ServiceConstant;

public final class TestHelper {
	private static final Logger LOG = LogManager.getLogger(TestHelper.class);

	private static final int DEFAULT_FRAME_SIZE = 128;
	private String host = "127.0.0.1";
	private String aeronDir = "/lunar/aeron/";
	private String aeronTransport = "udp";
	private int aeronPort = 5512;
	private int aeronStream = 2;

	private Duration requestTimeout = Duration.ofSeconds(5l);
	private Duration requestInitRetryDelay = Duration.ofSeconds(4l);
	private int requestMaxRetryAttempts = 0;
	private GrowthFunction requestRetryGrowth = GrowthFunction.CONSTANT;
	private Duration commandTimeout = Duration.ofSeconds(2l); 
	private SystemClock systemClock = new UserControlledSystemClock();

	private MessageFactory messageFactory;

	MessageSinkRefMgr messageSinkRefMgr;
	
	private UserControlledTimerService timerService;
	
	private HashedWheelTimerService realTimerService; 
	
	/**
	 * 
	 * @param actorSystem please mock this
	 * @param config please use concrete class
	 * @param self please mock this
	 * @return
	 */
	public static TestHelper of(){
		return new TestHelper(Collections.emptyMap(), DEFAULT_FRAME_SIZE);
	}
	
	public static TestHelper of(int frameSize){
		return new TestHelper(Collections.emptyMap(), frameSize);
	}

	public static TestHelper of(MessageSink... sinks){
		Map<Integer, MessageSink> map = null;
		if (sinks.length <= 0){
			map = Collections.emptyMap();
		}
		else{
			map = new HashMap<Integer, MessageSink>(sinks.length);
			for (int i = 0; i < sinks.length; i++){
				map.put(sinks[i].sinkId(), sinks[i]);
			}
		}
		return new TestHelper(map, DEFAULT_FRAME_SIZE);
	}

	private TestHelper(Map<Integer, MessageSink> refs, int frameSize){
		// create message factory
		EnumMap<RequestType, RequestTypeSetting> reqTypeSettings = new EnumMap<RequestType, RequestTypeSetting>(RequestType.class);
		for (RequestType requestType : RequestType.values()){
			reqTypeSettings.put(requestType, RequestTypeSetting.of(requestTimeout, requestInitRetryDelay, requestMaxRetryAttempts, requestRetryGrowth));
		}

		MessagingConfig messagingConfig = MessagingConfig.of(frameSize, commandTimeout, reqTypeSettings); 
		messageFactory = new MessageFactory(messagingConfig);

		timerService = new UserControlledTimerService(System.nanoTime(), 
				LocalDateTime.now(),
				messageFactory.createStandaloneTimerEventSender());

		realTimerService = new HashedWheelTimerService(new NamedThreadFactory("perf-test", "perf-test-timer"),
				Duration.ofMillis(100),
				256,
				messageFactory.createStandaloneTimerEventSender());

		messageSinkRefMgr = new TestMessageSinkRefMgr(1,
				128, 
				aeronDir, 
				CommunicationConfig.createChannelStr(aeronTransport, host, aeronPort), 
				aeronStream, 
				0, refs, timerService);
		LOG.debug("Created message sink reference manager [id:{}]", messageSinkRefMgr.id());
	}
	
	public MessageSinkRef register(MessageSink sink, String mnem){
		return messageSinkRefMgr.register(MessageSinkRef.of(sink, mnem));
	}
	
	public SystemClock systemClock() {
		return systemClock;
	}

	public UserControlledTimerService timerService(){
		return timerService;
	}

	public HashedWheelTimerService realTimerService(){
		return realTimerService;
	}

	public MessageSinkRefMgr messageSinkRefMgr(){
		return messageSinkRefMgr;
	}

	public MessageFactory messageFactory(){
		return messageFactory;
	}
	
	public Messenger createMessenger(MessageSinkRef self){
		MessageSender sender = MessageSender.of(messageFactory.frameSize(), self);
		return createMessenger(self, 
				sender, 
				MessageSenderBuilder.of());
	}
	
	public Messenger createMessenger(MessageSinkRef self, SenderBuilder senderBuilder){
		return createMessenger(self, MessageSender.of(messageFactory.frameSize(), self), senderBuilder);
	}
	private Messenger createMessenger(MessageSinkRef self, MessageSender sender, SenderBuilder senderBuilder){
		Messenger messenger = Messenger.of(messageSinkRefMgr,
				self,
				timerService,
				messageFactory,
				sender,
				MessageReceiver.of(),
				senderBuilder);
		return messenger;
	}
	
	public static MessageSink mock(MessageSink sink, int sinkId, ServiceType serviceType){
		when(sink.sinkId()).thenReturn(sinkId);
		when(sink.serviceType()).thenReturn(serviceType);
		when(sink.tryClaim(Mockito.anyInt(), Mockito.anyObject())).thenReturn(MessageSink.INSUFFICIENT_SPACE);
		when(sink.toString()).thenReturn("[name:mock-" + serviceType.name() + ", sinkId:" + sinkId + "]");
		return sink;
	}
	
	public static MessageSink mock(MessageSink sink, int systemId, int sinkId, ServiceType serviceType){
		when(sink.systemId()).thenReturn(systemId);
		when(sink.sinkId()).thenReturn(sinkId);
		when(sink.serviceType()).thenReturn(serviceType);
		when(sink.tryClaim(Mockito.anyInt(), Mockito.anyObject())).thenReturn(MessageSink.INSUFFICIENT_SPACE);
		when(sink.toString()).thenReturn("[systemId:" + systemId + ", name:mock-" + serviceType.name() + ", sinkId:" + sinkId + "]");
		return sink;
	}

	public static Map<Integer, MessageSink> mapOf(MessageSink... sink){
		if (sink.length <= 0){
			return Collections.emptyMap();
		}
		Map<Integer, MessageSink> map = new HashMap<Integer, MessageSink>(sink.length);
		for (int i = 0; i < sink.length; i++){
			map.put(sink[i].sinkId(), sink[i]);
		}
		return map;
	}
	
	public MutableDirectBuffer createDirectBuffer(){
		return new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
	}
}
