package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.base.Strings;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.LineHandlerConfig;
import com.lunar.config.OrderAndTradeSnapshotServiceConfig;
import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.core.TriggerInfo;
import com.lunar.core.WaitStrategy;
import com.lunar.exception.ConfigurationException;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.request.RequestContext;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.MutableHandler;
import com.lunar.message.binary.OrderDecoder;
import com.lunar.message.binary.ResponseDecoder;
import com.lunar.message.binary.TradeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.order.OrderAndTradeSnapshotService.OrderBuffer;
import com.lunar.order.OrderAndTradeSnapshotService.TradeBuffer;
import com.lunar.service.MessageServiceExecutionContext;
import com.lunar.service.ServiceBuilder;
import com.lunar.service.ServiceConstant;
import com.lunar.service.ServiceFactory;
import com.lunar.service.ServiceLifecycleAware;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;

@RunWith(MockitoJUnitRunner.class)
public class OrderAndTradeSnapshotServiceTest {
	private static final Logger LOG = LogManager.getLogger(OrderManagementAndExecutionServiceTest.class);

	@Mock
	private ServiceStatusSender serviceStatusSender;
	@Mock
	private MessageSink adminSink;
	@Mock
	private MessageSink omesSink;
	@Mock
	private MessageSink persistSink;

	@Mock
	private RequestTracker requestTracker;

	private MessageSinkRef admin;
	private MessageSinkRef omes;
	private MessageSinkRef persist;
	private final int adminSinkId = 1;
	private final int omesSinkId = 3;
	private final int persistSinkId = 5;

	private ServiceTestHelper testHelper;
	private LunarServiceTestWrapper wrapper;

	// Test sender
	private final int selfSinkId = 8;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller;
	private RingBufferMessageSink selfRingBufferSink;
	private Messenger selfMessenger;
	private MessageSinkRef selfSinkRef;
	
	// Test sender 2
	private final int selfSinkId2 = 9;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller2;
	private RingBufferMessageSink selfRingBufferSink2;
	private Messenger selfMessenger2;
	private MessageSinkRef selfSinkRef2;

	private OrderAndTradeSnapshotServiceConfig config;
	private final int systemId = 1;
	private final int serviceSinkId = 2;
	private final int expectedNumChannels = 128;
	private final int expectedNumOrdersPerChannel = 32;
	private final int expectedNumTradesPerChannel = 64;
	private final int expectedNumOrders = 1024;
	private final int expectedNumTrades = 1024;
	private final Duration broadcastFrequency = Duration.ofSeconds(1);
	private final AtomicInteger clientKeySeq = new AtomicInteger(2000000);
	private MutableHandler<ResponseSbeDecoder> mutableResponseHandler;
	
	@Before
	public void setup(){
		testHelper = ServiceTestHelper.of();
		selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId, ServiceType.DashboardService, 256, "testDashboard");
		selfRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				selfMessenger.receive(event, 0);
				return false;
			}
		});
		selfRingBufferSink = selfRingBufferSinkPoller.sink();
		selfMessenger = testHelper.createMessenger(selfRingBufferSink, "self");
		selfMessenger.registerEvents();
		selfSinkRef = selfMessenger.self();

		selfRingBufferSinkPoller2 = testHelper.createRingBufferMessageSinkPoller(selfSinkId2, ServiceType.DashboardService, 256, "testDashboard2");
		selfRingBufferSinkPoller2.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				selfMessenger2.receive(event, 0);
				return false;
			}
		});
		selfRingBufferSink2 = selfRingBufferSinkPoller2.sink();
		selfMessenger2 = testHelper.createMessenger(selfRingBufferSink2, "self2");
		selfMessenger2.registerEvents();
		selfSinkRef2 = selfMessenger2.self();
		
		config = new OrderAndTradeSnapshotServiceConfig(systemId, 
				"test-order-snapshot", 
				"test-order-snapshot", 
				"test-order-snapshot", 
				ServiceType.OrderAndTradeSnapshotService, 
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
				expectedNumChannels, 
				expectedNumOrdersPerChannel, 
				expectedNumTradesPerChannel, 
				expectedNumOrders, 
				expectedNumTrades, 
				broadcastFrequency);
		
		adminSink = TestHelper.mock(adminSink, systemId, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");

		omesSink = TestHelper.mock(omesSink, systemId, omesSinkId, ServiceType.OrderManagementAndExecutionService);
		omes = MessageSinkRef.of(omesSink, "test-omes");

        persistSink = TestHelper.mock(persistSink, systemId, persistSinkId, ServiceType.PersistService);
        persist = MessageSinkRef.of(persistSink, "test-persist");

        ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return OrderAndTradeSnapshotService.of(serviceConfig, messageService);
			}
		};

		SenderBuilder senderBuilder = new TestSenderBuilder();
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(omes);
		wrapper.messenger().referenceManager().register(persist);
		wrapper.messenger().referenceManager().register(selfSinkRef);
		wrapper.messenger().referenceManager().register(selfSinkRef2);
		
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
					return OrderAndTradeSnapshotService.of(serviceConfig, messageService);
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
				helper.timerService());
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
	public void testInvaidArgument() throws ConfigurationException{
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return new OrderAndTradeSnapshotService(serviceConfig, messageService);
			}
		};
		
		LineHandlerConfig lineHandlerConfig = LineHandlerConfig.of(1, 
				"line handler", "line handler", com.lunar.order.NullLineHandlerEngine.class,
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
				3, 
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
		// given
		
		// when
		wrapper.messageService().onStart();
		
		// then
		assertEquals(States.WAITING_FOR_SERVICES, wrapper.messageService().state());
		ServiceStatusTracker tracker = wrapper.messenger().serviceStatusTracker();
		assertTrue(tracker.isTrackingService(ServiceType.AdminService));
		assertTrue(tracker.isTrackingService(ServiceType.OrderManagementAndExecutionService));
	}
	
	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToRecoveryState() throws UnsupportedEncodingException{
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return OrderAndTradeSnapshotService.of(serviceConfig, messageService);
			}
		};
		
		SenderBuilder senderBuilder = new TestSenderBuilder().requestTracker(requestTracker);
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(omes);
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
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						omes.sinkId(),
						omes.serviceType(),
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
		
		// verify
		// 1) state, 2) subscription request is sent
		assertEquals(States.RECOVERY, wrapper.messageService().state());
		ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
		verify(requestTracker, times(1)).sendAndTrack(eq(omes), argumentCaptor.capture());
		Request request = argumentCaptor.getValue();
		LOG.debug("clientKey:{}", request.clientKey());
		assertEquals(RequestType.SUBSCRIBE, request.requestType());
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
						omes.sinkId(),
						omes.serviceType(),
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

        // verify
		// 1) state
        // 2) subscription request is sent
        assertEquals(States.RECOVERY, wrapper.messageService().state());
		assertEquals(1, wrapper.messengerWrapper().requestTracker().requests().size());
        
		// when - send a response to ack its subscription request
		RequestContext requestContext = wrapper.messengerWrapper().requestTracker().requests().values().iterator().next();
		selfMessenger.responseSender().sendResponse(wrapper.sink(), 
				requestContext.request().clientKey(), 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE,
				ResultType.OK);
		wrapper.pushNextMessage();
		
        // 3) get request is sent
		// when - send a response to ack its get request
		assertEquals(1, wrapper.messengerWrapper().requestTracker().requests().size());
		requestContext = wrapper.messengerWrapper().requestTracker().requests().values().iterator().next();
		selfMessenger.responseSender().sendResponse(wrapper.sink(),
				requestContext.request().clientKey(), 
				BooleanType.TRUE, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				ResultType.OK);
		wrapper.pushNextMessage();
		
		assertEquals(States.ACTIVE, wrapper.messageService().state());
		
		// Verify timer task is set, with appropriate delay
		assertEquals(1, testHelper.timerService().numOutstanding());
		long expiryNs = testHelper.timerService().nanoTime() + broadcastFrequency.toNanos();
		assertEquals(expiryNs, testHelper.timerService().timeouts().keySet().first().longValue());

		// verify - state is ACTIVE
		assertEquals(States.ACTIVE, wrapper.messageService().state());
	}
	
	@Test
	public void givenReadyWhenReceiveSubscriptionAckThenMoveToActiveState() {
		advanceToActiveState(wrapper, selfMessenger);
	}

	private void sendOrderAcceptedWithOrderInfo(int channelId, long channelSeq, int orderSid, boolean isFirstUpdateInOrderLifecycle){
		sendOrderAcceptedWithOrderInfo(400000, channelId, channelSeq, orderSid, isFirstUpdateInOrderLifecycle);
	}
	
	private void sendOrderAcceptedWithOrderInfo(long secSid, int channelId, long channelSeq, int orderSid, boolean isFirstUpdateInOrderLifecycle){
		// Encode order accepted into buffer
		OrderStatus status = OrderStatus.NEW;
		int price = 110000;
		int cumulativeQty = 0;
		int leavesQty = 300000;
		Side side = Side.BUY;
		int orderId = 91231212;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific
		int quantity = 300000;
		BooleanType isAlgo = BooleanType.FALSE;
		ExecutionType execType = ExecutionType.NEW;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int limitPrice = 110000;
		int stopPrice = -1;
		TimeInForce tif = TimeInForce.DAY;
		
		MutableDirectBuffer orderAcceptedBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderAcceptedOnly(orderAcceptedBuffer, 
				0, 
				new OrderAcceptedSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid, 
				status, 
				price, 
				cumulativeQty, 
				leavesQty, 
				side, 
				orderId, 
				secSid, 
				execType,
				updateTime);
		
		OrderAcceptedSbeDecoder accepted = new OrderAcceptedSbeDecoder().wrap(orderAcceptedBuffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		
		long createTime = LocalTime.now().toNanoOfDay();
		byte triggeredBy = 3;
		int triggerSeqNum = 7272911;
		long timestamp = LocalTime.now().toNanoOfDay();
		TriggerInfo triggerInfo = TriggerInfo.of().triggeredBy(triggeredBy).triggerSeqNum(triggerSeqNum).triggerNanoOfDay(timestamp);
		int encodedLength = selfMessenger.orderSender().sendOrderAcceptedWithOrderInfo(omes,
				channelId, channelSeq, 
				orderAcceptedBuffer, 
				0, 
				accepted,
				Order.of(secSid, 
						this.selfSinkRef, 
						orderSid, 
						quantity, 
						isAlgo, 
						orderType, 
						side, 
						limitPrice,
						stopPrice, 
						tif,
						status,
						createTime,
						updateTime,
						triggerInfo), 
				selfMessenger.internalEncodingBuffer(), 
				0);
		MessageSinkRef[] subs = new MessageSinkRef[1];
		long[] results = new long[1];
		subs[0] = wrapper.sink();
		long result = -1;
		if (encodedLength > 0){
			result = selfMessenger.trySend(subs, 
					selfMessenger.internalEncodingBuffer(), 
					0, 
					encodedLength,
					results);
			wrapper.pushNextMessage();
		}
		assertEquals(0, result);
		
		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(orderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(orderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(isAlgo, orderDecoder.isAlgoOrder());
		assertEquals(orderSid, orderDecoder.orderSid());
		assertEquals(orderType, orderDecoder.orderType());
		assertEquals(quantity, orderDecoder.quantity());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		if (isFirstUpdateInOrderLifecycle){
			assertEquals(updateTime, orderDecoder.createTime());
			assertEquals(OrderSbeDecoder.updateTimeNullValue(), orderDecoder.updateTime());
		}
		else {
			assertEquals(updateTime, orderDecoder.updateTime());
		}
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertTrue(bufferSet.size() > 0);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}
	
	@Test
	public void givenActiveWhenReceiveOrderAcceptedThenStoreUpdatesWithValidSequence() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		sendOrderAcceptedWithOrderInfo(1, 200001, 11111, true);
	}
	
	@Test
	public void givenActiveWhenReceiveSubscriptionRequestThenReplyOK() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, 1);
	}

	private void sendAllOrderAndTradeSubscription(Messenger messenger, RingBufferMessageSinkPoller poller, int channelId){
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = messenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.SUBSCRIBE, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()),
								Parameter.of(ParameterType.CHANNEL_ID, channelId))).clientKey(clientKey));
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
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertEquals(1, service.subscriptionChannels().get(channelId).numOrderSubscribers());
		assertEquals(1, service.subscriptionChannels().get(channelId).numTradeSubscribers());
	}
	
	@Test
	public void givenActiveWhenReceiveTradeOnlySubscriptionRequestThenReplyOK() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		sendTradeOnlySubscription();		
	}
	
	private void sendTradeOnlySubscription(){
		int clientKey = 12345;
		int channelId = 1;
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.SUBSCRIBE, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.TRADE_UPDATE.value()),
								Parameter.of(ParameterType.CHANNEL_ID, channelId))).clientKey(clientKey));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();
		assertTrue(received.get());

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertNotNull(service.subscriptionChannels().get(channelId));
		assertEquals(1, service.subscriptionChannels().get(channelId).numTradeSubscribers());
		assertEquals(0, service.subscriptionChannels().get(channelId).numOrderSubscribers());
	}

	@Test
	public void givenActiveWhenTimeoutThenSendTimerEventInternallyAndDistributeSnapshot() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify another TimerTask is queue
		assertEquals(1, testHelper.timerService().numOutstanding());
		long expiryNs = testHelper.timerService().nanoTime() + broadcastFrequency.toNanos();
		assertEquals(expiryNs, testHelper.timerService().timeouts().keySet().first().longValue());
	}
	
	@Test
	public void givenActiveAndHasActiveSubscriptionWhenReceiveOrderAcceptedThenDistributeSnapshotToSubscribers() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		sendOrderAcceptedWithOrderInfo(1, 200001l, 11111, true);
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndHasActiveSubscriptionWhenReceiveMultipleOrderUpdatesForSameOrderSidThenDistributeSnapshotOnceToSubscribers() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		sendOrderAcceptedWithOrderInfo(channelId, 20000, 11111, true);
		int count = 10;
		for (int i = 1; i < count; i++){
			sendOrderAcceptedWithOrderInfo(channelId, 20000 + i, 11111, false);
			wrapper.pushNextMessage();
		}
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
	}

	private void sendTradeCreatedWithOrderInfo(long channelSeq, int tradeSid){
		sendTradeCreatedWithOrderInfo(channelSeq, tradeSid, 400000, true);
	}
	
	private void sendTradeCreatedWithOrderInfo(long channelSeq, int tradeSid, long secSid, boolean expectedIncInTradeBufferCount){
		// Encode order accepted into buffer
		int channelId = 1;
		int orderSid = 200001;
		OrderStatus status = OrderStatus.NEW;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		int orderId = 91231212;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific
		int quantity = 100;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int limitPrice = 110000;
		int stopPrice = -1;
		TimeInForce tif = TimeInForce.DAY;
		
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		MutableDirectBuffer tradeCreatedBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		int executionPrice = 110000;
		int executionQty = 100;
		String executionId = Strings.padEnd("123456", TradeCreatedSbeEncoder.executionIdLength(), ' ');
		OrderSender.encodeTradeCreatedOnly(tradeCreatedBuffer, 
				0, 
				new TradeCreatedSbeEncoder(), 
				channelId, 
				channelSeq,
				tradeSid,
				orderSid, 
				orderId, 
				status, 
				side, 
				leavesQty,
				cumulativeQty,
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				executionPrice,
				executionQty,
				secSid,
				updateTime);
		
		selfMessenger.orderSender().sendTradeCreatedWithOrderInfo(wrapper.sink(), 
				channelId, channelSeq, 
				tradeCreatedBuffer, 
				0, 
				new TradeCreatedSbeDecoder().wrap(tradeCreatedBuffer, 0, TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION),
				Order.of(secSid, 
						this.selfSinkRef, 
						orderSid, 
						quantity, 
						isAlgo, 
						orderType, 
						side, 
						limitPrice,
						stopPrice, 
						tif,
						status), 
				messageBuffer, 
				0);
		
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		int origTradeBufferSize = service.tradesByChannelId().get(channelId).size();
		
		wrapper.pushNextMessage();
		
		// Verify
		assertTrue(service.tradeBySid().containsKey(tradeSid));
		TradeBuffer tradeBuffer = service.tradeBySid().get(tradeSid);
		
		TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
		tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, tradeDecoder.channelId());
		assertEquals(channelSeq, tradeDecoder.channelSnapshotSeq());
		assertEquals(channelSeq, tradeDecoder.channelSnapshotSeq());
		assertEquals(orderSid, tradeDecoder.orderSid());
		assertEquals(secSid, tradeDecoder.secSid());
		assertEquals(status, tradeDecoder.status());
		assertEquals(leavesQty, tradeDecoder.leavesQty());
		assertEquals(cumulativeQty, tradeDecoder.cumulativeQty());
		assertEquals(0, executionId.compareTo(new String(stringBuffer.byteArray(), 0, tradeDecoder.getExecutionId(stringBuffer.byteArray(), 0))));
		assertEquals(executionPrice, tradeDecoder.executionPrice());
		assertEquals(executionQty, tradeDecoder.executionQty());
		
		Set<TradeBuffer> bufferSet = service.tradesByChannelId().get(channelId);
		if (expectedIncInTradeBufferCount){
			assertEquals(origTradeBufferSize + 1, bufferSet.size());
		}
		else{
			assertEquals(origTradeBufferSize, bufferSet.size());
		}
		assertEquals(1, bufferSet.stream().filter(new Predicate<TradeBuffer>() {
			@Override
			public boolean test(TradeBuffer t) {
				return t == tradeBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveWhenReceiveTradeCreatedThenStoreUpdatesWithValidSequence() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		
		// When
		sendTradeCreatedWithOrderInfo(200001, 111111);
	}

	@Test
	public void givenActiveAndHasActiveSubscriptionWhenReceiveTradeCreatedThenDistributeSnapshotToSubscribers() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		sendTradeCreatedWithOrderInfo(200001l, 111111);
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		AtomicInteger expectedCount = new AtomicInteger(2);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndHasActiveSubscriptionWhenReceiveMultipleTradeCreatedOfDiffSidsThenDistributeSnapshotAccordingToChannelSeq(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		int count = 10;
		for (int i = 1; i <= count; i++){
			sendTradeCreatedWithOrderInfo(20000 + i, 111111 + i, i, true);
			wrapper.pushNextMessage();
		}
		// Send further updates for several securities, same trades 
		count = 5;
		for (int i = 1; i <= count; i++){
			sendTradeCreatedWithOrderInfo(20000 + 10 + i, 111111 + i, i, false);
			wrapper.pushNextMessage();
		}
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify - receive snapshot 15 times
		AtomicInteger expectedCount = new AtomicInteger(11);
		LongRBTreeSet seqs = new LongRBTreeSet();
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
				seqs.add(codec.channelSnapshotSeq());
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
				seqs.add(codec.channelSnapshotSeq());
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
		assertEquals(20006, seqs.firstLong());
		assertEquals(20015, seqs.lastLong());
	}
	
	@Test
	public void givenActiveAndHasActiveSubscriptionWhenReceiveMultipleOrderAcceptedOfDiffSidsThenDistributeSnapshotAccordingToChannelSeq(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 2;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		int count = 10;
		for (int i = 1; i <= count; i++){
			sendOrderAcceptedWithOrderInfo(i, 2, 20000 + i, 111111 + i, true);
			wrapper.pushNextMessage();
		}
		// Send further updates for several securities, same trades 
		count = 5;
		for (int i = 1; i <= count; i++){
			sendOrderAcceptedWithOrderInfo(i, 2, 20000 + 10 + i, 111111 + i, false);
			wrapper.pushNextMessage();
		}
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify - receive snapshot 15 times		
		AtomicInteger expectedCount = new AtomicInteger(10);
		LongRBTreeSet seqs = new LongRBTreeSet();
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
				seqs.add(codec.channelSnapshotSeq());
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
				seqs.add(codec.channelSnapshotSeq());
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
		assertEquals(20006, seqs.firstLong());
		assertEquals(20015, seqs.lastLong());
	}

	@Test
	public void givenActiveAndHasActiveSubscriptionWhenReceiveMulitpleTradeCreatedOfSameSidsThenDistributeSnapshotToSubscribers() {
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		int count = 10;
		for (int i = 0; i < count; i++){
			sendTradeCreatedWithOrderInfo(20000 + i, 111111 + i);
			wrapper.pushNextMessage();
		}
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		// Verify
		AtomicInteger expectedCount = new AtomicInteger(11);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveAndNoActiveSubscriptionWhenReceiveUnsubscribeRequestThenDoNothing(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);

		int clientKey = 12345;
		int channelId = 1;
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.UNSUBSCRIBE, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()),
								Parameter.of(ParameterType.CHANNEL_ID, channelId))).clientKey(clientKey));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();
		assertTrue(received.get());

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertEquals(0, service.subscriptionChannels().get(channelId).numOrderSubscribers());
		assertEquals(0, service.subscriptionChannels().get(channelId).numTradeSubscribers());
	}
	
	@Test
	public void givenActiveAndActiveSubscriptionWhenReceiveUnsubscribeRequestThenDoNothing(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);

		int clientKey = 12347;
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.UNSUBSCRIBE, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()))).clientKey(clientKey));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();
		assertTrue(received.get());

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertEquals(0, service.subscriptionChannels().get(channelId).numOrderSubscribers());
		assertEquals(0, service.subscriptionChannels().get(channelId).numTradeSubscribers());
	}
	
	@Test
	public void givenActiveWhenReceivesOrderUpdatesOutOfSyncThenStillOK(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);
		
		// When
		sendOrderAcceptedWithOrderInfo(channelId, 200001l, 11111, true);
		sendOrderAccepted(channelId, 200010l, 11113);

		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
	}
	
	@Test
	public void givenActiveAndOrderOutstandingsWhenReceiveGetThenReturnResult(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderAcceptedWithOrderInfo(1, 200001l, 11111, true);
		
		// When
		int clientKey = 12345;
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.GET, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()),
								Parameter.of(ParameterType.CHANNEL_ID, 1))).clientKey(clientKey));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		
		// Verify
		AtomicInteger expectedCount = new AtomicInteger(3);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.debug("Received {}", ResponseDecoder.decodeToString(codec));
				expectedCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
		assertTrue(received.get());
	}
	
	@Test
	public void givenActiveAndTradeOutstandingsWhenReceiveGetThenReturnResult(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendTradeCreatedWithOrderInfo(200001l, 11111);
		
		// When
		int clientKey = 12345;
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.GET, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()),
								Parameter.of(ParameterType.CHANNEL_ID, 1))).clientKey(clientKey));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();
		
		// Verify
		AtomicInteger expectedResponse = new AtomicInteger(3);
		AtomicInteger expectedOrder = new AtomicInteger(1);
		AtomicInteger expectedTrade = new AtomicInteger(1);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedOrder.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedTrade.decrementAndGet();
			}
		});
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.debug("Received {}", ResponseDecoder.decodeToString(codec));
				expectedResponse.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedResponse.get());
		assertEquals(0, expectedOrder.get());
		assertEquals(0, expectedTrade.get());
		assertTrue(received.get());
	}
	
	@Test
	public void givenActiveSendSnapshotThreeTimesOnlyFirstTimeIsSent(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId = 1;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId);

		sendOrderAcceptedWithOrderInfo(channelId, 200001l, 11111, true);

		AtomicInteger expectedResponse = new AtomicInteger(0);
		AtomicInteger expectedOrder = new AtomicInteger(1);
		AtomicInteger expectedTrade = new AtomicInteger(0);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				expectedOrder.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				expectedTrade.decrementAndGet();
			}
		});
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.debug("Received {}", ResponseDecoder.decodeToString(codec));
				expectedResponse.decrementAndGet();
			}
		});
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedResponse.get());
		assertEquals(0, expectedOrder.get());
		assertEquals(0, expectedTrade.get());

		sendOrderAcceptedWithOrderInfo(channelId, 200002l, 11111, false);

		expectedResponse.set(0);
		expectedOrder.set(1);
		expectedTrade.set(0);
		testHelper.timerService().advance(broadcastFrequency);
		wrapper.pushNextMessage();
		
		selfRingBufferSinkPoller.pollAll();
		assertAllZeroes(expectedResponse, expectedOrder, expectedTrade);
	}
	
	private void sendOrderAccepted(int channelId, long channelSeq, int orderSid){
		// Encode order accepted into buffer
		OrderStatus status = OrderStatus.NEW;
		int price = 110000;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific
		ExecutionType execType = ExecutionType.NEW;
		
		MutableDirectBuffer orderAcceptedBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderAcceptedOnly(orderAcceptedBuffer, 
				0, 
				new OrderAcceptedSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid, 
				status, 
				price, 
				cumulativeQty, 
				leavesQty, 
				side, 
				orderId, 
				secSid, 
				execType,
				updateTime);
		
		selfMessenger.orderSender().sendOrderAccepted(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderAcceptedBuffer, 
				0,
				new OrderAcceptedSbeDecoder().wrap(orderAcceptedBuffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION),
				messageBuffer, 
				0);
		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(orderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(orderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(BooleanType.NULL_VAL, orderDecoder.isAlgoOrder());
		assertEquals(orderSid, orderDecoder.orderSid());
		assertEquals(OrderType.NULL_VAL, orderDecoder.orderType());
		assertEquals(OrderSbeEncoder.quantityNullValue(), orderDecoder.quantity());
		assertEquals(OrderSbeEncoder.stopPriceNullValue(), orderDecoder.stopPrice());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}
	
	private void sendOrderCancelled(int channelId, long channelSeq, int orderSid, int origOrderSid, boolean isFirstUpdateInOrderLifecycle){
		// Encode order accepted into buffer
		OrderStatus status = OrderStatus.CANCELLED;
		int price = 110000;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific
		ExecutionType execType = ExecutionType.NEW;
		
		MutableDirectBuffer orderCancelledBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderCancelledOnly(orderCancelledBuffer, 
				0, 
				new OrderCancelledSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid,
				origOrderSid,
				status, 
				price, 
				side, 
				orderId, 
				secSid, 
				execType, 
				leavesQty,
				cumulativeQty,
				updateTime,
				0 /* quantity not avail from exchange */);
		
		selfMessenger.orderSender().sendOrderCancelled(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderCancelledBuffer, 
				0,
				new OrderCancelledSbeDecoder().wrap(orderCancelledBuffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION),
				messageBuffer, 
				0);
		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(origOrderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(origOrderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(origOrderSid, orderDecoder.orderSid());
		assertEquals(orderId, orderDecoder.orderId());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(side, orderDecoder.side());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		assertEquals(updateTime, orderDecoder.updateTime());
		if (isFirstUpdateInOrderLifecycle){
			assertEquals(OrderSbeEncoder.createTimeNullValue(), orderDecoder.createTime());
			assertEquals(BooleanType.NULL_VAL, orderDecoder.isAlgoOrder());
			assertEquals(OrderType.NULL_VAL, orderDecoder.orderType());
			assertEquals(OrderSbeEncoder.quantityNullValue(), orderDecoder.quantity());
			assertEquals(OrderSbeEncoder.stopPriceNullValue(), orderDecoder.stopPrice());
		}
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}
	
	@Test
	public void givenActiveReceiveCancelled(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderCancelled(1, 1, 11111, 11110, true);
	}

	private void sendOrderCancelledWithOrderInfo(long channelSeq, int orderSid){
		// Encode order accepted into buffer
		int channelId = 1;
		OrderStatus status = OrderStatus.CANCELLED;
		int price = 110000;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific
		ExecutionType execType = ExecutionType.NEW;
		int quantity = 100;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int limitPrice = 110000;
		int stopPrice = -1;
		TimeInForce tif = TimeInForce.DAY;

		MutableDirectBuffer orderCancelledBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		int origOrderSid = 22222;
		OrderSender.encodeOrderCancelledOnly(orderCancelledBuffer, 
				0, 
				new OrderCancelledSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid,
				origOrderSid,
				status, 
				price, 
				side, 
				orderId, 
				secSid, 
				execType, 
				leavesQty,
				cumulativeQty,
				updateTime,
				leavesQty + cumulativeQty);
		
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		selfMessenger.orderSender().sendOrderCancelledWithOrderInfo(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderCancelledBuffer, 
				0,
				new OrderCancelledSbeDecoder().wrap(orderCancelledBuffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION),
				Order.of(secSid, 
						this.selfSinkRef, 
						orderSid, 
						quantity, 
						isAlgo, 
						orderType, 
						side, 
						limitPrice,
						stopPrice, 
						tif,
						status), 
				messageBuffer, 
				0);

		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(origOrderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(origOrderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(origOrderSid, orderDecoder.orderSid());
		assertEquals(orderId, orderDecoder.orderId());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(side, orderDecoder.side());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		assertEquals(updateTime, orderDecoder.createTime());
		assertEquals(updateTime, orderDecoder.updateTime());
		assertEquals(isAlgo, orderDecoder.isAlgoOrder());
		assertEquals(orderType, orderDecoder.orderType());
		assertEquals(quantity, orderDecoder.quantity());
		assertEquals(stopPrice, orderDecoder.stopPrice());
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveReceiveCancelledWithOrderInfo(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderCancelledWithOrderInfo(1, 11111);
	}

	private void sendOrderRejectedWithOrderInfo(long channelSeq, int orderSid){
		// Encode order accepted into buffer
		int channelId = 1;
		OrderStatus status = OrderStatus.REJECTED;
		int price = 110000;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		OrderRejectType rejectType = OrderRejectType.INSUFFICIENT_LONG_POSITION;
		String reason = "INSUFFICIENT_LONG_POSITION";
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderRejectedSbeEncoder.reasonLength()));
		
		// Order specific
		int quantity = 100;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int limitPrice = 110000;
		int stopPrice = -1;
		TimeInForce tif = TimeInForce.DAY;

		MutableDirectBuffer orderRejectedBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderRejectedOnly(orderRejectedBuffer, 
				0, 
				new OrderRejectedSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid,
				orderId, 
				secSid, 
				side, 
				price, 
				cumulativeQty,
				leavesQty,
				status, 
				rejectType, 
				OrderSender.prepareRejectedReason(reason, byteBuffer),
				updateTime);
		
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		selfMessenger.orderSender().sendOrderRejectedWithOrderInfo(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderRejectedBuffer, 
				0,
				new OrderRejectedSbeDecoder().wrap(orderRejectedBuffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION),
				Order.of(secSid, 
						this.selfSinkRef, 
						orderSid, 
						quantity, 
						isAlgo, 
						orderType, 
						side, 
						limitPrice,
						stopPrice, 
						tif,
						status), 
				messageBuffer, 
				0);
		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(orderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(orderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(orderSid, orderDecoder.orderSid());
		assertEquals(orderId, orderDecoder.orderId());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(side, orderDecoder.side());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		assertEquals(updateTime, orderDecoder.createTime());
		assertEquals(updateTime, orderDecoder.updateTime());
		assertEquals(isAlgo, orderDecoder.isAlgoOrder());
		assertEquals(orderType, orderDecoder.orderType());
		assertEquals(quantity, orderDecoder.quantity());
		assertEquals(stopPrice, orderDecoder.stopPrice());
		assertEquals(rejectType, orderDecoder.orderRejectType());
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveReceiveRejectedWithOrderInfo(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderRejectedWithOrderInfo(1, 11111);
	}

	private void sendOrderRejected(long channelSeq, int orderSid){
		// Encode order accepted into buffer
		int channelId = 1;
		OrderStatus status = OrderStatus.REJECTED;
		int price = 110000;
		int cumulativeQty = 100;
		int leavesQty = 0;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		OrderRejectType rejectType = OrderRejectType.INSUFFICIENT_LONG_POSITION;
		String reason = "INSUFFICIENT_LONG_POSITION";
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderRejectedSbeEncoder.reasonLength()));

		// Order specific

		MutableDirectBuffer orderRejectedBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderRejectedOnly(orderRejectedBuffer, 
				0, 
				new OrderRejectedSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid,
				orderId, 
				secSid, 
				side, 
				price, 
				cumulativeQty,
				leavesQty,
				status, 
				rejectType, 
				OrderSender.prepareRejectedReason(reason, byteBuffer),
				updateTime);
		
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		selfMessenger.orderSender().sendOrderRejected(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderRejectedBuffer, 
				0,
				new OrderRejectedSbeDecoder().wrap(orderRejectedBuffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION),
				messageBuffer, 
				0);
		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(orderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(orderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(orderSid, orderDecoder.orderSid());
		assertEquals(orderId, orderDecoder.orderId());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(side, orderDecoder.side());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		assertEquals(OrderSbeDecoder.createTimeNullValue(), orderDecoder.createTime());
		assertEquals(updateTime, orderDecoder.updateTime());
		assertEquals(BooleanType.NULL_VAL, orderDecoder.isAlgoOrder());
		assertEquals(OrderType.NULL_VAL, orderDecoder.orderType());
		assertEquals(OrderSbeDecoder.quantityNullValue(), orderDecoder.quantity());
		assertEquals(OrderSbeDecoder.stopPriceNullValue(), orderDecoder.stopPrice());
		assertEquals(rejectType, orderDecoder.orderRejectType());
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveReceiveRejected(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderRejected(1, 11111);
	}
	
	private void sendOrderExpired(long channelSeq, int orderSid){
		// Encode order accepted into buffer
		int channelId = 1;
		OrderStatus status = OrderStatus.EXPIRED;
		int price = 110000;
		int cumulativeQty = 100;
		int leavesQty = 0;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific

		MutableDirectBuffer orderExpiredBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderExpiredOnly(orderExpiredBuffer, 
				0, 
				new OrderExpiredSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid,
				orderId, 
				secSid, 
				side, 
				price, 
				cumulativeQty,
				leavesQty,
				status, 
				updateTime);
		
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		selfMessenger.orderSender().sendOrderExpired(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderExpiredBuffer, 
				0,
				new OrderExpiredSbeDecoder().wrap(orderExpiredBuffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION),
				messageBuffer, 
				0);
		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(orderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(orderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(orderSid, orderDecoder.orderSid());
		assertEquals(orderId, orderDecoder.orderId());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(side, orderDecoder.side());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		assertEquals(OrderSbeDecoder.createTimeNullValue(), orderDecoder.createTime());
		assertEquals(updateTime, orderDecoder.updateTime());
		assertEquals(BooleanType.NULL_VAL, orderDecoder.isAlgoOrder());
		assertEquals(OrderType.NULL_VAL, orderDecoder.orderType());
		assertEquals(OrderSbeDecoder.quantityNullValue(), orderDecoder.quantity());
		assertEquals(OrderSbeDecoder.stopPriceNullValue(), orderDecoder.stopPrice());
		assertEquals(OrderRejectType.NULL_VAL, orderDecoder.orderRejectType());
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveReceiveExpired(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderExpired(1, 11111);
	}
	
	private void sendOrderExpiredWithOrderInfo(long channelSeq, int orderSid){
		// Encode order accepted into buffer
		int channelId = 1;
		OrderStatus status = OrderStatus.EXPIRED;
		int price = 110000;
		int cumulativeQty = 100;
		int leavesQty = 0;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		
		// Order specific
		// Order specific
		int quantity = 100;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int limitPrice = 110000;
		int stopPrice = -1;
		TimeInForce tif = TimeInForce.DAY;
		
		MutableDirectBuffer orderExpiredBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		OrderSender.encodeOrderExpiredOnly(orderExpiredBuffer, 
				0, 
				new OrderExpiredSbeEncoder(), 
				channelId, 
				channelSeq, 
				orderSid,
				orderId, 
				secSid, 
				side, 
				price, 
				cumulativeQty,
				leavesQty,
				status, 
				updateTime);
		
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		selfMessenger.orderSender().sendOrderExpiredWithOrderInfo(wrapper.sink(), 
				channelId, 
				channelSeq, 
				orderExpiredBuffer, 
				0,
				new OrderExpiredSbeDecoder().wrap(orderExpiredBuffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION),
				Order.of(secSid, 
						this.selfSinkRef, 
						orderSid, 
						quantity, 
						isAlgo, 
						orderType, 
						side, 
						limitPrice,
						stopPrice, 
						tif,
						status), 
				messageBuffer, 
				0);
		wrapper.pushNextMessage();

		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.orderBySid().containsKey(orderSid));
		OrderBuffer orderBuffer = service.orderBySid().get(orderSid);
		
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, orderDecoder.channelId());
		assertEquals(channelSeq, orderDecoder.channelSnapshotSeq());
		assertEquals(orderSid, orderDecoder.orderSid());
		assertEquals(orderId, orderDecoder.orderId());
		assertEquals(secSid, orderDecoder.secSid());
		assertEquals(side, orderDecoder.side());
		assertEquals(status, orderDecoder.status());
		assertEquals(leavesQty, orderDecoder.leavesQty());
		assertEquals(cumulativeQty, orderDecoder.cumulativeQty());
		assertEquals(updateTime, orderDecoder.createTime());
		assertEquals(updateTime, orderDecoder.updateTime());
		assertEquals(isAlgo, orderDecoder.isAlgoOrder());
		assertEquals(orderType, orderDecoder.orderType());
		assertEquals(quantity, orderDecoder.quantity());
		assertEquals(stopPrice, orderDecoder.stopPrice());
		assertEquals(OrderRejectType.NULL_VAL, orderDecoder.orderRejectType());
		
		Set<OrderBuffer> bufferSet = service.ordersByChannelId().get(channelId);
		assertEquals(1, bufferSet.stream().filter(new Predicate<OrderBuffer>() {
			@Override
			public boolean test(OrderBuffer t) {
				return t == orderBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveReceiveExpiredWithOrderInfo(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderExpiredWithOrderInfo(1, 11111);
	}

	private void sendTradeCreated(int channelId, long channelSeq, int tradeSid, int orderSid){
		// Encode order accepted into buffer
		OrderStatus status = OrderStatus.NEW;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		int orderId = 91231212;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		byte[] byteBuffer = new byte[128];
		
		// Order specific
		
		MutableDirectBuffer tradeCreatedBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		MutableDirectBuffer messageBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		int executionPrice = 110000;
		int executionQty = 100;
		String executionId = Strings.padEnd("123456", TradeCreatedSbeEncoder.executionIdLength(), ' ');
		OrderSender.encodeTradeCreatedOnly(tradeCreatedBuffer, 
				0, 
				new TradeCreatedSbeEncoder(), 
				channelId, 
				channelSeq,
				tradeSid,
				orderSid, 
				orderId, 
				status, 
				side, 
				leavesQty,
				cumulativeQty,
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				executionPrice,
				executionQty,
				secSid,
				updateTime);
		
		selfMessenger.orderSender().sendTradeCreated(wrapper.sink(), 
				channelId, 
				channelSeq, 
				tradeCreatedBuffer, 
				0, 
				new TradeCreatedSbeDecoder().wrap(tradeCreatedBuffer, 0, TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION),
				messageBuffer, 
				0);
		wrapper.pushNextMessage();
		
		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.tradeBySid().containsKey(tradeSid));
		TradeBuffer tradeBuffer = service.tradeBySid().get(tradeSid);
		
		TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
		tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, tradeDecoder.channelId());
		assertEquals(channelSeq, tradeDecoder.channelSnapshotSeq());
		assertEquals(channelSeq, tradeDecoder.channelSnapshotSeq());
		assertEquals(orderSid, tradeDecoder.orderSid());
		assertEquals(secSid, tradeDecoder.secSid());
		assertEquals(status, tradeDecoder.status());
		assertEquals(leavesQty, tradeDecoder.leavesQty());
		assertEquals(cumulativeQty, tradeDecoder.cumulativeQty());
		assertEquals(0, executionId.compareTo(new String(byteBuffer, 0, tradeDecoder.getExecutionId(byteBuffer, 0))));
		assertEquals(executionPrice, tradeDecoder.executionPrice());
		assertEquals(executionQty, tradeDecoder.executionQty());
		assertEquals(TradeSbeDecoder.updateTimeNullValue(), tradeDecoder.updateTime());
		assertEquals(updateTime, tradeDecoder.createTime());
		
		Set<TradeBuffer> bufferSet = service.tradesByChannelId().get(channelId);
		assertEquals(1, bufferSet.size());
		assertEquals(1, bufferSet.stream().filter(new Predicate<TradeBuffer>() {
			@Override
			public boolean test(TradeBuffer t) {
				return t == tradeBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	public void givenActiveReceiveTradeCreated(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendTradeCreated(1, 1, 11111, 10000);
	}

	private void sendTradeCancelled(long channelSeq, int tradeSid){
		// Encode order accepted into buffer
		int channelId = 1;
		int orderSid = 200001;
		OrderStatus status = OrderStatus.NEW;
		int cumulativeQty = 0;
		int leavesQty = 100;
		Side side = Side.BUY;
		long secSid = 400000;
		long updateTime = LocalTime.now().toNanoOfDay();
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		
		// Trade specific
		String executionId = Strings.padEnd("123456", TradeCancelledSbeEncoder.executionIdLength(), ' ');
		int executionPrice = 110000;
		int executionQty = 100;
		
		selfMessenger.orderSender().sendTradeCancelled(wrapper.sink(), 
				channelId, 
				channelSeq,
				tradeSid,
				orderSid, 
				status, 
				ExecutionType.TRADE_CANCEL,
				side, 
				OrderSender.prepareExecutionId(executionId, byteBuffer),
				executionPrice,
				executionQty,
				secSid,
				leavesQty,
				cumulativeQty,
				updateTime);
		wrapper.pushNextMessage();
		
		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		assertTrue(service.tradeBySid().containsKey(tradeSid));
		TradeBuffer tradeBuffer = service.tradeBySid().get(tradeSid);
		
		TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
		tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);
		assertEquals(channelId, tradeDecoder.channelId());
		assertEquals(channelSeq, tradeDecoder.channelSnapshotSeq());
		assertEquals(channelSeq, tradeDecoder.channelSnapshotSeq());
		assertEquals(orderSid, tradeDecoder.orderSid());
		assertEquals(secSid, tradeDecoder.secSid());
		assertEquals(status, tradeDecoder.status());
		assertEquals(leavesQty, tradeDecoder.leavesQty());
		assertEquals(cumulativeQty, tradeDecoder.cumulativeQty());
		assertEquals(0, executionId.compareTo(new String(byteBuffer.byteArray(), 0, tradeDecoder.getExecutionId(byteBuffer.byteArray(), 0))));
		assertEquals(executionPrice, tradeDecoder.executionPrice());
		assertEquals(executionQty, tradeDecoder.executionQty());
		assertEquals(TradeSbeDecoder.createTimeNullValue(), tradeDecoder.createTime());
		assertEquals(updateTime, tradeDecoder.updateTime());
		assertEquals(TradeStatus.CANCELLED, tradeDecoder.tradeStatus());
		
		Set<TradeBuffer> bufferSet = service.tradesByChannelId().get(channelId);
		assertEquals(1, bufferSet.size());
		assertEquals(1, bufferSet.stream().filter(new Predicate<TradeBuffer>() {
			@Override
			public boolean test(TradeBuffer t) {
				return t == tradeBuffer;
			}
		}).count());
		OrderAndTradeSubscriptionChannel channel = service.subscriptionChannels().get(channelId);
		assertEquals(channelSeq, channel.sequence());
	}

	@Test
	public void givenActiveReceiveTradeCancelled(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendTradeCancelled(1, 11111);
	}

	/**
	 * This test case does variety of stuff:
	 * 1) subscribe update for specific channel
	 * 2) get update for specific channel
	 */
	@Test
	public void givenActiveAndTwoActiveChannelSpecificSubscriptionsWhenReceiveUpdatesThenBroadcastAppropriately(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		int channelId1 = 1;
		int channelId2 = 2;
		sendAllOrderAndTradeSubscription(selfMessenger, selfRingBufferSinkPoller, channelId1);
		sendAllOrderAndTradeSubscription(selfMessenger2, selfRingBufferSinkPoller2, channelId2);
		
		AtomicInteger expectedOrderCountForSink1 = new AtomicInteger(1);
		AtomicInteger expectedTradeCountForSink1 = new AtomicInteger(0);
		AtomicInteger expectedOrderCountForSink2 = new AtomicInteger(0);
		AtomicInteger expectedTradeCountForSink2 = new AtomicInteger(0);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				expectedOrderCountForSink1.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				expectedTradeCountForSink1.decrementAndGet();
			}
		});
		selfMessenger2.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				expectedOrderCountForSink2.decrementAndGet();
			}
		});
		selfMessenger2.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				expectedTradeCountForSink2.decrementAndGet();
			}
		});
		
		// Action - send order accepted to channelId1 only
		sendOrderAcceptedWithOrderInfo(channelId1, 200001l, 11111, true);
		advanceTimer(broadcastFrequency);
		pollAll(selfRingBufferSinkPoller, selfRingBufferSinkPoller2);
		assertAllZeroes(expectedOrderCountForSink1, expectedTradeCountForSink1, expectedOrderCountForSink2, expectedTradeCountForSink2);

		// Action - send order accepted to channelId2 only
		sendOrderAcceptedWithOrderInfo(channelId2, 200001l, 11112, true);
		advanceTimer(broadcastFrequency);
		expectedOrderCountForSink1.set(0);
		expectedTradeCountForSink1.set(0);
		expectedOrderCountForSink2.set(1);
		expectedTradeCountForSink2.set(0);
		pollAll(selfRingBufferSinkPoller, selfRingBufferSinkPoller2);
		assertAllZeroes(expectedOrderCountForSink1, expectedTradeCountForSink1, expectedOrderCountForSink2, expectedTradeCountForSink2);
		
		// Action - no updates
		advanceTimer(broadcastFrequency);
		expectedOrderCountForSink1.set(0);
		expectedTradeCountForSink1.set(0);
		expectedOrderCountForSink2.set(0);
		expectedTradeCountForSink2.set(0);
		pollAll(selfRingBufferSinkPoller, selfRingBufferSinkPoller2);
		assertAllZeroes(expectedOrderCountForSink1, expectedTradeCountForSink1, expectedOrderCountForSink2, expectedTradeCountForSink2);

		// Action - two different updates on each channel
		sendOrderAcceptedWithOrderInfo(channelId1, 200002l, 11113, true);
		sendOrderCancelled(channelId1, 200003l, 11114, 11111, false);
		sendOrderAcceptedWithOrderInfo(channelId2, 200002l, 11115, true);
		sendOrderCancelled(channelId2, 200003l, 11116, 11112, false);
		advanceTimer(broadcastFrequency);
		expectedOrderCountForSink1.set(2);
		expectedTradeCountForSink1.set(0);
		expectedOrderCountForSink2.set(2);
		expectedTradeCountForSink2.set(0);
		pollAll(selfRingBufferSinkPoller, selfRingBufferSinkPoller2);
		assertAllZeroes(expectedOrderCountForSink1, expectedTradeCountForSink1, expectedOrderCountForSink2, expectedTradeCountForSink2);
		
		// Action - receive trades
		sendTradeCreated(channelId1, 200004l, 80000, 11113);
		sendTradeCreated(channelId2, 200004l, 80001, 11115);
		advanceTimer(broadcastFrequency);
		expectedOrderCountForSink1.set(1);
		expectedTradeCountForSink1.set(1);
		expectedOrderCountForSink2.set(1);
		expectedTradeCountForSink2.set(1);
		pollAll(selfRingBufferSinkPoller, selfRingBufferSinkPoller2);
		assertAllZeroes(expectedOrderCountForSink1, expectedTradeCountForSink1, expectedOrderCountForSink2, expectedTradeCountForSink2);
		
		// Action - get updates for channel1
		selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.GET, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()),
								Parameter.of(ParameterType.CHANNEL_ID, channelId1))).clientKey(clientKeySeq.getAndIncrement()));
		wrapper.pushNextMessage();
		AtomicInteger expectedChannel1ResponseCount = new AtomicInteger(4);
		IntOpenHashSet channel1ExpectedOrders = new IntOpenHashSet();
		channel1ExpectedOrders.add(11111);
		channel1ExpectedOrders.add(11113);
		IntOpenHashSet channel1ExpectedTrades = new IntOpenHashSet();
		channel1ExpectedTrades.add(80000);

		selfMessenger.receiver().orderHandlerList().clear();
		selfMessenger.receiver().tradeHandlerList().clear();
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
				channel1ExpectedOrders.rem(codec.orderSid());
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
				channel1ExpectedTrades.rem(codec.tradeSid());
			}
		});
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.debug("Received {}", ResponseDecoder.decodeToString(codec));
				expectedChannel1ResponseCount.decrementAndGet();
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertAllZeroes(expectedChannel1ResponseCount);
		assertTrue(channel1ExpectedOrders.isEmpty());
		assertTrue(channel1ExpectedTrades.isEmpty());
	}
	
	private void advanceTimer(Duration duration){
		testHelper.timerService().advance(duration);
		wrapper.pushNextMessage();

	}
	
	private static void assertAllZeroes(AtomicInteger... actual){
		for (int i = 0; i < actual.length; i++){
			if (actual[i].get() != 0){
				throw new AssertionError(i);
			}
		}
	}
	
	private static void pollAll(RingBufferMessageSinkPoller... pollers){
		for (RingBufferMessageSinkPoller poller : pollers){
			poller.pollAll();
		}
	}

	@Test
	public void givenActiveWhenReceiveGetAndSubscriptionRequestThenReturnResultAndBroadcastUpdatesAfterward(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		sendOrderAcceptedWithOrderInfo(1, 200001l, 11111, true);
		sendOrderAcceptedWithOrderInfo(1, 200002l, 11112, true);
		advanceTimer(broadcastFrequency);

		// When
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(), 
				Request.of(wrapper.sink().sinkId(), 
						RequestType.GET_AND_SUBSCRIBE, 
						Parameters.listOf(
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()))));
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				assertEquals(clientKey, request.clientKey());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});

		AtomicInteger expectedOrderCount = new AtomicInteger(2);
		AtomicInteger expectedTradeCount = new AtomicInteger(0);
		AtomicInteger expectedChannel1ResponseCount = new AtomicInteger(3);
		IntOpenHashSet channel1ExpectedOrders = new IntOpenHashSet();
		channel1ExpectedOrders.add(11111);
		channel1ExpectedOrders.add(11112);
		IntOpenHashSet channel1ExpectedTrades = new IntOpenHashSet();
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received order: {}", OrderDecoder.decodeToString(codec));
				channel1ExpectedOrders.rem(codec.orderSid());
				expectedOrderCount.decrementAndGet();
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received trade: {}", TradeDecoder.decodeToString(codec));
				channel1ExpectedTrades.rem(codec.tradeSid());
				expectedTradeCount.decrementAndGet();
			}
		});
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.debug("Received response: {}", ResponseDecoder.decodeToString(codec));
				expectedChannel1ResponseCount.decrementAndGet();
			}
		});
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();
		assertAllZeroes(expectedChannel1ResponseCount, expectedOrderCount, expectedTradeCount);
		assertTrue(channel1ExpectedOrders.isEmpty());
		assertTrue(channel1ExpectedTrades.isEmpty());
		
		// Verify
		OrderAndTradeSnapshotService service = (OrderAndTradeSnapshotService)wrapper.coreService();
		for (int channelId = 0; channelId < expectedNumChannels; channelId++){
			assertEquals(1, service.subscriptionChannels().get(channelId).numOrderSubscribers());
			assertEquals(1, service.subscriptionChannels().get(channelId).numTradeSubscribers());
		}

		// When and Verify - Broadcast, nothing should be received
		expectedChannel1ResponseCount.set(0);
		expectedOrderCount.set(0);
		expectedTradeCount.set(0);
		advanceTimer(broadcastFrequency);
		pollAll(selfRingBufferSinkPoller);
		assertAllZeroes(expectedChannel1ResponseCount, expectedOrderCount, expectedTradeCount);
		
		// When and Verify - Two updates, then broadcast
		sendOrderAcceptedWithOrderInfo(2, 200003l, 11113, true);
		sendOrderAcceptedWithOrderInfo(2, 200004l, 11114, true);
		expectedChannel1ResponseCount.set(0);
		expectedOrderCount.set(2);
		expectedTradeCount.set(0);
		advanceTimer(broadcastFrequency);
		pollAll(selfRingBufferSinkPoller);
		assertAllZeroes(expectedChannel1ResponseCount, expectedOrderCount, expectedTradeCount);
	}

	@Test
	public void givenNoOrderWhenReceiveGetRequestThenNothingReturn(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);

		// When
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(),
				Request.of(wrapper.sink().sinkId(), 
						RequestType.GET, 
						Parameters.listOf(
								Parameter.of(DataType.ALL_ORDER_AND_TRADE_UPDATE),
								Parameter.of(ParameterType.SECURITY_SID, 100001l)))
				.clientKey(clientKey)
				);
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				LOG.debug("clientKey:{}, request.clientKey:{}", clientKey, request.clientKey()); 
				assertEquals(clientKey, request.clientKey());
				LOG.debug("request.resultType:{}", request.resultType());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();

		selfRingBufferSinkPoller.pollAll();
		assertTrue(received.get());
	}
	
	@Test
	public void givenMultipleOrdersForSecurityWhenReceiveGetRequestThenReturnAppropriately(){
		// Given
		advanceToActiveState(wrapper, selfMessenger);
		long secSid = 3333333;
		sendOrderAcceptedWithOrderInfo(secSid, 1, 200001l, 11111, true);

		// When
		int clientKey = clientKeySeq.getAndIncrement();
		AtomicBoolean received = new AtomicBoolean(false);
		CompletableFuture<Request> requestFuture = selfMessenger.sendRequest(wrapper.sink(),
				Request.of(wrapper.sink().sinkId(), 
						RequestType.GET, 
						Parameters.listOf(
								Parameter.of(DataType.ALL_ORDER_AND_TRADE_UPDATE),
								Parameter.of(ParameterType.SECURITY_SID, secSid)))
				.clientKey(clientKey)
				);
		requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request request, Throwable throwable) {
				LOG.debug("clientKey:{}, request.clientKey:{}", clientKey, request.clientKey()); 
				assertEquals(clientKey, request.clientKey());
				LOG.debug("request.resultType:{}", request.resultType());
				assertEquals(ResultType.OK, request.resultType());
				received.set(true);
			}
		});
		wrapper.pushNextMessage();

		AtomicInteger expectedChannel1ResponseCount = new AtomicInteger(2);
		selfMessenger.receiver().orderHandlerList().add(new Handler<OrderSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
				LOG.debug("Received {}", OrderDecoder.decodeToString(codec));
			}
		});
		selfMessenger.receiver().tradeHandlerList().add(new Handler<TradeSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
				LOG.debug("Received {}", TradeDecoder.decodeToString(codec));
			}
		});
		selfMessenger.receiver().responseHandlerList().add(new Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.debug("Received {}", ResponseDecoder.decodeToString(codec));
				expectedChannel1ResponseCount.decrementAndGet();
			}
		});		
		selfRingBufferSinkPoller.pollAll();
		assertTrue(received.get());
		assertEquals(0, expectedChannel1ResponseCount.get());
	}

}
