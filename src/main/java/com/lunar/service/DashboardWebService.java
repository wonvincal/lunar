package com.lunar.service;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.Pooled;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.core.ConcurrentObjectPool;
import com.lunar.core.ObjectFactory;
import com.lunar.core.ObjectPool;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.fbs.CancelOrderRequestFbs;
import com.lunar.message.io.fbs.CommandFbs;
import com.lunar.message.io.fbs.EventCategoryFbs;
import com.lunar.message.io.fbs.EventLevelFbs;
import com.lunar.message.io.fbs.EventTypeFbs;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessageFbsEncoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.NewOrderRequestFbs;
import com.lunar.message.io.fbs.ParameterFbs;
import com.lunar.message.io.fbs.RequestFbs;
import com.lunar.message.io.fbs.ResultTypeFbs;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;
import com.lunar.service.DashboardStandAloneWebService.SbeHandler;
import com.lunar.util.ClientKey;
import com.lunar.util.WebSocketClientKey;

import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

public class DashboardWebService {
	
	private static final Logger LOG = LogManager.getLogger(DashboardWebService.class);
	private static final String INDEX_FILE_NAME = "index.html";
	private static final int NUM_CLIENT_WEBSOCKET_UPDATE_SENDER_THREADS = 1;
	protected static final int NUM_OUTSTANDING_BUFFER_THRESHOLD = 20000;

	private Undertow server;
	private final String wsPath;
	private final int port;
	private final Optional<Integer> numIoThreads;
	private final Optional<Integer> numCoreThreads;
	private final Optional<Duration> requestTimeout;
	private final Optional<Integer> bufferSizeInBytes; 
	private final int numOutstandingBufferPerChannelThreshold;
	private final ClassPathResourceManager resourceManager;
	private final DashboardService coreService;
	private final Gson gson;
	private final ExecutorService clientUpdateExecutor;
	private final ConcurrentHashMap<InetSocketAddress, WebSocketChannel> activeChannels;
	private final AtomicBoolean started;
	private final CopyOnWriteArrayList<WebSocketChannel> brokenChannels;

	/*
	 * Note: MessageFbsEncoder is not thread-safe.  Make sure that we are using one encoder per thread
	 */
	private final MessageFbsEncoder clientRequestUpdateFbsEncoder;
	private final MessageFbsEncoder clientUpdateFbsEncoder;
	private final SbeHandler<OrderSbeDecoder> clientOrderSbeEncoder; 
	private final SbeHandler<SecuritySbeDecoder> clientSecuritySbeEncoder;
	private final SbeHandler<ServiceStatusSbeDecoder> clientServiceStatusSbeEncoder;
	private final SbeHandler<StrategyWrtParamsSbeDecoder> clientStratWrtParamsSbeEncoder;
	private final SbeHandler<StrategyIssuerParamsSbeDecoder> clientStratIssuerParamsSbeEncoder;
	private final SbeHandler<StrategyIssuerUndParamsSbeDecoder> clientStratIssuerUndParamsSbeEncoder;
	private final SbeHandler<StrategyUndParamsSbeDecoder> clientStratUndParamsSbeEncoder;
	private final SbeHandler<RiskStateSbeDecoder> clientRiskStateSbeEncoder;
	private final SbeHandler<IssuerSbeDecoder> clientIssuerSbeEncoder;
	private final SbeHandler<PositionSbeDecoder> clientPositionSbeEncoder;
	private final SbeHandler<EventSbeDecoder> clientEventSbeEncoder;
	private final SbeHandler<TradeSbeDecoder> clientTradeSbeEncoder;
	private final SbeHandler<StrategyParamsSbeDecoder> clientStrategyParamsSbeEncoder;
	private final SbeHandler<StrategySwitchSbeDecoder> clientStrategySwitchSbeEncoder;
	private final SbeHandler<MarketStatsSbeDecoder> clientMarketStatsSbeEncoder;
	private final SbeHandler<OrderBookSnapshotSbeDecoder> clientOrderBookSnapshotSbeEncoder;
	private final SbeHandler<AggregateOrderBookUpdateSbeDecoder> clientAggregateOrderBookUpdateSbeEncoder;
	private final SbeHandler<ScoreBoardSbeDecoder> clientScoreBoardSbeEncoder;
	private final SbeHandler<NoteSbeDecoder> clientNoteSbeEncoder;
	private final SbeHandler<ChartDataSbeDecoder> clientChartDataSbeEncoder;
	
	public static DashboardWebService of(int port, String wsPath, Optional<Integer> numIoThreads, Optional<Integer> numCoreThreads, Optional<Duration> requestTimeout, Optional<Integer> bufferSizeInBytes, Optional<Integer> numOutstandingBufferPerChannelThreshold, DashboardService coreService){
		return new DashboardWebService(
				port, 
				wsPath,
				numIoThreads,
				numCoreThreads,
				requestTimeout,
				bufferSizeInBytes,
				numOutstandingBufferPerChannelThreshold,
				new ClassPathResourceManager(
						DashboardWebService.class.getClassLoader(), 
						"" /* DashboardUndertowWebService.class.getPackage()*/),
				coreService);
	}
	
	public static DashboardWebService of(int port, String wsPath, Optional<Integer> numIoThreads, Optional<Integer> numCoreThreads, Optional<Duration> requestTimeout, Optional<Integer> bufferSizeInBytes, Optional<Integer> numOutstandingBufferPerChannelThreshold, ClassPathResourceManager resourceManager,
			DashboardService coreService){
		return new DashboardWebService(port, 
				wsPath,
				numIoThreads,
				numCoreThreads,
				requestTimeout,
				bufferSizeInBytes,
				numOutstandingBufferPerChannelThreshold,
				resourceManager, 
				coreService);
	}
	
	private ByteBuffer encodeEventFbs(ByteBuffer suppliedBuffer, MessageFbsEncoder encoder,
			int sinkId,
    		long time,
			byte category,
			byte level,
			byte eventType,
			String description){
		encoder.encodeEvent(suppliedBuffer, sinkId, time, category, level, eventType, description);
		return suppliedBuffer;
	}
	
	DashboardWebService(int port, 
			String wsPath,
			Optional<Integer> numIoThreads, 
			Optional<Integer> numCoreThreads, 
			Optional<Duration> requestTimeout, 
			Optional<Integer> bufferSizeInBytes, 
			Optional<Integer> numOutstandingBufferPerChannelThreshold,
			ClassPathResourceManager resourceManager,
			DashboardService coreService){
		LOG.info("Constructor: port:{}, wsPath:{}", port, wsPath);
		this.port = port;
		this.wsPath = wsPath;
		this.resourceManager = resourceManager;
		this.coreService = coreService;
		this.gson = new Gson();
		this.clientUpdateExecutor= Executors.newFixedThreadPool(NUM_CLIENT_WEBSOCKET_UPDATE_SENDER_THREADS, new NamedThreadFactory("dashboard-web", "client-ws-update-sender"));
		this.clientRequestUpdateFbsEncoder = MessageFbsEncoder.of();
		this.clientUpdateFbsEncoder = MessageFbsEncoder.of();
		this.activeChannels = new ConcurrentHashMap<>();
		this.brokenChannels = new CopyOnWriteArrayList<>();
		this.started = new AtomicBoolean(false);
		this.numCoreThreads = numCoreThreads;
		this.numIoThreads = numIoThreads;
		this.requestTimeout = requestTimeout;
		this.bufferSizeInBytes =  bufferSizeInBytes;
		this.numOutstandingBufferPerChannelThreshold = numOutstandingBufferPerChannelThreshold.orElse(NUM_OUTSTANDING_BUFFER_THRESHOLD);
		this.objectFactory = (pool) -> { return FreePooledByteBufferCallback.of(pool);};
		this.objectPool = ConcurrentObjectPool.of(objectFactory, "callback-obj-pool", 1024);
		
		this.clientOrderSbeEncoder = this.clientUpdateFbsEncoder::encodeOrder;
		this.clientSecuritySbeEncoder = this.clientUpdateFbsEncoder::encodeSecurity;
		this.clientServiceStatusSbeEncoder = this.clientUpdateFbsEncoder::encodeServiceStatus;
		this.clientStratWrtParamsSbeEncoder = this.clientUpdateFbsEncoder::encodeStrategyWrtParams;
		this.clientStratIssuerParamsSbeEncoder = this.clientUpdateFbsEncoder::encodeStrategyIssuerParams;
		this.clientStratIssuerUndParamsSbeEncoder = this.clientUpdateFbsEncoder::encodeStrategyIssuerUndParams;
		this.clientStratUndParamsSbeEncoder = this.clientUpdateFbsEncoder::encodeStrategyUndParams;
		this.clientRiskStateSbeEncoder = this.clientUpdateFbsEncoder::encodeRiskState;
		this.clientIssuerSbeEncoder = this.clientUpdateFbsEncoder::encodeIssuer;
		this.clientPositionSbeEncoder = this.clientUpdateFbsEncoder::encodePosition;
		this.clientEventSbeEncoder = this.clientUpdateFbsEncoder::encodeEvent;
		this.clientTradeSbeEncoder = this.clientUpdateFbsEncoder::encodeTrade;
		this.clientStrategyParamsSbeEncoder = this.clientUpdateFbsEncoder::encodeStrategyParams;
		this.clientStrategySwitchSbeEncoder = this.clientUpdateFbsEncoder::encodeStrategySwitch;
		this.clientMarketStatsSbeEncoder = this.clientUpdateFbsEncoder::encodeMarketStats;
		this.clientOrderBookSnapshotSbeEncoder = this.clientUpdateFbsEncoder::encodeOrderBookSnapshot;
		this.clientAggregateOrderBookUpdateSbeEncoder = this.clientUpdateFbsEncoder::encodeAggregateOrderBookUpdate;
		this.clientScoreBoardSbeEncoder = this.clientUpdateFbsEncoder::encodeScoreBoard;
		this.clientNoteSbeEncoder = this.clientUpdateFbsEncoder::encodeNote;
		this.clientChartDataSbeEncoder = this.clientUpdateFbsEncoder::encodeChartData;
	}
	
	ConcurrentHashMap<InetSocketAddress, WebSocketChannel> activeChannels(){
		return this.activeChannels;
	}
	
	private static final String CHANNEL_INFO_KEY = "channelInfo";
	private static final String LAST_LOG_TIME = "lastLogTime";
	private static final int EXPECT_ALLOCATED_BUFFERS = 4096;
	
	private static class ChannelInfo{
		private final WebSocketChannel channel;
		private final AtomicInteger numOutstandingBuffers = new AtomicInteger(0);
		private final ConcurrentHashMap<Long, FreePooledByteBufferCallback> allocatedBuffers = new ConcurrentHashMap<>(EXPECT_ALLOCATED_BUFFERS);
		private final AtomicLong bufferOverflowAlertSentTime = new AtomicLong(Long.MIN_VALUE);
		
		static ChannelInfo of(WebSocketChannel channel){
			return new ChannelInfo(channel);
		}
		ChannelInfo(WebSocketChannel channel){
			this.channel = channel;
		}
		void clear(){
			this.allocatedBuffers.clear();
		}
	}
	
	public void start(){
		if (!started.compareAndSet(false, true)){
			LOG.warn("Dashboard web service has already started");
			return;
		}
		LOG.info("Starting dashboard web service - listening websocket messages on [localhost, port:{}, wsPath:{}]", port, wsPath);
		Builder builder = Undertow.builder();
		
		if (numCoreThreads.isPresent()){
			builder.setWorkerThreads(numCoreThreads.get());
			LOG.info("Web socket config [numCoreThreads:{}]", numCoreThreads.get()); 
		}
		
		if (numIoThreads.isPresent()){
			builder.setIoThreads(numIoThreads.get());
			LOG.info("Web socket config [numIoThreads:{}]", numIoThreads.get());
		}
		
		if (bufferSizeInBytes.isPresent()){
			builder.setBufferSize(bufferSizeInBytes.get());
			LOG.info("Web socket config [bufferSizeInBytes:{}]", bufferSizeInBytes.get());
		}
		
		if (requestTimeout.isPresent()){
			builder.setSocketOption(UndertowOptions.NO_REQUEST_TIMEOUT, (int)requestTimeout.get().toMillis() * 1000);
			LOG.info("Web socket config [requestTimeoutInMill:{}]", requestTimeout.get().toMillis());
		}
		
		builder.addHttpListener(port, "0.0.0.0")
				.setHandler(path()
//						.addPrefixPath("/lunar", new HttpHandler(){
//							@Override
//							public void handleRequest(HttpServerExchange exchange) throws Exception {
//								if (exchange.isInIoThread()){
//									exchange.dispatch(this);
//									return;
//								}
//								exchange.startBlocking( );
//								exchange.getRequestReceiver().receiveFullBytes(new FullBytesCallback() {
//									@Override
//									public void handle(HttpServerExchange exchange, byte[] message) {
//										LOG.info("Request: {}", new String(message));
//									}
//								});
//							}
//						})
						.addPrefixPath("/" + wsPath, websocket(new WebSocketConnectionCallback() {
							
							@Override
							public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
								LOG.info("Connection established on with [channelPeer:{}, channelLocal:{}, bufferSize:{}]", channel.getPeerAddress(), channel.getLocalAddress(), channel.getBufferPool().getBufferSize());
								
								if (!activeChannels.containsKey(channel.getSourceAddress())){
									channel.setAttribute(CHANNEL_INFO_KEY, ChannelInfo.of(channel));
									channel.setAttribute(LAST_LOG_TIME, Long.MIN_VALUE);
									if (activeChannels.put(channel.getSourceAddress(), channel) != null){
										LOG.warn("Already has a reference to this channel [channelPeer:{}, channelLocal:{}]", channel.getPeerAddress(), channel.getLocalAddress());
									}									
								}
								
								channel.addCloseTask(new ChannelListener<WebSocketChannel>() {
									@Override
									public void handleEvent(WebSocketChannel channel) {
										removeChannel(channel);
										LOG.info("Got called in addCloseTask [channelPeer:{}, closeCode:{}, closeReason:{}]", channel.getPeerAddress(), channel.getCloseCode(), channel.getCloseReason());
									}
								});
								channel.getReceiveSetter().set(new AbstractReceiveListener() {
									@Override
									protected void onError(WebSocketChannel channel, Throwable error) {
										if (removeChannel(channel)){
											LOG.warn("Channel is closed due to error [channelPeer:{}, channelLocal:{}, source:{}, dest:{}, closeCode:{}, closeReason:{}]", channel.getPeerAddress(), channel.getLocalAddress(), channel.getSourceAddress(), channel.getDestinationAddress(), channel.getCloseCode(), channel.getCloseReason());
										}
										else {
											LOG.warn("Couldn't remove channel that is on error [channelPeer:{}, channelLocal:{}, closeCode:{}, closeReason:{}]", channel.getPeerAddress(), channel.getLocalAddress(), channel.getCloseCode(), channel.getCloseReason());
										}
									}
									
									@Override
									protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
										if (removeChannel(webSocketChannel)){
											LOG.info("Removed closed channel [channelPeer:{}, channelLocal:{}, closeCode:{}, closeReason:{}]", webSocketChannel.getPeerAddress(), webSocketChannel.getLocalAddress(), webSocketChannel.getCloseCode(), webSocketChannel.getCloseReason());
										}
										else {
											LOG.warn("Couldn't remove channel that is being closed [channelPeer:{}, channelLocal:{}, closeCode:{}, closeReason:{}]", webSocketChannel.getPeerAddress(), webSocketChannel.getLocalAddress(), webSocketChannel.getCloseCode(), webSocketChannel.getCloseReason());
										}
									}
									
									@Override
									protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
//										LOG.info("source:{}, destination:{}", channel.getSourceAddress(), channel.getDestinationAddress());
										try{
											Pooled<ByteBuffer[]> pooled = message.getData();
											try {
												ByteBuffer[] buffers = pooled.getResource();
												ByteBuffer buffer = toByteBuffer(buffers);
												
												MessageFbsDecoder fbsDecoder = MessageFbsDecoder.of();
												byte payloadType = fbsDecoder.init(buffer);
												switch (payloadType){
												case MessagePayloadFbs.CommandFbs:
													handleCommandFbs(channel, fbsDecoder.asCommandFbs());
													break;													
												case MessagePayloadFbs.RequestFbs:
													handleRequestFbs(channel, fbsDecoder.asRequestFbs());
													break;
												case MessagePayloadFbs.NewOrderRequestFbs:
													handleNewOrderRequestFbs(channel, fbsDecoder.asNewOrderRequestFbs());
													break;
												case MessagePayloadFbs.CancelOrderRequestFbs:
													handleCancelOrderRequestFbs(channel, fbsDecoder.asCancelOrderRequestFbs());
													break;
												default:
													LOG.warn("Received unexpected message. [from:{}, payloadType:{}]", channel.getDestinationAddress().toString(), MessagePayloadFbs.name(payloadType));
													break;
												}
											}
											finally {
												pooled.free();
											}
											
										}
										catch (Exception ex){
                                    		LOG.error("Caught exception when processing message for " + channel.getDestinationAddress().toString(), ex);
										}
									}
									
                                    @Override
                                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                                    	try {
                                    		// Please be fuxking careful.  BufferedTextMessage.getData() clears the buffer after this call
                                    		String receivedMessage = message.getData();
//                                    		LOG.debug("received {}", receivedMessage);
                                    		@SuppressWarnings("rawtypes")
                                    		LinkedTreeMap map = gson.fromJson(receivedMessage, LinkedTreeMap.class);

                                    		String type = map.get("type").toString();
                                    		if (type.equals("GET_SERVICE_STATUS")){
                                    			handleServiceStatusRequest(channel);
                                    		}
                                    		else if (type.equals("HEARTBEAT")){
                                    			LOG.trace("Received heartbeat [client:{}]", channel.getSourceAddress().toString());                                    			
                                    		}
                                    		else if (type.equals("TOGGLE_SERVICE")){
                                    			LOG.info("Received command to toggle service");
                                    			handleStopCommand((int)Double.parseDouble(map.get("sinkId").toString()));
                                    			WebSockets.sendText("OK", channel, null);
                                    		}
//                                    		else if (type.equals("GET_SECURITY")){
//                                    			LOG.info("Received command to get security");
//                                    			WebSockets.sendBinary(handleGetSecurity(channel.getBufferPool().allocate().getBuffer()), channel, null);
//                                    		}
                                            else if (type.equals("GET_ISSUER")){
                                                LOG.info("Received command to get issuer");
                                                //WebSockets.sendBinary(handleGetIssuer(channel.getBufferPool().allocate().getBuffer()), channel, null);
                                            }                                    		
                                    		else if (type.equals("GET_STRATEGY_PARAMS")){
                                    			handleStrategyParamsRequest(channel);
                                    		}
                                    		else if (type.equals("PING")){
                                    			WebSockets.sendText("PONG", channel, null);
                                    		}
                                    		else {
                                    			LOG.warn("Received unexpected command: {}", map.get("type"));
                                    			WebSockets.sendText("FAILED", channel, null);
                                    		}
                                    	}
                                    	catch (Exception ex){
                                    		LOG.error("Caught exception!", ex);
                                    	}
                                    }
                                });
								channel.resumeReceives();
								
								// Send a message back
								WebSockets.sendText("CONNECTION:" + channel.getSourceAddress().getPort(), channel, null);
							}
							
						}))
						.addPrefixPath("/", resource(resourceManager).addWelcomeFiles(INDEX_FILE_NAME)));
		server = builder.build();
		server.start();
		LOG.info("Started dashboard web service");
	}
	
	public boolean started(){
		return started.get();
	}
	
	public void stop(){
		if (started.compareAndSet(true, false)){
			LOG.info("Stopping dashboard web service");
			this.clientUpdateExecutor.shutdown();
			server.stop();
			LOG.info("Stopped dashboard web service");
			return;
		}
		LOG.warn("Dashboard web service has been stopped or in the proceess of stopping");
	}
	
	private void handleStopCommand(int sinkId){
		this.coreService.handleExternalStopCommand(sinkId);
	}
	
	private void handleNewOrderRequestFbs(WebSocketChannel channel, NewOrderRequestFbs requestFbs){
		final int origClientKey = requestFbs.clientKey();
		NewOrderRequest request = NewOrderRequest.ofWithDefaults(requestFbs.clientKey(), 
				null,
				requestFbs.secSid(), 
				OrderType.get(requestFbs.orderType()), 
				requestFbs.quantity(), 
				Side.get(requestFbs.side()), 
				TimeInForce.get(requestFbs.tif()), 
				requestFbs.isAlgoOrder() ? BooleanType.TRUE : BooleanType.FALSE, 
				requestFbs.limitPrice(), 
				requestFbs.stopPrice(),
				requestFbs.timeoutAt(),
				requestFbs.retry(),
				requestFbs.portSid());

		CompletableFuture<OrderRequest> requestFuture = this.coreService.handleExternalNewOrderRequestAsync(
				WebSocketClientKey.of(requestFbs.clientKey(), channel), 
				request);

		requestFuture.whenCompleteAsync(new BiConsumer<OrderRequest, Throwable>() {
			@Override
			public void accept(OrderRequest request, Throwable throwable) {
				if (!channel.isOpen()){
					LOG.info("Web socket is already closed [ws:{}]", channel);
					return;
				}
				try {
					if (throwable != null){
						LOG.error("Caught exception", throwable);
						// TODO: request may not have all the information needed....
						ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
						if (channelInfo.numOutstandingBuffers.get() <= numOutstandingBufferPerChannelThreshold){
			                PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
							WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeOrderRequestCompletionWithClientKey(
									pooledByteBuffer.getBuffer(), request, origClientKey),
									channel,
									objectPool.get().buffer(channelInfo, pooledByteBuffer));						
						}
						else {
							broadcastEvent(channelInfo, activeChannels, clientRequestUpdateFbsEncoder);
						}
					}
					else {
						ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
						if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
			                PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
							WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeOrderRequestCompletionWithClientKey(
									pooledByteBuffer.getBuffer(), request, origClientKey),
									channel,
									objectPool.get().buffer(channelInfo, pooledByteBuffer));						
						}
						else {
							broadcastEvent(channelInfo, activeChannels, clientRequestUpdateFbsEncoder);
						}
					}					
				}
				catch (Exception ex){
					LOG.error("Caught exception when handling result from handleExternalNewOrderRequestAsync", ex);
				}
			}
		}, clientUpdateExecutor);
	}

	private void handleCancelOrderRequestFbs(WebSocketChannel channel, CancelOrderRequestFbs requestFbs){
		// TODO: How about all other fields?
		final int origClientKey = requestFbs.clientKey();
		CancelOrderRequest request = CancelOrderRequest.of(requestFbs.clientKey(), 
				null, 
				requestFbs.orderSidToBeCancelled(), 
				requestFbs.secSid(),
				Side.NULL_VAL);
		
		CompletableFuture<OrderRequest> requestFuture = this.coreService.handleExternalCancelOrderRequestAsync(
				WebSocketClientKey.of(requestFbs.clientKey(), channel), 
				request);

		requestFuture.whenCompleteAsync(new BiConsumer<OrderRequest, Throwable>() {
			@Override
			public void accept(OrderRequest request, Throwable throwable) {
				try {
					if (!channel.isOpen()){
						LOG.info("Web socket is already closed [channelPeer:{}, channelLocal:{}]", channel.getPeerAddress(), channel.getLocalAddress());
						return;
					}
					if (throwable != null){
						LOG.error("Caught exception for request", throwable);
						ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
						if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
							PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
							WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeOrderRequestCompletionWithClientKey(
									pooledByteBuffer.getBuffer(), request, origClientKey),
									channel,
									objectPool.get().buffer(channelInfo, pooledByteBuffer));
						}
						else {
							broadcastEvent(channelInfo, activeChannels, clientRequestUpdateFbsEncoder);
						}
					}
					else {
						ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
						if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
							PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
							WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeOrderRequestCompletionWithClientKey(
									pooledByteBuffer.getBuffer(), request, origClientKey),
									channel,
									objectPool.get().buffer(channelInfo, pooledByteBuffer));						
						}
						else {
							broadcastEvent(channelInfo, activeChannels, clientRequestUpdateFbsEncoder);
						}
					}
				}
				catch (Exception ex){
					LOG.error("Caught exception when handling result from handleExternalCancelOrderRequestAsync", ex);
				}
			}
		}, clientUpdateExecutor);		
	}

//	private WebSocketChannel blockedChannel = null;
	
	private void handleCommandFbs(WebSocketChannel channel, CommandFbs commandFbs){
		int parameterCount = commandFbs.parametersLength();
		List<Parameter> parameters = Parameter.NULL_LIST;
		if (parameterCount > 0){
			parameters = new ArrayList<Parameter>(parameterCount);
			for (int i = 0; i < parameterCount; i++){
				ParameterFbs parameterFbs = commandFbs.parameters(i);
				byte parameterType = parameterFbs.parameterType();
				Parameter parameter = null;
				if (parameterFbs.parameterValue() == null){
					parameter = Parameter.of(ParameterType.get(parameterType), parameterFbs.parameterValueLong());
				}
				else{
					parameter = Parameter.of(ParameterType.get(parameterType), parameterFbs.parameterValue());
				}
				parameters.add(parameter);
			}
		}
		final int origClientKey = commandFbs.clientKey();
		Command command = Command.of(ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				commandFbs.clientKey(), 
				CommandType.get(commandFbs.commandType()),
				parameters,
				BooleanType.get(commandFbs.toSend()));
		
		// Need to create a new instance of Request from requestFbs for passing into this
		// asynchronous method.
		CompletableFuture<Command> commandFuture = this.coreService.handleExternalCommandAsync(
				WebSocketClientKey.of(origClientKey, channel), 
				command);

		commandFuture.whenCompleteAsync(new BiConsumer<Command, Throwable>() {
			@Override
			public void accept(Command command, Throwable throwable) {
				if (!channel.isOpen()){
					LOG.info("Web socket is already closed [ws:{}]", channel);
					return;
				}
				if (throwable != null || command.ackType() != CommandAckType.OK){
					WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeCommandAck(channel.getBufferPool().allocate().getBuffer(), 
							origClientKey, 
							CommandAckType.FAILED,
							command.commandType()), 
							channel,
							LogWebSocketCallbackWithClientKey.of("command ack", origClientKey));
				}
				else {
					if (command.ackType() == CommandAckType.OK){
						WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeCommandAck(channel.getBufferPool().allocate().getBuffer(), 
								origClientKey,
								command.ackType(),
								command.commandType()),
								channel,
								LogWebSocketCallbackWithClientKey.of("command ack", origClientKey));
					}
				}
			}
		}, clientUpdateExecutor);
	}
	
	private void handleRequestFbs(WebSocketChannel channel, RequestFbs requestFbs){
		// Need to create a new instance of Request from requestFbs for passing into this
		// asynchronous method.
		int parameterCount = requestFbs.parametersLength();
		ImmutableListMultimap.Builder<ParameterType, Parameter> builder = ImmutableListMultimap.<ParameterType, Parameter>builder();
		if (parameterCount > 0){
			for (int i = 0; i < parameterCount; i++){
				ParameterFbs parameterFbs = requestFbs.parameters(i);
				byte parameterType = parameterFbs.parameterType();
				Parameter parameter = null;
				if (parameterFbs.parameterValue() == null){
					parameter = Parameter.of(ParameterType.get(parameterType), parameterFbs.parameterValueLong());
				}
				else{
					parameter = Parameter.of(ParameterType.get(parameterType), parameterFbs.parameterValue(), true);
				}
				builder.put(parameter.type(), parameter);
			}
		}
		final int origClientKey = requestFbs.clientKey();
		Request request = Request.createAndExtractEntityFromParameters(ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				RequestType.get(requestFbs.requestType()), 
				builder.build(), 
				requestFbs.toSend() ? BooleanType.TRUE : BooleanType.FALSE)
				.clientKey(origClientKey);
		
		CompletableFuture<Request> requestFuture = this.coreService.handleExternalRequestAsync(
				WebSocketClientKey.of(requestFbs.clientKey(), channel), 
				request);

		requestFuture.whenCompleteAsync((r, throwable) -> {
				if (!channel.isOpen()){
					LOG.info("Web socket is already closed [ws:{}]", channel);
					return;
				}
				try{
					ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
					if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){					
		                PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
						if (throwable != null){
							WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeResponse(pooledByteBuffer.getBuffer(), origClientKey, true, ResultTypeFbs.FAILED), 
									channel,
									objectPool.get().buffer(channelInfo, pooledByteBuffer));
						}
						else {
							// If there is no problem with the request, response will be delivered back to the client via handleResponse,
							// therefore we need to send a response back to client explicitly to 'complete a request'
							if (r.resultType() != ResultType.OK){
								WebSockets.sendBinary(clientRequestUpdateFbsEncoder.encodeResponse(pooledByteBuffer.getBuffer(), 
										origClientKey,
										true, 
										r.resultType().value()),
										channel,
										objectPool.get().buffer(channelInfo, pooledByteBuffer));
							}
						}
					}
					else {
						broadcastEvent(channelInfo, activeChannels, clientRequestUpdateFbsEncoder);
					}					
				}
				catch (Exception ex){
					LOG.error("Caught exception when handling result from handleRequestFbs", ex);
				}
			}
		, clientUpdateExecutor);
	}
	
	private static class LogWebSocketCallbackWithClientKey extends LogWebSocketCallback {
		private final int clientKey;
		
		static LogWebSocketCallbackWithClientKey of(String action, int clientKey){
			return new LogWebSocketCallbackWithClientKey(action, clientKey);
		}
		
		LogWebSocketCallbackWithClientKey(String action, int clientKey){
			super(action);
			this.clientKey = clientKey;
		}
		
		@Override
		public void complete(WebSocketChannel channel, Void context) {
			LOG.trace("Sent {} back to client successfully [clientKey:{}, channelPeer:{}, channelLocal:{}]", action, clientKey, channel.getPeerAddress(), channel.getLocalAddress());
		}

		@Override
		public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
			LOG.error("Could not send {} back to client [clientKey:{}, channelPeer:{}, channelLocal:{}]", action, clientKey, channel.getPeerAddress(), channel.getLocalAddress());
		}
	}
	
	private final ObjectPool<FreePooledByteBufferCallback> objectPool;
	private final ObjectFactory<FreePooledByteBufferCallback> objectFactory;
	
	private static final AtomicLong CALLBACK_ID_SEQ = new AtomicLong();
	private static final long FREQ_IN_NS = TimeUnit.SECONDS.toNanos(10L);
	private static final long CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS = TimeUnit.SECONDS.toNanos(10L);
	
	private static class FreePooledByteBufferCallback implements WebSocketCallback<Void>{
		private final ObjectPool<FreePooledByteBufferCallback> objectPool;
		private long id;
		private PooledByteBuffer buffer;
		private ChannelInfo channelInfo;

		static FreePooledByteBufferCallback of(ObjectPool<FreePooledByteBufferCallback> objectPool){
			return new FreePooledByteBufferCallback(objectPool);
		}
		
		FreePooledByteBufferCallback(ObjectPool<FreePooledByteBufferCallback> objectPool){
			this.objectPool = objectPool;
		}
		
		public FreePooledByteBufferCallback buffer(ChannelInfo channelInfo, PooledByteBuffer buffer){
			this.id = CALLBACK_ID_SEQ.getAndIncrement();
			this.buffer = buffer;
			this.channelInfo = channelInfo;			
			this.channelInfo.numOutstandingBuffers.incrementAndGet();
			this.channelInfo.allocatedBuffers.put(this.id, this);
			return this;
		}
		
		@Override
		public void complete(WebSocketChannel channel, Void context) {
			free();		
		}

		@Override
		public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
			free();
		}
		
		public void free(){
			if (this.buffer != null){
				this.buffer.close();
				this.buffer = null;				
				this.channelInfo.allocatedBuffers.remove(this.id);
				this.channelInfo.numOutstandingBuffers.decrementAndGet();
				this.channelInfo = null;
				this.objectPool.free(this);
			}			
		}
	}
	
	private static class LogWebSocketCallback implements WebSocketCallback<Void>{
		protected final String action;
		
		@SuppressWarnings("unused")
		static LogWebSocketCallback of(String action){
			return new LogWebSocketCallback(action);
		}
		
		LogWebSocketCallback(String action){
			this.action = action;
		}
		
		@Override
		public void complete(WebSocketChannel channel, Void context) {
			LOG.trace("Sent {} back to client successfully [channelPeer:{}, channelLocal:{}]", action, channel.getPeerAddress(), channel.getLocalAddress());
		}

		@Override
		public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
			LOG.error("Could not send {} back to client [channelPeer:{}, channelLocal:{}]", action, channel.getPeerAddress(), channel.getLocalAddress());
		}
	}

	private static boolean checkChannel(WebSocketChannel channel){
		boolean valid = true;
		if (!channel.isOpen()){
			Long lastLogTime = (Long)channel.getAttribute(LAST_LOG_TIME);
			long currentTime = System.nanoTime();
			if (lastLogTime + CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS < currentTime){
				channel.setAttribute(LAST_LOG_TIME, System.nanoTime());
				LOG.error("Web socket is not open. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + "]");
			}
			valid = false;
//			throw new IllegalStateException("Web socket is not open. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + "]");
		}
		else if (channel.isCloseFrameReceived()){
			Long lastLogTime = (Long)channel.getAttribute(LAST_LOG_TIME);
			long currentTime = System.nanoTime();
			if (lastLogTime + CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS < currentTime){
				channel.setAttribute(LAST_LOG_TIME, System.nanoTime());
				LOG.error("Web socket received Close Frame. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + ", closeCode:" + channel.getCloseCode() + ", reason:" + channel.getCloseReason() + "]");
			}
			valid = false;
//			throw new IllegalStateException("Web socket received Close Frame. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + ", closeCode:" + channel.getCloseCode() + ", reason:" + channel.getCloseReason() + "]");
		}
		else if (channel.isCloseFrameSent()){
			Long lastLogTime = (Long)channel.getAttribute(LAST_LOG_TIME);
			long currentTime = System.nanoTime();
			if (lastLogTime + CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS < currentTime){
				channel.setAttribute(LAST_LOG_TIME, System.nanoTime());
				LOG.error("Web socket sent Close Frame. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + ", closeCode:" + channel.getCloseCode() + ", reason:" + channel.getCloseReason() + "]");
			}
			valid = false;
//			throw new IllegalStateException("Web socket sent Close Frame. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + ", closeCode:" + channel.getCloseCode() + ", reason:" + channel.getCloseReason() + "]");
		}
		return valid;
	}
	
	private boolean removeChannel(WebSocketChannel channel){
		boolean result = (activeChannels.remove(channel.getSourceAddress()) != null);
		if (result){
			ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
			int count = 0;
			for (FreePooledByteBufferCallback callback : channelInfo.allocatedBuffers.values()){
				callback.free();
				count++;
			}
			channelInfo.clear();
			LOG.info("Clear buffer from removed channel [channel:{}, numBufferCleared:{}]", channel.getSourceAddress().toString(), count);			
		}
		return result;
	}
	
	private void removeBrokenChannels(){
		// This can filter out majority of calls
		if (brokenChannels.isEmpty()){
			return;
		}
		WebSocketChannel[] channels = brokenChannels.toArray(new WebSocketChannel[0]);
		brokenChannels.clear();
		
		// In worst scenario, multiple threads try to remove same channel from activeChannels.  That's fine too.
		for (int i = 0; i < channels.length; i++){
			if (removeChannel(channels[i])){
				LOG.info("Removed broken channel from list of active subscription channels [removedChannel: peer:{}, local:{}]", channels[i].getPeerAddress(), channels[i].getLocalAddress());
			}
		}
	}
	
	private void broadcastEvent(ChannelInfo overflownChannelInfo, ConcurrentHashMap<InetSocketAddress, WebSocketChannel> activeChannels, MessageFbsEncoder fbsEncoder){
		try {
			long currentTime = System.nanoTime();

			// Broadcast message if it hasn't been broadcast for the past 10 seconds
			long prevSentTime = overflownChannelInfo.bufferOverflowAlertSentTime.get();
			if (prevSentTime + FREQ_IN_NS < currentTime){
				if (!overflownChannelInfo.bufferOverflowAlertSentTime.compareAndSet(prevSentTime, currentTime)){
					return;
				}

				// Send event to overflown channel
				PooledByteBuffer pooledByteBuffer = overflownChannelInfo.channel.getBufferPool().allocate();
				String description = "Could not send message to " + overflownChannelInfo.channel.getSourceAddress().toString();

				WebSockets.sendBinary(
						encodeEventFbs(pooledByteBuffer.getBuffer(), fbsEncoder, 
								coreService.sinkId, 
								currentTime,
								EventCategoryFbs.CORE,
								EventLevelFbs.CRITICAL,
								EventTypeFbs.CONNECTION,
								description),
						overflownChannelInfo.channel,
						objectPool.get().buffer(overflownChannelInfo, pooledByteBuffer));

				// Broadcast message to everyone else
				for (WebSocketChannel individualChannel : this.activeChannels.values()){
					if (individualChannel == overflownChannelInfo.channel){
						continue;
					}
					if (checkChannel(individualChannel)){
						ChannelInfo individualChannelInfo = (ChannelInfo)individualChannel.getAttribute(CHANNEL_INFO_KEY);

						// Broadcast to other only if other is healthy
						if (individualChannelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
							PooledByteBuffer individualPooledByteBuffer = individualChannel.getBufferPool().allocate();
							WebSockets.sendBinary(
									encodeEventFbs(individualPooledByteBuffer.getBuffer(), fbsEncoder, 
											coreService.sinkId, 
											currentTime,
											EventCategoryFbs.CORE,
											EventLevelFbs.CRITICAL,
											EventTypeFbs.CONNECTION,
											description),
									individualChannel,
									objectPool.get().buffer(individualChannelInfo, pooledByteBuffer));
						}
					}
				}
			}
		}
		catch (Exception e){
			LOG.error("Caught exception when broadcasting event", e);
		}
	}
	
	private <T> void sendMessageToActiveChannel(MessageHeaderDecoder header, T decoder, SbeHandler<T> encoder){
//		if (System.currentTimeMillis() > reportedTime + TimeUnit.SECONDS.toMillis(2)){
//			LOG.info("Number of activeChannels [{}]", this.activeChannels.size());
//			reportedTime = System.currentTimeMillis();
//			
//			if (!this.activeChannels.isEmpty()){
//				for (Entry<InetSocketAddress, WebSocketChannel> entry : this.activeChannels.entrySet()){
//					LOG.info("[inetAddress:{}]", entry.getKey().toString());
//					LOG.info("[destAddress:{}]", entry.getValue().getDestinationAddress().toString());
//					break;
//				}
//			}
//		}
        for (WebSocketChannel channel : this.activeChannels.values()){
            try {
            	if (checkChannel(channel)){
            		// To use this asynchronous send, we must not reuse the same byte buffer.
            		// Doing so will cause data corruption
            		ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
            		if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
            			//					if (channel != blockedChannel){
            			PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
            			WebSockets.sendBinary(
            					encoder.apply(pooledByteBuffer.getBuffer(), header, decoder),
            					channel,
            					objectPool.get().buffer(channelInfo, pooledByteBuffer));
            			//					}
            			//					else {
            			// This is some code to artificially bump up 'created' count
            			//						PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
            			//						objectPool.get().buffer(channelInfo, pooledByteBuffer);
            			//						WebSockets.sendBinary(
            			//		                		encoder.apply(pooledByteBuffer.getBuffer(), decoder),
            			//		                        channel,
            			//		                        null);
            			//					}
            		}
            		else {
            			broadcastEvent(channelInfo, activeChannels, clientUpdateFbsEncoder);
            		}
            	}
            	else {
            		brokenChannels.add(channel);            		
            	}
            }
            catch (Exception ex){
                LOG.error("Caught exception for sending " + decoder.getClass().getName() + " back [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + "]", ex);
                brokenChannels.add(channel);
            }
        }
        removeBrokenChannels();
	}
	public void handleAggregateOrderBookUpdate(MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientAggregateOrderBookUpdateSbeEncoder); }
	public void handleOrderBookSnapshot(MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientOrderBookSnapshotSbeEncoder); }
    public void handleOrder(MessageHeaderDecoder header, OrderSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientOrderSbeEncoder); }
    public void handleTrade(MessageHeaderDecoder header, TradeSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientTradeSbeEncoder); }    
    public void handleRiskState(MessageHeaderDecoder header, RiskStateSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientRiskStateSbeEncoder); }    
    public void handleServiceStatus(MessageHeaderDecoder header, ServiceStatusSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientServiceStatusSbeEncoder); }
	public void handleSecurity(MessageHeaderDecoder header, ClientKey clientKey, SecuritySbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientSecuritySbeEncoder); }
	public void handleIssuer(MessageHeaderDecoder header, ClientKey clientKey, IssuerSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientIssuerSbeEncoder); }
	public void handleStrategyUndParams(MessageHeaderDecoder header, StrategyUndParamsSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientStratUndParamsSbeEncoder); }
    public void handleStrategyIssuerParams(MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientStratIssuerParamsSbeEncoder); }
    public void handleStrategyIssuerUndParams(MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientStratIssuerUndParamsSbeEncoder); }
    public void handleStrategyWrtParams(MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientStratWrtParamsSbeEncoder); }
    public void handlePosition(MessageHeaderDecoder header, PositionSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientPositionSbeEncoder); }
	public void handleEvent(MessageHeaderDecoder header, EventSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientEventSbeEncoder); }
    public void handleStrategyParams(MessageHeaderDecoder header, StrategyParamsSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientStrategyParamsSbeEncoder); }
    public void handleStrategySwitch(MessageHeaderDecoder header, StrategySwitchSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientStrategySwitchSbeEncoder); }
    public void handleMarketStats(MessageHeaderDecoder header, MarketStatsSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientMarketStatsSbeEncoder); }
    public void handleScoreBoard(MessageHeaderDecoder header, ScoreBoardSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientScoreBoardSbeEncoder); }
    public void handleNote(MessageHeaderDecoder header, NoteSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientNoteSbeEncoder); }
    public void handleChartData(MessageHeaderDecoder header, ChartDataSbeDecoder sbe){ sendMessageToActiveChannel(header, sbe, clientChartDataSbeEncoder); }

    public void handleServiceStatusesAsResponse(ClientKey clientKey, ObjectCollection<ServiceStatus> statuses){
		WebSocketClientKey clientKeyObject = (WebSocketClientKey)clientKey;
		WebSocketChannel channel = clientKeyObject.webSocketChannel();
		if (!channel.isOpen()){
			LOG.error("Web socket is not open.  Cannot send service status over. [channelPeer:{}, channelLocal:{}, source:{}, dest:{}]", channel.getPeerAddress(), channel.getLocalAddress(), channel.getSourceAddress(), channel.getDestinationAddress());
			return;
		}
		else if (channel.isCloseFrameReceived()){
			LOG.error("Web socket has received Close Frame.  Cannot send service status over. [channelPeer:{}, channelLocal:{}, source:{}, dest:{}, reason:{}]", 
					channel.getPeerAddress(), channel.getLocalAddress(),
					channel.getSourceAddress(), 
					channel.getDestinationAddress(), 
					channel.getCloseReason());
			return;
		}
		else if (channel.isCloseFrameSent()){
			LOG.info("Web socket has sent Close Frame.  Cannot send service status over. [channelPeer:{}, channelLocal:{}, source:{}, dest:{}]", channel.getPeerAddress(), channel.getLocalAddress(), channel.getSourceAddress(), channel.getDestinationAddress());
			return;
		}
    	for (ServiceStatus entry : statuses){
			ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
			if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
	            PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
	    		WebSockets.sendBinary(clientUpdateFbsEncoder.encodeResponse(pooledByteBuffer.getBuffer(),
	    				clientKey.key(),
	    				false,
	    				ResultTypeFbs.OK,
	    				entry),
	    				channel,
	    				objectPool.get().buffer(channelInfo, pooledByteBuffer));				
			}
			else {
				broadcastEvent(channelInfo, activeChannels, clientUpdateFbsEncoder);
			}
    	}
    	WebSockets.sendBinary(clientUpdateFbsEncoder.encodeResponse(channel.getBufferPool().allocate().getBuffer(), clientKey.key(), true, ResultTypeFbs.OK),
    			channel,
    			wsServiceStatusCallBack);
    }
    
	public void handleResponse(ClientKey clientKey, DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder sbe){
		WebSocketClientKey clientKeyObject = (WebSocketClientKey)clientKey;
		WebSocketChannel channel = clientKeyObject.webSocketChannel();
		// Send security back to client
		try {
			if (checkChannel(channel)){
				// To use this asynchronous send, we must not reuse the same byte buffer.
				// Doing so will cause data corruption
				if (sbe.isLast() == BooleanType.TRUE){
					LOG.debug("Send response [isLast:true, clientKey:{}, resultType:{}]", sbe.clientKey(), sbe.resultType().name());
				}
				ChannelInfo channelInfo = (ChannelInfo)channel.getAttribute(CHANNEL_INFO_KEY);
				if (channelInfo.numOutstandingBuffers.get() <= NUM_OUTSTANDING_BUFFER_THRESHOLD){
					//            	if (channel != blockedChannel){
					PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
					WebSockets.sendBinary(
							clientUpdateFbsEncoder.encodeResponseWithClientKey(pooledByteBuffer.getBuffer(), 
									buffer, 
									offset + header.encodedLength(),
									header, 
									sbe,
									clientKeyObject.key()),
							channel,
							objectPool.get().buffer(channelInfo, pooledByteBuffer));
					//            	}
					//            	else {
					//                	PooledByteBuffer pooledByteBuffer = channel.getBufferPool().allocate();
					//                	objectPool.get().buffer(channelInfo, pooledByteBuffer);
					//                	WebSockets.sendBinary(
					//                			clientUpdateFbsEncoder.encodeResponseWithClientKey(pooledByteBuffer.getBuffer(), 
					//                					buffer, 
					//                					offset + header.encodedLength(),
					//                					sbe,
					//                					clientKeyObject.key()),
					//                			channel,
					//                			null
					//                			);
					//            		
					//            	}
				}
				else {
					broadcastEvent(channelInfo, activeChannels, clientUpdateFbsEncoder);
				}
			}
			else {
				brokenChannels.add(channel);
			}
		}
		catch (Exception ex){
			LOG.error("Caught exception for sending response back [clientKey:" + clientKey + "]", ex);
			brokenChannels.add(channel);
		}
		removeBrokenChannels();
	}

	private WebSocketCallback<Void> wsServiceStatusCallBack = new WebSocketCallback<Void>() {
		@Override
		public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
			LOG.error("onError for sending service status back", throwable);
		}
		@Override
		public void complete(WebSocketChannel channel, Void context) {
			// TODO Think of something more meaningful to log here
			LOG.debug("Sent service status back to websocket successfully");
		}
	};
	    
	private void handleServiceStatusRequest(WebSocketChannel channel){
		LOG.info("Received request to get service status from {}", channel.getUrl());
		for (ServiceStatus status : coreService.serviceStatuses()){
			JsonElement element = gson.toJsonTree(status);
			JsonObject object = new JsonObject();
			object.add(TemplateType.SERVICE_STATUS.name(), element);
			String output = gson.toJson(object);
			WebSockets.sendText(output, channel, null);
			LOG.debug("Sent {} to client as object", output);
		}		
	}
	
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final SecuritySbeEncoder sbeEncoder = new SecuritySbeEncoder();
	private final SecuritySbeDecoder sbeDecoder = new SecuritySbeDecoder();
	private final UnsafeBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));

//	private ByteBuffer handleGetSecurity(ByteBuffer byteBuffer){
//		
//		long refSid = 12345;
//		String refCode = "MAX!";
//		SecurityType refType = SecurityType.STOCK;
//		int refExchangeSid = 2;
//		long refUndSecSid = 56789;
//		PutOrCall refPutOrCall = PutOrCall.CALL;
//		OptionStyle refStyle = OptionStyle.ASIAN;
//		int refStrikePrice = 123456;
//		int refConvRatio = 1000;
//		LocalDate refMaturity = LocalDate.of(2016, 3, 31);
//		int refIssuerSid = 1;
//		int refLotSize = 10000;
//		boolean refIsAlgo = true;
//		
//		Security security = Security.of(refSid, refType, refCode, refExchangeSid, refUndSecSid, Optional.of(refMaturity), ServiceConstant.NULL_LISTED_DATE, refPutOrCall, refStyle, refStrikePrice, refConvRatio, refIssuerSid, refLotSize, refIsAlgo, SpreadTableBuilder.get(refType));
//		SecuritySender.encodeSecurityOnly(buffer, 0, stringBuffer, sbeEncoder, security);
//		sbeDecoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);
//		
//		return clientUpdateFbsEncoder.encodeSecurity(byteBuffer, sbeDecoder);
//	}
	
	/**
	 * Change array of buffer into a byte array
	 * @param payload
	 * @return
	 */
	protected static byte[] toArray(ByteBuffer... payload) {
		if (payload.length == 1) {
			ByteBuffer buf = payload[0];
			if (buf.hasArray() && buf.arrayOffset() == 0 && buf.position() == 0) {
				return buf.array();
			}
		}
		int size = (int) Buffers.remaining(payload);
		byte[] data  = new byte[size];
		
		int offset = 0;
		for (ByteBuffer buf : payload) {
			int length = buf.remaining();
			buf.get(data, offset, length);
			offset += length;
		}
		return data;
	}

	protected static ByteBuffer toByteBuffer(ByteBuffer... payload) {
		if (payload.length == 1) {
//			LOG.debug("Has one buffer only");
			return payload[0];
		}
		int size = (int) Buffers.remaining(payload);
		byte[] data  = new byte[size];
		
		int offset = 0;
		for (ByteBuffer buf : payload) {
			int length = buf.remaining();
			buf.get(data, offset, length);
			offset += length;
		}
		return ByteBuffer.wrap(data);
	}
	
	private void handleStrategyParamsRequest(WebSocketChannel channel){
		LOG.info("Received request to get strategy parameters from {}", channel.getUrl());
//	    String s = "A:B:C:D";
//		JsonElement element = gson.toJsonTree(s);
		JsonObject object = new JsonObject();
//		object.add(TemplateType.StrategyParameters.name(), element); // Change the template type
		String output = gson.toJson(object);
		WebSockets.sendText(output, channel, null);
		LOG.debug("Sent {} to client as object", output);		
	}	
}

