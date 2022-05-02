package com.lunar.order.hkex.ocg;

import static org.apache.logging.log4j.util.Unbox.box;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import com.lunar.config.LineHandlerConfig;
import com.lunar.core.LifecycleState;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.LineHandlerEngine;
import com.lunar.order.LineHandlerEngineExceptionHandler;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;
import com.lunar.order.OrderUpdateEventProducer;
import com.lunar.service.LifecycleController;
import com.lunar.service.ServiceConstant;

public class CtpOcgLineHandlerEngine implements LineHandlerEngine {
	private static final Logger LOG = LogManager.getLogger(CtpOcgLineHandlerEngine.class);
	
	class CallbackContext {
	    private final MutableDirectBuffer erBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));;
	    private final MutableDirectBuffer erStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));;

	    private final OrderAcceptedSbeEncoder orderAcceptedEncoderForER = new OrderAcceptedSbeEncoder();
	    private final OrderExpiredSbeEncoder orderExpiredEncoderForER = new OrderExpiredSbeEncoder();
	    private final OrderCancelledSbeEncoder orderCancelledEncoderForER = new OrderCancelledSbeEncoder();
	    private final OrderRejectedSbeEncoder orderRejectedEncoderForER = new OrderRejectedSbeEncoder();
	    private final TradeCreatedSbeEncoder tradeCreatedEncoderForER = new TradeCreatedSbeEncoder();  
	}
	
	private static final int MAX_NUM_CALLBACK_THREADS = 5;
	@SuppressWarnings("unused")
	private static final String ACTUAL_RISK_VIOLATION_ERROR_TEXT_RULE_6 = "RMS Rejected:Violate risk control rule 6";
	private static final String RISK_VIOLATION_ERROR_TEXT_RULE_6 = "rule 6";
	private static final int RISK_VIOLATION_ERROR_TEXT_RULE_6_OFFSET = 34;
	private final String name;
	private boolean init =  false;
	private final TimerService timerService;
	private final SystemClock systemClock;
	private final MutableDirectBuffer activeBuffer;
	private final MutableDirectBuffer activeStringBuffer;
	
	private final CallbackContext[] callbackContextList = new CallbackContext[MAX_NUM_CALLBACK_THREADS];
	
	private final int channelId = 1;
	private final AtomicLong channelSeq = new AtomicLong();
	private volatile OrderUpdateEventProducer ordUpdEventProducer;
	private Consumer<NewOrderRequest> currentNewOrderRequestConsumer;
	private final Consumer<NewOrderRequest> activeNewOrderRequestConsumer;
	private Consumer<CancelOrderRequest> currentCancelOrderRequestConsumer;
	private final Consumer<CancelOrderRequest> activeCancelOrderRequestConsumer;
	private final String connectorFile;	
	private final String configFile;
	private final String user;
	private final String account;
    private volatile boolean isRecovered;
    private volatile boolean isConnected;

	private CtpOcgApi api;
	private Consumer<NewOrderRequest> noopNewOrderRequestConsumer = (o) -> { LOG.warn("Noop new order request consumer");};
	private Consumer<CancelOrderRequest> noopCancelOrderRequestConsumer = (o) -> { LOG.warn("Noop cancel order request consumer");};
	
	// Codec
    private final OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
    private final OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder = new OrderCancelRejectedSbeEncoder();
	
	// Lifecycle related fields
	private final LifecycleController controller;
	@SuppressWarnings("unused")
    private LifecycleExceptionHandler lifecycleExceptionHandler = LifecycleExceptionHandler.DEFAULT_HANDLER;

    private LineHandlerEngineExceptionHandler engineExceptionHandler = LineHandlerEngineExceptionHandler.NULL_HANDLER;

	private final LifecycleStateHook lifecycleStateChangeHandler = new LifecycleStateHook() {
		@Override
		public CompletableFuture<Boolean> onPendingWarmup() {
		    LOG.info("Initialize ocg api for warmup");
		    int result = api.invokeSetMode(true);
		    if (result != 0){
		        LOG.error("Could not set ocg api mode to warmup [result:{}]", result);
		        return CompletableFuture.completedFuture(false);
		    }
            final LocalDate localDate = systemClock.date();
            final int date = localDate.getYear() * 10000 + localDate.getMonthValue() * 100 + localDate.getDayOfMonth();
            result = api.invokeInitialize(connectorFile, configFile, user, account, date, ServiceConstant.START_ORDER_SID_SEQUENCE);
		    if (result != 0){
                LOG.error("Could not initialize ocg api [result:{}]", result);
                return CompletableFuture.completedFuture(false);		        
		    }
		    return CompletableFuture.completedFuture(true);
		}
		
		@Override
		public CompletableFuture<Boolean> onPendingReset() {
			resetState();
			int result = api.invokeClose();
			if (result != 0){
			    LOG.error("Could not close ocg api [result:{}]", result);
			    return CompletableFuture.completedFuture(false);
			}
            LOG.info("Reset completed and closed ocg api [name:{}]", name);
            recoveryAndConnectFuture = new CompletableFuture<Boolean>();
			return CompletableFuture.completedFuture(true);
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingRecovery() {
		    if (init){
		        return CompletableFuture.completedFuture(true);
		    }
            LOG.error("LineHandlerEngine has not been init [name:{}]", name);
            return CompletableFuture.completedFuture(false);
		};
		
		@Override
		public CompletableFuture<Boolean> onPendingActive() {
			if (isConnected && isRecovered){
                return CompletableFuture.completedFuture(true);
			}
			LOG.error("LineHandlerEngine has not been connected and recovered [name:{}]", name);
			return CompletableFuture.completedFuture(false);
		};
		
		@Override
		public void onWarmupEnter(){
			currentNewOrderRequestConsumer = activeNewOrderRequestConsumer;
			currentCancelOrderRequestConsumer = activeCancelOrderRequestConsumer;
		}
		
		@Override
		public void onActiveEnter() {
			currentNewOrderRequestConsumer = activeNewOrderRequestConsumer;
			currentCancelOrderRequestConsumer = activeCancelOrderRequestConsumer;
		};
		
        @Override
		public void onRecoveryEnter() {
            currentNewOrderRequestConsumer = activeNewOrderRequestConsumer;
            currentCancelOrderRequestConsumer = activeCancelOrderRequestConsumer;		    
		};
		
		@Override
		public void onResetEnter() {
			currentNewOrderRequestConsumer = noopNewOrderRequestConsumer;
			currentCancelOrderRequestConsumer = noopCancelOrderRequestConsumer;
		};

		@Override
		public void onStopped() {
			int result = api.invokeClose();
			if (result != 0){
			    LOG.error("Unable to stop ocg api during stopped [result:{}]", result);
			}
			else {
			    LOG.info("Stopped ocg api during successfully");
			}
		};
	};
	
	private final CtpOcgApi.CallbackHandler ocgApiCallbackHandler = new CtpOcgApi.CallbackHandler() {			
		@Override
		public int onSessionState(int threadId, int sessionState) {
			if (sessionState == CtpOcgApi.CONNECTED) {
			    LOG.info("OCG session connected.");
			    isConnected = true;
			}
			else if (sessionState == CtpOcgApi.DISCONNECTED) {
			    LOG.info("OCG session disconnected.");
			    isConnected = false;
			    if (ServiceConstant.SHOULD_OCG_API_DISCONNECTION_CHECK){
			    	LOG.info("OCG disconnection check has been enabled");
			    	engineExceptionHandler.onDisconnected();
			    	if (recoveryAndConnectFuture != null && !recoveryAndConnectFuture.isDone()){
			    		recoveryAndConnectFuture.completeExceptionally(new IllegalStateException("Ocg api is disconnected"));
			    	}
			    }
			    else{
			    	LOG.info("OCG disconnection check has been disabled");
			    }
			}
			return 0;
		}
		
        @Override
        public int onEndRecovery(int threadId) {
            LOG.info("End recovery received.");
            if (recoveryAndConnectFuture != null && !recoveryAndConnectFuture.isDone()){
                recoveryAndConnectFuture.complete(true);
                isRecovered = true;
                ordUpdEventProducer.onEndOfRecovery();
            }
            return 0;
        }

        private final AtomicInteger orderIdSeq = new AtomicInteger(6000000);
        @Override
		public int onExecutionReport(int threadId, int clientOrderId, String exchangeOrderId, long dateTime, int execTypeCode,
				int execTransTypeCode, char orderStatusCode, long secSid, int buySellCode, int orderPrice, int orderQuantity, int orderTotalTradeQuantity,
				int leavesQuantity, String executionId, int tradeQuantity, int tradePrice, String errorText, boolean inSyncFlag) {
        	if (LOG.isTraceEnabled()){
        		LOG.trace("Received execution report clientOrderId: {}, exchangeOrderId: {}, dateTime: {}, execTypeCode: {}, execTransTypeCode: {}, orderStatusCode: {}, secSid: {}, buySellCode: {}, orderPrice:{}, orderQuantity:{}, orderTotalTradeQuantity: {}, leavesQuantity: {}, executionId: {}, tradeQuantity: {}, tradePrice: {}, errorText: {}, inSyncFlag {}",
	                box(clientOrderId), exchangeOrderId, box(dateTime), box(execTypeCode),
	                box(execTransTypeCode), box(orderStatusCode), box(secSid), box(buySellCode),
	                box(orderPrice),
	                box(orderQuantity),
	                box(orderTotalTradeQuantity),
	                box(leavesQuantity), executionId, box(tradeQuantity), box(tradePrice), errorText, box(inSyncFlag));
        	}
		    try {
		        final CallbackContext callbackContext = callbackContextList[threadId];
				switch (orderStatusCode) {
				case CtpOcgApi.NEW:
//					LOG.debug("About to send order accepted {}", clientOrderId);
					sendOrderAccepted(callbackContext.erBuffer,
					        callbackContext.orderAcceptedEncoderForER,
							clientOrderId,
							OrderStatus.NEW,
							orderPrice,
							orderTotalTradeQuantity,
							leavesQuantity,
							CtpUtil.convertSide(buySellCode),
							orderIdSeq.getAndIncrement(),
							//exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
							secSid,
							ExecutionType.NULL_VAL,
							timerService.toNanoOfDay());
					break;
				case CtpOcgApi.PARTIALLY_FILLED:
				{
//					LOG.debug("About to send order partially filled {}", clientOrderId);
					final int orderId = orderIdSeq.getAndIncrement(); 
							//exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId);
					sendTradeCreated(callbackContext.erBuffer,
					        callbackContext.tradeCreatedEncoderForER,
							clientOrderId,
							orderId,
							secSid,
							CtpUtil.convertSide(buySellCode),
							leavesQuantity,
							orderTotalTradeQuantity, 
							OrderSender.prepareExecutionId(executionId, callbackContext.erStringBuffer),
							tradePrice, 
							tradeQuantity,
							OrderStatus.PARTIALLY_FILLED,
							timerService.toNanoOfDay());
					break;
					}
				case CtpOcgApi.FILLED:
				{
//					LOG.debug("About to send order filled {}", clientOrderId);
					final int orderId = orderIdSeq.getAndIncrement(); 
							//exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId);
					sendTradeCreated(callbackContext.erBuffer,
					        callbackContext.tradeCreatedEncoderForER,
							clientOrderId,
							orderId,
							secSid,
							CtpUtil.convertSide(buySellCode),
							leavesQuantity,
							orderTotalTradeQuantity, 
							OrderSender.prepareExecutionId(executionId, callbackContext.erStringBuffer),
							tradePrice, 
							tradeQuantity,
							OrderStatus.FILLED,
							timerService.toNanoOfDay());
					break;
				}
				case CtpOcgApi.DONE_FOR_DAY:
					sendOrderExpired(callbackContext.erBuffer,
					        callbackContext.orderExpiredEncoderForER,
							clientOrderId,
							orderIdSeq.getAndIncrement(),
                            secSid,
                            CtpUtil.convertSide(buySellCode),
							orderPrice,
							orderTotalTradeQuantity,
							leavesQuantity,
							OrderStatus.EXPIRED,
							timerService.toNanoOfDay());
					break;
				case CtpOcgApi.CANCELED:
				{
//					LOG.debug("About to send order cancelled {}", clientOrderId);
					Side cancelSide = Side.BUY;
					try {
						cancelSide = CtpUtil.convertSide(buySellCode);
					}
					catch (Exception e){
						LOG.error("Caught exception when converting side: ", e);
					}
					sendOrderCancelled(callbackContext.erBuffer,
					        callbackContext.orderCancelledEncoderForER,
							clientOrderId,
					        clientOrderId,
							OrderStatus.CANCELLED,
							orderPrice,
							cancelSide,
							orderIdSeq.getAndIncrement(),
//							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
							secSid,
							leavesQuantity,
							orderTotalTradeQuantity,
							timerService.toNanoOfDay());
					break;
				}
				case CtpOcgApi.STOPPED:
					sendOrderRejected(callbackContext.erBuffer,
					        callbackContext.orderRejectedEncoderForER,
							clientOrderId,
							orderIdSeq.getAndIncrement(),
//							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
							secSid,
							CtpUtil.convertSide(buySellCode),
							orderPrice,
							orderTotalTradeQuantity,
							leavesQuantity,
							OrderStatus.REJECTED,
							OrderRejectType.OTHER,
							OrderSender.prepareRejectedReason(errorText != null ? errorText : Strings.EMPTY, callbackContext.erStringBuffer),
							timerService.toNanoOfDay());
					break;
				case CtpOcgApi.REJECTED:
					Side side = Side.BUY;
					try {
						side = CtpUtil.convertSide(buySellCode);
					}
					catch (Exception e){
						LOG.error("Caught exception when converting side: ", e);
					}
					
					OrderRejectType rejectType = OrderRejectType.OTHER;
					if (errorText != null){
						if (errorText.startsWith(RISK_VIOLATION_ERROR_TEXT_RULE_6, RISK_VIOLATION_ERROR_TEXT_RULE_6_OFFSET)){
							rejectType = OrderRejectType.RMS_INSUFFICIENT_LONG_POSITION_DIRECT_MAP;
						}
					}
					else {
						errorText = Strings.EMPTY;
					}
//					LOG.debug("About to send order rejected {}", clientOrderId);
					sendOrderRejected(callbackContext.erBuffer,
					        callbackContext.orderRejectedEncoderForER,
							clientOrderId,
							orderIdSeq.getAndIncrement(),
//							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
                            secSid,
                            side,
                            orderPrice,
							orderTotalTradeQuantity,
							leavesQuantity,
							OrderStatus.REJECTED,
							rejectType,
							OrderSender.prepareRejectedReason(errorText, activeStringBuffer),
							timerService.toNanoOfDay());
					break;
				default:
				    LOG.error("Received unexpected order status code [status:{}]", orderStatusCode);
				    break;
				}
					
				return 0;
		    }
		    catch (final Exception e) {
                LOG.error("Error handling execution report", e);
                return 0;
		    }
		}
	};
	
	public static CtpOcgLineHandlerEngine of(LineHandlerConfig config, TimerService timerService, SystemClock systemClock){
		if (!config.connectorFile().isPresent()){
			throw new IllegalArgumentException("Connector file is missing from config [name:" + config.name() + "]");
		}
		if (!config.configFile().isPresent()){
			throw new IllegalArgumentException("Config file is missing from config [name:" + config.name() + "]");
		}
		if (!config.user().isPresent()){
			throw new IllegalArgumentException("User is missing from config [name:" + config.name() + "]");
		}
		if (!config.account().isPresent()){
			throw new IllegalArgumentException("Account is missing from config [name:" + config.name() + "]");
		}

		CtpOcgLineHandlerEngine engine = new CtpOcgLineHandlerEngine(config.name(), 
				timerService,
				systemClock,
				config.connectorFile().get(),
				config.configFile().get(),
				config.user().get(),
				config.account().get());
		return engine.setOcgApi(new CtpOcgApi(engine.ocgApiCallbackHandler, timerService));
	}

	CtpOcgLineHandlerEngine(String name, TimerService timerService, SystemClock systemClock, String connectorFile, String configFile, String user, String account){
		this.name = name;
		this.activeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.activeStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		this.timerService = timerService;
		this.systemClock = systemClock;
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateChangeHandler);
		this.connectorFile = connectorFile;
		this.configFile = configFile;
		this.user = user;
		this.account = account;
		for (int i = 0; i < MAX_NUM_CALLBACK_THREADS; i++) {
		    callbackContextList[i] = new CallbackContext();
		}
		this.activeNewOrderRequestConsumer = (o) -> {
			handleNewOrderRequest(activeBuffer, activeStringBuffer, o);
		};
		this.activeCancelOrderRequestConsumer = (o) -> {
			handleCancelOrderRequest(activeBuffer, activeStringBuffer, o);
		};
	}
	
	private void handleNewOrderRequest(MutableDirectBuffer messageBuffer, MutableDirectBuffer stringBuffer, NewOrderRequest request){
		// At this point, line handler should only communicate using only orderSid (not clientKey)
		if (LOG.isTraceEnabled()){
			LOG.trace("handleNewOrderRequest [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
		}
		try {
			boolean isEnhanced = false;
			final int orderType;
			switch (request.orderType()) {
			case ENHANCED_LIMIT_ORDER:
				orderType = CtpOcgApi.LIMIT;
				isEnhanced = true;
				break;
			case LIMIT_ORDER:
				orderType = CtpOcgApi.LIMIT;
				break;
			case STOP_ORDER:
				orderType = CtpOcgApi.STOP_MARKET;
				break;
			case MARKET_LIMIT_ORDER:
				orderType = CtpOcgApi.LIMIT_TO_MARKET;
				break;
			case MARKET_ORDER:
				orderType = CtpOcgApi.MARKET;
				break;
			case STOP_LIMIT_ORDER:
				orderType = CtpOcgApi.STOP_LIMIT;
				break;
			default:
				String message = String.format("Invalid order type %s", request.orderType());
				LOG.error(message);
				sendOrderRejected(activeBuffer,
						orderRejectedEncoder,
						request, 
						OrderRejectType.OTHER_INTERNALLY, 
						OrderSender.prepareRejectedReason(message, stringBuffer));
				return;
			}
			final int timeInForce;
			switch (request.tif()) {
			case FILL_AND_KILL:
				timeInForce = CtpOcgApi.IMMEDIATE_OR_CANCEL;
				break;
			case DAY:
				timeInForce = CtpOcgApi.GOOD_FOR_DAY;
				break;
			case GOOD_TILL_CANCEL:
				timeInForce = CtpOcgApi.GOOD_TILL_CANCELLED;
				break;
			case GOOD_TILL_DATE:
				timeInForce = CtpOcgApi.GOOD_TILL_DATE;
				break;
			default:
				String message = String.format("Invalid TIF %s", request.tif());
				LOG.error(message);
				sendOrderRejected(activeBuffer,
						orderRejectedEncoder,
						request, 
						OrderRejectType.OTHER_INTERNALLY, 
						OrderSender.prepareRejectedReason(message, stringBuffer));
				return;
			}
			if (this.api.invokeAddOrder(timerService.toNanoOfDay(),
			        request.orderSid(),
			        request.secSid(),
			        CtpUtil.convertSide(request.side()),
			        orderType, timeInForce, request.limitPrice(), request.quantity(), isEnhanced) != 0) {
			    sendOrderRejected(activeBuffer,
			            orderRejectedEncoder,
			            request, 
			            OrderRejectType.OTHER_INTERNALLY,
			            OrderSender.prepareRejectedReason("Cannot add order", stringBuffer));
			}
		} 
		catch (final Exception e) {
			LOG.error("Cannot add order.");
			sendOrderRejected(activeBuffer,
					orderRejectedEncoder,
					request, 
					OrderRejectType.OTHER_INTERNALLY,
					OrderSender.prepareRejectedReason("Cannot add order, caught exception", stringBuffer));
		}
	}
	
	private void handleCancelOrderRequest(MutableDirectBuffer messageBuffer, MutableDirectBuffer stringBuffer, CancelOrderRequest request){
		if (LOG.isTraceEnabled()){
			LOG.trace("handleCancelOrderRequest [clientKey:{}, ordSid:{}, ordSidToBeCancelled:{}]", request.clientKey(), request.orderSid(), request.ordSidToBeCancelled());
		}
		final int ordSidToBeCancelled = request.ordSidToBeCancelled();
		long nanoOfDay = timerService.toNanoOfDay();
		if (this.api.invokeCancelOrder(nanoOfDay,	String.valueOf(ordSidToBeCancelled), request.secSid(), (request.side() == Side.BUY ? CtpOcgApi.BUY : CtpOcgApi.SELL)) != 0) {
			sendOrderCancelRejected(activeBuffer,
					orderCancelRejectedEncoder, 
					request.orderSid(), 
					OrderStatus.NULL_VAL, request.secSid(), 
					OrderCancelRejectType.OTHER_INTERNALLY,
					OrderSender.ORDER_CANCEL_REJECTED_EMPTY_REASON,
					ExecutionType.CANCEL_REJECT,
					nanoOfDay);
		}
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
		this.lifecycleExceptionHandler = handler;
	}

	@Override
	public void sendOrderRequest(OrderRequest orderRequest) {
		switch (orderRequest.type()){
		case NEW:
			currentNewOrderRequestConsumer.accept(orderRequest.asNewOrderRequest());
			break;
		case CANCEL:
			currentCancelOrderRequestConsumer.accept(orderRequest.asCancelOrderRequest());
			break;
		default:
			throw new IllegalArgumentException("Unsupported order type [orderType:" + orderRequest.type().name() + "]");
		}		
	}

	@Override
	public void exceptionHandler(LineHandlerEngineExceptionHandler handler) {
		this.engineExceptionHandler = handler;
	}

	@Override
	public LineHandlerEngine init(OrderUpdateEventProducer producer) {
		LOG.info("Initialized line handler engine [name:{}]", this.name);
		this.ordUpdEventProducer = producer;
		this.init = true;
		return this;
	}

	CtpOcgLineHandlerEngine setOcgApi(CtpOcgApi api){
		this.api = api;
		return this;
	}
	
	private static final int CLIENT_KEY_FOR_RESET = 1;
	
	private void resetState(){
		channelSeq.set(0);
		api.apply(Command.of(ServiceConstant.SERVICE_ID_NOT_APPLICABLE, CLIENT_KEY_FOR_RESET, CommandType.LINE_HANDLER_ACTION, Parameters.listOf(Parameter.of(LineHandlerActionType.RESET))));
		LOG.info("Reset states successfully");
	}
	
	@Override
	public boolean isClear() {
		// TODO There is no straight forward way to determine if CtpOcApi has been cleared
	    return channelSeq.get() == 0;
	}

	@Override
	public int apply(Command command) {
		if (command.commandType() == CommandType.LINE_HANDLER_ACTION){
			if (command.parameters().isEmpty()){
				throw new IllegalArgumentException("Missing parameter");
			}
			if (controller.state() == LifecycleState.WARMUP){
				api.apply(command);
			}
		}
		else if (command.commandType() == CommandType.PRINT_ALL_ORDER_INFO_IN_LOG){
			api.invokeDisplayAllOrderInfo();
		}
		else if (command.commandType() == CommandType.PRINT_ORDER_INFO_IN_LOG){
			for (Parameter parameter : command.parameters()){
				if (parameter.type() == ParameterType.CLIENT_ORDER_ID){
					api.invokeDisplayOrderInfo(parameter.valueLong().intValue());
					return 0;
				}
			}
			LOG.error("Missing parameter for PRINT_ORDER_INFO_IN_LOG command [missing:PRINT_ORDER_INFO_IN_LOG]");
		}		
		return 0;
	}
	
	private void sendOrderAccepted(MutableDirectBuffer buffer,
			OrderAcceptedSbeEncoder encoder, 
			int orderSid, 
			OrderStatus status, 
			int price, 
			int cumulativeQty, 
			int leavesQty, 
			Side side, 
			int orderId, 
			long secSid, 
			ExecutionType execType, 
			long updateTime){
		OrderSender.encodeOrderAcceptedOnly(buffer, 
				0, 
				encoder, 
				channelId,
				channelSeq.getAndIncrement(),
				orderSid, 
				status, 
				price, 
				cumulativeQty, 
				leavesQty, 
				side, 
				orderId, 
				secSid, 
				execType,
				updateTime);
		ordUpdEventProducer.onOrderAccepted(buffer, 0);
	}

	private void sendTradeCreated(MutableDirectBuffer buffer,
			TradeCreatedSbeEncoder encoder,
			int orderSid, 
			int orderId, 
			long secSid, 
			Side side, 
			int leavesQty, 
			int cumulativeQty, 
			byte[] executionId, 
			int executionPrice, 
			int executionQty, 
			OrderStatus orderStatus, 
			long updateTime){
		OrderSender.encodeTradeCreatedOnly(buffer,
				0, 
				encoder, 
				channelId,
				channelSeq.getAndIncrement(),
				-1,//tradeSidSeq.getAndIncrement(),
				orderSid, 
				orderId,
				orderStatus, 
				side, 
				leavesQty,
				cumulativeQty,
				executionId,
				executionPrice, 
				executionQty, 
				secSid,
				updateTime);
		ordUpdEventProducer.onTradeCreated(buffer, 0);
	}

	private void sendOrderCancelled(MutableDirectBuffer buffer,
			OrderCancelledSbeEncoder encoder,
			int orderSid, 
			int orderSidBeingCancelled, 
			OrderStatus status, 
			int price, 
			Side side, 
			int orderId, 
			long secSid, 
			int leavesQty, 
			int cumulativeQty, 
			long updateTime){
		OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				encoder,
				channelId,
				channelSeq.getAndIncrement(),
				orderSid, 
				orderSidBeingCancelled, 
				status, 
				price, 
				side,
				orderId,
				secSid, 
				ExecutionType.CANCEL, 
				leavesQty, 
				cumulativeQty,
				updateTime,
				0 /* quantity not available from exchange */);
		ordUpdEventProducer.onOrderCancelled(buffer, 0);
	}

	private void sendOrderRejected(MutableDirectBuffer buffer,
			OrderRejectedSbeEncoder encoder,
			int orderSid, int orderId, 
			long secSid, 
			Side side, 
			int price, 
			int cumulativeQty, 
			int leavesQty, 
			OrderStatus status, 
			OrderRejectType rejectType,
			byte[] reason,
			long updateTime) {
		OrderSender.encodeOrderRejectedOnly(buffer, 
				0, 
				encoder, 
				channelId,
				channelSeq.getAndIncrement(),
				orderSid, 
				orderId,
				secSid,
				side,
				price,
				cumulativeQty,
				leavesQty,
				status,
				rejectType,
				reason,
				updateTime);
		ordUpdEventProducer.onOrderRejected(buffer, 0);
	}
	
	private void sendOrderRejected(MutableDirectBuffer buffer,
			OrderRejectedSbeEncoder encoder,
			NewOrderRequest request, 
			OrderRejectType rejectType, 
			byte[] reason){
		OrderSender.encodeOrderRejectedOnly(buffer, 
				0, 
				encoder, 
				channelId,
				channelSeq.getAndIncrement(),
				request.orderSid(),
				0,
				request.secSid(),
				request.side(),
				request.limitPrice(),
				0,
				0,
				OrderStatus.REJECTED,
				rejectType,
				reason,
				timerService.toNanoOfDay());
		ordUpdEventProducer.onOrderRejected(buffer, 0);
	}

	private void sendOrderCancelRejected(MutableDirectBuffer buffer,
			OrderCancelRejectedSbeEncoder encoder,
			int orderSid,
			OrderStatus status,
			long secSid,
			OrderCancelRejectType rejectType,
			byte[] reason,
			ExecutionType executionType,
			long updateTime){
		OrderSender.encodeOrderCancelRejectedOnly(buffer, 
				0, 
				encoder,
				channelId,
				channelSeq.getAndIncrement(),
				orderSid, 
				status, 
				secSid, 
				rejectType,
				reason,
				executionType,
				updateTime);
		ordUpdEventProducer.onOrderCancelRejected(buffer, 0);
	}

	private void sendOrderExpired(MutableDirectBuffer buffer,
			OrderExpiredSbeEncoder encoder,
			int orderSid, 
			int orderId, 
			long secSid, 
			Side side, 
			int price, 
			int cumulativeQty, 
			int leavesQty, 
			OrderStatus status, 
			long updateTime) {
		OrderSender.encodeOrderExpiredOnly(buffer, 
				0,
				encoder, 
				channelId,
				channelSeq.getAndIncrement(),
				orderSid, 
				orderId, 
				secSid, 
				side, 
				price, 
				cumulativeQty,
				leavesQty, 
				status,
				updateTime);
		ordUpdEventProducer.onOrderExpired(buffer, 0);
	}

	String connectorFile(){ return connectorFile; }	
	String configFile(){ return configFile;}
	String user(){ return user;}
	String account(){ return account;}

	private boolean connectAndRecoveryInProgress = false;
	private CompletableFuture<Boolean> recoveryAndConnectFuture = new CompletableFuture<Boolean>();
	
    @Override
    public CompletableFuture<Boolean> startRecovery() {
    	LOG.info("Start recovery [name:{}]", name);
        // If recovery is in progress, return existing future
        // If recovery is not in progress, create a new future and start recovery
        if (connectAndRecoveryInProgress){
            return recoveryAndConnectFuture;
        }
        
        connectAndRecoveryInProgress = true;
        
        // Connect
        // All updates will be sent back to OrderUpdateProcessor
        // Complete when endOfRecovery() is called
        int result = api.invokeSetMode(false);
        if (result != 0){
            LOG.error("Could not set ocg api mode to active [result:{}]", result);
            recoveryAndConnectFuture.complete(false);
            return recoveryAndConnectFuture;
        }
        final LocalDate localDate = systemClock.date();
        final int date = localDate.getYear() * 10000 + localDate.getMonthValue() * 100 + localDate.getDayOfMonth();
        LOG.info("Invoke OCG API, waiting... [name:{}]", name);
        result = api.invokeInitialize(connectorFile, configFile, user, account, date, ServiceConstant.START_ORDER_SID_SEQUENCE);
        if (result != 0){
            LOG.error("Could not initialize ocg api [result:{}]", result);
            recoveryAndConnectFuture.complete(false);
            return recoveryAndConnectFuture;
        }
        result = api.connect(systemClock.nanoOfDay());
        if (result != 0){
            LOG.error("Could not connect to ocg api [result:{}]", result);
            recoveryAndConnectFuture.complete(false);
            return recoveryAndConnectFuture;
        }
        LOG.info("End of start recovery");
        return recoveryAndConnectFuture;
    }

    boolean isConnected(){
        return isConnected;
    }
    
    boolean isRecovered(){
        return isRecovered;
    }
    
    boolean connectAndRecoveryInProgress(){
        return connectAndRecoveryInProgress;
    }

    @Override
    public void printOrderExecInfo(int clientOrderId){
    	api.invokeDisplayOrderInfo(clientOrderId);
    }

    @Override
    public void printAllOrderExecInfo(){
    	api.invokeDisplayAllOrderInfo();
    }

}
