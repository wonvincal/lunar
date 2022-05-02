package com.lunar.order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.lunar.config.LineHandlerConfig;
import com.lunar.core.SlidingWindowThrottleTracker;
import com.lunar.core.SystemClock;
import com.lunar.core.ThrottleTracker;
import com.lunar.core.TimerService;
import com.lunar.message.binary.Messenger;

public class LineHandlerBuilder {
	private LineHandlerConfig config;
	private OrderManagementContext orderManagementContext;
	private Messenger messenger;
	private boolean built = false;
	private int orderCompletionSinkId;
	private LineHandler lineHandler;
	private SystemClock systemClock;
	
	public static LineHandlerBuilder of(){
		return new LineHandlerBuilder();
	}
	
	LineHandlerBuilder(){}

	public LineHandlerBuilder orderCompletionSinkId(int orderCompletionSinkId){
		this.orderCompletionSinkId = orderCompletionSinkId;
		return this;
	}
	
	public LineHandlerBuilder lineHandlerConfig(LineHandlerConfig config){
		this.config = config;
		return this;
	}
	
	public LineHandlerBuilder messenger(Messenger messenger){
		this.messenger = messenger;
		return this;
	}
	
	public LineHandlerBuilder systemClock(SystemClock systemClock){
		this.systemClock = systemClock;
		return this;
	}

	public LineHandlerBuilder orderManagementContext(OrderManagementContext orderManagementContext){
		this.orderManagementContext = orderManagementContext;
		return this;
	}

	public LineHandler build() throws Exception{
		if (this.built){
			return lineHandler;
		}
		if (config == null){
			throw new IllegalStateException("LineHandlerConfig is required for building LineHandler");			
		}
		if (orderManagementContext == null){
			throw new IllegalStateException("OrderManagementContext is required for building LineHandler");
		}
		if (messenger == null){
			throw new IllegalStateException("Messenger is required for building LineHandler");
		}
		if (systemClock == null){
			throw new IllegalStateException("SystemClock is required for building LineHandler");
		}

		OrderContextManager ocm = OrderContextManager.createWithChildMessenger(config.numOutstandingOrders(), 
				orderManagementContext, 
				messenger, 
				OrderRequestCompletionSender.createWithChildMessenger(messenger, orderCompletionSinkId, orderManagementContext.performanceSubscribers()),
				ExposureUpdateHandler.createWithChildMessenger(messenger, orderCompletionSinkId));
		
		// Create an array of throttle trackers
		ThrottleTracker[] throttleTrackers;
		if (config.throttleArrangement().isPresent()){
			List<Integer> items = config.throttleArrangement().get();
			throttleTrackers = new ThrottleTracker[items.size()];
			for (int i = 0; i < items.size(); i++){
				throttleTrackers[i] = new SlidingWindowThrottleTracker(items.get(i), config.throttleDuration(), messenger.timerService());
			}
		}
		else {
			throttleTrackers = new ThrottleTracker[1];
			throttleTrackers[0] = new SlidingWindowThrottleTracker(config.throttle(), config.throttleDuration(), messenger.timerService());
		}
		OrderUpdateProcessor updateProcessor = OrderUpdateProcessor.of(config.name() + "-ord-upd-processor", messenger.timerService(), ocm, config.boundUpdateProcessorToCpu());
		LineHandlerEngine engine = buildEngine(config, messenger.timerService(), systemClock);
		OrderExecutor orderExecutor = OrderExecutor.of(config.name() + "-ord-exec", 
				throttleTrackers, 
				messenger.timerService(), 
				engine,
				OrderRequestCompletionSender.createWithChildMessenger(messenger, orderCompletionSinkId, orderManagementContext.performanceSubscribers()),
				config.boundExecutorToCpu(),
				config.maxNumBatchOrders(),
				config.ordExecQueueSize());
		
		lineHandler = LineHandler.of(config, messenger, orderExecutor, updateProcessor, engine);
		
		this.built = true;
		return lineHandler;
	}
	
	private static LineHandlerEngine buildEngine(LineHandlerConfig config, TimerService timerService, SystemClock systemClock) throws Exception{
		Method method;
		Object object = null;
		try {
			method = config.lineHandlerEngineClass().getMethod("of", LineHandlerConfig.class, TimerService.class, SystemClock.class);
			object = method.invoke(null, config, timerService, systemClock);
		} 
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw e;
		}
		return (LineHandlerEngine)object;

	}
}
