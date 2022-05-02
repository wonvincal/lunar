package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.math.DoubleMath;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.LineHandlerConfig;
import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.config.PortfolioAndRiskServiceConfig;
import com.lunar.config.PortfolioAndRiskServiceConfig.RiskControlConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.core.WaitStrategy;
import com.lunar.entity.Security;
import com.lunar.exception.ConfigurationException;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.MutableHandler;
import com.lunar.message.binary.PositionDecoder;
import com.lunar.message.binary.RiskStateDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.order.Boobs;
import com.lunar.order.Order;
import com.lunar.order.OrderUtil;
import com.lunar.order.Trade;
import com.lunar.order.TradeUtil;
import com.lunar.position.AggregatedSecurityPosition;
import com.lunar.position.PositionChangeTracker;
import com.lunar.position.PositionDetails;
import com.lunar.position.SecurityPosition;
import com.lunar.position.SecurityPositionDetails;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

import it.unimi.dsi.fastutil.ints.IntArrayList;

@RunWith(MockitoJUnitRunner.class)
public class PortfolioAndRiskServiceTest {
	private static final Logger LOG = LogManager.getLogger(PortfolioAndRiskServiceTest.class);

	@Mock
	private ServiceStatusSender serviceStatusSender;
	@Mock
	private MessageSink adminSink;
	@Mock
	private MessageSink rdsSink;
	@Mock
	private MessageSink mdSnapshotSink;
	@Mock
	private MessageSink otSnapshotSink;
	@Mock
	private MessageSink persistSink;
	@Mock
	private RequestTracker requestTracker;

	private MessageSinkRef admin;
	private MessageSinkRef rds;
	private MessageSinkRef mdSnapshot;
	private MessageSinkRef otSnapshot;
	private MessageSinkRef persist;
	private final int adminSinkId = 1;
	private final int rdsSinkId = 2;
	private final int mdSnapshotSinkId = 3;
	private final int orderSnapshotSinkId = 4;
	private final int persistSinkId = 7;
	private ServiceTestHelper testHelper;
	private LunarServiceTestWrapper wrapper;

	// Test sender
	private final int selfSinkId = 8;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller;
	private RingBufferMessageSink selfRingBufferSink;
	private Messenger selfMessenger;
	private MessageSinkRef selfSinkRef;

	private PortfolioAndRiskServiceConfig config;
	private final int systemId = 1;
	private final int serviceSinkId = 5;
	private final Duration broadcastFrequency = Duration.ofSeconds(1);
	private final AtomicInteger clientKeySeq = new AtomicInteger(4000000);
	private RiskControlConfig defaultSecurityConfig;
	private RiskControlConfig defaultIssuerConfig;
	private RiskControlConfig defaultUndConfig;
	private RiskControlConfig defaultFirmConfig;
	private MutableHandler<ResponseSbeDecoder> mutableResponseHandler;
	
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
		
		long maxOpenPosition = 10000;
		double maxProfit = 30000;
		double maxLoss = -30000;
		double maxCapUsed = 30000;
		defaultSecurityConfig = PortfolioAndRiskServiceConfig.RiskControlConfig.of(Optional.of(maxOpenPosition), 
				Optional.of(maxProfit), 
				Optional.of(maxLoss),
				Optional.of(maxCapUsed));
		
		long issuerMaxOpenPosition = 20000;
		double issuerMaxProfit = 200000;
		double issuerMaxLoss = -200000;
		double issuerMaxCapUsed = 200000;
		defaultIssuerConfig = PortfolioAndRiskServiceConfig.RiskControlConfig.of(Optional.of(issuerMaxOpenPosition), 
				Optional.of(issuerMaxProfit), 
				Optional.of(issuerMaxLoss),
				Optional.of(issuerMaxCapUsed)); 
		
		long undMaxOpenPosition = 30000;
		double undMaxProfit = 300000;
		double undMaxLoss = -300000;		
		double undMaxCapUsed = 300000;
		defaultUndConfig = PortfolioAndRiskServiceConfig.RiskControlConfig.of(Optional.of(undMaxOpenPosition), 
				Optional.of(undMaxProfit), 
				Optional.of(undMaxLoss),
				Optional.of(undMaxCapUsed));

		long firmMaxOpenPosition = 50000;
		double firmMaxProfit = 500000;
		double firmMaxLoss = -500000;
		double firmMaxCapUsed = 500000;
		defaultFirmConfig = PortfolioAndRiskServiceConfig.RiskControlConfig.of(Optional.of(firmMaxOpenPosition), 
				Optional.of(firmMaxProfit), 
				Optional.of(firmMaxLoss),
				Optional.of(firmMaxCapUsed));

		config = new PortfolioAndRiskServiceConfig(systemId, 
				"test-risk", 
				"test-risk", 
				"test-risk", 
				ServiceType.PortfolioAndRiskService, 
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
				broadcastFrequency,
				defaultSecurityConfig,
				defaultIssuerConfig,
				defaultUndConfig,
				defaultFirmConfig);
				
		adminSink = TestHelper.mock(adminSink, systemId, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");
		
		otSnapshotSink = TestHelper.mock(otSnapshotSink, systemId, orderSnapshotSinkId, ServiceType.OrderAndTradeSnapshotService);
		otSnapshot = MessageSinkRef.of(otSnapshotSink, "test-ot-snapshot");

		rdsSink = TestHelper.mock(rdsSink, systemId, rdsSinkId, ServiceType.RefDataService);
		rds = MessageSinkRef.of(rdsSink, "test-rds");

		mdSnapshotSink = TestHelper.mock(mdSnapshotSink, systemId, mdSnapshotSinkId, ServiceType.MarketDataSnapshotService);
		mdSnapshot = MessageSinkRef.of(mdSnapshotSink, "test-md-snapshot");

		persistSink = TestHelper.mock(persistSink, systemId, persistSinkId, ServiceType.PersistService);
		persist = MessageSinkRef.of(persistSink, "test-md-persist");

		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return PortfolioAndRiskService.of(serviceConfig, messageService);
			}
		};

		SenderBuilder senderBuilder = new TestSenderBuilder();
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(otSnapshot);
		wrapper.messenger().referenceManager().register(persist);
		wrapper.messenger().referenceManager().register(selfSinkRef);
		
		mutableResponseHandler = new MutableHandler<>();
		when(requestTracker.responseHandler()).thenReturn(mutableResponseHandler);
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
					return PortfolioAndRiskService.of(serviceConfig, messageService);
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
	public void testInvalidArgument() throws ConfigurationException{
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return PortfolioAndRiskService.of(serviceConfig, messageService);
			}
		};
		LineHandlerConfig lineHandlerConfig = LineHandlerConfig.of(1, 
				"line handler", 
				"line handler", 
				com.lunar.order.NullLineHandlerEngine.class, 
				4, 
				Optional.empty(),
				Duration.ofSeconds(1), 
				128,
				true,
				WaitStrategy.BLOCKING_WAIT,
				1024,
				128,
				true,
				WaitStrategy.BLOCKING_WAIT,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
		OrderManagementAndExecutionServiceConfig config = new OrderManagementAndExecutionServiceConfig(1,
				"test-omes", 
				"test-omes", 
				"test-omes", 
				ServiceType.OrderManagementAndExecutionService, 
				Optional.empty(), 
				6, 
				Optional.of(1024), 
				Optional.of(1024), 
				Optional.empty(),
				Optional.empty(),
				true, 
				true,
				Duration.ofSeconds(1), 
				false,
				"",
				ServiceConstant.START_ORDER_SID_SEQUENCE,
				ServiceConstant.START_TRADE_SID_SEQUENCE,
				128, 
				128, 
				128, 
				32, 
				2, 
				false, 
				128,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				lineHandlerConfig);
		
		LunarServiceTestWrapper wrapper = testHelper.createService(config, builder);
		wrapper.messageService().onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.STOP, wrapper.messageService().state());
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
		assertTrue(tracker.isTrackingService(ServiceType.RefDataService));
		assertTrue(tracker.isTrackingService(ServiceType.MarketDataSnapshotService));
		assertTrue(tracker.isTrackingService(ServiceType.OrderAndTradeSnapshotService));
		assertTrue(tracker.isTrackingService(ServiceType.PersistService));
		
		
	}
	
	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToReadyState(){
		// Given
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return PortfolioAndRiskService.of(serviceConfig, messageService);
			}
		};
		
		SenderBuilder senderBuilder = new TestSenderBuilder().requestTracker(requestTracker);
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(rds);
		wrapper.messenger().referenceManager().register(otSnapshot);
		wrapper.messenger().referenceManager().register(mdSnapshot);
		wrapper.messenger().referenceManager().register(persist);
		
		wrapper.messageService().onStart();
		assertEquals(States.WAITING_FOR_SERVICES, wrapper.messageService().state());
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						admin.sinkId(), 
						admin.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						otSnapshot.sinkId(),
						otSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						persist.sinkId(),
						persist.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						mdSnapshot.sinkId(),
						mdSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						rds.sinkId(),
						rds.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		// Verify
		assertEquals(States.READY, wrapper.messageService().state());
		ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
		verify(requestTracker, times(1)).sendAndTrack(eq(otSnapshot), argumentCaptor.capture());
		Request request = argumentCaptor.getValue();
		assertEquals(RequestType.GET_AND_SUBSCRIBE, request.requestType());
		Optional<Parameter> first = request.parameters().parallelStream().filter(new Predicate<Parameter>() {
			@Override
			public boolean test(Parameter t) {
				return t.type().equals(ParameterType.DATA_TYPE);
			}
		}).findFirst();
		assertTrue(first.isPresent());
		assertEquals(DataType.ALL_ORDER_AND_TRADE_UPDATE.value(), first.get().valueLong().byteValue());
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
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						otSnapshot.sinkId(),
						otSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						mdSnapshot.sinkId(),
						mdSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						persist.sinkId(),
						persist.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						rds.sinkId(),
						rds.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		// verify
		// 1) state, 2) subscription request and position request are sent, 
		assertEquals(States.READY, wrapper.messageService().state());
		assertEquals(2, wrapper.messengerWrapper().requestTracker().requests().size());
		
		// when - send a response to ack its requests
		IntArrayList clientKeys = new IntArrayList(2);
		wrapper.messengerWrapper().requestTracker().requests().values().forEach((r) -> { clientKeys.add(r.request().clientKey()); });
		for (int clientKey : clientKeys){
			selfMessenger.responseSender().sendResponse(wrapper.sink(), 
					clientKey,
					BooleanType.TRUE, 
					ServiceConstant.START_RESPONSE_SEQUENCE, 
					ResultType.OK);
			wrapper.pushNextMessage();			
		}
		
		// Verify timer task is set, with appropriate delay
		assertEquals(2, testHelper.timerService().numOutstanding());
		long expiryNs = testHelper.timerService().nanoTime() + broadcastFrequency.toNanos();
		assertEquals(expiryNs, testHelper.timerService().timeouts().keySet().first().longValue());

		// verify - state is ACTIVE
		assertEquals(States.ACTIVE, wrapper.messageService().state());
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertNotNull(service.firmPosition());
		assertNotNull(service.firmRiskState());
	}

	@Test
	public void givenReadyWhenReceiveSubscriptionAckThenMoveToActiveState() {
		advanceToActiveState(wrapper, selfMessenger);
	}
	
	static class RequestMatcher extends ArgumentMatcher<Request> {
		Predicate<Request> predicate;
		static RequestMatcher of(Predicate<Request> predicate){
			return new RequestMatcher(predicate);
		}
		RequestMatcher(Predicate<Request> predicate){
			this.predicate = predicate;
		}
		@Override
		public boolean matches(Object argument) {
			Request request = (Request) argument;
			return predicate.test(request);
		}
	}
	
	static class ResponseSbeDecoderMatcher extends ArgumentMatcher<ResponseSbeDecoder> {
		Predicate<ResponseSbeDecoder> predicate;
		static ResponseSbeDecoderMatcher of(Predicate<ResponseSbeDecoder> predicate){
			return new ResponseSbeDecoderMatcher(predicate);
		}
		ResponseSbeDecoderMatcher(Predicate<ResponseSbeDecoder> predicate){
			this.predicate = predicate;
		}
		@Override
		public boolean matches(Object argument) {
			ResponseSbeDecoder response = (ResponseSbeDecoder) argument;
			return predicate.test(response);
		}
	}
	
	private void setToActiveState(){
		// Given
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return PortfolioAndRiskService.of(serviceConfig, messageService);
			}
		};

		SenderBuilder senderBuilder = new TestSenderBuilder().requestTracker(requestTracker);
		
		CompletableFuture<Request> futureForGetAndSubscribeRequest = new CompletableFuture<Request>();
		when(requestTracker.sendAndTrack(eq(otSnapshot), argThat(RequestMatcher.of((r) -> { 
				return r.requestType().equals(RequestType.GET_AND_SUBSCRIBE); 
			})))).thenReturn(futureForGetAndSubscribeRequest);

		CompletableFuture<Request> futureForGetInitPositionRequest = new CompletableFuture<Request>();
		when(requestTracker.sendAndTrack(eq(persist), argThat(RequestMatcher.of((r) -> { 
			return r.requestType().equals(RequestType.GET); 
		})))).thenReturn(futureForGetInitPositionRequest);
		
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(rds);
		wrapper.messenger().referenceManager().register(otSnapshot);
		wrapper.messenger().referenceManager().register(mdSnapshot);
		wrapper.messenger().referenceManager().register(persist);
		
		wrapper.messageService().onStart();
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						admin.sinkId(), 
						admin.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						otSnapshot.sinkId(),
						otSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						mdSnapshot.sinkId(),
						mdSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						rds.sinkId(),
						rds.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						persist.sinkId(),
						persist.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		assertEquals(States.READY, wrapper.messageService().state());
		
		// Get the two expected requests from service
		ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
		verify(requestTracker, times(1)).sendAndTrack(eq(otSnapshot), argumentCaptor.capture());
		Request request = argumentCaptor.getValue();
		assertEquals(RequestType.GET_AND_SUBSCRIBE, request.requestType());
		Optional<Parameter> first = request.parameters().parallelStream().filter(new Predicate<Parameter>() {
			@Override
			public boolean test(Parameter t) {
				return t.type().equals(ParameterType.DATA_TYPE);
			}
		}).findFirst();
		assertTrue(first.isPresent());
		assertEquals(DataType.ALL_ORDER_AND_TRADE_UPDATE.value(), first.get().valueLong().byteValue());

		ArgumentCaptor<Request> argumentCaptorInitPos = ArgumentCaptor.forClass(Request.class);
		verify(requestTracker, times(1)).sendAndTrack(eq(persist), argumentCaptorInitPos.capture());
		Request requestInitPos = argumentCaptorInitPos.getValue();
		assertEquals(RequestType.GET, requestInitPos.requestType());
		Optional<Parameter> firstInitPos = requestInitPos.parameters().parallelStream().filter(new Predicate<Parameter>() {
			@Override
			public boolean test(Parameter t) {
				return t.type().equals(ParameterType.TEMPLATE_TYPE);
			}
		}).findFirst();
		assertTrue(firstInitPos.isPresent());
		assertEquals(TemplateType.POSITION.value(), firstInitPos.get().valueLong().byteValue());
		
		// Handling of response
		// ====================
		// 1. Setup such that we will complete a future when requestTracker receives a particular event
		mutableResponseHandler.impl(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				if (codec.clientKey() == request.clientKey() &&
						codec.isLast() == BooleanType.TRUE &&
								codec.resultType() == ResultType.OK){
					futureForGetAndSubscribeRequest.complete(request.resultType(ResultType.OK));
					return;
				}
				if (codec.clientKey() == requestInitPos.clientKey() &&
						codec.isLast() == BooleanType.TRUE &&
								codec.resultType() == ResultType.OK){
					futureForGetInitPositionRequest.complete(request.resultType(ResultType.OK));
					return;
				}
			}
		});
		
		// 2. Send response to request to service
		selfMessenger.responseSender().sendResponse(wrapper.sink(), 
				request.clientKey(), 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK);
		wrapper.pushNextMessage();
		
		selfMessenger.responseSender().sendResponse(wrapper.sink(), 
				requestInitPos.clientKey(), 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK);
		wrapper.pushNextMessage();
 	}
	
	private void createOrder(MutableDirectBuffer buffer, long secSid, int orderSid, Side side, int quantity, int limitPrice, OrderStatus status){
		long time = LocalTime.now().toNanoOfDay();
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int stopPrice = 40000;
		Order order = Order.of(secSid, 
				selfSinkRef, 
				orderSid, 
				quantity, 
				isAlgo, 
				orderType, 
				side, 
				limitPrice, 
				stopPrice, 
				tif,
				status,
				time,
				time);
	    int channelId = 5;
		order.channelId(channelId);
		long channelSeq = 123456;
		order.channelSeq(channelSeq);
		OrderUtil.populateFrom(new OrderSbeEncoder(), buffer, 0, order, selfMessenger.stringBuffer());
	}
	
	private void createTrade(MutableDirectBuffer buffer, int channelId, long channelSeq, long secSid, int tradeSid, int orderSid, Side side, int executionQty, int executionPrice, TradeStatus tradeStatus){
		long createTime = LocalTime.now().toNanoOfDay();
		String executionId = "123456789012345678901";
		int orderId = 1001001;
		int leavesQty = 0;
		int cumulativeQty = 1000;
		OrderStatus orderStatus = OrderStatus.FILLED;
		Trade trade = Trade.of(tradeSid, 
				orderSid, 
				orderId,
				secSid,
				side, 
				leavesQty, 
				cumulativeQty, 
				executionId, 
				executionPrice, 
				executionQty, 
				orderStatus,
				tradeStatus, 
				createTime, 
				createTime);
		trade.channelId(channelId).channelSeq(channelSeq);
		TradeUtil.populateFrom(new TradeSbeEncoder(), buffer, 0, trade, selfMessenger.stringBuffer());
	}
	
	private void sendOrder(int channelId, long channelSeq, int orderSid, long secSid, Side side, int quantity, int limitPrice, OrderStatus status){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		createOrder(buffer, secSid, orderSid, side, quantity, limitPrice, status);
		selfMessenger.trySendWithHeaderInfo(new MessageSinkRef[]{wrapper.sink()}, 
				1, 
				OrderSbeEncoder.BLOCK_LENGTH,
				OrderSbeEncoder.TEMPLATE_ID,
				OrderSbeEncoder.SCHEMA_ID,
				OrderSbeEncoder.SCHEMA_VERSION,
				buffer, 
				0, 
				OrderSbeEncoder.BLOCK_LENGTH,
				new long[1]);
		wrapper.pushNextMessage();
	}
	
	private void sendTrade(int channelId, long channelSeq, int tradeSid, int orderSid, long secSid, Side side, int executionQuantity, int executionPrice, TradeStatus tradeStatus){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		createTrade(buffer, channelId, channelSeq, secSid, tradeSid, orderSid, side, executionQuantity, executionPrice, tradeStatus);
		selfMessenger.trySendWithHeaderInfo(new MessageSinkRef[]{wrapper.sink()}, 
				1, 
				TradeSbeEncoder.BLOCK_LENGTH,
				TradeSbeEncoder.TEMPLATE_ID,
				TradeSbeEncoder.SCHEMA_ID,
				TradeSbeEncoder.SCHEMA_VERSION,
				buffer, 
				0, 
				TradeSbeEncoder.BLOCK_LENGTH,
				new long[1]);
		wrapper.pushNextMessage();
	}
	
	private void sendPrice(long secSid, Boobs boobs){
		selfMessenger.boobsSender().sendBoobs(wrapper.sink(), boobs);
		wrapper.pushNextMessage();
	}
	
	private void sendOrderOrTradeWithNoSecuritySetup(int channelId, long channelSeq, int orderSid, long secSid, TemplateType type){
		// Given
		AtomicInteger expectedNumInvocations = new AtomicInteger(2);
		CompletableFuture<Request> futureForGetSecurityRequest = new CompletableFuture<Request>();
		futureForGetSecurityRequest.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request t, Throwable u) {
				expectedNumInvocations.decrementAndGet();
			}
		});
		
		when(requestTracker.sendAndTrack(eq(rds), argThat(RequestMatcher.of((r) -> {
			Map<ParameterType, Parameter> result = r.parameters().stream().collect(Collectors.toMap(Parameter::type, Function.identity()));
			return r.requestType().equals(RequestType.GET) && 
					result.containsKey(ParameterType.TEMPLATE_TYPE) &&
					result.containsKey(ParameterType.SECURITY_SID);
		})))).thenReturn(futureForGetSecurityRequest);
		
		CompletableFuture<Request> futureForSubscribeBoobsRequest = new CompletableFuture<Request>();
		futureForSubscribeBoobsRequest.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request t, Throwable u) {
				expectedNumInvocations.decrementAndGet();
			}
		});
		when(requestTracker.sendAndTrack(eq(rds), argThat(RequestMatcher.of((r) -> {
			Map<ParameterType, Parameter> result = r.parameters().stream().collect(Collectors.toMap(Parameter::type, Function.identity()));
			return r.requestType().equals(RequestType.SUBSCRIBE) && 
					(result.containsKey(ParameterType.TEMPLATE_TYPE) && 
							result.get(ParameterType.TEMPLATE_TYPE).valueLong() == TemplateType.BOOBS.value()) &&
					result.containsKey(ParameterType.SECURITY_SID);
		})))).thenReturn(futureForSubscribeBoobsRequest);

		if (type == TemplateType.ORDER){
			int quantity = 1000;
			Side side = Side.BUY;
			int limitPrice = 40000;
			OrderStatus status = OrderStatus.NEW; 
			sendOrder(channelId, channelSeq, orderSid, secSid, side, quantity, limitPrice, status);
		}
		else {
			int tradeSid = 1212121;
			int executionPrice = 40000;
			Side side = Side.BUY;
			int executionQty = 1000;
			TradeStatus tradeStatus = TradeStatus.NEW;
			sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, side, executionQty, executionPrice, tradeStatus);
		}
		
		// Verify - expect a request for security and a request for market data
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().containsKey(secSid));
		assertEquals(0, service.undPositions().size());
		assertEquals(0, service.issuerPositions().size());
		
		// Handling of Get Security request
		// ================================
		// 1. Verify that a request has been sent
		ArgumentCaptor<Request> getSecurityRequestCaptor = ArgumentCaptor.forClass(Request.class);
		verify(requestTracker, times(1)).sendAndTrack(eq(rds), getSecurityRequestCaptor.capture());
		Request getSecurityRequest = getSecurityRequestCaptor.getValue();
		LOG.debug("getSecurityRequest [clientKey:{}]", getSecurityRequest.clientKey());
		assertEquals(RequestType.GET, getSecurityRequest.requestType());
		Map<ParameterType, Parameter> getSecurityRequestParameters = getSecurityRequest.parameters().stream().collect(Collectors.toMap(Parameter::type, Function.identity()));
		assertEquals(TemplateType.SECURITY.value(), getSecurityRequestParameters.get(ParameterType.TEMPLATE_TYPE).valueLong().byteValue());
		
		// 2. Setup such that we will complete a future when requestTracker receives the last response
		mutableResponseHandler.impl(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				if (codec.clientKey() == getSecurityRequest.clientKey() &&
						codec.isLast() == BooleanType.TRUE &&
								codec.resultType() == ResultType.OK){
					futureForGetSecurityRequest.complete(getSecurityRequest.resultType(ResultType.OK));
					LOG.debug("answered get security request in mockito");					
				}
			}
		});
		
		// 3. Send responses
		selfMessenger.responseSender().sendResponse(wrapper.sink(), 
				getSecurityRequest.clientKey(), 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK);
		wrapper.pushNextMessage();
		
		// Handling of Boobs subscription request
		// ======================================
		// 1. Set to complete a future when requestTracker receives a particular event
		ArgumentCaptor<Request> subscribeBoobsRequestCaptor = ArgumentCaptor.forClass(Request.class);
		verify(requestTracker, times(1)).sendAndTrack(eq(mdSnapshot), subscribeBoobsRequestCaptor.capture());
		Request subBoobsRequest = subscribeBoobsRequestCaptor.getValue();
		assertEquals(RequestType.SUBSCRIBE, subBoobsRequest.requestType());
		LOG.debug("subBoobsRequest [clientKey:{}]", subBoobsRequest.clientKey());

		// 2. Setup such that we will complete a future when requestTracker receives the last response
		mutableResponseHandler.impl(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				if (codec.clientKey() == subBoobsRequest.clientKey() &&
						codec.isLast() == BooleanType.TRUE &&
								codec.resultType() == ResultType.OK){
					futureForSubscribeBoobsRequest.complete(subBoobsRequest.resultType(ResultType.OK));
					LOG.debug("answered subscription boobs request in mockito");					
				}
			}
		});
		
		// 3. Send the last response
		selfMessenger.responseSender().sendResponse(wrapper.sink(), 
				subBoobsRequest.clientKey(), 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK);
		wrapper.pushNextMessage();
		
		// Verify
		assertEquals(0, expectedNumInvocations.get());
	}
	
	@Test
	public void givenActiveAndNoSecurityPositionWhenReceiveTradeFollowBySecurityUpdateThenPnlShouldCorrect2(){
		// Given
		setToActiveState();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().isEmpty());

		long secSid = 11792;
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		
		// When
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int tradeSid = 12122121;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, Side.BUY, 300_000, 228, tradeStatus);		
		sendTrade(channelId, channelSeq + 1, tradeSid + 1, orderSid + 1, secSid, Side.SELL, 300_000, 228, tradeStatus);
		sendTrade(channelId, channelSeq + 2, tradeSid + 2, orderSid + 2, secSid, Side.BUY, 200_000, 231, tradeStatus);		
		sendTrade(channelId, channelSeq + 3, tradeSid + 3, orderSid + 3, secSid, Side.SELL, 200_000, 231, tradeStatus);
		sendTrade(channelId, channelSeq + 4, tradeSid + 4, orderSid + 4, secSid, Side.BUY, 200_000, 235, tradeStatus);		
		sendTrade(channelId, channelSeq + 5, tradeSid + 5, orderSid + 5, secSid, Side.SELL, 200_000, 235, tradeStatus);
		sendTrade(channelId, channelSeq + 6, tradeSid + 6, orderSid + 6, secSid, Side.BUY, 400_000, 235, tradeStatus);		
		sendTrade(channelId, channelSeq + 7, tradeSid + 7, orderSid + 7, secSid, Side.SELL, 400_000, 235, tradeStatus);
		sendTrade(channelId, channelSeq + 8, tradeSid + 8, orderSid + 8, secSid, Side.BUY, 400_000, 235, tradeStatus);		
		sendTrade(channelId, channelSeq + 9, tradeSid + 9, orderSid + 9, secSid, Side.SELL, 400_000, 235, tradeStatus);
		
		SecurityType secType = SecurityType.WARRANT;
		String code = "11792.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		int lotSize = 1000;
	    boolean refIsAlgo = true;
		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, refIsAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();

		
		// Verify
		AssertUtil.assertDouble(-183.7184, service.secPositions().get(secSid).details().experimentalNetRealizedPnl(), "experimentalNetRealizedPnl");
		AssertUtil.assertDouble(-183.7184, service.secPositions().get(secSid).details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().experimentalUnrealizedPnl(), "experimentalUnrealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().unrealizedPnl(), "unrealizedPnl");
	}
	
	@Test
	public void givenActiveAndNoSecurityPositionWhenReceiveTradeFollowBySecurityUpdateThenPnlShouldCorrect(){
		// Given
		setToActiveState();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().isEmpty());

		long secSid = 11849;
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		
		// When
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int tradeSid = 12122121;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, Side.BUY, 50000, 115, tradeStatus);		
		sendTrade(channelId, channelSeq + 1, tradeSid + 1, orderSid + 1, secSid, Side.SELL, 50000, 115, tradeStatus);
		sendTrade(channelId, channelSeq + 2, tradeSid + 2, orderSid + 2, secSid, Side.BUY, 70000, 108, tradeStatus);		
		sendTrade(channelId, channelSeq + 3, tradeSid + 3, orderSid + 3, secSid, Side.SELL, 70000, 107, tradeStatus);
		sendTrade(channelId, channelSeq + 4, tradeSid + 4, orderSid + 4, secSid, Side.BUY, 50000, 109, tradeStatus);		
		sendTrade(channelId, channelSeq + 5, tradeSid + 5, orderSid + 5, secSid, Side.SELL, 50000, 109, tradeStatus);
		
		SecurityType secType = SecurityType.WARRANT;
		String code = "11849.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		int lotSize = 1000;
	    boolean refIsAlgo = true;
		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, refIsAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();

		
		// Verify
		AssertUtil.assertDouble(-93.50115000000005, service.secPositions().get(secSid).details().experimentalNetRealizedPnl(), "experimentalNetRealizedPnl");
		AssertUtil.assertDouble(-93.50115000000005, service.secPositions().get(secSid).details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().experimentalUnrealizedPnl(), "experimentalUnrealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().unrealizedPnl(), "unrealizedPnl");
	}
	
	@Test
	public void givenActiveAndNoSecurityPositionWhenReceiveTradeFollowBySecurityUpdateThenPnlShouldCorrect3(){
		// Given
		setToActiveState();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().isEmpty());

		long secSid = 11314;
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		
		// When
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int tradeSid = 12122121;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, Side.BUY, 100000, 159, tradeStatus);		
		sendTrade(channelId, channelSeq + 1, tradeSid + 1, orderSid + 1, secSid, Side.SELL, 100000, 160, tradeStatus);
		
		sendTrade(channelId, channelSeq + 2, tradeSid + 2, orderSid + 2, secSid, Side.BUY, 100000, 146, tradeStatus);		
		sendTrade(channelId, channelSeq + 3, tradeSid + 3, orderSid + 3, secSid, Side.SELL, 100000, 147, tradeStatus);
		
		sendTrade(channelId, channelSeq + 4, tradeSid + 4, orderSid + 4, secSid, Side.BUY, 100000, 147, tradeStatus);		
		sendTrade(channelId, channelSeq + 5, tradeSid + 5, orderSid + 5, secSid, Side.SELL, 100000, 147, tradeStatus);
		sendTrade(channelId, channelSeq + 6, tradeSid + 6, orderSid + 6, secSid, Side.BUY, 100000, 147, tradeStatus);		
		sendTrade(channelId, channelSeq + 7, tradeSid + 7, orderSid + 7, secSid, Side.SELL, 100000, 147, tradeStatus);
		sendTrade(channelId, channelSeq + 8, tradeSid + 8, orderSid + 8, secSid, Side.BUY, 100000, 147, tradeStatus);		
		sendTrade(channelId, channelSeq + 9, tradeSid + 9, orderSid + 9, secSid, Side.SELL, 100000, 147, tradeStatus);
		sendTrade(channelId, channelSeq + 10, tradeSid + 10, orderSid + 10, secSid, Side.BUY, 100000, 146, tradeStatus);		
		sendTrade(channelId, channelSeq + 11, tradeSid + 11, orderSid + 11, secSid, Side.SELL, 100000, 147, tradeStatus);
		sendTrade(channelId, channelSeq + 12, tradeSid + 12, orderSid + 12, secSid, Side.BUY, 100000, 149, tradeStatus);		
		sendTrade(channelId, channelSeq + 13, tradeSid + 13, orderSid + 13, secSid, Side.SELL, 100000, 149, tradeStatus);
		sendTrade(channelId, channelSeq + 14, tradeSid + 14, orderSid + 14, secSid, Side.BUY, 100000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 15, tradeSid + 15, orderSid + 15, secSid, Side.SELL, 100000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 16, tradeSid + 16, orderSid + 16, secSid, Side.BUY, 100000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 17, tradeSid + 17, orderSid + 17, secSid, Side.SELL, 100000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 18, tradeSid + 18, orderSid + 18, secSid, Side.BUY, 100000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 19, tradeSid + 19, orderSid + 19, secSid, Side.SELL, 100000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 20, tradeSid + 20, orderSid + 20, secSid, Side.BUY, 100000, 151, tradeStatus);		
		sendTrade(channelId, channelSeq + 21, tradeSid + 21, orderSid + 21, secSid, Side.SELL, 100000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 22, tradeSid + 22, orderSid + 22, secSid, Side.BUY, 100000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 23, tradeSid + 23, orderSid + 23, secSid, Side.SELL, 100000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 24, tradeSid + 24, orderSid + 24, secSid, Side.BUY, 50000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 25, tradeSid + 25, orderSid + 25, secSid, Side.SELL, 50000, 153, tradeStatus);
		sendTrade(channelId, channelSeq + 26, tradeSid + 26, orderSid + 26, secSid, Side.BUY, 75000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 27, tradeSid + 27, orderSid + 27, secSid, Side.SELL, 75000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 28, tradeSid + 28, orderSid + 28, secSid, Side.BUY, 100000, 152, tradeStatus);		
		sendTrade(channelId, channelSeq + 29, tradeSid + 29, orderSid + 29, secSid, Side.SELL, 100000, 152, tradeStatus);
		sendTrade(channelId, channelSeq + 30, tradeSid + 30, orderSid + 30, secSid, Side.BUY, 100000, 156, tradeStatus);		
		sendTrade(channelId, channelSeq + 31, tradeSid + 31, orderSid + 31, secSid, Side.SELL, 100000, 156, tradeStatus);

		SecurityType secType = SecurityType.WARRANT;
		String code = "11314.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		int lotSize = 1000;
	    boolean refIsAlgo = true;
		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, refIsAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();

		
		// Verify
		AssertUtil.assertDouble(265.56865, service.secPositions().get(secSid).details().experimentalNetRealizedPnl(), "experimentalNetRealizedPnl");
		AssertUtil.assertDouble(265.56865, service.secPositions().get(secSid).details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().experimentalUnrealizedPnl(), "experimentalUnrealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().unrealizedPnl(), "unrealizedPnl");
	}
	
	@Test
	public void givenActiveAndNoSecurityPositionWhenReceiveTradeFollowBySecurityUpdateThenPnlShouldCorrect4(){
		// Given
		setToActiveState();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().isEmpty());

		long secSid = 27134;
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		
		// When
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int tradeSid = 12122121;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, Side.BUY, 74000, 138, tradeStatus);		
		sendTrade(channelId, channelSeq + 1, tradeSid + 1, orderSid + 1, secSid, Side.SELL, 74000, 138, tradeStatus);
		
		sendTrade(channelId, channelSeq + 2, tradeSid + 2, orderSid + 2, secSid, Side.BUY, 74000, 143, tradeStatus);		
		sendTrade(channelId, channelSeq + 3, tradeSid + 3, orderSid + 3, secSid, Side.SELL, 74000, 144, tradeStatus);
		
		sendTrade(channelId, channelSeq + 4, tradeSid + 4, orderSid + 4, secSid, Side.BUY, 100000, 147, tradeStatus);		
		sendTrade(channelId, channelSeq + 5, tradeSid + 5, orderSid + 5, secSid, Side.SELL, 100000, 147, tradeStatus);
		
		sendTrade(channelId, channelSeq + 6, tradeSid + 6, orderSid + 6, secSid, Side.BUY, 100000, 149, tradeStatus);		
		sendTrade(channelId, channelSeq + 7, tradeSid + 7, orderSid + 7, secSid, Side.SELL, 100000, 149, tradeStatus);
		
		sendTrade(channelId, channelSeq + 8, tradeSid + 8, orderSid + 8, secSid, Side.BUY, 50000, 137, tradeStatus);		
		sendTrade(channelId, channelSeq + 9, tradeSid + 9, orderSid + 9, secSid, Side.SELL, 50000, 137, tradeStatus);

		SecurityType secType = SecurityType.WARRANT;
		String code = "11314.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		int lotSize = 1000;
	    boolean refIsAlgo = true;
		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, refIsAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();

		
		// Verify
		AssertUtil.assertDouble(22.994426, service.secPositions().get(secSid).details().experimentalNetRealizedPnl(), "experimentalNetRealizedPnl");
		AssertUtil.assertDouble(22.994426, service.secPositions().get(secSid).details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().experimentalUnrealizedPnl(), "experimentalUnrealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().unrealizedPnl(), "unrealizedPnl");
	}
	
	@Test
	public void givenActiveAndNoSecurityPositionWhenReceiveTradeFollowBySecurityUpdateThenPnlShouldCorrect5(){
		// Given
		setToActiveState();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().isEmpty());

		long secSid = 12912;
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		
		// When
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int tradeSid = 12122121;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, Side.BUY, 40000, 720, tradeStatus);		
		sendTrade(channelId, channelSeq + 1, tradeSid + 1, orderSid + 1, secSid, Side.SELL, 40000, 720, tradeStatus);
		
		sendTrade(channelId, channelSeq + 2, tradeSid + 2, orderSid + 2, secSid, Side.BUY, 20000, 720, tradeStatus);		
		sendTrade(channelId, channelSeq + 3, tradeSid + 3, orderSid + 3, secSid, Side.SELL, 20000, 720, tradeStatus);
		
		sendTrade(channelId, channelSeq + 4, tradeSid + 4, orderSid + 4, secSid, Side.BUY, 20000, 720, tradeStatus);		
		sendTrade(channelId, channelSeq + 5, tradeSid + 5, orderSid + 5, secSid, Side.SELL, 20000, 720, tradeStatus);
		
		sendTrade(channelId, channelSeq + 6, tradeSid + 6, orderSid + 6, secSid, Side.BUY, 60000, 710, tradeStatus);		
		sendTrade(channelId, channelSeq + 7, tradeSid + 7, orderSid + 7, secSid, Side.SELL, 60000, 710, tradeStatus);
		
		SecurityType secType = SecurityType.WARRANT;
		String code = "12912.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		int lotSize = 1000;
	    boolean refIsAlgo = true;
		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, refIsAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();

		
		// Verify
		AssertUtil.assertDouble(-65.4908, service.secPositions().get(secSid).details().experimentalNetRealizedPnl(), "experimentalNetRealizedPnl");
		AssertUtil.assertDouble(-65.4908, service.secPositions().get(secSid).details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().experimentalUnrealizedPnl(), "experimentalUnrealizedPnl");
		AssertUtil.assertDouble(0, service.secPositions().get(secSid).details().unrealizedPnl(), "unrealizedPnl");
	}

	@Test
	public void givenActiveAndNoSecurityPositionWhenReceiveSecurityThenDoThese(){
		// Given
		setToActiveState();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertTrue(service.secPositions().isEmpty());
		
		// When
		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		String code = "14928.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		int lotSize = 1000;
	    boolean refIsAlgo = true;

		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, refIsAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();
		
		// Verify
		assertEquals(1, service.secPositions().size());
		SecurityPosition securityPosition = service.secPositions().get(secSid);
		assertEquals(issuerSid, securityPosition.issuerSid());
		assertEquals(undSecSid, securityPosition.undSecSid());
		assertEquals(1, service.issuerPositions().size());
		AggregatedSecurityPosition issuerPosition = service.issuerPositions().get(issuerSid);
		assertNotNull(issuerPosition);
		assertEquals(issuerSid, issuerPosition.entitySid());
		assertEquals(EntityType.ISSUER, issuerPosition.entityType());
		AggregatedSecurityPosition undPosition = service.undPositions().get(undSecSid);
		assertNotNull(undPosition);
		assertEquals(undSecSid, undPosition.entitySid());
		assertEquals(EntityType.UNDERLYING, undPosition.entityType());
	}
	
	@Test
	public void givenActiveAndSecurityPositionAlreadyExistsWhenReceiveSecurityThenDoNothing(){
		// Given
		setToActiveState();

		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		String code = "14928.HK";
		int exchangeSid = 300001;
		long undSecSid = 200010;
		Optional<LocalDate> maturity = Optional.of(LocalDate.now());
		PutOrCall putOrCall = PutOrCall.CALL; 
		OptionStyle optionStyle = OptionStyle.AMERICAN;
		int strikePrice = 123456;
		int convRatio = 10;
		int issuerSid = 400001;
		int clientKey = clientKeySeq.getAndIncrement();
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		int lotSize = 10000;
        boolean isAlgo = true;
		
		service.createSecurityPosition(secSid, secType, putOrCall, undSecSid, issuerSid);

		// When
		Security security = Security.of(secSid, secType, code, exchangeSid, undSecSid, maturity, ServiceConstant.NULL_LISTED_DATE, putOrCall, 
				optionStyle, strikePrice, convRatio, issuerSid, lotSize, isAlgo, SpreadTableBuilder.get(secType));
		selfMessenger.responseSender().sendSbeEncodable(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK, security);
		wrapper.pushNextMessage();

		// Verify
		assertEquals(1, service.secPositions().size());
		SecurityPosition securityPosition = service.secPositions().get(secSid);
		assertEquals(issuerSid, securityPosition.issuerSid());
		assertEquals(undSecSid, securityPosition.undSecSid());
		assertEquals(1, service.issuerPositions().size());
		AggregatedSecurityPosition issuerPosition = service.issuerPositions().get(issuerSid);
		assertNotNull(issuerPosition);
		assertEquals(issuerSid, issuerPosition.entitySid());
		assertEquals(EntityType.ISSUER, issuerPosition.entityType());
		AggregatedSecurityPosition undPosition = service.undPositions().get(undSecSid);
		assertNotNull(undPosition);
		assertEquals(undSecSid, undPosition.entitySid());
		assertEquals(EntityType.UNDERLYING, undPosition.entityType());
	}

	@Test
	public void givenActiveWhenReceiveOrderOfUnknownSecurityThenPositionIsUpdatedAndRequestIsSentToGetSecurity(){
		// Given
		setToActiveState();

		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		long secSid = 90000001;
		sendOrderOrTradeWithNoSecuritySetup(channelId, channelSeq, orderSid, secSid, TemplateType.ORDER);		
	}

	@Test
	public void givenActiveWhenReceiveTradeOfUnknownSecurityThenPositionIsUpdatedAndRequestIsSentToGetSecurity(){
		// Given
		setToActiveState();
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		long secSid = 90000001;
		sendOrderOrTradeWithNoSecuritySetup(channelId, channelSeq, orderSid, secSid, TemplateType.TRADE);
	}

	@Test
	public void givenActiveAndSecurityExistWhenTriggeredRiskStateChangeThenRiskStateIsSent(){
		// Given
		setToActiveState();
		sendAllRiskStateChangeSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
		
		// Create security position 
		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 200010;
		int issuerSid = 400001;
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		service.createSecurityPosition(secSid, secType, putOrCall, undSecSid, issuerSid);
		assertTrue(service.secPositions().containsKey(secSid));
		
		// Send trade to trigger
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		Side side = Side.BUY;
		int tradeSid = 12122121;
		int executionPrice = 1000_000;
		int executionQty = 2000;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, side, executionQty, executionPrice, tradeStatus);

		// When
		// Advance time
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		// Receive position update
		AtomicInteger expectedCount = new AtomicInteger(8);
		selfMessenger.receiver().riskStateHandlerList().add(new Handler<RiskStateSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder codec) {
				LOG.debug("Received risk state {}", RiskStateDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		channelSeq = 1001;
		orderSid = 8000002;
		tradeSid = 12122122;
		side = Side.SELL;
		executionPrice = 2000_000;
		executionQty = 2000;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, side, executionQty, executionPrice, tradeStatus);
		selfRingBufferSinkPoller.pollAll();
		
		// Send price
		int bestBid = 1000_000;
		int bestAsk = 2020_000;
		int last = 1010_000;
		Boobs boobs = Boobs.of(secSid, bestBid, bestAsk, last); 
		sendPrice(secSid, boobs);
		
		selfRingBufferSinkPoller.pollAll();

		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndSecurityExistWhenVerifyPnlChangesWithNewTrades(){
		// Given
		setToActiveState();
		sendAllPositionSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
		
		// Create security position 
		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 200010;
		int issuerSid = 400001;
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		service.createSecurityPosition(secSid, secType, putOrCall, undSecSid, issuerSid);
		assertTrue(service.secPositions().containsKey(secSid));

		long secSid2 = 300001;
		service.createSecurityPosition(secSid2, secType, putOrCall, undSecSid, issuerSid);
		assertTrue(service.secPositions().containsKey(secSid));
		
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		Side side = Side.BUY;
		int tradeSid = 12122121;
		int executionPrice = 11000;
		int executionQty = 100;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq++, tradeSid, orderSid, secSid, side, executionQty, executionPrice, tradeStatus);

		// Send price
		int bestBid = 11_000;
		int bestAsk = 12_000;
		int last = 11_000;
		Boobs boobs = Boobs.of(secSid, bestBid, bestAsk, last); 
		sendPrice(secSid, boobs);

		executionQty = 100;
		executionPrice = 12000;
		tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq++, tradeSid, orderSid, secSid, side, executionQty, executionPrice, tradeStatus);

		side = Side.BUY;
		executionQty = 100;
		executionPrice = 11000;
		tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq++, tradeSid, orderSid, secSid2, side, executionQty, executionPrice, tradeStatus);

		side = Side.SELL;
		executionQty = 50;
		executionPrice = 12000;
		tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq++, tradeSid, orderSid, secSid2, side, executionQty, executionPrice, tradeStatus);

		bestBid = 12_000;
		bestAsk = 13_000;
		last = 11_000;
		boobs = Boobs.of(secSid2, bestBid, bestAsk, last); 
		sendPrice(secSid, boobs);
		
		// When
		// Advance time
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();

		// Verify
		// Receive position update
		AtomicInteger expectedCount = new AtomicInteger(5);
		selfMessenger.receiver().positionHandlerList().add(new Handler<PositionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
				LOG.debug("Received {}", PositionDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();

				if (codec.entitySid() == secSid){
					AssertUtil.assertDouble(-100, codec.experimentalUnrealizedPnl(), "exp-unrealized-sec");					
				}
				else if (codec.entitySid() == secSid2){
					AssertUtil.assertDouble(50, codec.experimentalUnrealizedPnl(), "exp-unrealized-sec2");
				}
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndSecurityExistWhenReceiveTradeThenPositionIsUpdated(){
		// Given
		setToActiveState();
		sendAllPositionSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
		
		// Create security position 
		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 200010;
		int issuerSid = 400001;
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		service.createSecurityPosition(secSid, secType, putOrCall, undSecSid, issuerSid);
		assertTrue(service.secPositions().containsKey(secSid));
	
		PositionDetails beforeFirm = PositionDetails.cloneOf(service.firmPosition().details());
		PositionDetails beforeUnd = PositionDetails.cloneOf(service.undPositions().get(undSecSid).details());
		PositionDetails beforeIssuer = PositionDetails.cloneOf(service.issuerPositions().get(issuerSid).details());
		SecurityPositionDetails beforeSecurity = SecurityPositionDetails.cloneOf(service.secPositions().get(secSid).details()); 
		
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		Side side = Side.BUY;
		int tradeSid = 12122121;
		int executionPrice = 40000;
		int executionQty = 1000;
		TradeStatus tradeStatus = TradeStatus.NEW;
		sendTrade(channelId, channelSeq, tradeSid, orderSid, secSid, side, executionQty, executionPrice, tradeStatus);
		
		// Verify
		// Security position is updated
		assertTrue(hasSecurityPositionDetailsChanged(beforeSecurity, service.secPositions().get(secSid).details()));
		assertTrue(hasPositionDetailsChanged(beforeFirm, service.firmPosition().details()));
		assertTrue(hasPositionDetailsChanged(beforeUnd, service.undPositions().get(undSecSid).details()));
		assertTrue(hasPositionDetailsChanged(beforeIssuer, service.issuerPositions().get(issuerSid).details()));
		
		// When
		// Advance time
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		// Receive position update
		AtomicInteger expectedCount = new AtomicInteger(4);
		selfMessenger.receiver().positionHandlerList().add(new Handler<PositionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
				LOG.debug("Received {}", PositionDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndSecurityExistWhenReceiveOrderThenPositionIsUpdated(){
		// Given
		setToActiveState();
		sendAllPositionSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
		
		// Create security position 
		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 200010;
		int issuerSid = 400001;
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		service.createSecurityPosition(secSid, secType, putOrCall, undSecSid, issuerSid);

		PositionDetails beforeFirm = PositionDetails.cloneOf(service.firmPosition().details());
		PositionDetails beforeUnd = PositionDetails.cloneOf(service.undPositions().get(undSecSid).details());
		PositionDetails beforeIssuer = PositionDetails.cloneOf(service.issuerPositions().get(issuerSid).details());
		SecurityPositionDetails beforeSecurity = SecurityPositionDetails.cloneOf(service.secPositions().get(secSid).details()); 
		
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int quantity = 1000;
		Side side = Side.BUY;
		int limitPrice = 40000;
		OrderStatus status = OrderStatus.NEW; 
		sendOrder(channelId, channelSeq, orderSid, secSid, side, quantity, limitPrice, status);
		
		// Verify
		// Security position is updated
		assertTrue(hasPositionDetailsChanged(beforeIssuer, service.issuerPositions().get(issuerSid).details()));
		assertTrue(hasSecurityPositionDetailsChanged(beforeSecurity, service.secPositions().get(secSid).details()));
		assertTrue(hasPositionDetailsChanged(beforeFirm, service.firmPosition().details()));
		assertTrue(hasPositionDetailsChanged(beforeUnd, service.undPositions().get(undSecSid).details()));
		
		// When
		// Advance time
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		// Receive position update
		AtomicInteger expectedCount = new AtomicInteger(4);
		selfMessenger.receiver().positionHandlerList().add(new Handler<PositionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
				LOG.debug("Received {}", PositionDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		LOG.debug("[{}]", service.secPositions().get(secSid).details());
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndSecurityExistWhenReceiveOrderAgainThenPositionIsNotUpdated(){
		// Given
		setToActiveState();
		sendAllPositionSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
		
		// Create security position 
		long secSid = 200001;
		SecurityType secType = SecurityType.WARRANT;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 200010;
		int issuerSid = 400001;
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		service.createSecurityPosition(secSid, secType, putOrCall, undSecSid, issuerSid);

		PositionDetails beforeFirm = PositionDetails.cloneOf(service.firmPosition().details());
		PositionDetails beforeUnd = PositionDetails.cloneOf(service.undPositions().get(undSecSid).details());
		PositionDetails beforeIssuer = PositionDetails.cloneOf(service.issuerPositions().get(issuerSid).details());
		SecurityPositionDetails beforeSecurity = SecurityPositionDetails.cloneOf(service.secPositions().get(secSid).details()); 
		
		int channelId = 1;
		int channelSeq = 1000;
		int orderSid = 8000001;
		
		// When
		int quantity = 1000;
		Side side = Side.BUY;
		int limitPrice = 40000;
		OrderStatus status = OrderStatus.NEW; 
		sendOrder(channelId, channelSeq, orderSid, secSid, side, quantity, limitPrice, status);
		
		// Verify
		// Security position is updated
		assertTrue(hasPositionDetailsChanged(beforeIssuer, service.issuerPositions().get(issuerSid).details()));
		assertTrue(hasSecurityPositionDetailsChanged(beforeSecurity, service.secPositions().get(secSid).details()));
		assertTrue(hasPositionDetailsChanged(beforeFirm, service.firmPosition().details()));
		assertTrue(hasPositionDetailsChanged(beforeUnd, service.undPositions().get(undSecSid).details()));
		
		// When
		// Advance time
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		// Receive position update
		AtomicInteger expectedCount = new AtomicInteger(4);
		selfMessenger.receiver().positionHandlerList().add(new Handler<PositionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
				LOG.debug("Received {}", PositionDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		LOG.debug("[{}]", service.secPositions().get(secSid).details());
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenTimeoutThenSendTimerEventInternallyAndDistributeSnapshot() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify another TimerTask is queue
		assertEquals(2, testHelper.timerService().numOutstanding());
		long expiryNs = testHelper.timerService().nanoTime() + broadcastFrequency.toNanos();
		assertEquals(expiryNs, testHelper.timerService().timeouts().keySet().first().longValue());
	}
	
	public static void assertSecurityPositionDetailEquals(SecurityPositionDetails expected, SecurityPositionDetails actual){
		assertPositionDetailEquals(expected, actual);
		assertTrue(DoubleMath.fuzzyEquals(expected.avgBuyPrice(), actual.avgSellPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.avgSellPrice(), actual.avgSellPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.buyNotional(), actual.buyNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.mtmSellPrice(), actual.mtmSellPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.mtmBuyPrice(), actual.mtmBuyPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.osBuyNotional(), actual.osBuyNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.osSellNotional(), actual.osSellNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.sellNotional(), actual.sellNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.totalPnl(), actual.totalPnl(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.capUsed(), actual.capUsed(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.maxCapUsed(), actual.maxCapUsed(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertEquals(expected.hasMtmSellPrice(), actual.hasMtmSellPrice());
		assertEquals(expected.hasMtmBuyPrice(), actual.hasMtmBuyPrice());
	}
	
	public static void assertPositionDetailEquals(PositionDetails expected, PositionDetails actual){
		assertEquals(expected.entitySid(), actual.entitySid());
		assertEquals(expected.entityType(), actual.entityType());
		assertTrue(DoubleMath.fuzzyEquals(expected.commission(), actual.commission(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)); 
		assertTrue(DoubleMath.fuzzyEquals(expected.fees(), actual.fees(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)); 
		assertTrue(DoubleMath.fuzzyEquals(expected.netRealizedPnl(), actual.netRealizedPnl(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
		assertTrue(DoubleMath.fuzzyEquals(expected.unrealizedPnl(), actual.unrealizedPnl(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE));
	}
	
	public static boolean hasSecurityPositionDetailsChanged(SecurityPositionDetails expected, SecurityPositionDetails actual){
		boolean hasChange = hasPositionDetailsChanged(expected, actual);
		if (!DoubleMath.fuzzyEquals(expected.avgBuyPrice(), actual.avgBuyPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("avgBuyPrice changed [before:{}, after:{}]", expected.avgBuyPrice(), actual.avgBuyPrice());
			hasChange = true;
		}

		if (!DoubleMath.fuzzyEquals(expected.avgSellPrice(), actual.avgSellPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("avgSellPrice changed [before:{}, after:{}]", expected.avgSellPrice(), actual.avgSellPrice());
			hasChange = true;
		}

		if (!DoubleMath.fuzzyEquals(expected.buyNotional(), actual.buyNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("buyNotional changed [before:{}, after:{}]", expected.buyNotional(), actual.buyNotional());
			hasChange = true;
		}

		if (!DoubleMath.fuzzyEquals(expected.mtmSellPrice(), actual.mtmSellPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("mtmSellPrice changed [before:{}, after:{}]", expected.mtmSellPrice(), actual.mtmSellPrice());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.mtmBuyPrice(), actual.mtmBuyPrice(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("mtmBuyPrice changed [before:{}, after:{}]", expected.mtmBuyPrice(), actual.mtmBuyPrice());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.osBuyNotional(), actual.osBuyNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("osBuyNotional changed [before:{}, after:{}]", expected.osBuyNotional(), actual.osBuyNotional());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.osSellNotional(), actual.osSellNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("osSellNotional changed [before:{}, after:{}]", expected.osSellNotional(), actual.osSellNotional());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.sellNotional(), actual.sellNotional(), PositionChangeTracker.PRICE_EQUALITY_TOLERANCE)){
			LOG.debug("sellNotional changed [before:{}, after:{}]", expected.sellNotional(), actual.sellNotional());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.totalPnl(), actual.totalPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("totalPnl changed [before:{}, after:{}]", expected.totalPnl(), actual.totalPnl());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.experimentalNetRealizedPnl(), actual.experimentalNetRealizedPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("experimentalNetRealizedPnl changed [before:{}, after:{}]", expected.experimentalNetRealizedPnl(), actual.experimentalNetRealizedPnl());
			hasChange = true;
		}
		 
		if (!DoubleMath.fuzzyEquals(expected.experimentalUnrealizedPnl(), actual.experimentalUnrealizedPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("experimentalUnrealizedPnl changed [before:{}, after:{}]", expected.experimentalUnrealizedPnl(), actual.experimentalUnrealizedPnl());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.experimentalTotalPnl(), actual.experimentalTotalPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("experimentalTotalPnl changed [before:{}, after:{}]", expected.experimentalTotalPnl(), actual.experimentalTotalPnl());
			hasChange = true;
		}

		LOG.debug("unrealizedPnl: {}", expected.unrealizedPnl(), actual.unrealizedPnl());
		LOG.debug("experimentalUnrealizedPnl: {}", expected.experimentalUnrealizedPnl(), actual.experimentalUnrealizedPnl());
		return hasChange;
	}
	
	public static boolean hasPositionDetailsChanged(PositionDetails expected, PositionDetails actual){
		LOG.debug("[{}:{}]", expected.entityType().name(), expected.entitySid());
		assertEquals(expected.entitySid(), actual.entitySid());
		assertEquals(expected.entityType(), actual.entityType());
		
		boolean hasChange = false;
		if (!DoubleMath.fuzzyEquals(expected.commission(), actual.commission(), PositionChangeTracker.FEES_AND_COMMISSION_EQUALITY_TOLERANCE)){
			LOG.debug("commission changed [before:{}, after:{}]", expected.commission(), actual.commission());
			hasChange = true;
		}

		if (!DoubleMath.fuzzyEquals(expected.fees(), actual.fees(), PositionChangeTracker.FEES_AND_COMMISSION_EQUALITY_TOLERANCE)){
			LOG.debug("fees changed [before:{}, after:{}]", expected.fees(), actual.fees());
			hasChange = true;
		}

		if (!DoubleMath.fuzzyEquals(expected.netRealizedPnl(), actual.netRealizedPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("netRealizedPnl changed [before:{}, after:{}]", expected.netRealizedPnl(), actual.netRealizedPnl());
			hasChange = true;
		}

		if (!DoubleMath.fuzzyEquals(expected.unrealizedPnl(), actual.unrealizedPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("unrealizedPnl changed [before:{}, after:{}]", expected.unrealizedPnl(), actual.unrealizedPnl());
			hasChange = true;
		}
		
		if (!DoubleMath.fuzzyEquals(expected.capUsed(), actual.capUsed(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			LOG.debug("capUsed changed [before:{}, after:{}]", expected.capUsed(), actual.capUsed());
			hasChange = true;
		}
		
		if (Math.abs(expected.tradeCount() - actual.tradeCount()) > PositionChangeTracker.TRADE_COUNT_TOLERANCE){
			LOG.debug("tradeCount changed [before:{}, after:{}]", expected.tradeCount(), actual.tradeCount());
			hasChange = true;
		}
		return hasChange;
	}

	@Test
	public void givenActiveWhenReceiveSubscriptionRequestThenReplyOK() {
		// Given
		setToActiveState();
		
		// When
		sendAllPositionSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
	}

	private void sendAllPositionSubscription(Messenger messenger, RingBufferMessageSinkPoller poller, int channelId){
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		Request request = Request.of(wrapper.sink().sinkId(), 
				RequestType.SUBSCRIBE, 
				Parameters.listOf(
						Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_POSITION_UPDATE.value())))
				.clientKey(clientKey);
		CompletableFuture<Request> requestFuture = messenger.sendRequest(wrapper.sink(), request);
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		poller.pollAll();
		
		// 2. Send response to request to service
		mutableResponseHandler.impl(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				if (codec.clientKey() == request.clientKey() &&
						codec.isLast() == BooleanType.TRUE &&
								codec.resultType() == ResultType.OK){
					requestFuture.complete(request.resultType(ResultType.OK));
					LOG.debug("answered position subscription request in mockito");					
				}
			}
		});

		selfMessenger.responseSender().sendResponse(wrapper.sink(), 
				clientKey, 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK);
		wrapper.pushNextMessage();

		assertTrue(received.get());

		// Verify
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertEquals(1, service.allPosUpdSubs().size());
		assertTrue(service.allPosUpdSubs().contains(messenger.self()));
	}
	
	@Test
	public void givenActiveWhenReceiveRiskStateSubscriptionRequestThenReplyOK() {
		// Given
		setToActiveState();
		
		// When
		sendAllRiskStateChangeSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
	}
	
	private void sendAllRiskStateChangeSubscription(Messenger messenger, RingBufferMessageSinkPoller poller, int channelId){
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = messenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.SUBSCRIBE, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_RISK_CONTROL_STATE_UPDATE.value()))).clientKey(clientKey));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		poller.pollAll();
		assertTrue(received.get());

		// Verify
		PortfolioAndRiskService service = (PortfolioAndRiskService)wrapper.coreService();
		assertEquals(1, service.allRiskStateChangeSubs().size());
		assertTrue(service.allRiskStateChangeSubs().contains(messenger.self()));
	}

}
