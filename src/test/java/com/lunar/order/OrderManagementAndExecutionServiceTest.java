package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.google.common.base.Strings;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.LineHandlerConfig;
import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.LifecycleState;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.core.TriggerInfo;
import com.lunar.core.WaitStrategy;
import com.lunar.entity.Security;
import com.lunar.exception.ConfigurationException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.CommandAckDecoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.OrderRequestCompletionDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderRequestAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeEncoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.order.OrderManagementAndExecutionService.CompositeOrderAction;
import com.lunar.service.MessageServiceExecutionContext;
import com.lunar.service.ServiceBuilder;
import com.lunar.service.ServiceConstant;
import com.lunar.service.ServiceFactory;
import com.lunar.service.ServiceLifecycleAware;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class OrderManagementAndExecutionServiceTest {
	private static final Logger LOG = LogManager.getLogger(OrderManagementAndExecutionServiceTest.class);

	@Mock
	private ServiceStatusSender serviceStatusSender;

	@Mock
	private MessageSink adminSink;
	
	@Mock
	private MessageSink rdsSink;

	@Mock
	private MessageSink warmupSink;

	@Mock
	private MessageSink persistSink;

	@Mock
	private MessageSink nsSink;

	@Mock
	private LineHandler lineHandler;
	
	@Mock
	private MessageSink orderUpdateSender;

	private OrderManagementAndExecutionServiceConfig config;
	private MessageSinkRef admin;
	private MessageSinkRef ns;
	private MessageSinkRef warmup;
	private MessageSinkRef persist;
	private MessageSinkRef rds;
	private final int adminSinkId = 1;
	private final int rdsSinkId = 2;
	private final int omesSinkId = 3;
	private final int persistSinkId = 4;
	private final int lineHandlerId = 5;
	private final int nsSinkId = 6;
	private final int warmupSinkId = 7;	
	private Security security;
	private Security anotherSecurity;
	private Security underlying;
	private Security anotherUnderlying;
	
	private ServiceTestHelper testHelper;
	private final int selfSinkId = 8;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller;
	private RingBufferMessageSink selfRingBufferSink;
	private LunarServiceTestWrapper wrapper;
	private LunarService messageService;
	private Messenger selfMessenger;
	private MessageSinkRef selfSinkRef;
	private MessageSinkRef serviceSinkRef;
	private NewOrderRequest newSellOrderRequest;
	private NewOrderRequest newBuyOrderRequest;
	private NewOrderRequest newBuyOrderRequestAnotherSecurity;
	private LineHandlerConfig lineHandlerConfig;
	
	private static final AtomicInteger clientKeySeq = new AtomicInteger(5000000);
	
	private static final int NUM_TOTAL_THROTTLE = 4;
	private static final int NUM_THROTTLE_PER_UND = 3;
	
	@Before
	public void setup() throws ConfigurationException{
		List<Integer> throttleArrangement = new ArrayList<Integer>();
		throttleArrangement.add(2);
		throttleArrangement.add(2);
		LineHandlerConfig lineHandlerConfig = LineHandlerConfig.of(1, 
				"line handler", 
				"line handler", 
				com.lunar.order.NullLineHandlerEngine.class,
				NUM_TOTAL_THROTTLE, 
				Optional.of(throttleArrangement),
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
		this.underlying = Security.of(700L, SecurityType.STOCK, "700", 101, false, SpreadTableBuilder.get(SecurityType.STOCK));
		this.security = Security.of(99999L, SecurityType.WARRANT, "61727", 101, 
				underlying.sid(), 
				Optional.of(LocalDate.now()),
				LocalDate.now(),
				PutOrCall.CALL,
				OptionStyle.AMERICAN,
				500,
				1000,
				2,
				10000, 
				true, SpreadTableBuilder.get(SecurityType.WARRANT));
		this.anotherUnderlying = Security.of(2628L, SecurityType.STOCK, "2628", 101, false, SpreadTableBuilder.get(SecurityType.STOCK));
		this.anotherSecurity = Security.of(99998L, SecurityType.WARRANT, "51727", 101, 
				anotherUnderlying.sid(), 
				Optional.of(LocalDate.now()),
				LocalDate.now(),
				PutOrCall.CALL,
				OptionStyle.AMERICAN,
				500,
				1000,
				2,
				10000, 
				true, SpreadTableBuilder.get(SecurityType.WARRANT));
		this.config = new OrderManagementAndExecutionServiceConfig(1,
				"test-omes", 
				"test-omes", 
				"test-omes", 
				ServiceType.OrderManagementAndExecutionService, 
				Optional.empty(), 
				omesSinkId, 
				Optional.of(1024), 
				Optional.of(1024), 
				Optional.empty(),
				Optional.empty(),
				true, 
				false,
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
				true, 
				99999,
				Optional.of(8),
				Optional.empty(),
				Optional.empty(),
//				Optional.empty(),
				Optional.of(NUM_THROTTLE_PER_UND),
				lineHandlerConfig);
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");
		rdsSink = TestHelper.mock(rdsSink, rdsSinkId, ServiceType.RefDataService);
		nsSink = TestHelper.mock(nsSink, nsSinkId, ServiceType.NotificationService);
		warmupSink = TestHelper.mock(warmupSink, warmupSinkId, ServiceType.WarmupService);
		persistSink = TestHelper.mock(persistSink, persistSinkId, ServiceType.PersistService);
		ns = MessageSinkRef.of(nsSink, "test-ns");
		warmup = MessageSinkRef.of(warmupSink, "test-warmup");
		persist = MessageSinkRef.of(persistSink, "test-persist");
		orderUpdateSender = TestHelper.mock(orderUpdateSender, 11, ServiceType.LineHandler);
		rds = MessageSinkRef.of(rdsSink, "test-rds");
		
		testHelper = ServiceTestHelper.of();
		selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId, ServiceType.DashboardService, 256, "testDashboard");
		selfRingBufferSink = selfRingBufferSinkPoller.sink();
		selfMessenger = testHelper.createMessenger(selfRingBufferSink, "self");
		selfMessenger.registerEvents();
		selfRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				LOG.debug("Received message in self sink");
				selfMessenger.receive(event, 0);
				return false;
			}
		});
		
		selfSinkRef = selfMessenger.self();

		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return new OrderManagementAndExecutionService(serviceConfig, messageService, new LineHandlerBuilder() {
					@Override
					public LineHandler build() {
						return lineHandler;
					}
				});
			}
		};
		when(lineHandler.id()).thenReturn(lineHandlerId);
		when(lineHandler.active()).thenReturn(CompletableFuture.completedFuture(LifecycleState.ACTIVE));
		when(lineHandler.stop()).thenReturn(CompletableFuture.completedFuture(LifecycleState.STOPPED));
		when(lineHandler.recover()).thenReturn(CompletableFuture.completedFuture(LifecycleState.RECOVERY));
		wrapper = testHelper.createService(config, builder);
		messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(MessageSinkRef.of(selfRingBufferSink));
		serviceSinkRef = messageService.messenger().self(); 
		
		newSellOrderRequest = NewOrderRequest.of(11111, 
				selfMessenger.self(), 
				security, 
				OrderType.LIMIT_ORDER, 
				1000, 
				Side.SELL, 
				TimeInForce.DAY, 
				BooleanType.TRUE, 
				650, 
				650, 
				12345);

		newBuyOrderRequest = NewOrderRequest.of(11112, 
				selfMessenger.self(), 
				security, 
				OrderType.LIMIT_ORDER, 
				1000, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.TRUE, 
				645, 
				645, 
				12345);
		

		newBuyOrderRequestAnotherSecurity = NewOrderRequest.of(11112, 
				selfMessenger.self(), 
				anotherSecurity, 
				OrderType.LIMIT_ORDER, 
				1000, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.TRUE, 
				645, 
				645, 
				12345);

	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStartStop() throws Exception{
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
					return OrderManagementAndExecutionService.of(serviceConfig, messageService);
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
	public void givenCreatedWhenIdleStartThenBeginTrackingServicesAndMoveToWaitState(){
		// given

		// when
		messageService.onStart();
		
		// then
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		ServiceStatusTracker tracker = messageService.messenger().serviceStatusTracker();
		assertTrue(tracker.isTrackingService(ServiceType.AdminService));
	}

	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToReadyState(){
		advanceToReadyState(wrapper, selfMessenger);
	}
	
	private void advanceToReadyState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		LOG.debug("advanceToReadyState begin");
		messageService.onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES, messageService.state());

		AtomicBoolean startedRecovery = new AtomicBoolean(false);
		AtomicBoolean startRecovery = new AtomicBoolean(false);
		when(lineHandler.state()).then(new Answer<LifecycleState>(){
			@Override
			public LifecycleState answer(InvocationOnMock invocation) throws Throwable {
				LOG.info("Called mock to get state");
				if (startedRecovery.get()){
					return LifecycleState.ACTIVE;
				}
				else if (startRecovery.get()){
					return LifecycleState.RECOVERY;
				}
				else{
					return LifecycleState.INIT;
				}
			}
		});
		
		// Line handler state should be set to ACTIVE when startRecovery is done
		when(lineHandler.startRecovery()).then(new Answer<CompletableFuture<Boolean>>() {
			@Override
			public CompletableFuture<Boolean> answer(InvocationOnMock invocation) throws Throwable {
				startedRecovery.set(true);
				return CompletableFuture.completedFuture(true);
			}
		});
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSinkRef.sinkId(),
						admin.sinkId(), 
						admin.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSinkRef.sinkId(),
						warmup.sinkId(),
						warmup.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSinkRef.sinkId(),
						ns.sinkId(),
						ns.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSinkRef.sinkId(),
						persist.sinkId(),
						persist.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSinkRef.sinkId(),
						rds.sinkId(),
						rds.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		LOG.debug("advanceToReadyState end");
		startRecovery.set(true);
		
		// Force create underlying tracker
		selfMessenger.securitySender().sendSecurity(serviceSinkRef, security);
		wrapper.pushNextMessage();
		selfMessenger.securitySender().sendSecurity(serviceSinkRef, anotherSecurity);
		wrapper.pushNextMessage();
		
		assertEquals("expect ready state", States.READY, messageService.state());
		
		// verify
		AssertUtil.assertTrueWithinPeriod("expect active state", () -> {

			// This is to mimic sending itself an EVALUATE_STATE command in OrderManagementAndExecutionService.verifyLineHandlerState
			selfMessenger.commandSender().sendCommand(serviceSinkRef, Command.of(serviceSinkRef.sinkId(), selfMessenger.getNextClientKeyAndIncrement(), CommandType.EVALUATE_STATE));		
			wrapper.pushNextMessage();
			
			return messageService.state().equals(States.ACTIVE);
		}, TimeUnit.SECONDS.toNanos(1l), TimeUnit.MILLISECONDS.toNanos(100));
		
		assertEquals("unexpected state", States.ACTIVE, messageService.state());
	}

	private CancelOrderRequest sendCancelOrderRequestAndRejectedInternally(LunarServiceTestWrapper wrapper, Order orderToBeCancelled){
		CancelOrderRequest request = CancelOrderRequest.of(clientKeySeq.getAndIncrement(),
				selfMessenger.self(), 
				orderToBeCancelled.sid(), 
				orderToBeCancelled.secSid(), 
				orderToBeCancelled.side());
	    MessageSinkRef serviceSinkRef = messageService.messenger().self();
	    selfMessenger.orderSender().sendCancelOrder(serviceSinkRef, request);
	    wrapper.pushNextMessage();
	    
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
	    int orderSid = coreService.orderManagementContext().orderRequestBySid().values().stream().filter((o) -> o.clientKey() == request.clientKey()).findFirst().get().orderSid();
	    request.orderSid(orderSid);
	    
		selfMessenger.orderSender().sendOrderRequestCompletion(serviceSinkRef,
				request.orderSid(),
				request.orderSid(), 
				OrderRequestCompletionType.REJECTED_INTERNALLY,
				OrderRequestRejectType.THROTTLED);
		wrapper.pushNextMessage();
	    
		assertTrue(coreService.pendingCancelByOrdSid().isEmpty());
	    return request;
	}
	
	private CancelOrderRequest sendCancelOrderRequest(LunarServiceTestWrapper wrapper, Order orderToBeCancelled){
		CancelOrderRequest request = CancelOrderRequest.of(clientKeySeq.getAndIncrement(),
				selfMessenger.self(), 
				orderToBeCancelled.sid(), 
				orderToBeCancelled.secSid(), 
				orderToBeCancelled.side());
	    MessageSinkRef serviceSinkRef = messageService.messenger().self();
	    selfMessenger.orderSender().sendCancelOrder(serviceSinkRef, request);
	    wrapper.pushNextMessage();
	    
	    // Get orderSid from service directly
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
	    int orderSid = coreService.orderManagementContext().orderRequestBySid().values().stream().filter((o) -> o.clientKey() == request.clientKey()).findFirst().get().orderSid();
	    request.orderSid(orderSid);
	    
	    LOG.info("Cancel order request: orderSid:{}, clientKey:{}", request.orderSid(), request.clientKey());

	    // Send order completion
	    sendOrderRequestCompletion(wrapper, request.orderSid());
	    
	    // Send order cancelled
	    sendOrderCancelled(wrapper, request, orderToBeCancelled);
	    
		return request;
	}
	
	private void sendOrderRequestCompletion(LunarServiceTestWrapper wrapper, int ordSid){
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		selfMessenger.orderSender().sendOrderRequestCompletionWithOrdSidOnly(serviceSinkRef, 
				ordSid, 
				OrderRequestCompletionType.OK);
		wrapper.pushNextMessage();
	}
	
	private void sendOrderCancelled(LunarServiceTestWrapper wrapper, CancelOrderRequest request, Order order){
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		int channelId = 1;
		long channelSeq = 1212;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		OrderCancelledSbeEncoder encoder = new OrderCancelledSbeEncoder();
		OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				encoder, 
				channelId, 
				channelSeq, 
				request.orderSid(), 
				request.ordSidToBeCancelled(), 
				OrderStatus.CANCELLED, 
				order.limitPrice(), 
				order.side(), 
				order.orderId(), 
				order.secSid(), 
				ExecutionType.CANCEL, 
				order.leavesQty(), 
				order.cumulativeExecQty(), 
				LocalTime.now().toNanoOfDay(),
				order.quantity());
		
		OrderCancelledSbeDecoder decoder = new OrderCancelledSbeDecoder();
		decoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		selfMessenger.orderSender().sendOrderCancelled(serviceSinkRef, 
				channelId,
				channelSeq,
				buffer, 
				0,
				decoder, 
				selfMessenger.internalEncodingBuffer(), 
				0);
		wrapper.pushNextMessage();		
	}
	
	private void sendOrderCancelled(LunarServiceTestWrapper wrapper, int cancelOrderSid, Order order){
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		int channelId = 1;
		long channelSeq = 1212;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		OrderCancelledSbeEncoder encoder = new OrderCancelledSbeEncoder();
		OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				encoder, 
				channelId, 
				channelSeq, 
				cancelOrderSid, 
				order.sid(), 
				OrderStatus.CANCELLED, 
				order.limitPrice(), 
				order.side(), 
				order.orderId(), 
				order.secSid(), 
				ExecutionType.CANCEL, 
				order.leavesQty(), 
				order.cumulativeExecQty(), 
				LocalTime.now().toNanoOfDay(),
				order.quantity());
		
		OrderCancelledSbeDecoder decoder = new OrderCancelledSbeDecoder();
		decoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		selfMessenger.orderSender().sendOrderCancelled(serviceSinkRef, 
				channelId,
				channelSeq,
				buffer, 
				0,
				decoder, 
				selfMessenger.internalEncodingBuffer(), 
				0);
		wrapper.pushNextMessage();
	}
	
	private Order sendOrderAccepted(LunarServiceTestWrapper wrapper, NewOrderRequest request){
		int channelId = 1;
		long channelSeq = 1;
		int orderId = 1212121;
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		OrderAcceptedSbeEncoder encoder = new OrderAcceptedSbeEncoder();
		OrderSender.encodeOrderAcceptedOnly(buffer, 
				0, 
				encoder, 
				channelId, 
				channelSeq, 
				request.orderSid(), 
				OrderStatus.NEW, 
				request.limitPrice(), 
				0, 
				request.quantity(), 
				request.side(), 
				orderId, 
				request.secSid(), 
				ExecutionType.NEW, 
				LocalTime.now().toNanoOfDay());
		
		MessageSinkRef serviceSinkRef = messageService.messenger().self(); 
		
		Order order = Order.of(request, 
				OrderStatus.NEW, 
				request.quantity(),
				0,
				LocalTime.now().toNanoOfDay(),
				LocalTime.now().toNanoOfDay()).orderId(orderId);
		LOG.debug("request price:{}, order price: {}", request.limitPrice(), order.limitPrice());
		
		OrderAcceptedSbeDecoder decoder = new OrderAcceptedSbeDecoder();
		decoder.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		selfMessenger.orderSender().sendOrderAcceptedWithOrderInfo(serviceSinkRef, 
				channelId,
				channelSeq,
				buffer,
				0, 
				decoder,
				order, 
				selfMessenger.internalEncodingBuffer(),
				0);
		wrapper.pushNextMessage();
		return order;
	}
	
	private Order sendNewCompositeOrderRequest(LunarServiceTestWrapper wrapper, Side side, int price, int quantity){
	    NewOrderRequest request = NewOrderRequest.of(clientKeySeq.getAndIncrement(), 
	            selfMessenger.self(), 
	            security, 
	            OrderType.LIMIT_THEN_CANCEL_ORDER, 
	            quantity, 
	            side, 
	            TimeInForce.DAY, 
	            BooleanType.TRUE, 
	            price, 
	            price, 
	            12345);
	    request.isPartOfCompositOrder(true);
	    request.numThrottleRequiredToProceed(OrderRequest.NUM_THROTTLE_REQUIRED_TO_PROCEED_FOR_LIMIT_THEN_CANCEL);

	    MessageSinkRef serviceSinkRef = messageService.messenger().self();
	    selfMessenger.orderSender().sendNewCompositeOrder(serviceSinkRef, request);
	    wrapper.pushNextMessage();
	    
	    // Get orderSid from service directly
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
	    Optional<OrderRequest> result = coreService.orderManagementContext().orderRequestBySid().values().stream().filter((o) -> o.clientKey() == request.clientKey()).findFirst();
	    
	    int orderSid = -1;
	    if (result.isPresent()){
	    	orderSid = result.get().orderSid();
		    request.orderSid(orderSid);
		    
		    // Check that NewOrderRequest and CancelOrderRequest have been sent
		    NewOrderRequest newOrderRequest = result.get().asNewOrderRequest();
		    LOG.info("New order request: orderSid:{}, clientKey:{}, side:{}, quantity:{}, orderType:{}", 
		    		request.orderSid(), 
		    		request.clientKey(), 
		    		request.side().name(), 
		    		request.quantity(),
		    		newOrderRequest.orderType());

		    CompositeOrderAction remainingActions = coreService.remainingActionsForCompositeOrderByClientKey().get(request.clientKey());
		    assertFalse(remainingActions.cancelSent());

		    sendOrderRequestCompletion(wrapper, orderSid);

		    // Check if cancel order has been sent		    
		    Integer cancelOrderSid = coreService.orderManagementContext().origOrdSidToCancelOrderSid().get(orderSid);
		    CancelOrderRequest cancelOrderRequest = coreService.orderManagementContext().orderRequestBySid().get(cancelOrderSid).asCancelOrderRequest();
		    LOG.info("Cancel order is sent for orderSid:{}, {}", cancelOrderSid, cancelOrderRequest);
		    
		    
		    assertTrue(remainingActions.cancelSent());
		    
		    // Send order request completion for both orders (new and cancel)
		    sendOrderRequestCompletion(wrapper, orderSid);		    
		    sendOrderRequestCompletion(wrapper, cancelOrderSid);
		    
		    remainingActions = coreService.remainingActionsForCompositeOrderByClientKey().get(request.clientKey());
		    assertEquals(coreService.remainingActionsForCompositeOrderByClientKey().defaultReturnValue(), remainingActions); 
		    
		    Order order = sendOrderAccepted(wrapper, request);
		    sendOrderCancelled(wrapper, cancelOrderSid, order);
		    
		    assertFalse(coreService.orderManagementContext().orderRequestBySid().containsKey(orderSid));
		    assertFalse(coreService.orderManagementContext().orderRequestBySid().containsKey(cancelOrderSid));		    
	    }
	    return null;
	}
	
	private Order sendNewOrderRequest(LunarServiceTestWrapper wrapper, Security security, Side side, int price, int quantity){
		return sendNewOrderRequest(wrapper, security, side, price, quantity, TimeInForce.DAY, ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX);
	}
		
	private Order sendNewOrderRequest(LunarServiceTestWrapper wrapper, Security security, Side side, int price, int quantity, int assignedThrottleTrackerIndex){
		return sendNewOrderRequest(wrapper, security, side, price, quantity, TimeInForce.DAY, assignedThrottleTrackerIndex);
	}

	private Order sendNewOrderRequest(LunarServiceTestWrapper wrapper, Security security, Side side, int price, int quantity, TimeInForce tif, int assignedThrottleTrackerIndex){
	    NewOrderRequest request = NewOrderRequest.of(clientKeySeq.getAndIncrement(), 
	            selfMessenger.self(), 
	            security, 
	            OrderType.LIMIT_ORDER, 
	            quantity, 
	            side, 
	            tif, 
	            BooleanType.TRUE, 
	            price, 
	            price, 
	            Long.MAX_VALUE,
	            false,
	            assignedThrottleTrackerIndex,
	            12345,
	            true,
	            TriggerInfo.of());

	    MessageSinkRef serviceSinkRef = messageService.messenger().self();
	    selfMessenger.orderSender().sendNewOrder(serviceSinkRef, request);
	    wrapper.pushNextMessage();
	    
	    // Get orderSid from service directly
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
	    Optional<OrderRequest> result = coreService.orderManagementContext().orderRequestBySid().values().stream().filter((o) -> o.clientKey() == request.clientKey()).findFirst();
	    int orderSid = -1;
	    if (result.isPresent()){
	    	orderSid = result.get().orderSid();
		    request.orderSid(orderSid);
		    LOG.info("New order request: orderSid:{}, clientKey:{}, side:{}, quantity:{}", request.orderSid(), request.clientKey(), request.side().name(), request.quantity());

		    // Send order completion
		    sendOrderRequestCompletion(wrapper, orderSid);
		    
		    // Send order accepted
		    return sendOrderAccepted(wrapper, request);
	    }
	    return null;
	}
	
	private static void increasePP(LunarServiceTestWrapper wrapper, long power){
        // Increase purchasing power and position
        OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
        coreService.exposure().initialPurchasingPower(power);	    
	}
	
	private void advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		advanceToReadyState(wrapper, selfMessenger);
		// Increase purchasing power and position
        OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
        
        // Check initial pp
        assertEquals(8000, coreService.exposure().purchasingPower());
        
		increasePP(wrapper, newBuyOrderRequest.limitPrice() * newBuyOrderRequest.quantity());
		ValidationOrderBook orderBook = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook();
		orderBook.position().longPosition(newSellOrderRequest.quantity() * 2);
		
		// send orders
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newSellOrderRequest);
		wrapper.pushNextMessage();

		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest);
		wrapper.pushNextMessage();
		
		HashMap<Integer, NewOrderRequest> requests = new HashMap<>(2);
		requests.put(newSellOrderRequest.clientKey(), newSellOrderRequest);
		requests.put(newBuyOrderRequest.clientKey(), newBuyOrderRequest);

		// get order request accepted message and its assigned order sid
		final AtomicInteger expectedCount = new AtomicInteger(2);
		selfMessenger.receiver().orderRequestAcceptedHandlerList().add(new Handler<OrderRequestAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder codec) {
				requests.get(codec.clientKey()).orderSid(codec.orderSid());
				expectedCount.decrementAndGet();
				LOG.debug("Received orderRequestAccepted orderSid:{}", codec.orderSid());
			}
		});
		while (selfRingBufferSinkPoller.poll()){
			// flush the message queue
		}
		assertNotNull(coreService.orderManagementContext().getOrderRequest(newSellOrderRequest.orderSid()));
		assertNotNull(coreService.orderManagementContext().getOrderRequest(newBuyOrderRequest.orderSid()));
		assertEquals(0, expectedCount.get());
	}

	@Test
	public void givenActiveWhenOrderOfUnderlyingExceedLimitThenReject(){
		advanceToReadyState(wrapper, selfMessenger);

		// create a new order request
        increasePP(wrapper, 100 * newBuyOrderRequest.limitPrice() * newBuyOrderRequest.quantity());

		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest); // OK
		wrapper.pushNextMessage();
		
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest); // OK
		wrapper.pushNextMessage();

		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest); // OK
		wrapper.pushNextMessage();

		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest); // Reject
		wrapper.pushNextMessage();

		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequestAnotherSecurity); // OK
		wrapper.pushNextMessage();

		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequestAnotherSecurity); // OK
		wrapper.pushNextMessage();

		final AtomicInteger expectedSentCount = new AtomicInteger(5);
		selfMessenger.receiver().orderRequestAcceptedHandlerList().add(new Handler<OrderRequestAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder codec) {
				expectedSentCount.decrementAndGet();
				LOG.debug("Received orderRequestAccepted orderSid:{}", codec.orderSid());
			}
		});
		
		final AtomicInteger expectedRejectCount = new AtomicInteger(1);
		
		selfMessenger.receiver().orderRequestCompletionHandlerList().add(new Handler<OrderRequestCompletionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
				LOG.info("Received: {}", OrderRequestCompletionDecoder.decodeToString(codec));
				if (codec.rejectType() != OrderRequestRejectType.NULL_VAL){
					expectedRejectCount.decrementAndGet();
				}
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedRejectCount.get());
		assertEquals(0, expectedSentCount.get());
	}
	
	@Test
	public void givenActiveWhenReceiveNewOrderRequestThenSendToLineHandler(){
		advanceToReadyState(wrapper, selfMessenger);

		// create a new order request
        increasePP(wrapper, newBuyOrderRequest.limitPrice() * newBuyOrderRequest.quantity());

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		int expectedOrdSid = coreService.peekOrdSid();

		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest);
		wrapper.pushNextMessage();
		
		// Check received messages
		// 1) Order Request Accepted
		// 2) Order Request Completion - we don't receive this unless we mock Order Update Receiver
		
		// Check if that order was sent out
		// 1) Check line handler
		
		final AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderRequestAcceptedHandlerList().add(new Handler<OrderRequestAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder codec) {
				assertEquals(newBuyOrderRequest.clientKey(), codec.clientKey());
				assertEquals(expectedOrdSid, codec.orderSid());
				expectedCount.decrementAndGet();
				LOG.debug("Received orderRequestAccepted orderSid:{}", codec.orderSid());
			}
		});

		selfMessenger.receiver().orderRequestCompletionHandlerList().add(new Handler<OrderRequestCompletionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
				LOG.debug("Received orderRequestCompletion orderSid:{}", codec.orderSid());
			}
		});
		selfMessenger.receiver().commandAckHandlerList().add(new Handler<CommandAckSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder codec) {
				LOG.debug("Received commandAck [{}]", CommandAckDecoder.decodeToString(codec));
			}
		});
		ArgumentCaptor<NewOrderRequest> argumentCaptor = ArgumentCaptor.forClass(NewOrderRequest.class);
		verify(this.lineHandler, times(1)).send(argumentCaptor.capture());
		NewOrderRequest capturedRequest = argumentCaptor.getValue();
		assertEquals(expectedOrdSid, capturedRequest.orderSid());
		assertEquals(newBuyOrderRequest.clientKey(), capturedRequest.clientKey());
		assertEquals(newBuyOrderRequest.limitPrice(), capturedRequest.limitPrice());
		assertEquals(newBuyOrderRequest.quantity(), capturedRequest.quantity());
		
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenReceiveNewOrderRequestWithInsufficientPPThenSendOrderCompletionFailedBack(){
		// given
		advanceToReadyState(wrapper, selfMessenger);

		// when
		// create a new order request
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newBuyOrderRequest);
		wrapper.pushNextMessage();

		// then
		final AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderRequestCompletionHandlerList().add(new Handler<OrderRequestCompletionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
				expectedCount.decrementAndGet();
				assertEquals(OrderRequestRejectType.ORDER_EXCEED_PURCHASING_POWER, codec.rejectType());
				assertEquals(OrderRequestCompletionSbeEncoder.orderSidNullValue(), codec.orderSid());
				assertEquals(newBuyOrderRequest.clientKey(), codec.clientKey());
				LOG.debug("Received orderRequestCompletion | clientKey:{}, orderSid:{}", codec.clientKey(), codec.orderSid());
			}
		});
		selfMessenger.receiver().commandAckHandlerList().add(new Handler<CommandAckSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder codec) {
				LOG.debug("Received commandAck [{}]", CommandAckDecoder.decodeToString(codec));
			}
		});
		
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
		verify(this.lineHandler, never()).send(isA(NewOrderRequest.class));
	}
	
	@Test
	public void givenActiveWhenReceiveNewOrderRequestWithInsufficientPositionThenSendOrderCompletionFailedBack(){
		// given
		advanceToReadyState(wrapper, selfMessenger);

		// when
		// create a new order request
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, newSellOrderRequest);
		wrapper.pushNextMessage();
		
		// then
		final AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderRequestCompletionHandlerList().add(new Handler<OrderRequestCompletionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
				expectedCount.decrementAndGet();
				assertEquals(OrderRequestRejectType.INSUFFICIENT_LONG_POSITION, codec.rejectType());
				assertEquals(OrderRequestCompletionSbeEncoder.orderSidNullValue(), codec.orderSid());
				assertEquals(newSellOrderRequest.clientKey(), codec.clientKey());
				LOG.debug("Received orderRequestCompletion | clientKey:{}, orderSid:{}", codec.clientKey(), codec.orderSid());
			}
		});
		selfMessenger.receiver().commandAckHandlerList().add(new Handler<CommandAckSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder codec) {
				LOG.debug("Received commandAck [{}]", CommandAckDecoder.decodeToString(codec));
			}
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
		verify(this.lineHandler, never()).send(isA(NewOrderRequest.class));
	}
	
	@Test
	public void givenActiveWhenReceiveNewOrderRequestThatCrossWithExistingOrderThenSendOrderCompletionFailedBack(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		// when
		NewOrderRequest crossOrderRequest = NewOrderRequest.of(11113, 
				selfMessenger.self(), 
				security, 
				OrderType.LIMIT_ORDER, 
				1000, 
				Side.SELL, 
				TimeInForce.DAY, 
				BooleanType.TRUE, 
				newBuyOrderRequest.limitPrice(), 
				newBuyOrderRequest.limitPrice(), 
				12345);
		selfMessenger.orderSender().sendNewOrder(serviceSinkRef, crossOrderRequest);
		wrapper.pushNextMessage();
		
		// then
		final AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderRequestCompletionHandlerList().add(new Handler<OrderRequestCompletionSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
				expectedCount.decrementAndGet();
				assertEquals(OrderRequestRejectType.CROSSED, codec.rejectType());
				assertEquals(OrderRequestCompletionSbeEncoder.orderSidNullValue(), codec.orderSid());
				assertEquals(crossOrderRequest.clientKey(), codec.clientKey());
				LOG.debug("Received orderRequestCompletion | {}", OrderRequestCompletionDecoder.decodeToString(codec));
			}
		});
		
		assertTrue(selfRingBufferSinkPoller.poll());
		assertFalse(selfRingBufferSinkPoller.poll());
		assertEquals(0, expectedCount.get());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
	}
	
	@Test
	public void givenActiveWhenReceiveCancelOrderRequestThenSendOrderRequestAcceptedBack(){
		// given
		advanceToReadyState(wrapper, selfMessenger);

		// when
		CancelOrderRequest cancelRequest = CancelOrderRequest.of(11114, 
				selfMessenger.self(), 
				11111,
				security.sid(),
				Side.BUY);
		selfMessenger.orderSender().sendCancelOrder(serviceSinkRef, cancelRequest);
		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		int expectedOrdSid = coreService.peekOrdSid();
		wrapper.pushNextMessage();

		// then
		final AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderRequestAcceptedHandlerList().add(new Handler<OrderRequestAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder codec) {
				assertEquals(cancelRequest.clientKey(), codec.clientKey());
				assertEquals(expectedOrdSid, codec.orderSid());
				expectedCount.decrementAndGet();
				LOG.debug("Received orderRequestAccepted orderSid:{}", codec.orderSid());
			}
		});
		selfMessenger.receiver().commandAckHandlerList().add(new Handler<CommandAckSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder codec) {
				LOG.debug("Received commandAck [{}]", CommandAckDecoder.decodeToString(codec));
			}
		});;
		selfRingBufferSinkPoller.pollAll();
		assertEquals(1, expectedCount.get());
	}
	
	@Ignore
	@Test
	public void givenAvoidMultiCancelAndActiveWhenReceiveCancelOrderRequestOfNonExistOrderThenSendOrderRequestRejectedBack(){
		this.config = new OrderManagementAndExecutionServiceConfig(1,
				"test-omes", 
				"test-omes", 
				"test-omes", 
				ServiceType.OrderManagementAndExecutionService, 
				Optional.empty(), 
				omesSinkId, 
				Optional.of(1024), 
				Optional.of(1024), 
				Optional.empty(),
				Optional.empty(),
				true, 
				false,
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
				true, 
				128,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				lineHandlerConfig);

		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return new OrderManagementAndExecutionService(serviceConfig, messageService, new LineHandlerBuilder() {	
					@Override
					public LineHandler build() {
						return lineHandler;
					}
				});
			}
		};
		wrapper = testHelper.createService(config, builder);
		messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(MessageSinkRef.of(selfRingBufferSink));
		serviceSinkRef = messageService.messenger().self(); 

		// given
		advanceToReadyState(wrapper, selfMessenger);

		// when
		int orderSidToBeCancelled = 11111;
		CancelOrderRequest cancelRequest = CancelOrderRequest.of(11114, 
				selfMessenger.self(), 
				orderSidToBeCancelled,
				security.sid(),
				Side.BUY);
		selfMessenger.orderSender().sendCancelOrder(serviceSinkRef, cancelRequest);
		wrapper.pushNextMessage();

		// then
		final AtomicInteger expectedCount = new AtomicInteger(1);
		selfMessenger.receiver().orderRequestCompletionHandlerList().add((DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) -> {
			assertEquals(cancelRequest.clientKey(), codec.clientKey());
			assertEquals(OrderRequestCompletionType.REJECTED, codec.completionType());
			assertEquals(OrderRequestRejectType.UNKNOWN_ORDER, codec.rejectType());
			expectedCount.decrementAndGet();
			LOG.debug("Received orderRequestCompletion orderSid:{}", codec.orderSid());			
		});
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenReceiveOrderAcceptedThenNoChange(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		
		// create an order accepted
//		Messenger messenger = testHelper.createMessenger(orderUpdateSender, "test-order-update");
		long channelSeq = 101;
		int channelId = 1;
		selfMessenger.orderSender().sendOrderAccepted(serviceSinkRef,
				channelId,
				channelSeq,
				newSellOrderRequest.orderSid(),
				OrderStatus.NEW,
				newSellOrderRequest.limitPrice(),
				0,
				newSellOrderRequest.quantity(),
				newSellOrderRequest.side(),
				22222,
				newSellOrderRequest.secSid(),
				ExecutionType.NEW,
				LocalTime.now().toNanoOfDay());
		wrapper.pushNextMessage();
		
		// then
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertEquals(purchasingPower, coreService.exposure().purchasingPower());
	}
	
	@Test
	public void givenActiveWhenReceiveSellOrderRejectedThenPositionInc(){
		LOG.debug("givenActiveWhenReceiveSellOrderRejectedThenPositionInc");
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newSellOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();
		
		// create an order rejected
		final int orderId = 55555;
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		String reason = Strings.padEnd("INCORRECT_QTY",  OrderRejectedSbeEncoder.reasonLength(), ' ');
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderRejectedSbeEncoder.reasonLength()));

		selfMessenger.orderSender().sendOrderRejected(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				newSellOrderRequest.orderSid(),
				orderId,
				newSellOrderRequest.secSid(),
				newSellOrderRequest.side(),
				newSellOrderRequest.limitPrice(),
				0,
				0,
				OrderStatus.REJECTED,
				OrderRejectType.INCORRECT_QTY,
				OrderSender.prepareRejectedReason(reason, byteBuffer),
				refUpdateTime);
		wrapper.pushNextMessage();

		// then
		// OrderRequest would definitely be removed
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertNull(coreService.orderManagementContext().getOrderRequest(newSellOrderRequest.orderSid()));
		assertEquals(purchasingPower, coreService.exposure().purchasingPower());
		assertEquals(position + newSellOrderRequest.quantity(), coreService.orderManagementContext().securiytLevelInfo(newSellOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());
	}

	@Test
	public void givenActiveWhenReceiveBuylOrderRejectedThenPurchasingPowerInc(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();
		
		// create an order rejected
		final int orderId = 55555;
		final int cumulativeQty = 0;
		final int leavesQty = 0;
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		String reason = Strings.padEnd("INCORRECT_QTY",  OrderRejectedSbeEncoder.reasonLength(), ' ');
		MutableDirectBuffer byteBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderRejectedSbeEncoder.reasonLength()));
		LOG.info("purchasingPower:{}, leavesQty:{}, limitPrice:{}", purchasingPower, leavesQty, newBuyOrderRequest.limitPrice());
		selfMessenger.orderSender().sendOrderRejected(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				newBuyOrderRequest.orderSid(),
				orderId,
				newBuyOrderRequest.secSid(),
				newBuyOrderRequest.side(),
				newBuyOrderRequest.limitPrice(),
				cumulativeQty,
				leavesQty,
				OrderStatus.REJECTED,
				OrderRejectType.INCORRECT_QTY,
				OrderSender.prepareRejectedReason(reason, byteBuffer),
				refUpdateTime);
		wrapper.pushNextMessage();

		// then
		// OrderRequest would definitely be removed
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertNull(coreService.orderManagementContext().getOrderRequest(newBuyOrderRequest.orderSid()));
		assertEquals(purchasingPower + (newBuyOrderRequest.quantity() - cumulativeQty) * newBuyOrderRequest.limitPrice(), coreService.exposure().purchasingPower());
		assertEquals(position, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());
	}

	@Test
	public void givenActiveWhenReceiveSellOrderExpiredThenPositionInc(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newSellOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long refUpdateTime = selfMessenger.timerService().toNanoOfDay();

		// create an order expired
		final int orderId = 55555;
		selfMessenger.orderSender().sendOrderExpired(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				newSellOrderRequest.orderSid(),
				orderId,
				newSellOrderRequest.secSid(),
				newSellOrderRequest.side(),
				newSellOrderRequest.limitPrice(),
				0,
				0,
				OrderStatus.EXPIRED,
				refUpdateTime);
		wrapper.pushNextMessage();

		// then
		// OrderRequest would definitely be removed
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertNull(coreService.orderManagementContext().getOrderRequest(newSellOrderRequest.orderSid()));
		assertEquals(purchasingPower, coreService.exposure().purchasingPower());
		assertEquals(position + newSellOrderRequest.quantity(), coreService.orderManagementContext().securiytLevelInfo(newSellOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());		
	}

	@Test
	public void givenActiveWhenReceiveBuyOrderExpiredThenPurchasingPowerInc(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long refUpdateTime = selfMessenger.timerService().toNanoOfDay();
		
		// create an order expired
		final int orderId = 55555;
		final int cumulativeQty = 100;
		final int leavesQty = 0;
		selfMessenger.orderSender().sendOrderExpired(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				newBuyOrderRequest.orderSid(),
				orderId,
				newBuyOrderRequest.secSid(),
				newBuyOrderRequest.side(),
				newBuyOrderRequest.limitPrice(),
				cumulativeQty,
				leavesQty,
				OrderStatus.EXPIRED,
				refUpdateTime);
		wrapper.pushNextMessage();

		// then
		// OrderRequest would definitely be removed
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertNull(coreService.orderManagementContext().getOrderRequest(newBuyOrderRequest.orderSid()));
		assertEquals(purchasingPower + (newBuyOrderRequest.quantity() - cumulativeQty) * newBuyOrderRequest.limitPrice(), coreService.exposure().purchasingPower());
		assertEquals(position, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());		
	}
	
	@Test
	public void givenActiveWhenReceiveSellTradeThenPurchasingPowerInc(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long updateTime = LocalTime.now().toNanoOfDay();
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		String executionId = Strings.padEnd("56789", TradeCancelledSbeEncoder.executionIdLength(), ' ');
		
		// create a trade
		int executionQty = newSellOrderRequest.quantity() - 100; 
		selfMessenger.orderSender().sendTradeCreated(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				12345,
				newSellOrderRequest.orderSid(),
				55555,
				OrderStatus.PARTIALLY_FILLED,
				newSellOrderRequest.side(),
				100,
				0,
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				newSellOrderRequest.limitPrice(),
				executionQty,
				newSellOrderRequest.secSid(),
				updateTime);
		wrapper.pushNextMessage();
		
		// then
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertEquals(purchasingPower + executionQty * newSellOrderRequest.limitPrice(), coreService.exposure().purchasingPower());
		assertEquals(position, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());		
	}
	
	@Test
	public void givenActiveWhenReceiveBuyTradeThenPositionInc(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		String executionId = Strings.padEnd("56789", TradeCancelledSbeEncoder.executionIdLength(), ' ');

		// create a trade
		int executionQty = newBuyOrderRequest.quantity() - 100; 
		selfMessenger.orderSender().sendTradeCreated(serviceSinkRef,
				refChannelId,
				refChannelSeq,
				12345,
				newBuyOrderRequest.orderSid(),
				55555,
				OrderStatus.PARTIALLY_FILLED,
				newBuyOrderRequest.side(),
				100,
				0,
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				newBuyOrderRequest.limitPrice(),
				executionQty,
				newBuyOrderRequest.secSid(),
				refUpdateTime);
		wrapper.pushNextMessage();
		
		// then
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertEquals(purchasingPower, coreService.exposure().purchasingPower());
		assertEquals(position + executionQty, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());		
	}

	@Test
	public void givenActiveWhenReceiveBuyOrderFilledThenNoChange(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();

		// create a filled trade
		int executionQty = newBuyOrderRequest.quantity();
//		Messenger messenger = testHelper.createMessenger(orderUpdateSender, "test-order-update");
		
		TestHelper helper = TestHelper.of();
		MutableDirectBuffer buffer = helper.createDirectBuffer();
		TradeSbeEncoder encoder = new TradeSbeEncoder();
		encoder.wrap(buffer, 0)
			.orderSid(newBuyOrderRequest.orderSid())
			.cumulativeQty(executionQty)
			.executionPrice(newBuyOrderRequest.limitPrice())
			.executionQty(executionQty)
			.leavesQty(0)
			.status(OrderStatus.FILLED)
			.side(newBuyOrderRequest.side())
			.secSid(newBuyOrderRequest.secSid());
		TradeSbeDecoder decoder = new TradeSbeDecoder();
		decoder.wrap(buffer, 0, TradeSbeEncoder.BLOCK_LENGTH, TradeSbeEncoder.SCHEMA_VERSION);
//		selfMessenger.orderSender().sendOrderFilled(serviceSinkRef, decoder);
		wrapper.pushNextMessage();
		
		// then
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertEquals(purchasingPower, coreService.exposure().purchasingPower());
		assertEquals(position, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());		
	}

	@Test
	public void givenActiveWhenReceiveSellTradeCancelledThenDecPurchasingPowerAndIncPosition(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newSellOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();

		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		// create a trade cancelled
		int executionQty = newSellOrderRequest.quantity() - 100; 
		final int refChannelId = 1;
		final long refChannelSeq = 1000l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		final int tradeSid = 11111;
		
		String executionId = Strings.padEnd("12312121", TradeCancelledSbeEncoder.executionIdLength(), ' ');
		selfMessenger.orderSender().sendTradeCancelled(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				tradeSid,
				newSellOrderRequest.orderSid(),
				OrderStatus.FILLED,
				ExecutionType.TRADE_CANCEL,
				newSellOrderRequest.side(),
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				newSellOrderRequest.limitPrice(),
				executionQty,
				newSellOrderRequest.secSid(),
				newSellOrderRequest.quantity(),
				newSellOrderRequest.quantity(),
				refUpdateTime);
		wrapper.pushNextMessage();
		
		// then
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertEquals(purchasingPower - executionQty * newSellOrderRequest.limitPrice(), coreService.exposure().purchasingPower());
		assertEquals(position + executionQty, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());		
	}

	@Test
	public void givenActiveWhenReceivingDifferentOrderAndTradeThenPositionOK(){
        // given
	    advanceToReadyState(wrapper, selfMessenger);
	    
	    long power = Long.MAX_VALUE;
        increasePP(wrapper, power);
        
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
        ValidationOrderBook orderBook = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook();
	    
	    // When
	    // Create order
        int price1 = 110000;
        int price2 = 111000;
        int price3 = 100000;
	    sendNewOrderRequest(wrapper, security, Side.BUY, price1, 100);
	    Order order2 = sendNewOrderRequest(wrapper, security, Side.BUY, price2, 100);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price3, 100);
	    assertEquals(3, orderBook.bidOrderBookLevelByPrice().size());
	    
	    
	    // Create trade - matched all order2 at 111000
	    sendTradeCreate(OrderStatus.PARTIALLY_FILLED, order2.sid(), Side.BUY, price2, 50 /* exec */, 50 /* cum */, 50 /* leaves */);
	    sendTradeCreate(OrderStatus.FILLED, order2.sid(), Side.BUY, price2, 50 /* exec */, 100 /* cum */, 0 /* leaves */);
	    assertEquals(2, orderBook.bidOrderBookLevelByPrice().size());
	    
		testHelper.timerService().advance(Duration.ofSeconds(1));

	    Order sellOrder = sendNewOrderRequest(wrapper, security, Side.SELL, price2, 100);
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    
	    // Cancel order
	    sendCancelOrderRequest(wrapper, sellOrder);
	    assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
	}
	
	@Test
	public void givenActiveWhenReceivingWhenReceiveCompositeOrderThenOK(){
        // given
	    advanceToReadyState(wrapper, selfMessenger);
	    
	    long power = Long.MAX_VALUE;
        increasePP(wrapper, power);
        
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
        coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook();
		
        int price1 = 110000;
	    sendNewCompositeOrderRequest(wrapper, Side.BUY, price1, 100);
	}

	@Test
	public void givenActiveWhenReceivingDifferentOrderAndPartialFillAndKillTradeThenPositionOK(){
        // given
	    advanceToReadyState(wrapper, selfMessenger);
	    
	    long power = Long.MAX_VALUE;
        increasePP(wrapper, power);
        
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
        ValidationOrderBook orderBook = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook();
	    
	    // When
	    // Create order
        int price1 = 110000;
        int price2 = 100000;
        int fillAndKillPrice = 120000;
	    sendNewOrderRequest(wrapper, security, Side.BUY, price1, 100);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price2, 100);
	    Order fillAndKillOrder = sendNewOrderRequest(wrapper, security, Side.BUY, fillAndKillPrice, 100, TimeInForce.FILL_AND_KILL, ServiceConstant.DEFAULT_ASSIGNED_THROTTLE_TRACKER_INDEX);
	    assertEquals(3, orderBook.bidOrderBookLevelByPrice().size());
	    
	    
	    // Create trade - matched all order2 at 111000
	    sendTradeCreate(OrderStatus.PARTIALLY_FILLED, fillAndKillOrder.sid(), Side.BUY, fillAndKillPrice, 50 /* exec */, 50 /* cum */, 50 /* leaves */);
	    sendTradeCreate(OrderStatus.PARTIALLY_FILLED, fillAndKillOrder.sid(), Side.BUY, fillAndKillPrice, 20 /* exec */, 70 /* cum */, 30 /* leaves */);
	    fillAndKillOrder.cumulativeExecQty(70);
	    fillAndKillOrder.leavesQty(0);
	    LOG.debug("price of order: {}", fillAndKillOrder.limitPrice());
	    sendOrderCancelled(wrapper, fillAndKillOrder.sid(), fillAndKillOrder);
	    assertEquals(2, orderBook.bidOrderBookLevelByPrice().size());

	    testHelper.timerService().advance(Duration.ofSeconds(1));
	    assertNull(sendNewOrderRequest(wrapper, security, Side.SELL, fillAndKillPrice, fillAndKillOrder.cumulativeExecQty() + 1));
	    Order sellOrder = sendNewOrderRequest(wrapper, security, Side.SELL, fillAndKillPrice, fillAndKillOrder.cumulativeExecQty());
	    assertEquals(1, orderBook.askOrderBookLevelByPrice().size());
	    
	    // Cancel order
	    sendCancelOrderRequest(wrapper, sellOrder);
	    assertEquals(0, orderBook.askOrderBookLevelByPrice().size());
	}
	
	@Test
	public void givenActiveWhenReceivingDifferentOrderForMultipleTrackersThenSendThem(){
        // given
	    advanceToReadyState(wrapper, selfMessenger);
	    
	    long power = Long.MAX_VALUE;
        increasePP(wrapper, power);
        
	    // When
	    // Create order
        int price1 = 110000;
        int price2 = 100000;
	    sendNewOrderRequest(wrapper, security, Side.BUY, price1, 100, 0);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price2, 100, 1);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price1, 100, 0);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price2, 100, 1);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price1, 100, 0);
	    sendNewOrderRequest(wrapper, security, Side.BUY, price2, 100, 1);
	}
	
	private void sendTradeCreate(OrderStatus orderStatus, int ordSid, Side side, int execPrice, int execQty, int cumQty, int leavesQty){
	    final int refChannelId = 1;
	    final long refChannelSeq = 1012l;
	    final int tradeSid = 1;
	    final long refUpdateTime = LocalTime.now().toNanoOfDay();
	    MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
	    String executionId = Strings.padEnd("12312121", TradeCancelledSbeEncoder.executionIdLength(), ' ');
	    int ordId = 23456;
	    selfMessenger.orderSender().sendTradeCreated(serviceSinkRef, 
	            refChannelId,
	            refChannelSeq,
	            tradeSid,
	            ordSid,
	            ordId,
	            orderStatus,
	            side,
	            leavesQty,
	            cumQty,
	            OrderSender.prepareExecutionId(executionId, stringBuffer),
	            execPrice,
	            execQty,
	            security.sid(),
	            refUpdateTime);
	    wrapper.pushNextMessage();
	}

	@Test
	public void givenActiveWhenReceivedBuyTradeCancelledThenIncPurchasingPowerAndDecPosition(){
		// given
		advanceToReadyStateWithTwoOutstandingOrdersSufficientPositionAndPP(wrapper, selfMessenger);

		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		long purchasingPower = coreService.exposure().purchasingPower();
		long position = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition();

		// create a trade cancelled
		final int refChannelId = 1;
		final long refChannelSeq = 1012l;
		final long refUpdateTime = LocalTime.now().toNanoOfDay();
		int executionQty = newBuyOrderRequest.quantity() - 100;
		final int tradeSid = 1;
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
		String executionId = Strings.padEnd("12312121", TradeCancelledSbeEncoder.executionIdLength(), ' ');
		
		selfMessenger.orderSender().sendTradeCancelled(serviceSinkRef, 
				refChannelId,
				refChannelSeq,
				tradeSid,
				newBuyOrderRequest.orderSid(),
				OrderStatus.FILLED,
				ExecutionType.TRADE_CANCEL,
				newBuyOrderRequest.side(),
				OrderSender.prepareExecutionId(executionId, stringBuffer),
				newBuyOrderRequest.limitPrice(),
				executionQty,
				newBuyOrderRequest.secSid(),
				newBuyOrderRequest.quantity(),
				newBuyOrderRequest.quantity(),
				refUpdateTime);
		wrapper.pushNextMessage();
		
		// then
		assertFalse(selfRingBufferSinkPoller.poll());
		verify(this.lineHandler, times(2)).send(isA(NewOrderRequest.class));
		assertEquals(purchasingPower + executionQty * newBuyOrderRequest.limitPrice(), coreService.exposure().purchasingPower());
		assertEquals(position - executionQty, coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook().position().volatileLongPosition());	
	}
	
	@Test
	public void givenActiveWhenReceiveSubscribeOrderUpdateRequestThenUpdateAndSendResponseBack(){
		// given
		advanceToReadyState(wrapper, selfMessenger);
		
		// create subscription request
		CompletableFuture<Request> future = selfMessenger.sendRequest(serviceSinkRef, 
				RequestType.SUBSCRIBE, 
				Parameters.listOf(Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()))
				);
		wrapper.pushNextMessage();
		
		// then
		// 1) verify that we can receive a response
		final AtomicInteger expectedCount = new AtomicInteger(1);
		future.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request t, Throwable u) {
				if (u != null){
					expectedCount.set(Integer.MIN_VALUE);
					return;
				}
				if (t.requestType() == RequestType.SUBSCRIBE){
					expectedCount.decrementAndGet();
				}
			}
		});
		
		// 2) verify that we are one of the subscribers
		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		assertTrue(Arrays.asList(coreService.orderManagementContext().orderUpdateSubscribers()).contains(selfMessenger.self()));
		
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenReceivePurchasingPowerUpdateRequestThenUpdateAndSendResponseBack(){
		// given
		advanceToReadyState(wrapper, selfMessenger);
		
		// create subscription request
		final long newPP = 1234567890l;
		CompletableFuture<Request> future = selfMessenger.sendRequest(serviceSinkRef, 
				RequestType.UPDATE, 
				Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, newPP))
				);
		wrapper.pushNextMessage();
		
		// then
		// 1) verify that we can receive a response
		final AtomicInteger expectedCount = new AtomicInteger(1);
		future.whenComplete(new BiConsumer<Request, Throwable>() {
			@Override
			public void accept(Request t, Throwable u) {
				if (u != null){
					expectedCount.set(Integer.MIN_VALUE);
					return;
				}
				if (t.requestType() == RequestType.UPDATE){
					expectedCount.decrementAndGet();
				}
			}
		});
		
		// 2) verify that PP has been updated
		OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
		assertEquals(newPP * 1000, coreService.exposure().purchasingPower());
		
		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenCancelOrderSentWhenRejectedInternallyThenRemoveCancelRequestFromContext(){
		// given
		advanceToReadyState(wrapper, selfMessenger);

	    long power = Long.MAX_VALUE;
        increasePP(wrapper, power);
        
	    OrderManagementAndExecutionService coreService = (OrderManagementAndExecutionService)wrapper.coreService();
        ValidationOrderBook orderBook = coreService.orderManagementContext().securiytLevelInfo(newBuyOrderRequest.secSid()).validationOrderBook();
	    
	    // When
	    // Create order
        int price1 = 110000;
	    Order order = sendNewOrderRequest(wrapper, security, Side.BUY, price1, 100);
	    assertEquals(1, orderBook.bidOrderBookLevelByPrice().size());
	    	    
	    // Cancel order and reject internally
	    sendCancelOrderRequestAndRejectedInternally(wrapper, order);
	}
	
	@Test
	public void testLoadPositions(){
		Optional<String> items = Optional.of("28696,21000;28110,1000");
		int numChannels = 2;
		int numOutstandingOrdersPerSecurity = 128;
		int numSecurities = 99999;
		int numOutstandingRequests = 128; 
		OrderManagementContext context = OrderManagementContext.of(numOutstandingRequests, numSecurities, numOutstandingOrdersPerSecurity, numChannels);
		OrderManagementAndExecutionService.loadExistingPositions(items, context);
		context.logSecurityPosition();
		assertEquals(21000, context.securiytLevelInfo(28696).validationOrderBook().position().volatileLongPosition());
		assertEquals(1000, context.securiytLevelInfo(28110).validationOrderBook().position().volatileLongPosition());
	}
}
