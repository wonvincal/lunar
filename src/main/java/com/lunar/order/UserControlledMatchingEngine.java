package com.lunar.order;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.base.Strings;
import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.OrderCancelRejectType;
import com.lunar.message.io.sbe.OrderRejectType;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class UserControlledMatchingEngine {
	private static final Logger LOG = LogManager.getLogger(UserControlledMatchingEngine.class);

	private static final int START_ORDER_ID_SEQUENCE = 30000000;
	private static final int START_TRADE_SID_SEQUENCE = 40000000;
	private static final int START_EXECUTION_ID_SEQUENCE = 50000000;
	private static final int PARTIAL_FILLED_QUANTITY = 100;
	private final String name;

	private final AtomicInteger orderIdSeq = new AtomicInteger(START_ORDER_ID_SEQUENCE);
	private final AtomicInteger tradeSidSeq = new AtomicInteger(START_TRADE_SID_SEQUENCE);
	private final AtomicInteger executionIdSeq = new AtomicInteger(START_EXECUTION_ID_SEQUENCE);
	private final TimerService timerService;
	private final Int2ObjectOpenHashMap<Order> orders;
	private final Int2ObjectOpenHashMap<Trade> trades;

	private MatchingEngineOrderUpdateHandler orderUpdateHandler;
	private Consumer<NewOrderRequest> currentNewOrderRequestConsumer;
	private Consumer<CancelOrderRequest> currentCancelOrderRequestConsumer;

	// New handlers
	private final Consumer<NewOrderRequest> acceptOnNew = (o) -> { acceptOnNew(o); };
	private final Consumer<NewOrderRequest> rejectOnNew = (o) -> { rejectOnNew(o); };
	private final Consumer<NewOrderRequest> matchOnNew = (o) -> { matchOrder(o); };
	private final Consumer<NewOrderRequest> partialMatchOnNew = (o) -> { partialMatchOrder(o); };
	private final Consumer<NewOrderRequest> matchWithMultiFillsOnNew = (o) -> { matchOrderWithMultiFills(o); };
	private final Consumer<NewOrderRequest> partialMatchWithMultiFillsOnNew = (o) -> { partialMatchWithMultiFills(o); };
	
	// Cancel handlers
	private final Consumer<CancelOrderRequest> acceptOnCancel = (o) -> { acceptOnCancel(o); };
	private final Consumer<CancelOrderRequest> rejectOnCancel = (o) -> { rejectCancelOrder(o); };

	public static UserControlledMatchingEngine of(String name, TimerService timerService){
	    return new UserControlledMatchingEngine(name, timerService);
	}
	
	UserControlledMatchingEngine(String name, TimerService timerService){
		this.name = name;
		this.timerService = timerService;
		this.orders = new Int2ObjectOpenHashMap<>();
		this.trades = new Int2ObjectOpenHashMap<>();
		currentNewOrderRequestConsumer = acceptOnNew;
		currentCancelOrderRequestConsumer = acceptOnCancel;
	}

	public String name() {
		return name;
	}
	
	public void init(MatchingEngineOrderUpdateHandler handler){
		this.orderUpdateHandler = handler;
	}

	private Order acceptOnNew(NewOrderRequest request){
//		LOG.info("About to accept order [orderSid:{}]", orderRequest.orderSid());
		// This gets ugly
		// This request comes from OMES - same object, where
		// NewOrderRequest.clientKey() is the individual clientKey
		// NewOrderRequest.orderSid() is the OMES key
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
				timerService.toNanoOfDay(),
				OrderSbeEncoder.updateTimeNullValue(),
				request.triggerInfo())
			.orderId(orderIdSeq.getAndIncrement())
			.leavesQty(request.quantity())
			.cumulativeExecQty(0);
		orders.put(order.sid(), order);
		
//		LOG.debug("Send order accepted [clientKey:{}, orderSid:{}]", orderRequest.clientKey(), orderRequest.orderSid());

		orderUpdateHandler.onOrderAccepted(order);
		return order;
	}
	
	private void partialMatchOrder(NewOrderRequest orderRequest){
		if (PARTIAL_FILLED_QUANTITY >= orderRequest.quantity()){
			throw new IllegalArgumentException("Cannot partial match, because partial filled quantity is greater than order quantity [partialFilledQty:" 
					+ PARTIAL_FILLED_QUANTITY + ", orderQty:" + orderRequest.quantity() + "]");
		}
		Order order = acceptOnNew(orderRequest);
		trade(order, order.limitPrice(), PARTIAL_FILLED_QUANTITY);
		
		// Make expired the remaining quantity
		cancelOrder(order);
	}
	
	private void matchOrderWithMultiFills(NewOrderRequest orderRequest){
		Order order = acceptOnNew(orderRequest);
		
		// Partial match to under 10 orders
		while (order.cumulativeExecQty() + PARTIAL_FILLED_QUANTITY < order.quantity()){
			trade(order, order.limitPrice(), PARTIAL_FILLED_QUANTITY);
		}
		trade(order, order.limitPrice(), order.leavesQty());
	}

	private void partialMatchWithMultiFills(NewOrderRequest orderRequest){
		if (PARTIAL_FILLED_QUANTITY >= orderRequest.quantity()){
			throw new IllegalArgumentException("Cannot partial match, because partial filled quantity is greater than order quantity [partialFilledQty:" 
					+ PARTIAL_FILLED_QUANTITY + ", orderQty:" + orderRequest.quantity());
		}
		Order order = acceptOnNew(orderRequest);
		
		// Partial match to under 10 orders
		while (order.cumulativeExecQty() + PARTIAL_FILLED_QUANTITY < order.quantity()){
			trade(order, order.limitPrice(), PARTIAL_FILLED_QUANTITY);
		}
		cancelOrder(order);
	}
	
	/**
	 * Fully match all quantity in one shot
	 * @param orderRequest
	 */
	private void matchOrder(NewOrderRequest orderRequest){
		Order order = acceptOnNew(orderRequest);
		trade(order, order.limitPrice(), order.quantity());		
	}
	
	@SuppressWarnings("unused")
	private void expireOrder(Order order){
		order.status(OrderStatus.EXPIRED)
			.leavesQty(0)
			.updateTime(timerService.toNanoOfDay());
		orderUpdateHandler.onOrderExpired(order);
	}
	
	private void cancelOrder(Order order){
		order.status(OrderStatus.CANCELLED)
			.leavesQty(0)
			.updateTime(timerService.toNanoOfDay());
		orderUpdateHandler.onOrderCancelled(order);
	}

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
				.cumulativeExecQty(0);
		order.orderRejectType(OrderRejectType.OTHER_INTERNALLY);
		order.reason("Reject on new");

		// No need to put order into map
		LOG.trace("Send order rejected [clientKey:{}, orderSid:{}]", orderRequest.clientKey(), orderRequest.orderSid());

		orderUpdateHandler.onOrderRejected(order);
	}
	
	private void trade(Order order, int executionPrice, int executionQty){
		int actualExecQty = Math.min(order.leavesQty(), executionQty);
		order.cumulativeExecQty(order.cumulativeExecQty() + actualExecQty);
		order.leavesQty(order.leavesQty() - actualExecQty);
		order.status((order.leavesQty() != 0) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED);
		order.updateTime(timerService.toNanoOfDay());
		
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
		orderUpdateHandler.onTradeCreated(order, trade);
	}
	
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


	private boolean acceptOnCancel(CancelOrderRequest orderRequest){
		int ordSidToBeCancelled = orderRequest.ordSidToBeCancelled();
//		LOG.debug("Attempt to cancel order [ordSidToBeCancelled:{}]", ordSidToBeCancelled);
		Order order = orders.remove(ordSidToBeCancelled);
		if (order != orders.defaultReturnValue()){
//			LOG.debug("Attempt to cancel order - cancelled [ordSidToBeCancelled:{}]", ordSidToBeCancelled);
			order.status(OrderStatus.CANCELLED)
			.leavesQty(0)
			.updateTime(timerService.toNanoOfDay());			
			orderUpdateHandler.onOrderCancelled(orderRequest, order);
			return true;
		}
		LOG.error("Could not find the order to be cancelled [ordSidToBeCancelled:{}]", ordSidToBeCancelled);
		return false;
	}
	
	private boolean rejectCancelOrder(CancelOrderRequest orderRequest){
		int ordSidToBeCancelled = orderRequest.ordSidToBeCancelled();
		LOG.debug("Attempt to reject cancel order [ordSidToBeCancelled:{}]", ordSidToBeCancelled);
		
		Order order = orders.get(ordSidToBeCancelled);
		if (order != orders.defaultReturnValue()){
			orderUpdateHandler.onCancelRejected(orderRequest, OrderCancelRejectType.OTHER_INTERNALLY, "Reject on cancel", order);
		}
		else {
			// Reject because order not exist
			orderUpdateHandler.onCancelRejected(orderRequest, OrderCancelRejectType.UNKNOWN_ORDER, "Unknown order", order);
		}
		return true;
	}
	
	@SuppressWarnings("unused")
    private void sendMassOrderCancelled(MassCancelOrderRequest orderRequest){
		throw new UnsupportedOperationException("MassOrderCancelled is not supported");
	}
	
	private void resetState(){
		orderIdSeq.set(START_ORDER_ID_SEQUENCE);
		tradeSidSeq.set(START_TRADE_SID_SEQUENCE);
		executionIdSeq.set(START_EXECUTION_ID_SEQUENCE);
		orders.clear();
		trades.clear();
		LOG.info("Reset states successfully");
	}
	
	public int apply(Command command) {
		boolean found = false;
		LineHandlerActionType actionType = LineHandlerActionType.NULL_VAL;
		if (command.commandType() == CommandType.LINE_HANDLER_ACTION){
			if (command.parameters().isEmpty()){
				throw new IllegalArgumentException("Missing parameter");
			}
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
				}				
			}
		}
		if (!found){
			throw new IllegalArgumentException("Invalid parameters");
		}
		LOG.debug("Applied action successfully [actionType:{}]", actionType.name());
		return 0;
	}
	
	Int2ObjectOpenHashMap<Order> orders(){
		return orders;
	}

	public boolean isClear() {
        return orders.isEmpty() && trades.isEmpty() && orderIdSeq.get() == START_ORDER_ID_SEQUENCE && tradeSidSeq.get() == START_TRADE_SID_SEQUENCE && executionIdSeq.get() == START_EXECUTION_ID_SEQUENCE;
    }
	
	public void endOfRecovery(){
		orderUpdateHandler.onEndOfRecovery();
	}
}
