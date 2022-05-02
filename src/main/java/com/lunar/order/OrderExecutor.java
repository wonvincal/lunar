package com.lunar.order;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.Lifecycle;
import com.lunar.core.ThrottleTracker;
import com.lunar.core.TimerService;
import com.lunar.service.LifecycleController;

import net.openhft.affinity.Affinity;

/**
 * A class that is responsible to handle order execution.  This should be exchange specific.
 * Input is passed thru ring buffer of OrderRequestEvent message. 
 * @author wongca
 *
 */
public class OrderExecutor implements Runnable, Lifecycle {
	private static final Logger LOG = LogManager.getLogger(OrderExecutor.class);
	private final int numThrottlesForWarmup = 1_048_576;
	private final String name; 
	private final ThrottleTracker[] throttleTrackers;
	private final int[] numThrottlesPerTracker;
	private final TimerService timerService;
	private final LineHandlerEngine lineHandlerEngine;
	private final OrderRequestCompletionHandler orderReqCompletionHandler;
	private volatile Consumer<OrderRequest> currentOrderRequestEventHandler;
	private volatile long numRequestProcessed;
	private final Optional<Integer> affinityLock;
	private final OneToOneConcurrentArrayQueue<OrderRequest> queue;
	private int currentBatchOrderSize = 0;
	private int currentBatchActionSize = 0;
	
	private final int maxNumBatchOrders;
	private final OrderRequest[] batchedOrders;
	private final BatchedAction[] batchedActions;
	
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
			for (int i = 0; i < throttleTrackers.length; i++){
				throttleTrackers[i].changeNumThrottles(numThrottlesForWarmup);
			}
			return CompletableFuture.completedFuture(true);
		}
		
		@Override
		public void onWarmupEnter() {
			currentOrderRequestEventHandler = activeOrderRequestEventHandler;
		};

		/**
		 * CAUTION: This is called from a separate thread
		 */
		@Override
		public CompletableFuture<Boolean> onPendingRecovery() {
			return CompletableFuture.completedFuture(true);
		}
		
        @Override
        public void onRecoveryEnter() {
            currentOrderRequestEventHandler = activeOrderRequestEventHandler;
        };

        @Override
        public void onResetEnter() {
        	currentOrderRequestEventHandler = noopOrderRequestEventHandler;
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
			return CompletableFuture.completedFuture(true);
		}
		
		@Override
		public void onActiveEnter() {
			currentOrderRequestEventHandler = activeOrderRequestEventHandler;
		};
		
		@Override
		public void onStopped() {
			currentOrderRequestEventHandler = noopOrderRequestEventHandler;
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingStop() {
			return CompletableFuture.completedFuture(true);
		};
	};
	
	/**
	 * 
	 * @param name
	 * @param throttleTracker
	 * @param waitTime If an order is throttled, the time to wait (ns) before re-attempting to get the next throttle.
	 * @param timerService
	 * @param omes
	 * @param sender
	 * @param exchange
	 * @return
	 */
	public static OrderExecutor of(String name,
			ThrottleTracker[] throttleTrackers,
			TimerService timerService, 
			LineHandlerEngine lineHandlerEngine,
			OrderRequestCompletionHandler orderReqCompletionHandler,
			Optional<Integer> boundToCpu,
			Optional<Integer> maxNumBatchOrders,
			int queueSize){
		return new OrderExecutor(name, throttleTrackers, timerService, lineHandlerEngine, orderReqCompletionHandler, boundToCpu, maxNumBatchOrders, queueSize);
	}
	
	OrderExecutor(String name, 
			ThrottleTracker[] throttleTrackers, 
			TimerService timerService, 
			LineHandlerEngine lineHandlerEngine, 
			OrderRequestCompletionHandler orderReqCompletionHandler,
			Optional<Integer> affinityLock,
			Optional<Integer> maxNumBatchOrders,
			int queueSize){
		this.name = name;
		this.throttleTrackers = throttleTrackers;
		this.numThrottlesPerTracker = new int[throttleTrackers.length];
		for (int i = 0; i < throttleTrackers.length; i++){
			this.numThrottlesPerTracker[i] = throttleTrackers[i].numThrottles();
		}
		this.timerService = timerService;
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateHook);
		this.lineHandlerEngine = lineHandlerEngine;
		this.orderReqCompletionHandler = orderReqCompletionHandler;
		this.currentOrderRequestEventHandler = noopOrderRequestEventHandler;
		this.affinityLock = affinityLock;
		this.maxNumBatchOrders = maxNumBatchOrders.orElse(1);
		this.batchedOrders = new OrderRequest[this.maxNumBatchOrders];
		this.batchedActions = new BatchedAction[this.maxNumBatchOrders * 10];
		for (int i = 0; i < this.batchedActions.length; i++){
			this.batchedActions[i] = new BatchedAction();
		}
		this.queue = new OneToOneConcurrentArrayQueue<>(queueSize);
	}
	
	private static class BatchedAction {
		private OrderRequest request;
		private int type;
		private long resultTimeNs;
	}

	
	public String name(){
		return name;
	}
	
	private volatile boolean isRunning = false;
	
	@Override
	public void run(){
		LOG.info("Started thread for OrderExecutor [name:{}]", name);
		if (affinityLock.isPresent()){
			Affinity.setAffinity(affinityLock.get());
			LOG.info("Started thread of OrderExecutor with affinity lock [name:{}, tid:{}, cpuId:{}]", name, Affinity.getThreadId(), Affinity.getCpu());
		}
		else{
			LOG.info("Started thread of OrderExecutor [name:{}, tid:{}, cpuId:{}]", name, Affinity.getThreadId(), Affinity.getCpu());
		}

		isRunning = true;
		int numRequests = 0;
		while (isRunning){
			numRequests = queue.drain(currentOrderRequestEventHandler);
			if (numRequests > 0){
				flushBatchedOrdersAndActions();
			}
		}
	}

	protected void consumeAll(){
		int numRequests = queue.drain(currentOrderRequestEventHandler);
		if (numRequests > 0){
			flushBatchedOrdersAndActions();
		}		
	}
	
	protected long numRequestProcessed(){
		return numRequestProcessed;
	}

	public void stopRunning(){
		this.isRunning = false;
		LOG.info("Stop Order Executor from running");
	}
	
	public boolean isRunning(){
		return isRunning;
	}
	
	public OneToOneConcurrentArrayQueue<OrderRequest> queue(){
		return this.queue;
	}

	private void flushBatchedOrdersAndActions(){
		// Send orders before sending actions
		OrderRequest request = null;			
		for (int i = 0; i < currentBatchOrderSize; i++){
			request = batchedOrders[i]; 
			try {
				lineHandlerEngine.sendOrderRequest(request);
			}
			catch (Exception e){
				orderReqCompletionHandler.fail(request, e);
				LOG.error("Caught exception when sending order to exchange", e);
			}
		}
		currentBatchOrderSize = 0;

		// Send actions
		BatchedAction action = null;
		for (int i = 0; i < currentBatchActionSize; i++){
			action = batchedActions[i]; 
			request = action.request;
			if (action.type == ACTION_TIMEOUT){
				LOG.debug("Timeout order [orderSid:{}, secSid:{}]", request.orderSid(), request.secSid());
				orderReqCompletionHandler.timeout(request);							
			}
			else if (action.type == ACTION_THROTTLED){
				LOG.debug("Throttled request [orderSid:{}, secSid:{}, now:{}, requestExpiry:{}, retry: false, trackerIndex:{}]", 
						request.orderSid(), 
						request.secSid(), 
						action.resultTimeNs,
						request.timeoutAtNanoOfDay(),
						request.assignedThrottleTrackerIndex());
				orderReqCompletionHandler.throttled(request);
			}
			else if (action.type == ACTION_THROTTLED_TIMEOUT){
				LOG.debug("Throttled timeout: [orderSid:{}, secSid:{}, now:{}, request set to expire at:{}, trackerIndex:{}]",
						request.orderSid(),
						request.secSid(),
						action.resultTimeNs, 
						request.timeoutAtNanoOfDay(),
						request.assignedThrottleTrackerIndex());
				orderReqCompletionHandler.timeoutAfterThrottled(request);
			}
			else if (action.type == ACTION_SENT_TO_EXCHANGE){
				orderReqCompletionHandler.sendToExchange(request, action.resultTimeNs);
			}
		}
		currentBatchActionSize = 0;
	}
	
	private static int ACTION_TIMEOUT = 0;
	private static int ACTION_THROTTLED = 1;
	private static int ACTION_THROTTLED_TIMEOUT = 3;
	private static int ACTION_SENT_TO_EXCHANGE = 4;
	private final Consumer<OrderRequest> activeOrderRequestEventHandler = new Consumer<OrderRequest>() {
		public void accept(OrderRequest request) {
			// Batched order if endOfBatch is false, send order if endOfBatch is true
			// Save all non rms operation to the end of the batch operation
//			LOG.info("Received order [orderSid:{}]", request.orderSid());
			long ts = timerService.toNanoOfDay();
			if (ts > request.timeoutAtNanoOfDay()){
				// Any reason that we can delay sending this?
				addBatchAction(request, ACTION_TIMEOUT, ts);
				return;
			}

			try{
				ThrottleTracker throttleTracker = throttleTrackers[request.assignedThrottleTrackerIndex()];
				// If throttle check is required && we have no more throttle
				if (request.throttleCheckRequired() && !throttleTracker.getThrottle(request.numThrottleRequiredToProceed())){
					// When we get throttled, we should flush any batched orders immediately, since all subsequent order(s) will
					// be throttled anyway.  We may get into a situation where a 'Buy' is throttled, but the next 'Sell' may not due to
					// 'request.numThrottleRequiredToProceed'.  However, there is no guarrantee that the next request will be a 'Sell'.
					// After flushing the order, we should add the 'elapsed' time into 'each throttle that was being used'					
					flushBatchedOrdersAndActions(); // <<-- force flush if there is any
					
					// Check if we need to wait for throttle, if yes, it is going to be a relatively long wait
					if (!request.retry() || request.numThrottleRequiredToProceed() > OrderRequest.NUM_THROTTLE_REQUIRED_TO_PROCEED){
						addBatchAction(request, ACTION_THROTTLED, ts);
						flushBatchedOrdersAndActions();
						return;
					}

					long nextAvailSystemNs = throttleTracker.nextAvailNs();
					ts = timerService.nanoTime();
					long timeoutInSystemNs = timerService.nanoTime() + (request.timeoutAtNanoOfDay() - timerService.toNanoOfDay());
					if (timeoutInSystemNs >= nextAvailSystemNs){
						// Correct thing to do
						// 1) flush all batched orders and actions, because you are already throttled, let's do other first
						// 2) busy spin to get a throttle, then send it out.  Actually, during this time, a lot can be done, but 
						//    most order will be rejected anyway (if an appropriate timeout is specified).
						// It's not 100% here, because nextAvailSystemNs should be adjusted by 'currentBatchOrderSize' * 60us
						// busy spin
						int count = 0;
						do {
							count++;
						}
						while (!throttleTracker.getThrottle());
						if (LOG.isTraceEnabled()){
							LOG.trace("Waited to get the next throttle [orderSid:{}, secSid:{}, count:{}]", request.orderSid(), request.secSid(), count);
						}
					}
					else {
						addBatchAction(request, ACTION_THROTTLED_TIMEOUT, ts);
						return;				
					}
				}
				
				// send out the message
				batchedOrders[currentBatchOrderSize++] = request;
				addBatchAction(request, ACTION_SENT_TO_EXCHANGE, timerService.nanoTime());
				if (currentBatchOrderSize >= maxNumBatchOrders){
					flushBatchedOrdersAndActions();
				}
//				LOG.info("Sending request to line handler [orderSid:{}]", request.orderSid());
			}
			catch (Exception e){
				orderReqCompletionHandler.fail(request, e);
				LOG.error("Caught exception when sending order to exchange", e);
			}
		}
	}; 
	
	private void addBatchAction(OrderRequest request, int type, long resultTimeNs){
		BatchedAction action = batchedActions[currentBatchActionSize++];
		action.request = request;
		action.type = type;		
		action.resultTimeNs = resultTimeNs;
	}
	
	private Consumer<OrderRequest> noopOrderRequestEventHandler = new Consumer<OrderRequest>() {
		public void accept(OrderRequest event) {
			LOG.error("Got order request event with noop handler");
		}
	};

	@Override
	public void lifecycleExceptionHandler(LifecycleExceptionHandler handler) {
		this.lifecycleExceptionHandler = handler;
	}

	@Override
	public LifecycleController controller() {
		return controller;
	}
	
	OrderExecutor noopOrderRequestEventHandler(Consumer<OrderRequest> handler){
		if (this.currentOrderRequestEventHandler == this.noopOrderRequestEventHandler){
			this.currentOrderRequestEventHandler = handler;
		}
		this.noopOrderRequestEventHandler = handler;
		return this;
	}

	private void resetState(){
		for (int i = 0; i < throttleTrackers.length; i++){
			throttleTrackers[i].changeNumThrottles(numThrottlesPerTracker[i]);
		}
	}
	
	public boolean isClear(){
		for (int i = 0; i < throttleTrackers.length; i++){
			if (throttleTrackers[i].numThrottles() != numThrottlesPerTracker[i]){
				return false;
			}
		}
		return true;
	}
	
	int currentBatchOrderSize(){
		return currentBatchOrderSize;
	}
	
	int currentBatchActionSize(){
		return currentBatchActionSize;
	}
	
	OrderRequest[] batchedOrders(){
		return batchedOrders;
	}
	
	BatchedAction[] batchedActions(){
		return batchedActions;
	}

	int maxNumBatchOrders(){
		return maxNumBatchOrders;
	}
	
	Consumer<OrderRequest> currentOrderRequestEventHandler(){
		return currentOrderRequestEventHandler;
	}
	
	ThrottleTracker[] throttleTrackers(){
		return throttleTrackers;
	}
}
