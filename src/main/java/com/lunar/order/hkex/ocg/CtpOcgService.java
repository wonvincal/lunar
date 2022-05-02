package com.lunar.order.hkex.ocg;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.lunar.config.ExchangeServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Request;
import com.lunar.message.ResponseHandler;
import com.lunar.message.binary.MessageReceiver;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.sender.OrderSender;
import com.lunar.order.ExchangeService;
import com.lunar.order.LineHandlerEngineOrderUpdateListener;
import com.lunar.service.ServiceConstant;
import com.lunar.service.ServiceLifecycleAware;

/**
 * All order request to be sent to thru its disruptor queue
 * 
 * To subscribe to order and trade updates:
 * 1) send subscription request to its disruptor queue
 * 2) directly register a LineHandlerUpdateListener
 * 
 * @author wongca
 *
 */
public class CtpOcgService implements ServiceLifecycleAware, ExchangeService {
	static final Logger LOG = LogManager.getLogger(CtpOcgService.class);
	private LunarService messageService;
	private Messenger messenger;
	private MessageReceiver receiver;
	private final String name;
	private final AtomicInteger tradeSidSeq;
	private LineHandlerEngineOrderUpdateListener updateListener = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;
	private volatile boolean isStopped;
	private final int channelId = 1;
	private final AtomicLong channelSeq = new AtomicLong();
	private final MutableDirectBuffer buffer;
	private final MutableDirectBuffer orderRequestStringBuffer;
	private final String connectorFile;	
	private final String configFile;
	private final String user;
	private final String account;
    private volatile boolean isRecovery;
	
	private CtpOcgApi api;
	
	public static CtpOcgService of(ServiceConfig config, LunarService messageService){
		return new CtpOcgService(config, messageService);
	}

	CtpOcgService(ServiceConfig config, LunarService messageService){
		this.name = config.name();
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.receiver = this.messenger.receiver();
		this.tradeSidSeq = new AtomicInteger(800000);
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.orderRequestStringBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.updateListener = LineHandlerEngineOrderUpdateListener.NULL_LISTENER;
		this.isStopped = true;
		
		if (config instanceof ExchangeServiceConfig) {
			ExchangeServiceConfig specificConfig = (ExchangeServiceConfig)config;
			this.connectorFile = specificConfig.connectorFile().get();
			this.configFile = specificConfig.configFile().get();
			this.user = specificConfig.user().get();
			this.account = specificConfig.account().get();
		}
        else{
            throw new IllegalArgumentException("Service " + this.name + " expects a CtpOcgServiceConfig config");
        }
		
		final Messenger childMessenger = messenger.createChildMessenger();
		final Command startCommand = Command.of(childMessenger.self().sinkId(), 0, CommandType.START, new ArrayList<Parameter>(), BooleanType.TRUE);
		final Command stopCommand = Command.of(childMessenger.self().sinkId(), 0, CommandType.STOP, new ArrayList<Parameter>(), BooleanType.TRUE);
		this.api = new CtpOcgApi(new CtpOcgApi.CallbackHandler() {			
			@Override
			public int onSessionState(int threadId, int sessionState) {
				if (sessionState == CtpOcgApi.CONNECTED) {
				    LOG.info("OCG session connected...");
				    startCommand.clientKey(childMessenger.getNextClientKeyAndIncrement());
				    childMessenger.commandSender().sendCommand(childMessenger.self(), startCommand);
					isStopped = false;
				}
				else if (sessionState == CtpOcgApi.DISCONNECTED) {
				    LOG.info("OCG session disconnected...");
                    startCommand.clientKey(childMessenger.getNextClientKeyAndIncrement());
                    childMessenger.commandSender().sendCommand(childMessenger.self(), stopCommand);				    
					isStopped = true;
				}
				return 0;
			}
			
			@Override
			public int onExecutionReport(int threadId, int clientOrderId, String exchangeOrderId, long dateTime, int execTypeCode,
					int execTransTypeCode, char orderStatusCode, long secSid, int buySellCode, int orderPrice, int orderQuantity, int orderTotalTradeQuantity,
					int leavesQuantity, String executionId, int tradeQuantity, int tradePrice, String errorText, boolean inSyncFlag) {
			    //TODO shayan
			    if (isRecovery) {
			        LOG.info("Is a recovery message, ignore for now...");
			        return 0;
			    }
		        LOG.info("Received execution report clientOrderId: {}, exchangeOrderId: {}, dateTime: {}, execTypeCode: {}, execTransTypeCode: {}, orderStatusCode: {}, secSid: {}, buySellCode: {}, orderTotalTradeQuantity: {}, leavesQuantity: {}, executionId: {}, tradeQuantity: {}, tradePrice: {}, errorText: {}, inSyncFlag {}",
		                clientOrderId, exchangeOrderId, dateTime, execTypeCode,
	                    execTransTypeCode, orderStatusCode, secSid, buySellCode, orderTotalTradeQuantity,
	                    leavesQuantity, executionId, tradeQuantity, tradePrice, errorText, inSyncFlag);
			    try {
    			    final Side side;
    			    switch (buySellCode) {
    			    case CtpOcgApi.BUY:
    			        side = Side.BUY;
    			        break;
    			    case CtpOcgApi.SELL:
    			        side = Side.SELL;
    			        break;
    			    default:
    			        side = Side.NULL_VAL;
    			        break;
    			    }
    				switch (orderStatusCode) {
    				case CtpOcgApi.NEW:
    					sendOrderAccepted(clientOrderId,
    							OrderStatus.NEW,
    							tradePrice,
    							orderTotalTradeQuantity,
    							leavesQuantity,
    							side,
    							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
    							secSid,
    							ExecutionType.NULL_VAL,
    							messenger.timerService().toNanoOfDay());
    					break;
    				case CtpOcgApi.PARTIALLY_FILLED:
    				{
    					final int orderId = exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId);
//    					final int tradeId = executionId == null ? 0 : Integer.parseInt(executionId);
    					final long updateTime = messenger.timerService().toNanoOfDay();
    					sendTradeCreated(clientOrderId,
    									orderId,
    									secSid,
    									side,
    									leavesQuantity,
    									orderTotalTradeQuantity, 
    									executionId,
    									tradePrice, 
    									tradeQuantity,
    									OrderStatus.PARTIALLY_FILLED,
    									updateTime);
    					break;
    					}
    				case CtpOcgApi.FILLED:
    				{
    					final int orderId = exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId);					
    					//final int tradeId = executionId == null ? 0 : Integer.parseInt(executionId);
//    					final int tradeId = orderId;
    					final long updateTime = messenger.timerService().toNanoOfDay();
    					sendTradeCreated(clientOrderId,
    									orderId,
    									secSid,
    									side,
    									leavesQuantity,
    									orderTotalTradeQuantity, 
    									executionId,
    									tradePrice, 
    									tradeQuantity,
    									OrderStatus.FILLED,
    									updateTime);
    					
    					break;
    				}
    				case CtpOcgApi.DONE_FOR_DAY:
    					sendOrderExpired(clientOrderId,
    							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
                                secSid,
                                side,
    							tradePrice,
    							orderTotalTradeQuantity,
    							leavesQuantity,
    							OrderStatus.EXPIRED,
    							messenger.timerService().toNanoOfDay());
    					break;
    				case CtpOcgApi.CANCELED:
    				{
    					sendOrderCancelled(clientOrderId,
    					        clientOrderId,
    							OrderStatus.CANCELLED,
    							tradePrice,
    							side,
    							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
    							secSid,
    							leavesQuantity,
    							orderTotalTradeQuantity,
    							messenger.timerService().toNanoOfDay());
    					break;
    				}
    				case CtpOcgApi.STOPPED:
    					sendOrderRejected(clientOrderId,
    							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
    							secSid,
    							side,
    							tradePrice,
    							orderTotalTradeQuantity,
    							leavesQuantity,
    							OrderStatus.REJECTED,
    							OrderRejectType.OTHER,
    							OrderSender.prepareRejectedReason(errorText, messenger.stringBuffer()),
    							messenger.timerService().toNanoOfDay());
    					break;
    				case CtpOcgApi.REJECTED:
    					sendOrderRejected(clientOrderId,
    							exchangeOrderId == null ? 0 : Integer.parseInt(exchangeOrderId),
                                secSid,
                                side,
    							tradePrice,
    							orderTotalTradeQuantity,
    							leavesQuantity,
    							OrderStatus.REJECTED,
    							OrderRejectType.OTHER,
    							OrderSender.prepareRejectedReason(errorText, messenger.stringBuffer()),
    							messenger.timerService().toNanoOfDay());
    					break;
    				}
    					
    				return 0;
			    }
			    catch (final Exception e) {
                    LOG.error("Error handling execution report", e);
                    return 0;
			    }
			}

            @Override
            public int onEndRecovery(int threadId) {
                isRecovery = false;
                LOG.info("End recovery received...");
                return 0;
            }
		}, this.messenger.timerService());
	}
	
	@Override
	public boolean isStopped() {
		return isStopped;
	};
	
	LunarService messageService(){
		return messageService;
	}
	
	public void updateListener(LineHandlerEngineOrderUpdateListener updateListener){
		this.updateListener = updateListener;
	}
	
	public String name(){
		return name;
	}
	
	@Override
    public StateTransitionEvent idleStart() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackServiceType(ServiceType.RefDataService);
        messenger.serviceStatusTracker().trackAggregatedServiceStatus((final boolean status) -> {
            if (status){
                messageService.stateEvent(StateTransitionEvent.READY);
            }
            else { // DOWN or INITIALIZING
                messageService.stateEvent(StateTransitionEvent.WAIT);
            }   
        });
        return StateTransitionEvent.WAIT;
    }

	@Override
	public StateTransitionEvent idleRecover() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent warmupEnter() {
		return StateTransitionEvent.WAIT;
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
        receiver.commandHandlerList().add(this::handleCommand);
        receiver.securityHandlerList().add(this::handleSecurity);
        CompletableFuture<Request> retrieveSecurityFuture = messenger.sendRequest(messenger.referenceManager().rds(),
                RequestType.SUBSCRIBE,
                new ImmutableList.Builder<Parameter>().add(Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.SECURITY.value()), Parameter.of(ParameterType.EXCHANGE_CODE, ServiceConstant.PRIMARY_EXCHANGE)).build(),
                ResponseHandler.NULL_HANDLER);
        retrieveSecurityFuture.thenAccept((r) -> {
            final LocalDate localDate = this.messageService.systemClock().date();
            final int date = localDate.getYear() * 10000 + localDate.getMonthValue() * 100 + localDate.getDayOfMonth();
            this.api.invokeInitialize(connectorFile, configFile, user, account, date, ServiceConstant.START_ORDER_SID_SEQUENCE);
            this.api.invokeSetMode(false);
            this.isRecovery = true;
            this.api.connect(this.messageService.systemClock().nanoOfDay());
            this.isStopped = false;
        });
		return StateTransitionEvent.NULL;	
	}

	@Override
	public void readyExit() {
	}

	@Override
	public StateTransitionEvent activeEnter() {
		this.isStopped = false;
		receiver.newOrderRequestHandlerList().add(this::handleNewOrderRequest);
		receiver.cancelOrderRequestHandlerList().add(this::handleCancelOrderRequest);
		return StateTransitionEvent.NULL;	
	}

	@Override
	public void activeExit() {
        receiver.securityHandlerList().remove(this::handleSecurity);
		receiver.newOrderRequestHandlerList().remove(this::handleNewOrderRequest);
		receiver.cancelOrderRequestHandlerList().remove(this::handleCancelOrderRequest);
		receiver.commandHandlerList().remove(this::handleCommand);
		this.api.invokeClose();
	}

    private void handleSecurity(final DirectBuffer buffer, final int offset, MessageHeaderDecoder header, final SecuritySbeDecoder security){
        //final byte[] bytes = new byte[SecuritySbeDecoder.codeLength()];
        //security.getCode(bytes, 0);
        //try {
		//	this.api.invokeRegisterSecurity(security.sid(), new String(bytes, SecuritySbeDecoder.codeCharacterEncoding()).trim());
		//} catch (UnsupportedEncodingException e) {
        //    LOG.error("Caught exception", e);
        //    this.messageService.stateEvent(StateTransitionEvent.FAIL);
        //    return;
        //}
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
		this.isStopped = true;
		return StateTransitionEvent.NULL;	
	}

	@Override
	public void stoppedExit() {
	}
	
	private OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
	private OrderRejectedSbeDecoder orderRejectedDecoder = new OrderRejectedSbeDecoder();
	
	private void sendOrderRejected(NewOrderRequestSbeDecoder request, OrderRejectType rejectType, byte[] reason){
		OrderSender.encodeOrderRejectedOnly(buffer, 
				0, 
				orderRejectedEncoder, 
				channelId,
				channelSeq.getAndIncrement(),
				request.clientKey(),
				0,
				request.secSid(),
				request.side(),
				request.limitPrice(),
				0,
				0,
				OrderStatus.REJECTED,
				rejectType,
				reason,
				messenger.timerService().toNanoOfDay());
		orderRejectedDecoder.wrap(buffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);
		
		// Send update to listener
		updateListener.receiveRejected(buffer, 0, orderRejectedDecoder);
	}
	
	private void sendOrderRejected(int orderSid, int orderId, long secSid, Side side, int price, int cumulativeQty, int leavesQty, OrderStatus status, 
			OrderRejectType rejectType,
			byte[] reason,
			long updateTime) {
		OrderSender.encodeOrderRejectedOnly(buffer, 
				0, 
				orderRejectedEncoder, 
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
		orderRejectedDecoder.wrap(buffer, 0, OrderRejectedSbeDecoder.BLOCK_LENGTH, OrderRejectedSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateListener.receiveRejected(buffer, 0, orderRejectedDecoder);
		
	}
	
	private OrderAcceptedSbeEncoder orderAcceptedEncoder = new OrderAcceptedSbeEncoder();
	private OrderAcceptedSbeDecoder orderAcceptedDecoder = new OrderAcceptedSbeDecoder();

	private void sendOrderAccepted(int orderSid, OrderStatus status, int price, int cumulativeQty, int leavesQty, Side side, int orderId, long secSid, ExecutionType execType, long updateTime){
		OrderSender.encodeOrderAcceptedOnly(buffer, 
				0, 
				orderAcceptedEncoder, 
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
		orderAcceptedDecoder.wrap(buffer, 0, OrderAcceptedSbeDecoder.BLOCK_LENGTH, OrderAcceptedSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateListener.receiveAccepted(buffer, 0, orderAcceptedDecoder);
	}
	
	private OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
	private OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();
	private void sendOrderCancelled(int orderSid, int orderSidBeingCancelled, OrderStatus status, int price, Side side, int orderId, long secSid, int leavesQty, int cumulativeQty, long updateTime){
		OrderSender.encodeOrderCancelledOnly(buffer, 
				0, 
				orderCancelledEncoder,
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
				0 /* quantity not avail from exchange */);
		orderCancelledDecoder.wrap(buffer, 0, OrderCancelledSbeDecoder.BLOCK_LENGTH, OrderCancelledSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateListener.receiveCancelled(buffer, 0, orderCancelledDecoder);
	}
	
	private OrderExpiredSbeEncoder orderExpiredEncoder = new OrderExpiredSbeEncoder();
	private OrderExpiredSbeDecoder orderExpiredDecoder = new OrderExpiredSbeDecoder();

	private void sendOrderExpired(int orderSid, int orderId, long secSid, Side side, int price, int cumulativeQty, int leavesQty, OrderStatus status, long updateTime) {
		OrderSender.encodeOrderExpiredOnly(buffer, 
				0,
				orderExpiredEncoder, 
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
		orderExpiredDecoder.wrap(buffer, 0, OrderExpiredSbeDecoder.BLOCK_LENGTH, OrderExpiredSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateListener.receiveExpired(buffer, 0, orderExpiredDecoder);
	}
	
	
	private void handleNewOrderRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder request) {
			LOG.info("handleNewOrderRequest [clientKey:{}]", request.clientKey());
			// create an order
			try {
			    boolean isEnhanced = false;
				final int orderType;
				switch (request.orderType()) {
				case STOP_ORDER:
					orderType = CtpOcgApi.STOP_MARKET;
					break;
				case LIMIT_ORDER:
					orderType = CtpOcgApi.LIMIT;
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
				case ENHANCED_LIMIT_ORDER:
				    orderType = CtpOcgApi.LIMIT;
				    isEnhanced = true;
				    break;
				default:
					String message = String.format("Invalid order type %s", request.orderType());
				    LOG.info(message);
				    this.sendOrderRejected(request, 
				    		OrderRejectType.OTHER_INTERNALLY, 
				    		OrderSender.prepareRejectedReason(message, orderRequestStringBuffer));
				    return;
				}
				final int timeInForce;
				switch (request.tif()) {
				case DAY:
					timeInForce = CtpOcgApi.GOOD_FOR_DAY;
					break;
				case FILL_AND_KILL:
					timeInForce = CtpOcgApi.IMMEDIATE_OR_CANCEL;
					break;
				case GOOD_TILL_CANCEL:
					timeInForce = CtpOcgApi.GOOD_TILL_CANCELLED;
					break;
				case GOOD_TILL_DATE:
					timeInForce = CtpOcgApi.GOOD_TILL_DATE;
					break;
				default:
					String message = String.format("Invalid TIF %s", request.tif());
					LOG.info(message);
                    this.sendOrderRejected(request, 
                    		OrderRejectType.OTHER_INTERNALLY,
                    		OrderSender.prepareRejectedReason(message, orderRequestStringBuffer));
                    return;
				}
				if (this.api.invokeAddOrder(this.messageService.systemClock().nanoOfDay(),
						request.clientKey(),
						request.secSid(),
						request.side() == Side.BUY ? CtpOcgApi.BUY : CtpOcgApi.SELL,
						orderType, timeInForce, request.limitPrice(), request.quantity(), isEnhanced) != 0) {
					this.sendOrderRejected(request, 
							OrderRejectType.OTHER_INTERNALLY,
							OrderSender.prepareRejectedReason("Cannot add order", orderRequestStringBuffer));
				}
			} catch (final Exception e) {
				LOG.error("Cannot add order...");
				sendOrderRejected(request, 
						OrderRejectType.OTHER_INTERNALLY,
						OrderSender.prepareRejectedReason("Cannot add order, caught exception", orderRequestStringBuffer));
			}			
	}

	private OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder = new OrderCancelRejectedSbeEncoder();
	private OrderCancelRejectedSbeDecoder orderCancelRejectedDecoder = new OrderCancelRejectedSbeDecoder();
	
	private void sendOrderCancelRejected(int orderSid,
			OrderStatus status,
			long secSid,
			OrderCancelRejectType rejectType,
			byte[] reason,
			ExecutionType executionType,
			long updateTime){
		OrderSender.encodeOrderCancelRejectedOnly(buffer, 
				0, 
				orderCancelRejectedEncoder,
				channelId,
				channelSeq.getAndIncrement(),
				orderSid, 
				status, 
				secSid, 
				rejectType,
				reason,
				executionType,
				updateTime);
		orderCancelRejectedDecoder.wrap(buffer, 0, OrderCancelRejectedSbeDecoder.BLOCK_LENGTH, OrderCancelRejectedSbeDecoder.SCHEMA_VERSION);

		// Send update to listener
		updateListener.receiveCancelRejected(buffer, 0, orderCancelRejectedDecoder);
	}
	
	private void handleCancelOrderRequest(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder request) {
		final int orderSid = request.orderSidToBeCancelled();
		long nanoOfDay = this.messageService.systemClock().nanoOfDay();
		if (this.api.invokeCancelOrder(nanoOfDay, String.valueOf(orderSid), request.secSid(), (request.side() == Side.BUY ? CtpOcgApi.BUY : CtpOcgApi.SELL)) != 0) {
			sendOrderCancelRejected(orderSid, 
					OrderStatus.NULL_VAL, request.secSid(), 
					OrderCancelRejectType.OTHER_INTERNALLY,
					OrderSender.ORDER_CANCEL_REJECTED_EMPTY_REASON,
					ExecutionType.CANCEL_REJECT,
					nanoOfDay);
		}
	}

	private TradeCreatedSbeEncoder tradeCreatedEncoder = new TradeCreatedSbeEncoder();
	private TradeCreatedSbeDecoder tradeCreatedDecoder = new TradeCreatedSbeDecoder();
	private void sendTradeCreated(int orderSid, int orderId, long secSid, Side side, int leavesQty, int cumulativeQty, String executionId, int executionPrice, int executionQty, OrderStatus orderStatus, long updateTime){
		int tradeSid = tradeSidSeq.getAndIncrement();
		OrderSender.encodeTradeCreatedOnly(buffer,
				0, 
				tradeCreatedEncoder, 
				channelId,
				channelSeq.getAndIncrement(),
				tradeSid,
				orderSid, 
				orderId,
				orderStatus, 
				side, 
				leavesQty,
				cumulativeQty,
				OrderSender.prepareExecutionId(executionId, messenger.stringBuffer()),
				executionPrice, 
				executionQty, 
				secSid,
				updateTime);
		tradeCreatedDecoder.wrap(buffer, 0, TradeSbeEncoder.BLOCK_LENGTH, TradeSbeEncoder.SCHEMA_VERSION);
		this.updateListener.receiveTradeCreated(buffer, 0, tradeCreatedDecoder);
	}
	
    private void handleCommand(final DirectBuffer buffer, final int offset, final MessageHeaderDecoder header, final CommandSbeDecoder codec) {
        try {
            handleCommand(header.senderSinkId(), codec);
        }
        catch (final Exception e) {
            LOG.error("Failed to handle command", e);
            messenger.sendCommandAck(header.senderSinkId(), codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
        }
    }
    
    private void handleCommand(final int senderSinkId, final CommandSbeDecoder codec) throws Exception {
        if (senderSinkId != messenger.self().sinkId()) {
            LOG.info("Only self can send commands to CtpOcgService!");
        }
        switch (codec.commandType()){
        case START: {
            LOG.info("Received START command");
            messageService.stateEvent(StateTransitionEvent.ACTIVATE);
            break;
        }
        case STOP: {
            LOG.info("Received STOP command");
            if (messageService.state() == States.ACTIVE) {
                messageService.stateEvent(StateTransitionEvent.STOP);
            }
            break;
        }
        default:
            messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
            break;
        }
    }    
}
