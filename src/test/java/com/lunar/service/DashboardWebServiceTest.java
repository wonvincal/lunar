package com.lunar.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessageFbsEncoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.ResponseFbs;
import com.lunar.message.io.fbs.SecurityFbs;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeEncoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.ResponseSender;
import com.lunar.message.sender.SecuritySender;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.Order;
import com.lunar.order.OrderUtil;
import com.lunar.order.Trade;
import com.lunar.order.TradeUtil;
import com.lunar.util.LogUtil;
import com.lunar.util.WebSocketClientKey;

import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.core.WebSocketChannel;

@RunWith(MockitoJUnitRunner.class)
public class DashboardWebServiceTest {
	static final Logger LOG = LogManager.getLogger(DashboardWebServiceTest.class);

	private DashboardWebService service;
	private static final int PORT = 8899;

	@Mock 
	private DashboardService coreService;  

	@BeforeClass
	public static void beforeClass(){
		Configurator.setLevel("org.eclipse.jetty", Level.ERROR);		
	}
	
	@AfterClass
	public static void afterClass(){
		Configurator.setLevel("org.eclipse.jetty", Level.DEBUG);		
	}
	
	@Before
	public void setUp() throws Exception{
		service = DashboardWebService.of(PORT, "myapp",
				Optional.of(8),
				Optional.of(20),
				Optional.of(Duration.ofSeconds(180)),
				Optional.of(32768),
				Optional.empty(),
				new ClassPathResourceManager(
						DashboardWebServiceTest.class.getClassLoader(), 
						"" /* DashboardUndertowWebService.class.getPackage()*/),
				coreService);
		service.start();
	}
	
	@After
	public void tearDown() throws Exception{
		service.stop();
	}
	
	@Test
	public void testPing(){
		String destUri = "ws://localhost:" + PORT + "/myapp";
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
			}
		}, new SimpleSocket.TextMessageHandler() {
			
			@Override
			public void onMessage(String text) {
				received.incrementAndGet();				
			}
		});

		boolean latchResult = false;
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, request);
			sessionFuture.get(5, TimeUnit.SECONDS);
			socket.send("{ type: PING }");
			latchResult = socket.latch().await(2, TimeUnit.SECONDS);
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
		assertTrue("Timeout - no response was received", latchResult);
		assertEquals(0, socket.numWriteFailed());
	}
	
	static class RequestMatcher extends ArgumentMatcher<Request> {
		Predicate<Request> predicate;
		static RequestMatcher of(Predicate<Request> predicate){
			return new RequestMatcher(predicate);
		}
		RequestMatcher(Predicate<Request> predicate){
			this.predicate = predicate;
		}
		@Override
		public boolean matches(Object argument) {
			Request request = (Request) argument;
			return predicate.test(request);
		}
	}
	
	@Test
	public void testRequestAsBinaryMessage(){
		// Given
		String destUri = "ws://localhost:" + PORT + "/myapp";
		WebSocketClient client = new WebSocketClient();
		final int expectedResponseCount = 0;
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

		CompletableFuture<Request> requestFuture = new CompletableFuture<Request>();
		request.resultType(ResultType.OK);
		requestFuture.complete(request);
		
		RequestMatcher matcher  = RequestMatcher.of(new Predicate<Request>() {
			@Override
			public boolean test(Request r) {
				return r.clientKey() == request.clientKey();
			}
		});
		when(coreService.handleExternalRequestAsync(anyObject(), argThat(matcher))).thenReturn(requestFuture);
		
		// When
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			LOG.info("Client: client bindAdress:{}, localAddress:{}, remoteAddress:{}", client.getBindAddress(), session.getLocalAddress(), session.getRemoteAddress());
			socket.send(buffer.slice());
			socket.latch().await(1, TimeUnit.SECONDS);
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
		assertEquals(0, socket.numWriteFailed());
		assertEquals(1, socket.receivedTextMessages().size());
		verify(coreService, times(1)).handleExternalRequestAsync(any(), argThat(matcher));
		assertEquals(0, socket.receivedBinaryMessages().size());
		assertEquals(0, service.activeChannels().size());
	}
	
	@Test
	public void testReceivingSecurity(){
		// Given
		String destUri = "ws://localhost:" + PORT + "/myapp";
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
		
		final int refClientKey = 2323232;
		
		MutableDirectBuffer directBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
		SecuritySbeEncoder sbeEncoder = new SecuritySbeEncoder();
		final int refSystemId = 1;
		final long refSid = 12345;
		final String refCode = "MAX!";
		final SecurityType refType = SecurityType.STOCK;
		final int refExchangeSid = 2;
		final long refUndSecSid = 56789;
		final PutOrCall refPutOrCall = PutOrCall.CALL;
		final OptionStyle refStyle = OptionStyle.ASIAN;
		final int refStrikePrice = 123456;
		final int refConvRatio = 1000;
		final int refIssuerSid = 1;
		final LocalDate refListedDate = LocalDate.now();
		LocalDate refMaturity = LocalDate.of(2016, 3, 31);
		final int refLotSize = 10000;
		final boolean refIsAlgo = true;
		Security security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), refListedDate, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, SpreadTableBuilder.get(refType))
				.omesSink(MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.OrderManagementAndExecutionService, "test-omes"))
                .mdsssSink(MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.MarketDataSnapshotService, "test-mdsss"))
				.mdsSink(MessageSinkRef.createValidNullSinkRef(refSystemId, ServiceType.MarketDataService, "test-mds"));
		
		SecuritySender.encodeSecurityOnly(directBuffer, 0, stringBuffer, sbeEncoder, security);
		
		SecuritySbeDecoder sbeDecoder = new SecuritySbeDecoder();
		sbeDecoder.wrap(directBuffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);
		
		// When
		try {
			client.start();
			URI uri = new URI(destUri);
			ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
			Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
			Session session = sessionFuture.get(5, TimeUnit.SECONDS);
			
			WebSocketChannel channel = service.activeChannels().get(session.getLocalAddress());
			WebSocketClientKey clientKey = WebSocketClientKey.of(refClientKey, channel);
			
			MessageHeaderDecoder header = new MessageHeaderDecoder();
			service.handleSecurity(header, clientKey, sbeDecoder);
			socket.latch().await(5, TimeUnit.SECONDS);
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
		assertEquals(0, socket.numWriteFailed());
		assertEquals(1, socket.receivedTextMessages().size());
		assertEquals(1, socket.receivedBinaryMessages().size());
		ByteBuffer receivedBinaryMessage = socket.receivedBinaryMessages().remove();
		MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
		assertEquals(MessagePayloadFbs.SecurityFbs, fbsDecoder.init(receivedBinaryMessage));
		SecurityFbs securityFbs = fbsDecoder.asSecurityFbs();
		assertEquals(security.code(), securityFbs.code().trim());
	}
	
	@Test
	public void testCommand(){
		String destUri = "ws://localhost:" + PORT + "/myapp";
		WebSocketClient client = new WebSocketClient();
        SimpleEchoSocket socket = new SimpleEchoSocket();
        socket.getToSendMessages().add("{ type: GET_SYSTEM_STATUS }");
        socket.getToSendMessages().add("{ type: START }");
        socket.getToSendMessages().add("{ type: STOP }");

        try {
			client.start();
			URI uri = new URI(destUri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            Future<Session> sessionFuture = client.connect(socket, uri, request);
            System.out.printf("Connecting to : %s, waiting for session %n", uri);
            Session session = sessionFuture.get();
            System.out.println("Got session");
            assertTrue(session != null);
			socket.awaitClose(1, TimeUnit.SECONDS);
			System.out.println("Received: " + socket.getReceivedMessages().size() + " messages");
        }
		catch (Throwable t){
			LOG.error("Caught throwable", t);
		}
		finally {
            try {
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
		}
        for (String message : socket.getReceivedMessages()){
        	LOG.debug(message);
        }
        
        assertEquals(4, socket.getReceivedMessages().size());
	}
	
	@Test
	public void test(){
		String destUri = "ws://localhost:" + PORT + "/myapp";
		WebSocketClient client = new WebSocketClient();
        SimpleEchoSocket socket = new SimpleEchoSocket();
        socket.getToSendMessages().add("{ \"type\": \"Hello\" }");
        socket.getToSendMessages().add("{ \"type\": \"Thanks for the conversation.\" }");
        socket.getToSendMessages().add("{ \"type\": \"" + createString(31000) + "\"}");
        socket.getToSendMessages().add("{ \"type\": \"" + createString(65400) + "\"}");

        try {
			client.start();
			URI uri = new URI(destUri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            Future<Session> sessionFuture = client.connect(socket, uri, request);
            System.out.printf("Connecting to : %s, waiting for session %n", uri);
            Session session = sessionFuture.get();
            System.out.println("Got session");
            assertTrue(session != null);
			socket.awaitClose(1, TimeUnit.SECONDS);
			System.out.println("Received: " + socket.getReceivedMessages().size() + " messages");
        }
		catch (Throwable t){
			LOG.error("Caught throwable", t);
		}
		finally {
            try {
                client.stop();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
		}

        assertEquals(5, socket.getReceivedMessages().size());
	}

	@Test
	public void testGetResponse() throws Exception{
		String destUri = "ws://localhost:" + PORT + "/myapp";
		WebSocketClient client = new WebSocketClient(Executors.newCachedThreadPool());
		client.setMaxBinaryMessageBufferSize(128);
		final AtomicInteger received = new AtomicInteger(0);
		final int expectedResponseCount = 3001;
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
		
		ImmutableList<Parameter> refParameters = Parameters.listOf(Parameter.of(TemplateType.RESPONSE));
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
			socket.latch().await(1, TimeUnit.SECONDS);
		}
		catch (TimeoutException e){
			LOG.error("Caught timeout exception", e);
			throw new AssertionError("Caught timeout exception", e);
		}
		catch (Throwable t){
			LOG.error("Caught throwable", t);
			throw new AssertionError("Caught throwable", t);
		}
	}
	
	@Test
	public void testMassiveResponse() throws Exception{
		String destUri = "ws://localhost:" + PORT + "/myapp";
		WebSocketClient client = new WebSocketClient(Executors.newCachedThreadPool());
		client.setMaxBinaryMessageBufferSize(128);
		final AtomicInteger received = new AtomicInteger(0);
		final int numFilledOrders = 3000;
		final int expectedResponseCount = numFilledOrders * 2 + 1;
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
		
		// Get a connection
		client.start();
		URI uri = new URI(destUri);
		ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
		Future<Session> sessionFuture = client.connect(socket, uri, upgradeRequest);
		Session session = sessionFuture.get(5, TimeUnit.SECONDS);
		WebSocketChannel channel = service.activeChannels().get(session.getLocalAddress());
		
		int responseClientKey = 88888;
		WebSocketClientKey clientKeyObj = WebSocketClientKey.of(responseClientKey, channel);
		
		// Encode into response
		// 1) Encode response
		// 2) Encode header
		int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
		BooleanType isLast = BooleanType.FALSE; 
		for (int i = 0; i < numFilledOrders; i++){
			Order order = createOrderResponse(responseClientKey, responseMsgSeq++, isLast);			
			headerDecoder.wrap(responseBuffer, 0);
			responseDecoder.wrap(responseBuffer, headerDecoder.encodedLength(), ResponseSbeDecoder.BLOCK_LENGTH, ResponseSbeDecoder.SCHEMA_VERSION);
			service.handleResponse(clientKeyObj, responseBuffer, 0, headerDecoder, responseDecoder);
			
			createTradeResponse(responseClientKey, responseMsgSeq++, isLast, order);
			responseDecoder.wrap(responseBuffer, headerDecoder.encodedLength(), ResponseSbeDecoder.BLOCK_LENGTH, ResponseSbeDecoder.SCHEMA_VERSION);
			service.handleResponse(clientKeyObj, responseBuffer, 0, headerDecoder, responseDecoder);
			
		}
		createResponse(responseClientKey, responseMsgSeq, BooleanType.TRUE);
		service.handleResponse(clientKeyObj, responseBuffer, 0, headerDecoder, responseDecoder);
		
		socket.latch().await(5, TimeUnit.SECONDS);
		
		MessageFbsDecoder decoder = MessageFbsDecoder.of();
		ByteBuffer byteBuffer = socket.receivedBinaryMessages().poll();
		int seq = ServiceConstant.START_RESPONSE_SEQUENCE;
		while (byteBuffer != null){
			switch (decoder.init(byteBuffer)){
			case MessagePayloadFbs.ResponseFbs:
				ResponseFbs responseFbs = decoder.asResponseFbs();
				if (responseFbs.isLast()){
					LOG.info("Response [isLast:{}, seq:{}]", responseFbs.isLast(), seq);
				}
				seq++;
//				LOG.info("Response: {}, {}, {}", responseFbs.clientKey(), responseFbs.isLast(), MessagePayloadFbs.name(responseFbs.messagePayloadType()));
				break;
			}
			byteBuffer = socket.receivedBinaryMessages().poll();
		}
		LOG.info("Received all responses: {}", received.get());
	}
	
	private MutableDirectBuffer entityInBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private OrderSbeEncoder orderEncoder = new OrderSbeEncoder();
	private TradeSbeEncoder tradeEncoder = new TradeSbeEncoder();
	private MutableDirectBuffer responseBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
	private MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
	private ResponseSbeEncoder responseEncoder = new ResponseSbeEncoder();
	private ResponseSbeDecoder responseDecoder = new ResponseSbeDecoder();
	private AtomicInteger messageSeqGen = new AtomicInteger(8000000);
	private AtomicLong channelSeqGen = new AtomicLong(9000000);
	private AtomicInteger orderSidGen = new AtomicInteger(6000000);
	private AtomicInteger tradeSidGen = new AtomicInteger(7000000);

	private int dashboardSinkId = 2;

	private void createResponse(int responseClientKey, int responseMsgSeq, BooleanType isLast){
		ResultType resultType = ResultType.OK;
		int payloadLength = ResponseSender.encodeResponseOnly(responseBuffer, 
				headerEncoder.encodedLength(), 
				responseEncoder, responseClientKey, isLast, responseMsgSeq, resultType);
		MessageSender.encodeHeader(headerEncoder, 
				(byte)ref.sinkId(),
				dashboardSinkId, 
				responseBuffer, 
				0, 
				ResponseSbeDecoder.BLOCK_LENGTH, 
				ResponseSbeDecoder.TEMPLATE_ID, 
				ResponseSbeDecoder.SCHEMA_ID, 
				ResponseSbeDecoder.SCHEMA_VERSION, 
				messageSeqGen.get(), 
				payloadLength);
	}
	
	private Trade createTradeResponse(int responseClientKey, int responseMsgSeq, BooleanType isLast, Order order){
		long time = LocalTime.now().toNanoOfDay();
		TradeStatus tradeStatus = TradeStatus.NEW; 
		String executionId = "123456789012345678901";
		Trade trade = Trade.of(tradeSidGen.getAndIncrement(), 
				order.sid(), 
				order.orderId(), 
				order.secSid(), 
				order.side(), 
				0, 
				order.quantity(), 
				executionId, 
				order.limitPrice(), 
				order.quantity(),
				order.status(), 
				tradeStatus, 
				time, 
				time);
		trade.channelId(channelId);
		trade.channelSeq(order.channelSeq());
		TradeUtil.populateFrom(tradeEncoder, entityInBuffer, 0, trade, stringBuffer);

		ResultType resultType = ResultType.OK;
		int templateId = TradeSbeEncoder.TEMPLATE_ID;
		int templateBlockLength = TradeSbeEncoder.BLOCK_LENGTH;
		int payloadLength = ResponseSender.encodeResponseOnly(responseBuffer, 
				headerEncoder.encodedLength(), 
				responseEncoder, responseClientKey, isLast, responseMsgSeq, resultType, 
				templateId, 
				templateBlockLength, 
				entityInBuffer, 
				0, 
				OrderSbeEncoder.BLOCK_LENGTH);

		MessageSender.encodeHeader(headerEncoder, 
				(byte)ref.sinkId(),
				dashboardSinkId, 
				responseBuffer, 
				0, 
				ResponseSbeDecoder.BLOCK_LENGTH, 
				ResponseSbeDecoder.TEMPLATE_ID, 
				ResponseSbeDecoder.SCHEMA_ID, 
				ResponseSbeDecoder.SCHEMA_VERSION, 
				messageSeqGen.get(), 
				payloadLength);
		return trade;
	}
	
    private int channelId = 0;
	int systemId = 1;
	int sinkId = 1;
	String name = "dummy";
	ServiceType serviceType = ServiceType.StrategyService;	
    MessageSinkRef ref = DummyMessageSink.refOf(systemId, sinkId, name, serviceType);
    
	private Order createOrderResponse(int responseClientKey, int responseMsgSeq, BooleanType isLast){
		long time = LocalTime.now().toNanoOfDay();
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderType orderType = OrderType.LIMIT_ORDER;
		int stopPrice = 40000;
		int quantity = 1000;
		
		Side side = Side.BUY;
		int limitPrice = 40000;
		OrderStatus status = OrderStatus.FILLED;
		long secSid = 1l;
		Order order = Order.of(secSid, 
				ref, 
				orderSidGen.getAndIncrement(), 
				quantity, 
				isAlgo, 
				orderType, 
				side, 
				limitPrice, 
				stopPrice, 
				tif,
				status,
				time,
				time);
		order.channelId(channelId);
		order.channelSeq(channelSeqGen.getAndIncrement());
		OrderUtil.populateFrom(orderEncoder, entityInBuffer, 0, order, stringBuffer);
		
		ResultType resultType = ResultType.OK;
		int templateId = OrderSbeEncoder.TEMPLATE_ID;
		int templateBlockLength = OrderSbeEncoder.BLOCK_LENGTH;
		int payloadLength = ResponseSender.encodeResponseOnly(responseBuffer, 
				headerEncoder.encodedLength(), 
				responseEncoder, responseClientKey, isLast, responseMsgSeq, resultType, 
				templateId, templateBlockLength, entityInBuffer, 0, OrderSbeEncoder.BLOCK_LENGTH);
		
		MessageSender.encodeHeader(headerEncoder, 
				(byte)ref.sinkId(), 
				dashboardSinkId, 
				responseBuffer, 
				0, 
				ResponseSbeDecoder.BLOCK_LENGTH, 
				ResponseSbeDecoder.TEMPLATE_ID, 
				ResponseSbeDecoder.SCHEMA_ID, 
				ResponseSbeDecoder.SCHEMA_VERSION, 
				messageSeqGen.get(), 
				payloadLength);
		return order;
	}
	
    private String createString(int i) {
        StringBuilder builder = new StringBuilder();
        while (builder.length() < i) {
            builder.append("A very long text.");
        }
        return builder.toString();
    }
}

