package com.lunar.service;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

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
import com.google.gson.internal.LinkedTreeMap;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.DashboardStandAloneWebServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ConcurrentObjectPool;
import com.lunar.core.ObjectFactory;
import com.lunar.core.ObjectPool;
import com.lunar.core.ServiceStatusTracker.AggregatedServiceStatusChangeHandler;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.StrategySwitchDecoder;
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
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;
import com.lunar.order.OrderRequestType;
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

public class DashboardStandAloneWebService implements ServiceLifecycleAware {
	public static interface SbeHandler<T> {
		ByteBuffer apply(ByteBuffer buffer, MessageHeaderDecoder header, T decoder);
	}
	
	static final Logger LOG = LogManager.getLogger(DashboardStandAloneWebService.class);
	private static final String INDEX_FILE_NAME = "index.html";
	private static final int NUM_CLIENT_WEBSOCKET_UPDATE_SENDER_THREADS = 1;
	protected static final int NUM_OUTSTANDING_BUFFER_THRESHOLD = 20000;
	private static final AtomicLong CALLBACK_ID_SEQ = new AtomicLong();
	private static final long FREQ_IN_NS = TimeUnit.SECONDS.toNanos(10L);
	private static final long CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS = TimeUnit.SECONDS.toNanos(10L);
	
	private LunarService messageService;
	private final Messenger messenger;
	private final String name;
	
	private Undertow server;
	private final String wsPath;
	private final int port;
	private final int dashboardListeningPort;
	private final String dashboardListeningUrl;
	private final Optional<Integer> numIoThreads;
	private final Optional<Integer> numCoreThreads;
	private final Optional<Duration> requestTimeout;
	private final Optional<Integer> bufferSizeInBytes; 
	private final int numOutstandingBufferPerChannelThreshold;
	private final ClassPathResourceManager resourceManager;
	private final Gson gson;
	private final ExecutorService clientUpdateExecutor;
	private final ConcurrentHashMap<InetSocketAddress, WebSocketChannel> activeChannels;
	private final AtomicBoolean started;
	private final CopyOnWriteArrayList<WebSocketChannel> brokenChannels;
	private final ObjectPool<FreePooledByteBufferCallback> objectPool;
	private final ObjectFactory<FreePooledByteBufferCallback> objectFactory;
	private final ExecutorService webServiceRequestExecutor;
	private final MutableDirectBuffer webServiceRequestBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final MutableDirectBuffer timerBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));

	private Duration commonPeriodicTaskFreq;
    private final AtomicReference<TimeoutHandlerTimerTask> commonPeriodicTask;
    private final int CLIENT_KEY_FOR_COMMON_PERIODIC_TASK = 1;
    
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
	
	private final Messenger webServiceMessenger;		
	private final ConcurrentHashMap<Integer, WebCallContext<Request>> webClientRequests;
	private final ConcurrentHashMap<Integer, WebCallContext<Command>> webClientCommands;
	private final ConcurrentHashMap<Integer, WebCallContext<OrderRequest>> webClientOrderRequests;
    private static WebCallContext<Request> NULL_REQUEST_WEB_CALL_CONTEXT = WebCallContext.of(ClientKey.NULL_INSTANCE, null, null);
	private WebCallContext<Request> prevCallContext = NULL_REQUEST_WEB_CALL_CONTEXT;
	
	/*
	 * Note: MessageFbsEncoder is not thread-safe.  Make sure that we are using one encoder per thread
	 */
	private final MessageFbsEncoder clientRequestUpdateFbsEncoder;
	private final MessageFbsEncoder clientUpdateFbsEncoder;
	private final SbeHandler<OrderSbeDecoder> clientOrderSbeEncoder; 
	@SuppressWarnings("unused")
	private final SbeHandler<SecuritySbeDecoder> clientSecuritySbeEncoder;
	private final SbeHandler<ServiceStatusSbeDecoder> clientServiceStatusSbeEncoder;
	private final SbeHandler<StrategyWrtParamsSbeDecoder> clientStratWrtParamsSbeEncoder;
	private final SbeHandler<StrategyIssuerParamsSbeDecoder> clientStratIssuerParamsSbeEncoder;
	private final SbeHandler<StrategyIssuerUndParamsSbeDecoder> clientStratIssuerUndParamsSbeEncoder;
	private final SbeHandler<StrategyUndParamsSbeDecoder> clientStratUndParamsSbeEncoder;
	private final SbeHandler<RiskStateSbeDecoder> clientRiskStateSbeEncoder;
	@SuppressWarnings("unused")
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
	
	private final ExecutorService dashboardReceiver = Executors.newSingleThreadExecutor(new NamedThreadFactory("dashboard-standalone", "dashboard-listener"));

	public static DashboardStandAloneWebService of(ServiceConfig config, LunarService messageService) {
		return new DashboardStandAloneWebService(config, messageService);
	}

	DashboardStandAloneWebService(ServiceConfig config, LunarService messageService) {
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.messenger.setClientKeySeq(2000);
		if (config instanceof DashboardStandAloneWebServiceConfig){
			DashboardStandAloneWebServiceConfig specificConfig = (DashboardStandAloneWebServiceConfig)config;
			this.port = specificConfig.port();
			this.wsPath = specificConfig.wsPath();
			if (!specificConfig.tcpListeningPort().isPresent()){
				throw new IllegalArgumentException("Service " + this.name + " expects a tcpListeningPort in config");					
			}
			this.dashboardListeningPort = specificConfig.tcpListeningPort().get();
			if (!specificConfig.tcpListeningUrl().isPresent()){
				throw new IllegalArgumentException("Service " + this.name + " expects a tcpListeningUrl in config");	
			}
			this.dashboardListeningUrl = specificConfig.tcpListeningUrl().get();
			this.resourceManager = new ClassPathResourceManager(
					DashboardStandAloneWebService.class.getClassLoader(), 
					"" /* DashboardUndertowWebService.class.getPackage()*/);
			
			this.gson = new Gson();
			this.clientUpdateExecutor= Executors.newFixedThreadPool(NUM_CLIENT_WEBSOCKET_UPDATE_SENDER_THREADS, new NamedThreadFactory("dashboard-sa", "client-ws-update-sender"));
			this.clientRequestUpdateFbsEncoder = MessageFbsEncoder.of();
			this.clientUpdateFbsEncoder = MessageFbsEncoder.of();
			this.activeChannels = new ConcurrentHashMap<>();
			this.brokenChannels = new CopyOnWriteArrayList<>();
			this.started = new AtomicBoolean(false);
			this.numCoreThreads = specificConfig.numCoreThreads();
			this.numIoThreads = specificConfig.numIoThreads();
			this.requestTimeout = specificConfig.requestTimeout();
			this.bufferSizeInBytes =  specificConfig.bufferSizeInBytes();
			this.numOutstandingBufferPerChannelThreshold = specificConfig.numOutstandingBufferPerChannelThreshold().orElse(NUM_OUTSTANDING_BUFFER_THRESHOLD);
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
			
			this.webClientRequests = new ConcurrentHashMap<>();
			this.webClientCommands = new ConcurrentHashMap<>();
			this.webClientOrderRequests = new ConcurrentHashMap<>();
			this.webServiceRequestExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("dashboard-sa", "request-sender"));
			this.webServiceMessenger = this.messenger.createChildMessenger();
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a DashboardServiceConfig config");
		}
		this.commonPeriodicTask = new AtomicReference<>();
		this.commonPeriodicTaskFreq = Duration.ofSeconds(10L);
	}
	
	private static final String CHANNEL_INFO_KEY = "channelInfo";
	private static final String LAST_LOG_TIME = "lastLogTime";
	private static final int EXPECT_ALLOCATED_BUFFERS = 4096;
	private static final long RECONNECT_WAIT_TIME_IN_MS = TimeUnit.SECONDS.toMillis(5);
	private static final int DASHBOARD_SINK_ID = 0;
	
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
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(aggServiceStatusHandler);
		return StateTransitionEvent.WAIT;
	}

	private final AggregatedServiceStatusChangeHandler aggServiceStatusHandler = new AggregatedServiceStatusChangeHandler() {
		
		@Override
		public void handle(boolean status) {
			if (status){
				messageService.stateEvent(StateTransitionEvent.READY);
			}
			else { // DOWN or INITIALIZING
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}
		}
	};
	
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
			builder.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, (int)requestTimeout.get().toMillis() * 1000);
			LOG.info("Web socket config [requestTimeoutInMill:{}]", requestTimeout.get().toMillis());
		}
		else {
			builder.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, -1);
			LOG.info("Web socket config - disable request timeout");
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
                                    		if (type.equals("HEARTBEAT")){
                                    			LOG.info("Received heartbeat [client:{}]", channel.getSourceAddress().toString());
                                    		}
//                                    		else if (type.equals("TOGGLE_SERVICE")){
//                                    			LOG.info("Received command to toggle service");
//                                    			handleStopCommand((int)Double.parseDouble(map.get("sinkId").toString()));
//                                    			WebSockets.sendText("OK", channel, null);
//                                    		}
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
		LOG.info("Started dashboard standalone web service");
	}
	
	public boolean webServiceStarted(){
		return started.get();
	}

	private Socket dashboardClientSocket;
	private BufferedOutputStream dashboardOutputStream;
	private final AtomicBoolean listeningForIncomingTcpData = new AtomicBoolean(false);

	private void receiveFromDashboard(){
		final MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE * 128));
		final MutableDirectBuffer copyReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE * 128 * 3));
		
		while (true){
			try {
				LOG.info("Opening socket [url:{}, port:{}]", this.dashboardListeningUrl, this.dashboardListeningPort);
				dashboardClientSocket = new Socket(this.dashboardListeningUrl, this.dashboardListeningPort);
				dashboardClientSocket.setKeepAlive(true);
				BufferedInputStream inputStream = new BufferedInputStream(dashboardClientSocket.getInputStream());
				
				dashboardOutputStream = new BufferedOutputStream(dashboardClientSocket.getOutputStream());
				
				MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
				int numBytesInCopyBuffer = 0;
				int bytesRead = 0;
				
				listeningForIncomingTcpData.set(true);
				
				while ((bytesRead = inputStream.read(wireReceiveBuffer.byteArray())) > 0){
					// LOG.info("Received data [numBytes:{}, numBytesInCopyBuffer:{}]", bytesRead, numBytesInCopyBuffer);
					// Easiest way is to copy read bytes out to another buffer
					// Once copied bytes exceed header length, read it.  If payload is also available, read it off the byte buffer.

					// Find out how to determine begin and end of data
					// Wrap bytes in DirectBuffer with MessageHeader
					// Make sure we have the whole message before decoding
					// Once enough byte, send them out
					if (numBytesInCopyBuffer == 0){
						// Consume all byteRead
						int offset = 0;
						while (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
							messageHeaderDecoder.wrap(wireReceiveBuffer, offset);
							int length = MessageHeaderDecoder.ENCODED_LENGTH + messageHeaderDecoder.payloadLength();
							if (bytesRead >= length){
								// Publish current message to self
								try {
									this.messenger.self().tryPublish(wireReceiveBuffer, offset, length);
								}
								catch (Exception e){
									LOG.error("Problem publishing message to own message queue [templateId:{}, length:{}]", messageHeaderDecoder.templateId(), length);
								}
								bytesRead -= length;
								offset += length;								
							}
							else {
								// Need to wait for next read off the wire
								break;
							}
						}
						if (bytesRead > 0){
							// Some dangling bytes, copy them from wireReceiveBuffer to copyReceiveBuffer
							copyReceiveBuffer.putBytes(numBytesInCopyBuffer, wireReceiveBuffer, offset, bytesRead);
							numBytesInCopyBuffer += bytesRead;
						}
					}
					else{
						copyReceiveBuffer.putBytes(numBytesInCopyBuffer, wireReceiveBuffer, 0, bytesRead);
						numBytesInCopyBuffer += bytesRead;
						
						int offset = 0;
						while (numBytesInCopyBuffer >= MessageHeaderDecoder.ENCODED_LENGTH){
							messageHeaderDecoder.wrap(copyReceiveBuffer, offset);
							int length = MessageHeaderDecoder.ENCODED_LENGTH + messageHeaderDecoder.payloadLength();
							if (numBytesInCopyBuffer >= length){
								// Copy current message
								try {
									this.messenger.self().tryPublish(copyReceiveBuffer, offset, length);
								}
								catch (Exception e){
									LOG.error("Problem publishing message to own message queue using copyReceiveBuffer [templateId:{}, length:{}]", messageHeaderDecoder.templateId(), length);
								}
								numBytesInCopyBuffer -= length;
								offset += length;
							}
							else {
								// Need to wait for next read off the wire
								break;
							}
						}
						// Re-align bytes to the beginning of buffer
						if (numBytesInCopyBuffer > 0){
							copyReceiveBuffer.putBytes(0, copyReceiveBuffer, offset, numBytesInCopyBuffer);
						}
					}
				}

			}
			catch (ConnectException e){
				LOG.warn("Cannot connect to Dashboard [dashboardUrl:{}, dashboardPort:{}]", this.dashboardListeningUrl, this.dashboardListeningPort);
				LOG.warn(e);
			}
			catch (UnknownHostException e){
				LOG.info("Dashboard is currently not available [dashboardUrl:{}, dashboardPort:{}]",
						this.dashboardListeningUrl, this.dashboardListeningPort);
			}
			catch (Exception e){
				LOG.error("Caught exception in dashboard receiver executor", e);
			}
			finally {
				if (dashboardClientSocket != null && !dashboardClientSocket.isClosed()){
					try {
						dashboardClientSocket.close();
						dashboardClientSocket = null;
					} 
					catch (IOException e1) {
						LOG.error("Caught exception when closing socket", e1);
					}
				}
				listeningForIncomingTcpData.set(false);
			}
			try {
				LOG.info("Reconnect in {} ms", RECONNECT_WAIT_TIME_IN_MS);
				Thread.sleep(RECONNECT_WAIT_TIME_IN_MS);
			}
			catch (Exception e){
				LOG.error("Interrupted from sleep", e);
			}
		}
	}
	
	@Override
	public StateTransitionEvent readyEnter() {
		start();
		dashboardReceiver.execute(this::receiveFromDashboard);
		
		// Since we cannot use CommandTracker, RequestTracker and OrderRequestTracker, we must 
		// unregister all events handling here to avoid confusions
		messenger.unregisterEvents();
		messenger.registerServiceStatusTracker();
		return StateTransitionEvent.ACTIVATE;
	}
	
	@Override
	public StateTransitionEvent activeEnter() {
		messenger.receiver().orderRequestCompletionHandlerList().add(orderRequestCompletionHandler);
		messenger.receiver().commandAckHandlerList().add(commandAckHandler);
		messenger.receiver().responseHandlerList().prepend(responseHandler);
		messenger.receiver().securityHandlerList().add(securityHandler);
		messenger.receiver().issuerHandlerList().add(issuerHandler);
		messenger.receiver().strategyTypeHandlerList().add(strategyTypeHandler);
		messenger.receiver().strategyParamsHandlerList().add(strategyParamsHandler);
		messenger.receiver().strategyUndParamsHandlerList().add(strategyUndParamsHandler);
		messenger.receiver().strategyIssuerParamsHandlerList().add(strategyIssuerParamsHandler);
		messenger.receiver().strategyIssuerUndParamsHandlerList().add(strategyIssuerUndParamsHandler);
		messenger.receiver().strategyWrtParamsHandlerList().add(strategyWrtParamsHandler);		
		messenger.receiver().strategySwitchHandlerList().add(strategySwitchHandler);		
		messenger.receiver().aggregateOrderBookUpdateHandlerList().add(aggregateOrderBookUpdateHandler);
		messenger.receiver().orderBookSnapshotHandlerList().add(orderBookSnapshotHandler);
		messenger.receiver().marketStatsHandlerList().add(marketStatsHandler);
		messenger.receiver().orderHandlerList().add(orderHandler);
		messenger.receiver().tradeHandlerList().add(tradeHandler);
		messenger.receiver().positionHandlerList().add(positionHandler);
		messenger.receiver().riskStateHandlerList().add(riskStateHandler);
		messenger.receiver().serviceStatusHandlerList().add(serviceStatusHandler);
		messenger.receiver().eventHandlerList().add(eventHandler);
		messenger.receiver().scoreBoardSchemaHandlerList().add(scoreBoardSchemaHandler);
		messenger.receiver().scoreBoardHandlerList().add(scoreBoardHandler);
		messenger.receiver().noteHandlerList().add(noteHandler);
		messenger.receiver().chartDataHandlerList().add(chartDataHandler);

		messenger.receiver().timerEventHandlerList().add(timerEventHandler);
		this.commonPeriodicTask.set(messenger.timerService().createTimerTask(commonPeriodicTaskTimer, "mds-common-periodic-task"));
		messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);

		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		messenger.receiver().orderRequestCompletionHandlerList().remove(orderRequestCompletionHandler);
		messenger.receiver().responseHandlerList().remove(responseHandler);
		messenger.receiver().orderHandlerList().remove(orderHandler);
		messenger.receiver().tradeHandlerList().remove(tradeHandler);
		messenger.unregisterServiceStatusTracker();
	}

	private final Handler<AggregateOrderBookUpdateSbeDecoder> aggregateOrderBookUpdateHandler = new Handler<AggregateOrderBookUpdateSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientAggregateOrderBookUpdateSbeEncoder);
		}
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, AggregateOrderBookUpdateSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
	};

	/**
	 * There are three message types that we need to ack back
	 * 1) OrderRequestCompletionSbeDecoder
	 * 2) Response
	 * 3) CommandAck
	 * 
	 * For each of these, we need to properly
	 * 1) Return the result to appropriate client (Replace client key)
	 * 		i) complete
	 * 		ii) complete exceptionally
	 * 2) Remove the context from webClientXXX
	 */
	private final Handler<OrderRequestCompletionSbeDecoder> orderRequestCompletionHandler = new Handler<OrderRequestCompletionSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder codec) {
			// Look up clientKey to see if there is any outstanding request
        	int clientKey = codec.clientKey();
			WebCallContext<OrderRequest> result = webClientOrderRequests.get(clientKey);
			if (result != null){
				result.item.orderSid(codec.orderSid());
				result.item.completionType(codec.completionType());
				if (result.item.type() == OrderRequestType.NEW){
					result.future.complete(result.item);
					webClientOrderRequests.remove(clientKey);					
				}
				else if (result.item.type() == OrderRequestType.CANCEL){
					result.future.complete(result.item);
					webClientOrderRequests.remove(clientKey);
				}
				else {
					LOG.error("Invalid order type [orderType:{}]", result.item.type().name());
				}
				return;
			}
			LOG.warn("Received command ack for a clientKey that no longer exists [senderSinkId:{}, clientKey:{}, isLast:{}]", header.senderSinkId(), clientKey);	
		}
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderRequestCompletionSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
	};
	
	/**
	 * Complete future upon receiving CommandAck
	 */
	private final Handler<CommandAckSbeDecoder> commandAckHandler = new Handler<CommandAckSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder codec) {
			// Look up clientKey to see if there is any outstanding request
        	int clientKey = codec.clientKey();
			WebCallContext<Command> result = webClientCommands.get(clientKey);
			if (result != null){
				result.item.ackType(codec.ackType());
				result.future.complete(result.item);
				webClientCommands.remove(clientKey);
				return;
			}
			LOG.warn("Received command ack for a clientKey that no longer exists [senderSinkId:{}, clientKey:{}, isLast:{}]", header.senderSinkId(), clientKey);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, CommandAckSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<ScoreBoardSbeDecoder> scoreBoardHandler = new Handler<ScoreBoardSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientScoreBoardSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, ScoreBoardSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<NoteSbeDecoder> noteHandler = new Handler<NoteSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientNoteSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, NoteSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<ChartDataSbeDecoder> chartDataHandler = new Handler<ChartDataSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ChartDataSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientChartDataSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, ChartDataSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<EventSbeDecoder> eventHandler = new Handler<EventSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientEventSbeEncoder);
        }    	
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, EventSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<PositionSbeDecoder> positionHandler = new Handler<PositionSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientPositionSbeEncoder);
        }
        
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, PositionSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<RiskStateSbeDecoder> riskStateHandler = new Handler<RiskStateSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientRiskStateSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, RiskStateSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<ServiceStatusSbeDecoder> serviceStatusHandler = new Handler<ServiceStatusSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
        	if (codec.serviceType() == ServiceType.DashboardStandAloneWebService){
        		LOG.info("Got heartbeat response back");
        		return;
        	}
        	sendMessageToActiveChannel(header, codec, clientServiceStatusSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, ServiceStatusSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder codec) {
			throw new UnsupportedOperationException("unexpected handling of security sbe [sinkId:" + messenger.self().sinkId() + ", name: " + name);
		}
		
		@Override
		public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, SecuritySbeDecoder codec) {
        	// Response will be sent transparently to webService
		}
	};

    private final Handler<IssuerSbeDecoder> issuerHandler = new Handler<IssuerSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, IssuerSbeDecoder codec) {
            throw new UnsupportedOperationException("unexpected handling of issuer sbe [sinkId:" + messenger.self().sinkId() + ", name: " + name);
        }
        
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, IssuerSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyTypeSbeDecoder> strategyTypeHandler = new Handler<StrategyTypeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyTypeSbeDecoder codec) {
            throw new UnsupportedOperationException("unexpected handling of strategy type sbe [sinkId:" + messenger.self().sinkId() + ", name: " + name);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyTypeSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<ScoreBoardSchemaSbeDecoder> scoreBoardSchemaHandler = new Handler<ScoreBoardSchemaSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSchemaSbeDecoder codec) {
            throw new UnsupportedOperationException("unexpected handling of score board schema sbe [sinkId:" + messenger.self().sinkId() + ", name: " + name);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, ScoreBoardSchemaSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyParamsSbeDecoder> strategyParamsHandler = new Handler<StrategyParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder codec) {
            sendMessageToActiveChannel(header, codec, clientStrategyParamsSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyUndParamsSbeDecoder> strategyUndParamsHandler = new Handler<StrategyUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientStratUndParamsSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyUndParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyIssuerParamsSbeDecoder> strategyIssuerParamsHandler = new Handler<StrategyIssuerParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientStratIssuerParamsSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyIssuerParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyIssuerUndParamsSbeDecoder> strategyIssuerUndParamsHandler = new Handler<StrategyIssuerUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientStratIssuerUndParamsSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyIssuerUndParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyWrtParamsSbeDecoder> strategyWrtParamsHandler = new Handler<StrategyWrtParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientStratWrtParamsSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyWrtParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<StrategySwitchSbeDecoder> strategySwitchHandler = new Handler<StrategySwitchSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder codec) {
        	LOG.info("Received strategy switch [{}]", StrategySwitchDecoder.decodeToString(codec));
        	sendMessageToActiveChannel(header, codec, clientStrategySwitchSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategySwitchSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<OrderBookSnapshotSbeDecoder> orderBookSnapshotHandler = new Handler<OrderBookSnapshotSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientOrderBookSnapshotSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderBookSnapshotSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<MarketStatsSbeDecoder> marketStatsHandler = new Handler<MarketStatsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientMarketStatsSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, MarketStatsSbeDecoder codec) {
            // Response will be sent transparently to webService
        }
    };

    
    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
	    	if (codec.timerEventType() == TimerEventType.TIMER){
	    		if (codec.clientKey() == CLIENT_KEY_FOR_COMMON_PERIODIC_TASK){
	    			commonPeriodicTask();
	    			messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
	    		}
	    	}
		}
	};
	
    private void commonPeriodicTask(){
    	LOG.trace("Common periodic task - send heartbeat to dashboard");
    	long current = this.messenger.timerService().toNanoOfDay();
		int length = this.messenger.serviceStatusSender()
				.encodeSelfServiceStatus(timerBuffer, 0, 
						DASHBOARD_SINK_ID, 
						ServiceType.DashboardStandAloneWebService, 
						ServiceStatusType.HEARTBEAT,
						current,
						current,
						current);

		try {
			LOG.info("Sending heartbeat to dashboard");
            if (this.dashboardOutputStream != null){
			    this.dashboardOutputStream.write(timerBuffer.byteArray(), 0, length);
			    this.dashboardOutputStream.flush();
            }
			LOG.info("Sent heartbeat to dashboard");
		}
		catch (IOException e) {
			LOG.error("Could not send heartbeat to dashboard");
		}
    }

    private final TimeoutHandler commonPeriodicTaskTimer = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_COMMON_PERIODIC_TASK, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(commonPeriodicTask.get(), commonPeriodicTaskFreq);
			}
		}
	};
	
    /**
     * When last response is received, complete the future
     */
	private final Handler<ResponseSbeDecoder> responseHandler = new Handler<ResponseSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder response) {
			// Look up clientKey to see if there is any outstanding request
//			LOG.info("Handling response from response handler");
			WebCallContext<Request> result;
			if (prevCallContext.clientKeyObject.key() == response.clientKey()){
				result = prevCallContext;
			}
			else {
				result = webClientRequests.get(response.clientKey());
			}
			if (result != null){
				prevCallContext = result;
				
				// Send response back to client
				handleResponse(result.clientKeyObject, buffer, offset, header, response);
				
				if (response.isLast() == BooleanType.TRUE){
					result.future.complete(result.item);
					webClientRequests.remove(response.clientKey());
				}
				
				return;
			}
			LOG.warn("Received response for a clientKey that no longer exists [senderSinkId:{}, clientKey:{}, isLast:{}]", header.senderSinkId(), response.clientKey(), response.isLast());
		}
	};

	@SuppressWarnings("unused")
	private <T> void sendMessageToSpecificChannel(T decoder, BiFunction<ByteBuffer, T, ByteBuffer> encoder){
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
            					encoder.apply(pooledByteBuffer.getBuffer(), decoder),
            					channel,
            					objectPool.get().buffer(channelInfo, pooledByteBuffer));
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
	
	private final Handler<OrderSbeDecoder> orderHandler = new Handler<OrderSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientOrderSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderSbeDecoder codec) {
        }
	};
	
    private final Handler<TradeSbeDecoder> tradeHandler = new Handler<TradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
        	sendMessageToActiveChannel(header, codec, clientTradeSbeEncoder);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, TradeSbeDecoder codec) {
        }
    };
	
	@Override
	public StateTransitionEvent stopEnter() {
		LOG.info("Stop enter dashboard standalone web service");
		messenger.receiver().commandAckHandlerList().remove(commandAckHandler);
		messenger.receiver().responseHandlerList().remove(responseHandler);
		messenger.receiver().securityHandlerList().remove(securityHandler);
        messenger.receiver().issuerHandlerList().remove(issuerHandler);
        messenger.receiver().strategyTypeHandlerList().remove(strategyTypeHandler);
        messenger.receiver().strategyParamsHandlerList().remove(strategyParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().remove(strategyUndParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().remove(strategyIssuerParamsHandler);
        messenger.receiver().strategyIssuerUndParamsHandlerList().remove(strategyIssuerUndParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().remove(strategyWrtParamsHandler);
        messenger.receiver().strategySwitchHandlerList().remove(strategySwitchHandler);		
		messenger.receiver().aggregateOrderBookUpdateHandlerList().remove(aggregateOrderBookUpdateHandler);
		messenger.receiver().orderBookSnapshotHandlerList().remove(orderBookSnapshotHandler);
		messenger.receiver().marketStatsHandlerList().remove(marketStatsHandler);
        messenger.receiver().orderHandlerList().remove(orderHandler);
        messenger.receiver().tradeHandlerList().remove(tradeHandler);
		messenger.receiver().positionHandlerList().remove(positionHandler);
		messenger.receiver().riskStateHandlerList().remove(riskStateHandler);
		messenger.receiver().eventHandlerList().remove(eventHandler);
		messenger.receiver().serviceStatusHandlerList().remove(serviceStatusHandler);
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public StateTransitionEvent stoppedEnter() {
		if (started.compareAndSet(true, false)){
			LOG.info("Stopping dashboard standalone service");
			this.clientUpdateExecutor.shutdown();
			server.stop();
			LOG.info("Stopped dashboard standalone service");
		}
		return StateTransitionEvent.NULL;
	}
	
	private <T> void sendMessageToActiveChannel(MessageHeaderDecoder header, T decoder, SbeHandler<T> encoder){
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
		}
		else if (channel.isCloseFrameReceived()){
			Long lastLogTime = (Long)channel.getAttribute(LAST_LOG_TIME);
			long currentTime = System.nanoTime();
			if (lastLogTime + CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS < currentTime){
				channel.setAttribute(LAST_LOG_TIME, System.nanoTime());
				LOG.error("Web socket received Close Frame. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + ", closeCode:" + channel.getCloseCode() + ", reason:" + channel.getCloseReason() + "]");
			}
			valid = false;
		}
		else if (channel.isCloseFrameSent()){
			Long lastLogTime = (Long)channel.getAttribute(LAST_LOG_TIME);
			long currentTime = System.nanoTime();
			if (lastLogTime + CHANNEL_ERROR_LOG_THROTTLE_PERIOD_IN_NS < currentTime){
				channel.setAttribute(LAST_LOG_TIME, System.nanoTime());
				LOG.error("Web socket sent Close Frame. [channelPeer:" + channel.getPeerAddress() + ", channelLocal:" + channel.getLocalAddress() + ", closeCode:" + channel.getCloseCode() + ", reason:" + channel.getCloseReason() + "]");
			}
			valid = false;
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
								this.messenger.self().sinkId(), 
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
											this.messenger.self().sinkId(), 
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
	
	protected static ByteBuffer toByteBuffer(ByteBuffer... payload) {
		if (payload.length == 1) {
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
	
	public CompletableFuture<Command> handleExternalCommandAsync(ClientKey clientKey, Command command){
		CompletableFuture<Command> resultFuture = new CompletableFuture<Command>();
		// Change the clientKey of the request to be based on this service's own messenger
		// such that we can match responses back to this request
		int ownClientKey = this.messenger.getNextClientKeyAndIncrement();
		command.clientKey(ownClientKey);
		webClientCommands.put(ownClientKey, WebCallContext.of(clientKey, command, resultFuture));
		webServiceRequestExecutor.execute(() -> {
			sendCommandToDashboard(command);
		});
		return resultFuture;
	}

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
		
		CompletableFuture<Command> commandFuture = handleExternalCommandAsync(WebSocketClientKey.of(origClientKey, channel), command);

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
	
	private void sendCommandToDashboard(Command command){
		int length = this.webServiceMessenger.commandSender().encodeCommand(webServiceRequestBuffer, 0, DASHBOARD_SINK_ID, command);
		try {
			this.dashboardOutputStream.write(webServiceRequestBuffer.byteArray(), 0, length);
			this.dashboardOutputStream.flush();
		}
		catch (IOException e) {
			LOG.error("Could not send byte to dashboard [command:{}]", command);
		}
	}

	private void sendRequestToDashboard(Request request){
		int length = this.webServiceMessenger.requestSender().encodeRequest(webServiceRequestBuffer, 0, DASHBOARD_SINK_ID, request);
		try {
			LOG.info("Sending request to dashboard [clientKey:{}, requestType:{}]", request.clientKey(), request.requestType().name());
			this.dashboardOutputStream.write(webServiceRequestBuffer.byteArray(), 0, length);
			this.dashboardOutputStream.flush();
			LOG.info("Sent request to dashboard [clientKey:{}, requestType:{}]", request.clientKey(), request.requestType().name());
		}
		catch (IOException e) {
			LOG.error("Could not send byte to dashboard [request:{}]", request);
		}
	}
	
	private static class WebCallContext<T> {
		private ClientKey clientKeyObject;
		private T item;
		private CompletableFuture<T> future;
		public static <U> WebCallContext<U> of(ClientKey clientKeyObject, U item, CompletableFuture<U> future){
			return new WebCallContext<U>(clientKeyObject, item, future);
		}
		WebCallContext(ClientKey clientKeyObject, T item, CompletableFuture<T> future){
			this.clientKeyObject = clientKeyObject;
			this.item = item;
			this.future = future;
		}
	}
	
	private CompletableFuture<Request> handleExternalRequestAsync(ClientKey clientKey, Request request){
		CompletableFuture<Request> resultFuture = new CompletableFuture<Request>();
		try {
			switch (request.requestType()){
			case SUBSCRIBE:
			case GET:
			case GET_AND_SUBSCRIBE:
			case UPDATE:
			case CREATE:
				// Change the clientKey of the request to be based on this service's own messenger
				// such that we can match responses back to this request
				int serviceClientKey = this.messenger.getNextClientKeyAndIncrement();
				request.clientKey(serviceClientKey);
                LOG.info("Handle external request [webClientKey:{}, serviceClientKey:{}, requestType:{}]", clientKey.key(), serviceClientKey, request.requestType().name());
				webClientRequests.put(serviceClientKey, WebCallContext.of(clientKey, request, resultFuture));
				webServiceRequestExecutor.execute(() -> {
					sendRequestToDashboard(request);
				});
				break;
			default:
				resultFuture.completeExceptionally(new Throwable("Receive unexpected request type [requestType:" + request.requestType().name() + "]"));
				LOG.warn("Receive unexpected request type [requestType:{}]", request.requestType().name());
				break;
			}
		}
		catch (Exception ex){
			LOG.warn("Caught exception when handling request [requestType:{}, clientKey:{}]", request.requestType().name(), clientKey);
		}
		return resultFuture;
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
		
		CompletableFuture<Request> requestFuture = handleExternalRequestAsync(WebSocketClientKey.of(requestFbs.clientKey(), channel), request);

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

	private void sendNewOrderRequestToDashboard(NewOrderRequest request){
		int length = this.webServiceMessenger.orderSender().encodeNewOrder(webServiceRequestBuffer, 0, DASHBOARD_SINK_ID, request);
		try {
			this.dashboardOutputStream.write(webServiceRequestBuffer.byteArray(), 0, length);
			this.dashboardOutputStream.flush();
		}
		catch (IOException e) {
			LOG.error("Could not send new order request to dashboard [request:{}]", request);
		}
	}
	
	private void sendCancelOrderRequestToDashboard(CancelOrderRequest request){
		int length = this.webServiceMessenger.orderSender().encodeCancelOrder(webServiceRequestBuffer, 0, DASHBOARD_SINK_ID, request);
		try {
			this.dashboardOutputStream.write(webServiceRequestBuffer.byteArray(), 0, length);
			this.dashboardOutputStream.flush();
		}
		catch (IOException e) {
			LOG.error("Could not send cancel order request to dashboard [request:{}]", request);
		}		
	}

	public CompletableFuture<OrderRequest> handleExternalNewOrderRequestAsync(ClientKey clientKey, NewOrderRequest request){
		CompletableFuture<OrderRequest> future = new CompletableFuture<>();
		int ownClientKey = this.messenger.getNextClientKeyAndIncrement();
		request.clientKey(ownClientKey);
		webClientOrderRequests.put(ownClientKey, WebCallContext.of(clientKey, request, future));
		webServiceRequestExecutor.execute(() -> {
			sendNewOrderRequestToDashboard(request);
		});
		return future;
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

		CompletableFuture<OrderRequest> requestFuture = handleExternalNewOrderRequestAsync(
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

	public CompletableFuture<OrderRequest> handleExternalCancelOrderRequestAsync(ClientKey clientKey, CancelOrderRequest request){
		CompletableFuture<OrderRequest> future = new CompletableFuture<>();
		int ownClientKey = this.messenger.getNextClientKeyAndIncrement();
		request.clientKey(ownClientKey);
		webClientOrderRequests.put(ownClientKey, WebCallContext.of(clientKey, request, future));
		webServiceRequestExecutor.execute(() -> {
			sendCancelOrderRequestToDashboard(request);
		});
		return future;
	}

	private void handleCancelOrderRequestFbs(WebSocketChannel channel, CancelOrderRequestFbs requestFbs){
		// TODO: How about all other fields?
		final int origClientKey = requestFbs.clientKey();
		CancelOrderRequest request = CancelOrderRequest.of(requestFbs.clientKey(),
				null,
				requestFbs.orderSidToBeCancelled(),
				requestFbs.secSid(),
				Side.NULL_VAL);

		CompletableFuture<OrderRequest> requestFuture = handleExternalCancelOrderRequestAsync(
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

	AtomicBoolean listeningForIncomingTcpData(){
		return listeningForIncomingTcpData;
	}

}
