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
import com.lunar.config.MarketDataServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.ReplayMarketDataService;
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
public class MarketDataServiceTest {
	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Mock
	private MessageSink adminSink;
    @Mock
    private MessageSink mdsssSink;
    @Mock
    private MessageSink refSink;

	private MarketDataServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef admin;
	private MessageSinkRef mdsss;
	private MessageSinkRef ref;
	
	private final int adminSinkId = 1;
	private final int mdsSinkId = 2;
	private final int mdsssSinkId = 3;
	private final int refSinkId = 4;
	private final int numSecurities = 10;
	
	@Mock
	private MessageSink selfSink;
	
	@Before
	public void setup(){
		serviceConfig = new MarketDataServiceConfig(1,
				"test", 
				"testMDS", 
				"test mds service",
				ServiceType.MarketDataService,
				Optional.of("com.lunar.marketdata.ReplayMarketDataService"), 
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
				numSecurities,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		helper = TestHelper.of();
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
  		refSink = TestHelper.mock(refSink, refSinkId, ServiceType.RefDataService);
        mdsssSink = TestHelper.mock(mdsssSink, mdsssSinkId, ServiceType.MarketDataSnapshotService);
	
		admin = MessageSinkRef.of(adminSink, "test-admin");
		mdsss = MessageSinkRef.of(mdsssSink, "test-mdsss");
		ref = MessageSinkRef.of(refSink, "test-ref");		
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
				return ReplayMarketDataService.of(serviceConfig, messageService);
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
		messageService.messenger().referenceManager().register(mdsss);
		messageService.messenger().referenceManager().register(ref);
		
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
		assertEquals(States.READY, messageService.state());

	}

	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);
        messageService.messenger().referenceManager().register(mdsss);
        messageService.messenger().referenceManager().register(ref);

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
                        mdsss.sinkId(), 
                        ServiceType.MarketDataSnapshotService, 
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
	
}
