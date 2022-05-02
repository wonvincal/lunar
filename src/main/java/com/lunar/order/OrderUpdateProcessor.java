package com.lunar.order;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lunar.core.Lifecycle;
import com.lunar.core.TimerService;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.service.LifecycleController;

import net.openhft.affinity.Affinity;

/**
 * A class whose main responsibility is to distribute received message to OrderContextManager 
 * 
 * Why using a disruptor?
 * 1) We need to write all incoming messages to journal, disruptor would allow us to supply another
 *    processor to write the binary message into a journal file.
 * 2) Disruptor can consolidate incoming messages from possibly more than one threads into a single
 *    source streams.  This allows me to generate sequence number of each message correctly.
 * 3) A possible 'low latency' way to consume incoming message is to process the message in the 
 *    same 'parsing' thread.  However this increases the risks of not able to parse messages from 
 *    wire fast enough.
 * 
 * @author wongca
 *
 */
public class OrderUpdateProcessor implements EventHandler<OrderUpdateEvent>, LifecycleAware, Lifecycle {
	private static final Logger LOG = LogManager.getLogger(OrderUpdateProcessor.class);
	private final String name;
	private final OrderContextManager contextManager;
	@SuppressWarnings("unused")
	private final TimerService timerService;
	private EventHandler<OrderUpdateEvent> currentOrderUpdateEventHandler; 
	private final Optional<Integer> affinityLock;

	// Codec
	private final Consumer<DirectBuffer>[] consumers;
	private final OrderAcceptedSbeDecoder orderAcceptedDecoder = new OrderAcceptedSbeDecoder();
	private final OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();
	private final OrderCancelRejectedSbeDecoder orderCancelRejectedDecoder = new OrderCancelRejectedSbeDecoder();
	private final OrderExpiredSbeDecoder orderExpiredDecoder = new OrderExpiredSbeDecoder();
	private final OrderRejectedSbeDecoder orderRejectedDecoder = new OrderRejectedSbeDecoder();
	private final TradeCreatedSbeDecoder tradeCreatedDecoder = new TradeCreatedSbeDecoder();
	private final TradeCancelledSbeDecoder tradeCancelledDecoder = new TradeCancelledSbeDecoder();

	// Lifecycle related fields
	private final LifecycleController controller;
	@SuppressWarnings("unused")
	private LifecycleExceptionHandler lifecycleExceptionHandler = LifecycleExceptionHandler.DEFAULT_HANDLER;
	
	private final LifecycleStateHook lifecycleStateHook = new LifecycleStateHook() {
		/**
		 * CAUTION: This is called from a separate thread
		 */
		@Override
		public CompletableFuture<Boolean> onPendingWarmup() {
		    contextManager.warmup();
			return CompletableFuture.completedFuture(true);
		}
		
		@Override
		public void onWarmupEnter() {
			currentOrderUpdateEventHandler = activeOrderUpdateEventHandler;
		};
		
		/**
		 * CAUTION: This is called from a separate thread
		 */
		@Override
		public CompletableFuture<Boolean> onPendingRecovery() {
		    contextManager.recover();
			return CompletableFuture.completedFuture(true);
		}
		
        @Override
        public void onRecoveryEnter() {
        	LOG.info("RECOVERY Change order update event hander to recovery");
            currentOrderUpdateEventHandler = activeOrderUpdateEventHandler;
        };
        
		/**
		 * CAUTION: This is called from a separate thread
		 */
		@Override
		public CompletableFuture<Boolean> onPendingReset() {
			LOG.info("Reset completed [name:{}]", name);
			resetState();
			return CompletableFuture.completedFuture(true);
		}
		
		/**
		 * CAUTION: This is called from a separate thread
		 */
		@Override
		public CompletableFuture<Boolean> onPendingActive() {
		    contextManager.active();
			return CompletableFuture.completedFuture(true);
		}
		
		@Override
		public void onActiveEnter() {
			LOG.info("ACTIVE Change order update event hander to active");
			currentOrderUpdateEventHandler = activeOrderUpdateEventHandler;
		};
		
		@Override
		public void onResetEnter() {
			currentOrderUpdateEventHandler = noopOrderUpdateEventHandler;
		};
		
		@Override
		public void onStopped() {
			currentOrderUpdateEventHandler = noopOrderUpdateEventHandler;
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingStop() {
			return CompletableFuture.completedFuture(true);
		};
	};

	
	/**
	 * How to turn an external security id into an internal security id
	 */
	
	public static OrderUpdateProcessor of(String name, 
			TimerService timerService,
			OrderContextManager contextManager,
			Optional<Integer> affinityLock){
		return new OrderUpdateProcessor(name, timerService, contextManager, affinityLock);
	}
	
	/**
	 * 
	 * @param name
	 * @param updateListener Listener to all updates from the exchange
	 * @param exchange
	 */
	@SuppressWarnings("unchecked")
	OrderUpdateProcessor(String name, 
			TimerService timerService, 
			OrderContextManager contextManager,
			Optional<Integer> affinityLock) {
		this.name = name;
		this.timerService = timerService;
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateHook);
		this.contextManager = contextManager;
		this.currentOrderUpdateEventHandler = noopOrderUpdateEventHandler;
		this.consumers = new Consumer[OrderUpdateEvent.NUM_UPDATE_TYPES];
		this.affinityLock = affinityLock;
		
		Consumer<DirectBuffer> nullConsumer = new Consumer<DirectBuffer>() {
			@Override
			public void accept(DirectBuffer buffer) {
				LOG.error("Null DirectBuffer Consumer");
			}
		};
		for (int i = 0; i < OrderUpdateEvent.NUM_UPDATE_TYPES; i++){
			this.consumers[i] = nullConsumer;
		}
		this.consumers[OrderUpdateEvent.ORDER_ACCEPTED] = this::consumerOrderAccepted;
		this.consumers[OrderUpdateEvent.ORDER_CANCELLED] = this::consumerOrderCancelled;
		this.consumers[OrderUpdateEvent.ORDER_CANCEL_REJECTED] = this::consumerOrderCancelRejected;
		this.consumers[OrderUpdateEvent.ORDER_EXPIRED] = this::consumerOrderExpired;
		this.consumers[OrderUpdateEvent.ORDER_REJECTED] = this::consumerOrderRejected;
		this.consumers[OrderUpdateEvent.TRADE_CANCELLED] = this::consumerTradeCancelled;
		this.consumers[OrderUpdateEvent.TRADE_CREATED] = this::consumerTradeCreated;
		this.consumers[OrderUpdateEvent.END_OF_RECOVERY] = this::consumerEndOfRecovery;
	}
	
	private void consumerOrderAccepted(DirectBuffer buffer){
		orderAcceptedDecoder.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveAccepted(buffer, 0, orderAcceptedDecoder);
	}
	
	private void consumerOrderCancelled(DirectBuffer buffer){
		orderCancelledDecoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveCancelled(buffer, 0, orderCancelledDecoder);
	}
	
	private void consumerOrderCancelRejected(DirectBuffer buffer){
		orderCancelRejectedDecoder.wrap(buffer, 0, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH, OrderCancelRejectedSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveCancelRejected(buffer, 0, orderCancelRejectedDecoder);
	}

	private void consumerOrderExpired(DirectBuffer buffer){
		orderExpiredDecoder.wrap(buffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveExpired(buffer, 0, orderExpiredDecoder);
	}

	private void consumerOrderRejected(DirectBuffer buffer){
		orderRejectedDecoder.wrap(buffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveRejected(buffer, 0, orderRejectedDecoder);
	}

	private void consumerTradeCreated(DirectBuffer buffer){
		tradeCreatedDecoder.wrap(buffer, 0, TradeCreatedSbeDecoder.BLOCK_LENGTH, TradeCreatedSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveTradeCreated(buffer, 0, tradeCreatedDecoder);
	}

	private void consumerTradeCancelled(DirectBuffer buffer){
		tradeCancelledDecoder.wrap(buffer, 0, TradeCancelledSbeDecoder.BLOCK_LENGTH, TradeCancelledSbeDecoder.SCHEMA_VERSION);
		contextManager.receiveTradeCancelled(buffer, 0, tradeCancelledDecoder);
	}
	
	private final CompletableFuture<Boolean> endOfRecoveryFuture = new CompletableFuture<>();
	
	public CompletableFuture<Boolean> endOfRecoveryFuture(){
		return endOfRecoveryFuture;
	}
	
	private void consumerEndOfRecovery(DirectBuffer buffer){
		endOfRecoveryFuture.complete(true);
	}
	
	public String name(){
		return name;
	}

	@Override
	public void onStart() {
		if (affinityLock.isPresent()){
			Affinity.setAffinity(affinityLock.get());
			LOG.info("Started disruptor of OrderUpdateProcessor with affinity lock [name:{}, tid:{}, cpuId:{}]", name, Affinity.getThreadId(), Affinity.getCpu());
		}
		else{
			LOG.info("Started disruptor of OrderUpdateProcessor [name:{}, tid:{}, cpuId:{}]", name, Affinity.getThreadId(), Affinity.getCpu());			
		}
	}

	@Override
	public void onShutdown() {
		LOG.info("Shutdown disruptor of OrderUpdateProcessor [name:{}]", name);
	}

	@Override
	public void onEvent(OrderUpdateEvent event, long sequence, boolean endOfBatch) throws Exception {
//		LOG.info("Received event in disruptor of order update processor");
		this.currentOrderUpdateEventHandler.onEvent(event, sequence, endOfBatch);
	}
	
	@Override
	public LifecycleController controller() {
		return controller;
	}

	private EventHandler<OrderUpdateEvent> activeOrderUpdateEventHandler = new EventHandler<OrderUpdateEvent>() {
		@Override
		public void onEvent(OrderUpdateEvent event, long sequence, boolean endOfBatch) throws Exception {
			consumers[event.updateType()].accept(event.buffer());
		}
	};

	private EventHandler<OrderUpdateEvent> noopOrderUpdateEventHandler = new EventHandler<OrderUpdateEvent>() {
		@Override
		public void onEvent(OrderUpdateEvent event, long sequence, boolean endOfBatch) throws Exception {
			LOG.error("Got order request event with noop handler");
		}
	};

	@Override
	public void lifecycleExceptionHandler(LifecycleExceptionHandler handler) {
		this.lifecycleExceptionHandler = handler;
	}
	
	public OrderUpdateProcessor noopOrderUpdateEventHandler(EventHandler<OrderUpdateEvent> handler){
		if (this.currentOrderUpdateEventHandler == this.noopOrderUpdateEventHandler){
			this.currentOrderUpdateEventHandler = handler;
		}
		this.noopOrderUpdateEventHandler = handler;
		return this;
	}

	private void resetState(){
	    contextManager.reset();
	}
	
	public boolean isClear(){
	    return contextManager.isClear();
	}
}
