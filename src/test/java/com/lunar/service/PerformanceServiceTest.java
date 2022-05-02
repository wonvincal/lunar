package com.lunar.service;

public class PerformanceServiceTest {
	/*
	static final Logger LOG = LogManager.getLogger(PerformanceServiceTest.class);
	private LunarService messageService;
	private PerformanceService perfService;
	private final long statGatheringFreqNs = 10_000_000_000L;

	private final AtomicInteger selfRingBufferSeq = new AtomicInteger(1);	
	private PerformanceServiceConfig serviceConfig;
//	private LunarServiceAccessor accessor;
	
	@Mock
	private MessageSink self;
	private int ownSinkId = 12;
	private ServiceType ownSinkServiceType = ServiceType.PerformanceService;

	@Mock
	private MessageSink admin;
	private int adminSinkId = 1;
	
	@Mock
	private MessageSink anyServiceSink;
	private int anyServiceSinkId = 2;
	
	@Mock
	private MessageSink dashboardSink;
	private int dashboardSinkId = 3;

	private TestHelper helper;

	@Before
	public void setup(){
		serviceConfig = new PerformanceServiceConfig("perf", "testPerf", "testing performance", 
												   ownSinkServiceType, 
												   ownSinkId, 
												   Optional.of(1024), 
												   Optional.of(1024), 
												   true, 
												   statGatheringFreqNs);
		
		helper = TestHelper.of(TestHelper.mock(self, ownSinkId, ownSinkServiceType, false),
							   TestHelper.mock(admin, adminSinkId, ServiceType.AdminService, false),
							   TestHelper.mock(anyServiceSink, anyServiceSinkId, ServiceType.RefDataService, false),
							   TestHelper.mock(dashboardSink, dashboardSinkId, ServiceType.DashboardService, false));

		messageService = new LunarService(new ServiceBuilder() {
			
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return new PerformanceService(serviceConfig, messageService);
			}
		}, serviceConfig, helper.messagingContext(), MessageSinkRef.of(self));
//		perfService = new PerformanceService(serviceConfig, 
//											 helper.messagingContext(), 
//											 MessageSinkRef.of(self));
//		accessor = LunarServiceAccessor.of(perfService);
	}

	@Test
	public void givenCreatedThenStateIsIdle(){
		assertEquals(States.IDLE, messageService.state());
	}
	
	@Test
	public void givenIdleWhenOnStartThenSendInitialing(){
		// given 
		assertEquals(States.IDLE, messageService.state());
		
		// when
		messageService.onStart();
//		perfService.onStart();
		
		// then
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		verify(admin, times(1)).tell(any(ServiceStatus.class)); // tell admin that i am initializing

	}
	
	@Test
	public void givenWaitingForServicesWhenReceiveAdminStatusThenStartTimerAndSendReadyAndGoToActiveState(){
		// given 
		assertEquals(States.IDLE, messageService.state());
		messageService.onStart();
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		
		// when
		ServiceStatus serviceStatus = new ServiceStatus(adminSinkId, 
				  1,
				  adminSinkId,
				  ServiceType.AdminService,
				  ServiceStatusType.UP,
				  System.nanoTime());
		serviceStatus.encode(ownSinkId, helper.codecForTest(), helper.frameForTest());
		messageService.onEvent(helper.frameForTest(), 1, true);

		// then
		assertEquals(1, helper.timerService().numOutstanding());
		verify(admin, times(2)).tell(any(ServiceStatus.class)); // tell admin that i am ready
		assertEquals(States.ACTIVE, messageService.state());
	}

	@Test
	public void givenWaitingForServicesWhenReceiveAdminStatusNotReadyThenNoChange(){
		// given 
		assertEquals(States.IDLE, messageService.state());
		messageService.onStart();
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		
		// when
		ServiceStatus serviceStatus = new ServiceStatus(adminSinkId, 
				  1,
				  adminSinkId,
				  ServiceType.AdminService,
				  ServiceStatusType.DOWN,
				  System.nanoTime());
		serviceStatus.encode(ownSinkId, helper.codecForTest(), helper.frameForTest());
		messageService.onEvent(helper.frameForTest(), 1, true);

		// then
		assertEquals(0, helper.timerService().numOutstanding());
		verify(admin, times(1)).tell(any(ServiceStatus.class)); // tell admin that i am initializing
 	}
	
	@Test
	public void givenActiveWhenAdminIsNotReadyThenStayInActive(){
		// given
		assertEquals(States.IDLE, messageService.state());
		messageService.onStart();
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		ServiceStatus serviceStatus = new ServiceStatus(adminSinkId, 
				  1,
				  adminSinkId,
				  ServiceType.AdminService,
				  ServiceStatusType.UP,
				  System.nanoTime());
		serviceStatus.encode(ownSinkId, helper.codecForTest(), helper.frameForTest());
		messageService.onEvent(helper.frameForTest(), 1, true);
		assertEquals(States.ACTIVE, messageService.state());

		// when
		serviceStatus = new ServiceStatus(adminSinkId, 
				  1,
				  adminSinkId,
				  ServiceType.AdminService,
				  ServiceStatusType.DOWN,
				  System.nanoTime());
		serviceStatus.encode(ownSinkId, helper.codecForTest(), helper.frameForTest());
		messageService.onEvent(helper.frameForTest(), 2, true);
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
	}
	
	private void advanceToActive(){
		// given
		assertEquals(States.IDLE, messageService.state());
		messageService.onStart();
		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
		ServiceStatus serviceStatus = new ServiceStatus(adminSinkId, 
				  1,
				  adminSinkId,
				  ServiceType.AdminService,
				  ServiceStatusType.UP,
				  System.nanoTime());
		serviceStatus.encode(ownSinkId, helper.codecForTest(), helper.frameForTest());
		messageService.onEvent(helper.frameForTest(), 1, true);
		assertEquals(States.ACTIVE, messageService.state());		
		verify(admin, times(2)).tell(any(ServiceStatus.class)); // one for init, one for ready
		assertEquals(1, helper.timerService().numOutstanding()); // one outstanding task
	}
	
	@Test
	public void givenActiveWhenTimeAdvanceThenTriggersTimerEventToBeSentToItself(){
		// given
		advanceToActive();
		
		// when
		assertTrue(helper.timerService().advance(statGatheringFreqNs, TimeUnit.NANOSECONDS));
		assertEquals(1, helper.timerService().numOutstanding()); // one expired then immediately insert back a new task,
																 // hence one outstanding task

		// pass some time but not enough to expire any task
		long timePassedNs = TimeUnit.SECONDS.toNanos(4l);
		assertFalse(helper.timerService().advance(timePassedNs, TimeUnit.NANOSECONDS));

		// advance again to expire a task
		assertTrue(helper.timerService().advance(statGatheringFreqNs - timePassedNs, TimeUnit.NANOSECONDS));
		verify(self, times(2)).publish(any(), any()); // publish timer event to self
		verify(admin, times(0)).publish(any(), any()); // no message will be published to admin
		assertEquals(1, helper.timerService().numOutstanding()); // one outstanding task
	}
	
	@Test
	public void givenWaitingForServicesWhenReceivedCommandThenDoNothing(){
		// given, when
		messageService.onStart();

		// when
		MessageCodec codecForTest = helper.codecForTest();
		Frame frameForTest = helper.frameForTest();
		codecForTest.encoder().encodeCommand(frameForTest, ownSinkId, ownSinkId, helper.messagingContext().getAndIncMsgSeq(), -1, CommandType.GATHER_PERF_STAT, BooleanType.FALSE);
		messageService.onEvent(frameForTest, selfRingBufferSeq.getAndIncrement(), true);

		assertEquals(States.WAITING_FOR_SERVICES, messageService.state());
	}
	
	@Ignore
	@Test
	public void givenActiveWhenReceiveStatGatheringCommandThenSendOutPing() throws Throwable{
		// given
		advanceToActive();

		// when
		MessageCodec codecForTest = helper.codecForTest();
		Frame frameForTest = helper.frameForTest();
		codecForTest.encoder().encodeCommand(frameForTest, ownSinkId, ownSinkId, helper.messagingContext().getAndIncMsgSeq(), -1, CommandType.GATHER_PERF_STAT, BooleanType.FALSE);
		messageService.onEvent(frameForTest, selfRingBufferSeq.getAndIncrement(), true);
		
		// then
		ArgumentCaptor<Frame> argumentCaptor = ArgumentCaptor.forClass(Frame.class);
		verify(anyServiceSink, times(1)).publish(argumentCaptor.capture(), any()); // send command out
		Message message = codecForTest.decoder().decodeAsMessage(argumentCaptor.getValue());
		Command command = (Command)message;
		assertEquals(CommandType.GATHER_PERF_STAT, command.commandType());
	}
	
	@Test
	public void givenActiveWhenReceiveLatencyDataThenAcceptIt() throws Throwable{
		// given
		advanceToActive();

		long startTimeNs = System.nanoTime();
		long hopLatencyNs = 5_00l;
		// when receive a ping from another service back to this sink
		MessageCodec codecForTest = helper.codecForTest();
		Frame frameForTest = helper.frameForTest();
		codecForTest.encoder().encodePing(frameForTest, ownSinkId, anyServiceSinkId, helper.messagingContext().getAndIncMsgSeq(), BooleanType.FALSE, ownSinkId, startTimeNs);
		codecForTest.encoder().encodePingAppendingTimestamp(frameForTest, anyServiceSinkId, ownSinkId, BooleanType.TRUE, 1, anyServiceSinkId, startTimeNs + hopLatencyNs);
		messageService.onEvent(frameForTest, selfRingBufferSeq.getAndIncrement(), true);
		
		// verify
		perfService = (PerformanceService)messageService.service();
		Int2ObjectMap<PerfData> perfDataBySinkId = perfService.perfDataManager().perfDataBySinkId();
		assertEquals(1, perfDataBySinkId.size());
	}
	
	@Test
	public void givenActiveAndNoStatHasBeenGatheredWhenReceiveRequestToGetStatThenReturnNothing() throws Exception{
		// given
		advanceToActive();

		// send a request
		MessageCodec codecForTest = helper.codecForTest();
		Frame frameForTest = helper.frameForTest();
		codecForTest.encoder().encodeRequest(frameForTest, dashboardSinkId, ownSinkId, helper.messagingContext().getAndIncMsgSeq(), RequestType.GET_PERF_STAT);
		messageService.onEvent(frameForTest, selfRingBufferSeq.getAndIncrement(), true);
		
		// then
		ArgumentCaptor<Frame> argumentCaptor = ArgumentCaptor.forClass(Frame.class);
		verify(dashboardSink, times(1)).publish(argumentCaptor.capture(), any()); // send response out, send perf data out
 	}

	@Test
	public void givenActiveAndStatHasBeenGatheredWhenReceiveRequestToGetStatThenReturnStat() throws Exception{
		// given
		advanceToActive();

		long startTimeNs = helper.timerService().nanoTime();
		MessageCodec codecForTest = helper.codecForTest();
		Frame frameForTest = helper.frameForTest();
		codecForTest.encodePing(frameForTest, ownSinkId, anyServiceSinkId, helper.messagingContext().getAndIncMsgSeq(), BooleanType.FALSE, ownSinkId, startTimeNs);

		long hopLatencyNs = 5_00l;
		helper.timerService().advance(hopLatencyNs, TimeUnit.NANOSECONDS);
		codecForTest.encodePingAppendingTimestamp(frameForTest, anyServiceSinkId, ownSinkId, BooleanType.TRUE, 0, anyServiceSinkId, helper.timerService().nanoTime());
		
		helper.timerService().advance(hopLatencyNs, TimeUnit.NANOSECONDS);		
		messageService.onEvent(frameForTest, selfRingBufferSeq.getAndIncrement(), true);

		// when send a request
		codecForTest.encodeRequest(frameForTest, dashboardSinkId, ownSinkId, helper.messagingContext().getAndIncMsgSeq(), RequestType.GET_PERF_STAT, BooleanType.FALSE, Parameter.NULL_LIST);
		messageService.onEvent(frameForTest, selfRingBufferSeq.getAndIncrement(), true);
		
		// then
		// too bad that we are mocking MessageSink interface here, since
		// MessageSink.publish takes Frame as a parameter, Mockito cannot differentiate
		// between different Frame for PerfData and Frame for Response
		ArgumentCaptor<Frame> argumentCaptor = ArgumentCaptor.forClass(Frame.class);
		verify(dashboardSink, times(2)).publish(argumentCaptor.capture(), any()); // perf service sends response ok and perf data back to dashboard
		Message perfDataMessage = codecForTest.decodeAsMessage(argumentCaptor.getAllValues().get(0));
		PerfDataMessage perfData = (PerfDataMessage)perfDataMessage;
		
		assertEquals(anyServiceSinkId, perfData.sinkId());
		assertEquals(hopLatencyNs * 2, perfData.roundTripNs()); // expected latency is two times of hop latency
 	}

	public void testSubscription(){
		
	}
	
	public void testUnsubscription(){
		
	}
	*/
}
