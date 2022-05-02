package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.config.DashboardServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.CommandTracker.CommandContext;
import com.lunar.core.RequestTracker;
import com.lunar.core.TriggerInfo;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.request.RequestContext;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.MutableHandler;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequestContext;
import com.lunar.util.AssertUtil;
import com.lunar.util.ConcurrentUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;

@RunWith(MockitoJUnitRunner.class)
public class DashboardServiceTest {
	private static final Logger LOG = LogManager.getLogger(DashboardServiceTest.class);

	@Mock
	private ServiceStatusSender serviceStatusSender;
	@Mock
	private MessageSink adminSink;
	@Mock
	private MessageSink mdSnapshotSink;
	@Mock
	private MessageSink otSnapshotSink;
	@Mock
	private MessageSink riskSink;
	@Mock
	private RequestTracker requestTracker;

	private MessageSinkRef admin;
	private MessageSinkRef risk;
	private MessageSinkRef mdSnapshot;
	private MessageSinkRef otSnapshot;

	private final int adminSinkId = 1;
	private final int riskSinkId = 2;
	private final int mdSnapshotSinkId = 3;
	private final int orderSnapshotSinkId = 4;

	private ServiceTestHelper testHelper;
	private LunarServiceTestWrapper wrapper;

	private DashboardServiceConfig config;
	private final int systemId = 1;
	private final int serviceSinkId = 5;
	private final int tcpListeningPort = 1235;
	private final String tcpListeningUrl = "127.0.0.1";	
	// Test sender
	private final int selfSinkId = 8;
	private RingBufferMessageSinkPoller selfRingBufferSinkPoller;
	private RingBufferMessageSink selfRingBufferSink;
	private Messenger selfMessenger;
	private MessageSinkRef selfSinkRef;
	private MutableHandler<ResponseSbeDecoder> mutableResponseHandler;
	
	@Before
	public void setup(){
		testHelper = ServiceTestHelper.of();
		selfRingBufferSinkPoller = testHelper.createRingBufferMessageSinkPoller(selfSinkId, ServiceType.DashboardService, 256, "testDashboard");
		selfRingBufferSinkPoller.handler(new com.lmax.disruptor.EventPoller.Handler<MutableDirectBuffer>() {
			@Override
			public boolean onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
				LOG.debug("Received message in self sink");
				selfMessenger.receive(event, 0);
				return false;
			}
		});
		selfRingBufferSink = selfRingBufferSinkPoller.sink();
		selfMessenger = testHelper.createMessenger(selfRingBufferSink, "self");
		selfMessenger.registerEvents();
		selfSinkRef = selfMessenger.self();
		
		config = new DashboardServiceConfig(systemId, 
				"test-db", 
				"test-db", 
				"test-db", 
				ServiceType.DashboardService, 
				Optional.empty(), 
				serviceSinkId, 
				Optional.of(1024), 
				Optional.of(1024), 
				Optional.empty(), 
				Optional.empty(),
				true, 
				false,
				Duration.ofSeconds(1),
				false,
				"",
				Optional.empty(),
				Optional.of(tcpListeningPort));
		
		adminSink = TestHelper.mock(adminSink, systemId, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");
		
		otSnapshotSink = TestHelper.mock(otSnapshotSink, systemId, orderSnapshotSinkId, ServiceType.OrderAndTradeSnapshotService);
		otSnapshot = MessageSinkRef.of(otSnapshotSink, "test-ot-snapshot");

		riskSink = TestHelper.mock(riskSink, systemId, riskSinkId, ServiceType.PortfolioAndRiskService);
		risk = MessageSinkRef.of(riskSink, "test-risk");

		mdSnapshotSink = TestHelper.mock(mdSnapshotSink, systemId, mdSnapshotSinkId, ServiceType.MarketDataSnapshotService);
		mdSnapshot = MessageSinkRef.of(mdSnapshotSink, "test-md-snapshot");

		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return DashboardService.of(serviceConfig, messageService);
			}
		};
		
		SenderBuilder senderBuilder = new TestSenderBuilder();
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(otSnapshot);
		wrapper.messenger().referenceManager().register(risk);
		wrapper.messenger().referenceManager().register(mdSnapshot);
		wrapper.messenger().referenceManager().register(selfSinkRef);

		mutableResponseHandler = new MutableHandler<>();
		when(requestTracker.responseHandler()).thenReturn(mutableResponseHandler);
	}
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	@Ignore
	@Test
	public void testStartStop() throws TimeoutException{
		TestHelper helper = TestHelper.of();

		MessageSinkRef self = DummyMessageSink.refOf(config.systemId(),
				config.sinkId(), 
				config.name(), 
				config.serviceType());
		
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
					return DashboardService.of(serviceConfig, messageService);
				}
			}, 
			config, 
			helper.messageFactory(), 
			helper.createMessenger(self, senderBuilder),
            helper.systemClock());
		disruptor.handleExceptionsWith(new ServiceFactory.GeneralDisruptorExceptionHandler(config.name()));
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
			return messageService.state() != States.ACTIVE;
		}, TimeUnit.SECONDS.toNanos(5L));
		context.shutdown();
		assertEquals(true, context.isStopped());
		assertTrue(executor.isShutdown());
	}
	
	@Ignore
	@Test
	public void givenWaitingWhenReceiveServiceStatusUpThenMoveToActiveState(){
		// Given
		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return DashboardService.of(serviceConfig, messageService);
			}
		};
		
		SenderBuilder senderBuilder = new TestSenderBuilder().requestTracker(requestTracker);
		when(requestTracker.sendAndTrack(any(), any())).thenReturn(new CompletableFuture<Request>());
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(risk);
		wrapper.messenger().referenceManager().register(otSnapshot);
		wrapper.messenger().referenceManager().register(mdSnapshot);

		wrapper.messageService().onStart();
		assertEquals(States.WAITING_FOR_SERVICES, wrapper.messageService().state());

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						admin.sinkId(), 
						admin.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						otSnapshot.sinkId(),
						otSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						risk.sinkId(),
						risk.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						mdSnapshot.sinkId(),
						mdSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		wrapper.pushNextMessage();
		
		// Verify
		assertEquals(States.ACTIVE, wrapper.messageService().state());
	}
	
	private void advanceToActiveState(LunarServiceTestWrapper wrapper, Messenger selfMessenger, ExecutorService serviceExecutor) {
		serviceExecutor.execute(() -> {wrapper.messageService().onStart();});
		AssertUtil.assertTrueWithinPeriod("Expect service in waiting for services state", 
				() -> { return wrapper.messageService().state() == States.WAITING_FOR_SERVICES; },
				TimeUnit.SECONDS.toNanos(5));
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						admin.sinkId(), 
						admin.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						otSnapshot.sinkId(),
						otSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		serviceExecutor.execute(() -> {wrapper.pushNextMessage();});

		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						risk.sinkId(),
						risk.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
		
		selfMessenger.serviceStatusSender().sendServiceStatus(wrapper.sink(), 
				ServiceStatus.of(selfSinkRef.systemId(),
						selfSinkRef.sinkId(),
						mdSnapshot.sinkId(),
						mdSnapshot.serviceType(),
						ServiceStatusType.UP, 
						System.nanoTime()));
		serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
		
		// verify
		// 1) state, 2) subscription request and position request are sent,
		// Check state
		AssertUtil.assertTrueWithinPeriod("Expect service in active state", 
				() -> { return wrapper.messageService().state() == States.ACTIVE; },
				TimeUnit.SECONDS.toNanos(5));

		DashboardService service = (DashboardService)wrapper.coreService();
		AssertUtil.assertTrueWithinPeriod("Expect listening to tcp connection", () -> {return service.listeningForClientConnection().get();});
	}
	
	@Test
	public void givenActiveWhenConnectToItThruTCPThenConnectionEstablished() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		// Given
		ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);
		
		// When
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Boolean> resultFuture = executor.submit(this::connectToAndSendMessageToDashboard);
		Boolean result = resultFuture.get(5, TimeUnit.SECONDS);
		assertTrue(result);
	}
	

	@Ignore
	@Test
	public void givenActiveWhenConnectAndSendMessageToDBThenReceivesResponseNoteThreadMatters() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		// Given
		// All service operation must be driven by the same thread
		ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);

		// Check state
		assertTrue(connectToDashboardAndSendARequestAndExpectAResponseMessageFromDashboard(serviceExecutor));
	}

	@Ignore
	@Test
	public void givenActiveWhenConnectAndSendMessageToDBThenReceivesCommandAckNoteThreadMatters() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		// Given
		// All service operation must be driven by the same thread
		//
		ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);

		// Check state
		assertTrue(connectToDashboardAndSendACommandAndExpectACommandAckMessageFromDashboard(serviceExecutor));
	}

	@Ignore
	@Test
	public void givenActiveWhenConnectAndSendMessageToDBThenReceivesOrderReqCompletionNoteThreadMatters() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		// Given
		// All service operation must be driven by the same thread
		//
		ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);

		// Check state
		assertTrue(connectToDashboardAndSendNewOrderRequestAndExpectOrderRequestCompletionMessageFromDashboard(serviceExecutor));
	}
	
	public void testReceiveMessage(){
		
	}
	
	public void testSendingMessage(){
		
	}
	
	public void testReceivingLotsOfMessage(){
		
	}
	
	public void testSendingLotsOfMessage(){
		
	}
	
	public void testSendingIndividualRequestTypeAndGetResponseBack(){}
	
	private boolean receiveOrderRequestCompletion(BufferedInputStream inputStream){
		final MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int bytesRead = 0;
		MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
		try {
			LOG.info("Waiting for message from dashboard");
			while ((bytesRead = inputStream.read(wireReceiveBuffer.byteArray())) > 0){
				LOG.info("Got message back from dashboard [numBytes:{}]", bytesRead);
				if (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
					messageHeaderDecoder.wrap(wireReceiveBuffer, 0);
					
					assertTrue("Expect command ack", messageHeaderDecoder.templateId() == OrderRequestCompletionSbeDecoder.TEMPLATE_ID);
					
					OrderRequestCompletionSbeDecoder decoder = new OrderRequestCompletionSbeDecoder();
					decoder.wrap(wireReceiveBuffer, 
							messageHeaderDecoder.encodedLength(), 
							messageHeaderDecoder.blockLength(), 
							messageHeaderDecoder.version());
					
					LOG.info("Received message [templateId:{}, clientKey:{}, orderSid:{}]", messageHeaderDecoder.templateId(), decoder.clientKey(), decoder.orderSid());					
				}
				return true;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			LOG.error("Caught exception", e);
			return false;
		}
		return true;
	}
	
	private boolean receiveResponse(BufferedInputStream inputStream){
		final MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int bytesRead = 0;
		MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
		try {
			LOG.info("Waiting for message from dashboard");
			while ((bytesRead = inputStream.read(wireReceiveBuffer.byteArray())) > 0){
				LOG.info("Got message back from dashboard [numBytes:{}]", bytesRead);
				if (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
					messageHeaderDecoder.wrap(wireReceiveBuffer, 0);
					
					assertTrue("Expect a response", messageHeaderDecoder.templateId() == ResponseSbeDecoder.TEMPLATE_ID);
					
					ResponseSbeDecoder responseDecoder = new ResponseSbeDecoder();
					responseDecoder.wrap(wireReceiveBuffer, 
							messageHeaderDecoder.encodedLength(), 
							messageHeaderDecoder.blockLength(), 
							messageHeaderDecoder.version());
					
					LOG.info("Received message [templateId:{}, clientKey:{}, resultType:{}]", messageHeaderDecoder.templateId(), responseDecoder.clientKey(), responseDecoder.resultType().name());					
				}
				return true;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			LOG.error("Caught exception", e);
			return false;
		}
		return true;
	}
	
	private boolean receiveCommandAck(BufferedInputStream inputStream){
		final MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		int bytesRead = 0;
		MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
		try {
			LOG.info("Waiting for message from dashboard");
			while ((bytesRead = inputStream.read(wireReceiveBuffer.byteArray())) > 0){
				LOG.info("Got message back from dashboard [numBytes:{}]", bytesRead);
				if (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
					messageHeaderDecoder.wrap(wireReceiveBuffer, 0);
					
					assertTrue("Expect command ack", messageHeaderDecoder.templateId() == CommandAckSbeDecoder.TEMPLATE_ID);
					
					CommandAckSbeDecoder decoder = new CommandAckSbeDecoder();
					decoder.wrap(wireReceiveBuffer, 
							messageHeaderDecoder.encodedLength(), 
							messageHeaderDecoder.blockLength(), 
							messageHeaderDecoder.version());
					
					LOG.info("Received message [templateId:{}, clientKey:{}, ackType:{}]", messageHeaderDecoder.templateId(), decoder.clientKey(), decoder.ackType().name());					
				}
				return true;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			LOG.error("Caught exception", e);
			return false;
		}
		return true;
	}
	
	private boolean connectToDashboardAndSendARequestAndExpectAResponseMessageFromDashboard(ExecutorService serviceExecutor){
		Socket dashboardClientSocket = null;
		try {
			dashboardClientSocket = new Socket(tcpListeningUrl, tcpListeningPort);

			ExecutorService receivingExecutor = Executors.newSingleThreadExecutor(); 
			BufferedInputStream inputStream = new BufferedInputStream(dashboardClientSocket.getInputStream());
			Future<Boolean> receivingFuture = receivingExecutor.submit(() -> { return receiveResponse(inputStream); });
	
			LOG.info("Connected to dashboard, got input stream");
			
			BufferedOutputStream outputStream = new BufferedOutputStream(dashboardClientSocket.getOutputStream());
			
			// Send a message over
			MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
			LOG.info("Connected to dashboard, got outputstream");
			
			// Send a subscription request to OrderAndTradeSnapshotService
			// Pretend that otss sends back a response and follow up with orders 
			Request request = Request.of(selfSinkId, RequestType.SUBSCRIBE, 
					Parameters.listOf(Parameter.of(ServiceType.OrderAndTradeSnapshotService), 
							Parameter.of(DataType.ALL_ORDER_AND_TRADE_UPDATE)));
			request.clientKey(8888);
			int length = selfMessenger.requestSender().encodeRequest(buffer, 0, serviceSinkId, request);
			try {
				LOG.info("Sending message to dashboard [length:{}]", length);
				outputStream.write(buffer.byteArray(), 0, length);
				outputStream.flush();
				LOG.info("Sent message to dashboard");
				
				DashboardService dashboard = (DashboardService)wrapper.coreService();
				AssertUtil.assertTrueWithinPeriod("Expect request", 
						() -> {
							LOG.info("Current number of client requests [size:{}]", dashboard.standAloneDashboardClientRequests().size());
							return (dashboard.standAloneDashboardClientRequests().size() == 1);
						},
						TimeUnit.SECONDS.toNanos(500));
				
				// Dashboard should have received a message thru TCP and sends itself a message
				// Now, we need to push this message manually
				ConcurrentUtil.sleep(1000);
				serviceExecutor.execute(() -> {wrapper.pushNextMessage();});

				// Send a fake response back from DashboardService
				// Need to get the request from dashboard service and send a corresponding response
				AssertUtil.assertTrueWithinPeriod("Expect a request in tracker", 
						() -> {
							return wrapper.messengerWrapper().requestTracker().requests().size() > 0;
						}, 
						TimeUnit.SECONDS.toNanos(2L));
				Request submittedRequest = null;
				for (Entry<RequestContext> entrySet : wrapper.messengerWrapper().requestTracker().requests().int2ObjectEntrySet()){
					if (entrySet.getValue().request().requestType() == RequestType.SUBSCRIBE){
						submittedRequest = entrySet.getValue().request();
						break;
					}
				}
				assertNotNull("Cannot find submitted request", submittedRequest);
				
				LOG.info("Sending response to dashboard");
				selfMessenger.responseSender().sendResponse(wrapper.sink(), 
						submittedRequest.clientKey(),
						BooleanType.TRUE, 
						ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK);
				serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
				ConcurrentUtil.sleep(2000);
				
				try {
					Boolean receivingResult = receivingFuture.get(5, TimeUnit.SECONDS);
					if (receivingResult.booleanValue()){
						return true;
					}
				} 
				catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				catch (java.util.concurrent.TimeoutException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return false;
				
			}
			catch (IOException e) {
				LOG.error("Could not send byte to dashboard [request:{}]", request);
			}			
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			//				if (dashboardClientSocket == null){
			//					return;
			//				}
			//				if (!dashboardClientSocket.isClosed()){
			//					try {
			//						dashboardClientSocket.close();
			//					} 
			//					catch (IOException e1) {
			//						LOG.error("Caught exception when closing socket", e1);
			//					}
			//				}
		}
		return false;
	}
	
	private boolean connectToDashboardAndSendACommandAndExpectACommandAckMessageFromDashboard(ExecutorService serviceExecutor){
		Socket dashboardClientSocket = null;
		try {
			dashboardClientSocket = new Socket(tcpListeningUrl, tcpListeningPort);

			ExecutorService receivingExecutor = Executors.newSingleThreadExecutor(); 
			BufferedInputStream inputStream = new BufferedInputStream(dashboardClientSocket.getInputStream());
			Future<Boolean> receivingFuture = receivingExecutor.submit(() -> { return receiveCommandAck(inputStream); });
	
			LOG.info("Connected to dashboard, got input stream");
			
			BufferedOutputStream outputStream = new BufferedOutputStream(dashboardClientSocket.getOutputStream());
			
			// Send a message over
			MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
			LOG.info("Connected to dashboard, got outputstream");
			
			// Send a subscription request to OrderAndTradeSnapshotService
			// Pretend that otss sends back a response and follow up with orders
			Command command = Command.of(selfSinkId, 9999, CommandType.CAPTURE_PROFIT);
			int length = selfMessenger.commandSender().encodeCommand(buffer, 0, serviceSinkId, command);
			try {
				LOG.info("Sending command to dashboard [length:{}]", length);
				outputStream.write(buffer.byteArray(), 0, length);
				outputStream.flush();
				LOG.info("Sent command to dashboard");
				
				DashboardService dashboard = (DashboardService)wrapper.coreService();
				AssertUtil.assertTrueWithinPeriod("Expect command", 
						() -> {
							LOG.info("Current number of client commands [size:{}]", dashboard.standAloneDashboardClientCommands().size());
							return (dashboard.standAloneDashboardClientCommands().size() == 1);
						},
						TimeUnit.SECONDS.toNanos(500));
				
				// Dashboard should have received a message thru TCP and sends itself a message
				// Now, we need to push this message manually
				ConcurrentUtil.sleep(1000);
				serviceExecutor.execute(() -> {wrapper.pushNextMessage();});

				// Send a fake response back from DashboardService
				// Need to get the request from dashboard service and send a corresponding response
				AssertUtil.assertTrueWithinPeriod("Expect a command in tracker", 
						() -> {
							return wrapper.messengerWrapper().commandTracker().commands().size() > 0;
						}, 
						TimeUnit.SECONDS.toNanos(2L));
				Command submittedCommand = null;
				for (Entry<CommandContext> entrySet : wrapper.messengerWrapper().commandTracker().commands().int2ObjectEntrySet()){
					if (entrySet.getValue().command().commandType() == CommandType.CAPTURE_PROFIT){
						submittedCommand = entrySet.getValue().command();
						break;
					}
				}
				assertNotNull("Cannot find submitted command", submittedCommand);
				
				LOG.info("Sending command ack to dashboard");
				selfMessenger.commandAckSender().sendCommandAck(wrapper.sink(), 
						submittedCommand.clientKey(), 
						CommandAckType.OK, 
						submittedCommand.commandType());
				serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
				ConcurrentUtil.sleep(2000);
				
				try {
					Boolean receivingResult = receivingFuture.get(5, TimeUnit.SECONDS);
					if (receivingResult.booleanValue()){
						return true;
					}
				} 
				catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				catch (java.util.concurrent.TimeoutException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return false;
				
			}
			catch (IOException e) {
				LOG.error("Could not send byte to dashboard [command:{}]", command);
			}
			
			// Check that a message has been sent out
			
			// Expect to receive message from dashboard
			
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
		}
		return false;
	}
	
	private boolean connectToDashboardAndSendNewOrderRequestAndExpectOrderRequestCompletionMessageFromDashboard(ExecutorService serviceExecutor){
		Socket dashboardClientSocket = null;
		try {
			dashboardClientSocket = new Socket(tcpListeningUrl, tcpListeningPort);

			ExecutorService receivingExecutor = Executors.newSingleThreadExecutor(); 
			BufferedInputStream inputStream = new BufferedInputStream(dashboardClientSocket.getInputStream());
			Future<Boolean> receivingFuture = receivingExecutor.submit(() -> { return receiveOrderRequestCompletion(inputStream); });
	
			LOG.info("Connected to dashboard, got input stream");
			
			BufferedOutputStream outputStream = new BufferedOutputStream(dashboardClientSocket.getOutputStream());
			
			// Send a message over
			MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
			LOG.info("Connected to dashboard, got outputstream");
			
			// Send a subscription request to OrderAndTradeSnapshotService
			// Pretend that otss sends back a response and follow up with orders
			long secSid = 800001;
			OrderType orderType = OrderType.LIMIT_ORDER;
			int quantity = 100;
			Side side = Side.BUY;
			TimeInForce tif = TimeInForce.DAY;
			BooleanType isAlgoOrder = BooleanType.FALSE;
			int limitPrice = 300;
			int stopPrice = 290;
			
			long timeoutAtNanoOfDay = -1;
			boolean retry = false;
			int assignedThrottleTrackerIndex = 0;
			int portSid = 600000;
			TriggerInfo triggerInfo = TriggerInfo.of(); 
			NewOrderRequest request = NewOrderRequest.of(7777, wrapper.sink(), secSid, orderType, quantity, side, tif, isAlgoOrder, limitPrice, stopPrice, timeoutAtNanoOfDay, retry, assignedThrottleTrackerIndex, portSid, triggerInfo);
			Command command = Command.of(selfSinkId, 9999, CommandType.CAPTURE_PROFIT);
			int length = selfMessenger.orderSender().encodeNewOrder(buffer, 0, serviceSinkId, request);
			try {
				LOG.info("Sending new order request to dashboard [length:{}]", length);
				outputStream.write(buffer.byteArray(), 0, length);
				outputStream.flush();
				LOG.info("Sent new order request to dashboard");
				
				DashboardService dashboard = (DashboardService)wrapper.coreService();
				AssertUtil.assertTrueWithinPeriod("Expect new order request", 
						() -> {
							LOG.info("Current number of client order request [size:{}]", dashboard.standAloneDashboardClientOrderRequests().size());
							return (dashboard.standAloneDashboardClientOrderRequests().size() == 1);
						},
						TimeUnit.SECONDS.toNanos(500));
				
				// Dashboard should have received a message thru TCP and sends itself a message
				// Now, we need to push this message manually
				ConcurrentUtil.sleep(1000);
				serviceExecutor.execute(() -> {wrapper.pushNextMessage();});

				// Send a fake response back from DashboardService
				// Need to get the request from dashboard service and send a corresponding response
				AssertUtil.assertTrueWithinPeriod("Expect a new order request in tracker", 
						() -> {
							return wrapper.messengerWrapper().orderTracker().requests().size() > 0;
						}, 
						TimeUnit.SECONDS.toNanos(2L));
				NewOrderRequest submittedRequest = null;
				for (Entry<OrderRequestContext> entrySet : wrapper.messengerWrapper().orderTracker().requests().int2ObjectEntrySet()){
					submittedRequest = entrySet.getValue().request().asNewOrderRequest();
					if (submittedRequest != null){
						break;
					}
				}
				assertNotNull("Cannot find submitted new order request", submittedRequest);
				
				LOG.info("Sending order request completion to dashboard [orderSid:{}]", submittedRequest.orderSid());
				selfMessenger.orderSender().sendOrderRequestCompletion(wrapper.sink(), 
						submittedRequest.clientKey(), 
						submittedRequest.orderSid(), 
						OrderRequestCompletionType.OK);
				serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
				ConcurrentUtil.sleep(2000);
				
				try {
					Boolean receivingResult = receivingFuture.get(5, TimeUnit.SECONDS);
					if (receivingResult.booleanValue()){
						return true;
					}
				} 
				catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				catch (java.util.concurrent.TimeoutException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return false;
				
			}
			catch (IOException e) {
				LOG.error("Could not send byte to dashboard [command:{}]", command);
			}
			
			// Check that a message has been sent out
			
			// Expect to receive message from dashboard
			
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}
	
	private boolean connectToAndSendMessageToDashboard(){
		Socket dashboardClientSocket = null;
		try {
			dashboardClientSocket = new Socket(tcpListeningUrl, tcpListeningPort);
			
			LOG.info("Connected to dashboard [port:{}]", dashboardClientSocket.getLocalPort());
			
			BufferedOutputStream outputStream = new BufferedOutputStream(dashboardClientSocket.getOutputStream());
			
			// Send a message over
			MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
			Request request = Request.of(selfSinkId, RequestType.SUBSCRIBE, Parameters.listOf(Parameter.of(ServiceType.StrategyService)));
			request.clientKey(8888);
			int length = selfMessenger.requestSender().encodeRequest(buffer, 0, serviceSinkId, request);
			try {
				LOG.info("Sending message to dashboard [length:{}]", length);
				outputStream.write(buffer.byteArray(), 0, length);
				outputStream.flush();
				LOG.info("Sent message to dashboard");
				return true;
			}
			catch (IOException e) {
				LOG.error("Could not send byte to dashboard [request:{}]", request);
			}
		} 
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
}
