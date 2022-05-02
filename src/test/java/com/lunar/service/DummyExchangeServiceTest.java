package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

import com.google.common.collect.ImmutableList;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.ExchangeServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.entity.Security;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.service.ReplayExchangeService.CancelMode;
import com.lunar.service.ReplayExchangeService.NewMode;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class DummyExchangeServiceTest {
	private static final Logger LOG = LogManager.getLogger(DummyExchangeServiceTest.class);

	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Mock
	private MessageSink adminSink;
	//@Mock
	private MessageSink refSink;

	private ExchangeServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef admin;
	private MessageSinkRef ref;
	
	private final int systemId = 1;
	private final int adminSinkId = 1;
	private final int exchangeSinkId = 2;
	private final int refSinkId = 3;

	@Mock
	private MessageSink selfSink;
	
	private ServiceTestHelper testHelper;
	
	private final int selfSinkId = 8;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller;
	private LunarServiceTestWrapper wrapper;
	private LunarService messageService;
	private Messenger selfMessenger;
	private MessageSinkRef selfSinkRef;
	private MessageSinkRef serviceSinkRef;
	private Security security;
	
	private final int subscriberSinkId = 9;
	RingBufferMessageSinkPoller refRingBufferSinkPoller;
	private RingBufferMessageSinkPoller subscriberRingBufferSinkPoller;
	private Messenger subscriberMessenger;
	private MessageSinkRef subscriberSinkRef;
	
	@Before
	public void setup(){
		serviceConfig = new ExchangeServiceConfig(1, 
				"test", 
				"testExchange", 
				"test exchange service",
				ServiceType.ExchangeService,
				Optional.of("com.lunar.service.ReplayExchangeService"), 
				exchangeSinkId,
				Optional.of(1024),
				Optional.of(1024),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(1),
				false,
				"",
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		helper = TestHelper.of();
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");
		
		testHelper = ServiceTestHelper.of();
		selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId,
				ServiceType.DashboardService, 
				256, 
				"testDashboard");
		RingBufferMessageSink selfRingBufferSink = selfRingBufferSinkPoller.sink();
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
		wrapper = testHelper.createService(serviceConfig);
		
		
		subscriberRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(subscriberSinkId,
				ServiceType.ClientService, 
				256, 
				"testClientService");
		RingBufferMessageSink subscriberRingBufferSink = subscriberRingBufferSinkPoller.sink();
		subscriberMessenger = testHelper.createMessenger(subscriberRingBufferSink, "subscriber");
		subscriberMessenger.registerEvents();
		subscriberRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				LOG.debug("Received message in subscriber sink");
				subscriberMessenger.receive(event, 0);
				return false;
			}
		});
		subscriberSinkRef = subscriberMessenger.self();
		
        refRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(refSinkId, ServiceType.RefDataService, 256, "testRefData");
        RingBufferMessageSink refRingBufferSink = refRingBufferSinkPoller.sink();
        Messenger refMessenger = testHelper.createMessenger(refRingBufferSink, "self");
        
        refMessenger.receiver().requestHandlerList().add(new Handler<RequestSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
                refMessenger.responseSender().sendResponse(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 0, ResultType.OK);
            }            
        });
        refRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
            @Override
            public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
                refMessenger.receive(event, 0);
                return false;
            }
        });        
        refSink = refRingBufferSink;
        ref = MessageSinkRef.of(refSink, "test-ref");
		

		messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(ref);
		messageService.messenger().referenceManager().register(selfSinkRef);
		messageService.messenger().referenceManager().register(subscriberSinkRef);
		serviceSinkRef = messageService.messenger().self();
		security = Security.of(12345678l, SecurityType.STOCK, "700.HK", 1, false, SpreadTableBuilder.get(SecurityType.STOCK));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStartStop() throws TimeoutException{
		MessageSinkRef self = DummyMessageSink.refOf(systemId, serviceConfig.sinkId(), serviceConfig.name(), serviceConfig.serviceType());

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
					return ReplayExchangeService.of(serviceConfig, messageService);
				}
			}, 
			serviceConfig, 
			helper.messageFactory(), 
			helper.createMessenger(self, senderBuilder),
            helper.systemClock());

		disruptor.handleExceptionsWith(new ServiceFactory.GeneralDisruptorExceptionHandler(serviceConfig.name()));
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
	public void givienCreatedWhenIdleStartThenBeginTrackingServicesAndMoveToWaitState(){
		// move to a particular state
		messageService.onStart();
		
		// verify
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		ServiceStatusTracker tracker = messageService.messenger().serviceStatusTracker();
		assertTrue(tracker.isTrackingService(ServiceType.AdminService));
        assertTrue(tracker.isTrackingService(ServiceType.RefDataService));
		
	}
	
	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToActiveState(){
		advanceToActiveState(wrapper, selfMessenger);
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						admin.sinkId(), 
						ServiceType.AdminService, 
						ServiceStatusType.DOWN, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
                ServiceStatus.of(selfSink.sinkId(),
                        ref.sinkId(), 
                        ServiceType.AdminService, 
                        ServiceStatusType.DOWN, 
                        System.nanoTime()));
        wrapper.pushNextMessage();
		
		// verify
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						admin.sinkId(), 
						ServiceType.AdminService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
                ServiceStatus.of(selfSink.sinkId(),
                        ref.sinkId(), 
                        ServiceType.RefDataService, 
                        ServiceStatusType.UP, 
                        System.nanoTime()));
        wrapper.pushNextMessage();
		
		// verify
		assertEquals(States.READY, messageService.state());
	}

	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		messageService.onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES, messageService.state());
		
		// Pretend sending service status on behalf of Admin, pretending!
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						admin.sinkId(), 
						ServiceType.AdminService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
                ServiceStatus.of(selfSink.sinkId(),
                        ref.sinkId(), 
                        ServiceType.RefDataService, 
                        ServiceStatusType.UP, 
                        System.nanoTime()));
        wrapper.pushNextMessage();		

		// verify
		assertEquals(States.READY, messageService.state());
		
		refRingBufferSinkPoller.poll();
        wrapper.pushNextMessage();        

		assertEquals(States.ACTIVE, messageService.state());
		
	}
	
	@Test
	public void givenActiveWhenReceiveSubscribeRequestThenSubscribe(){
		// given
		advanceToActiveState(wrapper, selfMessenger);
		subscribeToUpdate(selfMessenger, selfRingBufferSinkPoller);
	}
	
	private void subscribeToUpdate(Messenger messenger, RingBufferMessageSinkPoller poller){
		// when
		CompletableFuture<Request> future = messenger.sendRequest(serviceSinkRef, RequestType.SUBSCRIBE, Parameter.NULL_LIST);
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
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		assertTrue(Arrays.asList(coreService.subscribers()).contains(messenger.self()));
		
		poller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenReceiveUnsubscribeRequestThenUnsubscribe(){
		// given
		givenActiveWhenReceiveSubscribeRequestThenSubscribe();

		// when
		CompletableFuture<Request> future = selfMessenger.sendRequest(serviceSinkRef, RequestType.UNSUBSCRIBE, Parameter.NULL_LIST);
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
				if (t.requestType() == RequestType.UNSUBSCRIBE){
					expectedCount.decrementAndGet();
				}
			}
		});

		// 2) verify that we are one of the subscribers
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		assertFalse(Arrays.asList(coreService.subscribers()).contains(selfSinkRef));

		selfRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenReceiveAcceptOnNewCommandThenAckCommandAndUpdateNewMode(){
		// given
		advanceToActiveState(wrapper, selfMessenger);

		// when
		// 1) change the mode
		final int clientKey = selfMessenger.getNextClientKeyAndIncrement(); 
		CompletableFuture<Command> commandResult = selfMessenger.sendCommand(serviceSinkRef, 
				Command.of(selfSinkId, 
						clientKey, 
						CommandType.LINE_HANDLER_ACTION,
						new ImmutableList.Builder<Parameter>().add(
								Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW.value())).build()));
		wrapper.pushNextMessage();
		// 2) subscribe to updates
		subscribeToUpdate(subscriberMessenger, subscriberRingBufferSinkPoller);
		
		// then
		final AtomicInteger expectedCount = new AtomicInteger(2);
		commandResult.whenComplete(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command commandResult, Throwable t) {
				if (t != null || commandResult.ackType() != CommandAckType.OK){
					expectedCount.set(Integer.MIN_VALUE);
					return;
				}
				if (commandResult.clientKey() == clientKey){
					expectedCount.decrementAndGet();
				}
			}
		});
		// 1) verify that we can receive a command ack

		// 2) verify current NewMode 
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		assertEquals(NewMode.ACCEPT_ON_NEW, coreService.currentNewMode());
				
		// 3) send an order request
		final int clientKey2 = selfMessenger.getNextClientKeyAndIncrement();
		NewOrderRequest orderRequest = NewOrderRequest.of(clientKey2, 
				selfSinkRef, 
				security, 
				OrderType.LIMIT_ORDER, 
				100, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				1); 

		// 4) check if the subscriber can receive the update
		subscriberMessenger.receiver().orderAcceptedHandlerList().add(new Handler<OrderAcceptedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
				LOG.debug("received order accepted");
				if (codec.orderSid() == clientKey2){
					expectedCount.decrementAndGet();
				}
			}
		});
		selfMessenger.sendNewOrder(serviceSinkRef, orderRequest);
		wrapper.pushNextMessage();
		
		selfRingBufferSinkPoller.pollAll();
		subscriberRingBufferSinkPoller.pollAll();
		
		assertEquals(0, expectedCount.get());
		assertEquals(1, coreService.orders().size());
	}
	
	@Test
	public void givenActiveWhenReceiveRejectOnNewCommandThenAckCommandAndUpdateNewMode(){
		// given
		advanceToActiveState(wrapper, selfMessenger);
		// 2) subscribe to updates
		subscribeToUpdate(subscriberMessenger, subscriberRingBufferSinkPoller);

		// when
		final int clientKey = selfMessenger.getNextClientKeyAndIncrement(); 
		CompletableFuture<Command> commandResult = selfMessenger.sendCommand(serviceSinkRef, 
				Command.of(selfSinkId, 
						clientKey, 
						CommandType.LINE_HANDLER_ACTION,
						new ImmutableList.Builder<Parameter>().add(
								Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, 
										LineHandlerActionType.CHANGE_MODE_REJECT_ON_NEW.value())).build()));
		wrapper.pushNextMessage();
		
		// then
		final AtomicInteger expectedCount = new AtomicInteger(2);
		// 1) verify that we can receive a command ack
		commandResult.whenComplete(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command commandResult, Throwable t) {
				if (t != null || commandResult.ackType() != CommandAckType.OK){
					expectedCount.set(Integer.MIN_VALUE);
					return;
				}
				if (commandResult.clientKey() == clientKey){
					expectedCount.decrementAndGet();
				}
			}
		});
		
		// 2) verify current NewMode
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		assertEquals(NewMode.REJECT_ON_NEW, coreService.currentNewMode());
		
		// 3) send an order request
		final int clientKey2 = selfMessenger.getNextClientKeyAndIncrement();
		NewOrderRequest orderRequest = NewOrderRequest.of(clientKey2, 
				selfSinkRef, 
				security, 
				OrderType.LIMIT_ORDER, 
				100, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				1); 
		// 4) check if the subscriber can receive the update
		subscriberMessenger.receiver().orderRejectedHandlerList().add(new Handler<OrderRejectedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder codec) {
				if (codec.orderSid() == clientKey2){
					expectedCount.decrementAndGet();
				}
				else {
					LOG.debug("received order rejected with incorrect clientKey | clientKey:{}", clientKey2);					
				}
			}
		});
		selfMessenger.sendNewOrder(serviceSinkRef, orderRequest);
		wrapper.pushNextMessage();
		
		selfRingBufferSinkPoller.pollAll();
		subscriberRingBufferSinkPoller.pollAll();

		assertEquals(0, expectedCount.get());
		assertEquals(0, coreService.orders().size());
	}
	
	private void sendOrder(int clientKey){
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		int orderCount = coreService.orders().size();
		NewOrderRequest orderRequest = NewOrderRequest.of(clientKey, 
				selfMessenger.self(), 
				security, 
				OrderType.LIMIT_ORDER, 
				100, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				650, 
				650, 
				1); 
		selfMessenger.sendNewOrder(serviceSinkRef, orderRequest);
		wrapper.pushNextMessage();
		selfRingBufferSinkPoller.pollAll();
		subscriberRingBufferSinkPoller.pollAll();
		assertEquals(orderCount+1, coreService.orders().size());
	}
	
	@Test
	public void givenActiveWhenReceiveCancelOnCancelCommandThenAckCommandAndUpdateCancelMode(){
		// given
		// subscribe to updates
		// make sure that we are in ACCEPT_ON_NEW mode
		// add an order
		advanceToActiveState(wrapper, selfMessenger);
		subscribeToUpdate(subscriberMessenger, subscriberRingBufferSinkPoller);
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		assertEquals(NewMode.ACCEPT_ON_NEW, coreService.currentNewMode());
		
		final int orderClientKey = selfMessenger.getNextClientKeyAndIncrement();
		sendOrder(orderClientKey);
		
		// when
		final int clientKeyForCommand = selfMessenger.getNextClientKeyAndIncrement(); 
		CompletableFuture<Command> commandResult = selfMessenger.sendCommand(serviceSinkRef, 
				Command.of(selfSinkId, 
						clientKeyForCommand, 
						CommandType.LINE_HANDLER_ACTION,
						new ImmutableList.Builder<Parameter>().add(
								Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, 
										LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL.value())).build()));
		wrapper.pushNextMessage();
		
		// then
		final AtomicInteger expectedCount = new AtomicInteger(2);
		// 1) verify that we can receive a command ack
		commandResult.whenComplete(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command commandResult, Throwable t) {
				if (t != null || commandResult.ackType() != CommandAckType.OK){
					expectedCount.set(Integer.MIN_VALUE);
					return;
				}
				if (commandResult.clientKey() == clientKeyForCommand){
					expectedCount.decrementAndGet();
				}
			}
		});
		
		// 2) verify that we can see
		assertEquals(CancelMode.CANCEL_ON_CANCEL, coreService.currentCancelMode());
		
		// 3) send cancel order request
		final int cancelOrderClientKey = selfMessenger.getNextClientKeyAndIncrement();
		CancelOrderRequest cancelRequest = CancelOrderRequest.of(cancelOrderClientKey, 
				selfSinkRef, 
				orderClientKey, 
				security.sid(),
				Side.NULL_VAL);
		selfMessenger.sendCancelOrder(serviceSinkRef, cancelRequest);

		subscriberMessenger.receiver().orderCancelledHandlerList().add(new Handler<OrderCancelledSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
				if (codec.orderSid() == cancelOrderClientKey && codec.origOrderSid() == orderClientKey){
					expectedCount.decrementAndGet();
				}
				else {
					LOG.debug("received order cancelled with incorrect clientKey | clientKey:{}", cancelOrderClientKey);					
				}
			}
		});
		wrapper.pushNextMessage();
		
		selfRingBufferSinkPoller.pollAll();
		subscriberRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
	
	@Test
	public void givenActiveWhenReceiveRejectOnCancelCommandThenAckCommandAndUpdateCancelMode(){
		// given
		// subscribe to updates
		// make sure that we are in ACCEPT_ON_NEW mode
		// add an order
		advanceToActiveState(wrapper, selfMessenger);
		subscribeToUpdate(subscriberMessenger, subscriberRingBufferSinkPoller);
		ReplayExchangeService coreService = (ReplayExchangeService)wrapper.coreService();
		assertEquals(NewMode.ACCEPT_ON_NEW, coreService.currentNewMode());
		
		final int orderClientKey = selfMessenger.getNextClientKeyAndIncrement();
		sendOrder(orderClientKey);
		
		// when
		final int clientKeyForCommand = selfMessenger.getNextClientKeyAndIncrement(); 
		CompletableFuture<Command> commandResult = selfMessenger.sendCommand(serviceSinkRef, 
				Command.of(selfSinkId, 
						clientKeyForCommand, 
						CommandType.LINE_HANDLER_ACTION,
						new ImmutableList.Builder<Parameter>().add(
								Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, 
										LineHandlerActionType.CHANGE_MODE_REJECT_ON_CANCEL.value())).build()));
		wrapper.pushNextMessage();
		
		// then
		final AtomicInteger expectedCount = new AtomicInteger(2);
		
		commandResult.whenComplete(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command commandResult, Throwable t) {
				if (t != null || commandResult.ackType() != CommandAckType.OK){
					expectedCount.set(Integer.MIN_VALUE);
					return;
				}
				if (commandResult.clientKey() == clientKeyForCommand){
					expectedCount.decrementAndGet();
				}
			}
		});
		
		// 1) verify that we can receive a command ack

		// 2) verify that we can see
		assertEquals(CancelMode.CANCEL_REJECT_ON_CANCEL, coreService.currentCancelMode());
		
		// 3) send cancel order request
		final int cancelOrderClientKey = selfMessenger.getNextClientKeyAndIncrement();
		CancelOrderRequest cancelRequest = CancelOrderRequest.of(cancelOrderClientKey, 
				selfSinkRef, 
				orderClientKey, 
				security.sid(),
				Side.NULL_VAL);
		selfMessenger.sendCancelOrder(serviceSinkRef, cancelRequest);

		subscriberMessenger.receiver().orderCancelRejectedHandlerList().add(new Handler<OrderCancelRejectedSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder codec) {
				if (codec.orderSid() == cancelOrderClientKey){
					expectedCount.decrementAndGet();
				}
				else {
					LOG.debug("received order cancel rejected with incorrect clientKey | clientKey:{}", cancelOrderClientKey);
				}
			}
		});
		wrapper.pushNextMessage();
		
		selfRingBufferSinkPoller.pollAll();
		subscriberRingBufferSinkPoller.pollAll();
		assertEquals(0, expectedCount.get());
	}
}
