package com.lunar.message.binary;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lmax.disruptor.RingBuffer;
import com.lunar.core.CommandTracker;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;
import com.lunar.core.ServiceStatusTracker.ServiceStatusChangeHandler;
import com.lunar.core.SubscriberList;
import com.lunar.core.TimerService;
import com.lunar.entity.Note;
import com.lunar.fsm.channelbuffer.ChannelBufferContext;
import com.lunar.fsm.channelbuffer.ChannelMissingMessageRequester;
import com.lunar.message.Command;
import com.lunar.message.MessageFactory;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.BoobsSender;
import com.lunar.message.sender.ChartDataSender;
import com.lunar.message.sender.CommandAckSender;
import com.lunar.message.sender.CommandSender;
import com.lunar.message.sender.EchoSender;
import com.lunar.message.sender.EventSender;
import com.lunar.message.sender.IssuerSender;
import com.lunar.message.sender.MarketDataSender;
import com.lunar.message.sender.MarketDataTradeSender;
import com.lunar.message.sender.MarketStatsSender;
import com.lunar.message.sender.MarketStatusSender;
import com.lunar.message.sender.MessageSender;
import com.lunar.message.sender.MessageSenderBuilder;
import com.lunar.message.sender.NoteSender;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sender.PerformanceSender;
import com.lunar.message.sender.PingSender;
import com.lunar.message.sender.PositionSender;
import com.lunar.message.sender.PricingSender;
import com.lunar.message.sender.RequestSender;
import com.lunar.message.sender.ResponseSender;
import com.lunar.message.sender.RiskStateSender;
import com.lunar.message.sender.ScoreBoardSender;
import com.lunar.message.sender.SecuritySender;
import com.lunar.message.sender.SenderBuilder;
import com.lunar.message.sender.ServiceStatusSender;
import com.lunar.message.sender.StrategySender;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.message.sink.MessageSinkRefMgrProxy;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.order.OrderChannelBuffer;
import com.lunar.order.OrderReqAndExecTracker;
import com.lunar.order.OrderRequest;
import com.lunar.service.ServiceConstant;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * Handle fragmentation as well.
 * 
 * Each frame has a fixed size.
 * If we want to encode something with size greater than a frame, we will need to split it into N frames.
 * 
 * With ring buffer:
 * 1. If message size is <= frame size, we can encode directly.
 * 2. If message size is > frame size, we need to break that into multiple container messages
 * 
 * In our decoder, we need buffer to hold fragmented messages.  The easiest way is to use a hash map
 * to hold messages of same message id.  Once we have received the last message, we copy them 
 * into bigger frame (a large frame) and call the corresponding handler.
 * 
 * All these works would yield better cache hit rates for majority of the messages (i.e. market data), but
 * definitely slower for large messages.  Also, we would need to use memory on heap.
 * 
 * 
 * How to handle fragments of a message?
 * 1. What is fragment?  When a message is too big for an event in ring buffer, we need to break the message into smaller
 *    fragments.
 * 2. How to split it?  Each messenger (one thread) should have an id that is unique amount per process (JVM).  This messenger id is
 *    encoded into each fragment.  Also with fragment id and is last bid.  This is the bits layout:
 *    
 *    [sender sink id - 8 bits][messenger id - 8 bits][is last - 8 bits][fragment id - 8 bits][sequence number - 4 bytes]
 *    
 *    Please note that this arrangement is not efficient.  We will make a more compact layout in the future.
 *    
 * 3. When we receive a non-zero fragment id, we know that it is a fragmented message.  The receive will append the message
 *    content to a buffer (e.g. byte array) that is specific to (sender sink id, messenger id).  It will keep appending
 *    until it reaches the one with "is last" set to 1.  We can use the sequence number as a check.
 *    
 *    Missing message - that's generally not possible since:
 *    1. inter-thread communication - we will only drop a message if the buffer is full.  And when buffer is full, we should
 *       stop sending the rest of the fragments.
 *    2. inter-process communication - yea (e.g. udp or ipc using memory file).  In these cases, we should drop all other 
 *       fragments and request for resend.
 *    
 * Handles all communications
 * <p>
 * Needs:
 * <ol>
 * <li>Encoding: The most common sbes should be placed together to aim for cache hits</li>
 * <li>Decoding: Decoding is really mainly being used by matching template id to a sbe.  This matching of template id implies that
 *    we cannot inline the method.  If we have a specific type of message that we want to decode in tight loop, better use
 *    a specific sbe for that.  We need to put the most common message types together: 1) have id close to each other,
 *    2) put them beside each other in this class
 * </li>
 * <li>Different trackers inside this messenger</li>
 * <li>Define ways to send message to one sink, or a list of sinks.  With one sink, we should write directly onto the recipient's
 * message sink.  With multiple sinks, we should make a copy first and copy the message directly.
 * </li>
 * </ol>
 * <strong>TODO</strong> If there is a particular service that needs only a couple of decoders, we should create a specific MessageDecoder with
 * just those decoders, so that we can make better use of cache.
 *    
 */
public class Messenger {
	private static final Logger LOG = LogManager.getLogger(Messenger.class);
	private static final int EXPECTED_NUM_REMOTE_ADMINS = 3;
	private final static int PARENT_MESSENGER_ID = 0;
	/**
	 * We will get weird bug if a messenger sends out more than SEQ_RANGE number of messages.
	 * TODO Use long as seq
	 */
	private final int SEQ_RANGE = 100_000_000;
	
	private final int messengerId;
	private final TimerService timerService;
	private final MessageSinkRefMgrProxy refMgrProxy;
	private final MessageFactory messageFactory;
	private final CommandTracker commandTracker;
	private final RequestTracker requestTracker;
	private final ServiceStatusTracker serviceStatusTracker;
	private long serviceStatusLastUpdateTime;
	private final MessageSinkRef self;
	
	private final MessageReceiver receiver;
	private final SenderBuilder senderBuilder;
	/**
	 * TODO remove
	 */
	private final OrderReqAndExecTracker orderReqTracker;
	private final RequestSender requestSender;

	private final MessageSender messageSender;
	private final OrderSender orderSender;
	private final CommandSender commandSender;
	private final ResponseSender responseSender;
	private final TimerEventSender timerEventSender;
	private final EchoSender echoSender;
	private final CommandAckSender commandAckSender;
	private final PingSender pingSender;
	private final PositionSender positionSender;
	private final RiskStateSender riskStateSender;
	private final SecuritySender secSender;
	private final IssuerSender issuerSender;
	private final MarketDataSender mdSender;	
	private final MarketDataTradeSender tdSender;
	private final MarketStatsSender mstatsSender;
	private final BoobsSender boSender;
	private final PricingSender prSender;
	private final MarketStatusSender msSender;
	private final StrategySender stratSender;
	private final ServiceStatusSender serviceStatusSender;
	private final PerformanceSender perfSender;
	private final ScoreBoardSender scoreBoardSender;
	private final EventSender eventSender;
	private final NoteSender noteSender;
	private final ChartDataSender chartDataSender;
	private final AtomicInteger clientKeySeq;
	private MutableDirectBuffer stringBuffer;
	private byte[] stringByteBuffer;
	private final long[] sinkSendResults;
	private final NoteSbeDecoder noteSbeDecoder;
	
	public static Messenger of(MessageSinkRefMgr messageSinkRefMgr, MessageSinkRef self, TimerService timerService, MessageFactory messageFactory, MessageSender sender, MessageReceiver receiver, SenderBuilder builder){
		return new Messenger(PARENT_MESSENGER_ID, messageSinkRefMgr, self, timerService, messageFactory, sender, receiver, builder);
	}
	
	public void setClientKeySeq(int value){
		this.clientKeySeq.set(value);
	}
	
	Messenger(int messengerId, MessageSinkRefMgr referenceManager, MessageSinkRef self, TimerService timerService, MessageFactory messageFactory, MessageSender sender, MessageReceiver receiver, SenderBuilder builder){
		this.timerService = timerService;
		this.receiver = receiver;
		this.messengerId = messengerId;
		this.self = self;
		this.refMgrProxy = MessageSinkRefMgrProxy.of(self.sinkId(), referenceManager);
		this.messageFactory = messageFactory;
		this.messageSender = sender;
		this.commandSender = CommandSender.of(sender);
		this.commandTracker = CommandTracker.of(self, timerService, messageFactory.commandTimeoutNs(), this.commandSender);
		this.requestSender = builder.buildRequestSender(sender);
		this.requestTracker = builder.buildRequestTracker(self, timerService, messageFactory, this.requestSender);
		this.serviceStatusTracker = ServiceStatusTracker.of(self);
		this.clientKeySeq = new AtomicInteger(messengerId * SEQ_RANGE);
		this.sinkSendResults = new long[ServiceConstant.MAX_SUBSCRIBERS];
		
		/**
		 * hot fields
		 */
		this.secSender = SecuritySender.of(sender);
		this.issuerSender = IssuerSender.of(sender);
		this.orderSender = builder.buildOrderSender(sender);		
		this.orderReqTracker = OrderReqAndExecTracker.of(self, timerService, this.orderSender);
		this.mdSender = MarketDataSender.of(sender);
		this.tdSender = MarketDataTradeSender.of(sender);
		this.mstatsSender = MarketStatsSender.of(sender);
		this.boSender = BoobsSender.of(sender);
		this.prSender = PricingSender.of(sender);
		this.msSender = MarketStatusSender.of(sender);
		this.stratSender = StrategySender.of(sender);
		this.perfSender = PerformanceSender.of(sender);
		this.scoreBoardSender = ScoreBoardSender.of(sender);
		/**
		 * cold fields
		 */
		this.responseSender = ResponseSender.of(sender);
		this.positionSender = PositionSender.of(sender);
		this.riskStateSender = RiskStateSender.of(sender);
		this.timerEventSender = TimerEventSender.of(sender);
		this.echoSender = EchoSender.of(sender);
		this.commandAckSender = CommandAckSender.of(sender);
		this.pingSender = PingSender.of(sender, this.refMgrProxy);
		this.serviceStatusSender = builder.buildServiceStatusSender(this.refMgrProxy.systemId(), sender);
		this.eventSender = EventSender.of(sender);
		this.noteSender = NoteSender.of(sender);
		this.chartDataSender = ChartDataSender.of(sender);
		this.senderBuilder = builder;
		this.stringBuffer = sender.stringBuffer();
		this.stringByteBuffer = this.stringBuffer.byteArray();
		this.noteSbeDecoder = new NoteSbeDecoder();
	}	
	
	public int getNextClientKeyAndIncrement(){
		return this.clientKeySeq.getAndIncrement();
	}
	
	public CompletableFuture<Command> sendCommand(int sinkId, Command command){
		return this.commandTracker.sendAndTrack(this.referenceManager().get(sinkId), command);
	}

	public CompletableFuture<Command> sendCommand(MessageSinkRef sink, Command command){
		return this.commandTracker.sendAndTrack(sink, command);
	}
	
	public CompletableFuture<Command> sendCommand(MessageSinkRef sink, CommandSbeDecoder commandSbe){
		Command command = null;
		try {
			command = Command.of(self.sinkId(), 
					commandSbe.clientKey(), 
					commandSbe.commandType(), 
					CommandDecoder.generateParameterList(stringBuffer, commandSbe), 
					commandSbe.toSend());
		} 
		catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.commandTracker.sendAndTrack(sink, command);
	}

	public void sendCommandAck(MessageSinkRef sink, int key, CommandAckType commandAckType, CommandType commandType){
		this.commandAckSender.sendCommandAck(
				sink,
				key, 
				commandAckType, 
				commandType);
	}

	public void sendCommandAck(int sinkId, int key, CommandAckType commandAckType, CommandType commandType){
		this.commandAckSender.sendCommandAck(
				this.referenceManager().get(sinkId),
				key, 
				commandAckType, 
				commandType);
	}
	
	public void sendPerfData(MessageSinkRef sink, int sinkId, long roundTripNs){
//		this.codec.encoder().encodePerfData(frame, self.sinkId(), sink.sinkId(), seq, sinkId, roundTripNs);
//		sink.publish(frame, unsafeBuffer);
	}
	
	public CompletableFuture<OrderRequest> sendNewOrder(MessageSinkRef omes, NewOrderRequest request){
		return this.orderReqTracker.sendNewOrderAndTrack(omes, request);
	}
	
	public CompletableFuture<OrderRequest> sendNewCompositeOrder(MessageSinkRef omes, NewOrderRequest request){
		return this.orderReqTracker.sendNewCompositeOrderAndTrack(omes, request);
	}
	
	public void sendAmendOrder(Order order, Object responseHandler /* to be defined */){
		
	}
	
	public CompletableFuture<OrderRequest> sendCancelOrder(MessageSinkRef omes, CancelOrderRequest request){
		return this.orderReqTracker.cancelOrderAndTrack(omes, request);
	}
	
	public CommandAckSender commandAckSender(){
		return this.commandAckSender;
	}

	public CommandSender commandSender(){
		return this.commandSender;
	}

	public EchoSender echoSender(){
		return this.echoSender;
	}

	public EventSender eventSender(){
		return this.eventSender;
	}

	public NoteSender noteSender(){
		return this.noteSender;
	}

	public ChartDataSender chartDataSender(){
		return this.chartDataSender;
	}

	public MarketDataSender marketDataSender(){
		return this.mdSender;
	}
	
	public MarketDataTradeSender marketDataTradeSender() {
		return this.tdSender;
	}
	
	public MarketStatsSender marketStatsSender() {
	    return this.mstatsSender;
	}
	
	public BoobsSender boobsSender() {
	    return this.boSender;
	}
	
	public PricingSender pricingSender() {
	    return this.prSender;
	}
	
	public MarketStatusSender marketStatusSender() {
		return this.msSender;
	}
	
	public PositionSender positionSender() {
		return this.positionSender;
	}

	public RiskStateSender riskStateSender() {
		return this.riskStateSender;
	}

	public StrategySender strategySender() {
	    return this.stratSender;
	}
	
	public PerformanceSender performanceSender() {
	    return this.perfSender;
	}
	
	public ScoreBoardSender scoreBoardSender() {
	    return this.scoreBoardSender;
	}
	
	public SecuritySender securitySender() {
		return this.secSender;
	}

	public IssuerSender issuerSender() {
	    return this.issuerSender;
	}

	public OrderSender orderSender(){
		return this.orderSender;
	}
	
	public PingSender pingSender(){
		return this.pingSender;
	}

	public RequestSender requestSender(){
		return this.requestSender;
	}
	
	public ResponseSender responseSender(){
		return this.responseSender;
	}

	public TimerEventSender timerEventSender(){
		return this.timerEventSender;
	}

	public ServiceStatusSender serviceStatusSender(){
		return this.serviceStatusSender;
	}
	
	/**
	 * Service status related methods
	 */
	public long sendServiceStatusToLocalSinksExcept(ServiceStatus serviceStatus, int except){
		
		return serviceStatusSender.sendServiceStatusExcept(refMgrProxy.localSinks(), serviceStatus, except, sinkSendResults);
	}
	/**
	 * This syntactic sugar creates a new array every time.  Escape analysis 'should' be able to
	 * create this in stack instead of on heap    
	 * @param serviceStatus
	 * @param except
	 */
	public void sendServiceStatusToLocalSinksExcept(ServiceStatus serviceStatus, int[] except){
		serviceStatusSender.sendServiceStatusExcept(refMgrProxy.localSinks(), serviceStatus, except, new long[refMgrProxy.localSinks().length]);
	}
	
	public long sendServiceStatusToRemoteAdmin(ServiceStatus serviceStatus){
		ArrayList<MessageSinkRef> remoteAdmins = new ArrayList<>(EXPECTED_NUM_REMOTE_ADMINS);
		for (MessageSinkRef sinkRef : refMgrProxy.remoteSinks()){
			if (sinkRef.serviceType() == ServiceType.AdminService){
				remoteAdmins.add(sinkRef);
			}
		}
		return serviceStatusSender.sendServiceStatus(remoteAdmins.toArray(new MessageSinkRef[0]), serviceStatus, sinkSendResults);
	}
	
	public void notifyAdminOnServiceUp(){
		notifyAdmin(ServiceStatusType.UP);
	}
	
	public void notifyAdminOnServiceDown(){
		notifyAdmin(ServiceStatusType.DOWN);
	}

	public void notifyAdminOnServiceInit(){
		notifyAdmin(ServiceStatusType.INITIALIZING);
	}

	public void notifyAdminOnServiceWarmup(){
		notifyAdmin(ServiceStatusType.WARMING_UP);
	}
	
	private void notifyAdmin(ServiceStatusType statusType){
		serviceStatusSender.sendOwnServiceStatus(refMgrProxy.admin(),
											  statusType, 
											  timerService.toNanoOfDay());
	}
	
	/**
	 * Call by main service
	 * @param frame
	 */
	public void receive(final DirectBuffer buffer, int offset){
		receiver.receive(buffer, offset);
	}
	
	public MessageReceiver receiver(){
		return this.receiver;
	}
	
	public TimerService timerService(){
		return this.timerService;
	}
	
	private boolean hasRegisteredEvents;
	private boolean hasRegisteredEventsForOrderTracker;
	/**
	 * Register messages for different 'trackers'
	 * @param decoder
	 */
	public boolean registerEvents(){
		if (!hasRegisteredEvents){
			// we can decide if we want to register less handlers
			this.serviceStatusTracker.trackAnyServiceStatusChange(anyServiceStatusChangeHandler);
			receiver.serviceStatusHandlerList().add(serviceStatusTracker.eventHandler());
			receiver.commandAckHandlerList().add(commandTracker.commandHandler());
			receiver.timerEventHandlerList().add(commandTracker.timerEventHandler());
			receiver.responseHandlerList().add(requestTracker.responseHandler());
			hasRegisteredEvents = true;
		}
		return true;
	}
	
	public void unregisterEvents(){
		if (hasRegisteredEvents){
			this.serviceStatusTracker.untrackAnyServiceStatusChange(anyServiceStatusChangeHandler);
			receiver.serviceStatusHandlerList().remove(serviceStatusTracker.eventHandler());
			receiver.commandAckHandlerList().remove(commandTracker.commandHandler());
			receiver.timerEventHandlerList().remove(commandTracker.timerEventHandler());
			receiver.responseHandlerList().remove(requestTracker.responseHandler());
			hasRegisteredEvents = false;
		}
	}

	public void registerServiceStatusTracker(){
		this.serviceStatusTracker.trackAnyServiceStatusChange(anyServiceStatusChangeHandler);
		receiver.serviceStatusHandlerList().add(serviceStatusTracker.eventHandler());		
	}
	
	public void unregisterServiceStatusTracker(){
		this.serviceStatusTracker.untrackAnyServiceStatusChange(anyServiceStatusChangeHandler);
		receiver.serviceStatusHandlerList().remove(serviceStatusTracker.eventHandler());		
	}
	
	public void registerEventsForOrderTracker(){
		if (!hasRegisteredEventsForOrderTracker){
			orderReqTracker.registerEventsForOrderRequestTracker(receiver);
			hasRegisteredEventsForOrderTracker = true;
		}
	}
	
	public void unregisterEventsForOrderTracker(){
		if (hasRegisteredEventsForOrderTracker){
			orderReqTracker.unregisterEventsForOrderRequestTracker(receiver);
			hasRegisteredEventsForOrderTracker = false;
		}
	}

	public MessageSinkRef self(){
		return self;
	}

	public static Messenger create(MessageSinkRefMgr refMgr, TimerService timerService, int sinkId, ServiceType serviceType, MessageFactory messageFactory, String name, RingBuffer<MutableDirectBuffer> ringBuffer){
		// create and register message sink
		MessageSinkRef sinkRef = refMgr.createAndRegisterRingBufferMessageSink(sinkId, serviceType, name, ringBuffer);
		
		MessageSender messageSender = MessageSender.of(messageFactory.frameSize(), sinkRef);
		OrderChannelBuffer orderChannelBuffer = OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY);
		Messenger messenger = Messenger.of(refMgr, 
				sinkRef, 
				timerService, 
				messageFactory,
				messageSender,
				MessageReceiver.of(refMgr.maxNumSinks(), orderChannelBuffer),
				MessageSenderBuilder.of());
		orderChannelBuffer.messageRequester(createOrderAndTradeSnapshotRequester(messenger));
		return messenger;
	}
	
	public static Messenger create(MessageSinkRefMgr refMgr, TimerService timerService, int sinkId, ServiceType serviceType, MessageFactory messageFactory, String name, RingBuffer<MutableDirectBuffer> ringBuffer, SenderBuilder senderBuilder){
		// create and register message sink
		MessageSinkRef sinkRef = refMgr.createAndRegisterRingBufferMessageSink(sinkId, serviceType, name, ringBuffer);
		MessageSender messageSender = MessageSender.of(messageFactory.frameSize(), sinkRef);
		OrderChannelBuffer orderChannelBuffer = OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY);
		Messenger messenger = Messenger.of(refMgr,
				sinkRef, 
				timerService, 
				messageFactory,
				messageSender,
				MessageReceiver.of(refMgr.maxNumSinks(), orderChannelBuffer),
				senderBuilder);
		orderChannelBuffer.messageRequester(createOrderAndTradeSnapshotRequester(messenger));
		return messenger;
	}

	/**
	 * Create a child messenger with its own instance of {@link MessageSender}, such that it is thread-safe
	 * if the child is run on a separate thread.  However, we haven't thought thoroughly on whether
	 * we should create a separate instance of {@link MessageReceiver}...
	 * @return
	 */
	public Messenger createChildMessenger(){
		if (messengerId != PARENT_MESSENGER_ID){
			throw new IllegalStateException("A child messenger is not allowed to create child messenger of its own [sinkId:" + this.self.sinkId() + ", messengerId:" + messengerId + "]");
		}
		// create a messenger with messenger id ++
		// restrict child from further creating child messenger
		return new Messenger(messengerId + 1, 
				refMgrProxy,
				self, 
				timerService, 
				messageFactory, 
				MessageSender.of(messageFactory.frameSize(), self), 
				receiver, 
				senderBuilder);
	}
	
	public MessageSender createMessageSender(){
		return MessageSender.of(messageFactory.frameSize(), self);
	}
	
	private static ChannelMissingMessageRequester createOrderAndTradeSnapshotRequester(Messenger messenger){
		return new ChannelMissingMessageRequester() {
			@Override
			public void request(ChannelBufferContext context, int channelId, long expectedNextSeq, long fromSeq, long toSeq, int senderSinkId) {
				
				CompletableFuture<Request> requestFuture = messenger.sendRequest(messenger.refMgrProxy.get(senderSinkId), 
						RequestType.GET,
						Parameters.listOf(Parameter.of(ParameterType.CHANNEL_ID, channelId),
								Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value())));
				requestFuture.whenComplete(new BiConsumer<Request, Throwable>() {
					@Override
					public void accept(Request t, Throwable u) {
						if (u != null){
							LOG.error("Could not complete request successfully", u);
							context.onMissingMessageRequestFailure(ResultType.FAILED);
							// Something went wrong, resend request if something went wrong
							// Check result type, resend request if something went wrong
						}
						if (t.resultType() != ResultType.OK){
							LOG.error("Could not complete request successfully [resultType:{}]", t.resultType().name());
							context.onMissingMessageRequestFailure(t.resultType());
						}
					}
				});
			}
		};
	}

	public MessageSinkRefMgr referenceManager(){
		return refMgrProxy;
	}
	
	public MessageSinkRef sinkRef(int sinkId){
		return refMgrProxy.get(sinkId);
	}
	
	private final ServiceStatusChangeHandler anyServiceStatusChangeHandler = new ServiceStatusChangeHandler() {
		@Override
		public void handle(ServiceStatus status) {
			// read in volatileModifyTime so that we can get the latest changed in refMgr
			// --- and ---
			// assigning the return value so that the compiler won't optimize this call away 
			// reading the volatile value to make sure that service related changes have been 
			// propagated to this thread
			serviceStatusLastUpdateTime = refMgrProxy.volatileModifyTime();			
		}
	};
	
	public long serviceStatusLastUpdateTime(){
		return serviceStatusLastUpdateTime;
	}
	
	public ServiceStatusTracker serviceStatusTracker(){
		return serviceStatusTracker;
	}

	public MessageFactory messageFactory(){
		return messageFactory;
	}
	
	public CompletableFuture<Request> sendRequest(MessageSinkRef sink, Request request){
		return this.requestTracker.sendAndTrack(sink, request);
	}

	public CompletableFuture<Request> sendRequest(DirectBuffer buffer, int offset, MessageSinkRef sink, RequestSbeDecoder requestSbe, ImmutableListMultimap<ParameterType, Parameter> parameters){
		requestSbe.limit(requestSbe.offset() + RequestSbeDecoder.BLOCK_LENGTH);
		Request request = null;
		request = messageFactory.createRequest(requestSbe.requestType(),
				self.sinkId(),
				new ImmutableList.Builder<Parameter>().addAll(parameters.values()).build(),
				BooleanType.FALSE).clientKey(requestSbe.clientKey());

		if (requestSbe.parameterBinaryTemplateId() != RequestSbeDecoder.parameterBinaryTemplateIdNullValue()){
			if (requestSbe.parameterBinaryTemplateId() == TemplateType.NOTE.value()){
				RequestDecoder.wrapEmbeddedNote(buffer, offset, parameters.size(), requestSbe, this.noteSbeDecoder);					
				request.entity(Note.of(this.noteSbeDecoder, stringBuffer));
			}
		}
		return this.requestTracker.sendAndTrack(sink, request);
	}

	public CompletableFuture<Request> sendSubscriptionRequest(MessageSinkRef sink, Parameter... parameters){
		Request request = messageFactory.createRequest(RequestType.SUBSCRIBE,
				self.sinkId(),
				Parameters.listOf(parameters),
				BooleanType.FALSE).clientKey(getNextClientKeyAndIncrement());
		return this.requestTracker.sendAndTrack(sink, request);
	}
	
	public CompletableFuture<Request> sendGetRequest(MessageSinkRef sink, Parameter... parameters){
		Request request = messageFactory.createRequest(RequestType.GET,
				self.sinkId(),
				Parameters.listOf(parameters),
				BooleanType.FALSE).clientKey(getNextClientKeyAndIncrement());
		return this.requestTracker.sendAndTrack(sink, request);
	}
	
	public CompletableFuture<Request> sendRequest(MessageSinkRef sink, RequestType requestType, ImmutableList<Parameter> parameters){
		Request request = messageFactory.createRequest(requestType,
				self.sinkId(),
				parameters,
				BooleanType.FALSE).clientKey(getNextClientKeyAndIncrement());
		return this.requestTracker.sendAndTrack(sink, request);
	}
	
	public CompletableFuture<Request> sendRequest(MessageSinkRef sink, RequestType requestType, ImmutableList<Parameter> parameters, ResponseHandler responseHandler){
		Request request = messageFactory.createRequest(requestType,
				self.sinkId(),
				parameters,
				BooleanType.FALSE).clientKey(getNextClientKeyAndIncrement());
		return this.requestTracker.sendAndTrack(sink, request, responseHandler);
	}

	public void sendResponseForRequest(int sinkId, RequestSbeDecoder request, ResultType resultType){
		responseSender.sendResponseForRequest(refMgrProxy.get(sinkId), request, resultType);
	}
	
	public void sendOrderRequestCompletion(int sinkId, int clientKey, OrderRequestCompletionType completionType, OrderRequestRejectType rejectType){
		this.orderSender.sendOrderRequestCompletionWithClientKeyOnly(refMgrProxy.get(sinkId), clientKey, completionType, rejectType);
	}

	public void sendOrderRequestCompletion(int sinkId, int clientKey, int ordSid, OrderRequestCompletionType completionType){
		this.orderSender.sendOrderRequestCompletion(refMgrProxy.get(sinkId), clientKey, ordSid, completionType);
	}

	public void sendOrderRequestCompletion(MessageSinkRef sink, int clientKey, int ordSid, OrderRequestCompletionType completionType, OrderRequestRejectType rejectType, byte[] reason){
		this.orderSender.sendOrderRequestCompletion(sink, clientKey, ordSid, completionType, rejectType, reason);
	}

	public void sendOrderRequestAccepted(int sinkId, int clientKey, int ordSid){
		this.orderSender.sendOrderRequestAccepted(refMgrProxy.get(sinkId), clientKey, ordSid);
	}

	public MutableDirectBuffer stringBuffer() {
		return stringBuffer;
	}
	
	public byte[] stringByteBuffer(){
		return stringByteBuffer;
	}
	
	/**
	 * The internal buffer that will be used for encoding.  Use this only when you know your stuff won't be 
	 * overwritten by other normal encoding methods such as {@link OrderSender#sendNewOrder(MessageSinkRef, NewOrderRequest)} 
	 * @return
	 */
	public MutableDirectBuffer internalEncodingBuffer() {
		return messageSender.buffer();
	}

	public long trySendWithHeaderInfo(SubscriberList subscribers,
			int blockLength,
			int templateId,
			int schemaId,
			int schemaVersion,
			DirectBuffer payloadBuffer, 
			int payloadOffset, 
			int payloadLength, 
			long[] results){
		return messageSender.trySend(subscribers.elements(), subscribers.size(), blockLength, templateId, schemaId, schemaVersion, payloadBuffer, payloadOffset, payloadLength, results);
	}

	public long trySendWithHeaderInfo(SubscriberList subscribers,
	        MessageSinkRef individualSink,
	        int blockLength,
	        int templateId,
	        int schemaId,
	        int schemaVersion,
	        DirectBuffer payloadBuffer, 
	        int payloadOffset, 
	        int payloadLength, 
	        long[] results){
	    return messageSender.trySend(subscribers.elements(), subscribers.size(), individualSink, blockLength, templateId, schemaId, schemaVersion, payloadBuffer, payloadOffset, payloadLength, results);
	}

	public long trySendWithHeaderInfo(MessageSinkRef[] sinks,
			int numSinks,
			int blockLength,
			int templateId,
			int schemaId,
			int schemaVersion,
			DirectBuffer payloadBuffer, 
			int payloadOffset, 
			int payloadLength, 
			long[] results){
		return messageSender.trySend(sinks, numSinks, blockLength, templateId, schemaId, schemaVersion, payloadBuffer, payloadOffset, payloadLength, results);
	}
	
	public long send(MessageSinkRef[] sinks, MutableDirectBuffer buffer, int offset, int length, long[] results){
		return messageSender.send(sinks, buffer, offset, length, results);
	}

	public long trySend(MessageSinkRef[] sinks, MutableDirectBuffer buffer, int offset, int length, long[] results){
		return messageSender.trySend(sinks, buffer, offset, length, results);
	}
	
	public long trySend(MessageSinkRef[] sinks, int numSinks, MutableDirectBuffer buffer, int offset, int length, long[] results){
		return messageSender.trySend(sinks, numSinks, buffer, offset, length, results);
	}

	public long trySend(int sinkId, DirectBuffer buffer, int offset, int length){
		return messageSender.trySend(refMgrProxy.get(sinkId), buffer, offset, length);
	}

	public long trySendEvent(EventCategory category,  
			EventLevel level, 
			int sinkId, 
			long eventTime,
			String description){
		return eventSender.sendEvent(refMgrProxy.ns(), category, level, sinkId, eventTime, description);
	}
	
	public long trySendEventWithOneValue(EventCategory category,  
			EventLevel level, 
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType,
			long value){
		return eventSender.sendEventWithOneValue(refMgrProxy.ns(), category, level, sinkId, eventTime, description, eventValueType, value);
	}

	public long trySendEventWithTwoValues(EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType,
			long value,
			EventValueType eventValueType2,
			long value2){
		return eventSender.sendEventWithTwoValues(refMgrProxy.ns(), category, level, eventType, sinkId, eventTime, description, eventValueType, value, eventValueType2, value2);
	}
	
	public long trySendEventWithThreeValues(EventCategory category,  
			EventLevel level, 
			EventType eventType,
			int sinkId, 
			long eventTime,
			String description,
			EventValueType eventValueType,
			long value,
			EventValueType eventValueType2,
			long value2,
			EventValueType eventValueType3,
			long value3){
		return eventSender.sendEventWithThreeValues(refMgrProxy.ns(), category, level, eventType, sinkId, eventTime, description, eventValueType, value, eventValueType2, value2, eventValueType3, value3);
	}

	public String decodeToString(RequestSbeDecoder decoder){
		return RequestDecoder.decodeToString(decoder, this.stringBuffer.byteArray());
	}
	
	public Timeout newTimeout(TimerTask task, Duration duration){
		return timerService.newTimeout(task, duration.toMillis(), TimeUnit.MILLISECONDS);
	}
	
	RequestTracker requestTracker(){
		return requestTracker;
	}
	
	CommandTracker commandTracker(){
		return commandTracker;
	}
	
	OrderReqAndExecTracker orderReqAndExecTracker(){
		return orderReqTracker;
	}
	
	public MessageSinkRefMgr useWarmupRefMgr(){
		if (messengerId != PARENT_MESSENGER_ID){
			throw new IllegalStateException("A child messenger is not allowed to switch to warmup reference manager [sinkId:" + this.self.sinkId() + ", messengerId:" + messengerId + "]");
		}

		this.refMgrProxy.useWarmupRefMgr();
		return this.refMgrProxy;
	}
	
	public MessageSinkRefMgr useNormalRefMgr(){
		LOG.info("Use normal reference manager now [selfSinkId:{}]", self.sinkId());
		this.refMgrProxy.useNormalRefMgr();
		return this.refMgrProxy;
	}
}
