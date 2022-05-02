package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.lunar.entity.Issuer;
import com.lunar.entity.Note;
import com.lunar.entity.Security;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestSbeDecoder;
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
public class ReferenceDataServiceTest {
    private static final Logger LOG = LogManager.getLogger(ReferenceDataServiceTest.class);
    @Mock
    private ServiceStatusSender serviceStatusSender;

    @Mock
    private MessageSink adminSink;
    @Mock
    private MessageSink persiSink;
    @Mock
    private MessageSink selfSink;

    private final int adminSinkId = 1;
    private final int rdsSinkId = 2;
    private final int persiSinkId = 3;
    
    private ReferenceDataServiceConfig serviceConfig;
    private TestHelper helper;
    private MessageSinkRef admin;
    private MessageSinkRef persi;

    @Before
    public void setup(){
        EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings = new EnumMap<TemplateType, EntityTypeSetting>(TemplateType.class);

        serviceConfig = new ReferenceDataServiceConfig(1, 
        		"test", 
                "testRDS", 
                "test rds service",
                ServiceType.RefDataService,
                Optional.of("com.lunar.service.ReferenceDataService"),
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
        persiSink = TestHelper.mock(persiSink, persiSinkId, ServiceType.PersistService);
        admin = MessageSinkRef.of(adminSink, "test-admin");
        persi = MessageSinkRef.of(persiSink, "test-persi");
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
                return ReferenceDataService.of(serviceConfig, messageService);
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
        messageService.messenger().referenceManager().register(persi);
        assertEquals(States.IDLE, messageService.state());

        // when, verify
        Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
        advanceToReadyState(wrapper, selfMessenger);
    }

    @Test
    public void testLoadedSecuritiesWhenStateIsActive(){
        // given
        ServiceTestHelper testHelper = ServiceTestHelper.of();
        LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
        LunarService messageService = wrapper.messageService();
        messageService.messenger().referenceManager().register(admin);
        messageService.messenger().referenceManager().register(persi);

        assertEquals(States.IDLE, messageService.state());

        Messenger selfMessenger = testHelper.createMessenger(selfSink, "self");
        advanceToReadyState(wrapper, selfMessenger);

        //ReferenceDataService coreService = (ReferenceDataService)wrapper.coreService();
        //LongEntityManager<Security> securities = coreService.securities();		
        //assertTrue(securities.entities().stream().count() > 5000);
        //Security security = coreService.securities().get(ReferenceDataService.BEGINNING_SID);
        //assertEquals(ReferenceDataService.BEGINNING_SID, security.sid());
    }

    @Test
    public void givenActiveWhenReceiveGetRequestThenResponseWithAppropriateEntity(){
        // create a service
        ServiceTestHelper testHelper = ServiceTestHelper.of();
        LunarServiceTestWrapper wrapper = testHelper.createService(serviceConfig);
        LunarService messageService = wrapper.messageService();
        messageService.messenger().referenceManager().register(admin);
        
        RingBufferMessageSinkPoller persiRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(persiSinkId, ServiceType.PersistService, 256, "testPersistData");
        RingBufferMessageSink persiRingBufferSink = persiRingBufferSinkPoller.sink();
        Messenger persiMessenger = testHelper.createMessenger(persiRingBufferSink, "persi");
        messageService.messenger().referenceManager().register(MessageSinkRef.of(persiRingBufferSink));        

        // when, verify
        final int selfSinkId = 8;
        RingBufferMessageSinkPoller selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId, ServiceType.DashboardService, 256, "testDashboard");
        RingBufferMessageSink selfRingBufferSink = selfRingBufferSinkPoller.sink();
        messageService.messenger().referenceManager().register(MessageSinkRef.of(selfRingBufferSink));
        Messenger selfMessenger = testHelper.createMessenger(selfRingBufferSink, "self");

        assertEquals(States.IDLE, messageService.state());

        advanceToReadyState(wrapper, selfMessenger);
        wrapper.pushNextMessage();

        final ReferenceDataService refService = (ReferenceDataService)wrapper.coreService();

        final AtomicInteger requestCount = new AtomicInteger(0);
        final AtomicBoolean isHandled = new AtomicBoolean(false);
        persiMessenger.receiver().requestHandlerList().add(new com.lunar.message.binary.Handler<RequestSbeDecoder>() {
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
                int counter = requestCount.getAndAdd(1);
                if (counter == 0) {
                    final Issuer issuer = Issuer.of(1, "HS", "HS");    
                    persiMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 1, ResultType.OK, issuer);
                    wrapper.pushNextMessage();   
                }
                else if (counter == 1) {                
                    final Security warrant = Security.of(12345L, SecurityType.WARRANT, "25689", 0, 5L, Optional.of(LocalDate.of(2016, 3, 31)), ServiceConstant.NULL_LISTED_DATE, PutOrCall.CALL, OptionStyle.ASIAN, 123456, 654321, 1, 0, true, SpreadTableBuilder.get(SecurityType.WARRANT));
                    final Security underlying = Security.of(5L, SecurityType.STOCK, "5", 0, 0L, Optional.empty(), ServiceConstant.NULL_LISTED_DATE, PutOrCall.NULL_VAL, OptionStyle.NULL_VAL, 0, 0, 1, 0, true, SpreadTableBuilder.get(SecurityType.STOCK));
                    
                    persiMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.FALSE, 2, ResultType.OK, underlying);
                    wrapper.pushNextMessage();
    
                    persiMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 2, ResultType.OK, warrant);
                    wrapper.pushNextMessage();
                }
                else if (counter == 2){
                	Note note = Note.of(1, 12345L, 20170113, LocalTime.now().toNanoOfDay(), 
                			20170113, LocalTime.now().toNanoOfDay(), 
                			BooleanType.FALSE, 
                			BooleanType.FALSE,
                			"Test note");
                	
                    persiMessenger.responseSender().sendSbeEncodable(wrapper.messenger().self(), codec.clientKey(), BooleanType.TRUE, 3, ResultType.OK, note);
                    wrapper.pushNextMessage();
                }
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
        // get issuers 
        persiRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        assertEquals(1, refService.issuers().entities().size());
        isHandled.set(false);
        // get securities
        persiRingBufferSinkPoller.poll();
        assertTrue(isHandled.get()); 
        assertEquals(2, refService.securities().entities().size());
        // get note
        persiRingBufferSinkPoller.poll();
        assertTrue(isHandled.get());
        isHandled.set(false);
        assertEquals(1, refService.notes().entities().size());
        
        // send a request
        selfMessenger.sendRequest(messageService.messenger().self(), 
                RequestType.GET, 
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value())).add(Parameter.of(ParameterType.SECURITY_SID, 5)).build(),
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
            @Override
            public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder decoder,
                    SecuritySbeDecoder codec) {
                LOG.info("Received security");
                assertEquals(5, codec.sid());

                byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
                codec.getCode(bytes, 0);
                String code;
                try {
                    code = new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim();
                    assertEquals("5", code);
                } 
                catch (UnsupportedEncodingException e) {
                    throw new RuntimeException();
                }
            }

            @Override
            public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, SecuritySbeDecoder codec) {
                handle(buffer, offset, header, codec);
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

    private void advanceToReadyState(LunarServiceTestWrapper wrapper, Messenger selfMessenger){
        LunarService messageService = wrapper.messageService();
        MessageSinkRef serviceSinkRef = messageService.messenger().self();


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
                        persi.sinkId(), 
                        ServiceType.PersistService, 
                        ServiceStatusType.UP, 
                        System.nanoTime()));
        wrapper.pushNextMessage();
        
        // verify
        assertEquals(States.READY, messageService.state());
    }
}
