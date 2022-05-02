package com.lunar.fsm.service.lunar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService.Mode;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.ValidNullMessageSink;
import com.lunar.service.DummyService;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class LunarServiceTest {
	static final Logger LOG = LogManager.getLogger(LunarServiceTest.class);
	private final int systemId = 1;
	@Mock
	private MessageSink self;
	private int ownSinkId = 27;
	private ServiceType ownSinkServiceType = ServiceType.DashboardService;
	
	@Mock
	private MessageSink admin;
	private int adminSinkId = 26;
	
	private MessageSink warmup;
	private int warmupSinkId = 28;
	
	private TestHelper helper;
	private ServiceConfig serviceConfig;
	private LunarService lunarService;
	
	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Before
	public void setup(){
		serviceConfig = new ServiceConfig(1, "test", "testLunar", "testing lunar base service", 
										  ownSinkServiceType, 
										  Optional.empty(),
				   						  ownSinkId, 
				   						  Optional.of(1024),
				   						  Optional.of(1024),
				   						  Optional.empty(),
				   						  Optional.empty(),
				   						  true,
				   						  false,
				   						  Duration.ofSeconds(1),
				   						  false,
				   						  "");
		
		TestSenderBuilder senderBuilder = new TestSenderBuilder();
		senderBuilder.serviceStatusSender(serviceStatusSender);
		
		MessageSinkRef selfRef = MessageSinkRef.of(self);
		
		warmup = ValidNullMessageSink.of(systemId, warmupSinkId, ServiceType.WarmupService);
		helper = TestHelper.of(
				TestHelper.mock(self, systemId, ownSinkId, ownSinkServiceType),
				TestHelper.mock(admin, systemId, adminSinkId, ServiceType.AdminService),
				warmup
				);
		helper.register(admin, "admin");
		helper.register(warmup, "warmup");
		
		lunarService = LunarService.of(DummyService.BUILDER, 
				serviceConfig, 
				helper.messageFactory(),
				helper.createMessenger(selfRef, senderBuilder),
				helper.systemClock());
	}
	
	@After
	public void tearDown(){
		
	}
	
	@Test
	public void givenCreatedThenAtIdle(){
		// given - noop
		// when - noop
		// then
		assertEquals(States.IDLE, lunarService.state());
		verify(serviceStatusSender, times(0)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
	}
	
	@Test
	public void givenIdleWhenReceiveThreadStartThenGoToWaitForServices(){
		// given - noop
		assertEquals(States.IDLE, lunarService.state());
		assertFalse(lunarService.messenger.serviceStatusTracker().hasHandlerForAnyServiceStatusChange());
		
		// when
		lunarService.onStart();
		
		// TODO timer is set
		// there must be a handler to get 'all services are up'
		assertTrue(lunarService.messenger.serviceStatusTracker().hasHandlerForAnyServiceStatusChange()); 
		assertEquals(States.WAITING_FOR_SERVICES, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.INITIALIZING), anyLong());
	}

	@Test
	public void givenWaitForServicesWhenChangeModeToRecoveryThenReturnFalse(){
		// given
		lunarService.state(States.WAITING_FOR_SERVICES);
		
		// when, then
		assertFalse(lunarService.mode(Mode.RECOVERY));
	}
	
	@Test
	public void givenWaitForServicesWhenReceiveActivateThenGoToActive(){
		// given
		lunarService.buildService();
		lunarService.state(States.WAITING_FOR_SERVICES);
		
		// when
		lunarService.send(StateTransitionEvent.ACTIVATE);
		
		// then
		assertEquals(States.ACTIVE, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.UP), anyLong());
	}
	
	@Test
	public void givenWaitForServicesWhenReceiveTimeoutThenGoToStop(){
		// given
		lunarService.buildService();
		lunarService.state(States.WAITING_FOR_SERVICES);

		// when
		lunarService.send(StateTransitionEvent.TIMEOUT);

		// then
		assertEquals(States.STOP, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());

//		verify(self, times(1)).tell(any(KillServiceThread.class));
	}
	
	@Test
	public void givenWaitForServicesWhenReceiveThreadStopThenGoToStopped(){
		// given
		lunarService.buildService();
		lunarService.state(States.WAITING_FOR_SERVICES);
		lunarService.threadStarted(true);
		
		// when
		lunarService.onShutdown();

		// then
		assertEquals(States.STOPPED, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
	}

	@Test
	public void givenWaitForServicesWhenReceiveFailThenGoToStop(){
		// given
		lunarService.state(States.WAITING_FOR_SERVICES);
		
/*		lunarService.messageCodec().decoder().addCommandHandler(new Handler<CommandSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoderCommandSbeDecoder codec) {
				throw new RuntimeException("intentional runtime exception");
			}
		});
		helper.codecForTest().encoder().encodeCommand(helper.frameForTest(), ownSinkId, ownSinkId, 0, 0, CommandType.NA, BooleanType.FALSE);
		lunarService.onEvent(helper.frameForTest(), 0, true);

*/		// then
		assertEquals(States.WAITING_FOR_SERVICES, lunarService.state());
	}

	@Test
	public void givenWaitForServicesWhenReceiveStopThenGoToStop(){
		// given
		lunarService.buildService();
		lunarService.state(States.WAITING_FOR_SERVICES);

		// when
		lunarService.send(StateTransitionEvent.STOP);

		// then
		assertEquals(States.STOP, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());		
	}
	
	@Test
	public void givenReadyWhenReceiveStateTransitionExceptionThenGoToStop(){
		// given
		lunarService.buildService();
		lunarService.state(States.READY);
		
		// when - throw StateTransitionException somewhere
		DummyService service = (DummyService)lunarService.service();
		service.throwExceptionOnActiveEnter(true);
		lunarService.send(StateTransitionEvent.ACTIVATE);

		// then
		assertEquals(States.STOP, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
	}

	@Test
	public void givenActiveWhenReceiveFailThenGoToStop(){
		// given
		lunarService.buildService();
		lunarService.state(States.ACTIVE);

		// when
		lunarService.send(StateTransitionEvent.FAIL);

		// then
		assertEquals(States.STOP, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
//		verify(self, times(1)).tell(any(KillServiceThread.class));
	}
	
	@Test
	public void givenActiveWhenReceiveWaitThenGoToWait(){
		// given
		lunarService.buildService();
		lunarService.state(States.ACTIVE);

		// when
		lunarService.send(StateTransitionEvent.WAIT);

		// then
		assertEquals(States.WAITING_FOR_SERVICES, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.INITIALIZING), anyLong());		
	}
	
	@Test
	public void givenActiveWhenReceiveStopThenGoToStop(){
		// given
		lunarService.buildService();
		lunarService.state(States.ACTIVE);

		// when
		lunarService.send(StateTransitionEvent.STOP);

		// then
		assertEquals(States.STOP, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());		
	}
	
	@Test
	public void givenActiveWhenReceiveThreadStopThenGoToStopped(){
		// given
		lunarService.buildService();
		lunarService.state(States.ACTIVE);
		lunarService.threadStarted(true);
		
		// when
		lunarService.onShutdown();

		// then
		assertEquals(States.STOPPED, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
	}
	
	@Test
	public void givenStoppedWhenReceiveAnythingThenDoNothing(){
		// given
		lunarService.state(States.STOPPED);
		
		// when
		lunarService.send(StateTransitionEvent.FAIL);

		// then
		assertEquals(States.STOPPED, lunarService.state());
		verify(serviceStatusSender, times(0)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
	}

	@Test
	public void givenStopWhenReceiveThreadStopThenGoToStopped(){
		// given
		lunarService.buildService();
		lunarService.threadStarted(true);
		lunarService.state(States.STOP);
		
		// when
		lunarService.onShutdown();

		// then
		assertEquals(States.STOPPED, lunarService.state());
		verify(serviceStatusSender, times(1)).sendOwnServiceStatus(any(MessageSinkRef.class), eq(ServiceStatusType.DOWN), anyLong());
	}
	
	@Test
	public void givenWaitingForWarmupWhenReceiveWarmupThenGoToWarmup(){
		// given
		lunarService.buildService();
		lunarService.state(States.WAITING_FOR_WARMUP_SERVICES);
		
		// when
		lunarService.send(StateTransitionEvent.WARMUP);
		
		// then
		assertEquals(States.WARMUP, lunarService.state());
	}
	
	@Test
	public void givenWarmupWhenReceiveWarmupCompleteThenGoToReset(){
		// given
		lunarService.buildService();
		lunarService.state(States.WARMUP);
		
		// when
		lunarService.send(StateTransitionEvent.WARMUP_COMPLETE);
		
		// then
		assertEquals(States.RESET, lunarService.state());
	}
	
	@Test
	public void givenResetWhenReceiveResetCompleteThenGoToWaiting(){
		// given
		lunarService.buildService();
		lunarService.state(States.RESET);
		
		// when
		lunarService.send(StateTransitionEvent.RESET_COMPLETE);
		
		// then
		assertEquals(States.WAITING_FOR_SERVICES, lunarService.state());		
	}
	
	@Test
	public void givenActiveWhenReceiveResetThenGoToReset(){
		// given
		lunarService.buildService();
		lunarService.state(States.ACTIVE);
		
		// when
		lunarService.send(StateTransitionEvent.RESET);
		
		// then
		assertEquals(States.RESET, lunarService.state());		
	}
}
