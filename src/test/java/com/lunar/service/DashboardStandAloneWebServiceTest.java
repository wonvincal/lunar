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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.DashboardStandAloneWebServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.RequestTracker;
import com.lunar.exception.TimeoutException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceTestWrapper;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.MessageSinkEventFactory;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.MutableHandler;
import com.lunar.message.io.fbs.MessageFbsEncoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeEncoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.TestSenderBuilder;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.RingBufferMessageSink;
import com.lunar.message.sink.RingBufferMessageSinkPoller;
import com.lunar.order.Trade;
import com.lunar.util.AssertUtil;
import com.lunar.util.ConcurrentUtil;
import com.lunar.util.LogUtil;
import com.lunar.util.ServiceTestHelper;
import com.lunar.util.TestHelper;

@RunWith(MockitoJUnitRunner.class)
public class DashboardStandAloneWebServiceTest {
	static final Logger LOG = LogManager.getLogger(DashboardStandAloneWebServiceTest.class);
	
	@Mock
	private ServiceStatusSender serviceStatusSender;

	@Mock
	private MessageSink adminSink;

	@Mock
	private RequestTracker requestTracker;

	private MessageSinkRef admin;
	private final int adminSinkId = 1;

	private ServiceTestHelper testHelper;
	private LunarServiceTestWrapper wrapper;
	private DashboardStandAloneWebServiceConfig config;
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

	private ExecutorService serverListeningExecutor;
	private int listeningPort = 30001;
	private ServerSocket serverSocket;
	private Socket clientSocket;
	private MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private MutableDirectBuffer copyReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private RingBuffer<MutableDirectBuffer> ringBuffer;
	private ExecutorService messageConsumer;
	private int frameSize;
	private int queueSize;
	private int wsPort = 8123;
	private String wsPath = "ws";
	
	private EventHandler<MutableDirectBuffer> eventHandler = new EventHandler<MutableDirectBuffer>() {
		
		@Override
		public void onEvent(MutableDirectBuffer event, long sequence, boolean endOfBatch) throws Exception {
			LOG.info("Received message");
		}
	};
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	@Before
	public void setup(){
		frameSize = 1024;
		queueSize = 8192;
		buffer.setMemory(0, buffer.capacity(), (byte)0);		
		serverListeningExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("dashboard", "tcp-listener"));
		
		messageConsumer = Executors.newSingleThreadExecutor(new NamedThreadFactory("message-consumer", "message-consumer" + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
		
		Disruptor<MutableDirectBuffer> disruptor =  new Disruptor<MutableDirectBuffer>(
				new MessageSinkEventFactory(frameSize),
				queueSize,
				messageConsumer, 
				ProducerType.SINGLE, 
				new BlockingWaitStrategy());
		disruptor.handleEventsWith(eventHandler);
		disruptor.start();
		ringBuffer = disruptor.getRingBuffer();
		
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
		
		config = new DashboardStandAloneWebServiceConfig(systemId, 
				"test-db-standalone", 
				"test-db-standalone", 
				"test-db-standalone", 
				ServiceType.DashboardStandAloneWebService, 
				Optional.empty(),
				serviceSinkId, 
				Optional.of(queueSize), 
				Optional.of(queueSize), 
				Optional.empty(), 
				Optional.empty(), 
				true, 
				false, 
				Duration.ofSeconds(1), 
				false, 
				"", 
				wsPort, 
				wsPath,
				Optional.of(4),
				Optional.of(8),
				Optional.of(Duration.ofSeconds(60)),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.of(tcpListeningPort),
				Optional.of(tcpListeningUrl));
		
		adminSink = TestHelper.mock(adminSink, systemId, adminSinkId, ServiceType.AdminService);
		admin = MessageSinkRef.of(adminSink, "test-admin");

		ServiceBuilder builder = new ServiceBuilder() {
			@Override
			public ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService) {
				return DashboardStandAloneWebService.of(serviceConfig, messageService);
			}
		};
		
		SenderBuilder senderBuilder = new TestSenderBuilder();
		wrapper = testHelper.createService(config, builder, senderBuilder);
		wrapper.messenger().referenceManager().register(admin);
		wrapper.messenger().referenceManager().register(selfSinkRef);
		
		mutableResponseHandler = new MutableHandler<>();
		when(requestTracker.responseHandler()).thenReturn(mutableResponseHandler);
	}

	public static long tryPublish(RingBuffer<MutableDirectBuffer> ringBuffer, DirectBuffer srcBuffer, int offset, int length) {
		long sequence = -1;
		try {
			sequence = ringBuffer.tryNext();
			MutableDirectBuffer buffer = ringBuffer.get(sequence);
			buffer.putBytes(0, srcBuffer, offset, length);
		} 
		catch (InsufficientCapacityException e){
			return MessageSink.INSUFFICIENT_SPACE;
		}
		finally {
			// cannot think of any other way beside than this
			if (sequence != -1){
				ringBuffer.publish(sequence);
			}
		}
		return MessageSink.OK;
	}
	
	@Ignore
	@Test
	public void testCreate(){
		AtomicInteger payloadLength = new AtomicInteger();
		serverListeningExecutor.execute(() -> {
			try {
				this.serverSocket = new ServerSocket(listeningPort);			
			}
			catch (IOException e){
				LOG.error("Cannot create server socket to listen to port " + listeningPort, e);
			}
			
			TradeSbeEncoder tradeEncoder = new TradeSbeEncoder();
			TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
			MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
			MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
			MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
			while (true){
				try {
					clientSocket = this.serverSocket.accept();
					
					for (int i = 0; i < 5; i++){
				    int tradeSid = 100001;
					int orderSid = 200001;
					int orderId = 300001;
					long secSid = 500001;
					int leavesQty = 0;
					int cumulativeQty = 2000;
					String executionId = "400001400001400001123";
					int executionPrice = 520000;
					int executionQty = 800;
										
					Trade trade = Trade.of(tradeSid, 
				    		orderSid, 
				    		orderId, 
				    		secSid, 
				    		Side.BUY, 
				    		leavesQty, 
				    		cumulativeQty, 
				    		executionId, 
				    		executionPrice, 
				    		executionQty, OrderStatus.PARTIALLY_FILLED, TradeStatus.NEW, 0, 1);
					
					byte senderSinkId = 8;
					int sinkId = 1;
					int seq = 2;
				    int length = OrderSender.encodeTradeOnly(buffer, 
				    		messageHeaderEncoder.encodedLength(), 
				    		tradeEncoder, 123, 1, trade.sid(), trade.orderSid(), trade.orderId(), OrderStatus.NEW, 
				    		trade.tradeStatus(), 
				    		trade.side(), 
				    		OrderSender.prepareExecutionId(trade.executionId(), stringBuffer),
				    		trade.executionPrice(), 
				    		trade.executionQty(), 
				    		secSid);
				    tradeDecoder.wrap(buffer, messageHeaderEncoder.encodedLength(), TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION);

				    payloadLength.set(length);

				    
					MessageSender.encodeHeader(messageHeaderEncoder, 
							senderSinkId, 
							sinkId, 
							buffer, 
							0, 
							TradeSbeEncoder.BLOCK_LENGTH,
							TradeSbeEncoder.TEMPLATE_ID,
							TradeSbeEncoder.SCHEMA_ID,
							TradeSbeEncoder.SCHEMA_VERSION, 
							seq, 
							length);
					
					int totalLength = length + messageHeaderEncoder.encodedLength();
					
					// Make sure we can decode
					messageHeaderDecoder.wrap(buffer, 0);
					assertEquals(length, messageHeaderDecoder.payloadLength());
					assertEquals(senderSinkId, messageHeaderDecoder.senderSinkId());
				    
					tradeDecoder.wrap(buffer, 
							messageHeaderDecoder.encodedLength(), 
							messageHeaderDecoder.blockLength(), 
							messageHeaderDecoder.version());
					assertEquals(secSid, tradeDecoder.secSid());
					
					// Send data to clientSocket
					LOG.info("Sending bytes [numBytes:{}]", totalLength);
					clientSocket.getOutputStream().write(buffer.byteArray(), 0, totalLength);
					}
					break;
				}
				catch (Exception e){
					LOG.error("Caught exception when accepting new client", e);
				}
			}
		});

		Socket toSocket = null;
		try {
			MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

			toSocket = new Socket("127.0.0.1", listeningPort);
//			byte[] byteBuffer = new byte[65536];
			int byteRead = 0;
			int copyReceiveBufferOffset = -1;
			BufferedInputStream inFromServer = new BufferedInputStream(toSocket.getInputStream());
			
			while ((byteRead = inFromServer.read(wireReceiveBuffer.byteArray())) > 0){
				// Easiest way is to copy read bytes out to another buffer
				// Once copied bytes exceed header length, read it.  If payload is also available, read it off the byte buffer.

				// Find out how to determine begin and end of data
				// Wrap bytes in DirectBuffer with MessageHeader
				// Make sure we have the whole message before decoding
				// Once enough byte, send them out
				int processedBytes = 0;
				if (copyReceiveBufferOffset == -1){
					// Consume all byteRead
					while (byteRead >= MessageHeaderDecoder.ENCODED_LENGTH){
						messageHeaderDecoder.wrap(wireReceiveBuffer, 0);
						int length = MessageHeaderDecoder.ENCODED_LENGTH + messageHeaderDecoder.payloadLength();
						if (byteRead >= length){
							// Copy current message
							tryPublish(ringBuffer, wireReceiveBuffer, 0, length);
							byteRead -= length;
							processedBytes += length;
						}
						else {
							// Need to wait for next read off the wire
							break;
						}
					}
					if (byteRead > 0){
						// Some dangling bytes, copy them from wireReceiveBuffer to copyReceiveBuffer
						copyReceiveBuffer.putBytes(copyReceiveBufferOffset, wireReceiveBuffer, processedBytes, byteRead);
						copyReceiveBufferOffset += byteRead;
					}
				}
				else{
					// Copy from wireReceiveBuffer to copyReceiveBuffer, then process from copyReceiveBuffer instead
					if (copyReceiveBufferOffset + byteRead >= copyReceiveBuffer.capacity()){
						// Need to re-align receive buffer
						// offset = 9 (length == 10)
						copyReceiveBuffer.putBytes(0, copyReceiveBuffer, copyReceiveBufferOffset, copyReceiveBufferOffset + 1);
						copyReceiveBufferOffset = -1;
					}
					
					copyReceiveBuffer.putBytes(copyReceiveBufferOffset, wireReceiveBuffer, 0, byteRead);
					copyReceiveBufferOffset += byteRead;
					while (copyReceiveBufferOffset + 1 >= MessageHeaderDecoder.ENCODED_LENGTH){
						messageHeaderDecoder.wrap(copyReceiveBuffer, 0);
						int length = MessageHeaderDecoder.ENCODED_LENGTH + messageHeaderDecoder.payloadLength();
						if (copyReceiveBufferOffset + 1 >= length){
							// Copy current message
							tryPublish(ringBuffer, copyReceiveBuffer, 0, length);
							copyReceiveBufferOffset += length;
							processedBytes += length;
						}
						else {
							// Need to wait for next read off the wire
							break;
						}
					}
				}

				LOG.info("Received data [numBytes:{}]", processedBytes);
				/*
				if (unreadNumBytes + byteRead <= copyReceiveBuffer.capacity()){
					copyReceiveBuffer.putBytes(unreadNumBytes, wireReceiveBuffer, 0, byteRead);
					unreadNumBytes += byteRead;
					
					// Write message to ring buffer
				}
				else {
					// Ok, receiveBuffer can no longer hold everything
					// Log it and throw data away
					threwAway += byteRead;
				}				
				
				LOG.info("Received data [numBytes:{}]", byteRead);
				
				// Read all bytes - found
				if (unreadNumBytes >= MessageHeaderDecoder.ENCODED_LENGTH){
					messageHeaderDecoder.wrap(copyReceiveBuffer, 0);
					int length = messageHeaderDecoder.payloadLength();
					assertEquals(payloadLength.get(), length);
					
					if (unreadNumBytes >= MessageHeaderDecoder.ENCODED_LENGTH + length){
						tradeDecoder.wrap(copyReceiveBuffer, 
								messageHeaderDecoder.encodedLength(), 
								messageHeaderDecoder.blockLength(), 
								messageHeaderDecoder.version());
						assertEquals(500001, tradeDecoder.secSid());
						LOG.info("Received sbe message [secSid:{}]", tradeDecoder.secSid());
					}
				}
				*/
				
			}
		} 
		catch (IOException e) {
			LOG.error("Caught exception", e);
		}
		finally{
			if (toSocket != null){
				try {
					toSocket.close();
				} 
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	@SuppressWarnings({ "deprecation", "unchecked" })
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
					return DashboardStandAloneWebService.of(serviceConfig, messageService);
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
		
		serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
		
		// verify
		// 1) state, 2) subscription request and position request are sent,
		// Check state
		AssertUtil.assertTrueWithinPeriod("Expect service in active state", 
				() -> { return wrapper.messageService().state() == States.ACTIVE; },
				TimeUnit.SECONDS.toNanos(5));

		DashboardStandAloneWebService service = (DashboardStandAloneWebService)wrapper.coreService();
		AssertUtil.assertTrueWithinPeriod("Cannot start listening to tcp connection", () -> {return service.listeningForIncomingTcpData().get();});
		AssertUtil.assertTrueWithinPeriod("Cannot start web service", () -> {return service.webServiceStarted();});
	}
	
	private boolean acceptClientConnection(){
		try {
			this.serverSocket = new ServerSocket(tcpListeningPort);			
		}
		catch (IOException e){
			LOG.error("Cannot create server socket to listen to port " + tcpListeningPort, e);
			return false;
		}
		try {
			LOG.info("Listening for new client connection");
			Socket clientSocket = this.serverSocket.accept();
			LOG.info("Accepted a client connection");
			clientSocket.close();
			return true;
		}
		catch (Exception e){
			LOG.error("Caught exception when accepting new client", e);
		}
		return false;
	}
	
	private Socket acceptClientConnectionAndReturnClientSocket(){
		try {
			this.serverSocket = new ServerSocket(tcpListeningPort);			
		}
		catch (IOException e){
			LOG.error("Cannot create server socket to listen to port " + tcpListeningPort, e);
			return null;
		}
		try {
			LOG.info("Listening for new client connection");
			Socket clientSocket = this.serverSocket.accept();
			LOG.info("Accepted a client connection");
			return clientSocket;
		}
		catch (Exception e){
			
		}
		return null;
	}
	
	@SuppressWarnings("unused")
	private int acceptClientConnectionAndReturnRequestClientKey(ExecutorService serviceExecutor){
		try {
			this.serverSocket = new ServerSocket(tcpListeningPort);			
		}
		catch (IOException e){
			LOG.error("Cannot create server socket to listen to port " + tcpListeningPort, e);
			return -1;
		}
		try {
			LOG.info("Listening for new client connection");
			Socket clientSocket = this.serverSocket.accept();
			LOG.info("Accepted a client connection");
		
			final MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));

			BufferedInputStream inputStream = new BufferedInputStream(clientSocket.getInputStream());
			int bytesRead = 0;
			MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
			LOG.info("Waiting for message from standalone service");
			while ((bytesRead = inputStream.read(wireReceiveBuffer.byteArray())) > 0){
				LOG.info("Dashboard got message from standalone service");
				if (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
					messageHeaderDecoder.wrap(wireReceiveBuffer, 0);
					
					assertTrue("Expect request", messageHeaderDecoder.templateId() == RequestSbeDecoder.TEMPLATE_ID);

					RequestSbeDecoder decoder = new RequestSbeDecoder();
					decoder.wrap(wireReceiveBuffer,
							messageHeaderDecoder.encodedLength(), 
							messageHeaderDecoder.blockLength(), 
							messageHeaderDecoder.version());
					
					LOG.info("Received message [templateId:{}, clientKey:{}, requestType:{}]", messageHeaderDecoder.templateId(), decoder.clientKey(), decoder.requestType().name());
					
					return decoder.clientKey();
					// Return a response
//					selfMessenger.responseSender().sendResponse(wrapper.sink(), 
//							decoder.clientKey(),
//							BooleanType.TRUE, 
//							ServiceConstant.START_RESPONSE_SEQUENCE, ResultType.OK);
//					serviceExecutor.execute(() -> {wrapper.pushNextMessage();});
				}
				break;
			}
			
			// Return a message
			BufferedOutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream());			
			
		}
		catch (Exception e){
			LOG.error("Caught exception when accepting new client", e);
		}
		return -1;
	}
	
	@Ignore
	@Test
	public void givenActiveWhenConnectToItThruTCPThenConnectionEstablished() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException{
		// Given
		ExecutorService dashboardService = Executors.newSingleThreadExecutor();
		Future<Boolean> dashboardServiceFuture = dashboardService.submit(this::acceptClientConnection);
		
		ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);
		
		// When
		Boolean result = dashboardServiceFuture.get(5, TimeUnit.SECONDS);
		assertTrue(result.booleanValue());
	}
	
	private boolean dashboardExpectToReceiveRequestThenSendOutResponse(BufferedInputStream inputStream, BufferedOutputStream outputStream) throws IOException{
		int bytesRead = 0;
		MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
		while ((bytesRead = inputStream.read(wireReceiveBuffer.byteArray())) > 0){
			LOG.info("Dashboard service received message from dashboard standalone service thru TCP [numBytes:{}]", bytesRead);
			if (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
				messageHeaderDecoder.wrap(wireReceiveBuffer, 0);
				
				assertTrue("Expect request messsage", messageHeaderDecoder.templateId() == RequestSbeDecoder.TEMPLATE_ID);
				
				RequestSbeDecoder decoder = new RequestSbeDecoder();
				decoder.wrap(wireReceiveBuffer, 
						messageHeaderDecoder.encodedLength(), 
						messageHeaderDecoder.blockLength(), 
						messageHeaderDecoder.version());
				
				LOG.info("Received message [templateId:{}, clientKey:{}, name:{}]", messageHeaderDecoder.templateId(), decoder.clientKey(), decoder.requestType().name());
				
				// Send a response back
				MutableDirectBuffer sendBackBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
				MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
				ResponseSbeEncoder responseEncoder = new ResponseSbeEncoder();
				
				responseEncoder.wrap(sendBackBuffer, headerEncoder.encodedLength())
					.clientKey(decoder.clientKey())
					.responseMsgSeq(ServiceConstant.START_RESPONSE_SEQUENCE)
					.resultType(ResultType.OK);
				
				MessageSender.encodeHeader(headerEncoder, 
						(byte)serviceSinkId, 
						serviceSinkId, 
						sendBackBuffer, 
						0, 
						ResponseSbeEncoder.BLOCK_LENGTH, 
						ResponseSbeEncoder.TEMPLATE_ID, 
						ResponseSbeEncoder.SCHEMA_ID, 
						ResponseSbeEncoder.SCHEMA_VERSION, 
						0, 
						responseEncoder.encodedLength());
				
				outputStream.write(sendBackBuffer.byteArray(), 0, headerEncoder.encodedLength() + responseEncoder.encodedLength());
				outputStream.flush();
				LOG.info("Dashboard service sent response back thru TCP");
			}
			return true;
		}
		return false;
	}
	
	@Ignore
	@Test
	public void givenActiveWhenSendFbsRequestThenSbeRequestWillBeSentToDashboard() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException, IOException{
		// Given
		ExecutorService dashboardService = Executors.newSingleThreadExecutor();
		
		// Pretending to be dashboard service and start listening to tcp connection 
		Future<Socket> dashboardServiceFuture = dashboardService.submit(this::acceptClientConnectionAndReturnClientSocket);
		
		// Start dashboard standlone service, which will establish a connection with dashboard service
		ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);
				
		Socket clientSocket = dashboardServiceFuture.get(2, TimeUnit.SECONDS);
		
		BufferedInputStream inputStream = new BufferedInputStream(clientSocket.getInputStream());
		BufferedOutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
		Future<Boolean> dashboardServiceRequestFuture = dashboardService.submit(()->{
			return dashboardExpectToReceiveRequestThenSendOutResponse(inputStream, outputStream);
		});
		
		// When - send message using fbs
		boolean result = sendRequestAndReceiveResponseThruWebSocket(serviceExecutor);
		
		dashboardServiceRequestFuture.get(3, TimeUnit.SECONDS);
		assertNotNull("Could not get client socket", clientSocket);
		assertTrue(result);
		
		// Send request thru fbs
//				() -> { return acceptClientConnectionAndReturnRequestClientKey(serviceExecutor);});
//		advanceToActiveState(wrapper, selfMessenger, serviceExecutor);

		// When - send message using fbs
//		sendRequest();
//		
//		Integer dashboardResult = dashboardServiceFuture.get(10, TimeUnit.SECONDS);
//		LOG.info("Got response clientKey [{}]", dashboardResult);
		
		// Send a response back to dashboard
//		assertTrue(dashboardResult.booleanValue());
	}
	
	@Test
	public void givenActiveWhenReceiveSbeResponseThenFbsResponseWillBeSentToStandAloneService(){
		
	}
	
	private boolean sendRequestAndReceiveResponseThruWebSocket(ExecutorService serviceExecutor){
		// Given
		String destUri = "ws://localhost:" + wsPort + "/" + wsPath;
		WebSocketClient client = new WebSocketClient();
		final int expectedResponseCount = 1;
		final AtomicInteger received = new AtomicInteger(0);
		SimpleSocket socket = new SimpleSocket(new Supplier<Boolean>() {
			@Override
			public Boolean get() {
				return received.get() == expectedResponseCount;
			}
		}, new SimpleSocket.BinaryMessageHandler() {
			
			@Override
			public void onMessage(ByteBuffer buffer) {
				received.incrementAndGet();
			}
		}, new SimpleSocket.TextMessageHandler() {
			
			@Override
			public void onMessage(String text) {
				
			}
		});

		ImmutableList<Parameter> refParameters = com.lunar.message.Parameters.listOf(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.NULL_VAL.value()));
		final int anySinkId = 1;
		final int refClientKey = 5656565;
		final RequestType refRequestType = RequestType.GET;
		Request request = Request.of(anySinkId,
									 refRequestType,
									 refParameters).clientKey(refClientKey);

		MessageFbsEncoder messageFbsEncoder = MessageFbsEncoder.of();
		ByteBuffer buffer = messageFbsEncoder.encodeRequest(request);
		LOG.info("Sent buffer info [position:{}, limit:{}, remaining:{}, capacity:{}]", buffer.position(), buffer.limit(), buffer.remaining(), buffer.capacity());
		LOG.info("Sent buffer\n{}", LogUtil.dumpBinary(buffer, buffer.position(), buffer.remaining(), 8));

		// When
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
			socket.send(buffer.slice());
			
			ConcurrentUtil.sleep(1000);
			serviceExecutor.execute(() -> {wrapper.pushNextMessage();});			
			socket.latch().await(100000, TimeUnit.SECONDS);
			
			// Should expect to receive a ReponseFbs here
			LOG.info("Received [{}] message thru web socket", received.get());
			return true;
		}
		catch (TimeoutException e){
			LOG.error("Caught timeout exception", e);
			throw new AssertionError("Caught timeout exception", e);
		}
		catch (Throwable t){
			LOG.error("Caught throwable", t);
			throw new AssertionError("Caught throwable", t);
		}
		finally {
            try {
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
		}
	}
}
