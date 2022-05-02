package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.ServiceConfig;
import com.lunar.config.StrategyServiceConfig;
import com.lunar.core.SbeEncodable;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.entity.Issuer;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder.FieldsDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.strategy.GenericStrategySchema;
import com.lunar.strategy.StrategyBooleanSwitch;
import com.lunar.strategy.StrategySwitch;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.util.AssertUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

@RunWith(MockitoJUnitRunner.class)
public class StrategyServiceTest {
	@Mock
	private ServiceStatusSender serviceStatusSender;
	
	@Mock
	private MessageSink adminSink;
    @Mock
    private MessageSink mdsssSink;
    @Mock
    private MessageSink mdsSink;    
    @Mock
    private MessageSink refSink;
    @Mock
    private MessageSink omesSink;
    @Mock
    private MessageSink persiSink;
    @Mock
    private MessageSink prcSink;
    @Mock
    private MessageSink wmSink;
    @Mock
    private MessageSink otsSink;
    @Mock
    private MessageSink posSink;

	private StrategyServiceConfig serviceConfig;
	private TestHelper helper;
	private MessageSinkRef admin;
	private MessageSinkRef mds;
	private MessageSinkRef mdsss;
	private MessageSinkRef ref;
	private MessageSinkRef omes;
	private MessageSinkRef persi;
	private MessageSinkRef prc;
	private MessageSinkRef wm;
    private MessageSinkRef ots;
    private MessageSinkRef pos;
	
	private final int adminSinkId = 1;
	private final int mdsSinkId = 2;
	private final int mdsssSinkId = 3;
	private final int refSinkId = 4;
	private final int stratSinkId = 5;
	private final int omesSinkId = 6;
	private final int persiSinkId = 7;
	private final int prcSinkId = 8;
	private final int wmSinkId = 9;
    private final int otsSinkId = 10;
    private final int posSinkId = 11;
	
	@Mock
	private MessageSink selfSink;
	
	@Before
	public void setup(){
		serviceConfig = new StrategyServiceConfig(1,
				"test", 
				"testSTRAT", 
				"test strat service",
				ServiceType.StrategyService,
				Optional.of("com.lunar.service.StrategyService"), 
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
				5,
				10,
				5,
				"",
				"",
				false,
				0,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		helper = TestHelper.of();
		adminSink = TestHelper.mock(adminSink, adminSinkId, ServiceType.AdminService);
  		refSink = TestHelper.mock(refSink, refSinkId, ServiceType.RefDataService);
        mdsssSink = TestHelper.mock(mdsssSink, mdsssSinkId, ServiceType.MarketDataSnapshotService);
        mdsSink = TestHelper.mock(mdsSink, mdsSinkId, ServiceType.MarketDataService);
        omesSink = TestHelper.mock(omesSink, omesSinkId, ServiceType.OrderManagementAndExecutionService);
        persiSink = TestHelper.mock(persiSink, persiSinkId, ServiceType.PersistService);
        prcSink = TestHelper.mock(prcSink, prcSinkId, ServiceType.PricingService);
        wmSink = TestHelper.mock(wmSink, wmSinkId, ServiceType.WarmupService);
        otsSink = TestHelper.mock(otsSink, otsSinkId, ServiceType.OrderAndTradeSnapshotService);
        posSink = TestHelper.mock(posSink, posSinkId, ServiceType.PortfolioAndRiskService);

		admin = MessageSinkRef.of(adminSink, "test-admin");
		mdsss = MessageSinkRef.of(mdsssSink, "test-mdsss");
		mds = MessageSinkRef.of(mdsSink, "test-mds");
		ref = MessageSinkRef.of(refSink, "test-ref");
		omes = MessageSinkRef.of(omesSink, "test-omes");
		persi = MessageSinkRef.of(persiSink, "test-persi");
		prc = MessageSinkRef.of(prcSink, "test-price");
		wm = MessageSinkRef.of(wmSink, "test-wm");
        ots = MessageSinkRef.of(otsSink, "test-ots");
        pos = MessageSinkRef.of(posSink, "test-pos");
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
				return StrategyService.of(serviceConfig, messageService);
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
		messageService.messenger().referenceManager().register(mds);
		messageService.messenger().referenceManager().register(ref);
		messageService.messenger().referenceManager().register(omes);
		messageService.messenger().referenceManager().register(persi);
		messageService.messenger().referenceManager().register(prc);
		messageService.messenger().referenceManager().register(wm);
        messageService.messenger().referenceManager().register(ots);
        messageService.messenger().referenceManager().register(pos);
		
		// move to active state
		Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
		advanceToReadyState(wrapper, selfMessenger);
		
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

	private void advanceToReadyState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
		LunarService messageService = wrapper.messageService();
		MessageSinkRef serviceSinkRef = messageService.messenger().self();

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

        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
                ServiceStatus.of(selfSink.sinkId(),
                        mds.sinkId(), 
                        ServiceType.MarketDataService, 
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
                        persi.sinkId(), 
                        ServiceType.PersistService, 
                        ServiceStatusType.UP, 
                        System.nanoTime()));
        wrapper.pushNextMessage();

        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
                ServiceStatus.of(selfSink.sinkId(),
                        prc.sinkId(), 
                        ServiceType.PricingService, 
                        ServiceStatusType.UP, 
                        System.nanoTime()));
        wrapper.pushNextMessage();
        
        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef, 
                ServiceStatus.of(selfSink.sinkId(),
                        wm.sinkId(), 
                        ServiceType.WarmupService, 
                        ServiceStatusType.UP, 
                        System.nanoTime()));
        wrapper.pushNextMessage();

        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef,
                ServiceStatus.of(selfSink.sinkId(),
                        ots.sinkId(),
                        ServiceType.OrderAndTradeSnapshotService,
                        ServiceStatusType.UP,
                        System.nanoTime()));
        wrapper.pushNextMessage();
        
        selfMessenger.serviceStatusSender().sendServiceStatus(serviceSinkRef,
                ServiceStatus.of(selfSink.sinkId(),
                        pos.sinkId(),
                        ServiceType.PortfolioAndRiskService,
                        ServiceStatusType.UP,
                        System.nanoTime()));
        wrapper.pushNextMessage();        

		// verify
		assertEquals(States.READY, messageService.state());
	}
	
    @Test
    public void awesomeTest() throws InterruptedException{
        // create a service
        ServiceTestHelper testHelper = ServiceTestHelper.of();
        LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
        LunarService messageService = wrapper.messageService();
        
        Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
        
        RingBufferMessageSinkPoller refRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(refSinkId, ServiceType.RefDataService, 256, "testRefData");
        RingBufferMessageSink refRingBufferSink = refRingBufferSinkPoller.sink();
        Messenger refMessenger = testHelper.createMessenger(refRingBufferSink, "self");
        
        RingBufferMessageSinkPoller mdsRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(mdsSinkId, ServiceType.MarketDataService, 256, "testMds");
        RingBufferMessageSink mdsRingBufferSink = mdsRingBufferSinkPoller.sink();
        Messenger mdsMessenger = testHelper.createMessenger(mdsRingBufferSink, "self");

        RingBufferMessageSinkPoller posRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(posSinkId, ServiceType.PortfolioAndRiskService, 256, "testPos");
        RingBufferMessageSink posRingBufferSink = posRingBufferSinkPoller.sink();
        Messenger posMessenger = testHelper.createMessenger(posRingBufferSink, "self");
        
        RingBufferMessageSinkPoller persiRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(persiSinkId, ServiceType.PersistService, 256, "testPersist");
        RingBufferMessageSink persiRingBufferSink = persiRingBufferSinkPoller.sink();
        Messenger persiMessenger = testHelper.createMessenger(persiRingBufferSink, "self");
        
        messageService.messenger().referenceManager().register(MessageSinkRef.of(refRingBufferSink));
        messageService.messenger().referenceManager().register(admin);
        messageService.messenger().referenceManager().register(mdsss);
        messageService.messenger().referenceManager().register(MessageSinkRef.of(mdsRingBufferSink));
        messageService.messenger().referenceManager().register(omes);
        messageService.messenger().referenceManager().register(prc);
        messageService.messenger().referenceManager().register(wm);
        messageService.messenger().referenceManager().register(ots);
        messageService.messenger().referenceManager().register(MessageSinkRef.of(posRingBufferSink));
        messageService.messenger().referenceManager().register(MessageSinkRef.of(persiRingBufferSink));

        advanceToReadyState(wrapper, selfMessenger);        
        wrapper.pushNextMessage();
        final StrategyService strategyService = (StrategyService)wrapper.coreService();

        final Issuer issuer = Issuer.of(1, "HS", "HS");    
        final Security warrant = Security.of(12345L, SecurityType.WARRANT, "25689", 0, 5L, Optional.of(LocalDate.of(2016, 3, 31)), ServiceConstant.NULL_LISTED_DATE, PutOrCall.CALL, OptionStyle.ASIAN, 123456, 654321, 1, 1000, true, SpreadTableBuilder.get(SecurityType.WARRANT));
        final Security underlying = Security.of(5L, SecurityType.STOCK, "5", 0, 0L, Optional.empty(), ServiceConstant.NULL_LISTED_DATE, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, 1, 1000, true, SpreadTableBuilder.get(SecurityType.STOCK));
        
        final AtomicInteger requestCount = new AtomicInteger(0);
        final AtomicBoolean isHandled = new AtomicBoolean(false);
        refMessenger.receiver().requestHandlerList().add(new Handler<RequestSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
                int counter = requestCount.getAndAdd(1);
                if (counter == 0) {
                    refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 1, ResultType.OK, issuer);
                    wrapper.pushNextMessage();          
                }
                else if (counter == 1) {
                    warrant.omesSink(omes).mdsSink(mds).mdsssSink(mdsss);
                    underlying.omesSink(omes).mdsSink(mds).mdsssSink(mdsss);
                    
                    refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.FALSE, 2, ResultType.OK, underlying);
                    wrapper.pushNextMessage();
    
                    refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, warrant);
                    wrapper.pushNextMessage();
                }
                isHandled.set(true);
            }            
        });
        refRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
            @Override
            public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
                refMessenger.receive(event, 0);
                return false;
            }
        });
        // issuer
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());   
        assertEquals(1, strategyService.issuers().entities().size());
        assertEquals(issuer.code(), strategyService.issuers().get(issuer.sid()).code());
        isHandled.set(false);

        // security
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get()); 
        isHandled.set(false);        
        
        posMessenger.receiver().requestHandlerList().add(new Handler<RequestSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
                posMessenger.responseSender().sendResponseForRequest(wrapper.messenger().self(), codec, ResultType.OK);
                wrapper.pushNextMessage();
                isHandled.set(true);
            }            
        });
        posRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
            @Override
            public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
                posMessenger.receive(event, 0);
                return false;
            }
        });        
        // position
        posRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        isHandled.set(false);        
        
        final MarketStatusType marketStatus = MarketStatusType.CT;
        mdsMessenger.receiver().requestHandlerList().add(new Handler<RequestSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
            	mdsMessenger.responseSender().sendResponseForRequest(wrapper.messenger().self(), codec, ResultType.OK);
                wrapper.pushNextMessage();
            	mdsMessenger.marketStatusSender().sendMarketStatus(wrapper.messenger().self(), 0, marketStatus);
                wrapper.pushNextMessage();
                isHandled.set(true);
            }            
        });
        mdsRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
            @Override
            public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
                mdsMessenger.receive(event, 0);
                return false;
            }
        });
        
        // market status
        mdsRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        assertEquals(marketStatus, strategyService.marketStatus());
        isHandled.set(false);
        
    	final StrategyType strategyType = StrategyType.of(123, "SpeedArb1");
        final GenericStrategyTypeParams params = new GenericStrategyTypeParams();
        params.strategyId(strategyType.sid());
        params.defaultUndInputParams().sizeThreshold(600);
        params.defaultUndInputParams().velocityThreshold(500);
        params.defaultWrtInputParams().mmAskSize(7500);
        params.defaultWrtInputParams().mmBidSize(8500);
        final GenericUndParams undParams = new GenericUndParams();
        undParams.strategyId(strategyType.sid());
        undParams.underlyingSid(underlying.sid());
        undParams.sizeThreshold(30000);
        undParams.velocityThreshold(600000);
        final GenericWrtParams wrtParams = new GenericWrtParams();
        wrtParams.strategyId(strategyType.sid());
        wrtParams.secSid(warrant.sid());
        wrtParams.mmAskSize(2000);
        wrtParams.mmBidSize(2500);
        final GenericIssuerParams issuerParams = new GenericIssuerParams();
        issuerParams.strategyId(strategyType.sid());
        issuerParams.issuerSid(warrant.issuerSid());
        issuerParams.allowStopLossOnFlashingBid(true);
        
        final StrategySwitch strategySwitch = new StrategyBooleanSwitch(StrategySwitchType.SCOREBOARD_PERSIST, StrategyParamSource.STRATEGY_ID, strategyType.sid(), BooleanType.FALSE);
        final StrategySwitch underlyingSwitch = new StrategyBooleanSwitch(StrategySwitchType.SCOREBOARD_PERSIST, StrategyParamSource.UNDERLYING_SECURITY_SID, underlying.sid(), BooleanType.FALSE);
        final StrategySwitch warrantSwitch = new StrategyBooleanSwitch(StrategySwitchType.SCOREBOARD_PERSIST, StrategyParamSource.SECURITY_SID, warrant.sid(), BooleanType.FALSE);
        final StrategySwitch issuerSwitch = new StrategyBooleanSwitch(StrategySwitchType.SCOREBOARD_PERSIST, StrategyParamSource.ISSUER_SID, issuer.sid(), BooleanType.FALSE);

        persiMessenger.receiver().requestHandlerList().add(new com.lunar.message.binary.Handler<RequestSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, strategyType);
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, params);
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, (SbeEncodable)params.defaultUndInputParams());
                wrapper.pushNextMessage();
                
                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, (SbeEncodable)params.defaultWrtInputParams());
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, (SbeEncodable)params.defaultIssuerInputParams());
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, undParams);
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, wrtParams);
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, issuerParams);
                wrapper.pushNextMessage();

                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, strategySwitch);
                wrapper.pushNextMessage();
                
                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, underlyingSwitch);
                wrapper.pushNextMessage();        
                
                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, warrantSwitch);
                wrapper.pushNextMessage();
                
                refMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, issuerSwitch);
                wrapper.pushNextMessage();     
                isHandled.set(true);
            }	
        });
        persiRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
            @Override
            public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
            	persiMessenger.receive(event, 0);
                return false;
            }
        });
        
        // strategy
        persiRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        assertEquals(marketStatus, strategyService.marketStatus());
        isHandled.set(false);
        
		assertEquals(States.ACTIVE, messageService.state());

        
        refMessenger.sendRequest(wrapper.messenger().self(),
        		RequestType.GET,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATEGYTYPE.value())).build(),
                null);
        wrapper.pushNextMessage();
        refMessenger.receiver().strategyTypeHandlerList().add(new Handler<StrategyTypeSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyTypeSbeDecoder codec) {
                byte[] bytes = new byte[StrategyTypeSbeDecoder.nameLength()];
                codec.getName(bytes, 0);

                try {
                    assertEquals(strategyType.sid(), codec.strategyId());
                    assertEquals(strategyType.name(), new String(bytes, StrategyTypeSbeDecoder.nameCharacterEncoding()).trim());
                } catch (final UnsupportedEncodingException e) {
                    assertTrue(false);
                }
                
                final Long2ObjectOpenHashMap<String> parameters = new Long2ObjectOpenHashMap<String>(10);                
                FieldsDecoder decoder = codec.fields();
                while (decoder.hasNext()) {
                    decoder = decoder.next();
                    parameters.put(decoder.parameterId(), String.valueOf(decoder.parameterId()));
                }
                assertEquals(72, parameters.size());
                isHandled.set(true);
            }           
        });        
        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        // ignore request response
        refRingBufferSinkPoller.poll();
        
        refMessenger.sendRequest(wrapper.messenger().self(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATPARAMUPDATE.value()))
                    .add(Parameter.of(ParameterType.STRATEGY_ID, strategyType.sid()))
                    .build(),
                null);
        wrapper.pushNextMessage();
        refMessenger.receiver().strategyParamsHandlerList().add(new Handler<StrategyParamsSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder codec) {
                assertEquals(strategyType.sid(), codec.strategyId());
                final Long2LongOpenHashMap parameters = new Long2LongOpenHashMap(10);
                com.lunar.message.io.sbe.StrategyParamsSbeDecoder.ParametersDecoder decoder = codec.parameters();
                while (decoder.hasNext()) {
                    decoder = decoder.next();
                    parameters.put(decoder.parameterId(), decoder.parameterValueLong());                    
                }
                assertEquals(1, parameters.size());
                isHandled.set(true);
            }            
        });
        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        // ignore switch response
        refRingBufferSinkPoller.poll();
        // ignore the request response
        refRingBufferSinkPoller.poll();
        
        refMessenger.sendRequest(wrapper.messenger().self(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATUNDPARAMUPDATE.value()))
                    .add(Parameter.of(ParameterType.UNDERLYING_SECURITY_SID, underlying.sid()))
                    .build(),
                null);
        wrapper.pushNextMessage();
        refMessenger.receiver().strategyUndParamsHandlerList().add(new Handler<StrategyUndParamsSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder codec) {
                assertEquals(strategyType.sid(), codec.strategyId());
                assertEquals(underlying.sid(), codec.undSid());
                final Long2LongOpenHashMap parameters = new Long2LongOpenHashMap(10);
                com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder.ParametersDecoder decoder = codec.parameters();
                while (decoder.hasNext()) {
                    decoder = decoder.next();
                    parameters.put(decoder.parameterId(), decoder.parameterValueLong());                    
                }
                assertEquals(3, parameters.size());
                isHandled.set(true);
            }            
        });
        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        // ignore switch response
        refRingBufferSinkPoller.poll();        
        // ignore the request response
        refRingBufferSinkPoller.poll();

        refMessenger.sendRequest(wrapper.messenger().self(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATWRTPARAMUPDATE.value()))
                    .add(Parameter.of(ParameterType.SECURITY_SID, warrant.sid()))
                    .build(),
                null);
        wrapper.pushNextMessage();
        final AtomicLong mmBidSize = new AtomicLong(0);
        refMessenger.receiver().strategyWrtParamsHandlerList().add(new Handler<StrategyWrtParamsSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder codec) {
                assertEquals(strategyType.sid(), codec.strategyId());
                assertEquals(warrant.sid(), codec.secSid());
                final Long2LongOpenHashMap parameters = new Long2LongOpenHashMap(10);
                com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder.ParametersDecoder decoder = codec.parameters();
                while (decoder.hasNext()) {
                    decoder = decoder.next();
                    parameters.put(decoder.parameterId(), decoder.parameterValueLong());  
                    if (decoder.parameterId() == GenericStrategySchema.MM_BID_SIZE_ID) {
                        if (mmBidSize.get() == 0) {
                            assertEquals(wrtParams.mmBidSize(), (int)decoder.parameterValueLong());
                        }
                        else {
                            assertEquals(mmBidSize.get(), decoder.parameterValueLong());
                        }
                    }
                }
                assertEquals(60, parameters.size());
                isHandled.set(true);
            }
        });
        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        
        refMessenger.receiver().strategySwitchHandlerList().add(new Handler<StrategySwitchSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder codec) {
                assertEquals(StrategyParamSource.SECURITY_SID, codec.switchSource());
                assertEquals(warrant.sid(), codec.sourceSid());
                if (codec.switchType() == StrategySwitchType.STRATEGY_DAY_ONLY) {
                    assertEquals(BooleanType.TRUE, codec.onOff());                    
                }
                else {
                    assertEquals(BooleanType.FALSE, codec.onOff());
                }
                isHandled.set(true);
            }
            
        });
        
        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());

        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());

        // ignore the request response
        refRingBufferSinkPoller.poll();

        mmBidSize.set(67890);
        refMessenger.sendRequest(wrapper.messenger().self(),
                RequestType.UPDATE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.STRATWRTPARAMUPDATE.value()))
                    .add(Parameter.of(ParameterType.SECURITY_SID, warrant.sid()))
                    .add(Parameter.of(ParameterType.STRATEGY_ID, strategyType.sid()))
                    .add(Parameter.of(ParameterType.PARAM_ID, GenericStrategySchema.MM_BID_SIZE_ID))
                    .add(Parameter.of(ParameterType.PARAM_VALUE, mmBidSize.get()))
                    .build(),
                null);
        wrapper.pushNextMessage();

        isHandled.set(false);
        refRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        
        // ignore the request response
        refRingBufferSinkPoller.poll();
        
    }
	
	
}
