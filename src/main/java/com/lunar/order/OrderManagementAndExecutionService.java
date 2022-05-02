package com.lunar.order;

import static org.apache.logging.log4j.util.Unbox.box;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.OrderManagementAndExecutionServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.core.Lifecycle.LifecycleExceptionHandler;
import com.lunar.core.LifecycleState;
import com.lunar.core.ServiceStatusTracker.ServiceStatusChangeHandler;
import com.lunar.core.SlidingWindowThrottleTracker;
import com.lunar.core.SubscriberList;
import com.lunar.core.ThrottleTracker;
import com.lunar.core.TimerService;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.Messenger;
import com.lunar.message.binary.NewCompositeOrderRequestDecoder;
import com.lunar.message.binary.NewOrderRequestDecoder;
import com.lunar.message.binary.RequestDecoder;
import com.lunar.message.io.sbe.AmendOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewCompositeOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.service.ServiceConstant;
import com.lunar.service.ServiceLifecycleAware;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Main thread is responsible for handling order requests.
 * A separate thread is responsible for handling order updates and managing order states
 * A separate thread is responsible for handling executions
 * @author wongca
 *
 */
public class OrderManagementAndExecutionService implements ServiceLifecycleAware {
	private static final Logger LOG = LogManager.getLogger(OrderManagementAndExecutionService.class);
	private static final int EXPECTED_NUM_UNDERLYINGS = 64;
	private final LunarService messageService;
	private final Messenger messenger;
	private final MessageSinkRefMgr refMgr;
	final String name;
	
	private final OrderManagementContext orderManagementContext;
	private final Int2IntOpenHashMap pendingCancelByOrdSid;
	private final Int2ObjectOpenHashMap<CompositeOrderAction> remainingActionsForCompositeOrderByClientKey;
	
	private final boolean avoidMultipleActionPerOrder;
	private final int numThrottleRequiredToProceedForBuy;
	private final Optional<Integer> numThrottlePerUnderlying;
	private AtomicInteger ordSidGenerator;
	private final LineHandler lineHandler;
	private final Messenger lineHandlerRelatedMessenger;
	private final Exposure exposure;

	private boolean isAdminUp = false;
	private boolean isWarmupUp = false;
	private boolean isPersistUp = false;
	private boolean isNsUp = false;
	private boolean isRefDataUp = false;
	private boolean isLineHandlerRecoveryCompleted = false;
	
    private final SubscriberList performanceSubscribers;
    
    private final ExecutorService lineHandlerRelatedExecutor;
	private int startOrderSidSequence;

	private final Optional<String> existingPositions;
	private final Long2ObjectOpenHashMap<ThrottleTracker> undTrackerByUndSecSid;

	static class CompositeOrderAction {
		private NewOrderRequest request;
		private boolean cancelSent;
		public static CompositeOrderAction of(NewOrderRequest request){
			CompositeOrderAction action = new CompositeOrderAction();
			action.request = request;
			action.cancelSent = false;
			return action;
		}
		NewOrderRequest request(){
			return request;
		}
		boolean cancelSent(){
			return cancelSent;
		}
		
	}
	
	/**
	 * A static method to create OCG line handler
	 * @param config
	 * @param messageService
	 * @return
	 */
	public static OrderManagementAndExecutionService of(ServiceConfig config, LunarService messageService){
		return new OrderManagementAndExecutionService(config, messageService, LineHandlerBuilder.of());
	}
	
	OrderManagementAndExecutionService(ServiceConfig config, LunarService messageService, LineHandlerBuilder lineHandlerBuilder){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.refMgr = this.messenger.referenceManager();
		
		OrderManagementAndExecutionServiceConfig specificConfig = null;
		if (config instanceof OrderManagementAndExecutionServiceConfig){
			specificConfig = (OrderManagementAndExecutionServiceConfig)config;
			this.orderManagementContext = OrderManagementContext.of(specificConfig.numOutstandingOrderRequests(), 
					specificConfig.expectedTradableSecurities(), 
					specificConfig.numOutstandingOrdersPerSecurity(),
					specificConfig.numChannels());
			this.orderManagementContext.addOrderUpdateSubscriber(this.messenger.self());
			
			this.exposure = this.orderManagementContext.exposure();
			if (specificConfig.initPurchasingPower().isPresent()){
				updateInitialPurchasingPowerInDollar(specificConfig.initPurchasingPower().get());
			}
			LOG.info("Initialize purchasing power [current:{}]", this.exposure.purchasingPower());
			this.avoidMultipleActionPerOrder = specificConfig.avoidMultiCancelOrAmend();
			this.pendingCancelByOrdSid = new Int2IntOpenHashMap(specificConfig.numOutstandingOrderRequests());

			this.remainingActionsForCompositeOrderByClientKey = new Int2ObjectOpenHashMap<CompositeOrderAction>(specificConfig.numOutstandingOrderRequests());
			try {
				this.lineHandler = lineHandlerBuilder
						.lineHandlerConfig(specificConfig.lineHandlerConfig())
						.messenger(messenger)
						.orderCompletionSinkId(messenger.self().sinkId())
						.orderManagementContext(orderManagementContext)
						.systemClock(messageService.systemClock())
						.build();
			}
			catch (Exception e){
				throw new IllegalStateException("Cannot create line handler", e);
			}
			this.lineHandlerRelatedMessenger = this.messenger.createChildMessenger();
			this.startOrderSidSequence = specificConfig.startOrderSidSequence();
			this.ordSidGenerator = new AtomicInteger(startOrderSidSequence);
			this.lineHandler.lifecycleExceptionHandler(lineHandlerExceptionHandler);
			this.lineHandlerRelatedExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory(this.name, this.name + "-self-messenger"));
			this.performanceSubscribers = this.orderManagementContext.performanceSubscribers();
			this.existingPositions = specificConfig.existingPositions();
			this.numThrottleRequiredToProceedForBuy = specificConfig.numThrottleRequiredToProceedForBuy().orElse(OrderRequest.NUM_THROTTLE_REQUIRED_TO_PROCEED);
			this.numThrottlePerUnderlying = specificConfig.numThrottlePerUnderlying();
			this.undTrackerByUndSecSid = new Long2ObjectOpenHashMap<>(EXPECTED_NUM_UNDERLYINGS);
		}
		else{
			throw new IllegalArgumentException("Service " + this.name + " expects a OrderManagementAndExecutionServiceConfig config");
		}
	}
	
	LineHandler lineHandler(){
		return this.lineHandler;
	}
	
	int peekOrdSid(){
		return this.ordSidGenerator.get();
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService, adminStatusChangeHandler);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.WarmupService, warmupServiceStatusChangeHandler);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.PersistService, persistServiceStatusChangeHandler);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.NotificationService, nsStatusChangeHandler);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService, refDataStatusChangeHandler);
		messenger.receiver().commandHandlerList().add(commandHandler);
		/*
		 * Initialize line handler
		 */
		lineHandler.init();
		
		return StateTransitionEvent.NULL;
	}
 
	@Override
	public StateTransitionEvent idleRecover() {
		// TODO need to design on recovery procedure
		// TODO need to find out the last used id and make sure that our sequence generates numbers after that
		this.ordSidGenerator = new AtomicInteger();
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent waitingForWarmupServicesEnter() {
		lineHandler.warmup().whenCompleteAsync((state, cause) -> { verifyLineHandlerState(LifecycleState.WARMUP, state, cause);}, lineHandlerRelatedExecutor);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForWarmupServicesExit() {
	}

	/**
	 * Warmup classes that can't be warmup by WarmupService
	 */
	private void warmupClasses(){
		TimerService timerService = messenger.timerService();
		warmupThrottleTracker(1, timerService);
		warmupThrottleTracker(6, timerService);
	}
	
	private void warmupThrottleTracker(int numThrottles, TimerService timerService){
		ThrottleTracker tracker = new SlidingWindowThrottleTracker(numThrottles, Duration.ofNanos(1000), timerService);
		NewOrderRequest request = NewOrderRequest.ofWithDefaults(-1, this.messenger.self(), 0, OrderType.LIMIT_ORDER, 0, Side.BUY, TimeInForce.DAY, BooleanType.TRUE, 0, 0, Long.MAX_VALUE, true, -1);
		int numSent = 0;
		int numRetry = 0;
		for (int i = 0; i < 150_000; i++){
			if (!tracker.getThrottle()){
				if (request.retry()){
					long nextAvailSystemNs = tracker.nextAvailNs();
					long timeoutInSystemNs = timerService.nanoTime() + (request.timeoutAtNanoOfDay() - timerService.toNanoOfDay());
					if (timeoutInSystemNs >= nextAvailSystemNs){
						do {}
						while (tracker.getThrottle());
					}
					numRetry++;
				}
			}
			if (!tracker.getThrottle(request.numThrottleRequiredToProceed())){
				if (request.retry()){
					long nextAvailSystemNs = tracker.nextAvailNs();
					long timeoutInSystemNs = timerService.nanoTime() + (request.timeoutAtNanoOfDay() - timerService.toNanoOfDay());
					if (timeoutInSystemNs >= nextAvailSystemNs){
						do {}
						while (tracker.getThrottle());
					}
					numRetry++;
				}
			}
			numSent++;
		}
		LOG.info("Warmup initial phase completed [numSent:{}, numRetry:{}]", numSent, numRetry);		
	}
	
	@Override
	public StateTransitionEvent warmupEnter() {
		warmupClasses();
		
		registerHandlers();
		refMgr.perf().disablePublishToNullSinkWarning();
		performanceSubscribers.add(refMgr.perf());
		
		// Send a command to warmup service to start warming up
		final long timeoutWarmup = 1_000_000_000l * 180;
		CompletableFuture<Command> commandFuture = messenger.sendCommand(refMgr.warmup(), Command.of(messenger.self().sinkId(), 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.WARMUP,
				Parameters.listOf(Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value())),
				timeoutWarmup));
		commandFuture.whenComplete((command, throwable) -> {
			if (messageService.state() == States.WARMUP){
				if (command.ackType() == CommandAckType.OK){
					LOG.info("Received ack from warmup command [{}]", command);
				}
				else {
					if (throwable != null){
						LOG.warn("Caught a throwable from warmup command [" + command + "]", throwable);
					}
					else {
						LOG.warn("Received nack from warmup command [{}]", command);							
					}
				}
				messageService.stateEvent(StateTransitionEvent.WARMUP_COMPLETE);
			}
			else {
				LOG.warn("Received and ignored response from warmup command because service is no longer in WARMUP state [{}, {}]", command, throwable);
			}
		});
		return StateTransitionEvent.NULL;
	};
	
	@Override
	public void warmupExit() {
		performanceSubscribers.clear();
		refMgr.perf().enablePublishToNullSinkWarning();
	}
	
	@Override
	public StateTransitionEvent resetEnter() {
	    resetState();
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public StateTransitionEvent waitingForServicesEnter() {
		if (isAdminUp && isPersistUp && isNsUp && isLineHandlerUp()){
			LOG.info("Enter waitingForServicesEnter - proceed straight to READY");
			return StateTransitionEvent.READY;
		}
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForServicesExit() {
	}

	private boolean isLineHandlerUp(){
		switch (lineHandler.state()){
		case INIT:
		case ACTIVE:
		case RESET:
		case WARMUP:
		case RECOVERY:
			return true;
		default:
			return false;
		}
		
	}
	
	private void transitionBetweenStates(){
		if (messageService.state() == States.IDLE){
			return;
		}
		
		LOG.debug("Dependent states [current:{}, admin:{}, warmup:{}, persist:{}, ns:{}, refData:{}, lineHandler:{}]",
				messageService.state(),
				isAdminUp, 
				isWarmupUp, 
				isPersistUp, 
				isNsUp, 
				isRefDataUp,
				lineHandler.state());
		if (messageService.state() == States.WAITING_FOR_WARMUP_SERVICES){
			if (isAdminUp && isWarmupUp && isNsUp && isRefDataUp && lineHandler.state() == LifecycleState.WARMUP){
				messageService.stateEvent(StateTransitionEvent.WARMUP);
			}
			return;
		}
		
		if (messageService.state() == States.WARMUP){
			// Go back to WAITING_FOR_WARMUP_SERVICES if any one of the services is not available
			if (!(isAdminUp && isWarmupUp && isNsUp && isRefDataUp && lineHandler.state() == LifecycleState.WARMUP)){
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}
			return;
		}
		
		if (messageService.state() == States.WAITING_FOR_SERVICES){
			if (isAdminUp && isPersistUp && isNsUp && isRefDataUp && isLineHandlerUp()){
				messageService.stateEvent(StateTransitionEvent.READY);
			}
			return;
		}

		// When we are in READY and line handler is UP, we want to transition to recover state to begin recovering
		if (messageService.state() == States.READY){
			if (isAdminUp && isPersistUp && isNsUp && isRefDataUp && isLineHandlerUp()){
				messageService.stateEvent(StateTransitionEvent.RECOVER);
			}
			return;
		}
		
		if (messageService.state() == States.RESET){
			if (isAdminUp && isNsUp && isPersistUp && isRefDataUp && lineHandler.state() == LifecycleState.RESET){
				messageService.stateEvent(StateTransitionEvent.RESET_COMPLETE);
			}
			return;
		}

		if (messageService.state() == States.RECOVERY){
			if (isAdminUp && isNsUp && isRefDataUp && isPersistUp && isLineHandlerUp()){
				if (isLineHandlerRecoveryCompleted){
					messageService.stateEvent(StateTransitionEvent.ACTIVATE);
					return;
				}
				else {
					LOG.error("Line handler recovery did not completed successfully");
					return;
				}
			}
			if (!isAdminUp || !isPersistUp || !isLineHandlerUp() || !isRefDataUp){
				LOG.warn("Dependent service is down.  Move to wait state [isAdminUp:{}, isPersistUp:{}, isRefDataUp:{}, lineHandler:{}]",
						isAdminUp,
						isPersistUp,
						isRefDataUp,
						lineHandler.state().name());
				// Something is really wrong, move back to WAIT state
				messageService.stateEvent(StateTransitionEvent.WAIT);
				return;			
			}
		}
		
		if (messageService.state() == States.ACTIVE){
			if (!isAdminUp || !isPersistUp || !isRefDataUp || lineHandler.state() != LifecycleState.ACTIVE){
				// Something is really wrong, move back to WAIT state
				LOG.warn("Dependent service is down.  Move to wait state [isAdminUp:{}, isPersistUp:{}, isRefDataUp:{}, lineHandler:{}]",
						isAdminUp,
						isPersistUp,
						isRefDataUp,
						lineHandler.state().name());
				messageService.stateEvent(StateTransitionEvent.WAIT);
			}
			return;
		}
	}
	
	Exposure exposure(){
		return this.orderManagementContext.exposure();
	}
	
	OrderManagementContext orderManagementContext(){
		return this.orderManagementContext;
	}
	
	private final ServiceStatusChangeHandler warmupServiceStatusChangeHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus status) {
			isWarmupUp = (status.statusType() == ServiceStatusType.UP);
			transitionBetweenStates();		
		}
	};
	
	private final ServiceStatusChangeHandler persistServiceStatusChangeHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus status) {
			isPersistUp = (status.statusType() == ServiceStatusType.UP);
			transitionBetweenStates();		
		}
	};

	private final ServiceStatusChangeHandler adminStatusChangeHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus status) {
			isAdminUp = (status.statusType() == ServiceStatusType.UP);
			transitionBetweenStates();		
		}
	};
	
	private final ServiceStatusChangeHandler nsStatusChangeHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus status) {
			isNsUp = (status.statusType() == ServiceStatusType.UP);
			transitionBetweenStates();
		}
	};

	private final ServiceStatusChangeHandler refDataStatusChangeHandler = new ServiceStatusChangeHandler() {
		
		@Override
		public void handle(ServiceStatus status) {
			isRefDataUp = (status.statusType() == ServiceStatusType.UP);
			transitionBetweenStates();
		}
	};

	@Override
	public StateTransitionEvent readyEnter() {
		if (isAdminUp && isPersistUp && isNsUp && isRefDataUp && isLineHandlerUp()){
			// Setting up throttle per underlying after warming up
			if (numThrottlePerUnderlying.isPresent()){
				messenger.receiver().securityHandlerList().add(securityHandler);
				// Get last of securities from RefData
				CompletableFuture<Request> request = messenger.sendRequest(refMgr.rds(),
						RequestType.GET, 
						Parameters.listOf(Parameter.of(TemplateType.SECURITY),
								Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)));
				request.whenComplete(new BiConsumer<Request, Throwable>() {
					@Override
					public void accept(Request request, Throwable throwable) {
						// Move to next state no matter success or not
						if (throwable != null){
							LOG.error("Caught throwable for request to get all securities", throwable);
							return;
						}
						if (!request.resultType().equals(ResultType.OK)){
							LOG.error("Received failure result for request to get all securities [{}]", request.toString());
							return;
						}
						messageService.stateEvent(StateTransitionEvent.RECOVER);
					}
				});
			}
			else {
				return StateTransitionEvent.RECOVER;
			}
		}
		return StateTransitionEvent.NULL;
	}
	
    private final Handler<SecuritySbeDecoder> securityHandler = new Handler<SecuritySbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder security) {
			if (security.isAlgo() != BooleanType.TRUE || 
				(security.securityType() != SecurityType.WARRANT && security.securityType() != SecurityType.CBBC) || 
				(security.undSid() == Security.INVALID_SECURITY_SID)){
				return;
			}
			long secSid = security.sid();
			SecurityLevelInfo securiytLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
			if (!securiytLevelInfo.hasThrottleTracker()){
				long undSecSid = security.undSid();
				ThrottleTracker throttleTracker = undTrackerByUndSecSid.get(undSecSid);
				if (throttleTracker != undTrackerByUndSecSid.defaultReturnValue()){
					securiytLevelInfo.throttleTracker(throttleTracker);
				}
				else {
					ThrottleTracker tracker = new SlidingWindowThrottleTracker(numThrottlePerUnderlying.get(), Duration.ofSeconds(1), messenger.timerService());
					securiytLevelInfo.throttleTracker(tracker);
					undTrackerByUndSecSid.put(undSecSid, tracker);
					LOG.info("Created underlying throttle tracker [undSecSid:{}, numThrottles:{}]", undSecSid, numThrottlePerUnderlying.get());
				}
			}
		}
    };
    
	/**
	 * If line handler state is as expected, send an EVALUATE_STATE event to itself
	 * @param expected
	 * @param state
	 * @param cause
	 */
	private void verifyLineHandlerState(LifecycleState expected, LifecycleState state, Throwable cause){
		if (cause != null){
			LOG.error("Cannot move line handler to state [id:" + lineHandler.id() + ", name: " + lineHandler.name() + ", expected: " + expected.name() + "]", cause);
			return;
		}
		if (state != expected){
			LOG.error("Unexpected state in line handler [id:{}, name:{}, expectedState:{}, currentState:{}]", lineHandler.id(), lineHandler.name(), expected.name(), state.name()); 
			return;
		}
		try {
			LOG.info("Sent command to request evaluate state [selfSinkId:{}]", lineHandlerRelatedMessenger.self().sinkId());
			lineHandlerRelatedMessenger.commandSender().sendCommand(lineHandlerRelatedMessenger.self(), 
					Command.of(lineHandlerRelatedMessenger.self().sinkId(), 
							lineHandlerRelatedMessenger.getNextClientKeyAndIncrement(), 
							CommandType.EVALUATE_STATE));
		}
		catch (Exception e){
			LOG.error("Caught exception", e);
		}
	}
	
	@Override
	public void readyExit() {
	}

	@Override
	public StateTransitionEvent recoveryEnter() {
		orderManagementContext.addOrderUpdateSubscriber(this.messenger.self());
		orderManagementContext.addOrderUpdateSubscriber(refMgr.persi());
		registerHandlers();

		// Get OMES ready for recovery - no changes
		lineHandler.recover().whenCompleteAsync((state, cause) -> {

			if (cause != null){
				LOG.error("Unable to recover line handler", cause);
				return;
			}

			if (state != LifecycleState.RECOVERY){
				LOG.error("Unable to transition line handler to RECOVERY state [state:{}]", state.name());
				return;
			}

			// Verify state again when recover is done
			LOG.info("Invoke line handler recovery");
			lineHandler.startRecovery().whenCompleteAsync((lhState, lhCause) -> {
				isLineHandlerRecoveryCompleted = true;
				
				LOG.info("Line handler recovery completed successfully");
				verifyLineHandlerState(LifecycleState.RECOVERY, state, cause);
			}, lineHandlerRelatedExecutor);
		}, lineHandlerRelatedExecutor);
		return StateTransitionEvent.NULL;
	}
	
	@Override
	public void recoveryExit() {
	    long time = orderManagementContext.volatileExposureAfterRecoveryUpdatedTime();
	    ordSidGenerator.set(Math.max(startOrderSidSequence, orderManagementContext.latestOrderSid() + 1));
	    LOG.info("Recovery completed [time:{}, exposure:{}, startOrderSid:{}]", time, exposure, ordSidGenerator.get());
	    loadExistingPositions(this.existingPositions, this.orderManagementContext);
	    orderManagementContext.logSecurityPosition();
	}

	public static void loadExistingPositions(Optional<String> existingPositions, OrderManagementContext context){
		if (!existingPositions.isPresent()){
			return;
		}
		try {
			StringTokenizer pairs = new StringTokenizer(existingPositions.get(), ";");
			while (pairs.hasMoreElements()){
				String pair = pairs.nextElement().toString();
				String[] items = pair.split(",");
				long secSid = Long.parseLong(items[0]);
				int quantity = Integer.parseInt(items[1]);
				context.securiytLevelInfo(secSid).validationOrderBook().addExistingPosition(quantity);
			}
			LOG.info("Loaded existing positions");
		}
		catch (Exception e){
			LOG.error("Unable to apply existing positions", e);
		}
	}
	
	@Override
	public StateTransitionEvent activeEnter() {
		LOG.info("Enter activeEnter [name:{}]", name);
		// TODO to be fixed later
		lineHandler.active();
		orderManagementContext.addOrderUpdateSubscriber(this.messenger.self());
		orderManagementContext.addOrderUpdateSubscriber(refMgr.persi());
		registerHandlers();		
		return StateTransitionEvent.NULL;
	}
	
	private void registerHandlers(){
		MessageReceiver receiver = messenger.receiver();
		if (this.avoidMultipleActionPerOrder){
			receiver.cancelOrderRequestHandlerList().add(cancelOrderRequestAvoidMultiCancelHandler);
			receiver.amendOrderRequestHandlerList().add(amendOrderRequestAvoidMultiAmendHandler);
			receiver.orderCancelledHandlerList().add(orderCancelledAvoidMultiCancelHandler);
			receiver.orderCancelRejectedHandlerList().add(orderCancelRejectedAvoidMultiCancelHandler);
			receiver.orderCancelRejectedWithOrderInfoHandlerList().add(orderCancelRejectedWithOrderInfoAvoidMultiCancelHandler);
		}
		else{
			receiver.cancelOrderRequestHandlerList().add(cancelOrderRequestHandler);
			receiver.amendOrderRequestHandlerList().add(amendOrderRequestHandler);
			receiver.orderCancelledHandlerList().add(orderCancelledHandler);
			receiver.orderCancelRejectedHandlerList().add(orderCancelRejectedHandler);
			receiver.orderCancelRejectedWithOrderInfoHandlerList().add(orderCancelRejectedWithOrderInfoHandler);
		}
		
		receiver.newOrderRequestHandlerList().add(newOrderRequestHandler);
		receiver.newCompositeOrderRequestHandlerList().add(newCompositeOrderRequestHandler);
		receiver.orderRequestCompletionHandlerList().add(orderRequestCompletionHandler);
		receiver.orderAcceptedHandlerList().add(orderAcceptedHandler);
		receiver.orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandler);
		receiver.orderAmendedHandlerList().add(orderAmendedHandler);
		receiver.orderRejectedHandlerList().add(orderRejectedHandler);
		receiver.orderRejectedWithOrderInfoHandlerList().add(orderRejectedWithOrderInfoHandler);
		receiver.orderExpiredHandlerList().add(orderExpiredHandler);
		receiver.orderExpiredWithOrderInfoHandlerList().add(orderExpiredWithOrderInfoHandler);
		receiver.orderAmendRejectedHandlerList().add(orderAmendRejectedHandler);
		receiver.tradeCreatedHandlerList().add(tradeCreatedHandler);
		receiver.tradeCreatedWithOrderInfoHandlerList().add(tradeCreatedWithOrderInfoHandler);
		receiver.tradeCancelledHandlerList().add(tradeCancelledHandler);
		receiver.requestHandlerList().add(requestHandler);
		// No need to handle CancelRejected, because the original cancel won't impact anything
	}

	@Override
	public void activeExit() {
		unregisterHandlers();
	}

	private void unregisterHandlers(){
		MessageReceiver receiver = messenger.receiver();
		if (this.avoidMultipleActionPerOrder){
			receiver.cancelOrderRequestHandlerList().remove(cancelOrderRequestAvoidMultiCancelHandler);
			receiver.amendOrderRequestHandlerList().remove(amendOrderRequestAvoidMultiAmendHandler);			
			receiver.orderCancelledHandlerList().remove(orderCancelledAvoidMultiCancelHandler);
			receiver.orderCancelRejectedHandlerList().remove(orderCancelRejectedAvoidMultiCancelHandler);
			receiver.orderCancelRejectedWithOrderInfoHandlerList().remove(orderCancelRejectedWithOrderInfoAvoidMultiCancelHandler);
		}
		else{
			receiver.cancelOrderRequestHandlerList().remove(cancelOrderRequestHandler);
			receiver.amendOrderRequestHandlerList().remove(amendOrderRequestHandler);
			receiver.orderCancelledHandlerList().remove(orderCancelledHandler);
			receiver.orderCancelRejectedHandlerList().remove(orderCancelRejectedHandler);
			receiver.orderCancelRejectedWithOrderInfoHandlerList().remove(orderCancelRejectedWithOrderInfoHandler);
		}

		receiver.newOrderRequestHandlerList().remove(newOrderRequestHandler);
		receiver.newCompositeOrderRequestHandlerList().remove(newCompositeOrderRequestHandler);
		receiver.orderRequestCompletionHandlerList().remove(orderRequestCompletionHandler);
		receiver.orderAcceptedHandlerList().remove(orderAcceptedHandler);
		receiver.orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandler);
		receiver.orderAmendedHandlerList().remove(orderAmendedHandler);
		receiver.orderRejectedHandlerList().remove(orderRejectedHandler);
		receiver.orderRejectedWithOrderInfoHandlerList().remove(orderRejectedWithOrderInfoHandler);
		receiver.orderExpiredHandlerList().remove(orderExpiredHandler);
		receiver.orderExpiredWithOrderInfoHandlerList().remove(orderExpiredWithOrderInfoHandler);
		receiver.orderAmendRejectedHandlerList().remove(orderAmendRejectedHandler);
		receiver.tradeCreatedHandlerList().remove(tradeCreatedHandler);
		receiver.tradeCreatedWithOrderInfoHandlerList().remove(tradeCreatedWithOrderInfoHandler);
		receiver.tradeCancelledHandlerList().remove(tradeCancelledHandler);
		receiver.requestHandlerList().remove(requestHandler);
	}
	
	@Override
	public StateTransitionEvent stopEnter() {
		lineHandler.stop().whenCompleteAsync((state, cause) -> { verifyLineHandlerState(LifecycleState.STOPPED, state, cause);}, lineHandlerRelatedExecutor);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stopExit() {
	}

	@Override
	public StateTransitionEvent stoppedEnter() {
		LOG.info("{} stopped", name);
		lineHandler.stop();
		messenger.receiver().commandHandlerList().remove(commandHandler);
		return StateTransitionEvent.NULL;
	}

	@Override
	public void stoppedExit() {
	}

	private OrderRejectType validate(NewCompositeOrderRequestSbeDecoder codec){
		return OrderRejectType.VALID_AND_NOT_REJECT;
	}

	private final Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder command) {
			int senderSinkId = header.senderSinkId();
			try {
				LOG.info("Received command [clientKey:{}, commandType:{}]", command.clientKey(), command.commandType().name());
				if (command.commandType() == CommandType.EVALUATE_STATE){
					transitionBetweenStates();
				}
				else if (command.commandType() == CommandType.LINE_HANDLER_ACTION ||
					command.commandType() == CommandType.PRINT_ALL_ORDER_INFO_IN_LOG ||
					command.commandType() == CommandType.PRINT_ORDER_INFO_IN_LOG){
					lineHandler.apply(Command.of(senderSinkId, command, messenger.stringBuffer()));
				}
				else if (command.commandType() == CommandType.RESET){
					// Move line handler to reset state, then back to active
					if (messageService.state() == States.ACTIVE){
						messageService.stateEvent(StateTransitionEvent.RESET);
					}
					else {
						LOG.warn("Cannot reset service when it is not in active state [name:{}, state:{}]", name, messageService.state());
						messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.FAILED, command.commandType());
						return;						
					}
				}
				else {
					messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.NOT_SUPPORTED, command.commandType());
					return;
				}
				messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.OK, command.commandType());
			}
			catch (Exception e){
				LOG.error("Caught exception when handling command [clientKey:{}, commandType:{}]", command.clientKey(), command.commandType(), e);
				messenger.sendCommandAck(senderSinkId, command.clientKey(), CommandAckType.FAILED, command.commandType());
			}
		}
	};
	
	private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder request) {
			byte senderSinkId = header.senderSinkId();
            LOG.info("Received request [senderSinkId:{}, {}]", senderSinkId, RequestDecoder.decodeToString(request, messenger.stringByteBuffer()));
			final ImmutableListMultimap<ParameterType, Parameter> parameters;
			try {
				parameters = RequestDecoder.generateParameterMap(messenger.stringBuffer(), request);
			} 
			catch (UnsupportedEncodingException e) {
				LOG.error("Caught encoding exception when processing request [clientKey:" + request.clientKey() + "]", e);
				messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);
				return;
			}
			
			if (request.requestType() == RequestType.SUBSCRIBE){
				LOG.debug("Received subscribe request [senderSinkId:{}]", senderSinkId);
				MessageSinkRef subscriberSink = messenger.referenceManager().get(senderSinkId);

				Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);
				if (templateType.isPresent() && templateType.get() == TemplateType.GENERIC_TRACKER){
					performanceSubscribers.add(subscriberSink);
                    messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
                    return;					
				}
				
				// subscribe order update and trade update
				Optional<DataType> dataType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
				if (!dataType.isPresent()){
					LOG.error("Illegal argument, expect DATA_TYPE in subscription request [clientKey:" + request.clientKey() + "]");
					messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
					return;
				}
				
				boolean subscribeOrderUpdate = false;
				switch (dataType.get()){
				case ALL_ORDER_AND_TRADE_UPDATE:
					subscribeOrderUpdate = true;
					break;
				default:
					messenger.sendResponseForRequest(senderSinkId, request, ResultType.ILLEGAL_ARGUMENT_OPERATION);
					return;
				}

				if (subscribeOrderUpdate){
					if (addOrderUpdateSubscriber(subscriberSink)){
						messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);					
					}
					else{
						messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);					
					}
				}
			}
			else if (request.requestType() == RequestType.UNSUBSCRIBE) {
	            LOG.debug("Received unsubscribe request [senderSinkId:{}]", senderSinkId);
	            MessageSinkRef subscriberSink = messenger.referenceManager().get(senderSinkId);
			    
	            ImmutableList<Parameter> parameterList = parameters.get(ParameterType.TEMPLATE_TYPE);
	            if (!parameterList.isEmpty()) {
	                if (parameterList.get(0).valueLong() == TemplateType.GENERIC_TRACKER.value()) {
	                    performanceSubscribers.remove(subscriberSink);
	                    messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
	                    return;
	                }
	            }		    
			}
			else if (request.requestType() == RequestType.UPDATE){
				ImmutableList<Parameter> parameterList = parameters.get(ParameterType.PURCHASING_POWER);
				if (!parameterList.isEmpty()){
					long value = parameterList.get(0).valueLong();
					if (updateInitialPurchasingPowerInDollar(value)){
						messenger.sendResponseForRequest(senderSinkId, request, ResultType.OK);
					}
					else{
						messenger.sendResponseForRequest(senderSinkId, request, ResultType.FAILED);						
					}
				}

				Optional<DataType> dataTypeParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.DATA_TYPE, DataType.class);
				if (dataTypeParam.isPresent() && dataTypeParam.get() == DataType.VALIDATION_BOOK){
					Optional<Side> sideParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SIDE, Side.class);
					Optional<Long> priceParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.PRICE);
					Optional<Long> secSidParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.SECURITY_SID);
					Optional<Long> qtyParam = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.QUANTITY);
					if (sideParam.isPresent() && priceParam.isPresent() && secSidParam.isPresent() && qtyParam.isPresent()){
						long secSid = secSidParam.get();
						SecurityLevelInfo securiytLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
						if (sideParam.get() == Side.BUY){
							securiytLevelInfo.validationOrderBook().newBuyOrder(priceParam.get().intValue());							
						}
						else {
							securiytLevelInfo.validationOrderBook().newSellOrder(priceParam.get().intValue(), qtyParam.get().intValue());
						}
					}
				}
			}
		}
	};
	
	private boolean addOrderUpdateSubscriber(MessageSinkRef subscriber){
		LOG.info("Added subscriber to order update | subscriber:{}", subscriber);
		this.orderManagementContext.addOrderUpdateSubscriber(subscriber);
		return true;
	}
	
	/**
	 * This thread:
	 * Exposure: apply difference in quantity
	 * Cross: no change
	 * 
	 * Order Update Thread:
	 * Position: no change
	 * 
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param accepted
	 */
	private final Handler<OrderAcceptedSbeDecoder> orderAcceptedHandler = new Handler<OrderAcceptedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder codec) {
			// No impact to exposure, position and crossing
			// therefore no need to get order book
		}
	};

	private final Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandler = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
			// No impact to exposure, position and crossing
			// therefore no need to get order book
		}		
	};

	/**
	 * This thread:
	 * Exposure: apply difference in quantity
	 * Cross: no change
	 * 
	 * Order Update Thread:
	 * Position: no change
	 * 
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param amended
	 */
	private final Handler<OrderAmendedSbeDecoder> orderAmendedHandler = new Handler<OrderAmendedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendedSbeDecoder codec) {
			// 6.6.8.1 Message Flow - Amend Request - No Price Change + No Quantity Increase
			// Exposure: remains unchanged to be more conservative
			// Position: no change 
			// Cross: no change to number of order
			//
			// Two outcomes:
			// 1) Amend Reject
			//    Todo: No exposure, position and crossing change
			// 2) Amended
			//    Todo: Change exposure from previous quantity to current quantity
			//
			// 6.6.8.2 Message Flow - Amend Request - Change Price / Increase Quantity
			// On Amend, 
			// a. reverse exposure and cross for original request
			// b. apply exposure and cross for amend request
			//
			// Exposure: if increase in quantity, change exposure to reflect extra quantity
			// Position: no change
			// Cross: if change in price, remove from original price and add to new price
			//
			// Three outcomes:
			// 1) Amend Reject
			//    a. reverse exposure and cross for new amend
			//    b. apply exposure and cross for original request
			// 2) Amend Reject + Cancel of Original Request
			//    a. reverse exposure and cross for new amend
			//    b. apply exposure and cross for original request
			//    c. reverse exposure and cross for original request
			// 2.5) Cancel of Original Request + Amend Reject 
			//    a. reverse exposure and cross for original request
			//    b. reverse exposure and cross for new amend
			//    
			// 3) Amended
			//    Todo: Change exposure
			// this.orderManagementContext.orderBook(amended.secSid()).onAmended(amended);
			throw new UnsupportedOperationException("Order amend is currently not supported");
		}
	};
	
	/**
	 * This thread:
	 * Exposure: remove original quantity
	 * Cross: decrement order count
	 * 
	 * Order Update Thread:
	 * Position: no change
	 * 
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param rejected
	 */
	private final Handler<OrderRejectedSbeDecoder> orderRejectedHandler = new Handler<OrderRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder rejected) {
			handleOrderRejected(rejected.orderSid(), rejected.secSid(), rejected.cumulativeQty(), rejected.side(), rejected.rejectType());
		}
	};

	private final Handler<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandler = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder rejected) {
			handleOrderRejected(rejected.orderSid(), rejected.secSid(), rejected.cumulativeQty(), rejected.side(), rejected.rejectType());
		}
	};
	
	private void handleOrderRejected(int orderSid, long secSid, int cumulativeQty, Side side, OrderRejectType rejectType){
		// Remove this request from map
		NewOrderRequest request = this.orderManagementContext.removeOrderRequest(orderSid).asNewOrderRequest();
		LOG.trace("Removed order request from map after receiving order rejected [orderSid:{}, secSid:{}, side:{}, rejectType:{}]", 
				box(orderSid),
				box(secSid),
				side.name(),
				rejectType.name());
		// Order Reject won't have cumulativeQty and leavesQty
		// Reverse exposure/position/crossing impact
		int price = request.limitPrice();
		ValidationOrderBook orderBook = request.securityLevelInfo().validationOrderBook();
		int resetQuantity = request.quantity() - cumulativeQty;
		if (request.side() == Side.BUY){
			// Reverse exposure
			exposure.incPurchasingPower(price * resetQuantity);
			// position/crossing impact
			orderBook.buyOrderRejected(price);
		}
		else{
			// Reverse position/crossing impact
			orderBook.sellOrderRejected(price, resetQuantity);
		}
	}

	/**
	 * This thread:
	 * Exposure: remove original quantity
	 * Cross: decrement order count
	 * 
	 * Order Update Thread:
	 * Position: no change
	 * 
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param cancelled
	 */
	private final Handler<OrderCancelledSbeDecoder> orderCancelledHandler = new Handler<OrderCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder cancelled) {
//			LOG.debug("Received orderCancelledHandler [orderSid:{}, origOrderSid:{}]", cancelled.orderSid(), cancelled.origOrderSid());

			// OrderCancelled can be received in the following scenarios:
			// 1) Cancel Order Request
			//    Todo: remove previous exposure and order
			// 2) Cancel on behalf
			//    Todo: remove previous exposure and order
			// 3) Cancel following an Amend
			//    Todo: remove previous exposure and order

			// Remove the original new request from map
			// The current assumption is that the original request must exist at this point
			orderManagementContext.origOrdSidToCancelOrderSid().remove(cancelled.origOrderSid());
			OrderRequest removedOrigRequest = orderManagementContext.removeOrderRequest(cancelled.origOrderSid());
			if (removedOrigRequest != null){
				NewOrderRequest request = removedOrigRequest.asNewOrderRequest();
				int price = request.limitPrice();
				int resetQuantity = request.quantity() - cancelled.cumulativeQty();
				ValidationOrderBook orderBook = request.securityLevelInfo().validationOrderBook();
				if (cancelled.side() == Side.BUY){
					// Reverse exposure
					exposure.incPurchasingPower(price * resetQuantity);
					// position/crossing impact
					orderBook.buyOrderCancelled(price);
				}
				else{
					// Reverse position/crossing impact
					orderBook.sellOrderCancelled(price, resetQuantity);
				}				
			}
			else {
				LOG.error("Received cancelled for an order that does not exist [origOrderSid:{}]", cancelled.origOrderSid());
				messenger.trySendEventWithOneValue(EventCategory.EXECUTION, 
						EventLevel.CRITICAL, 
						messenger.self().sinkId(), 
						messenger.timerService().toNanoOfDay(), 
						"Received cancelled for a non-exist order", 
						EventValueType.ORDER_SID,
						cancelled.origOrderSid());
			}

			// Remove this cancel request from map
			if (cancelled.orderSid() != cancelled.origOrderSid()){
				orderManagementContext.removeOrderRequest(cancelled.orderSid());
			}
		}
	};
	
	private final Handler<OrderCancelRejectedSbeDecoder> orderCancelRejectedHandler = new Handler<OrderCancelRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder cancelled) {
			orderManagementContext.removeOrderRequest(cancelled.orderSid());
			orderManagementContext.origOrdSidToCancelOrderSid().remove(cancelled.origOrderSid());
		}
	};
	
	private final Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> orderCancelRejectedWithOrderInfoHandler = new Handler<OrderCancelRejectedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedWithOrderInfoSbeDecoder cancelled) {
			orderManagementContext.removeOrderRequest(cancelled.orderSid());
			orderManagementContext.origOrdSidToCancelOrderSid().remove(cancelled.origOrderSid());
		}
	};
	
	private final Handler<OrderCancelledSbeDecoder> orderCancelledAvoidMultiCancelHandler = new Handler<OrderCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder cancelled) {
//			LOG.debug("Received orderCancelledAvoidMultiCancelHandler [orderSid:{}, origOrderSid:{}]", cancelled.orderSid(), cancelled.origOrderSid());
			// OrderCancelled can be received in the following scenarios:
			// 1) Cancel Order Request
			//    Todo: remove previous exposure and order
			// 2) Cancel on behalf
			//    Todo: remove previous exposure and order
			// 3) Cancel following an Amend
			//    Todo: remove previous exposure and order

			// Remove this cancel request from map
			orderManagementContext.origOrdSidToCancelOrderSid().remove(cancelled.origOrderSid());
			pendingCancelByOrdSid.remove(cancelled.origOrderSid());
			OrderRequest removedOrigRequest = orderManagementContext.removeOrderRequest(cancelled.origOrderSid());
			if (removedOrigRequest != null){
				NewOrderRequest request = removedOrigRequest.asNewOrderRequest();
				int price = request.limitPrice();
				int resetQuantity = request.quantity() - cancelled.cumulativeQty();
				ValidationOrderBook orderBook = request.securityLevelInfo().validationOrderBook();
				if (cancelled.side() == Side.BUY){
					// Reverse exposure
					exposure.incPurchasingPower(price * resetQuantity);
					// position/crossing impact
					orderBook.buyOrderCancelled(price);
				}
				else{
					// Reverse position/crossing impact
					orderBook.sellOrderCancelled(price, resetQuantity);
				}				
			}
			else {
				LOG.error("Received cancelled for an order that does not exist [origOrderSid:{}]", cancelled.origOrderSid());
				messenger.trySendEventWithOneValue(EventCategory.EXECUTION, 
						EventLevel.CRITICAL, 
						messenger.self().sinkId(), 
						messenger.timerService().toNanoOfDay(), 
						"Received cancelled for a non-exist order", 
						EventValueType.ORDER_SID,
						cancelled.origOrderSid());
			}

			// Remove this cancel request from map
			if (cancelled.orderSid() != cancelled.origOrderSid()){
				orderManagementContext.removeOrderRequest(cancelled.orderSid());
			}
		}
	};

	private final Handler<OrderCancelRejectedSbeDecoder> orderCancelRejectedAvoidMultiCancelHandler = new Handler<OrderCancelRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder cancelRejected) {
			pendingCancelByOrdSid.remove(cancelRejected.origOrderSid());
			orderManagementContext.removeOrderRequest(cancelRejected.orderSid());
			orderManagementContext.origOrdSidToCancelOrderSid().remove(cancelRejected.origOrderSid());
		}
	};
	
	private final Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> orderCancelRejectedWithOrderInfoAvoidMultiCancelHandler = new Handler<OrderCancelRejectedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedWithOrderInfoSbeDecoder cancelRejected) {
			pendingCancelByOrdSid.remove(cancelRejected.origOrderSid());
			orderManagementContext.removeOrderRequest(cancelRejected.orderSid());
			orderManagementContext.origOrdSidToCancelOrderSid().remove(cancelRejected.origOrderSid());
		}
	};
	
	/**
	 * This thread:
	 * Exposure: remove original quantity
	 * Cross: decrement order count
	 * 
	 * Order Update Thread:
	 * Position: no change
	 * 
	 * @param buffer
	 * @param offset
	 * @param senderSinkId
	 * @param dstSinkId
	 * @param seq
	 * @param expired
	 */
	private final Handler<OrderExpiredSbeDecoder> orderExpiredHandler = new Handler<OrderExpiredSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder expired) {
			handleOrderExpiredInternal(expired.orderSid(), expired.secSid(), expired.side(), expired.cumulativeQty());
		}
	};
	
	private final Handler<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandler = new Handler<OrderExpiredWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder expired) {
			handleOrderExpiredInternal(expired.orderSid(), expired.secSid(), expired.side(), expired.cumulativeQty());
		}
	};
	
	private void handleOrderExpiredInternal(int orderSid, long secSid, Side side, int cumulativeQty){
		// Remove the new request from map
		NewOrderRequest request = this.orderManagementContext.removeOrderRequest(orderSid).asNewOrderRequest();
		// Reverse exposure/position/crossing impact
		int price = request.limitPrice();
		int resetQuantity = request.quantity() - cumulativeQty;
		ValidationOrderBook orderBook = request.securityLevelInfo().validationOrderBook();
		if (side == Side.BUY){
			// Reverse exposure
			this.exposure.incPurchasingPower(price * resetQuantity);
			// position/crossing impact
			orderBook.buyOrderExpired(price);
		}
		else{
			// Reverse position/crossing impact
			orderBook.sellOrderExpired(price, resetQuantity);
		}		
	}

	private final Handler<OrderAmendRejectedSbeDecoder> orderAmendRejectedHandler = new Handler<OrderAmendRejectedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendRejectedSbeDecoder codec) {
			throw new UnsupportedOperationException("Order Amend is not supported");
		}
	};

	private Handler<OrderRequestCompletionSbeDecoder> orderRequestCompletionHandler = new Handler<OrderRequestCompletionSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder complete) {
			// Response to the order request sender
			int ordSid = complete.orderSid();
			OrderRequest request = orderManagementContext.getOrderRequest(ordSid);
			if (request != null){
				
				complete.getReason(messenger.stringByteBuffer(), 0);
				
				// Reverse changes to exposure and order book if the order request has been 'rejected' internally
				// If it is rejected externally, we would handle that in OrderRejected
				if (complete.completionType() != OrderRequestCompletionType.OK && complete.completionType() != OrderRequestCompletionType.REJECTED){
					orderManagementContext.removeOrderRequest(ordSid);
					if (request.type() == OrderRequestType.NEW){
						NewOrderRequest newOrderRequest = request.asNewOrderRequest();
						LOG.info("Removed order request from map after receiving order rejected [orderSid:{}]", newOrderRequest.orderSid());
						// Order Reject won't have cumulativeQty and leavesQty
						// Reverse exposure/position/crossing impact
						int price = newOrderRequest.limitPrice();
						ValidationOrderBook orderBook = newOrderRequest.securityLevelInfo().validationOrderBook();
						int resetQuantity = newOrderRequest.quantity();
						if (newOrderRequest.side() == Side.BUY){
							// Reverse exposure
							exposure.incPurchasingPower(price * resetQuantity);
							// position/crossing impact
							orderBook.buyOrderRejected(price);
						}
						else{
							// Reverse position/crossing impact
							orderBook.sellOrderRejected(price, resetQuantity);
						}
					}
					else if (request.type() == OrderRequestType.CANCEL){
						CancelOrderRequest cancelOrderRequest = request.asCancelOrderRequest();
						int ordSidToBeCancelled = cancelOrderRequest.ordSidToBeCancelled();
						pendingCancelByOrdSid.remove(ordSidToBeCancelled);
						orderManagementContext.origOrdSidToCancelOrderSid().remove(ordSidToBeCancelled);
					}
					else {
						LOG.error("Received rejected/failed message for an unknown request type [orderSid:{}, type:{}]", request.orderSid(), request.type().name());
					}
				}
				
				// Complete order request if it is not a composite order
				if (!request.isPartOfCompositOrder()){
					// Complete order request after we have reverted the position impact to our ValidationOrderBook
					messenger.sendOrderRequestCompletion(request.owner(),
							request.clientKey(),
							complete.orderSid(),
							complete.completionType(),
							complete.rejectType(),
							messenger.stringByteBuffer());
					return;
				}
				
				// Composite Order
				// 1) LIMIT_THEN_CANCEL [new order, cancel order]
				// i)  [OK, CANCELLED] -> [OK]
				// ii) [OK, REJECTED] -> [OK]
				// iii)[OK, EXPIRED] -> [OK] (actually, not OK, there we cannot tell the diff between cancelled
				// iv) [REJECTED, REJECTED] -> [REJECTED]
				// Note: let's return OK for all cases for now
				CompositeOrderAction compositeOrderAction = remainingActionsForCompositeOrderByClientKey.get(request.clientKey());
				if (compositeOrderAction != remainingActionsForCompositeOrderByClientKey.defaultReturnValue()){
					if (!compositeOrderAction.cancelSent){
						// Cancel order request
						int cancelOrderOrdSid = ordSidGenerator.getAndIncrement();
						OrderRequest cancelOrderRequest = CancelOrderRequest.of(request.clientKey(),
								compositeOrderAction.request.owner(),
								compositeOrderAction.request.orderSid(), 
								cancelOrderOrdSid,
								compositeOrderAction.request.secSid(), 
								compositeOrderAction.request.side()).isPartOfCompositOrder(true);
						orderManagementContext.putOrderRequest(cancelOrderOrdSid, cancelOrderRequest);
						orderManagementContext.origOrdSidToCancelOrderSid().put(compositeOrderAction.request.orderSid(), cancelOrderOrdSid);
						lineHandler.send(cancelOrderRequest);
						compositeOrderAction.cancelSent = true;
					}
					else {
						// Complete order request after we have reverted the position impact to our ValidationOrderBook
						messenger.sendOrderRequestCompletion(request.owner(),
								request.clientKey(),
								complete.orderSid(),
								complete.completionType(),
								complete.rejectType(),
								messenger.stringByteBuffer());
						remainingActionsForCompositeOrderByClientKey.remove(request.clientKey());						
					}
				}
				else {
					remainingActionsForCompositeOrderByClientKey.remove(request.clientKey());
					LOG.error("Received more than expected order completion [clientKey:{}]", request.clientKey());					
				}
				return;
			}
			LOG.warn("Trying to get an order request that is no longer exist | ordSid:{}", ordSid);
		}
	};
	
	private final Handler<NewCompositeOrderRequestSbeDecoder> newCompositeOrderRequestHandler = new Handler<NewCompositeOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewCompositeOrderRequestSbeDecoder request) {
			if (LOG.isTraceEnabled()){
				LOG.trace("Received new composite order request [{}]", NewCompositeOrderRequestDecoder.decodeToString(request));
			}
		    final long timestamp = messageService.systemClock().timestamp(); 
			byte senderSinkId = header.senderSinkId();
	        final TriggerInfoDecoder triggerInfo = request.triggerInfo();
	        final byte triggeredBy = triggerInfo.triggeredBy();
	        final int triggerSeqNum = triggerInfo.triggerSeqNum();
	        final long triggerNanoOfDay = triggerInfo.nanoOfDay();
	        if (triggeredBy > 0) {
	            messenger.performanceSender().sendGenericTracker(performanceSubscribers, (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_NEWORDERREQUEST, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
	        }
			// Validation #1 - fat finger check
			OrderRejectType rejectType = validate(request);
			if (rejectType != OrderRejectType.VALID_AND_NOT_REJECT){
				messenger.sendOrderRequestCompletion(senderSinkId, request.clientKey(), OrderRequestCompletionType.REJECTED, OrderRequestRejectTypeConverter.from(rejectType));
				return;
			}

			// Validation #2 - exposure
			long secSid = request.secSid();
			SecurityLevelInfo securityLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
			int price = request.limitPrice();
			int quantity = request.quantity();
			if (request.side() == Side.BUY){
				long notional = (long)price * quantity;
				if (!exposure.okToBuy(notional)){
					messenger.sendOrderRequestCompletion(senderSinkId, 
							request.clientKey(), 
							OrderRequestCompletionType.REJECTED, 
							OrderRequestRejectType.ORDER_EXCEED_PURCHASING_POWER);
					messenger.trySendEvent(EventCategory.EXECUTION, EventLevel.WARNING, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Order exceed purchasing power");
					if (LOG.isTraceEnabled()){
						LOG.trace("Order exceed purchasing power [secSid:{}, price:{}, quantity:{}, notional:{}, pp:{}]", secSid, price, quantity, notional, exposure.purchasingPower());
					}
					return;
				}
				// Validation #3 - position, cross
				ValidationOrderBook book = securityLevelInfo.validationOrderBook();
				rejectType = book.isNewBuyOrderOk(request.limitPrice(), request.quantity());
				if (rejectType != OrderRejectType.VALID_AND_NOT_REJECT){
					OrderRequestRejectType requestRejectType = OrderRequestRejectTypeConverter.from(rejectType);
					messenger.sendOrderRequestCompletion(senderSinkId, 
							request.clientKey(), 
							OrderRequestCompletionType.FAILED, 
							requestRejectType);
					messenger.trySendEvent(EventCategory.EXECUTION, EventLevel.WARNING, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Order rejected due to " + requestRejectType.name());
					if (LOG.isTraceEnabled()){
						LOG.trace("Order rejected [secSid:{}, price:{}, quantity:{}, reason:{}]", secSid, price, quantity, requestRejectType.name());
					}
					return;
				}
				exposure.decPurchasingPower(notional);
				book.newBuyOrder(price);
			}
			else{
				// Validation #3 - position, cross
				ValidationOrderBook book = securityLevelInfo.validationOrderBook();
				rejectType = book.isNewSellOrderOk(price, quantity);
				if (rejectType != OrderRejectType.VALID_AND_NOT_REJECT){
					OrderRequestRejectType requestRejectType = OrderRequestRejectTypeConverter.from(rejectType);
					messenger.sendOrderRequestCompletion(senderSinkId, 
							request.clientKey(), 
							OrderRequestCompletionType.FAILED, 
							requestRejectType);
					messenger.trySendEvent(EventCategory.EXECUTION, EventLevel.WARNING, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Order rejected due to " + requestRejectType.name());
					if (LOG.isTraceEnabled()){
						LOG.trace("Order rejected [secSid:{}, price:{}, quantity:{}, reason:{}]", secSid, price, quantity, requestRejectType.name());
					}
					return;
				}
				book.newSellOrder(price, quantity);
			}
			
			// Create an order request
			int newOrderOrdSid = ordSidGenerator.getAndIncrement();
			NewOrderRequest newOrderRequest = NewOrderRequest.of(refMgr.get(senderSinkId), 
					request, 
					newOrderOrdSid, 
					securityLevelInfo);
			newOrderRequest
					.isPartOfCompositOrder(true)
					.numThrottleRequiredToProceed(OrderRequest.NUM_THROTTLE_REQUIRED_TO_PROCEED_FOR_LIMIT_THEN_CANCEL);
			orderManagementContext.putOrderRequest(newOrderOrdSid, newOrderRequest);
			
			// Send order
			lineHandler.send(newOrderRequest);

			// Send an order request completion message back to the originator
			// so that the client would know the assigned ordSid
			messenger.sendOrderRequestAccepted(senderSinkId, request.clientKey(), newOrderOrdSid);

			// Current composite requires 2 individual order completion
			remainingActionsForCompositeOrderByClientKey.put(request.clientKey(), CompositeOrderAction.of(newOrderRequest));
		}
	};
	
	private void sendPerformance(long timestamp, NewOrderRequestSbeDecoder request){
		final TriggerInfoDecoder triggerInfo = request.triggerInfo();
		final byte triggeredBy = triggerInfo.triggeredBy();
		if (triggeredBy > 0) {
			final int triggerSeqNum = triggerInfo.triggerSeqNum();
			final long triggerNanoOfDay = triggerInfo.nanoOfDay();
			messenger.performanceSender().sendGenericTracker(performanceSubscribers.elements()[0], (byte)messenger.self().sinkId(), TrackerStepType.RECEIVED_NEWORDERREQUEST, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
		}
	}

	private final Handler<NewOrderRequestSbeDecoder> newOrderRequestHandler = new Handler<NewOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder request) {
			if (LOG.isTraceEnabled()){
				LOG.trace("Received new order request [{}]", NewOrderRequestDecoder.decodeToString(request));
			}
		    final long timestamp = messageService.systemClock().timestamp(); 
			byte senderSinkId = header.senderSinkId();

			// Validation #1 - fat finger check
			OrderRejectType rejectType = OrderRejectType.VALID_AND_NOT_REJECT;
			//OrderRejectType rejectType = validate(request);
			//if (rejectType != OrderRejectType.VALID_AND_NOT_REJECT){
			//	messenger.sendOrderRequestCompletion(senderSinkId, request.clientKey(), OrderRequestCompletionType.REJECTED, OrderRequestRejectTypeConverter.from(rejectType));
			//	return;
			//}

			// Validation #2 - exposure
			long secSid = request.secSid();
			Side side = request.side();
			SecurityLevelInfo securityLevelInfo = orderManagementContext.securiytLevelInfo(secSid);
			if (side == Side.BUY && !securityLevelInfo.throttleTracker().getThrottle()){
				int nextOrdSid = ordSidGenerator.getAndIncrement();
				messenger.sendOrderRequestCompletion(refMgr.get(senderSinkId),
						request.clientKey(),
						nextOrdSid,
						OrderRequestCompletionType.REJECTED_INTERNALLY, 
						OrderRequestRejectType.EXCEED_UNDERLYING_THROTTLE,
						OrderSender.ORDER_REQUEST_COMPLETION_EMPTY_REASON);
				
				messenger.trySendEventWithThreeValues(EventCategory.EXECUTION, 
						EventLevel.INFO,
						EventType.THROTTLED,
						senderSinkId,
						messageService.systemClock().nanoOfDay(),
						"Order throttle (underlying)",
						EventValueType.ORDER_SID,
						nextOrdSid,
						EventValueType.SECURITY_SID,
						secSid,
						EventValueType.SIDE,
						side.value());
				if (LOG.isTraceEnabled()){
					LOG.trace("Order exceeds underlying throttle [secSid:{}, undSecSid:{}]", secSid, securityLevelInfo.undSecSid());
				}
				sendPerformance(timestamp, request);
				return;
			}
			
			int price = request.limitPrice();
			int quantity = request.quantity();
			if (side == Side.BUY){
				long notional = (long)price * quantity;
				if (!exposure.okToBuy(notional)){
					messenger.sendOrderRequestCompletion(senderSinkId, 
							request.clientKey(), 
							OrderRequestCompletionType.REJECTED, 
							OrderRequestRejectType.ORDER_EXCEED_PURCHASING_POWER);
					messenger.trySendEvent(EventCategory.EXECUTION, EventLevel.WARNING, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Order exceed purchasing power");
					if (LOG.isTraceEnabled()){
						LOG.trace("Order exceed purchasing power [secSid:{}, price:{}, quantity:{}, notional:{}, pp:{}]", secSid, price, quantity, notional, exposure.purchasingPower());
					}
					sendPerformance(timestamp, request);
					return;
				}
				// Validation #3 - position, cross
				ValidationOrderBook book = securityLevelInfo.validationOrderBook();
				rejectType = book.isNewBuyOrderOk(request.limitPrice(), request.quantity());
				if (rejectType != OrderRejectType.VALID_AND_NOT_REJECT){
					OrderRequestRejectType requestRejectType = OrderRequestRejectTypeConverter.from(rejectType);
					messenger.sendOrderRequestCompletion(senderSinkId, 
							request.clientKey(), 
							OrderRequestCompletionType.FAILED, 
							requestRejectType);
					messenger.trySendEvent(EventCategory.EXECUTION, EventLevel.WARNING, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Order rejected due to " + requestRejectType.name());
					if (LOG.isTraceEnabled()){
						LOG.trace("Order rejected [secSid:{}, price:{}, quantity:{}, reason:{}]", secSid, price, quantity, requestRejectType.name());
					}
					sendPerformance(timestamp, request);
					return;
				}
				exposure.decPurchasingPower(notional);
				book.newBuyOrder(price);
			}
			else{
				// Validation #3 - position, cross
				ValidationOrderBook book = securityLevelInfo.validationOrderBook();
				rejectType = book.isNewSellOrderOk(price, quantity);
				if (rejectType != OrderRejectType.VALID_AND_NOT_REJECT){
					OrderRequestRejectType requestRejectType = OrderRequestRejectTypeConverter.from(rejectType);
					messenger.sendOrderRequestCompletion(senderSinkId, 
							request.clientKey(), 
							OrderRequestCompletionType.FAILED, 
							requestRejectType);
					messenger.trySendEvent(EventCategory.EXECUTION, EventLevel.WARNING, messenger.self().sinkId(), messenger.timerService().toNanoOfDay(), "Order rejected due to " + requestRejectType.name());
					if (LOG.isTraceEnabled()){
						LOG.trace("Order rejected [secSid:{}, price:{}, quantity:{}, reason:{}]", secSid, price, quantity, requestRejectType.name());
					}
					sendPerformance(timestamp, request);
					return;
				}
				book.newSellOrder(price, quantity);
			}
			
			// Create an order request
			int nextOrdSid = ordSidGenerator.getAndIncrement();
			NewOrderRequest newOrderRequest = NewOrderRequest.of(refMgr.get(senderSinkId), request, nextOrdSid, securityLevelInfo);
			orderManagementContext.putOrderRequest(nextOrdSid, newOrderRequest.numThrottleRequiredToProceed((request.side() == Side.BUY) ? numThrottleRequiredToProceedForBuy : OrderRequest.NUM_THROTTLE_REQUIRED_TO_PROCEED));
			
			// Send order
			lineHandler.send(newOrderRequest);
			
			// Send an order request completion message back to the originator
			// so that the client would know the assigned ordSid
			messenger.sendOrderRequestAccepted(senderSinkId, request.clientKey(), nextOrdSid);
			sendPerformance(timestamp, request);
		}
	};

	private final Handler<CancelOrderRequestSbeDecoder> cancelOrderRequestAvoidMultiCancelHandler = new Handler<CancelOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder request) {
			int ordSidToBeCancelled = request.orderSidToBeCancelled();
			// Validation #0: Make sure we don't have a cancellation on the same order
			if (pendingCancelByOrdSid.get(ordSidToBeCancelled) != pendingCancelByOrdSid.defaultReturnValue()){
				// Reject this cancel as one is already in progress
				messenger.sendOrderRequestCompletion(header.senderSinkId(), 
						request.clientKey(), 
						OrderRequestCompletionType.ALREADY_IN_PENDING_CANCEL,
						OrderRequestRejectType.OTHER);
				return;
			}
			// Make sure the order to be cancelled exists
			OrderRequest orderRequest = orderManagementContext.getOrderRequest(ordSidToBeCancelled);
			if (orderManagementContext.getOrderRequest(ordSidToBeCancelled) != null){
				pendingCancelByOrdSid.put(ordSidToBeCancelled, request.clientKey());
				handleCancelOrderRequest(buffer, offset, header, request, orderRequest.asNewOrderRequest());
				return;
			}
			// Reject this cancel as one is already in progress
			messenger.sendOrderRequestCompletion(header.senderSinkId(), 
					request.clientKey(), 
					OrderRequestCompletionType.REJECTED,
					OrderRequestRejectType.UNKNOWN_ORDER);
		}
	};
	
	private final Handler<CancelOrderRequestSbeDecoder> cancelOrderRequestHandler = new Handler<CancelOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder request) {
			OrderRequest orderRequest = orderManagementContext.getOrderRequest(request.orderSidToBeCancelled());
			if (orderRequest != null){
				handleCancelOrderRequest(buffer, offset, header, request, orderRequest.asNewOrderRequest());
			}
			else {
				if (request.force() == BooleanType.TRUE){
					handleCancelOrderRequest(buffer, offset, header, request);
				}
				else {
					LOG.error("Cannot cancel an order that no longer exists [ordSidToBeCancelled:{}]", request.orderSidToBeCancelled());
					messenger.sendOrderRequestCompletion(header.senderSinkId(), request.clientKey(), OrderRequestCompletionType.REJECTED_INTERNALLY, OrderRequestRejectType.UNKNOWN_ORDER);
				}
			}
		}
	};
	
	private void handleCancelOrderRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder request, NewOrderRequest newOrderRequest) {
		// No validation is required
		int ordSid = ordSidGenerator.getAndIncrement();
		byte senderSinkId = header.senderSinkId();
		CancelOrderRequest cancelOrderRequest = CancelOrderRequest.of(refMgr.get(senderSinkId), request, ordSid, newOrderRequest.secSid(), newOrderRequest.side());
		this.orderManagementContext.putOrderRequest(ordSid, cancelOrderRequest);
		this.orderManagementContext.origOrdSidToCancelOrderSid().put(request.orderSidToBeCancelled(), ordSid);
		this.lineHandler.send(cancelOrderRequest);
		this.messenger.sendOrderRequestAccepted(senderSinkId, request.clientKey(), ordSid);
	}

	private void handleCancelOrderRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder request) {
		// No validation is required
		int ordSid = ordSidGenerator.getAndIncrement();
		byte senderSinkId = header.senderSinkId();
		CancelOrderRequest cancelOrderRequest = CancelOrderRequest.of(refMgr.get(senderSinkId), request, ordSid, request.secSid(), request.side());
		this.orderManagementContext.putOrderRequest(ordSid, cancelOrderRequest);
		this.orderManagementContext.origOrdSidToCancelOrderSid().put(request.orderSidToBeCancelled(), ordSid);
		this.lineHandler.send(cancelOrderRequest);
		this.messenger.sendOrderRequestAccepted(senderSinkId, request.clientKey(), ordSid);
	}

	private final Handler<AmendOrderRequestSbeDecoder> amendOrderRequestHandler = new Handler<AmendOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AmendOrderRequestSbeDecoder codec) {
			throw new UnsupportedOperationException("Order Amend is currently not supported");
		}
	};
	
	private final Handler<AmendOrderRequestSbeDecoder> amendOrderRequestAvoidMultiAmendHandler = new Handler<AmendOrderRequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, AmendOrderRequestSbeDecoder codec) {
			throw new UnsupportedOperationException("Order Amend is currently not supported");
			// Validation #0: Make sure there is no pending amend for the same order
//			if (this.pendingAmendByOrdSid.get(request.orderSidToBeAmended()) != this.pendingAmendByOrdSid.defaultReturnValue()){
//				// Reject this cancel as one is already in progress
//				this.messenger.sendOrderRequestCompletion(senderSinkId, request.clientKey(), OrderRequestCompletionType.ALREADY_IN_PENDING_AMEND);
//				return;
//			}
//			this.pendingAmendByOrdSid.put(request.orderSidToBeAmended(), request.clientKey());
//			handleAmendOrderRequest(buffer, offset, senderSinkId, dstSinkId, seq, request);
		}
	};
	
	private final Handler<TradeCreatedSbeDecoder> tradeCreatedHandler = new Handler<TradeCreatedSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder trade) {
			try {
				//			LOG.debug("Received trade created [orderSid:{}]", trade.orderSid());
				if (trade.status() == OrderStatus.FILLED){
					handleFilledTrade(trade.orderSid(), trade.side(), trade.executionPrice(), trade.executionQty());
				}
				else {
					handleUnfilledTrade(trade.secSid(), trade.side(), trade.executionPrice(), trade.executionQty());
				}
			}
			catch (Exception e){
				LOG.error("Caught exception when handling trade created [orderSid:{}, orderStatus:{}]", trade.orderId(), trade.status().name(), e);
			}
		}
	};
	
	private final Handler<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoHandler = new Handler<TradeCreatedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder trade) {
			if (trade.status() == OrderStatus.FILLED){
				handleFilledTrade(trade.orderSid(), trade.side(), trade.executionPrice(), trade.executionQty());
			}
			else {
				handleUnfilledTrade(trade.secSid(), trade.side(), trade.executionPrice(), trade.executionQty());
			}
		}
	};
	
	private void handleUnfilledTrade(long secSid, Side side, int execPrice, int execQty){
		if (side == Side.BUY){
			orderManagementContext.securiytLevelInfo(secSid).validationOrderBook().buyTrade(execPrice, execQty);
		}
		else{
			exposure.incPurchasingPower(execPrice * execQty);
			orderManagementContext.securiytLevelInfo(secSid).validationOrderBook().sellTrade(execPrice, execQty);
		}		
	}
	
	private void handleFilledTrade(int orderSid, Side side, int execPrice, int execQty){
//		LOG.info("Removed order request from map after receiving order filled [orderSid:{}]", orderSid);
		NewOrderRequest request = orderManagementContext.removeOrderRequest(orderSid).asNewOrderRequest();
		int price = request.limitPrice();
		ValidationOrderBook orderBook = request.securityLevelInfo().validationOrderBook();
		if (side == Side.BUY){
			orderBook.buyTrade(execPrice, execQty);
			orderBook.buyOrderFilled(price);
		}
		else{
			exposure.incPurchasingPower(execPrice * execQty);
			orderBook.sellTrade(execPrice, execQty);
			orderBook.sellOrderFilled(price);
		}
	}
	
	private final Handler<TradeCancelledSbeDecoder> tradeCancelledHandler = new Handler<TradeCancelledSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCancelledSbeDecoder tradeCancelled) {
			int price = tradeCancelled.cancelledExecutionPrice();
			int executionQuantity = tradeCancelled.cancelledExecutionQty();
			long notional = (long)executionQuantity * price;
			if (tradeCancelled.side() == Side.BUY){
				orderManagementContext.securiytLevelInfo(tradeCancelled.secSid()).validationOrderBook().buyTradeCancelled(price, executionQuantity);
				exposure.incPurchasingPower(notional);
			}
			else{
				orderManagementContext.securiytLevelInfo(tradeCancelled.secSid()).validationOrderBook().sellTradeCancelled(price, executionQuantity);
				exposure.decPurchasingPower(notional);
			}
		}
	};

	private boolean updateInitialPurchasingPowerInDollar(long power){
		long newValue = power * 1000;
		if (power < 0 || newValue < 0){
			throw new IllegalArgumentException("Initial purchasing power must not be negative (newValue exceeds Long.MAX?) [input:" + power + ", newValue:" + newValue + "]");
		}
		long orig = this.exposure.purchasingPower();
		this.exposure.initialPurchasingPower(newValue);
		LOG.info("Updated purchasing power [newInitial:{}, orig:{}, current:{}]", newValue, orig, this.exposure.purchasingPower());
		return true;
	}
	
	private final LifecycleExceptionHandler lineHandlerExceptionHandler = new LifecycleExceptionHandler() {
		
		@Override
		public void handleOnStartException(Throwable e) {
			LOG.error("Cannot start line handler", e);
		}
		
		@Override
		public void handleOnShutdownException(Throwable e) {
			LOG.error("Cannot shutdown line handler", e);
		}
		
		@Override
		public boolean handle(LifecycleState current, Throwable e) {
			LOG.error("Caught exception in line handler [currentState:" + current.name() + "]", e);
			return false;
		}
	};

	private void resetState(){
	    LOG.info("Reset state [name:{}]", name);
        orderManagementContext.reset();
        pendingCancelByOrdSid.clear();
        exposure.clear();
        lineHandler.reset().whenCompleteAsync((state, cause) -> { verifyLineHandlerState(LifecycleState.RESET, state, cause);}, lineHandlerRelatedExecutor);	    
	}
	
	boolean isClear(){
	    return orderManagementContext.isClear() && pendingCancelByOrdSid.isEmpty() && lineHandler.isClear();
	}
	
	Int2ObjectOpenHashMap<CompositeOrderAction> remainingActionsForCompositeOrderByClientKey(){
		return remainingActionsForCompositeOrderByClientKey;
	}
	
	Int2IntOpenHashMap pendingCancelByOrdSid(){
		return pendingCancelByOrdSid;
	}
}
 
