package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.ClientServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ServiceStatusTracker;
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
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class DummyClientServiceTest {
	private static final Logger LOG = LogManager.getLogger(DummyClientServiceTest.class);

	@Mock
	private Messenger serviceMessenger;
	
	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Mock
	private MessageSink adminSink;

	private ClientServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef admin;

	private final int systemId = 1;
	private final int adminSinkId = 1;
	private final int clientServiceSinkId = 2;

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

	private final int exchangeSinkId = 3;
	private final int omesSinkId = 4;
	
	@Before
	public void setup(){
		serviceConfig = new ClientServiceConfig(systemId,
				"test", 
				"testClient", 
				"test client service",
				ServiceType.ClientService,
				Optional.of("com.lunar.service.DummyClientService"), 
				clientServiceSinkId,
				Optional.of(1024),
				Optional.of(1024),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(1),
				false,
				"",
				exchangeSinkId);

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
		
		messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(selfSinkRef);
		serviceSinkRef = messageService.messenger().self();
		
		// Operate on the messenger object
		MessageSinkRefMgr refMgr = messageService.messenger().referenceManager();
		when(serviceMessenger.referenceManager()).thenReturn(refMgr);
		when(serviceMessenger.sendNewOrder(any(), any())).thenReturn(new CompletableFuture<>());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStartStop() throws TimeoutException{
		MessageSinkRef self = DummyMessageSink.refOf(systemId, serviceConfig.sinkId(), serviceConfig.name(), serviceConfig.serviceType());

		TestSenderBuilder senderBuilder = new TestSenderBuilder();
		senderBuilder.serviceStatusSender(serviceStatusSender);
		Mockito.doNothing().when(serviceStatusSender).sendServiceStatus(any(MessageSinkRef.class), any(ServiceStatus.class));
		
		ExecutorService executor = Executors.newCachedThreadPool();
		
		Disruptor<MutableDirectBuffer> disruptor =  new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				128,
				executor);
		
		LunarService messageService = LunarService.of(new ServiceBuilder() {

				@Override
				public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
					return DummyClientService.of(serviceConfig, messageService);
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
	}
	
	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToActiveState(){
		// move to active state
		advanceToActiveState(wrapper, selfMessenger);
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(systemId,
						selfSink.sinkId(),
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
		messageService.onStart();
		assertEquals(com.lunar.fsm.service.lunar.States.WAITING_FOR_SERVICES, messageService.state());
		
		DummyClientService coreService = (DummyClientService)wrapper.coreService();
		coreService.messenger(serviceMessenger);
		
		// Pretend sending service status on behalf of Admin, pretending!
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
				ServiceStatus.of(selfMessenger.self().sinkId(),
						admin.sinkId(), 
						ServiceType.AdminService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef,
				ServiceStatus.of(selfMessenger.self().sinkId(),
						exchangeSinkId, 
						ServiceType.ExchangeService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef,
				ServiceStatus.of(selfMessenger.self().sinkId(),
						omesSinkId, 
						ServiceType.OrderManagementAndExecutionService, 
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		// verify
		assertEquals(States.ACTIVE, messageService.state());
	}
	
}
