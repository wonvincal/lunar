package com.lunar.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.DashboardServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.ServiceStatusTracker.AggregatedServiceStatusChangeHandler;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.CommandDecoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.binary.ServiceStatusDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CancelOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeEncoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeEncoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeEncoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeEncoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeEncoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;
import com.lunar.util.ClientKey;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

/**
 * System dash board.  We are going to implement a web service interface in this as well.
 * Dash board should subscribe to the following:
 * 1. Status of all services in the system
 * 2. Performance statistics of all services in the system
 * 
 * It should accept the following:
 * 1. request for different reports
 * 2. request for subscription of different reports
 * 3. start/stop/restart of different services
 * 
 * Threads
 * 1. Receive messages from its disruptor's ring buffer
 * 2. We 
 *  
 * @author Calvin
 *
 */
public class DashboardService implements ServiceLifecycleAware {
	static final Logger LOG = LogManager.getLogger(DashboardService.class);
	private static int STANDALONE_DASHBOARD_COMMON_SINK_ID = 126;
	private static int NUM_FAILED_DELIVERY_ATTEMPTS_THRESHOLD = 10000;
	private LunarService messageService;
	private final Messenger messenger;
	private final MessageSinkRefMgr refMgr;
	private final String name;
	
	private final long LOG_FREQ = TimeUnit.SECONDS.toNanos(30);
	private AtomicLong nextLogTimeNs = new AtomicLong(0);
	private final AtomicLong eventCount = new AtomicLong();
	private final AtomicLong serviceStatusCount = new AtomicLong();
	private final AtomicLong riskCount = new AtomicLong();
	private final AtomicLong strategyCount = new AtomicLong();
	private final AtomicLong strategyWarrantCount = new AtomicLong();
	private final AtomicLong marketDataCount = new AtomicLong();
	private final AtomicLong orderCount = new AtomicLong();
	private final AtomicLong positionCount = new AtomicLong();
	private final AtomicLong scoreboardCount = new AtomicLong();
	private final AtomicLong noteCount = new AtomicLong();
	private final AtomicLong chartDataCount = new AtomicLong();
	private final AtomicLong responseCount = new AtomicLong();

	private static final int EXPECTED_OUTSTANDING_REQUESTS = 4096 * 16;
	private static final int EXPECTED_OUTSTANDING_ORDER_REQUESTS = 128;	
	private final ConcurrentHashMap<Integer, ClientRequestContext> standAloneDashboardClientRequests = new ConcurrentHashMap<>(EXPECTED_OUTSTANDING_REQUESTS, 0.75f, 2);
	private final ConcurrentHashMap<Integer, ClientRequestContext> standAloneDashboardClientCommands = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, ClientRequestContext> standAloneDashboardClientOrderRequests = new ConcurrentHashMap<>(EXPECTED_OUTSTANDING_ORDER_REQUESTS);

	private final Optional<List<Integer>> marketDataAutoSubSecSids;
	private final Optional<Integer> tcpListeningPort;
	
	// Thread#1 - listen to new incoming connection
	private final ExecutorService tcpListeningExecutor;
	
	// Thread#N - one thread per incoming connection
	private final ExecutorService tcpClientExecutor;

	// Thread#1 - send buffer back to dashboard web service thru tcp
	private final Disruptor<MutableDirectBufferWithLength> tcpBufferSender;
	private final RingBuffer<MutableDirectBufferWithLength> tcpBufferSenderRingBuffer;

	private static final int MAX_CLIENTS = 3;
	
	public static DashboardService of(ServiceConfig config, LunarService messageService) {
		return new DashboardService(config, messageService);
	}
	
	private static class MutableDirectBufferWithLength {
		private ClientConnectionContext context;
		private int length;
		private final MutableDirectBuffer buffer;
		MutableDirectBufferWithLength(MutableDirectBuffer buffer){
			this.length = 0;
			this.buffer = buffer;
		}
	}
	
	private static class MutableDirectBufferWithLengthFactory implements EventFactory<MutableDirectBufferWithLength>{
		private int frameSize;
		public static MutableDirectBufferWithLengthFactory of(){
			return new MutableDirectBufferWithLengthFactory(ServiceConstant.MAX_MESSAGE_SIZE);
		}
		public static MutableDirectBufferWithLengthFactory of(int frameSize){
			return new MutableDirectBufferWithLengthFactory(frameSize);
		}
		public MutableDirectBufferWithLengthFactory(int frameSize){
			this.frameSize = frameSize;
		}
		public MutableDirectBufferWithLength newInstance(){
			return new MutableDirectBufferWithLength(new UnsafeBuffer(ByteBuffer.allocate(frameSize)));
		}
	}
	
	private long errorMessagePrintTimeInNs;
	private final long ERROR_MESSAGE_FREQ = TimeUnit.SECONDS.toNanos(5L);
	
	private long onData(DirectBuffer buffer, int offset, int length, ClientConnectionContext context){
		long sequence = -1;
		try {
			sequence = tcpBufferSenderRingBuffer.tryNext();
			MutableDirectBufferWithLength event = tcpBufferSenderRingBuffer.get(sequence);
			event.context = context;
			event.length = length;
			event.buffer.putBytes(0, buffer, offset, length);
		} 
		catch (InsufficientCapacityException e){
			// Check to see if any connection is blocked, kill it.
			if (this.messenger.timerService().nanoTime() - errorMessagePrintTimeInNs > ERROR_MESSAGE_FREQ){
				errorMessagePrintTimeInNs = this.messenger.timerService().nanoTime(); 
				LOG.error("Unable to publish message into tcp ring buffer");				
				killBlockingConnection();
			}
			return MessageSink.INSUFFICIENT_SPACE;
		}
		finally {
			if (sequence != -1){
				tcpBufferSenderRingBuffer.publish(sequence);
			}
		}
		return MessageSink.OK;		
	}
	
	private void logMessageCount(){
		LOG.info("[event:{}, status:{}, risk:{}, strategy:{}, strategyWarrant:{}, marketData:{}, order:{}, position:{}, scoreboard:{}, note:{}, chartData:{}, response:{}",
				eventCount.get(),
				serviceStatusCount.get(),
				riskCount.get(),
				strategyCount.get(),
				strategyWarrantCount.get(),
				marketDataCount.get(),
				orderCount.get(),
				positionCount.get(),
				scoreboardCount.get(),
				noteCount.get(),
				chartDataCount.get(),
				responseCount.get());
	}
	
	@SuppressWarnings("unchecked")
	DashboardService(ServiceConfig config, LunarService messageService) {
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.refMgr = this.messenger.referenceManager();
		if (config instanceof DashboardServiceConfig){
			DashboardServiceConfig specificConfig = (DashboardServiceConfig)config;
			tcpListeningPort = specificConfig.tcpListeningPort();
			marketDataAutoSubSecSids = specificConfig.marketDataAutoSubSecSids();
			tcpBufferSender = new Disruptor<MutableDirectBufferWithLength>(
					MutableDirectBufferWithLengthFactory.of(),
					specificConfig.queueSize().get(),
					new NamedThreadFactory("dashboard", "tcp-message-sender"),
					ProducerType.MULTI,
					new BlockingWaitStrategy());
			
			tcpBufferSender.handleEventsWith(tcpBufferHandler);
			tcpBufferSenderRingBuffer = tcpBufferSender.getRingBuffer(); 
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a DashboardServiceConfig config");
		}
		ClientConnectionContext[] items = new ClientConnectionContext[MAX_CLIENTS]; 
		this.clientConnectionContexts = ObjectArrayList.wrap(items);
		this.clientConnectionContexts.size(0);
		this.tcpListeningExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("dashboard", "tcp-listener"));
		this.tcpClientExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("dashboard", "tcp-client"));
				
	}

	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(aggServiceStatusHandler);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderAndTradeSnapshotService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.PortfolioAndRiskService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.MarketDataSnapshotService);
		return StateTransitionEvent.WAIT;
	}
	
	private final EventHandler<MutableDirectBufferWithLength> tcpBufferHandler = new EventHandler<MutableDirectBufferWithLength>() {
		
		@Override
		public void onEvent(MutableDirectBufferWithLength event, long sequence, boolean endOfBatch) throws Exception {
			// Send bytes to client with timeout
			// On timeout, don't send for a while, send out a critical message
			sendBytesToClientSockets(event.buffer.byteArray(), 0, event.length, event.context);
		}
	};
	
	private final AggregatedServiceStatusChangeHandler aggServiceStatusHandler = new AggregatedServiceStatusChangeHandler() {
		
		@Override
		public void handle(boolean status) {
			if (status){
				messageService.stateEvent(StateTransitionEvent.ACTIVATE);
			}
			else { // DOWN or INITIALIZING
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}
		}
	};
	
	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		// we should wait for health check on database connection
		messenger.registerEventsForOrderTracker();
		messenger.receiver().cancelOrderRequestHandlerList().add(cancelOrderRequestHandler);
		messenger.receiver().commandHandlerList().add(commandHandler);
		messenger.receiver().newOrderRequestHandlerList().add(newOrderRequestHandler);
		messenger.receiver().requestHandlerList().add(requestHandler);
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
		
		// Noop handlers that we won't remove
		messenger.receiver().orderAcceptedHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderCancelledHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderCancelledWithOrderInfoHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderExpiredHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderExpiredWithOrderInfoHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderRejectedHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().tradeCreatedHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().tradeCreatedWithOrderInfoHandlerList().add((buffer, offset, header, codec) -> {});
		messenger.receiver().tradeCancelledHandlerList().add((buffer, offset, header, codec) -> {});
		
		// Start socket
		// One thread to listen for client request
		// One thread for each client (there should be one only)
		// Dashboard should only be allowed to proceed to Active state after it has 
		// started to listen for new client connection
		tcpBufferSender.start();
		tcpListeningExecutor.execute(this::acceptClientConnection);
		
		long sleptTimeInMilli = 0;
		while (!listeningForClientConnection.get()){
			final int sleepTimeInMilli = 500;
			sleptTimeInMilli += sleepTimeInMilli; 				
			try {
				Thread.sleep(sleepTimeInMilli);
			} 
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (sleptTimeInMilli > 5_000L){
				LOG.error("Not able to bring up TCP connection");
				return StateTransitionEvent.FAIL;
			}
		}		
		
		return StateTransitionEvent.NULL;
	}

	private ServerSocket serverSocket = null;
	private final ReentrantLock lock = new ReentrantLock();
	private final ObjectArrayList<ClientConnectionContext> clientConnectionContexts;
	
	@Override
	public StateTransitionEvent activeEnter() {
		// Subscribe
		if (marketDataAutoSubSecSids.isPresent()){
			messenger.receiver().marketDataTradeHandlerList().add((buffer, offset, header, codec) -> {});
			for (Integer secSid : marketDataAutoSubSecSids.get()){
				final CompletableFuture<Request> marketDataSub = messenger.sendRequest(messenger.referenceManager().mds(),
						RequestType.SUBSCRIBE,
						new ImmutableList.Builder<Parameter>().add(Parameter.of(TemplateType.ORDERBOOK_SNAPSHOT), 
								Parameter.of(ParameterType.SECURITY_SID, secSid)).build(),
						ResponseHandler.NULL_HANDLER);
				marketDataSub.whenComplete(new BiConsumer<Request, Throwable>() {
					@Override
					public void accept(Request request, Throwable throwable) {
						if (throwable != null || request.resultType() != ResultType.OK){
							LOG.error("Could not auto subscribe market data. [secSid:{}]", secSid);
						}
					}
				});
			}			
		}
		
		return StateTransitionEvent.NULL;
	}

	private final AtomicBoolean listeningForClientConnection = new AtomicBoolean(false);
	
	private void acceptClientConnection(){
		// Accept at most N connection, once that exceeds kill the first established connection
		int port = -1;
		try {
			port = tcpListeningPort.get();
			this.serverSocket = new ServerSocket(port);			
		}
		catch (IOException e){
			LOG.error("Cannot create server socket to listen to port " + port, e);
			return;
		}
		catch (Exception e){
			LOG.error("Caught exception when creating server socket", e);
			return;
		}
		
		listeningForClientConnection.set(true);
		
		// @TODO Logic to handle graceful shutdown
		while (listeningForClientConnection.get()){
			try {
				
				LOG.info("Listening for new client connection [listeningPort:{}]", port);
				Socket clientSocket = this.serverSocket.accept();
				
				lock.lock();
				
				ClientConnectionContext context = ClientConnectionContext.of(clientSocket, new BufferedOutputStream(clientSocket.getOutputStream()), this.messenger.createChildMessenger());
				clientConnectionContexts.add(context);
				if (clientConnectionContexts.size() <= MAX_CLIENTS){
					// We should allow more than one connection
					LOG.info("Accepted client connection [address:{}, port:{}]", clientSocket.getInetAddress(), clientSocket.getPort());
					tcpClientExecutor.execute(() -> {handleClientConnection(context);});	
				}
				else {
					// @TODO Kill the first client in the list
					LOG.warn("Unable to create more client connection");
				}
			}
			catch (Exception e){
				LOG.error("Caught exception when listening for new client connection", e);
			}
			finally {
				if (lock.isLocked()){
					lock.unlock();
				}
			}
		}
	}

	AtomicBoolean listeningForClientConnection(){
		return listeningForClientConnection;
	}
	
	private static class ClientRequestContext {
		private final int clientId;
		private final ClientConnectionContext context;
		
		static ClientRequestContext of(int clientId, ClientConnectionContext context){
			return new ClientRequestContext(clientId, context);
		}
		
		ClientRequestContext(int clientId, ClientConnectionContext context){
			this.clientId = clientId;
			this.context = context;
		}
	}
	
	public static class ClientConnectionContext {
		private final MutableDirectBuffer contextDisruptorThreadBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));		
		private final MutableDirectBuffer contextTcpReceiveThreadBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		private final Socket clientSocket;
		private BufferedOutputStream outputStream;
		private final Messenger clientConnectionMessenger;
		private final CommandSbeEncoder commandEncoder = new CommandSbeEncoder();
		private final CommandSbeDecoder commandDecoder = new CommandSbeDecoder();
		private final NewOrderRequestSbeEncoder newOrderRequestEncoder = new NewOrderRequestSbeEncoder();
		private final NewOrderRequestSbeDecoder newOrderRequestDecoder = new NewOrderRequestSbeDecoder();
		private final CancelOrderRequestSbeEncoder cancelOrderRequestEncoder = new CancelOrderRequestSbeEncoder();
		private final CancelOrderRequestSbeDecoder cancelOrderRequestDecoder = new CancelOrderRequestSbeDecoder();
		private final RequestSbeEncoder requestEncoder = new RequestSbeEncoder();
		private final RequestSbeDecoder requestDecoder = new RequestSbeDecoder();
		private final ResponseSbeEncoder responseEncoder = new ResponseSbeEncoder();
		private final ResponseSbeDecoder responseDecoder = new ResponseSbeDecoder();
		private final CommandAckSbeEncoder commandAckEncoder = new CommandAckSbeEncoder();
		private final CommandAckSbeDecoder commandAckDecoder = new CommandAckSbeDecoder();
		
		// If it has been detected that a sentBeginAtNano has been lasted for more than N time, we should kill this connection
		private volatile long sentBeginAtNano;
		private int failedCount = 0;
		
		static ClientConnectionContext of(Socket clientSocket, BufferedOutputStream outputStream, Messenger messenger){
			return new ClientConnectionContext(clientSocket, outputStream, messenger);
		}
		
		ClientConnectionContext(Socket clientSocket, BufferedOutputStream outputStream, Messenger messenger){
			this.clientSocket = clientSocket;
			this.clientConnectionMessenger = messenger;
			this.outputStream = outputStream;
		}
		
		private final long TCP_SEND_TIMEOUT_IN_NS = TimeUnit.SECONDS.toNanos(10L);
		public boolean blockedTooLong(){
			if (this.sentBeginAtNano == 0){
				return false;
			}
			return (this.clientConnectionMessenger.timerService().nanoTime() - this.sentBeginAtNano) > TCP_SEND_TIMEOUT_IN_NS; 
		}
		
		public void setSentBeginAtNano(){
			this.sentBeginAtNano = this.clientConnectionMessenger.timerService().nanoTime();
		}
		
		public void clearSentBeginAtNano(){
			this.sentBeginAtNano = 0;
		}
		
		public int incrementFailedCount(){
			return ++failedCount;			
		}
		
		public int encodeFailedCommandAck(int clientId, int responseMsgSeq, ResultType resultType){
			return -1;
		}
		
		public int copyBytesAndReplaceClientId(DirectBuffer buffer, int offset, int length, MessageHeaderDecoder messageHeader, MutableDirectBuffer dstBuffer, int newClientId){
			dstBuffer.putBytes(0, buffer, offset, length);			
			return replaceClientId(dstBuffer, 0, messageHeader, newClientId);
		}
		
		public int replaceClientId(MutableDirectBuffer buffer, int offset, MessageHeaderDecoder messageHeader, int newClientId){
			int result = -1;
			switch (messageHeader.templateId()){
			case CommandSbeDecoder.TEMPLATE_ID:
				commandDecoder.wrap(buffer, offset + messageHeader.encodedLength(), messageHeader.blockLength(), messageHeader.version());
				result = commandDecoder.clientKey();
				commandEncoder.wrap(buffer, offset + messageHeader.encodedLength()).clientKey(newClientId);
				break;
			case NewOrderRequestSbeDecoder.TEMPLATE_ID:
				newOrderRequestDecoder.wrap(buffer, offset + messageHeader.encodedLength(), messageHeader.blockLength(), messageHeader.version());
				result = newOrderRequestDecoder.clientKey();
				newOrderRequestEncoder.wrap(buffer, offset + messageHeader.encodedLength()).clientKey(newClientId);				
				break;				
			case CancelOrderRequestSbeDecoder.TEMPLATE_ID:
				cancelOrderRequestDecoder.wrap(buffer, offset + messageHeader.encodedLength(), messageHeader.blockLength(), messageHeader.version());
				result = cancelOrderRequestDecoder.clientKey();
				cancelOrderRequestEncoder.wrap(buffer, offset + messageHeader.encodedLength()).clientKey(newClientId);				
				break;
			case ResponseSbeDecoder.TEMPLATE_ID:
				responseDecoder.wrap(buffer, offset + messageHeader.encodedLength(), messageHeader.blockLength(), messageHeader.version());
				result = responseDecoder.clientKey(); 
				responseEncoder.wrap(buffer, offset + messageHeader.encodedLength()).clientKey(newClientId);
//				LOG.info("Replaced response client key [clientId:{}, prevClientId:{}]", newClientId, result);
				break;
			case CommandAckSbeDecoder.TEMPLATE_ID:
				commandAckDecoder.wrap(buffer, offset + messageHeader.encodedLength(), messageHeader.blockLength(), messageHeader.version());
				result = commandAckDecoder.clientKey();
				commandAckEncoder.wrap(buffer, offset + messageHeader.encodedLength()).clientKey(newClientId);
				break;
			case RequestSbeDecoder.TEMPLATE_ID:
				requestDecoder.wrap(buffer, offset + messageHeader.encodedLength(), messageHeader.blockLength(), messageHeader.version());
				RequestType requestType = requestDecoder.requestType();
				if (requestType == RequestType.CREATE ||
					requestType == RequestType.SUBSCRIBE ||
					requestType == RequestType.GET ||
					requestType == RequestType.GET_AND_SUBSCRIBE ||
					requestType == RequestType.UPDATE){
					result = requestDecoder.clientKey();
					requestEncoder.wrap(buffer, offset + messageHeader.encodedLength()).clientKey(newClientId);
				}
				else {
					LOG.warn("Receive unexpected request type [requestType:{}]", requestType.name());					
				}
				break;
			default:
				LOG.error("Unknown message type, not going to replace client id [templateId:{}]", messageHeader.templateId());
				break;
			}
			return result;
		}
	}
	
	/**
	 * This is being called on the TCP receiving thread
	 * 
	 * Create a client request context
	 * @param buffer
	 * @param offset
	 * @param length
	 * @param messageHeader
	 * @param connectionContext
	 * @return
	 */
	private boolean publishRequestToSelf(MutableDirectBuffer buffer, int offset, int length, MessageHeaderDecoder messageHeader, ClientConnectionContext connectionContext){
		int newDashboardClientId = this.messenger.getNextClientKeyAndIncrement();		
		int existingClientId = connectionContext.replaceClientId(buffer, offset, messageHeader, newDashboardClientId);		
		if (existingClientId == -1){
			// Send a failed response back to socket
			sendResponse(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, existingClientId, ResultType.FAILED, ServiceConstant.START_RESPONSE_SEQUENCE);
			return false;
		}
		standAloneDashboardClientRequests.put(newDashboardClientId, ClientRequestContext.of(existingClientId, connectionContext));
		long result;
		// Publish a message to self
		if ((result = this.messenger.self().tryPublish(buffer, offset, length)) != MessageSink.OK){
			// Send a failed response back to socket
			LOG.error("Could not publish request to self [result:{}]", result);
			// Send a failed response back to socket
			sendResponse(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, existingClientId, ResultType.FAILED, ServiceConstant.START_RESPONSE_SEQUENCE);
			standAloneDashboardClientRequests.remove(newDashboardClientId);
			return false;
		}
		LOG.debug("Published request [clientId:{}, dashboardSpecificClientId:{}, length:{}]", existingClientId, newDashboardClientId, length);
		return true;
	}

	private boolean publishCommandToSelf(MutableDirectBuffer buffer, int offset, int length, MessageHeaderDecoder messageHeader, ClientConnectionContext connectionContext){
		int newDashboardClientId = this.messenger.getNextClientKeyAndIncrement();		
		int existingClientId = connectionContext.replaceClientId(buffer, offset, messageHeader, newDashboardClientId);		
		if (existingClientId == -1){
			// Send a failed response back to socket
			sendCommandAck(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, existingClientId, CommandAckType.FAILED);
			return false;
		}
		standAloneDashboardClientCommands.put(newDashboardClientId, ClientRequestContext.of(existingClientId, connectionContext));
		long result;
		if ((result = this.messenger.self().tryPublish(buffer, offset, length)) != MessageSink.OK){
			// Send a failed response back to socket
			LOG.error("Could not publish command to self [result:{}]", result);
			// Send a failed response back to socket
			sendCommandAck(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, existingClientId, CommandAckType.FAILED);
			standAloneDashboardClientCommands.remove(newDashboardClientId);
			return false;
		}
		LOG.info("Published command [clientId:{}, dashboardSpecificClientId:{}]", existingClientId, newDashboardClientId);
		return true;
	}

	private final ServiceStatusSbeDecoder serviceStatusDecoder = new ServiceStatusSbeDecoder();
	
	/**
	 * Service status is received from DashboardStandAloneWebService
	 * @param buffer
	 * @param offset
	 * @param length
	 * @param messageHeader
	 * @param connectionContext
	 * @return
	 */
	private boolean publishServiceStatusBack(MutableDirectBuffer buffer, int offset, int length, MessageHeaderDecoder messageHeader, ClientConnectionContext connectionContext){
		serviceStatusDecoder.wrap(buffer, 
				offset + messageHeader.encodedLength(), 
				messageHeader.blockLength(), 
				messageHeader.version());
		if (serviceStatusDecoder.serviceType() == ServiceType.DashboardStandAloneWebService && serviceStatusDecoder.statusType() == ServiceStatusType.HEARTBEAT){
			// Copy buffer and send it back
			connectionContext.contextTcpReceiveThreadBuffer.putBytes(0, buffer, offset, length);
			onData(connectionContext.contextTcpReceiveThreadBuffer, 0, length, connectionContext);
//			sendBytesToClientSocket(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, 0, length);
			return true;
		}
		LOG.error("Received unexpected service status [{}]", ServiceStatusDecoder.decodeToString(serviceStatusDecoder));
		return false;
	}
	
	private boolean publishOrderRequestToSelf(MutableDirectBuffer buffer, int offset, int length, MessageHeaderDecoder messageHeader, ClientConnectionContext connectionContext){
		int newDashboardClientId = this.messenger.getNextClientKeyAndIncrement();		
		int existingClientId = connectionContext.replaceClientId(buffer, offset, messageHeader, newDashboardClientId);		
		if (existingClientId == -1){
			// Send a failed response back to socket
			sendOrderRequestCompletion(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, existingClientId, -1, OrderRequestCompletionType.FAILED, OrderRequestRejectType.NULL_VAL, OrderSender.ORDER_REQUEST_COMPLETION_EMPTY_REASON);
			return false;
		}
		standAloneDashboardClientOrderRequests.put(newDashboardClientId, ClientRequestContext.of(existingClientId, connectionContext));
		long result;
		if ((result = this.messenger.self().tryPublish(buffer, offset, length)) != MessageSink.OK){
			// Send a failed response back to socket
			LOG.error("Could not publish order request to self [result:{}]", result);
			// Send a failed response back to socket
			sendOrderRequestCompletion(connectionContext, connectionContext.contextTcpReceiveThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, existingClientId, -1, OrderRequestCompletionType.FAILED, OrderRequestRejectType.NULL_VAL, OrderSender.ORDER_REQUEST_COMPLETION_EMPTY_REASON);
			standAloneDashboardClientOrderRequests.remove(newDashboardClientId);
			return false;
		}
		LOG.info("Published order request [clientId:{}, dashboardSpecificClientId:{}]", existingClientId, newDashboardClientId);
		return true;
	}

	private boolean publishMessageToSelf(MessageSender messageSender, MutableDirectBuffer buffer, int offset, int length, MessageHeaderDecoder messageHeader, ClientConnectionContext connectionContext){
        try {
            int templateId = messageHeader.templateId();
            switch (templateId){
            case RequestSbeDecoder.TEMPLATE_ID:
                publishRequestToSelf(buffer, offset, length, messageHeader, connectionContext);
                break;
            case CommandSbeDecoder.TEMPLATE_ID:
                publishCommandToSelf(buffer, offset, length, messageHeader, connectionContext);
                break;
            case NewOrderRequestSbeDecoder.TEMPLATE_ID:
            case CancelOrderRequestSbeDecoder.TEMPLATE_ID:
                publishOrderRequestToSelf(buffer, offset, length, messageHeader, connectionContext);
                break;
            case ServiceStatusSbeDecoder.TEMPLATE_ID:
                publishServiceStatusBack(buffer, offset, length, messageHeader, connectionContext);
                break;
            default:
                LOG.error("Received unexpected message type [templateId:{}]", templateId);
                return false;
            }
        }
        catch (Exception ex){
            LOG.error("Caught exception when publishing message", ex);
            return false;
        }
		return true;
	}
	
	/**
	 * Listen to incoming message from a client connection
	 * @param connectionContext
	 */
	private void handleClientConnection(ClientConnectionContext connectionContext) {
	    final MutableDirectBuffer wireReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE * 128));
	    final MutableDirectBuffer copyReceiveBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE * 128 * 3));

	    BufferedInputStream inputStream;
	    try {
	    	// @todo Tune buffer size
	    	inputStream = new BufferedInputStream(connectionContext.clientSocket.getInputStream());
	    }
	    catch (IOException e){
	    	LOG.error("Could not get input stream [" + connectionContext.clientSocket.getInetAddress() + "]", e);
	    	return;
	    }
        catch (Exception e){
	    	LOG.error("Caught unexpected exception when getting input stream [" + connectionContext.clientSocket.getInetAddress() + "]", e);
            return;
        }
	    
	    MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
	    MessageSender messageSender = this.messenger.createMessageSender();
	    int numBytesInCopyBuffer = 0;
	    int bytesRead = 0;

        // We don't know what is causing a stream to return -1
        long endOfStreamCount = 0;
        boolean endOfStreamReached = false;
	    while (true){
            if (!endOfStreamReached){
	    	    LOG.info("Waiting for bytes from client [port:{}]", connectionContext.clientSocket.getPort());
            }
				
	    	try {
                bytesRead = inputStream.read(wireReceiveBuffer.byteArray());
				if (bytesRead == -1){
                    if (endOfStreamCount % 10 == 0){
					    LOG.info("Detected end of stream, sleep [port:{}, count:{}]", connectionContext.clientSocket.getPort(), endOfStreamCount);
                    }
                    try {
                        Thread.sleep(200);
                    }
                    catch (InterruptedException e){
                        LOG.warn("Caught interrupted exception during sleep", e);
                    }
                    endOfStreamCount++;
                    endOfStreamReached = true;
					// removeClientConnection(connectionContext);
					continue;
				}
                endOfStreamReached = false;
                endOfStreamCount = 0;
	    		LOG.info("Received message from client [port:{}, bytesRead:{}]", connectionContext.clientSocket.getPort(), bytesRead);
				
				// Send message to itself
				if (numBytesInCopyBuffer == 0){
					// Consume all byteRead directly from wireReceiveBuffer
					int offset = 0;
					while (bytesRead >= MessageHeaderDecoder.ENCODED_LENGTH){
						messageHeaderDecoder.wrap(wireReceiveBuffer, offset);
						int length = MessageHeaderDecoder.ENCODED_LENGTH + messageHeaderDecoder.payloadLength();
						if (bytesRead >= length){
							// Publish this same message to Dashboard's sink
							publishMessageToSelf(messageSender, wireReceiveBuffer, offset, length, messageHeaderDecoder, connectionContext);
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
					
					// copyReceiveBufferOffset is where the next byte will be placed
					// that is also the number of bytes in the buffer
					int offset = 0;
					while (numBytesInCopyBuffer >= MessageHeaderDecoder.ENCODED_LENGTH){
						messageHeaderDecoder.wrap(copyReceiveBuffer, offset);
						int length = MessageHeaderDecoder.ENCODED_LENGTH + messageHeaderDecoder.payloadLength();
						if (numBytesInCopyBuffer >= length){
							// Copy current message
							publishMessageToSelf(messageSender, copyReceiveBuffer, offset, length, messageHeaderDecoder, connectionContext);
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
	    	catch (IOException e) {
	    		LOG.error("Encounter io exception when reading [" + connectionContext.clientSocket.getInetAddress() + "]", e);
	    		removeClientConnection(connectionContext);
	    		break;
			}
            catch (Exception e){
                LOG.error("Caught unknown exception when processing message read from tcp", e);
                // Since this is not an IO Exception, we want to keep processing the next message
            }
	    }
	}
	
	private void removeClientConnection(ClientConnectionContext connectionContext){
		lock.lock();
		try {
			int index = -1;
			boolean found = false;
			for (ClientConnectionContext context : clientConnectionContexts){
				index++;
				if (connectionContext.clientSocket.equals(context.clientSocket)){
					found = true;
					break;
				}
			}
			if (found){
				ClientConnectionContext removed = clientConnectionContexts.remove(index);
				LOG.info("Removed failed client socket [index:{}, address:{}]", index, removed.clientSocket.getInetAddress());									
			}
		}
		catch (Exception ex){
			LOG.error("Could not remove client connection [socket:{}]", connectionContext.clientSocket, ex);
		}
		finally {
			lock.unlock();
		}
	}

	private final Handler<NewOrderRequestSbeDecoder> newOrderRequestHandler = new Handler<NewOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder request) {
			LOG.info("Received new order request [clientKey:{}, secSid:{}, side:{}, price:{}, quantity:{}, orderType:{}, tif:{}]",
					request.clientKey(),
					request.secSid(),
					request.side().name(),
					request.limitPrice(),
					request.quantity(),
					request.orderType().name(),
					request.tif().name());
			
			CompletableFuture<OrderRequest> requestFuture;
			if (request.orderType() != OrderType.LIMIT_THEN_CANCEL_ORDER){
				requestFuture = messenger.sendNewOrder(refMgr.omes(), NewOrderRequest.of(messenger.self(), request));
			}
			else {
				requestFuture = messenger.sendNewCompositeOrder(refMgr.omes(), NewOrderRequest.of(messenger.self(), request));
			}
			requestFuture.whenComplete(new BiConsumer<OrderRequest, Throwable>() {
				@Override
				public void accept(OrderRequest request, Throwable throwable) {
					try {
						ClientRequestContext removed = standAloneDashboardClientOrderRequests.remove(request.clientKey());
						if (removed != null){
							LOG.info("Complete new order request future, sending order request completion to standalone service [clientKey:{}, orderSid:{}]", removed.clientId, request.orderSid());
							sendOrderRequestCompletion(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, request.orderSid(), request.completionType(), request.rejectType(), OrderSender.ORDER_REQUEST_COMPLETION_EMPTY_REASON);
							return;
						}
						LOG.error("New order request completed for a clientKey that no longer exists [clientKey:{}]", request.clientKey());						
					}
					catch (Exception e){
						LOG.error("Caught exception", e);
					}
				}
			});
		}
	};
	
	private final Handler<CancelOrderRequestSbeDecoder> cancelOrderRequestHandler = new Handler<CancelOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder request) {
			CompletableFuture<OrderRequest> requestFuture = messenger.sendCancelOrder(refMgr.omes(), CancelOrderRequest.of(messenger.self(), request));
			requestFuture.whenComplete(new BiConsumer<OrderRequest, Throwable>() {
				@Override
				public void accept(OrderRequest request, Throwable throwable) {
					ClientRequestContext removed = standAloneDashboardClientOrderRequests.remove(request.clientKey());
					if (removed != null){
						sendOrderRequestCompletion(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, request.orderSid(), request.completionType(), request.rejectType(), OrderSender.ORDER_REQUEST_COMPLETION_EMPTY_REASON);
						return;
					}
					LOG.error("Cancel order request completed for a clientKey that no longer exists [clientKey:{}]", request.clientKey());
				}
			});
		}
	};

	/**
	 * 
	 */
	private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
			LOG.info("Received request [{}]", RequestDecoder.decodeToString(request, new byte[1024]));
			int clientKey = request.clientKey();
//			boolean special = false;
			try{
				switch (request.requestType()){
				case SUBSCRIBE:
	            case GET:
	            case GET_AND_SUBSCRIBE:
	            case UPDATE:
	            case CREATE: {
	                final MessageSinkRef destSink; 
					ImmutableListMultimap<ParameterType, Parameter> mm = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
					List<Parameter> serviceTypeList = mm.get(ParameterType.SERVICE_TYPE);
					ServiceType specifiedServiceType = ServiceType.NULL_VAL;
					TemplateType specifiedTemplateType = TemplateType.NULL_VAL;
					if (!serviceTypeList.isEmpty()){
						specifiedServiceType = ServiceType.get(serviceTypeList.get(0).valueLong().byteValue());
						if (specifiedServiceType != ServiceType.StrategyService){
							destSink = getSinkFor(refMgr, specifiedServiceType);
						}
						else {
							Optional<MessageSinkRef> strategySinkRef = getSpecificStrategySinkRef(mm, refMgr);
							if (!strategySinkRef.isPresent()){
								removeAndCompleteRequestOnWarning(clientKey, 
										"Parameter missing for strategy related request [require:" + ParameterType.SINK_ID.name() + "]");								
								return;
							}
							destSink = strategySinkRef.get();
						}
					}
					else {
						List<Parameter> parameterList = mm.get(ParameterType.TEMPLATE_TYPE);
						if (parameterList.isEmpty()){
							// Send response with result FAILED
							removeAndCompleteRequestOnWarning(clientKey, 
									"Parameter missing [require:" + ParameterType.TEMPLATE_TYPE.name() + "]");
							return;
						}
						specifiedTemplateType = TemplateType.get(parameterList.get(0).valueLong().byteValue());
						switch (specifiedTemplateType){
						case SERVICE_STATUS:
							handleOwnServiceStatusRequest(buffer, offset, header, request, mm);
							return;
						case SECURITY:
						case EXCHANGE:
						case ISSUER:
							destSink = refMgr.rds();
							break;
						case AggregateOrderBookUpdate:
						case ORDERBOOK_SNAPSHOT:
						case MARKET_STATS:
							destSink = refMgr.mdsss();
							break;
						case ORDER:
						case TRADE:
							LOG.debug("Received an order/trade request [senderSinkId:{}]", header.senderSinkId());
							destSink = refMgr.otss();
							break;
						case POSITION:
						case RISK_CONTROL:
						case RISK_STATE:
							destSink = refMgr.risk();
							break;
						case RESPONSE:
//							special = true;
							destSink = null;
							break;
						case SCOREBOARD_SCHEMA:
						case SCOREBOARD:
						    destSink = refMgr.scoreboard();
						    break;						    
						default:
							destSink = null;
						}
					}
//					if (special){
//						// Send response back to web
//						int serviceClientKey = request.clientKey();
//						WebCallContext<Request> removed = webClientRequests.remove(serviceClientKey);
//						ClientKey clientKeyObj = removed.clientKeyObject;
//						Optional<Long> parameterValue = Parameter.getParameterIfOnlyOneExist(mm, ParameterType.PARAM_VALUE);
//						long expectedResponseCount = parameterValue.orElseGet(() -> { return 3000l; });
//						LOG.info("Sending {} responses", expectedResponseCount);
//						int responseMsgSeq = ServiceConstant.START_RESPONSE_SEQUENCE;
//						BooleanType isLast = BooleanType.FALSE; 
//						for (int i = 0; i < expectedResponseCount; i++){
//							Order order = createOrderResponse(serviceClientKey, responseMsgSeq++, isLast);			
//							headerDecoder.wrap(responseBuffer, 0);
//							responseDecoder.wrap(responseBuffer, headerDecoder.encodedLength(), ResponseSbeDecoder.BLOCK_LENGTH, ResponseSbeDecoder.SCHEMA_VERSION);
//							webService.handleResponse(clientKeyObj, responseBuffer, 0, headerDecoder, responseDecoder);
//							
//							createTradeResponse(serviceClientKey, responseMsgSeq++, isLast, order);
//							responseDecoder.wrap(responseBuffer, headerDecoder.encodedLength(), ResponseSbeDecoder.BLOCK_LENGTH, ResponseSbeDecoder.SCHEMA_VERSION);
//							webService.handleResponse(clientKeyObj, responseBuffer, 0, headerDecoder, responseDecoder);
//						}
//						createResponse(serviceClientKey, responseMsgSeq, BooleanType.TRUE);
//						webService.handleResponse(clientKeyObj, responseBuffer, 0, headerDecoder, responseDecoder);
//						LOG.info("Completed");
//						
//						// Complete request
//						Request requestObj = Request.of(header.senderSinkId(), request.requestType(), new ImmutableList.Builder<Parameter>().build());
//						requestObj.resultType(ResultType.OK);						
//						removed.future.complete(requestObj);
//					}
					if (destSink != null) {
	                    // Send request out to service
	                    CompletableFuture<Request> requestFuture = messenger.sendRequest(buffer, offset, destSink, request, mm);
	                    requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
	                        @Override
	                        public void accept(Request request, Throwable throwable) {
	                        	LOG.info("Received response for request [clientKey:{}]", request.clientKey());
	                            // Check both
	                        	ClientRequestContext removed = standAloneDashboardClientRequests.remove(request.clientKey());
	                        	if (removed != null){
	                        		// All response would have been sent back to client in responseHandler
	                        		// Only remaining case is a failure that has not been 'traversed' thru responseHandler.
	                        		// That can be a timeout failure or an unknown failure, for these two cases, 
	    							// we need to send a response back to client explicitly to 'complete a request'.  For other ResultType != OK cases,
	                        		// it doesn't hurt to send back a duplicate response failure (client will drop it if it cannot find the corresponding request)
	                        		if (throwable != null || request.resultType() != ResultType.OK){
	                        			sendResponse(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, ResultType.FAILED, ServiceConstant.START_RESPONSE_SEQUENCE);
	                        		}
	                        		return;
	                        	}
	                        	
	                        	LOG.error("Request completed for a clientKey that no longer exists [clientKey:{}]", request.clientKey());
	                        }
	                    });                 
					}
					else {
					    removeAndCompleteRequestOnWarning(clientKey, 
					    		"Unsupported templateType/serviceType [templateType:" + specifiedTemplateType.name() + ", serviceType:" + specifiedServiceType.name() +"]");
					}
					break;
				}            
				default:
					removeAndCompleteRequestOnWarning(clientKey, 
							"Unsupported requestType [requestType:" + request.requestType().name() + "]");
					break;
				}
			}
			catch (Exception e) {
				removeAndCompleteRequestOnError(clientKey, 
						"Caught exception when processing request [request:" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()), 
						e);
				return;
			}
		}		
	};

	private void handleOwnServiceStatusRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request, ListMultimap<ParameterType, Parameter> mm) {
//		ClientRequestContext clientRequestContext = standAloneDashboardClientRequests.get(request.clientKey());
//		if (clientRequestContext != standAloneDashboardClientRequests.defaultReturnValue()){
			// Don't think this is needed
//			standAloneDashboardClientRequests
//		}
//		else {
//			WebCallContext<Request> result = webClientRequests.get(request.clientKey());
//			webService.handleServiceStatusesAsResponse(result.clientKeyObject, this.serviceStatuses());			
//		}
	}
	
//	private WebCallContext<Request> prevCallContext = NULL_REQUEST_WEB_CALL_CONTEXT;
	
    private ClientRequestContext prevRequestContext = null;
    private int prevRequestClientKey = -1;
	/**
	 * Capable of handling response for both web service and standalone dashboard service 
	 */
	private final Handler<ResponseSbeDecoder> responseHandler = new Handler<ResponseSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder response) {
			responseCount.incrementAndGet();
			
			try {
				// This response will be processed by DashboardService.requestTracker and the corresponding future will be completed
				// Look up clientKey to see if there is any outstanding request
                int clientKey = response.clientKey();
                ClientRequestContext context;
                if (prevRequestClientKey == clientKey){
                    context = prevRequestContext;
                }
                else {
                    context = standAloneDashboardClientRequests.get(clientKey);
                    prevRequestClientKey = clientKey;
                    prevRequestContext = context;
                }
                if (context != null){
					//LOG.info("Sending response back to standalone service [clientKey:{}]", context.clientId);
					int length = header.encodedLength() + header.payloadLength();
					context.context.copyBytesAndReplaceClientId(buffer, offset, length, header, sendBuffer, context.clientId);
					onData(sendBuffer, offset, length, null);
				}
				else {
					LOG.error("Received a response with no request [clientKey:{}]", response.clientKey());
				}				
			}
			catch (Exception e){
				LOG.error("Caught exception when handling response", e);
				throw e;
			}
		}
	};
	
	private void removeAndCompleteRequestOnError(int clientKey, String msg){
		ClientRequestContext clientRequestContext = standAloneDashboardClientRequests.get(clientKey);
		if (clientRequestContext != null){
			ClientRequestContext removed = standAloneDashboardClientRequests.remove(clientKey);
			sendResponse(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, ResultType.FAILED, ServiceConstant.START_RESPONSE_SEQUENCE);
			LOG.error(msg);
		}
		else {
			LOG.error("Received unexpected web service request");
		}
	}
	
	private void removeAndCompleteRequestOnWarning(int clientKey, String msg){
		ClientRequestContext clientRequestContext = standAloneDashboardClientRequests.get(clientKey);
		if (clientRequestContext != null){
			ClientRequestContext removed = standAloneDashboardClientRequests.remove(clientKey);
			sendResponse(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, ResultType.FAILED, ServiceConstant.START_RESPONSE_SEQUENCE);
			LOG.warn(msg);
		}
		else {
			LOG.error("Received unexpected web service request");
		}
	}
	
	private void removeAndCompleteRequestOnError(int clientKey, String msg, Throwable cause){
		ClientRequestContext context = standAloneDashboardClientRequests.get(clientKey);
		if (context != null){
			ClientRequestContext removed = standAloneDashboardClientRequests.remove(clientKey);
			sendResponse(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, ResultType.FAILED, ServiceConstant.START_RESPONSE_SEQUENCE);
			LOG.error(msg, cause);
		}
		else {
			LOG.error("Received unexpected web service request");
		}
	}

	private void removeAndCompleteCommandOnError(int clientKey, String msg, Throwable cause){
		ClientRequestContext context = standAloneDashboardClientCommands.get(clientKey);
		if (context != null){
			ClientRequestContext removed = standAloneDashboardClientCommands.remove(clientKey);
			sendCommandAck(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, CommandAckType.FAILED);
			LOG.error(msg, cause);
		}
		else {
			LOG.error("Received unexpected web service command");
		}
	}

	private void removeAndCompleteCommandOnWarning(int clientKey, String msg){
		ClientRequestContext context = standAloneDashboardClientCommands.get(clientKey);
		if (context != null){
			ClientRequestContext removed = standAloneDashboardClientCommands.remove(clientKey);
			sendCommandAck(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, CommandAckType.FAILED);
			LOG.warn(msg);
		}
		else {
			LOG.error("Received unexpected web service command");
		}
	}

//	private static <T> void removeAndCompleteOnError(int clientKey, String msg, ConcurrentHashMap<Integer, WebCallContext<T>> items){
//		WebCallContext<T> result = items.remove(clientKey);
//		result.future.completeExceptionally(new Throwable(msg));
//		LOG.error(msg);
//	}

//	private static <T> void removeAndCompleteOnError(int clientKey, String msg, Throwable cause, ConcurrentHashMap<Integer, WebCallContext<T>> items){
//		WebCallContext<T> result = items.remove(clientKey);
//		result.future.completeExceptionally(new Throwable(msg, cause));
//		LOG.error(msg, cause);
//	}

	private static MessageSinkRef getSinkFor(MessageSinkRefMgr refMgr, ServiceType serviceType){
		switch (serviceType){
		case AdminService:
			return refMgr.admin();
		case MarketDataService:
			return refMgr.mds();
		case MarketDataSnapshotService:
			return refMgr.mdsss();
		case OrderAndTradeSnapshotService:
			return refMgr.otss();
		case OrderManagementAndExecutionService:
			return refMgr.omes();
		case PerformanceService:
			return refMgr.perf();
		case PersistService:
			return refMgr.persi();
		case PortfolioAndRiskService:
			return refMgr.risk();
		case RefDataService:
			return refMgr.rds();
		case NotificationService:
			return refMgr.ns();
		case PricingService:
			return refMgr.prc();
        case ScoreBoardService:
            return refMgr.scoreboard();
		default:
			throw new UnsupportedOperationException("Service type not supported [serviceType:" + serviceType.name() + "]");
		}
	}
	
	private Optional<MessageSinkRef> getSpecificStrategySinkRef(ImmutableListMultimap<ParameterType, Parameter> parameters, MessageSinkRefMgr mgr){
		Optional<Long> sinkId = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SINK_ID);
		if (!sinkId.isPresent()){
			return Optional.empty();			
		}
		MessageSinkRef sinkRef = mgr.get(sinkId.get().intValue());
		if (sinkRef.serviceType() != ServiceType.StrategyService){
			return Optional.empty();
		}
		return Optional.of(sinkRef);
	}
	
	private final Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder command) {
			LOG.info("Received command [{}]", CommandDecoder.decodeToString(command));
			int clientKey = command.clientKey();
			final MessageSinkRef destSink; 
			try {
				switch (command.commandType()){
				case START:
				case STOP:
					destSink = refMgr.admin();
					break;
				case START_TEST_MODE:
				case STOP_TEST_MODE:
				{
					ImmutableListMultimap<ParameterType, Parameter> mm = CommandDecoder.generateParameterMap(messenger.stringBuffer(), command);
					Optional<MessageSinkRef> strategySinkRef = getSpecificStrategySinkRef(mm, refMgr);
					if (!strategySinkRef.isPresent()){
						removeAndCompleteCommandOnWarning(clientKey, 
								"Parameter missing for strategy related request [require:" + ParameterType.SINK_ID.name() + "]");
						return;							
					}
				    destSink = strategySinkRef.get();
				    break;
				}
				default:
					ImmutableListMultimap<ParameterType, Parameter> mm = CommandDecoder.generateParameterMap(messenger.stringBuffer(), command);
					List<Parameter> serviceTypeList = mm.get(ParameterType.SERVICE_TYPE);
					ServiceType specifiedServiceType = ServiceType.get(serviceTypeList.get(0).valueLong().byteValue());
					
					// For strategy, sinkId is required
					if (specifiedServiceType != ServiceType.StrategyService){
						destSink = getSinkFor(refMgr, specifiedServiceType);						
					}
					else{
						Optional<MessageSinkRef> strategySinkRef = getSpecificStrategySinkRef(mm, refMgr);
						if (!strategySinkRef.isPresent()){
							removeAndCompleteCommandOnWarning(clientKey, 
									"Parameter missing for strategy related request [require:" + ParameterType.SINK_ID.name() + "]");
							return;							
						}
						destSink = strategySinkRef.get();
	
					}
					LOG.info("Resolve destSink to {}, {}", specifiedServiceType.name(), destSink);
					break;
				}
				CompletableFuture<Command> commandFuture = messenger.sendCommand(destSink, command);
				commandFuture.whenComplete(new BiConsumer<Command, Throwable>() {
					@Override
					public void accept(Command command, Throwable throwable) {
						LOG.debug("Command completed [clientKey:{}]", command.clientKey());
						
						ClientRequestContext removed = standAloneDashboardClientCommands.remove(command.clientKey());
						if (removed != null){
                    		// Removed request from standalone request successfully
                    		if (throwable == null){
                    			sendCommandAck(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, CommandAckType.OK);
                    		}
                    		else {
                    			sendCommandAck(removed.context, removed.context.contextDisruptorThreadBuffer, STANDALONE_DASHBOARD_COMMON_SINK_ID, removed.clientId, CommandAckType.FAILED);
                    		}	                        		
                    		return;
						}
						LOG.error("Command completed for a clientKey that no longer exists [clientKey:{}]", command.clientKey());
					}
				});				
			}
			catch (Exception e) {
				removeAndCompleteCommandOnError(clientKey, 
						"Caught exception when processing command [command:" + CommandDecoder.decodeToString(command),
						e);
				return;
			}
		}
	}; 
	
	@Override
	public void activeExit() {
	}

	@Override
	public StateTransitionEvent stopEnter() {
		LOG.info("Stop enter dashboard");
		messenger.receiver().cancelOrderRequestHandlerList().remove(cancelOrderRequestHandler);
		messenger.receiver().commandHandlerList().remove(commandHandler);
		messenger.receiver().newOrderRequestHandlerList().remove(newOrderRequestHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
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
//		webService.stop();
		closeServerSocket();
		return StateTransitionEvent.NULL;
	}
	
	private void closeServerSocket(){
		listeningForClientConnection.set(false);
		if (serverSocket != null && !serverSocket.isClosed()){
			try {
				serverSocket.close();
			}
			catch (IOException e) {
				LOG.error("Caught exception when closing server socket", e);
			}
			serverSocket = null;
		}		
	}
	
	@Override
	public StateTransitionEvent stoppedEnter() {
//		if (webService.started()){
//			webService.stop();
//		}
		closeServerSocket();
		return StateTransitionEvent.NULL;
	}
	
	public ObjectCollection<ServiceStatus> serviceStatuses(){
		return this.messenger.serviceStatusTracker().statuses();
	}
	
	public void handleExternalStopCommand(int sinkId){
		messenger.sendCommand(messenger.referenceManager().admin(),
				Command.of(this.messenger.self().sinkId(),
						this.messenger.getNextClientKeyAndIncrement(),
						CommandType.STOP,
						Arrays.asList(Parameter.of(ParameterType.SINK_ID, sinkId))));
	}
	
	public CompletableFuture<Command> handleExternalCommandAsync(ClientKey clientKey, Command command){
		CompletableFuture<Command> resultFuture = new CompletableFuture<Command>();
		// Change the clientKey of the request to be based on this service's own messenger
		// such that we can match responses back to this request
//		int ownClientKey = this.messenger.getNextClientKeyAndIncrement();
//		command.clientKey(ownClientKey);
//		webClientCommands.put(ownClientKey, WebCallContext.of(clientKey, resultFuture));
//		webServiceRequestExecutor.execute(() -> {
//			sendCommandToSelf(command);
//		});
		return resultFuture;
	}

	public CompletableFuture<OrderRequest> handleExternalNewOrderRequestAsync(ClientKey clientKey, NewOrderRequest request){
		CompletableFuture<OrderRequest> future = new CompletableFuture<>();
//		int ownClientKey = this.messenger.getNextClientKeyAndIncrement();
//		request.clientKey(ownClientKey);
//		webClientOrderRequests.put(ownClientKey, WebCallContext.of(clientKey, future));
//		webServiceRequestExecutor.execute(() -> {
//			sendNewOrderRequestToSelf(request);
//		});
		return future;
	}
	
	public CompletableFuture<OrderRequest> handleExternalCancelOrderRequestAsync(ClientKey clientKey, CancelOrderRequest request){
		CompletableFuture<OrderRequest> future = new CompletableFuture<>();
//		int ownClientKey = this.messenger.getNextClientKeyAndIncrement();
//		request.clientKey(ownClientKey);
//		webClientOrderRequests.put(ownClientKey, WebCallContext.of(clientKey, future));
//		webServiceRequestExecutor.execute(() -> {
//			sendCancelOrderRequestToSelf(request);
//		});
		return future;
	}

	/**
	 * Handle request from dashboard web service
	 * @param clientKey
	 * @param request
	 * @return
	 */
	public CompletableFuture<Request> handleExternalRequestAsync(ClientKey clientKey, Request request){
		CompletableFuture<Request> resultFuture = new CompletableFuture<Request>();
//		try {
//			switch (request.requestType()){
//			case SUBSCRIBE:
//			case GET:
//			case GET_AND_SUBSCRIBE:
//			case UPDATE:
//			case CREATE:
//				// Change the clientKey of the request to be based on this service's own messenger
//				// such that we can match responses back to this request
//				int serviceClientKey = this.messenger.getNextClientKeyAndIncrement();
//				request.clientKey(serviceClientKey);
//                LOG.info("Handle external request [webClientKey:{}, serviceClientKey:{}, requestType:{}]", clientKey.key(), serviceClientKey, request.requestType().name());
//				webClientRequests.put(serviceClientKey, WebCallContext.of(clientKey, resultFuture));
//				webServiceRequestExecutor.execute(() -> {
//					sendRequestToSelf(request);
//				});
//				break;
//			default:
//				resultFuture.completeExceptionally(new Throwable("Receive unexpected request type [requestType:" + request.requestType().name() + "]"));
//				LOG.warn("Receive unexpected request type [requestType:{}]", request.requestType().name());
//				break;
//			}
//		}
//		catch (Exception ex){
//			LOG.warn("Caught exception when handling request [requestType:{}, clientKey:{}]", request.requestType().name(), clientKey);
//		}
		return resultFuture;
	}
	
//	private void sendRequestToSelf(Request request){
//		try {
//			if (!request.entity().isPresent()){
//				long result = webServiceMessenger.requestSender().sendRequest(this.messenger.self(), request);
//				if (result != MessageSink.OK){
//					removeAndCompleteRequestOnError(request.clientKey(), "Request could not be sent to Dashboard service [result:" + result + ", clientKey:" + request.clientKey() + "]");
//				}
//			}
//			else{
//				if (!webServiceMessenger.requestSender().sendEntity(this.messenger.self(), request, request.entity().get())){
//					removeAndCompleteRequestOnError(request.clientKey(), 
//							"Entity Request could not be sent to Dashboard service [clientKey:" + request.clientKey() + "]");				
//				}
//			}			
//		}
//		catch (Exception ex){
//			LOG.error("Caught exception in sendRequestToSelf", ex);
//		}
//	}
	
//	private void sendCommandToSelf(Command command){
//		try {
//			long result = webServiceMessenger.commandSender().sendCommand(this.messenger.self(), command);
//			if (result != MessageSink.OK){
//				removeAndCompleteCommandOnError(command.clientKey(), 
//						"Command could not be sent to Dashboard service [result:" + result + ", clientKey:" + command.clientKey() + "]");
//			}			
//		}
//		catch (Exception e){
//			LOG.error("Caught exception in sendCommandToSelf", e);
//		}
//	}
	
//	private void sendNewOrderRequestToSelf(NewOrderRequest request){
//		try {
//			long result = webServiceMessenger.orderSender().sendNewOrder(this.messenger.self(), request);
//			if (result != MessageSink.OK){
//				removeAndCompleteOrderRequestOnError(request.clientKey(), 
//						"New order request could not be sent to Dashboard service [result:" + result + ", clientKey:" + request.clientKey() + "]");
//			}			
//		}
//		catch (Exception e){
//			LOG.error("Caught exception in sendNewOrderRequestToSelf", e);			
//		}
//	}
	
//	private void sendCancelOrderRequestToSelf(CancelOrderRequest request){
//		try {
//			long result = webServiceMessenger.orderSender().sendCancelOrder(this.messenger.self(), request);
//			if (result != MessageSink.OK){
//				removeAndCompleteOrderRequestOnError(request.clientKey(), 
//						"Cancel order request could not be sent to Dashboard service [result:" + result + ", clientKey:" + request.clientKey() + "]");
//			}
//		}
//		catch (Exception e){
//			LOG.error("Caught exception in sendCancelOrderRequestToSelf", e);			
//		}
//	}

	private final Handler<AggregateOrderBookUpdateSbeDecoder> aggregateOrderBookUpdateHandler = new Handler<AggregateOrderBookUpdateSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder codec) {
			onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
		}
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, AggregateOrderBookUpdateSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
	};
	
	private final Handler<OrderSbeDecoder> orderHandler = new Handler<OrderSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder codec) {
            orderCount.incrementAndGet();
            onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
	};
	
	private void sendOrderRequestCompletion(ClientConnectionContext connectionContext, MutableDirectBuffer dstBuffer, int sinkId, int clientKey, int ordSid, OrderRequestCompletionType completionType,
			OrderRequestRejectType rejectType,
			byte[] reason){
		int length = connectionContext.clientConnectionMessenger.orderSender().encodeOrderRequestCompletion(sinkId,
				dstBuffer,
				0, 
				clientKey, 
				ordSid, completionType, rejectType, reason);
		onData(dstBuffer, 0, length, connectionContext);
	}
	
	private void sendCommandAck(ClientConnectionContext connectionContext, MutableDirectBuffer dstBuffer, int sinkId, int clientKey, CommandAckType resultType){
		int length = connectionContext.clientConnectionMessenger.commandAckSender().encodeCommandAck(
				dstBuffer, 
				0,
				sinkId,
				clientKey,
				clientKey,
				resultType,
				CommandType.NA);
		onData(dstBuffer, 0, length, connectionContext);
	}

	private void sendResponse(ClientConnectionContext connectionContext, MutableDirectBuffer dstBuffer, int sinkId, int clientKey, ResultType resultType, int seq){
		LOG.info("Sending response back to standalone service [clientKey:{}]", clientKey);
		int length = connectionContext.clientConnectionMessenger.responseSender().encodeResponse(
				dstBuffer,
				sinkId, 
				clientKey, 
				ServiceConstant.START_RESPONSE_SEQUENCE, 
				resultType);
		onData(dstBuffer, 0, length, connectionContext);
	}
	
	private void sendBytesToClientSocket(ClientConnectionContext connectionContext, DirectBuffer buffer, int offset, int length){
		try {			
//			LOG.info("Sending bytes to client socket");
			connectionContext.setSentBeginAtNano();
			connectionContext.outputStream.write(buffer.byteArray(), offset, length);
			connectionContext.outputStream.flush();
			connectionContext.clearSentBeginAtNano();
//			LOG.info("Send bytes to client socket");
		} 
		catch (IOException e) {
			LOG.error("Could not send byte to the other side [address:{}]", connectionContext.clientSocket.getInetAddress());

			// Remove this clientSocket if problem persists
			if (connectionContext.incrementFailedCount() > NUM_FAILED_DELIVERY_ATTEMPTS_THRESHOLD){
				removeClientConnection(connectionContext);
			}
		}
	}
	
	/**
	 * Expect this method to be called only by the Dashboard's main disruptor thread
	 * @param buffer
	 * @param offset
	 * @param header
	 */
	private final ObjectArrayList<ClientConnectionContext> failedConnections = new ObjectArrayList<ClientConnectionContext>();
	private final MutableDirectBuffer sendBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	
	private void killBlockingConnection(){
		int size = clientConnectionContexts.size();
		int index = 0;
		for (index = 0; index < size; index++){
			ClientConnectionContext connectionContext = clientConnectionContexts.elements()[index];
			try {
				
				if (connectionContext.blockedTooLong()){
					LOG.warn("Detected connection has been blocked for a long time, killing it [address:{}]", connectionContext.clientSocket.getInetAddress());
					// Close it
					connectionContext.clientSocket.close();
					failedConnections.add(connectionContext);
				}
			} 
			catch (Exception e){
				LOG.error("Caught exception when closing a blocked connection [index:{}, address:{}]", index, clientConnectionContexts.elements()[index].clientSocket.getInetAddress());
				// Remove this clientSocket if problem persists
				failedConnections.add(connectionContext);
			}
		}
		if (!failedConnections.isEmpty()){
			for (ClientConnectionContext context : failedConnections){
				removeClientConnection(context);
			}
			failedConnections.clear();
		}
	}
	
	private void sendBytes(byte[] buffer, int offset, int length, ClientConnectionContext specificContext, ObjectArrayList<ClientConnectionContext> failed){
		try {
			specificContext.setSentBeginAtNano();
			specificContext.outputStream.write(buffer, offset, length);
			
			// We may still get blocked here if the other side is not ready
			specificContext.outputStream.flush();
			specificContext.clearSentBeginAtNano();
		} 
		catch (IOException e) {
			LOG.error("Could not send byte to the other side [address:{}]", specificContext.clientSocket.getInetAddress());
			LOG.error("Exception details: ", e);

			// Remove this clientSocket if problem persists
			if (specificContext.incrementFailedCount() > NUM_FAILED_DELIVERY_ATTEMPTS_THRESHOLD){
				failed.add(specificContext);
			}
		}
		catch (Exception e){
			LOG.error("Caught exception when sending byte to the other side [address:{}]", specificContext.clientSocket.getInetAddress());
			LOG.error("Exception details: ", e);
			// Remove this clientSocket if problem persists
			if (specificContext.incrementFailedCount() > NUM_FAILED_DELIVERY_ATTEMPTS_THRESHOLD){
				failed.add(specificContext);
			}
		}		
	}
	
	private void sendBytesToClientSockets(byte[] buffer, int offset, int length, ClientConnectionContext specificContext){
		lock.lock();
		try {			
			if (specificContext != null){
				sendBytes(buffer, offset, length, specificContext, failedConnections);
			}
			else {
				int size = clientConnectionContexts.size();
				int index = 0;
				for (index = 0; index < size; index++){
					sendBytes(buffer, offset, length, clientConnectionContexts.elements()[index], failedConnections);	
				}				
			}
			
			if (!failedConnections.isEmpty()){
				for (ClientConnectionContext context : failedConnections){
					removeClientConnection(context);
				}
				failedConnections.clear();
			}			
		}
		catch (Exception e){
			LOG.error("Caught exception when trying to send bytes to client", e);
		}
		finally {
			lock.unlock();
		}
	}
	
    private final Handler<TradeSbeDecoder> tradeHandler = new Handler<TradeSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder codec) {
        	orderCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, TradeSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<ScoreBoardSbeDecoder> scoreBoardHandler = new Handler<ScoreBoardSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSbeDecoder codec) {
        	scoreboardCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, ScoreBoardSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<NoteSbeDecoder> noteHandler = new Handler<NoteSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NoteSbeDecoder codec) {
//        	LOG.info("Received note in dashboard [{}]", NoteDecoder.decodeToString(codec));
        	noteCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, NoteSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<ChartDataSbeDecoder> chartDataHandler = new Handler<ChartDataSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ChartDataSbeDecoder codec) {
//        	LOG.info("Received note in dashboard [{}]", NoteDecoder.decodeToString(codec));
        	chartDataCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, ChartDataSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<EventSbeDecoder> eventHandler = new Handler<EventSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
        	eventCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }    	
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, EventSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<PositionSbeDecoder> positionHandler = new Handler<PositionSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
        	positionCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }    	
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, PositionSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<RiskStateSbeDecoder> riskStateHandler = new Handler<RiskStateSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder codec) {
        	riskCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, RiskStateSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<ServiceStatusSbeDecoder> serviceStatusHandler = new Handler<ServiceStatusSbeDecoder>(){
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder codec) {
        	serviceStatusCount.incrementAndGet(); 
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
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
        	strategyCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyUndParamsSbeDecoder> strategyUndParamsHandler = new Handler<StrategyUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder codec) {
        	strategyCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyUndParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyIssuerParamsSbeDecoder> strategyIssuerParamsHandler = new Handler<StrategyIssuerParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder codec) {
        	strategyCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyIssuerParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyIssuerUndParamsSbeDecoder> strategyIssuerUndParamsHandler = new Handler<StrategyIssuerUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder codec) {
//        	LOG.info("Received strategy issuer und in dashboard");
        	strategyCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyIssuerUndParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };

    private final Handler<StrategyWrtParamsSbeDecoder> strategyWrtParamsHandler = new Handler<StrategyWrtParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder codec) {
        	strategyWarrantCount.incrementAndGet();
            if (messenger.timerService().toNanoOfDay() >= nextLogTimeNs.get()){
            	logMessageCount();
            	nextLogTimeNs.set(messenger.timerService().toNanoOfDay() + LOG_FREQ);
            }
            onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategyWrtParamsSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<StrategySwitchSbeDecoder> strategySwitchHandler = new Handler<StrategySwitchSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder codec) {
        	strategyCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, StrategySwitchSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<OrderBookSnapshotSbeDecoder> orderBookSnapshotHandler = new Handler<OrderBookSnapshotSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder codec) {
        	orderCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderBookSnapshotSbeDecoder codec) {
        	// Response will be sent transparently to webService
        }
    };
    
    private final Handler<MarketStatsSbeDecoder> marketStatsHandler = new Handler<MarketStatsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder codec) {
        	marketDataCount.incrementAndGet();
        	onData(buffer, offset, header.encodedLength() + header.payloadLength(), null);
        }
        @Override
        public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, MarketStatsSbeDecoder codec) {
            // Response will be sent transparently to webService
        }
    };
    
	int sinkId = 1;

	ConcurrentHashMap<Integer, ClientRequestContext> standAloneDashboardClientRequests(){
		return standAloneDashboardClientRequests;
	}
	
	ConcurrentHashMap<Integer, ClientRequestContext> standAloneDashboardClientCommands(){
		return standAloneDashboardClientCommands;
	}
	
	ConcurrentHashMap<Integer, ClientRequestContext> standAloneDashboardClientOrderRequests(){
		return standAloneDashboardClientOrderRequests;
	}
}
