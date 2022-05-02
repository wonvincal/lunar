package com.lunar.order;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Strings;
import com.lunar.core.LifecycleState;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.LifecycleController;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

abstract public class MatchingLineHandlerEngine implements LineHandlerEngine {
	private static final Logger LOG = LogManager.getLogger(MatchingLineHandlerEngine.class);
	private static final int START_ORDER_ID_SEQUENCE = 30000000;
	private static final int START_TRADE_SID_SEQUENCE = 40000000;
	private static final int START_EXECUTION_ID_SEQUENCE = 50000000;
	private static final int PARTIAL_FILLED_QTY = 100;
	private final String name;
	private boolean init =  false;
	private final MutableDirectBuffer buffer;
	private final MutableDirectBuffer stringBuffer;
	private OrderUpdateEventProducer ordUpdEventProducer;
	private final TimerService timerService;
	private MatchingEngine matchingEngine;
	private final int channelId = 1;
	private final AtomicLong channelSeq = new AtomicLong();
	private final AtomicInteger orderIdSeq = new AtomicInteger(START_ORDER_ID_SEQUENCE);
	private final AtomicInteger tradeSidSeq = new AtomicInteger(START_TRADE_SID_SEQUENCE);
	private final AtomicInteger executionIdSeq = new AtomicInteger(START_EXECUTION_ID_SEQUENCE);
	private final Int2ObjectOpenHashMap<Order> orders;
	private final Int2ObjectOpenHashMap<Trade> trades;
    private final SystemClock systemClock;	
	private Consumer<NewOrderRequest> currentNewOrderRequestConsumer;
	private Consumer<NewOrderRequest> noopNewOrderRequestConsumer = (o) -> { LOG.warn("Noop new order request consumer");};
	private Consumer<CancelOrderRequest> currentCancelOrderRequestConsumer;
	private Consumer<CancelOrderRequest> noopCancelOrderRequestConsumer = (o) -> { LOG.warn("Noop cancel order request consumer");};
	
	private final static String UNKNOWN_ORDER_REASON = "Unknown order";
	
	// Lifecycle related fields
	private final LifecycleController controller;
	@SuppressWarnings("unused")
	private LifecycleExceptionHandler lifecycleExceptionHandler = LifecycleExceptionHandler.DEFAULT_HANDLER;
	
	// New handlers
	private final Consumer<NewOrderRequest> acceptAndMatchWithEngine = (o) -> { acceptAndMatchWithEngine(o); };
	private final Consumer<NewOrderRequest> acceptOnNew = (o) -> { acceptOnNew(o); };
	private final Consumer<NewOrderRequest> rejectOnNew = (o) -> { rejectOnNew(o); };
	private final Consumer<NewOrderRequest> matchOnNew = (o) -> { matchOrder(o); };
	private final Consumer<NewOrderRequest> partialMatchOnNew = (o) -> { partialMatchOrder(o); };
	private final Consumer<NewOrderRequest> matchWithMultiFillsOnNew = (o) -> { matchOrderWithMultiFills(o); };
	private final Consumer<NewOrderRequest> partialMatchWithMultiFillsOnNew = (o) -> { partialMatchWithMultiFills(o); };
	
	// Cancel handlers
	private final Consumer<CancelOrderRequest> acceptOnCancel = (o) -> { acceptOnCancel(o); };
	private final Consumer<CancelOrderRequest> rejectOnCancel = (o) -> { rejectCancelOrder(o); };

	private final LifecycleStateHook lifecycleStateChangeHandler = new LifecycleStateHook() {
		@Override
		public CompletableFuture<Boolean> onPendingReset() {
			LOG.info("Reset completed [name:{}]", name);
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
		    MatchingEngineWarmup.warmup();
			currentNewOrderRequestConsumer = acceptOnNew;
			currentCancelOrderRequestConsumer = acceptOnCancel;
		}
		
		@Override
		public void onActiveEnter() {
			currentNewOrderRequestConsumer = acceptAndMatchWithEngine;
			currentCancelOrderRequestConsumer = noopCancelOrderRequestConsumer;
		};
		
		@Override
		public void onResetEnter() {
			currentNewOrderRequestConsumer = noopNewOrderRequestConsumer;
			currentCancelOrderRequestConsumer = noopCancelOrderRequestConsumer;
		};
	};
	
	@SuppressWarnings("unused")
	private LineHandlerEngineExceptionHandler exceptionHandler = LineHandlerEngineExceptionHandler.NULL_HANDLER;
	
	// Codec
	private OrderAcceptedSbeEncoder orderAcceptedEncoder = new OrderAcceptedSbeEncoder();
	private OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
	private OrderExpiredSbeEncoder orderExpiredEncoder = new OrderExpiredSbeEncoder();
	private OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
	private OrderCancelRejectedSbeEncoder cancelRejectedEncoder = new OrderCancelRejectedSbeEncoder();
	private TradeCreatedSbeEncoder tradeCreatedEncoder = new TradeCreatedSbeEncoder();

	MatchingLineHandlerEngine(String name, TimerService timerService, SystemClock systemClock){
		this.name = name;
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		this.timerService = timerService;
		this.systemClock = systemClock;
		this.orders = new Int2ObjectOpenHashMap<>();
		this.trades = new Int2ObjectOpenHashMap<>();
		this.controller = LifecycleController.of(this.name + "-lifecycle-controller", lifecycleStateChangeHandler);
		this.currentNewOrderRequestConsumer = noopNewOrderRequestConsumer;
		this.currentCancelOrderRequestConsumer = noopCancelOrderRequestConsumer;
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

	/**
	 * Accept order and match with underlying matching engine
	 * @param request
	 * @return
	 */
	private Order acceptAndMatchWithEngine(NewOrderRequest request){
	    LOG.trace("acceptAndMatchWithEngine [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
	    Order order = Order.of(request.secSid(), 
	            MessageSinkRef.NA_INSTANCE,
	            request.orderSid(), 
	            request.quantity(), 
	            BooleanType.FALSE, 
	            request.orderType(), 
	            request.side(),
	            request.limitPrice(), 
	            request.stopPrice(), 
	            request.tif(), 
	            OrderStatus.NEW, 
	            systemClock.nanoOfDay(),
	            OrderSbeEncoder.updateTimeNullValue(),
	            request.triggerInfo());
	    order.clientKey(request.clientKey());
	    orders.put(order.sid(), order);
	    ordUpdEventProducer.onOrderAccepted(buffer , orderAcceptedEncoder, channelId, channelSeq.getAndIncrement(), order);

	    try {
	        matchingEngine.addOrder(request.secSid(), order);
	    } 
	    catch (final Exception e) {
	        LOG.error("Cannot add order to matching engine...", e);
	        rejectOrder(buffer, stringBuffer, orderRejectedEncoder, order, OrderRejectType.OTHER_INTERNALLY, UNKNOWN_ORDER_REASON);
	    }
	    return order;
	}
	
	/**
	 * Warmup method - accept on new order
	 * @param request
	 * @return
	 */
	private Order acceptOnNew(NewOrderRequest request){
        LOG.trace("acceptOrder [clientKey:{}, ordSid:{}]", request.clientKey(), request.orderSid());
        Order order = Order.of(request.secSid(), 
                MessageSinkRef.NA_INSTANCE,
                request.orderSid(), 
                request.quantity(), 
                BooleanType.FALSE, 
                request.orderType(), 
                request.side(),
                request.limitPrice(), 
                request.stopPrice(), 
                request.tif(), 
                OrderStatus.NEW, 
                systemClock.nanoOfDay(),
                OrderSbeEncoder.updateTimeNullValue(),
                request.triggerInfo());
        order.clientKey(request.clientKey());
        orders.put(order.sid(), order);
        ordUpdEventProducer.onOrderAccepted(buffer , orderAcceptedEncoder, channelId, channelSeq.getAndIncrement(), order);        
        return order;
	}
	
	/**
	 * Warmup method - accept on new, follow by a series of partial filled, follow by expired
	 * @param orderRequest
	 */
	private void partialMatchOrder(NewOrderRequest orderRequest){
	    if (PARTIAL_FILLED_QTY >= orderRequest.quantity()){
	        throw new IllegalArgumentException("Cannot partial match, because partial filled quantity is greater than order quantity [partialFilledQty:" 
	                + PARTIAL_FILLED_QTY + ", orderQty:" + orderRequest.quantity());
	    }
	    Order order = acceptOnNew(orderRequest);
	    trade(order, order.limitPrice(), PARTIAL_FILLED_QTY);

	    // Make expired the remaining quantity
	    cancelOrder(order);
	}

	/**
	 * Warmup method - match order with multiple fills
	 * @param orderRequest
	 */
	private void matchOrderWithMultiFills(NewOrderRequest orderRequest){
	    Order order = acceptOnNew(orderRequest);

	    // Partial match to under 10 orders
	    while (order.cumulativeExecQty() + PARTIAL_FILLED_QTY < order.quantity()){
	        trade(order, order.limitPrice(), PARTIAL_FILLED_QTY);
	    }
	    trade(order, order.limitPrice(), order.leavesQty());
	}

	/**
	 * Warmup method - partial match order with multiple fills
	 * @param orderRequest
	 */
	private void partialMatchWithMultiFills(NewOrderRequest orderRequest){
	    if (PARTIAL_FILLED_QTY >= orderRequest.quantity()){
	        throw new IllegalArgumentException("Cannot partial match, because partial filled quantity is greater than order quantity [partialFilledQty:" 
	                + PARTIAL_FILLED_QTY + ", orderQty:" + orderRequest.quantity());
	    }
	    Order order = acceptOnNew(orderRequest);

	    // Partial match to under 10 orders
	    while (order.cumulativeExecQty() + PARTIAL_FILLED_QTY < order.quantity()){
	        trade(order, order.limitPrice(), PARTIAL_FILLED_QTY);
	    }
	    cancelOrder(order);
	}

	/**
	 * Warmup method - fully match order with one trade
	 * @param orderRequest
	 */
	private void matchOrder(NewOrderRequest orderRequest){
	    Order order = acceptOnNew(orderRequest);
	    trade(order, order.limitPrice(), order.quantity());     
	}
	
	/**
	 * Warmup method - make an order expired
	 * @param order
	 */
	@SuppressWarnings("unused")
	private void expireOrder(Order order){
	    order.status(OrderStatus.EXPIRED)
	    .leavesQty(0)
	    .updateTime(timerService.toNanoOfDay());
	    ordUpdEventProducer.onOrderExpired(buffer, orderExpiredEncoder, channelId, channelSeq.getAndIncrement(), order);
	}

	private void cancelOrder(Order order){
		order.status(OrderStatus.CANCELLED)
			.leavesQty(0)
			.updateTime(timerService.toNanoOfDay());

		ordUpdEventProducer.onOrderCancelled(buffer, orderCancelledEncoder, channelId, channelSeq.getAndIncrement(), order);
	}
	
	/**
	 * Warmpu method - reject on new method
	 * @param orderRequest
	 */
    private void rejectOnNew(NewOrderRequest orderRequest){
        Order order = Order.of(orderRequest.secSid(),
                MessageSinkRef.NA_INSTANCE,
                orderRequest.orderSid(),
                orderRequest.quantity(),
                BooleanType.FALSE, 
                orderRequest.orderType(), 
                orderRequest.side(),
                orderRequest.limitPrice(), 
                orderRequest.stopPrice(), 
                orderRequest.tif(), 
                OrderStatus.REJECTED,
                timerService.toNanoOfDay(),
                OrderSbeEncoder.updateTimeNullValue())
                .orderId(orderIdSeq.getAndIncrement())
                .leavesQty(0)
                .cumulativeExecQty(0)
                .clientKey(orderRequest.clientKey());
        rejectOrder(buffer, stringBuffer, orderRejectedEncoder, order, OrderRejectType.OTHER_INTERNALLY, "Reject on new");
    }
	
    /**
     * Warmup method - reject order
     * @param messageBuffer
     * @param stringBuffer
     * @param encoder
     * @param order
     * @param rejectType
     * @param reason
     */
	private void rejectOrder(MutableDirectBuffer messageBuffer, 
			MutableDirectBuffer stringBuffer,
			OrderRejectedSbeEncoder encoder,
			Order order,
			OrderRejectType rejectType, 
			String reason){
		order.status(OrderStatus.REJECTED)
			.leavesQty(0)
			.reason(reason)
			.updateTime(systemClock.nanoOfDay());

		ordUpdEventProducer.onOrderRejected(messageBuffer, stringBuffer, encoder, channelId, channelSeq.getAndIncrement(), order);
	}

	/**
	 * Warmup method - create trade
	 * @param order
	 * @param executionPrice
	 * @param executionQty
	 */
	private void trade(Order order, int executionPrice, int executionQty){
	    int actualExecQty = Math.min(order.leavesQty(), executionQty);
	    order.cumulativeExecQty(order.cumulativeExecQty() + actualExecQty);
	    order.leavesQty(order.leavesQty() - actualExecQty);
	    order.status((order.leavesQty() != 0) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED);

	    createAndSendTrade(buffer, stringBuffer, tradeCreatedEncoder, order, executionPrice, executionQty);
	}

	/**
	 * Warmup method - accept on cancel
	 * @param orderRequest
	 * @return
	 */
	private boolean acceptOnCancel(CancelOrderRequest orderRequest){
	    int ordSidToBeCancelled = orderRequest.ordSidToBeCancelled();
	    Order order = orders.remove(ordSidToBeCancelled);
	    if (order != orders.defaultReturnValue()){
	        // LOG.debug("Attempt to cancel order - cancelled [ordSidToBeCancelled:{}]", ordSidToBeCancelled);
	        order.status(OrderStatus.CANCELLED)
	        .leavesQty(0)
	        .updateTime(timerService.toNanoOfDay());
	        ordUpdEventProducer.onOrderCancelled(buffer, orderCancelledEncoder, channelId, channelSeq.getAndIncrement(), order);
	        return true;
	    }
	    LOG.error("Could not find the order to be cancelled [ordSidToBeCancelled:{}]", ordSidToBeCancelled);
	    return false;
	}

	/**
	 * Warmup method - reject a cancel order
	 * @param orderRequest
	 * @return
	 */
	private boolean rejectCancelOrder(CancelOrderRequest orderRequest){
	    int ordSidToBeCancelled = orderRequest.ordSidToBeCancelled();
	    LOG.debug("Attempt to reject cancel order [ordSidToBeCancelled:{}]", ordSidToBeCancelled);

	    Order order = orders.get(ordSidToBeCancelled);
	    if (order != orders.defaultReturnValue()){
	        ordUpdEventProducer.onCancelRejected(buffer, 
	                stringBuffer, 
	                cancelRejectedEncoder,
	                channelId,
	                channelSeq.getAndIncrement(), 
	                orderRequest,
	                OrderCancelRejectType.OTHER_INTERNALLY, 
	                "Reject on cancel", 
	                timerService.toNanoOfDay(),
	                order.status());
	    }
	    else {
	        // Reject because order not exist
	        ordUpdEventProducer.onCancelRejected(buffer, 
	                stringBuffer, 
	                cancelRejectedEncoder, 
	                channelId, 
	                channelSeq.getAndIncrement(), 
	                orderRequest, 
	                OrderCancelRejectType.UNKNOWN_ORDER, 
	                "Reject on cancel", 
	                timerService.toNanoOfDay(), 
	                OrderStatus.NULL_VAL);
	    }
	    return true;
	}

	/**
	 * Do not modify leavesQty and updateTime, matching engine has already handled those fields
	 * @param messageBuffer
	 * @param encoder
	 * @param order
	 */
	protected void sendOrderExpired(MutableDirectBuffer messageBuffer, 
			OrderExpiredSbeEncoder encoder,
			Order order){
		ordUpdEventProducer.onOrderExpired(messageBuffer, encoder, channelId, channelSeq.getAndIncrement(), order);
	}

    /**
     * Do not modify leavesQty and updateTime, matching engine has already handled those fields
     * @param messageBuffer
     * @param encoder
     * @param order
     */
    protected void sendOrderCancelled(MutableDirectBuffer messageBuffer, 
            OrderCancelledSbeEncoder encoder,
            Order order){
        ordUpdEventProducer.onOrderCancelled(messageBuffer, encoder, channelId, channelSeq.getAndIncrement(), order);
    }
    
	/**
	 * Do not modify order cumulativeQty and leavesQty; matching engine has taken care of those fields 
	 * @param messageBuffer
	 * @param stringBuffer
	 * @param encoder
	 * @param order
	 * @param executionPrice
	 * @param executionQty
	 */
	protected void createAndSendTrade(MutableDirectBuffer messageBuffer, 
			MutableDirectBuffer stringBuffer,
			TradeCreatedSbeEncoder encoder, 
			Order order, 
			int executionPrice, 
			int executionQty){
		int tradeSid = tradeSidSeq.getAndIncrement();
		String executionId = Strings.padStart(Integer.toString(executionIdSeq.getAndIncrement()), TradeCreatedSbeEncoder.executionIdLength(), '0');
		Trade trade = Trade.of(tradeSid, 
				order.sid(), 
				order.orderId(), 
				order.secSid(), 
				order.side(), 
				order.leavesQty(),
				order.cumulativeExecQty(), 
				executionId,
				executionPrice, 
				executionQty, 
				order.status(), 
				TradeStatus.NEW,
				order.updateTime(), 
				ServiceConstant.NULL_TIME_NS);
		trades.put(trade.sid(), trade);
		ordUpdEventProducer.onTradeCreate(messageBuffer, stringBuffer, encoder, channelId, channelSeq.getAndIncrement(), order, trade);
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

	private void resetState(){
		channelSeq.set(0);
		orderIdSeq.set(START_ORDER_ID_SEQUENCE);
		tradeSidSeq.set(START_TRADE_SID_SEQUENCE);
		executionIdSeq.set(START_EXECUTION_ID_SEQUENCE);
		orders.clear();
		trades.clear();
		LOG.info("Reset states successfully");
	}

	@Override
	public void exceptionHandler(LineHandlerEngineExceptionHandler handler) {
		this.exceptionHandler = handler;
	}

	@Override
	public LineHandlerEngine init(OrderUpdateEventProducer producer) {
		LOG.info("Initialized line handler engine [name:{}]", this.name);
		this.ordUpdEventProducer = producer;
		this.init = true;
		return this;
	}

	@Override
	public int apply(Command command) {
	    boolean found = false;
		LineHandlerActionType actionType = LineHandlerActionType.NULL_VAL;
		if (command.commandType() == CommandType.LINE_HANDLER_ACTION){
			if (command.parameters().isEmpty()){
				throw new IllegalArgumentException("Missing parameter");
			}
			if (controller.state() == LifecycleState.WARMUP){
			    for (Parameter parameter : command.parameters()){
			        if (parameter.type() == ParameterType.LINE_HANDLER_ACTION_TYPE){
			            found = true;
			            actionType = LineHandlerActionType.get(parameter.valueLong().byteValue());
			            switch (actionType){
			            case CHANGE_MODE_ACCEPT_ON_NEW:
			                currentNewOrderRequestConsumer = acceptOnNew;
			                break;
			            case CHANGE_MODE_REJECT_ON_NEW:
			                currentNewOrderRequestConsumer = rejectOnNew;
			                break;
			            case CHANGE_MODE_MATCH_ON_NEW:
			                currentNewOrderRequestConsumer = matchOnNew;
			                break;
			            case CHANGE_MODE_MATCH_WITH_MULTI_FILLS_ON_NEW:
			                currentNewOrderRequestConsumer = matchWithMultiFillsOnNew;
			                break;
			            case CHANGE_MODE_ACCEPT_ON_CANCEL:
			                currentCancelOrderRequestConsumer = acceptOnCancel;
			                break;
			            case CHANGE_MODE_REJECT_ON_CANCEL:
			                currentCancelOrderRequestConsumer = rejectOnCancel;
			                break;
			            case CHANGE_MODE_PARTIAL_MATCH_ON_NEW:
			                currentNewOrderRequestConsumer = partialMatchOnNew;
			                break;
			            case CHANGE_MODE_PARTIAL_MATCH_WITH_MULTI_FILLS_ON_NEW:
			                currentNewOrderRequestConsumer = partialMatchWithMultiFillsOnNew;
			                break;
			            case RESET:
			                resetState();
			                break;
			            default:
			                throw new IllegalArgumentException("Unsupported action type [lineHandlerActionType:" + actionType.name() + "]");
			            }
			            break;
			        }               
			    }
            }
			else {
                for (Parameter parameter : command.parameters()){
                    if (parameter.type() == ParameterType.LINE_HANDLER_ACTION_TYPE){
                        actionType = LineHandlerActionType.get(parameter.valueLong().byteValue());
                        if (actionType == LineHandlerActionType.RESET){
                            found = true;
                            resetState();
                            break;
                        }
                    }
                }			    
			}
        }
		else if (command.commandType() == CommandType.PRINT_ALL_ORDER_INFO_IN_LOG){
			printAllOrderExecInfo();
			found = true;
		}
		else if (command.commandType() == CommandType.PRINT_ORDER_INFO_IN_LOG){
			for (Parameter parameter : command.parameters()){
				if (parameter.type() == ParameterType.CLIENT_ORDER_ID){
					printOrderExecInfo(parameter.valueLong().intValue());
					found = true;
					break;
				}
			}
		}
		if (found){
		    LOG.info("Applied action successfully [actionType:{}]", actionType.name());
		}
		else {
		    LOG.info("Did not apply unknown command successfully [actionType:{}]", actionType.name());
		}
		return 0;
	}

	protected void initializeMatchingEngine(final MatchingEngine matchingEngine) {
	    this.matchingEngine = matchingEngine;
	}

	Int2ObjectOpenHashMap<Order> orders(){
	    return orders;
	}

    @Override
    public boolean isClear() {
        return orders.isEmpty() && trades.isEmpty() && orderIdSeq.get() == START_ORDER_ID_SEQUENCE && tradeSidSeq.get() == START_TRADE_SID_SEQUENCE && executionIdSeq.get() == START_EXECUTION_ID_SEQUENCE;
    }
    
    @Override
    public CompletableFuture<Boolean> startRecovery() {
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
        ordUpdEventProducer.onEndOfRecovery();
        return future;
    }
    
    @Override
    public void printOrderExecInfo(int clientOrderId){
    	LOG.info("Print order exec info [clientOrderId:{}]", clientOrderId);
    }

    @Override
    public void printAllOrderExecInfo(){
    	LOG.info("Print all order exec info");
    }

}
