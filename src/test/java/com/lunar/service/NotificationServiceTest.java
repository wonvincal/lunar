package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.NotificationServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.EventDecoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.util.AssertUtil;
import com.lunar.util.MutableDirectBufferEvent;
import com.lunar.util.ObjectCircularBuffer;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceTest {
	private static final Logger LOG = LogManager.getLogger(NotificationServiceTest.class);
	@Mock
	private ServiceStatusSender serviceStatusSender;
	@Mock
	private MessageSink adminSink;

	private ServiceTestHelper testHelper;
	private LunarServiceTestWrapper wrapper;

	// Test sender
	private final int selfSinkId = 8;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller;
	private RingBufferMessageSink selfRingBufferSink;
	private Messenger selfMessenger;
	private MessageSinkRef selfSinkRef;

	private MessageSinkRef admin;
	private final int adminSinkId = 1;
	
	private NotificationServiceConfig config;
	private final int systemId = 1;
	private final int serviceSinkId = 5;
	private final Duration broadcastFrequency = Duration.ofSeconds(1);
	private final int allEventBufferSize = 1024;
	private final int allEventBufferPurgeCountIfFull = 128; 
	private final int snapshotEventBufferSize = 256;
	private final int snapshotEventBufferPurgeCountIfFull = 32; 
	private final AtomicInteger clientKeySeq = new AtomicInteger(4000000);
	
	@Before
	public void setup(){
		testHelper = ServiceTestHelper.of();
		selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId, ServiceType.DashboardService, 256, "testDashboard");
		selfRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				LOG.debug("Received message in self sink");
				selfMessenger.receive(event, 0);
				return false;
			}
		});
		selfRingBufferSink = selfRingBufferSinkPoller.sink();
		selfMessenger = testHelper.createMessenger(selfRingBufferSink, "self");
		selfMessenger.registerEvents();
		selfSinkRef = selfMessenger.self();
		
		config = new NotificationServiceConfig(systemId, 
				"test-notify", 
				"test-notify", 
				"test-notify", 
				ServiceType.NotificationService, 
				Optional.empty(), 
				serviceSinkId, 
				Optional.of(1024), 
				Optional.of(1024), 
				Optional.empty(),
				Optional.empty(),
				true, 
				false, 
				Duration.ofSeconds(1),
				false,
				"",
				allEventBufferSize, 
				allEventBufferPurgeCountIfFull, 
				snapshotEventBufferSize, 
				snapshotEventBufferPurgeCountIfFull, 
				broadcastFrequency);
		
		adminSink = TestHelper.mock(adminSink, systemId, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");

		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return NotificationService.of(serviceConfig, messageService);
			}
		};

		SenderBuilder senderBuilder = new TestSenderBuilder();

		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(selfSinkRef);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStartStop() throws TimeoutException{
		TestHelper helper = TestHelper.of();

		MessageSinkRef self = DummyMessageSink.refOf(config.systemId(),
				config.sinkId(), 
				config.name(), 
				config.serviceType());
		
		TestSenderBuilder senderBuilder = new TestSenderBuilder();
		senderBuilder.serviceStatusSender(serviceStatusSender);
		Mockito.doNothing().when(serviceStatusSender).sendOwnServiceStatus(any(MessageSinkRef.class), any(ServiceStatusType.class), anyLong());

		ExecutorService executor = Executors.newCachedThreadPool();
		
		Disruptor<MutableDirectBuffer> disruptor =  new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				128,
				executor);
		LunarService messageService = LunarService.of(new ServiceBuilder() {
			
				@Override
				public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
					return NotificationService.of(serviceConfig, messageService);
				}
			}, 
			config, 
			helper.messageFactory(), 
			helper.createMessenger(self, senderBuilder),
            helper.systemClock());
		disruptor.handleExceptionsWith(new ServiceFactory.GeneralDisruptorExceptionHandler(config.name()));
		disruptor.handleEventsWith(messageService);

		MessageServiceExecutionContext context = MessageServiceExecutionContext.of(
				messageService, 
				disruptor, 
				Optional.of(executor),
				Duration.ofMillis(1000),
				helper.realTimerService());
		assertEquals(false, context.isStopped());
		context.start();
		AssertUtil.assertTrueWithinPeriod("Service cannot get past IDLE state", () -> {
			return messageService.state() != States.IDLE;
		}, TimeUnit.SECONDS.toNanos(2l));
		context.shutdown();
		assertEquals(true, context.isStopped());
		assertTrue(executor.isShutdown());
	}
	
	@Test
	public void givenCreatedWhenIdleStartsHandlingServiceStatus(){
		// Given
		
		// When
		wrapper.messageService().onStart();
		
		// Then
		assertEquals(States.WAITING_FOR_SERVICES, wrapper.messageService().state());
		ServiceStatusTracker tracker = wrapper.messageService().messenger().serviceStatusTracker();
		assertTrue(tracker.isTrackingService(ServiceType.AdminService));
	}

	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToActiveState(){
		advanceToActiveState(wrapper, selfMessenger);
	}
	
	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger) {
		wrapper.messageService().onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES, wrapper.messageService().state());
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						admin.sinkId(), 
						admin.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		// Verify
		assertEquals(States.ACTIVE, wrapper.messageService().state());
	}
	
	@Test
	public void givenActiveWhenReceiveEventThenStoreEvent() throws Exception{
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		long timeNow = LocalTime.now().toNanoOfDay();
		String description = "what the...";
		sendEvent(5, EventCategory.CORE, EventLevel.INFO, EventType.NULL_VAL, timeNow, description);
		
		// Then
		NotificationService service = (NotificationService)wrapper.coreService();
		ObjectCircularBuffer<MutableDirectBufferEvent> allEventBuffer = service.allEventBuffer();
		EventSbeDecoder decoder = new EventSbeDecoder();
		int eventCount = allEventBuffer.peekTillEmpty(new EventHandler<MutableDirectBufferEvent>() {
			@Override
			public void onEvent(MutableDirectBufferEvent event, long sequence, boolean endOfBatch) throws Exception {
				decoder.wrap(event.buffer(), 0, EventSbeDecoder.BLOCK_LENGTH, EventSbeDecoder.SCHEMA_VERSION);
				assertEquals(EventCategory.CORE, decoder.category());
				assertEquals(EventLevel.INFO, decoder.level());
				assertEquals(timeNow, decoder.time());
				assertEquals(0, decoder.eventValues().count());
				assertEquals(0, description.compareTo(decoder.description()));
			}
		});
		assertEquals(1, eventCount);
	}
	
	private static class EventItem {
		private EventCategory category;
		private EventLevel level;
		private EventType eventType;
		private long time;
		private String description;
		static EventItem of(EventCategory category, EventLevel level, EventType eventType, long time, String description){
			return new EventItem(category, level, eventType, time, description);
		}
		EventItem (EventCategory category, EventLevel level, EventType eventType, long time, String description){
			this.category = category;
			this.level = level;
			this.eventType = eventType;
			this.time = time;
			this.description = description;
		}
	}
	
	@Test
	public void givenActiveAndEventStoredWhenReceiveGetRequestThenReturnStoredEvents(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		long timeNow = LocalTime.now().toNanoOfDay();
		EventItem[] items = new EventItem[2];
		items[0] = EventItem.of(EventCategory.CORE, EventLevel.CRITICAL, EventType.NULL_VAL, timeNow, "what the...");
		items[1] = EventItem.of(EventCategory.CORE, EventLevel.INFO, EventType.NULL_VAL, timeNow + 1, "ok now");
		Arrays.stream(items).forEach(i -> sendEvent(5, i));
	
		AtomicInteger expectedResponseCount = new AtomicInteger(3);
		AtomicInteger eventCount = new AtomicInteger(0);
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				expectedResponseCount.decrementAndGet();
				LOG.debug("Received response");
			}
		});
		
		selfMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				LOG.debug("Received event {}", EventDecoder.decodeToString(codec));
				assertEquals(items[eventCount.get()].category, codec.category());
				assertEquals(items[eventCount.get()].level, codec.level());
				assertEquals(items[eventCount.get()].time, codec.time());
				int count = codec.eventValues().count();
				assertEquals(items[eventCount.get()].description, codec.description());
				eventCount.getAndIncrement();
			}
		});
		
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		Request request = Request.of(wrapper.sink().sinkId(), 
				RequestType.GET, 
				Parameters.listOf(
						Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_EVENT.value())))
				.clientKey(clientKey);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), request);
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
				LOG.debug("Received response for GET request");
			}
		});
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();

		assertTrue("Haven't received response for GET request", received.get());
		assertEquals(0, expectedResponseCount.get());
		assertEquals(2, eventCount.get());
	}
	
	private void sendEvent(int sinkId, EventItem item){
		sendEvent(sinkId, item.category, item.level, item.eventType, item.time, item.description);
	}
	
	private void sendThrottledEvent(int sinkId, long secSid, EventItem item){
		selfMessenger.trySendEventWithTwoValues(item.category, item.level, item.eventType, sinkId, item.time, item.description, EventValueType.ORDER_SID, 10000000l, EventValueType.SECURITY_SID, secSid);	
	}
	
	private void sendEvent(int sinkId, EventCategory category, EventLevel level, EventType eventType, long eventTime, String description){
		selfMessenger.eventSender().sendEvent(wrapper.sink(),
				category, 
				level,
				eventType,
				sinkId,
				eventTime, 
				description);
		
		wrapper.pushNextMessage();
	}
	
	@Test
	public void givenActiveWhenSendSubscriptionRequestThenReturnOK(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When and then
		sendSubscriptionRequest();
	}
	
	private void sendSubscriptionRequest(){
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		Request request = Request.of(wrapper.sink().sinkId(), 
				RequestType.SUBSCRIBE, 
				Parameters.listOf(
						Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_EVENT.value())))
				.clientKey(clientKey);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), request);
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
				LOG.debug("Received response for SUBSCRIBE request");
			}
		});
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();

		assertTrue("Haven't received response for SUBSCRIBE request", received.get());		
	}
	
	@Test
	public void givenActiveAndSubscribedWhenReceiveEventThenPublishAfterSomeTime(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendSubscriptionRequest();
		
		long timeNow = LocalTime.now().toNanoOfDay();
		EventItem[] items = new EventItem[2];
		items[0] = EventItem.of(EventCategory.CORE, EventLevel.CRITICAL, EventType.NULL_VAL, timeNow, "what the...");
		items[1] = EventItem.of(EventCategory.CORE, EventLevel.INFO, EventType.NULL_VAL, timeNow + 1, "ok now");

		AtomicInteger responseCount = new AtomicInteger(0);
		AtomicInteger eventCount = new AtomicInteger(0);
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				responseCount.incrementAndGet();
				LOG.debug("Received response");
			}
		});
		
		selfMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				assertEquals(items[eventCount.get()].category, codec.category());
				assertEquals(items[eventCount.get()].level, codec.level());
				assertEquals(items[eventCount.get()].time, codec.time());
				codec.eventValues().count();
				assertEquals(items[eventCount.get()].description, codec.description());
				eventCount.getAndIncrement();
			}
		});

		Arrays.stream(items).forEach(i -> sendEvent(5, i));
		
		// When
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();

		// Then
		assertEquals(0, responseCount.get());
		assertEquals(2, eventCount.get());
	}

	@Test
	public void givenActiveAndSubscribedWhenReceiveEventThatCanBeThrottledThenPublishOnlyOneEvent(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendSubscriptionRequest();
		
		long timeNow = LocalTime.now().toNanoOfDay();
		EventItem[] items = new EventItem[3];
		items[0] = EventItem.of(EventCategory.EXECUTION, EventLevel.INFO, EventType.THROTTLED, timeNow, "what the...");
		items[1] = EventItem.of(EventCategory.EXECUTION, EventLevel.WARNING, EventType.THROTTLED, timeNow + 1, "within threshold");
		items[2] = EventItem.of(EventCategory.EXECUTION, EventLevel.WARNING, EventType.THROTTLED, timeNow + 150_000_001, "expect this");

		AtomicInteger responseCount = new AtomicInteger(0);
		AtomicInteger eventCount = new AtomicInteger(0);
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				responseCount.incrementAndGet();
				LOG.debug("Received response");
			}
		});
		
		selfMessenger.receiver().eventHandlerList().add(new Handler<EventSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
				LOG.debug("Received [category:{}, level:{}, type:{}, time:{}, numValues:{}, description:{}]", 
						codec.category(),
						codec.level(),
						codec.eventType(),
						codec.time(),
						codec.eventValues().count(),
						codec.description());
//				assertEquals(items[eventCount.get()].category, codec.category());
//				assertEquals(items[eventCount.get()].level, codec.level());
//				assertEquals(items[eventCount.get()].time, codec.time());
//				assertEquals(items[eventCount.get()].description, codec.description());
				eventCount.getAndIncrement();
			}
		});

		Arrays.stream(items).forEach(i -> sendThrottledEvent(5, 200000400l, i));
		
		// When
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();

		// Then
		assertEquals(0, responseCount.get());
		assertEquals(2, eventCount.get());
	}

}
