package com.lunar.service;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ImmutableList;
import com.lunar.config.ClientServiceConfig;
import com.lunar.config.ServiceConfig;
import com.lunar.entity.Security;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceStatusType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.order.OrderRequest;
import com.lunar.order.Trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Support different operations for testing
 * @author wongca
 *
 */
public class DummyClientService implements ServiceLifecycleAware {
	static final Logger LOG = LogManager.getLogger(DummyClientService.class);
	private LunarService messageService;
	private Messenger messenger;
	private final int exchangeSinkId;
	
	private boolean isAdminUp;
	private boolean isExchangeUp;
	private boolean isOMESUp;
	
	/**
	 * Hard-coding stuff
	 * This should really be based on the reference in Security class
	 */
	private final Security security;
	private final MessageSinkRef omes;
	private final int ownPortSid = 1000;
	private MessageSinkRef exchange;
	private final byte[] byteBuffer = new byte[128];

	public static DummyClientService of(ServiceConfig config, LunarService messageService){
		return new DummyClientService(config, messageService);
	}
	
	DummyClientService(ServiceConfig config, LunarService messageService){
		this.messageService = messageService;
		this.messenger = this.messageService.messenger();
		this.omes = this.messenger.referenceManager().omes();
		this.security = Security.of(1234567, SecurityType.STOCK, "61512.HK", 1, false, SpreadTableBuilder.get(SecurityType.STOCK));
		this.isAdminUp = false;
		this.isExchangeUp = false;
		this.isOMESUp = false;
		
		if (config instanceof ClientServiceConfig){
			ClientServiceConfig specificConfig = (ClientServiceConfig)config;
			this.exchangeSinkId = specificConfig.exchangeSinkId();
		}
		else {
			throw new IllegalArgumentException("Expected config of type DummyClientServiceConfig");
		}
	}

	void messenger(Messenger messenger){
		this.messenger = messenger;
	}
	
	@Override
	public StateTransitionEvent idleStart() {
		messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService, this::handleAdminServiceStatusChange);
		messenger.serviceStatusTracker().trackServiceType(ServiceType.OrderManagementAndExecutionService, this::handleOMESStatusChange);
		messenger.serviceStatusTracker().trackSinkId(exchangeSinkId, this::handleExchangeServiceStatusChange);
		return StateTransitionEvent.NULL;
	}

	private void handleOMESStatusChange(ServiceStatus status){
		if (status.statusType() == ServiceStatusType.UP){
			this.isOMESUp = true;
		}
		else{
			this.isOMESUp = false;
		}
		this.exchange = this.messenger.referenceManager().get(exchangeSinkId);
		activateOrWait();
	}
	
	private void handleExchangeServiceStatusChange(ServiceStatus status){
		if (status.statusType() == ServiceStatusType.UP){
			this.isExchangeUp = true;
		}
		else{
			this.isExchangeUp = false;
		}
		this.exchange = this.messenger.referenceManager().get(exchangeSinkId);
		activateOrWait();
	}
	
	private void handleAdminServiceStatusChange(ServiceStatus status){
		if (status.statusType() == ServiceStatusType.UP){
			this.isAdminUp = true;
		}
		else { // DOWN or INITIALIZING
			this.isAdminUp = false;
		}
		activateOrWait();
	}
	
	private void activateOrWait(){
		if (this.isAdminUp && this.isExchangeUp && this.isOMESUp){
			messageService.stateEvent(StateTransitionEvent.ACTIVATE);
		}
		else{
			messageService.stateEvent(StateTransitionEvent.WAIT);			
		}
	}

	@Override
	public StateTransitionEvent idleRecover() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void idleExit() {
	}

	@Override
	public StateTransitionEvent waitingForWarmupServicesEnter() {
		return StateTransitionEvent.NULL;
	}

	@Override
	public void waitingForWarmupServicesExit() {
	}

	@Override
	public StateTransitionEvent warmupEnter() {
		return StateTransitionEvent.READY;
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
		return StateTransitionEvent.NULL;	
	}

	@Override
	public void readyExit() {
	}

	/*
	public static class Task {
		private final Runnable method;
		Task(Runnable method){
			this.method = method;
		}
		public void start(){
			method.run();
		}
	}
	*/
	
	@Override
	public StateTransitionEvent activeEnter() {
		messenger.registerEventsForOrderTracker();
	
		// A list of tasks to do
		// 1) send new orders and make sure we get all accepted
		// newOrderAndAcceptedStart();
		// 2) send new orders and make sure we get all rejected
		// newOrderAndRejectedStart();
		// 3) send new orders and make sure we get all accepted, then follow by expired
		// 4) send cancel orders and make sure we get all cancel rejected
		// cancelOrderAndRejectedStart();
		// 5) send cancel orders and make sure we get all cancelled
		// cancelOrderStart();
		// 6) we want to test unsolicited cancelled
		// 7) send new orders and make sure we get all accepted, and get trades
		// newOrderThenAcceptedThenFilledStart();
		// 8) cancel all trades
		// 9) clear all
		// clearAllOrdersAndTrades();
		return StateTransitionEvent.NULL;
	}
	
	// ===================================
	// newOrderThenAcceptedThenFilledStart
	// ===================================
	private Int2ObjectOpenHashMap<Trade> trades = new Int2ObjectOpenHashMap<Trade>(64, 0.8f);

	private CompletableFuture<Boolean> newOrderThenAcceptedThenFilledFuture;
	private int newOrderThenAcceptedThenFilledOrderFilledCount;
	@SuppressWarnings("unused")
	private CompletableFuture<Boolean> newOrderThenAcceptedThenFilledStart(){
		reset();
		this.newOrderThenAcceptedThenFilledOrderFilledCount = 0;
		messenger.receiver().tradeCreatedHandlerList().add(this::newOrderThenAcceptedThenFilledTradeHandler);
		this.newOrderThenAcceptedThenFilledFuture = new CompletableFuture<Boolean>();
		// create new orders
		CompletableFuture<Boolean> future = createOrderStart();
		// send command to change mode to CANCEL
		future.whenComplete(this::newOrderThenAcceptedThenFilledSendCommand);
		return this.newOrderThenAcceptedThenFilledFuture;
	}
	
	private void newOrderThenAcceptedThenFilledSendCommand(Boolean result, Throwable t){
		if (t != null){
			LOG.error("caught exception", t);
		}
		LOG.info("result value: {}", result.booleanValue());
		if (t == null && result.booleanValue()){
			LOG.info("sending command to exchange");
			reset();
			this.expectedSuccessfulCount = this.orders.size();

			// Then change mode of exchange
			CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
					Command.of(exchangeSinkId, 
							messenger.getNextClientKeyAndIncrement(), 
							CommandType.LINE_HANDLER_ACTION, 
							new ImmutableList.Builder<Parameter>().add(
									Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.FILL_ALL_LIVE_ORDERS.value())).build()));
			commandResultFuture.whenComplete(this::newOrderThenAcceptedThenFilledSendCommandCompletion);
		}
		else{
			LOG.info("not sending command to exchange");
			newOrderThenAcceptedThenFilledComplete(false);
		}
	}
	
	private void newOrderThenAcceptedThenFilledSendCommandCompletion(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		LOG.info("Got FILL_ALL_LIVE_ORDERS completion back");
	}

	private void newOrderThenAcceptedThenFilledTradeHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder trade) {
		receivedOrderUpdateCount++;
		Trade t = Trade.of(trade.tradeSid(), 
				trade.orderSid(), 
				trade.orderId(),
				trade.secSid(),
				trade.side(),
				trade.leavesQty(),
				trade.cumulativeQty(),
				new String(byteBuffer, 0, trade.getExecutionId(byteBuffer, 0)),
				trade.executionPrice(), 
				trade.executionQty(),
				trade.status(),
				TradeStatus.NEW,
				messenger.timerService().toNanoOfDay(),
				ServiceConstant.NULL_TIME_NS);
		trades.put(t.sid(), t);
		if (orders.remove(trade.orderSid()) == orders.defaultReturnValue()){
			throw new IllegalStateException("weird....");
		}
		newOrderThenAcceptedThenFilledCheckComplete();
	}

	private void newOrderThenAcceptedThenFilledCheckComplete(){
		// Make sure the right number of orders have been cancelled
		LOG.info("receivedOrderUpdateCount:{}, expectedSuccessfulCount:{}, orders.size():{}, receivedFilled:{}", 
				receivedOrderUpdateCount,
				expectedSuccessfulCount,
				orders.size(),
				this.newOrderThenAcceptedThenFilledOrderFilledCount);
		boolean result = (receivedOrderUpdateCount == expectedSuccessfulCount) && 
				(orders.size() == 0) &&
				(this.newOrderThenAcceptedThenFilledOrderFilledCount == expectedSuccessfulCount) &&
				(this.trades.size() == expectedSuccessfulCount);
		if (result){
			newOrderThenAcceptedThenFilledComplete(result);
		}
	}
	
	private void newOrderThenAcceptedThenFilledComplete(boolean status){
		messenger.receiver().tradeCreatedHandlerList().remove(this::newOrderThenAcceptedThenFilledTradeHandler);
		if (status){
			LOG.info("newOrderThenAcceptedThenFilled completed successfully [numOrdersRemaining:{}]", orders.size());
		}
		else{
			LOG.warn("newOrderThenAcceptedThenFilled failed [numOrdersRemaining:{}]", orders.size());
		}
		this.newOrderThenAcceptedThenFilledFuture.complete(status);
	}
	
	private void reset(){
		this.expectedSuccessfulCount = 0;
		this.receivedOrderRequestCompletionCount = 0;
		this.receivedOrderUpdateCount = 0;		
		this.trades.clear();
	}
	
	// ====================================
	// newOrderThenAcceptedThenExpiredStart
	// ====================================
	
	private CompletableFuture<Boolean> newOrderThenAcceptedThenExpiredFuture;
	@SuppressWarnings("unused")
	private CompletableFuture<Boolean> newOrderThenAcceptedThenExpiredStart(){
		reset();
		this.newOrderThenAcceptedThenExpiredFuture = new CompletableFuture<Boolean>();
		return this.newOrderThenAcceptedThenExpiredFuture;
	}
	
	// ==================
	// cancelOrder
	// ==================

	private CompletableFuture<Boolean> cancelOrderFuture;
	
	@SuppressWarnings("unused")
	private CompletableFuture<Boolean> cancelOrderStart(){
		reset();
		messenger.receiver().orderCancelledHandlerList().add(this::cancelOrderOrderCancelledHandler);
		
		this.cancelOrderFuture = new CompletableFuture<Boolean>();
		// create new orders
		CompletableFuture<Boolean> future = createOrderStart();
		// send command to change mode to CANCEL
		future.whenComplete(this::cancelOrderSendCommand);
		// send cancel order request
		return this.cancelOrderFuture;
	}
	
	private void cancelOrderSendCommand(Boolean result, Throwable t){
		if (result.booleanValue()){
			this.expectedSuccessfulCount = this.orders.size();
			this.receivedOrderRequestCompletionCount = 0;
			this.receivedOrderUpdateCount = 0;

			// Then change mode of exchange
			CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
					Command.of(exchangeSinkId, 
							messenger.getNextClientKeyAndIncrement(), 
							CommandType.LINE_HANDLER_ACTION, 
							new ImmutableList.Builder<Parameter>().add(
									Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL.value())).build()));
			commandResultFuture.whenComplete(this::cancelOrderSendCommandCompletion);
		}
		else{
			this.cancelOrderFuture.complete(false);
		}
	}
	
	private void cancelOrderSendCommandCompletion(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		LOG.info("Got cancel_on_cancel completion back");
		for (Entry<Order> order : this.orders.int2ObjectEntrySet()){
			cancelOrderSendRequest(order.getValue());
		}
	}
	
	private void cancelOrderSendRequest(Order order){
		CancelOrderRequest request = CancelOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				order.sid(),
				security.sid(),
				Side.NULL_VAL);
		CompletableFuture<OrderRequest> future = messenger.sendCancelOrder(omes, request);
		future.whenComplete(this::cancelOrderCancelOrderCompletionHandler);
	}
	
	private void cancelOrderCancelOrderCompletionHandler(OrderRequest orderRequest, Throwable t){
		if (t != null){
			LOG.error("Got exception for order request [{}]", t);
			return;
		}
		receivedOrderRequestCompletionCount++;
		cancelOrderCompleted();
	}
	
	private void cancelOrderOrderCancelledHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder cancelled) {
		receivedOrderUpdateCount++;
		orders.remove(cancelled.origOrderSid());
		cancelOrderCompleted();
	}

	private void cancelOrderCompleted(){
		if (receivedOrderUpdateCount == expectedSuccessfulCount && receivedOrderRequestCompletionCount == expectedSuccessfulCount){
			messenger.receiver().orderCancelledHandlerList().remove(this::cancelOrderOrderCancelledHandler);
			// Make sure the right number of orders have been cancelled
			if (orders.size() == 0){
				LOG.info("cancelOrder completed successfully [numOrdersRemaining:{}]", orders.size());
				this.cancelOrderFuture.complete(true);
			}
			else{
				LOG.warn("cancelOrder failed [numOrdersRemaining:{}]", orders.size());
				this.cancelOrderFuture.complete(false);
			}
		}
	}
	
	// ==================
	// clearAll
	// ==================
	@SuppressWarnings("unused")
	private void clearAllOrdersAndTrades(){
		// Then change mode of exchange
		CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
				Command.of(exchangeSinkId, 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.LINE_HANDLER_ACTION, 
				new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.RESET.value())).build()));
		commandResultFuture.whenComplete(this::clearAllOrdersAndTradesCompletion);
	}
	
	private void clearAllOrdersAndTradesCompletion(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		LOG.info("Test clearAllOrdersAndTrades completed successfully");
	}
	
	private CompletableFuture<Boolean> createOrderFuture;
	private CompletableFuture<Boolean> createOrderStart(){
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(this::createOrderOrderAcceptedWithOrderInfoHandler);
		
		this.expectedSuccessfulCount = 0;
		this.receivedOrderRequestCompletionCount = 0;
		this.receivedOrderUpdateCount = 0;
		
		this.expectedSuccessfulCount = 12;
		long expectedPrice = 650;
		long expectedQuantity = 1000;
		long requiredPurchasingPower = expectedSuccessfulCount * expectedPrice * expectedQuantity;
		
		// Make sure we have enough purchasing power in OMES
		CompletableFuture<Request> requestFuture = messenger.sendRequest(omes, RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, requiredPurchasingPower)));
		requestFuture.whenComplete(this::createOrderUpdateResponse);
		
		this.createOrderFuture = new CompletableFuture<Boolean>();
		return this.createOrderFuture;
	}
	
	private void createOrderUpdateResponse(Request request, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result from omes [{}]", t);
			return;
		}
		// Then change mode of exchange
		CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
				Command.of(exchangeSinkId, 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.LINE_HANDLER_ACTION, 
				new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW.value())).build()));
		
		commandResultFuture.whenComplete(this::createOrderCommandAck);
	}
	
	private void createOrderCommandAck(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		for (int i = 0; i < this.expectedSuccessfulCount; i++){
			createOrderSendRequest();
		}
	}
	
	private void createOrderSendRequest(){
		int quantity = 1000;
		int limitPrice = 650;
		int stopPrice = 650;
		
		NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				security, 
				OrderType.LIMIT_ORDER, 
				quantity, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				limitPrice, 
				stopPrice, 
				ownPortSid);
		CompletableFuture<OrderRequest> future = messenger.sendNewOrder(omes, request);
		future.whenComplete(this::createOrderNewOrderCompletionHandler);
	}
	
	private void createOrderNewOrderCompletionHandler(OrderRequest orderRequest, Throwable t){
		if (t != null){
			LOG.error("Got exception for order request [{}]", t);
			return;
		}
		receivedOrderRequestCompletionCount++;
		createOrderCompleted();
	}
	
	private Int2ObjectOpenHashMap<Order> orders = new Int2ObjectOpenHashMap<Order>(64, 0.8f);
	private void createOrderOrderAcceptedWithOrderInfoHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder accepted) {
		receivedOrderUpdateCount++;
		long createTime = this.messageService.systemClock().nanoOfDay();
		Order order = Order.of(accepted.secSid(),
				this.messenger.self(),
				accepted.orderSid(),
				accepted.leavesQty(),
				accepted.isAlgoOrder(),
				accepted.orderType(),
				accepted.side(),
				accepted.price(),
				accepted.price(),
				accepted.tif(),
				accepted.status(),
				createTime,
				createTime);
		orders.put(order.sid(), order);
		createOrderCompleted();
	}

	private void createOrderCompleted(){
		LOG.info("receivedOrderAcceptedCount:{}, receivedOrderRequestCompletionCount:{}, numOrders:{}", receivedOrderUpdateCount, receivedOrderRequestCompletionCount, orders.size());
		if (receivedOrderUpdateCount == expectedSuccessfulCount && receivedOrderRequestCompletionCount == expectedSuccessfulCount){
			messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(this::createOrderOrderAcceptedWithOrderInfoHandler);
			// Make sure the right number of orders have been created
			if (orders.size() == this.expectedSuccessfulCount){
				LOG.info("createOrder completed successfully [numOrdersCreated:{}]", orders.size());
				this.createOrderFuture.complete(true);
			}
			else{
				LOG.warn("createOrder failed [numOrdersCreated:{}]", orders.size());
				this.createOrderFuture.complete(false);
			}
		}
	}
	
	// ===================
	// newOrderAndAccepted
	// ===================
	private int expectedSuccessfulCount;
	private int receivedOrderRequestCompletionCount = 0;
	private int receivedOrderUpdateCount = 0;
	
	@SuppressWarnings("unused")
	private void newOrderAndAcceptedStart(){
		messenger.receiver().orderAcceptedHandlerList().add(this::newOrderAndAcceptedOrderAcceptedHandler);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(this::newOrderAndAcceptedOrderAcceptedWithOrderInfoHandler);

		// What do I need to know after I send out a request?
		// 1) Whether the request was received by OMES successfully
		// 2) That's the only thing, basically
		// 3) a) Timeout
		this.expectedSuccessfulCount = 24;
		long expectedPrice = 650;
		long expectedQuantity = 1000;
		long requiredPurchasingPower = expectedSuccessfulCount * expectedPrice * expectedQuantity;
		
		// Make sure we have enough purchasing power in OMES
		CompletableFuture<Request> requestFuture = messenger.sendRequest(omes, RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, requiredPurchasingPower)));
		requestFuture.whenComplete(this::newOrderAndAcceptedUpdateResponse);
	}
	
	private void newOrderAndAcceptedUpdateResponse(Request request, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result from omes [{}]", t);
			return;
		}
		// Then change mode of exchange
		CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
				Command.of(exchangeSinkId, 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.LINE_HANDLER_ACTION, 
				new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW.value())).build()));
		
		commandResultFuture.whenComplete(this::newOrderAndAcceptedCommandAck);
	}
	
	private void newOrderAndAcceptedCommandAck(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		for (int i = 0; i < this.expectedSuccessfulCount; i++){
			newOrderAndAcceptedSendRequest();
		}
	}
	
	private void newOrderAndAcceptedSendRequest(){
		int quantity = 1000;
		int limitPrice = 650;
		int stopPrice = 650;
		
		NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				security, 
				OrderType.LIMIT_ORDER, 
				quantity, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				limitPrice, 
				stopPrice, 
				ownPortSid);
		CompletableFuture<OrderRequest> future = messenger.sendNewOrder(omes, request);
		future.whenComplete(this::newOrderAndAcceptedNewOrderCompletionHandler);		
	}
	
	private void newOrderAndAcceptedNewOrderCompletionHandler(OrderRequest orderRequest, Throwable t){
		if (t != null){
			LOG.error("Got exception for order request [{}]", t);
			return;
		}
		receivedOrderRequestCompletionCount++;
		newOrderAndAcceptedCompleted();
	}
	
	private void newOrderAndAcceptedCompleted(){
		if (receivedOrderUpdateCount == expectedSuccessfulCount && receivedOrderRequestCompletionCount == expectedSuccessfulCount){
			LOG.info("Test newOrderAndAccepted completed successfully");
			messenger.receiver().orderAcceptedHandlerList().remove(this::newOrderAndAcceptedOrderAcceptedHandler);
			messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(this::newOrderAndAcceptedOrderAcceptedWithOrderInfoHandler);
		}
	}
	
	private void newOrderAndAcceptedOrderAcceptedHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder accepted) {
		receivedOrderUpdateCount++;
		newOrderAndAcceptedCompleted();
		LOG.info("Received order accepted. [ordSid:{}]", accepted.orderSid());
	}

	private void newOrderAndAcceptedOrderAcceptedWithOrderInfoHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder accepted) {
		receivedOrderUpdateCount++;
		newOrderAndAcceptedCompleted();
		LOG.info("Received order accepted with order info. [ordSid:{}]", accepted.orderSid());
	}

	// ===================
	// newOrderAndRejected
	// ===================
	private int expectedNewOrderCountForNewOrderAndRejected;
	private int receivedOrderRequestCompletionCountForNewOrderAndRejected = 0;
	private int receivedOrderRejectedCountForNewOrderAndRejected = 0;
	
	@SuppressWarnings("unused")
	private void newOrderAndRejectedStart(){
		messenger.receiver().orderRejectedHandlerList().add(this::newOrderAndRejectedOrderRejectedHandler);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(this::newOrderAndRejectedOrderRejectedWithOrderInfoHandler);

		// What do I need to know after I send out a request?
		// 1) Whether the request was received by OMES successfully
		// 2) That's the only thing, basically
		// 3) a) Timeout
		this.expectedNewOrderCountForNewOrderAndRejected = 24;
		long expectedPrice = 650;
		long expectedQuantity = 1000;
		long requiredPurchasingPower = expectedNewOrderCountForNewOrderAndRejected * expectedPrice * expectedQuantity;
		
		// Make sure we have enough purchasing power in OMES
		CompletableFuture<Request> requestFuture = messenger.sendRequest(omes, RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, requiredPurchasingPower)));
		requestFuture.whenComplete(this::newOrderAndRejectedUpdateResponse);
	}
	
	private void newOrderAndRejectedUpdateResponse(Request request, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result from omes [{}]", t);
			return;
		}
		// Then change mode of exchange
		CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
				Command.of(exchangeSinkId, 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.LINE_HANDLER_ACTION, 
				new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_REJECT_ON_NEW.value())).build()));
		
		commandResultFuture.whenComplete(this::newOrderAndRejectedCommandAck);
	}
	
	private void newOrderAndRejectedCommandAck(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		LOG.info("Got command ack");
		for (int i = 0; i < this.expectedNewOrderCountForNewOrderAndRejected; i++){
			newOrderAndRejectedSendRequest();
		}
	}
	
	private void newOrderAndRejectedSendRequest(){
		int quantity = 1000;
		int limitPrice = 650;
		int stopPrice = 650;
		
		LOG.info("Send message");
		NewOrderRequest request = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				security, 
				OrderType.LIMIT_ORDER, 
				quantity, 
				Side.BUY, 
				TimeInForce.DAY, 
				BooleanType.FALSE, 
				limitPrice, 
				stopPrice, 
				ownPortSid);
		CompletableFuture<OrderRequest> future = messenger.sendNewOrder(omes, request);
		future.whenComplete(this::newOrderAndRejectedNewOrderCompletionHandler);
	}
	
	private void newOrderAndRejectedNewOrderCompletionHandler(OrderRequest orderRequest, Throwable t){
		if (t != null){
			LOG.error("Got exception for order request [{}]", t);
			return;
		}
		LOG.info("Got order request completion");
		receivedOrderRequestCompletionCountForNewOrderAndRejected++;
		newOrderAndRejectedCompleted();
	}
	
	private void newOrderAndRejectedOrderRejectedHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder accepted) {
		receivedOrderRejectedCountForNewOrderAndRejected++;
		newOrderAndRejectedCompleted();
		LOG.info("Received order rejected. [ordSid:{}]", accepted.orderSid());
	}
	
	private void newOrderAndRejectedOrderRejectedWithOrderInfoHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder accepted) {
		receivedOrderRejectedCountForNewOrderAndRejected++;
		newOrderAndRejectedCompleted();
		LOG.info("Received order rejected. [ordSid:{}]", accepted.orderSid());
	}

	private void newOrderAndRejectedCompleted(){
		if (receivedOrderRejectedCountForNewOrderAndRejected == expectedNewOrderCountForNewOrderAndRejected && 
				receivedOrderRequestCompletionCountForNewOrderAndRejected == expectedNewOrderCountForNewOrderAndRejected){
			LOG.info("Test newOrderAndRejected completed successfully");
			messenger.receiver().orderRejectedHandlerList().remove(this::newOrderAndRejectedOrderRejectedHandler);
			messenger.receiver().orderRejectedWithOrderInfoHandlerList().remove(this::newOrderAndRejectedOrderRejectedWithOrderInfoHandler);
		}
	}
	
	// ======================
	// cancelOrderAndRejected
	// ======================
	private int expectedCancelOrderCountForCancelOrderAndRejected = 0;
	private int receivedOrderRequestCompletionCountForCancelOrderAndRejected = 0;
	
	/**
	 * Require to check:
	 * 1. CancelOrderRequest is sent to OMES
	 * 2. OMES sends CancelOrderRequest to OrderExecutor
	 * 3. OMES sends OrderRequestCompletion
	 */
	@SuppressWarnings("unused")
	private void cancelOrderAndRejectedStart(){
		messenger.receiver().orderCancelRejectedHandlerList().add(this::cancelOrderAndRejectedOrderCancelRejectedHandler);

		// What do I need to know after I send out a request?
		// 1) Whether the request was received by OMES successfully
		// 2) That's the only thing, basically
		// 3) a) Timeout
		this.expectedCancelOrderCountForCancelOrderAndRejected = 24;

		// Then change mode of exchange
		CompletableFuture<Command> commandResultFuture = messenger.sendCommand(exchange, 
				Command.of(exchangeSinkId, 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.LINE_HANDLER_ACTION, 
				new ImmutableList.Builder<Parameter>().add(
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_REJECT_ON_CANCEL.value())).build()));
		
		commandResultFuture.whenComplete(this::cancelOrderAndRejectedCommandAck);

	}
	
	private void cancelOrderAndRejectedCommandAck(Command commandResult, Throwable t){
		if (t != null){
			LOG.error("Got unexpected result for command", t);
			return;
		}
		if (commandResult.ackType() != CommandAckType.OK){
			LOG.error("Got unexpected result from exchange [{}]", commandResult.toString());
			return;
		}
		LOG.info("Got command ack");
		for (int i = 0; i < this.expectedCancelOrderCountForCancelOrderAndRejected; i++){
			cancelOrderAndRejectedSendRequest();
		}
	}
	
	private void cancelOrderAndRejectedSendRequest(){
		LOG.info("Send message");
		CancelOrderRequest request = CancelOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				11111,
				security.sid(),
				Side.NULL_VAL);
		CompletableFuture<OrderRequest> future = messenger.sendCancelOrder(omes, request);
		future.whenComplete(this::cancelOrderAndRejectedOrderRequestCompletionHandler);
	}
	
	private void cancelOrderAndRejectedOrderRequestCompletionHandler(OrderRequest orderRequest, Throwable t){
		if (t != null){
			LOG.error("Got exception for order request [{}]", t);
			return;
		}
		if (orderRequest.completionType() == OrderRequestCompletionType.REJECTED){
			LOG.info("Got order request completion rejected");
			receivedOrderRequestCompletionCountForCancelOrderAndRejected++;
			cancelOrderAndRejectedCompleted();
		}
		else {
			LOG.warn("Got order request completion with unexpected result [clientKey:{}, completionType:{}, rejectType:{}]", orderRequest.clientKey(), orderRequest.completionType(), orderRequest.rejectType());
		}
	}
	
	private void cancelOrderAndRejectedOrderCancelRejectedHandler(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder rejected) {
		LOG.info("Received order rejected. [ordSid:{}]", rejected.orderSid());
		throw new IllegalStateException("OrderCancelRejected should not be sent out to client.");
	}

	private void cancelOrderAndRejectedCompleted(){
		if (receivedOrderRequestCompletionCountForCancelOrderAndRejected == expectedCancelOrderCountForCancelOrderAndRejected){
			LOG.info("Test cancelOrderAndRejectedCompleted completed successfully");
			messenger.receiver().orderCancelRejectedHandlerList().remove(this::cancelOrderAndRejectedOrderCancelRejectedHandler);
		}
		else{
			LOG.info("receivedOrderRequestCompletionCountForCancelOrderAndRejected: {}", receivedOrderRequestCompletionCountForCancelOrderAndRejected);
		}
	}

	@Override
	public void activeExit() {
		messenger.unregisterEventsForOrderTracker();
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
		return StateTransitionEvent.NULL;	}

	@Override
	public void stoppedExit() {
	}

}
