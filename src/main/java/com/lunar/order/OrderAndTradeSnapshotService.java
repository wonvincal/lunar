package com.lunar.order;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.config.OrderAndTradeSnapshotServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.TimeoutHandler;
import com.lunar.core.TimeoutHandlerTimerTask;
import com.lunar.exception.SequenceNumberGapDetectedException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.OrderDecoder;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.binary.TradeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.service.ServiceConstant;
import com.lunar.service.ServiceLifecycleAware;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ReferenceRBTreeMap;

public class OrderAndTradeSnapshotService implements ServiceLifecycleAware {

	public static interface Filterable {
		public int channelId();
		public long channelSeq();
		public long secSid();
		public int id();
	}
	
	public static class TradeBuffer implements Filterable {
		private final TradeSbeEncoder encoder;
		private int channelId;
		private long channelSeq;
		private long secSid;
		private int tradeSid;
		private final MutableDirectBuffer internalBuffer;
		
		public static TradeBuffer of(TradeSbeEncoder tradeEncoder){
			return new TradeBuffer(tradeEncoder);
		}
		
		TradeBuffer(TradeSbeEncoder encoder){
			this.encoder = encoder;
			this.channelId = -1;
			this.channelSeq = -1;
			this.secSid = -1;
			this.tradeSid = -1;
			this.internalBuffer = new UnsafeBuffer(ByteBuffer.allocate(TradeSbeEncoder.BLOCK_LENGTH));
		}
		
		public TradeBuffer populateFrom(TradeCreatedSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			tradeSid = codec.tradeSid();
    		TradeUtil.populateFrom(encoder, internalBuffer, 0, codec, stringBuffer);
    		return this;
		}

		public TradeBuffer mergeWith(TradeCreatedSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			tradeSid = codec.tradeSid();
			TradeUtil.mergeWith(encoder, internalBuffer, 0, codec, stringBuffer);
			return this;
		}
		
		public TradeBuffer populateFrom(TradeCreatedWithOrderInfoSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			tradeSid = codec.tradeSid();
    		TradeUtil.populateFrom(encoder, internalBuffer, 0, codec, stringBuffer);
    		return this;
		}

		public TradeBuffer mergeWith(TradeCreatedWithOrderInfoSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			tradeSid = codec.tradeSid();
			TradeUtil.mergeWith(encoder, internalBuffer, 0, codec, stringBuffer);
			return this;
		}
		
		public TradeBuffer populateFrom(TradeCancelledSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			tradeSid = codec.tradeSid();
    		TradeUtil.populateFrom(encoder, internalBuffer, 0, codec, stringBuffer);
    		return this;
		}

		public TradeBuffer mergeWith(TradeCancelledSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			tradeSid = codec.tradeSid();
			TradeUtil.mergeWith(encoder, internalBuffer, 0, codec, stringBuffer);
			return this;
		}
		
		public MutableDirectBuffer internalBuffer(){
			return internalBuffer;
		}
		
		@Override
		public int channelId(){
			return channelId;
		}
		
		@Override
		public long channelSeq(){
			return channelSeq;
		}		

		@Override
		public long secSid(){
			return secSid;
		}

		@Override
		public int id(){
			return tradeSid;
		}
	}

	public static class OrderBuffer implements Filterable {
		private final OrderSbeEncoder encoder;
		private int channelId;
		private long channelSeq;
		private long secSid;
		private int orderSid;
		private final MutableDirectBuffer internalBuffer;
		
		public static OrderBuffer of(OrderSbeEncoder orderEncoder){
			return new OrderBuffer(orderEncoder);
		}
		
		OrderBuffer(OrderSbeEncoder orderEncoder){
			this.encoder = orderEncoder;
			this.channelId = -1;
			this.channelSeq = -1;
			this.secSid = -1;
			this.orderSid = -1;
			this.internalBuffer = new UnsafeBuffer(ByteBuffer.allocate(OrderSbeEncoder.BLOCK_LENGTH));
		}
		
		public OrderBuffer populateFrom(OrderAcceptedSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(OrderAcceptedSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}

		public OrderBuffer populateFrom(OrderAcceptedWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(OrderAcceptedWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}

		public OrderBuffer populateFrom(OrderCancelledSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.origOrderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(OrderCancelledSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.origOrderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}

		public OrderBuffer populateFrom(OrderCancelledWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.origOrderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(OrderCancelledWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.origOrderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}
		
		public OrderBuffer populateFrom(OrderExpiredSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(OrderExpiredSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}
		
		public OrderBuffer populateFrom(OrderExpiredWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(OrderExpiredWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}
		
		public OrderBuffer populateFrom(OrderRejectedSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec, stringBuffer);
    		return this;
		}

		public OrderBuffer mergeWith(OrderRejectedSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec, stringBuffer);
			return this;
		}
		
		public OrderBuffer populateFrom(OrderRejectedWithOrderInfoSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
    		OrderUtil.populateFrom(encoder, internalBuffer, 0, codec, stringBuffer);
    		return this;
		}

		public OrderBuffer mergeWith(OrderRejectedWithOrderInfoSbeDecoder codec, MutableDirectBuffer stringBuffer){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec, stringBuffer);
			return this;
		}
		
		public OrderBuffer populateFrom(TradeCreatedSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(TradeCreatedSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}
		
		public OrderBuffer populateFrom(TradeCreatedWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.populateFrom(encoder, internalBuffer, 0, codec);
    		return this;
		}

		public OrderBuffer mergeWith(TradeCreatedWithOrderInfoSbeDecoder codec){
			channelId = codec.channelId();
			channelSeq = codec.channelSeq();
			secSid = codec.secSid();
			orderSid = codec.orderSid();
			OrderUtil.mergeWith(encoder, internalBuffer, 0, codec);
			return this;
		}
		
		public MutableDirectBuffer internalBuffer(){
			return internalBuffer;
		}
		
		@Override
		public int channelId(){
			return channelId;
		}
		
		@Override
		public long channelSeq(){
			return channelSeq;
		}

		@Override
		public long secSid(){
			return secSid;
		}

		@Override
		public int id(){
			return orderSid;
		}

	}
	
	private static final Logger LOG = LogManager.getLogger(OrderAndTradeSnapshotService.class);
	private static final int EXPECTED_NUM_ORDERS = 20000;
	private static final int EXPECTED_UPDATES_PER_ORDER = 3;
	private final int expectedNumOrdersPerChannel;
	private final int expectedNumTradesPerChannel;
	private final LunarService messageService;
	private final Messenger messenger;
	private final MessageSinkRefMgr refMgr;
	private final int numChannels;
	private final Int2ReferenceOpenHashMap<OrderAndTradeSubscriptionChannel> subscriptionChannels;
	
	private final HashMultimap<Integer, OrderBuffer> ordersByChannelId;
	private final HashMultimap<Integer, TradeBuffer> tradesByChannelId;
	private final Int2ObjectOpenHashMap<OrderBuffer> orderBySid;
	private final Int2ObjectOpenHashMap<TradeBuffer> tradeBySid;
	
	final String name;
    private final OrderSbeEncoder orderEncoder = new OrderSbeEncoder();
    private final OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
    private final TradeSbeEncoder tradeEncoder = new TradeSbeEncoder();
    private final TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
    private long[] sinkSendResults = new long[ServiceConstant.MAX_SUBSCRIBERS];
    private Duration publishFrequency;
    private final AtomicReference<TimeoutHandlerTimerTask> publishSnapshotTask;
    private final int CLIENT_KEY_FOR_PUBLISHING = 1;
    private final IntArrayList allChannelIds; 
    private final OrderUpdateEventStore recoveryOrderUpdateStore;
    
	public static OrderAndTradeSnapshotService of(ServiceConfig config, LunarService messageService){
		return new OrderAndTradeSnapshotService(config, messageService);
	}
	
	Int2ReferenceOpenHashMap<OrderAndTradeSubscriptionChannel> subscriptionChannels(){ return subscriptionChannels;}
	HashMultimap<Integer, OrderBuffer> ordersByChannelId(){ return ordersByChannelId; }
	HashMultimap<Integer, TradeBuffer> tradesByChannelId(){ return tradesByChannelId; }
	Int2ObjectOpenHashMap<OrderBuffer> orderBySid(){ return orderBySid; }
	Int2ObjectOpenHashMap<TradeBuffer> tradeBySid(){ return tradeBySid; }
	
	// Other logic groupings
	// By security sid
	private final HashMultimap<Long, OrderBuffer> ordersBySecSid;
	private final HashMultimap<Long, TradeBuffer> tradesBySecSid;
	
	private final Int2ObjectArrayMap<Long2ReferenceRBTreeMap<OrderBuffer>> transientOrderUpdates;
	private final Int2ObjectArrayMap<Long2ReferenceRBTreeMap<TradeBuffer>> transientTradeUpdates;
	
	OrderAndTradeSnapshotService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.refMgr = this.messenger.referenceManager();
		this.publishSnapshotTask = new AtomicReference<>();
		OrderAndTradeSnapshotServiceConfig specificConfig = null;
		if (config instanceof OrderAndTradeSnapshotServiceConfig){
			specificConfig = (OrderAndTradeSnapshotServiceConfig)config;
			this.orderBySid = new Int2ObjectOpenHashMap<>(specificConfig.expectedNumOrders());
			this.tradeBySid = new Int2ObjectOpenHashMap<>(specificConfig.expectedNumTrades());
			this.ordersByChannelId = HashMultimap.create(specificConfig.numChannels(), specificConfig.expectedNumOrdersPerChannel());
			this.tradesByChannelId = HashMultimap.create(specificConfig.numChannels(), specificConfig.expectedNumTradesPerChannel());
			this.subscriptionChannels = new Int2ReferenceOpenHashMap<OrderAndTradeSubscriptionChannel>(specificConfig.numChannels());
			this.numChannels = specificConfig.numChannels();
			this.expectedNumOrdersPerChannel = specificConfig.expectedNumOrdersPerChannel();
			this.expectedNumTradesPerChannel = specificConfig.expectedNumTradesPerChannel();
			this.publishFrequency = specificConfig.publishFrequency();
			this.allChannelIds = new IntArrayList(this.numChannels);
			this.transientOrderUpdates = new Int2ObjectArrayMap<>(this.numChannels); 
			this.transientTradeUpdates = new Int2ObjectArrayMap<>(this.numChannels);
			for (int i = 0; i < this.numChannels; i++){
				this.allChannelIds.add(i);
				this.transientOrderUpdates.put(i, new Long2ReferenceRBTreeMap<>());
				this.transientTradeUpdates.put(i, new Long2ReferenceRBTreeMap<>());
			}
		}
		else{
			throw new IllegalArgumentException("OrderAndTradeSnapshotServiceConfig is missing");
		}
		this.recoveryOrderUpdateStore = OrderUpdateEventStore.of(EXPECTED_NUM_ORDERS, EXPECTED_UPDATES_PER_ORDER);
		this.ordersBySecSid = HashMultimap.create(400, 400);
		this.tradesBySecSid = HashMultimap.create(400, 400);
		
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderManagementAndExecutionService);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService);
		messenger.serviceStatusTracker().trackAggregatedServiceStatus(this::handleAggregatedServiceStatusChange);
		return StateTransitionEvent.NULL;
	}

	void handleAggregatedServiceStatusChange(boolean status){
		if (status){
			messageService.stateEvent(StateTransitionEvent.READY);
		}
		else { // DOWN or INITIALIZING
			messageService.stateEvent(StateTransitionEvent.WAIT);
		}
	}

	@Override
	public StateTransitionEvent idleRecover() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent waitingForWarmupServicesEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForWarmupServicesExit() {
	}

	@Override
	public StateTransitionEvent warmupEnter() {
		return StateTransitionEvent.READY;
	};
	
	@Override
	public void warmupExit() {
	}
	
	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForServicesExit() {
	}

	@Override
	public StateTransitionEvent readyEnter() {
		return StateTransitionEvent.RECOVER;
	}

	@Override
	public void readyExit() {
	}

	@Override
	public StateTransitionEvent recoveryEnter() {
		recoveryOrderUpdateStore.register(messenger);
		messenger.receiver().orderAcceptedHandlerList().add(orderAcceptedHandlerForRecovery);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderCancelRejectedHandlerList().add(orderCancelRejectedHandlerForRecovery);
		messenger.receiver().orderCancelRejectedWithOrderInfoHandlerList().add(orderCancelRejectedWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderCancelledHandlerList().add(orderCancelledHandlerForRecovery);
		messenger.receiver().orderCancelledWithOrderInfoHandlerList().add(orderCancelledWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderExpiredHandlerList().add(orderExpiredHandlerForRecovery);
		messenger.receiver().orderExpiredWithOrderInfoHandlerList().add(orderExpiredWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderRejectedHandlerList().add(orderRejectedHandlerForRecovery);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(orderRejectedWithOrderInfoHandlerForRecovery);
		messenger.receiver().tradeCreatedHandlerList().add(tradeCreatedHandlerForRecovery);
		messenger.receiver().tradeCreatedWithOrderInfoHandlerList().add(tradeCreatedWithOrderInfoHandlerForRecovery);
		messenger.receiver().tradeCancelledHandlerList().add(tradeCancelledHandlerForRecovery);

		// 1) Start subscribing from omes
		// 2) Once the ack is received, get latest order update from persist service
		// a) If there has been no updates, we will get all the order updates
		// b) If there has been some updates before omes receives our subscription, persist service would receive them
		// c) If there has been some updates after omes receives our subscription, persist service and us would receive them
		// 3) Buffer all updates from omes
		// 4) At the end of the get latest order update method, we can reprocess all buffered order updates
		// At this point, we have latest order and trade and we can move to active state
		CompletableFuture<Request> requestFuture = messenger.sendSubscriptionRequest(refMgr.omes(),
				Parameter.of(DataType.ALL_ORDER_AND_TRADE_UPDATE));
		requestFuture.whenComplete((request, cause) -> {
			if (cause != null){
				LOG.error("Cannot subscribe order update", cause);
				return;
			}
			if (request.resultType() != ResultType.OK){
				LOG.error("Cannot subscribe order update [resultType:{}]", request.resultType().name());
				return;
			}
			CompletableFuture<Request> getFuture = messenger.sendGetRequest(refMgr.persi(), Parameter.of(DataType.ALL_ORDER_AND_TRADE_UPDATE));
			getFuture.whenComplete((getRequest, getRequestCause) -> {
				// All latest updates have been received and is ready to be distributed
				processBufferedUpdates();
				messageService.stateEvent(StateTransitionEvent.ACTIVATE);
			});
		});
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public void recoveryExit() {
		recoveryOrderUpdateStore.unregister(messenger);
		messenger.receiver().orderAcceptedHandlerList().remove(orderAcceptedHandlerForRecovery);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderCancelRejectedHandlerList().remove(orderCancelRejectedHandlerForRecovery);
		messenger.receiver().orderCancelRejectedWithOrderInfoHandlerList().remove(orderCancelRejectedWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderCancelledHandlerList().remove(orderCancelledHandlerForRecovery);
		messenger.receiver().orderCancelledWithOrderInfoHandlerList().remove(orderCancelledWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderExpiredHandlerList().remove(orderExpiredHandlerForRecovery);
		messenger.receiver().orderExpiredWithOrderInfoHandlerList().remove(orderExpiredWithOrderInfoHandlerForRecovery);
		messenger.receiver().orderRejectedHandlerList().remove(orderRejectedHandlerForRecovery);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().remove(orderRejectedWithOrderInfoHandlerForRecovery);
		messenger.receiver().tradeCreatedHandlerList().remove(tradeCreatedHandlerForRecovery);
		messenger.receiver().tradeCreatedWithOrderInfoHandlerList().remove(tradeCreatedWithOrderInfoHandlerForRecovery);
		messenger.receiver().tradeCancelledHandlerList().remove(tradeCancelledHandlerForRecovery);
	}
	
	@Override
	public StateTransitionEvent activeEnter() {
		messenger.receiver().orderAcceptedHandlerList().add(orderAcceptedHandler);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandler);
		messenger.receiver().orderCancelRejectedHandlerList().add(orderCancelRejectedHandler);
		messenger.receiver().orderCancelRejectedWithOrderInfoHandlerList().add(orderCancelRejectedWithOrderInfoHandler);
		messenger.receiver().orderCancelledHandlerList().add(orderCancelledHandler);
		messenger.receiver().orderCancelledWithOrderInfoHandlerList().add(orderCancelledWithOrderInfoHandler);
		messenger.receiver().orderExpiredHandlerList().add(orderExpiredHandler);
		messenger.receiver().orderExpiredWithOrderInfoHandlerList().add(orderExpiredWithOrderInfoHandler);
		messenger.receiver().orderRejectedHandlerList().add(orderRejectedHandler);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(orderRejectedWithOrderInfoHandler);
		messenger.receiver().tradeCreatedHandlerList().add(tradeCreatedHandler);
		messenger.receiver().tradeCreatedWithOrderInfoHandlerList().add(tradeCreatedWithOrderInfoHandler);
		messenger.receiver().tradeCancelledHandlerList().add(tradeCancelledHandler);
		messenger.receiver().requestHandlerList().add(requestHandler);
		messenger.receiver().timerEventHandlerList().add(timerEventHandler);
		
		this.publishSnapshotTask.set(messenger.timerService().createTimerTask(publishSnapshots, "ots-publish-snapshots"));
		messenger.timerService().newTimeout(publishSnapshotTask.get(), publishFrequency.toMillis(), TimeUnit.MILLISECONDS);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void activeExit() {
		messenger.receiver().orderAcceptedHandlerList().remove(orderAcceptedHandler);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandler);
		messenger.receiver().orderCancelRejectedHandlerList().remove(orderCancelRejectedHandler);
		messenger.receiver().orderCancelRejectedWithOrderInfoHandlerList().remove(orderCancelRejectedWithOrderInfoHandler);
		messenger.receiver().orderCancelledHandlerList().remove(orderCancelledHandler);
		messenger.receiver().orderCancelledWithOrderInfoHandlerList().remove(orderCancelledWithOrderInfoHandler);
		messenger.receiver().orderExpiredHandlerList().remove(orderExpiredHandler);
		messenger.receiver().orderExpiredWithOrderInfoHandlerList().remove(orderExpiredWithOrderInfoHandler);
		messenger.receiver().orderRejectedHandlerList().remove(orderRejectedHandler);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().remove(orderRejectedWithOrderInfoHandler);
		messenger.receiver().tradeCreatedHandlerList().remove(tradeCreatedHandler);
		messenger.receiver().tradeCreatedWithOrderInfoHandlerList().remove(tradeCreatedWithOrderInfoHandler);
		messenger.receiver().tradeCancelledHandlerList().remove(tradeCancelledHandler);
		messenger.receiver().requestHandlerList().remove(requestHandler);
		messenger.receiver().timerEventHandlerList().remove(timerEventHandler);
	}

	@Override
	public StateTransitionEvent stopEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stopExit() {
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		LOG.info("{} stopped", name);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
	}
	
	private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
    		byte senderSinkId = header.senderSinkId();
            LOG.info("Received request [senderSinkId:{}, {}]", senderSinkId, RequestDecoder.decodeToString(request, messenger.stringByteBuffer()));
	    	try {
				ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
				switch (request.requestType()){
				case GET:
					handleGetRequest(senderSinkId, request, parameters);
					break;
				case SUBSCRIBE:
					handleSubscriptionRequest(senderSinkId, request, parameters);
					break;
				case UNSUBSCRIBE:
					handleUnsubscriptionRequest(senderSinkId, request, parameters);
					break;
				case GET_AND_SUBSCRIBE:
					handleGetAndSubscriptionRequest(senderSinkId, request, parameters);
					break;				
				default:
					LOG.error("Unsupported operation [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
					messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
					break;
				}			
			} 
	    	catch (UnsupportedEncodingException e) {
	    		LOG.error("Caught exception while processing request [" + RequestDecoder.decodeToString(request, messenger.stringBuffer().byteArray()) + "]");
	    		messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			}
		}
	};

    private void handleGetAndSubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
		ImmutableList<Parameter> dataTypes = parameters.get(ParameterType.DATA_TYPE);
		if (dataTypes.size() != 1){
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			LOG.warn("Invalid get request.  Exact one data type is required [{}]", messenger.decodeToString(request));
			return;
		}
		
		ImmutableList<Parameter> channelIds = parameters.get(ParameterType.CHANNEL_ID);
		IntArrayList requestedChannelIds;
		if (channelIds.isEmpty()){
			requestedChannelIds = allChannelIds;
		}
		else {
			requestedChannelIds = new IntArrayList(channelIds.size());
			for (Parameter channelIdParam : channelIds){
				requestedChannelIds.add(channelIdParam.valueLong().intValue());
			}
		}
		
		DataType dataType = DataType.get(dataTypes.get(0).valueLong().byteValue());
		MessageSinkRef sink = this.refMgr.get(senderSinkId);
		int clientKey = request.clientKey();
		// Send back responses per channel
		clearTransientStructures();
		switch (dataType){
		case ALL_ORDER_AND_TRADE_UPDATE:
		{
			int responseSeq = 0;
			for (int channelId : requestedChannelIds){
				for (OrderBuffer orderBuffer : this.ordersByChannelId.get(channelId)){
    				transientOrderUpdates.get(orderBuffer.channelId()).put(orderBuffer.channelSeq(), orderBuffer);
	  			}
				for (TradeBuffer tradeBuffer : this.tradesByChannelId.get(channelId)){
    				transientTradeUpdates.get(tradeBuffer.channelId()).put(tradeBuffer.channelSeq(), tradeBuffer);
				}
			}
			sendResponse(sink, clientKey, responseSeq, transientOrderUpdates, transientTradeUpdates);
			for (int channelId : requestedChannelIds){
				LOG.info("Start subscribing to order and trade snapshots [sinkId:{}, channelId:{}]", sink.sinkId(), channelId);
				OrderAndTradeSubscriptionChannel channel = getChannel(channelId);
				channel.addOrderSubscriber(sink);
				channel.addTradeSubscriber(sink);
			}
			break;
		}
		case TRADE_UPDATE:
		{
			int responseSeq = 0;
			for (int channelId : requestedChannelIds){
				for (TradeBuffer tradeBuffer : this.tradesByChannelId.get(channelId)){
					transientTradeUpdates.get(tradeBuffer.channelId()).put(tradeBuffer.channelSeq(), tradeBuffer);
				}
			}
			sendResponse(sink, clientKey, responseSeq, transientOrderUpdates, transientTradeUpdates);
			for (int channelId : requestedChannelIds){
				LOG.info("Start subscribing to trade only snapshots [sinkId:{}, channelId:{}]", sink.sinkId(), channelId);
				getChannel(channelId).addTradeSubscriber(sink);				
			}
			break;
		}
		default:
			LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
    }
    
    private void handleSubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
    	Optional<DataType> parameterDataType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
		if (!parameterDataType.isPresent()){
			LOG.error("Number of subscription types must be 1");
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		
		MessageSinkRef sink = refMgr.get(senderSinkId);
		IntArrayList requestedChannelIds;
		ImmutableList<Parameter> channelIds = parameters.get(ParameterType.CHANNEL_ID);
		if (channelIds.isEmpty()){
			requestedChannelIds = allChannelIds;
		}
		else {
			requestedChannelIds = new IntArrayList(channelIds.size());
			for (Parameter channelIdParam : channelIds){
				requestedChannelIds.add(channelIdParam.valueLong().intValue());
			}
		}
			
		for (int channelId : requestedChannelIds){
			DataType dataType = parameterDataType.get();
			switch (dataType){
			case ALL_ORDER_AND_TRADE_UPDATE:
				LOG.info("Start subscribing to order and trade snapshots [sinkId:{}, clientKey:{}, channelId:{}]", sink.sinkId(), request.clientKey(), channelId);
				OrderAndTradeSubscriptionChannel channel = getChannel(channelId);
				channel.addOrderSubscriber(sink);
				channel.addTradeSubscriber(sink);
				break;
			case TRADE_UPDATE:
				LOG.info("Start subscribing to trade only snapshots [sinkId:{}]", sink.sinkId());
				getChannel(channelId).addTradeSubscriber(sink);
				break;
			default:
				LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
				return;
			}
		}
		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		return;
	}

    private static void removeOrderSubscription(MessageSinkRef sink, OrderAndTradeSubscriptionChannel channel){
		if (channel.removeOrderSubscriber(sink)){
			LOG.info("Unsubscribed from order subscription [sinkId:{}, channelId:{}]", sink.sinkId(), channel.channelId());
		}
		else{
			LOG.info("Tried to unsubscribe a non-exist order subscription [sinkId:{}, channelId:{}]", sink.sinkId(), channel.channelId());
		}
    }

    private static void removeTradeSubscription(MessageSinkRef sink, OrderAndTradeSubscriptionChannel channel){
		if (channel.removeTradeSubscriber(sink)){
			LOG.info("Unsubscribed from trade subscription [sinkId:{}, channelId:{}]", sink.sinkId(), channel.channelId());
		}
		else{
			LOG.info("Tried to unsubscribe a non-exist trade subscription [sinkId:{}, channelId:{}]", sink.sinkId(), channel.channelId());
		}
    }

    private void handleUnsubscriptionRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
		// Get the snapshots and send them back
		ImmutableList<Parameter> dataTypes = parameters.get(ParameterType.DATA_TYPE);
		MessageSinkRef sink = refMgr.get(senderSinkId);
		ImmutableList<Parameter> channelIds = parameters.get(ParameterType.CHANNEL_ID);
		
		if (dataTypes.size() != 1){
			LOG.error("Invalid unsubscription request.  Exact one data type is required. [{}]clientKey:" + request.clientKey() + ", numDataTypes:"+ dataTypes.size() + "]");
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}
		
		// With no subscription types, we want to remove all subscriptions for this particular
		// sender
		LOG.debug("channelIds.size():{}", channelIds.size());
		if (channelIds.size() == 0){
			for (Entry<OrderAndTradeSubscriptionChannel> entrySet : subscriptionChannels.int2ReferenceEntrySet()){
				if (entrySet.getValue().removeOrderSubscriber(sink)){
					LOG.info("Unsubscribed from order subscription [sinkId:{}, channelId:{}]", sink.sinkId(), entrySet.getIntKey());
				}
				if (entrySet.getValue().removeTradeSubscriber(sink)){
					LOG.info("Unsubscribed from trade subscription [sinkId:{}, channelId:{}]", sink.sinkId(), entrySet.getIntKey());
				}
			}
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
			return;
		}
		
		for (Parameter channelIdParam : channelIds){
			int channelId = channelIdParam.valueLong().intValue();
			DataType dataType = DataType.get(dataTypes.get(0).valueLong().byteValue());
			switch (dataType){
			case ALL_ORDER_AND_TRADE_UPDATE:
				removeOrderSubscription(sink, getChannel(channelId));
				removeTradeSubscription(sink, getChannel(channelId));
				break;
			case TRADE_UPDATE:
				removeTradeSubscription(sink, getChannel(channelId));
				break;
			default:
				LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
				return;
			}
		}
		messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
		return;
	}
        
    private void sendResponse(MessageSinkRef sink,
    		int clientKey,
    		int responseSeq,
    		Int2ObjectArrayMap<Long2ReferenceRBTreeMap<OrderBuffer>> orderUpdates, 
    		Int2ObjectArrayMap<Long2ReferenceRBTreeMap<TradeBuffer>> tradeUpdates){
		// Send results
		for (Long2ReferenceRBTreeMap<OrderBuffer> orderUpdatesPerChannel : this.transientOrderUpdates.values()){
			for (OrderBuffer orderBuffer : orderUpdatesPerChannel.values()){
				long result = messenger.responseSender().sendOrder(sink, clientKey, responseSeq++, orderBuffer.internalBuffer());
				if (result != MessageSink.OK){
					LOG.error("Could not send order back to request [result:{}]", result);
				}
			}
		}
		for (Long2ReferenceRBTreeMap<TradeBuffer> tradeUpdatesPerChannel : this.transientTradeUpdates.values()){
			for (TradeBuffer tradeBuffer : tradeUpdatesPerChannel.values()){
				long result = messenger.responseSender().sendTrade(sink, clientKey, responseSeq++, tradeBuffer.internalBuffer());
				if (result != MessageSink.OK){
					LOG.error("Could not send trade back to request [result:{}]", result);
				}
				LOG.debug("Send trade back to requester [{}]", TradeDecoder.decodeToString(tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION)));    				
			}
		}
		messenger.responseSender().sendResponse(sink, clientKey, BooleanType.TRUE, responseSeq, ResultType.OK);    	
    }
    
    private void handleGetBySecSidRequest(MessageSinkRef sink, RequestSbeDecoder request, DataType dataType, long secSid, Function<Filterable, Boolean> predicate) {
    	// Check if channel sequence and channel id is available
    	int clientKey = request.clientKey();
    	int responseSeq = 0;
    	clearTransientStructures();
    	switch (dataType){
    	case ALL_ORDER_AND_TRADE_UPDATE:
    		// Prepare results
    		for (OrderBuffer orderBuffer : this.ordersBySecSid.get(secSid)){
    			if (predicate.apply(orderBuffer)){
    				transientOrderUpdates.get(orderBuffer.channelId()).put(orderBuffer.channelSeq(), orderBuffer);
    			}
    		}
    		for (TradeBuffer tradeBuffer : this.tradesBySecSid.get(secSid)){
    			if (predicate.apply(tradeBuffer)){
    				transientTradeUpdates.get(tradeBuffer.channelId()).put(tradeBuffer.channelSeq(), tradeBuffer);
    				LOG.trace("Send trade back to requester [{}]", TradeDecoder.decodeToString(tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION)));
    			}
    		}
    		sendResponse(sink, clientKey, responseSeq, transientOrderUpdates, transientTradeUpdates);
    		break;
    	case TRADE_UPDATE:
    		for (TradeBuffer tradeBuffer : this.tradesBySecSid.get(secSid)){
    			if (predicate.apply(tradeBuffer)){
    				transientTradeUpdates.get(tradeBuffer.channelId()).put(tradeBuffer.channelSeq(), tradeBuffer);
    			}
    		}
    		sendResponse(sink, clientKey, responseSeq, transientOrderUpdates, transientTradeUpdates);
    		break;
    	default:
    		LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
    		messenger.sendResponseForRequest(sink.sinkId(), request, ResultType.FAILED);
    	}
    }
    
	private static Function<Filterable, Boolean> createPredicate(ImmutableListMultimap<ParameterType, Parameter> parameters){
		Function<Filterable, Boolean> predicate;
		Optional<Long> securityParameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
		Optional<Long> channelSeqParameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.CHANNEL_SEQ);
		if (securityParameter.isPresent() && channelSeqParameter.isPresent()){
			predicate = (o) -> { return (o.secSid() == securityParameter.get()) && (o.channelSeq() > channelSeqParameter.get()); };
		}
		else if (securityParameter.isPresent()){
			predicate = (o) -> { return o.secSid() == securityParameter.get(); }; 
		}
		else if (channelSeqParameter.isPresent()){
			predicate = (o) -> { return o.channelSeq() > channelSeqParameter.get(); }; 
		}
		else {
			predicate = (o) -> { return true; };
		}
		return predicate;
	}

	private void clearTransientStructures(){
		for (Long2ReferenceRBTreeMap<OrderBuffer> item : transientOrderUpdates.values()){
			item.clear();
		}
		for (Long2ReferenceRBTreeMap<TradeBuffer> item : transientTradeUpdates.values()){
			item.clear();
		}
	}
	
	private void handleGetRequest(int senderSinkId, RequestSbeDecoder request, ImmutableListMultimap<ParameterType, Parameter> parameters) {
        // Get the snapshots and send them back
    	Optional<DataType> parameterDataType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
		if (!parameterDataType.isPresent()){
			LOG.error("Number of data type must be 1");
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
			return;
		}

		DataType dataType = parameterDataType.get();
		int clientKey = request.clientKey();
		MessageSinkRef sink = this.refMgr.get(senderSinkId);

		// Create a Predicate from parameter
		Function<Filterable, Boolean> predicate = createPredicate(parameters);

		// If security sid is specified, we can get our result from elsewhere
		Optional<Long> securityParameter = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
		if (securityParameter.isPresent()){
			handleGetBySecSidRequest(sink, request, dataType, securityParameter.get(), predicate);
			return;
		}
		
		// Find out which channel is required
		IntArrayList requestedChannelIds = new IntArrayList();
		ImmutableList<Parameter> channelIds = parameters.get(ParameterType.CHANNEL_ID);
		if (channelIds.isEmpty()){
			requestedChannelIds.addAll(allChannelIds);
		}
		else {
			channelIds.forEach((p) -> requestedChannelIds.add(p.valueLong().intValue()));
		}

		// Send back responses per channel
		clearTransientStructures();
		switch (dataType){
		case ALL_ORDER_AND_TRADE_UPDATE:
		{
			int responseSeq = 0;
			for (int channelId : requestedChannelIds){
				for (OrderBuffer orderBuffer : this.ordersByChannelId.get(channelId)){
					if (predicate.apply(orderBuffer)){
	    				transientOrderUpdates.get(orderBuffer.channelId()).put(orderBuffer.channelSeq(), orderBuffer);
					}
	  			}
				for (TradeBuffer tradeBuffer : this.tradesByChannelId.get(channelId)){
					if (predicate.apply(tradeBuffer)){
	    				transientTradeUpdates.get(tradeBuffer.channelId()).put(tradeBuffer.channelSeq(), tradeBuffer);
					}
				}
			}
			sendResponse(sink, clientKey, responseSeq, transientOrderUpdates, transientTradeUpdates);
			break;
		}
		case TRADE_UPDATE:
		{
			int responseSeq = 0;
			for (int channelId : requestedChannelIds){
				for (TradeBuffer tradeBuffer : this.tradesByChannelId.get(channelId)){
					if (predicate.apply(tradeBuffer)){
	    				transientTradeUpdates.get(tradeBuffer.channelId()).put(tradeBuffer.channelSeq(), tradeBuffer);
					}
				}
			}
			sendResponse(sink, clientKey, responseSeq, transientOrderUpdates, transientTradeUpdates);
			break;
		}
		default:
			LOG.error("Unsupported data type [clientKey:{}, dataType:{}]", request.clientKey(), dataType.name());
			messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
			return;
		}
	}

	private OrderAndTradeSubscriptionChannel getChannel(int channelId) {
		OrderAndTradeSubscriptionChannel channelUpdate = subscriptionChannels.get(channelId);
    	if (channelUpdate == null){
    		channelUpdate = OrderAndTradeSubscriptionChannel.of(channelId, this.expectedNumOrdersPerChannel, this.expectedNumTradesPerChannel);
    		subscriptionChannels.put(channelId, channelUpdate);
    	}
		return channelUpdate;
    }

	private OrderAndTradeSubscriptionChannel getChannel(int channelId, long seq) throws SequenceNumberGapDetectedException{
		OrderAndTradeSubscriptionChannel channelUpdate = subscriptionChannels.get(channelId);
    	if (channelUpdate == null){
    		channelUpdate = OrderAndTradeSubscriptionChannel.of(channelId, seq, this.expectedNumOrdersPerChannel, this.expectedNumTradesPerChannel);
    		subscriptionChannels.put(channelId, channelUpdate);
    		return channelUpdate;
    	}
    	if (!channelUpdate.init()){
    		return channelUpdate.init(seq);
    	}
    	if (seq == channelUpdate.sequence() + 1){
    		return channelUpdate.sequence(seq);
    	}
    	throw new SequenceNumberGapDetectedException("channelId:" + channelUpdate.channelId() + ", prevSeq:" + channelUpdate.sequence() + ", currentSeq:" + seq);
    }
    
    private OrderAndTradeSubscriptionChannel getChannelUpdateSafe(int channelId, long seq) {
    	OrderAndTradeSubscriptionChannel channelUpdate = subscriptionChannels.get(channelId);
    	if (channelUpdate == null){
    		channelUpdate = OrderAndTradeSubscriptionChannel.of(channelId, seq, this.expectedNumOrdersPerChannel, this.expectedNumTradesPerChannel);
    		subscriptionChannels.put(channelId, channelUpdate);
    		return channelUpdate;
    	}
    	if (!channelUpdate.init()){
    		return channelUpdate.init(seq);
    	}
		channelUpdate.sequence(seq);
		return channelUpdate;    	
    }
    
    private final OrderAcceptedSbeDecoder orderAcceptedDecoder = new OrderAcceptedSbeDecoder();
    private final OrderAcceptedWithOrderInfoSbeDecoder orderAcceptedWithOrderInfoDecoder = new OrderAcceptedWithOrderInfoSbeDecoder();
    private final OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();
    private final OrderCancelledWithOrderInfoSbeDecoder orderCancelledWithOrderInfoDecoder = new OrderCancelledWithOrderInfoSbeDecoder();
    private final OrderExpiredSbeDecoder orderExpiredDecoder = new OrderExpiredSbeDecoder();
    private final OrderExpiredWithOrderInfoSbeDecoder orderExpiredWithOrderInfoDecoder = new OrderExpiredWithOrderInfoSbeDecoder();
    private final OrderRejectedSbeDecoder orderRejectedDecoder = new OrderRejectedSbeDecoder();
    private final OrderRejectedWithOrderInfoSbeDecoder orderRejectedWithOrderInfoDecoder = new OrderRejectedWithOrderInfoSbeDecoder();
    private final TradeCreatedSbeDecoder tradeCreatedDecoder = new TradeCreatedSbeDecoder();
    private final TradeCreatedWithOrderInfoSbeDecoder tradeCreatedWithOrderInfoDecoder = new TradeCreatedWithOrderInfoSbeDecoder();
    private final TradeCancelledSbeDecoder tradeCancelledDecoder = new TradeCancelledSbeDecoder();
    
    private void processBufferedUpdates(){
    	LOG.info("Start processing buffered updates");
    	for (OrderUpdateEvent update : this.recoveryOrderUpdateStore.orderUpdates()){
    		switch (update.updateType()){
    		case OrderUpdateEvent.ORDER_ACCEPTED:
    			orderAcceptedDecoder.wrap(update.buffer(), 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
    			processOrderAccepted(update.buffer(), 0, orderAcceptedDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_ACCEPTED_WITH_ORDER_INFO:
    			orderAcceptedWithOrderInfoDecoder.wrap(update.buffer(), 0, OrderAcceptedWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderAcceptedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
    			processOrderAcceptedWithOrderInfo(update.buffer(), 0, orderAcceptedWithOrderInfoDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_CANCELLED:
    			orderCancelledDecoder.wrap(update.buffer(), 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
    			processOrderCancelled(update.buffer(), 0, orderCancelledDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_CANCELLED_WITH_ORDER_INFO:
    			orderCancelledWithOrderInfoDecoder.wrap(update.buffer(), 0, OrderCancelledWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderCancelledWithOrderInfoSbeDecoder.SCHEMA_VERSION);
    			processOrderCancelledWithOrderInfo(update.buffer(), 0, orderCancelledWithOrderInfoDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_EXPIRED:
    			orderExpiredDecoder.wrap(update.buffer(), 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
    			processOrderExpired(update.buffer(), 0, orderExpiredDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_EXPIRED_WITH_ORDER_INFO:
    			orderExpiredWithOrderInfoDecoder.wrap(update.buffer(), 0, OrderExpiredWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderExpiredWithOrderInfoSbeDecoder.SCHEMA_VERSION);
    			processOrderExpiredWithOrderInfo(update.buffer(), 0, orderExpiredWithOrderInfoDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_REJECTED:
    			orderRejectedDecoder.wrap(update.buffer(), 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);
    			processOrderRejected(update.buffer(), 0, orderRejectedDecoder);
    			break;
    		case OrderUpdateEvent.ORDER_REJECTED_WITH_ORDER_INFO:
    			orderRejectedWithOrderInfoDecoder.wrap(update.buffer(), 0, OrderRejectedWithOrderInfoSbeDecoder.BLOCK_LENGTH, OrderRejectedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
    			processOrderRejectedWithOrderInfo(update.buffer(), 0, orderRejectedWithOrderInfoDecoder);
    			break;
    		case OrderUpdateEvent.TRADE_CREATED:
    			tradeCreatedDecoder.wrap(update.buffer(), 0, TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION);
    			processTradeCreated(update.buffer(), 0, tradeCreatedDecoder);
    			break;
    		case OrderUpdateEvent.TRADE_CREATED_WITH_ORDER_INFO:
    			tradeCreatedWithOrderInfoDecoder.wrap(update.buffer(), 0, TradeCreatedWithOrderInfoSbeDecoder.BLOCK_LENGTH, TradeCreatedWithOrderInfoSbeDecoder.SCHEMA_VERSION);
    			processTradeCreatedWithOrderInfo(update.buffer(), 0, tradeCreatedWithOrderInfoDecoder);
    			break;
    		case OrderUpdateEvent.TRADE_CANCELLED:
    			tradeCancelledDecoder.wrap(update.buffer(), 0, TradeCancelledSbeDecoder.BLOCK_LENGTH, TradeCancelledSbeDecoder.SCHEMA_VERSION);
    			processTradeCancelled(update.buffer(), 0, tradeCancelledDecoder);
    			break;
    		default:
    			LOG.error("Unsupported order update type [type:{}]", update.updateType());
    			break;
    		}
    	}
    	LOG.info("End of processing buffered updates");
    }
    
    // During recovery
    // handle()
    // 1) Buffer updates
    // handleEmbedded()
    // 1) Handle normally, merge updates into states 
    
    // During active
    // handle()
    // 1) Handle normally, merge updates into states
    // handleEmbedded()
    // 1) don't handle
    
    // Recovery Handlers
    // =================
    private static interface EmbeddedOnlyHandler<C> extends Handler<C> {
    	@Override
    	public default void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, C payload) {}
    }

    private final EmbeddedOnlyHandler<OrderAcceptedSbeDecoder> orderAcceptedHandlerForRecovery = new EmbeddedOnlyHandler<OrderAcceptedSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderAcceptedSbeDecoder codec) {
    		processOrderAccepted(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandlerForRecovery = new EmbeddedOnlyHandler<OrderAcceptedWithOrderInfoSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, OrderAcceptedWithOrderInfoSbeDecoder codec) {
    		processOrderAcceptedWithOrderInfo(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderCancelledSbeDecoder> orderCancelledHandlerForRecovery = new EmbeddedOnlyHandler<OrderCancelledSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderCancelledSbeDecoder codec) {
    		processOrderCancelled(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderCancelledWithOrderInfoSbeDecoder> orderCancelledWithOrderInfoHandlerForRecovery = new EmbeddedOnlyHandler<OrderCancelledWithOrderInfoSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderCancelledWithOrderInfoSbeDecoder codec) {
    		processOrderCancelledWithOrderInfo(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderExpiredSbeDecoder> orderExpiredHandlerForRecovery = new EmbeddedOnlyHandler<OrderExpiredSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderExpiredSbeDecoder codec) {
    		processOrderExpired(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandlerForRecovery = new EmbeddedOnlyHandler<OrderExpiredWithOrderInfoSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderExpiredWithOrderInfoSbeDecoder codec) {
    		processOrderExpiredWithOrderInfo(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderRejectedSbeDecoder> orderRejectedHandlerForRecovery = new EmbeddedOnlyHandler<OrderRejectedSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderRejectedSbeDecoder codec) {
    		processOrderRejected(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandlerForRecovery = new EmbeddedOnlyHandler<OrderRejectedWithOrderInfoSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderRejectedWithOrderInfoSbeDecoder codec) {
    		processOrderRejectedWithOrderInfo(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderCancelRejectedSbeDecoder> orderCancelRejectedHandlerForRecovery = new EmbeddedOnlyHandler<OrderCancelRejectedSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderCancelRejectedSbeDecoder codec) {
    		processOrderCancelRejected(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<OrderCancelRejectedWithOrderInfoSbeDecoder> orderCancelRejectedWithOrderInfoHandlerForRecovery = new EmbeddedOnlyHandler<OrderCancelRejectedWithOrderInfoSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,OrderCancelRejectedWithOrderInfoSbeDecoder codec) {
    		processOrderCancelRejectedWithOrderInfo(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<TradeCreatedSbeDecoder> tradeCreatedHandlerForRecovery = new EmbeddedOnlyHandler<TradeCreatedSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,TradeCreatedSbeDecoder codec) {
    		processTradeCreated(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoHandlerForRecovery = new EmbeddedOnlyHandler<TradeCreatedWithOrderInfoSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast,TradeCreatedWithOrderInfoSbeDecoder codec) {
    		processTradeCreatedWithOrderInfo(buffer, offset, codec);
    	}
    };

    private final EmbeddedOnlyHandler<TradeCancelledSbeDecoder> tradeCancelledHandlerForRecovery = new EmbeddedOnlyHandler<TradeCancelledSbeDecoder>() {
    	@Override
    	public void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, TradeCancelledSbeDecoder codec) {
    		processTradeCancelled(buffer, offset, codec);
    	}
    };

	// Active Handlers
	// ==================
    private final Handler<OrderAcceptedSbeDecoder> orderAcceptedHandler = new Handler<OrderAcceptedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
			processOrderAccepted(buffer, offset, codec);
		}
	};
	
    private final Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandler = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
			processOrderAcceptedWithOrderInfo(buffer, offset, codec);
	    }
    };
    
    private final Handler<OrderCancelledSbeDecoder> orderCancelledHandler = new Handler<OrderCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
			processOrderCancelled(buffer, offset, codec);
		}
    };
        
    private final Handler<OrderCancelledWithOrderInfoSbeDecoder> orderCancelledWithOrderInfoHandler = new Handler<OrderCancelledWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledWithOrderInfoSbeDecoder codec) {
	    	processOrderCancelledWithOrderInfo(buffer, offset, codec);
		}
    };

    private final Handler<OrderExpiredSbeDecoder> orderExpiredHandler = new Handler<OrderExpiredSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder codec) {
			processOrderExpired(buffer, offset, codec);
		}
    };
    
    private final Handler<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandler = new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder codec) {
			processOrderExpiredWithOrderInfo(buffer, offset, codec);
		}
    };
    
    private final Handler<OrderRejectedSbeDecoder> orderRejectedHandler = new Handler<OrderRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder codec) {
			processOrderRejected(buffer, offset, codec);
		}
    };
    
    private final Handler<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandler = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder codec) {
			processOrderRejectedWithOrderInfo(buffer, offset, codec);
		}
    };
    
    private final Handler<OrderCancelRejectedSbeDecoder> orderCancelRejectedHandler = new Handler<OrderCancelRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder codec) {
			processOrderCancelRejected(buffer, offset, codec);
		}
    };
    
    private final Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> orderCancelRejectedWithOrderInfoHandler = new Handler<OrderCancelRejectedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedWithOrderInfoSbeDecoder codec) {
			processOrderCancelRejectedWithOrderInfo(buffer, offset, codec);
		}
    };

    private final Handler<TradeCreatedSbeDecoder> tradeCreatedHandler = new Handler<TradeCreatedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder codec) {
			processTradeCreated(buffer, offset, codec);
		}
    };
    
    private final Handler<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoHandler = new Handler<TradeCreatedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder codec) {
			processTradeCreatedWithOrderInfo(buffer, offset, codec);
		}
    };

    private void putOrder(OrderAndTradeSubscriptionChannel channelUpdate, OrderBuffer orderBuffer){
		orderBySid.put(orderBuffer.id(), orderBuffer);
		ordersByChannelId.put(orderBuffer.channelId(), orderBuffer);
		ordersBySecSid.put(orderBuffer.secSid(), orderBuffer);
		channelUpdate.addOrderUpdate(orderBuffer);
    }
    
    private void putTrade(OrderAndTradeSubscriptionChannel channelUpdate, TradeBuffer tradeBuffer){
		tradeBySid.put(tradeBuffer.id(), tradeBuffer);
		tradesByChannelId.put(tradeBuffer.channelId(), tradeBuffer);
		tradesBySecSid.put(tradeBuffer.secSid(), tradeBuffer);
		channelUpdate.addTradeUpdate(tradeBuffer);
    }
    
    /**
     * Process OrderAccepted and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderAccepted(DirectBuffer buffer, int offset, OrderAcceptedSbeDecoder codec){
    	int orderSid = codec.orderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
    	
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
		
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
        	LOG.warn("Received order accepted that does not exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
        	orderBuffer = OrderBuffer.of(orderEncoder);
        	orderBuffer.populateFrom(codec);
        	putOrder(channelUpdate, orderBuffer);
    		return;
    	}
    	
    	// Merge into existing order
    	orderBuffer.mergeWith(codec);
		channelUpdate.addOrderUpdate(orderBuffer);
		LOG.debug("Order accepted [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));    	
    }
    
    /**
     * Process OrderAcceptedWithOrderInfo and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
	private void processOrderAcceptedWithOrderInfo(DirectBuffer buffer, int offset, OrderAcceptedWithOrderInfoSbeDecoder codec){
		int orderSid = codec.orderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
    		orderBuffer = OrderBuffer.of(orderEncoder);
    		orderBuffer.populateFrom(codec);
    		putOrder(channelUpdate, orderBuffer);
    		LOG.debug("Created order [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));
    		return;
    	}
    	
    	// Merge into existing order instead
    	LOG.warn("Received order accepted with order info for an order that already exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
    	channelUpdate.addOrderUpdate(orderBuffer.mergeWith(codec));
	}
	
    /**
     * Process OrderCancelled and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderCancelled(DirectBuffer buffer, int offset, OrderCancelledSbeDecoder codec){
    	int orderSid = codec.origOrderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
        	LOG.warn("Received order cancelled that does not exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
        	orderBuffer = OrderBuffer.of(orderEncoder);
    		orderBuffer.populateFrom(codec);
    		putOrder(channelUpdate, orderBuffer);
    		return;
    	}
    	
    	// Merge into existing order
    	orderBuffer.mergeWith(codec);
    	channelUpdate.addOrderUpdate(orderBuffer);			
		LOG.debug("Cancelled order [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));
    }
    
    /**
     * Process OrderCancelledWithOrderInfo and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderCancelledWithOrderInfo(DirectBuffer buffer, int offset, OrderCancelledWithOrderInfoSbeDecoder codec){
    	int orderSid = codec.origOrderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
    		orderBuffer = OrderBuffer.of(orderEncoder);
    		orderBuffer.populateFrom(codec);
    		putOrder(channelUpdate, orderBuffer);
    		LOG.debug("Cancelled order with order info [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));
    		return;
    	}
    	
    	// Merge into existing order instead
    	LOG.warn("Received order cancelled with order info for an order that already exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
    	orderBuffer.mergeWith(codec);
    	channelUpdate.addOrderUpdate(orderBuffer);
    }

    /**
     * Process OrderExpired and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderExpired(DirectBuffer buffer, int offset, OrderExpiredSbeDecoder codec){
    	int orderSid = codec.orderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
        	LOG.warn("Received order expired that does not exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
        	orderBuffer = OrderBuffer.of(orderEncoder);
        	orderBuffer.populateFrom(codec);
        	putOrder(channelUpdate, orderBuffer);
    		return;
    	}
    	
    	// Merge into existing order
    	orderBuffer.mergeWith(codec);
    	channelUpdate.addOrderUpdate(orderBuffer);
		LOG.debug("Order expired [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));    	
    }
    
    /**
     * Process OrderExpiredWithOrderInfo and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderExpiredWithOrderInfo(DirectBuffer buffer, int offset, OrderExpiredWithOrderInfoSbeDecoder codec){
    	int orderSid = codec.orderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
    		orderBuffer = OrderBuffer.of(orderEncoder);
    		orderBuffer.populateFrom(codec);
    		putOrder(channelUpdate, orderBuffer);
    		LOG.debug("Order expired with order info [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));
    		return;
    	}
    	
    	// Merge into existing order instead
    	LOG.warn("Received order expired with order info for an order that already exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
    	orderBuffer.mergeWith(codec);
    	channelUpdate.addOrderUpdate(orderBuffer);    	
    }
    
    /**
     * Process OrderRejected and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderRejected(DirectBuffer buffer, int offset, OrderRejectedSbeDecoder codec){
    	int orderSid = codec.orderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
        	LOG.warn("Received order rejected that does not exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
        	orderBuffer = OrderBuffer.of(orderEncoder);
        	orderBuffer.populateFrom(codec, messenger.stringBuffer());
        	putOrder(channelUpdate, orderBuffer);
    		return;
    	}
    	
    	// Merge into existing order
    	orderBuffer.mergeWith(codec, messenger.stringBuffer());
    	channelUpdate.addOrderUpdate(orderBuffer);
		LOG.debug("Order rejected [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));
    }
    
    /**
     * Process OrderRejectedWithOrderInfo and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderRejectedWithOrderInfo(DirectBuffer buffer, int offset, OrderRejectedWithOrderInfoSbeDecoder codec){
    	int orderSid = codec.orderSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
    		orderBuffer = OrderBuffer.of(orderEncoder);
    		orderBuffer.populateFrom(codec, messenger.stringBuffer());
    		putOrder(channelUpdate, orderBuffer);
    		LOG.debug("Order rejected with order info [{}]", OrderDecoder.decodeToString(orderDecoder.wrap(orderBuffer.internalBuffer(), 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION)));
    		return;
    	}
    	
    	// Merge into existing order instead
    	LOG.warn("Received order rejected with order info for an order that already exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
    	orderBuffer.mergeWith(codec, messenger.stringBuffer());
    	channelUpdate.addOrderUpdate(orderBuffer);
    }

    /**
     * Process OrderCancelRejected and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderCancelRejected(DirectBuffer buffer, int offset, OrderCancelRejectedSbeDecoder codec){
    	int channelId = codec.channelId();
		try {
			getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		// We are not going to send this update downstream, because this update doesn't affect status of any order or trade    	
    }
    
    /**
     * Process OrderCancelRejected and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processOrderCancelRejectedWithOrderInfo(DirectBuffer buffer, int offset, OrderCancelRejectedWithOrderInfoSbeDecoder codec){
    	int channelId = codec.channelId();
		try {
			getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		// We are not going to send this update downstream, because this update doesn't affect status of any order or trade    	
    }

    /**
     * Process TradeCreated and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processTradeCreated(DirectBuffer buffer, int offset, TradeCreatedSbeDecoder codec){
    	int channelId = codec.channelId();
    	
    	// Update order
    	int orderSid = codec.orderSid();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in order updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
        	LOG.warn("Received trade created for an order that does not exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}, tradeSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid(), codec.tradeSid());
        	orderBuffer = OrderBuffer.of(orderEncoder);
        	orderBuffer.populateFrom(codec);
        	putOrder(channelUpdate, orderBuffer);
    	}
    	else{
    		// Merge into existing order
    		orderBuffer.mergeWith(codec);
    		channelUpdate.addOrderUpdate(orderBuffer);
    	}
    	
		// Update trade
    	int tradeSid = codec.tradeSid();
    	TradeBuffer tradeBuffer = tradeBySid.get(tradeSid);
    	if (tradeBuffer == null){
    		tradeBuffer = TradeBuffer.of(tradeEncoder);
    		tradeBuffer.populateFrom(codec, messenger.stringBuffer());
    		putTrade(channelUpdate, tradeBuffer);
    		LOG.debug("Created trade [{}]", TradeDecoder.decodeToString(tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION)));
    		return;
    	}
    	
    	// Merge into existing trade
    	LOG.warn("Received trade created for a trade that already exists in our cache [channelId:{}, channelSeq:{}, tradeSid:{}, orderSid:{}]", codec.channelId(), codec.channelSeq(), codec.tradeSid(), codec.orderSid());
    	tradeBuffer.mergeWith(codec, messenger.stringBuffer());
    	channelUpdate.addTradeUpdate(tradeBuffer);
    }
    
    /**
     * Process TradeCreatedWithOrderInfo and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processTradeCreatedWithOrderInfo(DirectBuffer buffer, int offset, TradeCreatedWithOrderInfoSbeDecoder codec){
    	int channelId = codec.channelId();

    	// Update order
    	int orderSid = codec.orderSid();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in trade updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
		OrderBuffer orderBuffer = orderBySid.get(orderSid);
    	if (orderBuffer == null){
    		orderBuffer = OrderBuffer.of(orderEncoder);
    		orderBuffer.populateFrom(codec);
    		putOrder(channelUpdate, orderBuffer);
    	}
    	else {
    		// Merge into existing order instead
    		LOG.warn("Received trade with order info for an order that already exist in our cache [channelId:{}, channelSeq:{}, ordSid:{}]", codec.channelId(), codec.channelSeq(), codec.orderSid());
    		orderBuffer.mergeWith(codec);
    		channelUpdate.addOrderUpdate(orderBuffer);
    	}
    	
    	int tradeSid = codec.tradeSid();
    	TradeBuffer tradeBuffer = tradeBySid.get(tradeSid);
    	if (tradeBuffer == null){
    		tradeBuffer = TradeBuffer.of(tradeEncoder);
    		tradeBuffer.populateFrom(codec, messenger.stringBuffer());
    		putTrade(channelUpdate, tradeBuffer);
    		LOG.debug("Created trade [{}]", TradeDecoder.decodeToString(tradeDecoder.wrap(tradeBuffer.internalBuffer(), 0, TradeSbeDecoder.BLOCK_LENGTH, TradeSbeDecoder.SCHEMA_VERSION)));
    		return;
    	}
    	
    	// Merge into existing trade instead
    	LOG.warn("Received trade created with order info for a trade that already exist in our cache [channelId:{}, channelSeq:{}, tradeSid:{}, ordSid:{}]", 
    			codec.channelId(), 
    			codec.channelSeq(),
    			codec.tradeSid(),
    			codec.orderSid());
    	tradeBuffer.mergeWith(codec, messenger.stringBuffer());
    	channelUpdate.addTradeUpdate(tradeBuffer);
    }
    
    /**
     * Process trade cancelled and merge that into our cache
     * @param buffer
     * @param offset
     * @param codec
     */
    private void processTradeCancelled(DirectBuffer buffer, int offset, TradeCancelledSbeDecoder codec){
    	int tradeSid = codec.tradeSid();
    	int channelId = codec.channelId();
    	OrderAndTradeSubscriptionChannel channelUpdate;
		try {
			channelUpdate = getChannel(channelId, codec.channelSeq());
		} 
		catch (SequenceNumberGapDetectedException e) {
			LOG.error("Detected gap in trade updates.  No solution unless we have a way to query missing message of particular sequence from OMES or we have a persistence service. [orderSid:" + codec.orderSid() + "]", e);
			channelUpdate = getChannelUpdateSafe(channelId, codec.channelSeq());
		}
    	
		TradeBuffer tradeBuffer = tradeBySid.get(tradeSid);
    	if (tradeBuffer == null){
        	LOG.warn("Received trade created that does not exist in our cache [channelId:{}, channelSeq:{}, tradeSid:{}, orderSid:{}]", codec.channelId(), codec.channelSeq(), codec.tradeSid(), codec.orderSid());
        	tradeBuffer = TradeBuffer.of(tradeEncoder);
        	tradeBuffer.populateFrom(codec, messenger.stringBuffer());
        	putTrade(channelUpdate, tradeBuffer);
    		return;
    	}
    	
    	// Merge into existing trade
    	tradeBuffer.mergeWith(codec, messenger.stringBuffer());
    	channelUpdate.addTradeUpdate(tradeBuffer);
    }
    
    private final Handler<TradeCancelledSbeDecoder> tradeCancelledHandler = new Handler<TradeCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCancelledSbeDecoder codec) {
			processTradeCancelled(buffer, offset, codec);
		}
    };

    private void publishSnapshots(){
		// Send out all order and trade changes
		for(Entry<OrderAndTradeSubscriptionChannel> entry : subscriptionChannels.int2ReferenceEntrySet()){
	    	clearTransientStructures();
			OrderAndTradeSubscriptionChannel channel = entry.getValue();
			for (OrderBuffer update : channel.orderUpdates()){
				transientOrderUpdates.get(update.channelId()).put(update.channelSeq(), update);
			}
			channel.clearOrderUpdates();
			for (TradeBuffer update : channel.tradeUpdates()){
				transientTradeUpdates.get(update.channelId()).put(update.channelSeq(), update);
			}
			channel.clearTradeUpdates();
			// Send results
			for (Long2ReferenceRBTreeMap<OrderBuffer> orderUpdatesPerChannel : this.transientOrderUpdates.values()){
				for (OrderBuffer orderBuffer : orderUpdatesPerChannel.values()){
					long result = messenger.trySendWithHeaderInfo(channel.orderSubscribers(),
							refMgr.persi(),
							OrderSbeEncoder.BLOCK_LENGTH,
							OrderSbeEncoder.TEMPLATE_ID,
							OrderSbeEncoder.SCHEMA_ID,
							OrderSbeEncoder.SCHEMA_VERSION,
							orderBuffer.internalBuffer(),
							0, 
							OrderSbeEncoder.BLOCK_LENGTH, 
							sinkSendResults);
					if (result != MessageSink.OK){
						LOG.error("Could not send order to subscribers and/or persist service [result:{}, channelId:{}, channelSeq:{}, secSid:{}]", 
								result, 
								orderBuffer.channelId, 
								orderBuffer.channelSeq, 
								orderBuffer.secSid);
					}
				}
			}
			for (Long2ReferenceRBTreeMap<TradeBuffer> tradeUpdatesPerChannel : this.transientTradeUpdates.values()){
				for (TradeBuffer tradeBuffer : tradeUpdatesPerChannel.values()){
					long result = messenger.trySendWithHeaderInfo(channel.tradeSubscribers(),
							refMgr.persi(),
							TradeSbeEncoder.BLOCK_LENGTH,
							TradeSbeEncoder.TEMPLATE_ID,
							TradeSbeEncoder.SCHEMA_ID,
							TradeSbeEncoder.SCHEMA_VERSION,
							tradeBuffer.internalBuffer(), 
							0, 
							TradeSbeEncoder.BLOCK_LENGTH, 
							sinkSendResults);
					if (result != MessageSink.OK){
						LOG.error("Could not send trade to persist service [result:{}, channelId:{}, channelSeq:{}, secSid:{}]", 
								result, 
								tradeBuffer.channelId, 
								tradeBuffer.channelSeq, 
								tradeBuffer.secSid);
					}
				}
			}
		}
    }
    
    private final Handler<TimerEventSbeDecoder> timerEventHandler = new Handler<TimerEventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder codec) {
	    	if (codec.timerEventType() == TimerEventType.TIMER && codec.clientKey() == CLIENT_KEY_FOR_PUBLISHING){
	    		publishSnapshots();
	    		messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
	    	}
		}
	};
    
    private final TimeoutHandler publishSnapshots = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
			// Retry in a moment
			// TODO Stop after N attempt
			messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {
			long result = timerEventSender.sendTimerEvent(messenger.self(), CLIENT_KEY_FOR_PUBLISHING, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				messenger.newTimeout(publishSnapshotTask.get(), publishFrequency);
			}
		}
	};

	HashMultimap<Long, OrderBuffer> ordersBySecSid(){ return ordersBySecSid; }
	
	HashMultimap<Long, TradeBuffer> tradesBySecSid(){ return tradesBySecSid; }

	
}

