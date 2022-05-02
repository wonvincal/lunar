package com.lunar.order;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.config.LineHandlerConfig;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.service.LifecycleController;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Support the following operations:
 * 1. NewOrderRequest -> OrderAccepted
 * 2. CancelOrderRequest -> OrderCancelled if order exists else OrderCancelRejected
 * @author wongca
 *
 */
public class SimpleLineHandlerEngine implements LineHandlerEngine {
	private static final Logger LOG = LogManager.getLogger(SimpleLineHandlerEngine.class);
	private final String name;
	private final int channelId = 1;
	private final AtomicLong channelSeq = new AtomicLong();

	private Consumer<OrderRequest> currentOrderRequestConsumer;
	private Consumer<OrderRequest> noopOrderRequestConsumer = (o) -> { LOG.warn("Noop order request consumer");};
	private Consumer<OrderRequest> activeOrderRequestConsumer;
	
	private UserControlledMatchingEngine engine;
	private boolean init =  false;
	private final TimerService timerService;
	
	// Lifecycle related fields
	private final LifecycleController controller;
	@SuppressWarnings("unused")
	private LifecycleExceptionHandler lifecycleExceptionHandler = LifecycleExceptionHandler.DEFAULT_HANDLER;
	private final LifecycleStateHook lifecycleStateChangeHandler = new LifecycleStateHook() {
	    @Override
	    public void onInit() {
	        currentOrderRequestConsumer = noopOrderRequestConsumer;
	    };
	    
		@Override
		public CompletableFuture<Boolean> onPendingReset() {
			LOG.info("Reset completed [name:{}]", name);
			engine.apply(Command.of(ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
			        ServiceConstant.NULL_CLIENT_MSG_KEY, 
			        CommandType.LINE_HANDLER_ACTION, 
			        Parameters.listOf(Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, 
			                LineHandlerActionType.RESET.value()))));
			resetState();
			return CompletableFuture.completedFuture(true);
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingActive() {
			if (init){
				return CompletableFuture.completedFuture(true);
			}
			LOG.error("LineHandlerEngine has not been init [name:{}]", name);
			return CompletableFuture.completedFuture(false);
		};
		
		@Override
		public void onWarmupEnter(){
			currentOrderRequestConsumer = activeOrderRequestConsumer;
		}
		
		@Override
		public void onActiveEnter() {
			currentOrderRequestConsumer = activeOrderRequestConsumer;
		};
		
		@Override
		public void onResetEnter() {
			currentOrderRequestConsumer = noopOrderRequestConsumer;
		};
	};
	
	// LineHandlerEngine related fields
	@SuppressWarnings("unused")
	private LineHandlerEngineExceptionHandler exceptionHandler = LineHandlerEngineExceptionHandler.NULL_HANDLER;
	
	public static SimpleLineHandlerEngine of(LineHandlerConfig config, TimerService timerService, SystemClock systemClock){
		return new SimpleLineHandlerEngine(config.name(), timerService);
	}
	
	public static SimpleLineHandlerEngine of(String name, TimerService timerService){
		return new SimpleLineHandlerEngine(name, timerService);
	}
	
	SimpleLineHandlerEngine(String name, TimerService timerService){
		this.name = name;
		this.timerService = timerService;
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateChangeHandler);
		this.engine = UserControlledMatchingEngine.of(this.name + "-matching-engine", timerService);
		this.activeOrderRequestConsumer = (o) -> { 
			engine.sendOrderRequest(o);
		};
	}
	
	@Override
	public String name() {
		return name;
	}

	@Override
	public void lifecycleExceptionHandler(LifecycleExceptionHandler handler) {
		this.lifecycleExceptionHandler = handler;
	}

	@Override
	public void sendOrderRequest(OrderRequest orderRequest) {
	    currentOrderRequestConsumer.accept(orderRequest);
	}

	@Override
	public void exceptionHandler(LineHandlerEngineExceptionHandler handler) {
		this.exceptionHandler = handler;
	}
	
	@Override
	public int apply(Command command) {
		boolean found = false;
		if (command.commandType() == CommandType.LINE_HANDLER_ACTION){
			if (command.parameters().isEmpty()){
				throw new IllegalArgumentException("Missing parameter");
			}
			for (Parameter parameter : command.parameters()){
				if (parameter.type() == ParameterType.LINE_HANDLER_ACTION_TYPE){
					found = true;
					engine.apply(command);
					if (LineHandlerActionType.get(parameter.valueLong().byteValue()) == LineHandlerActionType.RESET){
                        resetState();
					}
				}				
			}
		}
		if (!found){
			throw new IllegalArgumentException("Invalid parameters");
		}
		return 0;
	}

	@Override
	public LifecycleController controller() {
		return controller;
	}

	@Override
	public LineHandlerEngine init(OrderUpdateEventProducer producer) {
		LOG.info("Initialized line handler engine [name:{}]", this.name);
		engine.init(OrderUpdateEventProducerBridge.of(channelId, 
				channelSeq, producer, timerService));
		this.init = true;
		return this;
	}
	
	private void resetState(){
		channelSeq.set(0);
	}
	
	@Override
	public boolean isClear(){
	    return channelSeq.get() == 0 && engine.isClear();
	}

	Int2ObjectOpenHashMap<Order> orders(){
		return engine.orders();
	}

	SimpleLineHandlerEngine noopOrderRequestConsumer(Consumer<OrderRequest> handler){
		if (currentOrderRequestConsumer == this.noopOrderRequestConsumer){
			currentOrderRequestConsumer = handler;
		}		
		this.noopOrderRequestConsumer = handler;
		return this;
	}

    @Override
    public CompletableFuture<Boolean> startRecovery() {
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
        engine.endOfRecovery();
        return future;
    }
    @Override
    public void printOrderExecInfo(int clientOrderId){}
    @Override
    public void printAllOrderExecInfo(){}
}