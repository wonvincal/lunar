package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.ServiceConfig;
import com.lunar.config.StrategyServiceConfig;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.entity.Security;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class DummyStrategyServiceTest {
	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Mock
	private MessageSink rdsSink;

	@Mock
	private MessageSink omesSink;

	@Mock
	private MessageSink mdsSink;
	
	@Mock
	private MessageSink adminSink;
	
	@Mock
	private MessageSink mdsssSink;

	private StrategyServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef omes;
	private MessageSinkRef mds;
	private MessageSinkRef mdsss;
	private MessageSinkRef rds;
	private MessageSinkRef admin;
	
	private final int adminSinkId = 1;
	private final int mdsSinkId = 2;
	private final int omesSinkId = 3;
	private final int stratSinkId = 4;
	private final int rdsSinkId = 5;
	private final int mdsssSinkId = 6;
	
	@Mock
	private MessageSink selfSink;
	
	private final long secSid = 1001;
	
	@Before
	public void setup(){
		serviceConfig = new StrategyServiceConfig(1,
				"test", 
				"testStrat", 
				"test strategy service",
				ServiceType.StrategyService, 
				Optional.of("com.lunar.service.DummyStrategyService"),
				stratSinkId,
				Optional.of(1024),
				Optional.of(1024),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(1),
				false,
				"",
				5000,
				200,
				10,
				"2001",
				String.valueOf(secSid),
				false,
				0,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		helper = TestHelper.of();
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
		mdsSink = TestHelper.mock(mdsSink, mdsSinkId, ServiceType.MarketDataService);
		omesSink = TestHelper.mock(omesSink, omesSinkId, ServiceType.OrderManagementAndExecutionService);
		rdsSink = TestHelper.mock(rdsSink, rdsSinkId, ServiceType.RefDataService);
		mdsssSink = TestHelper.mock(mdsssSink, mdsssSinkId,  ServiceType.MarketDataSnapshotService);
		admin = MessageSinkRef.of(adminSink, "test-admin");
		mds = MessageSinkRef.of(mdsSink, "test-mds");
		mdsss = MessageSinkRef.of(mdsssSink, "test-mdsss");
		omes = MessageSinkRef.of(omesSink, "test-omes");
		rds = MessageSinkRef.of(rdsSink, "test-rds");
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStartStop() throws TimeoutException{

		MessageSinkRef self = DummyMessageSink.refOf(serviceConfig.systemId(), serviceConfig.sinkId(), serviceConfig.name(), serviceConfig.serviceType());

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
				return DummyStrategyService.of(serviceConfig, messageService);
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
	
	public void testStartSubscribed(){}
	
	public void testStartUnsubscribed(){}
	
	public void testStartHandleMarketData(){}
	
	@Test
	public void givienCreatedWhenIdleStartThenBeginTrackingServicesAndMoveToWaitState(){
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		
		// move to a particular state
		messageService.onStart();
		
		// verify
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		ServiceStatusTracker tracker = messageService.messenger().serviceStatusTracker();
		assertTrue(tracker.isTrackingService(ServiceType.AdminService));
		assertTrue(tracker.isTrackingService(ServiceType.MarketDataService));
		assertTrue(tracker.isTrackingService(ServiceType.OrderManagementAndExecutionService));
	}

	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToActiveState(){
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(mdsss);
		messageService.messenger().referenceManager().register(omes);
		
		// move to active state
		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToActiveState(wrapper, selfMessenger);
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						mds.sinkId(), 
						ServiceType.MarketDataService, 
						ServiceStatusType.DOWN, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		// verify
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						mds.sinkId(), 
						ServiceType.MarketDataService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		// verify
		assertEquals(States.ACTIVE, messageService.state());

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						mds.sinkId(), 
						ServiceType.MarketDataService, 
						ServiceStatusType.INITIALIZING, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		// verify
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
	}

	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(mdsss);
		messageService.messenger().referenceManager().register(omes);
		messageService.messenger().referenceManager().register(rds);

		messageService.onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES, messageService.state());
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						admin.sinkId(), 
						ServiceType.AdminService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						omes.sinkId(), 
						ServiceType.OrderManagementAndExecutionService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						mds.sinkId(), 
						ServiceType.MarketDataService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						rds.sinkId(), 
						ServiceType.RefDataService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		// verify
		assertEquals(States.READY, messageService.state());
		
		// verify
		final AtomicBoolean received = new AtomicBoolean(false);
		final AtomicInteger key = new AtomicInteger();
		ArgumentCaptor<MutableDirectBuffer> captor = ArgumentCaptor.forClass(MutableDirectBuffer.class);
		verify(rdsSink, times(1)).tryPublish(captor.capture(), anyInt(), anyInt());
		
		Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
				received.set(true);
				key.set(codec.clientKey());
				assertEquals(RequestType.GET, codec.requestType());
				assertEquals(secSid, codec.parameters().next().parameterValueLong());
			}
		};
		
		MutableDirectBuffer value = captor.getValue();
		selfMessenger.receiver().requestHandlerList().add(requestHandler);
		selfMessenger.receiver().receive(value, 0);
		selfMessenger.receiver().requestHandlerList().remove(requestHandler);
		assertTrue(received.get());

		// send response back 
		selfMessenger.responseSender().sendSbeEncodable(serviceSinkRef, 
				key.get(), 
				BooleanType.TRUE, 
				0, 
				ResultType.OK, Security.of(secSid, SecurityType.STOCK, String.valueOf(secSid), 2001, false, SpreadTableBuilder.get(SecurityType.STOCK)).omesSink(omes).mdsSink(mds).mdsssSink(mdsss));
		wrapper.pushNextMessage();

		// verify
		assertEquals(States.ACTIVE, messageService.state());
	}
	
	public void givenActiveWhenReceiveSubscriptionFailureThenMoveToStopState(){}
	
	@Test
	public void givenActiveWhenReceiveStartCommandThenSendSubscriptionRequest(){
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(mdsss);
		messageService.messenger().referenceManager().register(omes);
		messageService.messenger().referenceManager().register(rds);

		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToActiveState(wrapper, selfMessenger);

		selfMessenger.commandSender().sendCommand(serviceSinkRef, 
				Command.of(selfMessenger.self().sinkId(), 
						selfMessenger.getNextClientKeyAndIncrement(),
						CommandType.START, 
						Parameter.NULL_LIST));
		wrapper.pushNextMessage();
		
		// verify
		final AtomicBoolean received = new AtomicBoolean(false);
		ArgumentCaptor<MutableDirectBuffer> captor = ArgumentCaptor.forClass(MutableDirectBuffer.class);
		verify(mdsSink, times(1)).tryPublish(captor.capture(), anyInt(), anyInt());
		
		MutableDirectBuffer value = captor.getValue();
		selfMessenger.receiver().requestHandlerList().add(new Handler<RequestSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
				received.set(true);
				assertEquals(RequestType.SUBSCRIBE, codec.requestType());
			}
		});
		selfMessenger.receiver().receive(value, 0);
		assertTrue(received.get());
	}
	
	@Test
	public void givenActiveWhenReceiveStopCommandThenMoveToStopState(){
		// check no outstanding handlers
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(mdsss);
		messageService.messenger().referenceManager().register(omes);

		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToActiveState(wrapper, selfMessenger);
		
		// when
		selfMessenger.commandSender().sendCommand(serviceSinkRef, 
				Command.of(selfMessenger.self().sinkId(), 
						selfMessenger.getNextClientKeyAndIncrement(),
						CommandType.STOP, 
						Parameter.NULL_LIST));
		wrapper.pushNextMessage();
		
		// verify
		assertEquals(States.STOP, messageService.state());
	}	
}
