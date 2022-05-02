package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import com.lmax.disruptor.EventPoller.Handler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.ReferenceDataServiceConfig;
import com.lunar.config.ReferenceDataServiceConfig.EntityTypeSetting;
import com.lunar.config.ServiceConfig;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Parameter;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class DummyReferenceDataServiceTest {
	private static final Logger LOG = LogManager.getLogger(DummyReferenceDataServiceTest.class);
	@Mock
	private ServiceStatusSender serviceStatusSender;

	@Mock
	private MessageSink omesSink;

	@Mock
	private MessageSink mdsSink;
	
	@Mock
	private MessageSink adminSink;

	@Mock
	private MessageSink selfSink;
	
	private final int adminSinkId = 1;
	private final int rdsSinkId = 2;
	private final int mdsSinkId = 3;
	private final int omesSinkId = 4;

	private ReferenceDataServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef omes;
	private MessageSinkRef mds;
	private MessageSinkRef admin;
	
	@Before
	public void setup(){
		EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings = new EnumMap<TemplateType, EntityTypeSetting>(TemplateType.class);
		serviceConfig = new ReferenceDataServiceConfig(1,
				"test", 
				"testRDS", 
				"test rds service",
				ServiceType.RefDataService,
				Optional.of("com.lunar.service.DummyReferenceDataService"),
				rdsSinkId,
				Optional.of(1024),
				Optional.of(1024),
				Optional.empty(),
				Optional.empty(),
				true,
				false,
				Duration.ofSeconds(1),
				false,
				"",
				entityTypeSettings);

		helper = TestHelper.of();
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
		mdsSink = TestHelper.mock(mdsSink, mdsSinkId, ServiceType.MarketDataService);
		omesSink = TestHelper.mock(omesSink, omesSinkId, ServiceType.OrderManagementAndExecutionService);		
		admin = MessageSinkRef.of(adminSink, "test-admin");
		mds = MessageSinkRef.of(mdsSink, "test-mds");
		omes = MessageSinkRef.of(omesSink, "test-omes");
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
				return DummyReferenceDataService.of(serviceConfig, messageService);
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
	public void givenIdleWhenReceiveAdminStatusThenMoveToActiveState(){
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(omes);
		assertEquals(States.IDLE, messageService.state());
		
		// when, verify
		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToActiveState(wrapper, selfMessenger);
	}
	
	@Test
	public void testLoadedSecuritiesWhenStateIsActive(){
		// given
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(omes);
		assertEquals(States.IDLE, messageService.state());
		
		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToActiveState(wrapper, selfMessenger);
		
		DummyReferenceDataService coreService = (DummyReferenceDataService)wrapper.coreService();
		LongEntityManager<Security> securities = coreService.securities();
		assertEquals(2, securities.entities().stream().count());
		assertTrue(securities.entities().stream().anyMatch(s -> { 
			return (s.securityType() == SecurityType.STOCK && (s.code().compareTo("700") == 0) && s.sid() == DummyReferenceDataService.BEGINNING_SID);
		}));
		assertTrue(securities.entities().stream().anyMatch(s -> { 
			return (s.securityType() == SecurityType.WARRANT && (s.code().compareTo("62345") == 0) && s.sid() == DummyReferenceDataService.BEGINNING_SID + 1);
		}));
		Security security = coreService.securities().get(DummyReferenceDataService.BEGINNING_SID);
		assertEquals(DummyReferenceDataService.BEGINNING_SID, security.sid());
	}
	
	@Test
	public void givenActiveWhenReceiveGetRequestThenResponseWithAppropriateEntity(){
		// create a service
		ServiceTestHelper testHelper = ServiceTestHelper.of();
		LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
		LunarService messageService = wrapper.messageService();
		messageService.messenger().referenceManager().register(admin);
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(omes);
		assertEquals(States.IDLE, messageService.state());
		
		// when, verify
		final int selfSinkId = 8;
		RingBufferMessageSinkPoller selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId, ServiceType.DashboardService, 256, "testDashboard");
		RingBufferMessageSink selfRingBufferSink = selfRingBufferSinkPoller.sink();
		messageService.messenger().referenceManager().register(MessageSinkRef.of(selfRingBufferSink));
		Messenger selfMessenger = testHelper.createMessenger(selfRingBufferSink, "self");
		advanceToActiveState(wrapper, selfMessenger);
		
		// send a request
		selfMessenger.sendRequest(messageService.messenger().self(), 
				RequestType.GET, 
				new ImmutableList.Builder<Parameter>().add(
						Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()),
						Parameter.of(ParameterType.SECURITY_SID, DummyReferenceDataService.BEGINNING_SID)).build(),
				null);
		wrapper.pushNextMessage();
		
		// check message received
		selfMessenger.receiver().responseHandlerList().add(new com.lunar.message.binary.Handler<ResponseSbeDecoder>() {
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder codec) {
				LOG.info("Received response");
				assertEquals(ResultType.OK, codec.resultType());
			}
		});
		
		selfMessenger.receiver().securityHandlerList().add(new com.lunar.message.binary.Handler<SecuritySbeDecoder>() {
			
			private void handle(SecuritySbeDecoder codec){
				assertEquals(DummyReferenceDataService.BEGINNING_SID, codec.sid());
				assertEquals(SecurityType.STOCK, codec.securityType());
				assertEquals(1, codec.exchangeSid());
				assertEquals(omesSinkId, codec.omesSinkId());
				assertEquals(mdsSinkId, codec.mdsSinkId());	
				
				byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
				codec.getCode(bytes, 0);
				String code;
				try {
					code = new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim();
					assertEquals(0, code.compareTo("700"));
				} 
				catch (UnsupportedEncodingException e) {
					throw new RuntimeException();
				}
			}
			
			@Override
			public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder codec) {
				LOG.info("Received security");
				handle(codec);
			}
			
			@Override
			public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, SecuritySbeDecoder codec) {
				handle(codec);
			}
		});
		
		assertTrue(selfRingBufferSinkPoller.poll(new Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				// decode the message
				LOG.info("Received message in self's sink");
				selfMessenger.receive(event, 0);
				return false;
			}
		}));
	}
	
	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();
		messageService.messenger().referenceManager().register(admin);

		messageService.onStart();
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

		// verify
		assertEquals(States.ACTIVE, messageService.state());
	}
}
