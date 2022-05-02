package com.lunar.order;

import java.util.concurrent.CompletableFuture;

import com.lunar.config.LineHandlerConfig;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.service.LifecycleController;

public class NullLineHandlerEngine implements LineHandlerEngine {
	LineHandlerConfig config;
	TimerService timerService;
	private final LifecycleController controller;
	private final LifecycleStateHook lifecycleStateChangeHandler = LifecycleStateHook.NULL_HANDLER;
	private final String name;

	public static LineHandlerEngine of(LineHandlerConfig config, TimerService timerService, SystemClock systemClock){
		return new NullLineHandlerEngine(config, timerService);
	}
	
	NullLineHandlerEngine(LineHandlerConfig config, TimerService timerService){
		this.config = config;
		this.name = config.name();
		this.timerService = timerService;
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateChangeHandler);
	}
	
	@Override
	public LifecycleController controller() {
		return controller;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void lifecycleExceptionHandler(LifecycleExceptionHandler handler) {
	}

	@Override
	public void sendOrderRequest(OrderRequest orderRequest) {
	}

	@Override
	public void exceptionHandler(LineHandlerEngineExceptionHandler handler) {
	}

	@Override
	public LineHandlerEngine init(OrderUpdateEventProducer producer) {
		return this;
	}

	@Override
	public int apply(Command command) {
		return 0;
	}

	@Override
	public boolean isClear() {
	    return true;
	}

    @Override
    public CompletableFuture<Boolean> startRecovery() {
        return CompletableFuture.completedFuture(true);
    }
    
    @Override
    public void printOrderExecInfo(int clientOrderId){}

    @Override
    public void printAllOrderExecInfo(){}

}
