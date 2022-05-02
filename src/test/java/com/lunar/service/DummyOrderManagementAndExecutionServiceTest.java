package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.LineHandlerConfig;
import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.core.WaitStrategy;
import com.lunar.exception.ConfigurationException;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Messenger;
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
public class DummyOrderManagementAndExecutionServiceTest {
	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Mock
	private MessageSink adminSink;

	private OrderManagementAndExecutionServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef admin;
	
	private final int systemId = 1;
	private final int adminSinkId = 1;
	private final int mdsSinkId = 2;
	
	@Mock
	private MessageSink selfSink;
	
	@Before
	public void setup() throws ConfigurationException{
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
		serviceConfig = new OrderManagementAndExecutionServiceConfig(1,
				"test", 
				"testOMES", 
				"test omes service",
				ServiceType.OrderManagementAndExecutionService,
				Optional.of("com.lunar.service.DummyOrderManagementAndExecutionService"),
				mdsSinkId,
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
				1024,
				128,
				16,
				2,
				true,
				128,
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				lineHandlerConfig);

		helper = TestHelper.of();
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");
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
					return DummyOrderManagementAndExecutionService.of(serviceConfig, messageService);
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
	}

	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToActiveState(){
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);
		
		// move to active state
		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToActiveState(wrapper, selfMessenger);
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						admin.sinkId(), 
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
		// verify
		assertEquals(States.ACTIVE, messageService.state());

	}

	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);

		messageService.onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES, messageService.state());
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfSink.sinkId(),
						admin.sinkId(), 
						ServiceType.AdminService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		// verify
		assertEquals(States.ACTIVE, messageService.state());
	}
	
	public void givenActiveWhenReceiveSubscriptionFailureThenMoveToStopState(){}
	
}
