package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.config.AdminServiceConfig;
import com.lunar.config.CommunicationConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.config.SystemConfig;
import com.lunar.core.LifecycleState;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.util.TestHelper;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

@RunWith(MockitoJUnitRunner.class)
public class AdminServiceTest {
	static final Logger LOG = LogManager.getLogger(AdminServiceTest.class);

	@Mock
	private LunarService messageService;
	
	@Mock
	private Messenger messenger;
	
	@Mock
	private ServiceStatusTracker serviceStatusTracker; 
	
	@Mock
	private ServiceStatusSender serviceStatusSender; 

	@Mock
	private MessageSinkRefMgr refMgr;
	
	@Mock
	private SystemConfig systemConfig;
	
	private static AdminServiceConfig config;
	private static final int refAdminSinkId = 1;
	
	private TestHelper helper;
	private ServiceFactory serviceFactory;
	
	@Mock
	private MessageSink adminSink;
	
	private final int systemId = 1;
	
	@Before
	public void setup(){
		
		config = new AdminServiceConfig(systemId, 
				"test-admin", 
				"test-admin", 
				"test-admin", 
				ServiceType.AdminService, 
				Optional.empty(),
				refAdminSinkId, 
				Optional.of(128),
				Optional.of(128),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(ServiceConstant.DEFAULT_STOP_TIMEOUT_IN_SECOND),
				false,
				"",
				false,
				Duration.ZERO
				);	

		TestSenderBuilder senderBuilder = new TestSenderBuilder();
		senderBuilder.serviceStatusSender(serviceStatusSender);
		Mockito.doNothing().when(serviceStatusSender).sendServiceStatus(any(MessageSinkRef.class), any(ServiceStatus.class));
		
		helper = TestHelper.of();
		
		serviceFactory = TestServiceFactory.of(helper);
		
		when(messageService.messenger()).thenReturn(messenger);
		when(messenger.timerService()).thenReturn(helper.realTimerService());
		when(messenger.serviceStatusTracker()).thenReturn(serviceStatusTracker);
		when(messenger.serviceStatusSender()).thenReturn(serviceStatusSender);
		when(messenger.referenceManager()).thenReturn(refMgr);
		when(refMgr.systemId()).thenReturn(1);
		
		TestHelper.mock(adminSink, refAdminSinkId, ServiceType.AdminService);
		MessageSinkRef sinkRef = MessageSinkRef.of(adminSink);
		when(messenger.self()).thenReturn(sinkRef);
		//when(messageService.ownSinkId()).thenReturn(refAdminSinkId);

	}
	
	@Test
	public void testCreate(){
		AdminService.of(systemConfig, config, messageService, serviceFactory);
	}
	
	@Test
	public void testCreateWithAeron(){
		AdminServiceConfig configWithAeron = new AdminServiceConfig(systemId, 
				"test-admin", 
				"test-admin", 
				"test-admin", 
				ServiceType.AdminService, 
				Optional.empty(),
				refAdminSinkId, 
				Optional.of(128),
				Optional.of(128),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(ServiceConstant.DEFAULT_STOP_TIMEOUT_IN_SECOND),
				false,
				"",
				true,
				Duration.ZERO);
		String hostname = "127.0.0.1";
		String transport = "udp";
		int port = 55161;
		int streamId = 2;
		String aeronDir = "lunar/aeron";
		CommunicationConfig aeronConfig = CommunicationConfig.of(systemId, 
				refAdminSinkId, 
				transport, 
				port, 
				streamId, 
				aeronDir, 
				hostname,
				false);
		configWithAeron.localAeronConfig(aeronConfig);
		AdminService adminService = AdminService.of(systemConfig, configWithAeron, messageService, serviceFactory);
		assertFalse(adminService.remoteContext().running());
		assertTrue(adminService.remoteContext().enableAeron());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testCreateAndStartAeronWithNoAeronConfig(){
		AdminServiceConfig configWithAeron = new AdminServiceConfig(systemId, 
				"test-admin", 
				"test-admin", 
				"test-admin", 
				ServiceType.AdminService, 
				Optional.empty(),
				refAdminSinkId, 
				Optional.of(128),
				Optional.of(128),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(ServiceConstant.DEFAULT_STOP_TIMEOUT_IN_SECOND),
				false,
				"",
				true,
				Duration.ZERO);
		AdminService adminService = AdminService.of(systemConfig, configWithAeron, messageService, serviceFactory);
		adminService.idleStart();
	}
	
	@Ignore
	@Test
	public void testCreateAndStartAeron(){
		when(messenger.timerService()).thenReturn(helper.realTimerService());

		AdminServiceConfig configWithAeron = new AdminServiceConfig(systemId, 
				"test-admin", 
				"test-admin", 
				"test-admin", 
				ServiceType.AdminService, 
				Optional.empty(),
				refAdminSinkId, 
				Optional.of(128),
				Optional.of(128),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(ServiceConstant.DEFAULT_STOP_TIMEOUT_IN_SECOND),
				false,
				"",
				true,
				Duration.ofSeconds(1l));
		String hostname = "127.0.0.1";
		String transport = "udp";
		int port = 55161;
		int streamId = 2;
		String aeronDir = "lunar/aeron";
		CommunicationConfig aeronConfig = CommunicationConfig.of(systemId, 
				refAdminSinkId, 
				transport, 
				port, 
				streamId, 
				aeronDir, 
				hostname,
				true);
		configWithAeron.localAeronConfig(aeronConfig);
		AdminService adminService = AdminService.of(systemConfig, configWithAeron, messageService, serviceFactory);
		adminService.idleStart();
		
		assertEquals(LifecycleState.ACTIVE, adminService.remoteContext().distributionService().state());
	}

	@Test
	public void testCreateOneChild(){
		// Create configuration for one child
		final int numChildren = 1;
		addChildConfig(config, numChildren, 2);
		AdminService adminService = AdminService.of(systemConfig, config, messageService, serviceFactory);
		adminService.idleStart();
		assertEquals(numChildren, adminService.children().size());
	}

	@Test
	public void testCreateChildren(){
		final int numChildren = 3;
		addChildConfig(config, numChildren, 2);
		AdminService adminService = AdminService.of(systemConfig, config, messageService, serviceFactory);
		adminService.idleStart();
		assertEquals(numChildren, adminService.children().size());
	}
	
	public static void addChildConfig(AdminServiceConfig config, int numChildren, int startSinkId){
		final int systemId = 1;
		for (int i = 0; i < numChildren; i++){
			ServiceConfig childConfig = new ServiceConfig(systemId,
					"child" + i,
					"child" + i, 
					"child" + i + "-desc", 
					ServiceType.EchoService,
					Optional.empty(),
					startSinkId + i, 
					Optional.of(128), 
					Optional.of(128), 
					Optional.of(1), //Optional.empty(),
					Optional.empty(),
					true,
					false,
					Duration.ofSeconds(ServiceConstant.DEFAULT_STOP_TIMEOUT_IN_SECOND),
					false,
					"");
			config.childServiceConfigs().add(childConfig);
		}
	}
	
	public void givenChildrenCreatedWhenReceiveStatusChangeFromItselfThenBroadcastToAllButItsel(){
		
	}
	
	@Test
	public void givenChildrenCreatedWhenReceiveStatusChangeFromChildThenBroadcastToAllButItselfAndTheChild(){
		// TODO local children
		// TODO remote admins
		// given
		final int numChildren = 3;
		final int startSinkId = 2;
		addChildConfig(config, numChildren, startSinkId);

		when(messenger.referenceManager()).thenReturn(refMgr);
		AdminService adminService = AdminService.of(systemConfig, config, messageService, serviceFactory);
		adminService.idleStart();
		
		// given one status
		Int2ObjectOpenHashMap<ServiceStatus> statuses = new Int2ObjectOpenHashMap<>();
		statuses.put(startSinkId, ServiceStatus.of(systemId, startSinkId, startSinkId, ServiceType.EchoService, ServiceStatusType.UP, System.nanoTime()));
		statuses.put(startSinkId + 1, ServiceStatus.of(systemId, startSinkId + 1, startSinkId + 1, ServiceType.EchoService, ServiceStatusType.UP, System.nanoTime()));
		statuses.put(startSinkId + 2, ServiceStatus.of(systemId, startSinkId + 2, startSinkId + 2, ServiceType.EchoService, ServiceStatusType.UP, System.nanoTime()));
		
		// when
		final MessageSinkRef refSenderSink = MessageSinkRef.createValidNullSinkRef(1, ServiceType.RefDataService, "test-any");
		when(messenger.sinkRef(Mockito.anyInt())).thenReturn(refSenderSink);
		when(serviceStatusTracker.statuses()).thenReturn(statuses.values());
		adminService.anyServiceStatusHandler.handle(ServiceStatus.of(systemId, startSinkId, startSinkId, ServiceType.EchoService, ServiceStatusType.UP, System.nanoTime()));

		// verify
		// broadcast to all local except sender and admin
		// broadcast to all remote
		// send latest service status to the sender
		ArgumentCaptor<int[]> exceptSinksCaptor = ArgumentCaptor.forClass(int[].class);
		verify(messenger, times(1)).sendServiceStatusToLocalSinksExcept(any(ServiceStatus.class), exceptSinksCaptor.capture());
		int[] exceptSinks = exceptSinksCaptor.getValue();
		LOG.info("{}", exceptSinks);
		assertEquals(refAdminSinkId, exceptSinks[0]);
		assertEquals(startSinkId, exceptSinks[1]);
		
		verify(messenger, times(1)).sendServiceStatusToRemoteAdmin(any(ServiceStatus.class));
		
		// verify - send latest service status of all other sinks to the sender
		ArgumentCaptor<ServiceStatus> serviceStatusCaptor = ArgumentCaptor.forClass(ServiceStatus.class);
		verify(serviceStatusSender, times(2)).sendServiceStatus(any(MessageSinkRef.class), serviceStatusCaptor.capture());
		List<ServiceStatus> values = serviceStatusCaptor.getAllValues();
		values.sort((o1, o2) -> {return (o1.sinkId() > o2.sinkId()) ? 1 : (o1.sinkId() == o2.sinkId() ? 0 : -1);});
		assertEquals(startSinkId + 1, values.get(0).sinkId());
		assertEquals(startSinkId + 2, values.get(1).sinkId());
	}
	
	@Test
	public void givenChildrenCreatedWhenCloseThenStopAfterAllChildrenAreStopped(){
		final int numChildren = 3;
		final int startSinkId = 2;
		addChildConfig(config, numChildren, startSinkId);

		// given
		AdminService adminService = AdminService.of(systemConfig, config, messageService, serviceFactory);
		adminService.createChildren();

		// start
		adminService.startChildren();
		
		// stop synchronously
		adminService.stopChildren();
	
		// verify
		for (MessageServiceExecutionContext child : adminService.children().values()){
			assertTrue(child.isStopped());
		}
	}
	
}
