package com.lunar.order;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.LineHandlerConfig;
import com.lunar.core.Lifecycle;
import com.lunar.core.LifecycleState;
import com.lunar.core.TimerService;
import com.lunar.exception.SessionDisconnectedException;
import com.lunar.message.Command;
import com.lunar.message.binary.Messenger;
import com.lunar.service.LifecycleController;
import com.lunar.util.DisruptorUtil;

/**
 * Responsible for bringing up LineHandlerEngine, OrderExecutor and OrderUpdateReceiver
 * 
 * Idle
 * 
 * Warmup
 * - Order Executor must be in warmup
 * - Update Processor must be in warmup
 * - Engine must be in warmup
 * 
 * Recovery
 * - Order Executor must be in recovery
 * - Update Processor must be in recovery
 * - Engine must be in recovery
 * 
 * Pending Active
 * - Must have been initialized
 * - Each child must be in active state.  If not start it.
 * 
 * Active
 * - Order Executor must be active
 * - Update Processor must be active
 * - Engine must be active
 * 
 * Stopped
 * 
 * @author wongca
 *
 */
public class LineHandler implements Lifecycle {
	private static final Logger LOG = LogManager.getLogger(LineHandler.class);
	private final int id;
	private final String name;
	protected boolean init;
	
	// Lifecycle related fields
	private final LifecycleController controller;
	private LifecycleExceptionHandler lifecycleExceptionHandler = LifecycleExceptionHandler.DEFAULT_HANDLER; 
	
	private LifecycleExceptionHandler ordExecLifecycleExceptionHandler = new LifecycleExceptionHandler() {
		
		@Override
		public void handleOnStartException(Throwable e) {
			LOG.error("Cannot start order executor [name:{}]", name);
			lifecycleExceptionHandler.handleOnStartException(e);
		}
		
		@Override
		public void handleOnShutdownException(Throwable e) {
			LOG.error("Cannot shutdown order executor [name:{}]", name);
			lifecycleExceptionHandler.handleOnShutdownException(e);
		}
		
		@Override
		public boolean handle(LifecycleState current, Throwable e) {
			LOG.error("Caught exception in order executor [name:" + name + ", current:" + current.name() +"]", name, current, e);
			lifecycleExceptionHandler.handle(current, e);
			return false;
		}
	};
	
	private LifecycleExceptionHandler updateProcessorLifecycleExceptionHandler = new LifecycleExceptionHandler() {
		
		@Override
		public void handleOnStartException(Throwable e) {
			LOG.error("Cannot start update processor [name:{}]", name);
			lifecycleExceptionHandler.handleOnStartException(e);
		}
		
		@Override
		public void handleOnShutdownException(Throwable e) {
			LOG.error("Cannot shutdown update processor [name:{}]", name);
			lifecycleExceptionHandler.handleOnShutdownException(e);
		}
		
		@Override
		public boolean handle(LifecycleState current, Throwable e) {
			LOG.error("Caught exception in update processor [name:" + name + ", current:" + current.name() +"]", name, current, e);
			lifecycleExceptionHandler.handle(current, e);
			return false;
		}
	};
	
	private LifecycleExceptionHandler engineLifecycleExceptionHandler = new LifecycleExceptionHandler() {
		
		@Override
		public void handleOnStartException(Throwable e) {
		}
		
		@Override
		public void handleOnShutdownException(Throwable e) {
		}
		
		@Override
		public boolean handle(LifecycleState current, Throwable e) {
			LOG.error("Caught exception in line handler engine [name:" + name + ", current:" + current.name() +"]", name, current, e);
			if (e instanceof SessionDisconnectedException){
				// Should I wait or bounce?
			}
			return false;
		}
	};
	
	private final ExecutorService stopExecutor;

	// Order executor
	private final OrderExecutor orderExecutor;
	private final ExecutorService executor;
 	private OneToOneConcurrentArrayQueue<OrderRequest> orderExecutorQueue;
	
	// Engine
	private final LineHandlerEngine engine;
	
	// Order update receiver
	private final OrderUpdateProcessor updateProcessor;
	private Disruptor<OrderUpdateEvent> disruptorForOrderUpdateProcessor;
	private RingBuffer<OrderUpdateEvent> orderUpdateEventRingBuffer;
	
	private final LineHandlerConfig lineHandlerConfig;
	private final TimerService timerService;
	
	static LineHandler of(LineHandlerConfig lineHandlerConfig,
			Messenger messenger,
			OrderExecutor orderExecutor,
			OrderUpdateProcessor updateProcessor,
			LineHandlerEngine engine){
		
		return new LineHandler(lineHandlerConfig, messenger, orderExecutor, updateProcessor, engine);
	}
	
	LineHandler(LineHandlerConfig lineHandlerConfig,
			Messenger messenger, 
			OrderExecutor orderExecutor,
			OrderUpdateProcessor updateProcessor,
			LineHandlerEngine engine){
		this.lineHandlerConfig = lineHandlerConfig;
		this.id = lineHandlerConfig.id();
		this.name = lineHandlerConfig.name();
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateChangeHandler);
		this.timerService = messenger.timerService();
		
		// Initialize OrderUpdateReceiver with Disruptor
		this.updateProcessor = updateProcessor;
		this.updateProcessor.lifecycleExceptionHandler(updateProcessorLifecycleExceptionHandler);
		
		// Initialize OrderExecutor with Disruptor
		this.orderExecutor = orderExecutor;
		this.orderExecutorQueue = this.orderExecutor.queue();
		this.orderExecutor.lifecycleExceptionHandler(ordExecLifecycleExceptionHandler);
		
		this.engine = engine;
		this.engine.lifecycleExceptionHandler(engineLifecycleExceptionHandler);
		
		this.stopExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(this.name, this.name + "-action"));
		this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("line-handler", "ord-executor"));
	}
	
	@SuppressWarnings("unchecked")
	public void init(){
		disruptorForOrderUpdateProcessor = new Disruptor<OrderUpdateEvent>(
				OrderUpdateEvent::new,
				lineHandlerConfig.ordUpdRecvQueueSize(),
				new NamedThreadFactory("line-handler", "ord-upd-processor"),
				lineHandlerConfig.singleProducerToOrdUpdRecv() ? ProducerType.SINGLE : ProducerType.MULTI,
						DisruptorUtil.convertToDisruptorStrategy(lineHandlerConfig.ordUpdRecvWaitStrategy()));
		disruptorForOrderUpdateProcessor.handleEventsWith(updateProcessor);
		disruptorForOrderUpdateProcessor.setDefaultExceptionHandler(orderUpdateReceiverExceptionHandler);

		if (!running.compareAndSet(false, true)){
			LOG.error("Cannot start disruptors because they have already been started");
		}
		
		this.executor.execute(orderExecutor);
		orderUpdateEventRingBuffer = disruptorForOrderUpdateProcessor.start();
		LOG.info("Started disruptors for order executor and order update processor");

		engine.init(new OrderUpdateEventProducer(orderUpdateEventRingBuffer));
		LOG.info("Initialized line handler [id:{}, name:{}]", this.id, this.name);
		init = true;
	}

	private final com.lmax.disruptor.ExceptionHandler<OrderUpdateEvent> orderUpdateReceiverExceptionHandler = new com.lmax.disruptor.ExceptionHandler<OrderUpdateEvent>() {
		
		@Override
		public void handleOnStartException(Throwable ex) {
			LOG.error("Caught exception on start of disruptor for " + name, ex);
		}
		
		@Override
		public void handleOnShutdownException(Throwable ex) {
			// If we cannot shutdown executor, we should log the error
			LOG.error("Caught exception on shutdown of disruptor for " + name, ex);
		}
		
		@Override
		public void handleEventException(Throwable ex, long sequence, OrderUpdateEvent event) {
			// If we receive exception when handling event in order executor, we should log it but keeps
			// everything running
			LOG.error("Caught exception on processing of event for " + name + ": " + event.toString(), ex);
		}
	}; 

	public int id(){
		return id;
	}
	
	@Override
	public String name() {
		return name;
	}

	private final AtomicBoolean running = new AtomicBoolean(false);
	
	private boolean stopChildDisruptors(){
		if (running.compareAndSet(true, false)){
			boolean doneWithOrderExecutor = stopExecutor(orderExecutor, executor, timerService, id, name, "order-executor");
			boolean doneWithUpdateProcessor = stopDisruptor(disruptorForOrderUpdateProcessor, timerService, id, name, "order-update-processor");
			return doneWithOrderExecutor && doneWithUpdateProcessor;
		}
		LOG.error("Cannot stop disruptors because they have already been (or in-progress-of-being) stopped");		
		return false;
	}
	
	private static boolean stopExecutor(OrderExecutor orderExecutor, ExecutorService executor, TimerService timerService, int id, String name, String item){
		final long timeoutNs = TimeUnit.SECONDS.toNanos(5);
		long timeoutExpiryNs = timerService.toNanoOfDay() + timeoutNs;
		boolean done = false;
		orderExecutor.stopRunning();
		while (timerService.toNanoOfDay() <= timeoutExpiryNs){
			if (!orderExecutor.isRunning()){
				done = true;
				break;					
			}
		}
		executor.shutdown();
		if (done){
			LOG.info("Stopped thread for {}", item);
		}
		else {
			LOG.error("Cannot stop thread for {} within {} ns", timeoutNs);
		}
		return done;
	}
	
	private static boolean stopDisruptor(@SuppressWarnings("rawtypes") Disruptor disruptor, TimerService timerService, int id, String name, String item){
		final int timeout = 1;
		final long timeoutNs = TimeUnit.SECONDS.toNanos(5);
		long timeoutExpiryNs = timerService.toNanoOfDay() + timeoutNs;
		boolean done = false;
		while (timerService.toNanoOfDay() <= timeoutExpiryNs){
			try {
				disruptor.shutdown(timeout, TimeUnit.SECONDS);
				done = true;
				break;
			} 
			catch (TimeoutException e) {
				LOG.info("Disruptor for {} is still active [id:{}, name:{}]", item, id, name);
			}
		}
		if (done){
			LOG.info("Stopped disruptor for {}", item);
		}
		else {
			LOG.error("Cannot stop disruptor for {} within {} ns", timeoutNs);
		}
		return done;
	}
	
	private final LifecycleStateHook lifecycleStateChangeHandler = new LifecycleStateHook() {
		@Override
		public CompletableFuture<Boolean> onPendingReset() {
			if (!init){
				return CompletableFuture.completedFuture(false);
 			}
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			engine.reset().whenComplete((state, cause) -> {
				if (cause != null){
					LOG.error("Not able to idle engine", cause);
					future.complete(false);
					return;
				}
				if (state != LifecycleState.RESET){
					LOG.error("Engine is not in idle state [state:{}]", state.name());
					future.complete(false);
					return;
				}
				
				orderExecutor.reset().whenComplete((stateOrderExecutor, causeOrderExecutor) -> {
					if (causeOrderExecutor != null){
						LOG.error("Not able to idle order executor", causeOrderExecutor);
						future.complete(false);
						return;
					}
					if (stateOrderExecutor != LifecycleState.RESET){
						LOG.error("Order Executor is not in idle state [state:{}]", stateOrderExecutor.name());
						future.complete(false);
						return;
					}
					
					updateProcessor.reset().whenComplete((stateUpdateProcessor, causeUpdateProcessor) -> {
						if (causeUpdateProcessor != null){
							LOG.error("Not able to idle update processor", causeUpdateProcessor);
							future.complete(false);
							return;
						}
						if (stateUpdateProcessor != LifecycleState.RESET){
							LOG.error("Update Processor is not in idle state [state:{}]", stateUpdateProcessor.name());
							future.complete(false);
							return;
						}
						future.complete(true);
					});
				});
			});
			
			return future;
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingActive() {
			if (!init){
				return CompletableFuture.completedFuture(false);
			}
			LOG.info("LIFE linehandler pending active");
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			engine.active().whenComplete((state, cause) -> {
				if (cause != null){
					LOG.error("Not able to activate engine", cause);
					future.complete(false);
					return;
				}
				if (state != LifecycleState.ACTIVE){
					LOG.error("Engine is not in active state [state:{}]", state.name());
					future.complete(false);
					return;
				}
			
				LOG.info("LIFE engine pending active");
				
				orderExecutor.active().whenComplete((stateOrderExecutor, causeOrderExecutor) -> {
					if (causeOrderExecutor != null){
						LOG.error("Not able to activate order executor", causeOrderExecutor);
						future.complete(false);
						return;
					}
					if (stateOrderExecutor != LifecycleState.ACTIVE){
						LOG.error("Order Executor is not in active state [state:{}]", stateOrderExecutor.name());
						future.complete(false);
						return;
					}
					
					LOG.info("LIFE order executor pending active");
					
					updateProcessor.active().whenComplete((stateUpdateProcessor, causeUpdateProcessor) -> {
						if (causeUpdateProcessor != null){
							LOG.error("Not able to activate update processor", causeUpdateProcessor);
							future.complete(false);
							return;
						}
						if (stateUpdateProcessor != LifecycleState.ACTIVE){
							LOG.error("Update Processor is not in active state [state:{}]", stateUpdateProcessor.name());
							future.complete(false);
							return;
						}
						
						LOG.info("LIFE update processor pending active");
						
						future.complete(true);
					});
				});
			});
			return future;
		}
		
		@Override
		public CompletableFuture<Boolean> onPendingWarmup() {
			if (!init){
				LOG.error("Line handler cannot be moved to warmup state.  It has not been initialized. [name:{}]", name);
				return CompletableFuture.completedFuture(false);
			}
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			engine.warmup().whenComplete((state, cause) -> {
				if (cause != null){
					LOG.error("Not able to warmup engine", cause);
					future.complete(false);
					return;
				}
				if (state != LifecycleState.WARMUP){
					LOG.error("Engine is not in warmup state [state:{}]", state.name());
					future.complete(false);
					return;
				}
				
				orderExecutor.warmup().whenComplete((stateOrderExecutor, causeOrderExecutor) -> {
					if (causeOrderExecutor != null){
						LOG.error("Not able to warmup order executor", causeOrderExecutor);
						future.complete(false);
						return;
					}
					if (stateOrderExecutor != LifecycleState.WARMUP){
						LOG.error("Order Executor is not in warmup state [state:{}]", stateOrderExecutor.name());
						future.complete(false);
						return;
					}
					
					updateProcessor.warmup().whenComplete((stateUpdateProcessor, causeUpdateProcessor) -> {
						if (causeUpdateProcessor != null){
							LOG.error("Not able to warmup update processor", causeUpdateProcessor);
							future.complete(false);
							return;
						}
						if (stateUpdateProcessor != LifecycleState.WARMUP){
							LOG.error("Update Processor is not in warmup state [state:{}]", stateUpdateProcessor.name());
							future.complete(false);
							return;
						}
						future.complete(true);
					});
				});
			});
			
			return future;
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingRecovery() {
			if (!init){
				return CompletableFuture.completedFuture(false);
			}
			CompletableFuture<Boolean> future = new CompletableFuture<>();
            orderExecutor.recover().whenComplete((stateOrderExecutor, causeOrderExecutor) -> {
                if (causeOrderExecutor != null){
                    LOG.error("Not able to recover order executor", causeOrderExecutor);
                    future.complete(false);
                    return;
                }
                if (stateOrderExecutor != LifecycleState.RECOVERY){
                    LOG.error("Order Executor is not in recover state [state:{}]", stateOrderExecutor.name());
                    future.complete(false);
                    return;
                }

                LOG.info("LIFE Order executor got into recovery state successfully");
                updateProcessor.recover().whenComplete((stateUpdateProcessor, causeUpdateProcessor) -> {
                    if (causeUpdateProcessor != null){
                        LOG.error("Not able to recover update processor", causeUpdateProcessor);
                        future.complete(false);
                        return;
                    }
                    if (stateUpdateProcessor != LifecycleState.RECOVERY){
                        LOG.error("Update Processor is not in recover state [state:{}]", stateUpdateProcessor.name());
                        future.complete(false);
                        return;
                    }
                    
                    LOG.info("LIFE Order update processor got into recovery state successfully");

        			engine.recover().whenComplete((state, cause) -> {
        				if (cause != null){
        					LOG.error("Not able to recover engine", cause);
        					future.complete(false);
        					return;
        				}
        				if (state != LifecycleState.RECOVERY){
        					LOG.error("Engine is not in recover state [state:{}]", state.name());
        					future.complete(false);
        					return;
        				}

        				LOG.info("LIFE Line handler engine got into recovery state successfully");
                        future.complete(true);
        				});
        			});
                });
			
			
			return future;			
		};		

		@Override
		public CompletableFuture<Boolean> onPendingStop() {
			if (!init){
				return CompletableFuture.completedFuture(false);
			}
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			// Stop disruptors first
			stopExecutor.execute(() -> {
				boolean result = stopChildDisruptors();
				if (!result){
					future.completeExceptionally(new Exception("Cannot stop child disruptors"));
					return;
				}
				engine.stop().whenComplete((state, cause) -> {
					if (cause != null){
						LOG.error("Not able to stop engine", cause);
						future.complete(false);
						return;
					}
					if (state != LifecycleState.STOPPED){
						LOG.error("Engine is not in stop state [state:{}]", state.name());
						future.complete(false);
						return;
					}
					
					orderExecutor.stop().whenComplete((stateOrderExecutor, causeOrderExecutor) -> {
						if (causeOrderExecutor != null){
							LOG.error("Not able to stop order executor", causeOrderExecutor);
							future.complete(false);
							return;
						}
						if (stateOrderExecutor != LifecycleState.STOPPED){
							LOG.error("Order Executor is not in stop state [state:{}]", stateOrderExecutor.name());
							future.complete(false);
							return;
						}
						
						updateProcessor.stop().whenComplete((stateUpdateProcessor, causeUpdateProcessor) -> {
							if (causeUpdateProcessor != null){
								LOG.error("Not able to stop update processor", causeUpdateProcessor);
								future.complete(false);
								return;
							}
							if (stateUpdateProcessor != LifecycleState.STOPPED){
								LOG.error("Update Processor is not in stop state [state:{}]", stateUpdateProcessor.name());
								future.complete(false);
								return;
							}
							future.complete(true);
						});
					});
				});
			});			
			return future;			
		};
	};

//	@Override
//	public CompletableFuture<LifecycleState> reset() {
//		// Reset state of all components
//		// 1) ThrottleTracker: no need
//		// 2) OrderContextManager: clear is enough
//		// 3) LineHandlerEnginer: reset() and wait for the operation to be done
//		// 4) Disruptor for OrderExecutor
//		// 6) Stop processing events in OrderExecutor
//		// 7) Stop processing events in OrderUpdateReceiver
//		
//		CompletableFuture<LifecycleState> future = new CompletableFuture<>();
//		contextManager.reset();
//		engine.reset().whenComplete(new BiConsumer<LifecycleState, Throwable>() {
//			@Override
//			public void accept(LifecycleState state, Throwable cause) {
//				if (cause != null){
//					future.completeExceptionally(cause);
//					return;
//				}
//				// Return our own state back to the caller
//				future.complete(controller.state());
//			}
//		});
//		return future;
//	}

	@Override
	public void lifecycleExceptionHandler(LifecycleExceptionHandler handler) {
		this.lifecycleExceptionHandler = handler;
	}
	
	public void apply(Command command){
		this.engine.apply(command);
	}
	
	public void send(OrderRequest request){
		orderExecutorQueue.offer(request);
	}

	@Override
	public LifecycleController controller() {
		return controller;
	}
	
	OrderExecutor orderExecutor(){
		return orderExecutor;
	}
	
	LineHandlerEngine engine(){
		return engine;
	}
	
	OrderUpdateProcessor updateProcessor(){
		return updateProcessor;
	}
	
	Disruptor<OrderUpdateEvent> disruptorForOrderUpdateProcessor(){
		return disruptorForOrderUpdateProcessor;
	}
	
	boolean isInit(){
		return init;
	}
	
	boolean isClear(){
	    return engine.isClear() && updateProcessor.isClear() && orderExecutor.isClear();
	}
	
	public CompletableFuture<Boolean> startRecovery(){
		// Request engine to start recovery
		// Complete future only when order update processor has finished processing all recovery		
	    engine.startRecovery().whenComplete((result, cause) -> {
	    	// Complete updateProcessor's future only when an error is encountered
	    	// If everything works as expected, OrderUpdateProcessor will complete
	    	// updateProcessor.endOfRecoveryFuture() upon receiving END_OF_RECOVERY message
	    	if (cause != null){
	    		updateProcessor.endOfRecoveryFuture().completeExceptionally(cause);
	    		return;
	    	}
	    	if (!result){
	    		updateProcessor.endOfRecoveryFuture().completeExceptionally(new IllegalStateException("Unable to complete engine recovery"));
	    		return;
	    	}
	    });
	    return updateProcessor.endOfRecoveryFuture();
	}
}
