package com.lunar.simple;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.lunar.core.LunarSystem;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.exception.ConfigurationException;
import com.lunar.fsm.service.lunar.LunarServiceStateHook;
import com.lunar.fsm.service.lunar.RemoteStateHook;
import com.lunar.message.Parameter;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.OrderFbs;
import com.lunar.message.io.fbs.OrderRequestCompletionFbs;
import com.lunar.message.io.fbs.OrderRequestCompletionTypeFbs;
import com.lunar.message.io.fbs.OrderStatusFbs;
import com.lunar.message.io.fbs.TradeFbs;
import com.lunar.message.io.fbs.TradeStatusFbs;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.service.SimpleSocket.BinaryMessageHandler;

public class LunarOrderRelatedServicesIT {
	private static final Logger LOG = LogManager.getLogger(LunarOrderRelatedServicesIT.class);
	@SuppressWarnings("unused")
	private static LunarSystem system;
	private static WebClient webClient = new WebClient();
	private static RemoteStateHook stateHook;
	private final int secSid = 700;
	
	@Ignore
	@Test
	public void testNewAndSellOrder() throws InterruptedException{
		webClient.sendCommand("accept-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW));

		newThenSellOrder(true);
	}

	@Ignore
	@Test
	public void testNewOrder() throws InterruptedException{
		webClient.sendCommand("accept-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW));
		
		for (int i = 0; i < 5; i++){
			newOrder(Side.BUY, true);
		}
		newOrder(Side.BUY, true);
		newOrder(Side.SELL, 120000, true);
	}
	
	@Ignore
	@Test
	public void testNewCompositeOrder() throws InterruptedException{
		webClient.sendCommand("accept-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW));
		
		newCompositeOrder(Side.BUY, true);
	}
	
	
	@Ignore
	@Test
	public void givenMatchOnNewWhenNewOrderThenReceiveOrderAndTradeThenSell() throws InterruptedException{
		webClient.sendCommand("match-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ServiceType.OrderManagementAndExecutionService),
				Parameter.of(LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW));
		
		newOrderWithFullMatchTradeThenSell();
	}
	
	@Ignore
	@Test
	public void testStart() throws InterruptedException, UnsupportedEncodingException, ExecutionException, TimeoutException{
		webClient.sendCommand("Change line handler mode to accept-on-cancel", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ServiceType.OrderManagementAndExecutionService),
				Parameter.of(LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL));

		// Step 3
		// Order Request
		// Make sure that we can receive OrderRequestCompletion and Order
		Order order = newOrder(Side.BUY, true);
		
		// Step 3
		// Cancel order
		cancelOrder(order.sid(), order.secSid(), order.side(), true);

		// Step 4
		// Change to reject on new
		webClient.sendCommand("Change line handler mode to reject-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ServiceType.OrderManagementAndExecutionService),
				Parameter.of(LineHandlerActionType.CHANGE_MODE_REJECT_ON_NEW));
		newOrder(Side.BUY, false);
	}

	@Ignore
	@Test
	public void testResetOMES() throws InterruptedException{
		// Make sure OMES is back to ACTIVE before proceeding
		CountDownLatch latch = new CountDownLatch(1);
		stateHook.hook(new LunarServiceStateHook() {
			@Override
			public void onResetExit(int sinkId, String name) {
			}
			@Override
			public void onActiveEnter(int sinkId, String name) {
				LOG.info("Enter active state here [sinkId:{}, name:{}]", sinkId, name);
				if (sinkId == 7){
					latch.countDown();
				}
			}
		});

		webClient.sendCommand("reset omes", CommandType.RESET, Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()));
		boolean result = latch.await(3, TimeUnit.SECONDS);
		assertTrue(result);
		
	}

	@Ignore
	@Test
	public void givenNewOrderWhenResetAndNewOrderThenOK() throws InterruptedException{
		webClient.sendCommand("Change line handler mode to accept-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW.value()));

		newOrder(Side.BUY, true);

		// Make sure OMES is back to ACTIVE before proceeding
		CountDownLatch latch = new CountDownLatch(1);
		stateHook.hook(new LunarServiceStateHook() {
			@Override
			public void onResetExit(int sinkId, String name) {
			}
			@Override
			public void onActiveEnter(int sinkId, String name) {
				LOG.info("Enter active state here [sinkId:{}, name:{}]", sinkId, name);
				if (sinkId == 7){
					latch.countDown();
				}
			}
		});

		webClient.sendCommand("reset omes", CommandType.RESET, Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()));
		boolean result = latch.await(3, TimeUnit.SECONDS);
		assertTrue(result);
		
		final long refNewPurchasingPower = 1_000_000_000;
		webClient.sendRequest("update purchasing power - next", RequestType.UPDATE, 
		        Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
		        Parameter.of(ParameterType.PURCHASING_POWER, refNewPurchasingPower));

		newOrder(Side.BUY, true);
		stateHook.clear();
	}
	
	@Ignore
	@Test
	public void givenRejectCancelModeWhenCancelThenReject() throws InterruptedException{
		webClient.sendCommand("Change line handler mode to reject-on-cancel", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_REJECT_ON_CANCEL.value()));
		
		long anySecSid = 10121021;
		int anyOrdSid = 10120412;

		// TODO - to run this in loop of 10
		// to make sure i won't encounter ALREADY_IN_PENDING_CANCEL
		cancelOrder(anyOrdSid, anySecSid, Side.SELL, false);
	}
	
	@Ignore
	@Test
	public void givenMatchOnNewWhenNewOrderThenReceiveOrderAndTrade() throws InterruptedException{
		webClient.sendCommand("match-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW.value()));
		
		newOrderWithFullMatchTrade();
	}
	
	@Ignore
	@Test
	public void givenPartialOnNewWhenNewOrderThenReceiveOrderAndTrade() throws InterruptedException{
		webClient.sendCommand("partial-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_PARTIAL_MATCH_ON_NEW.value()));
		
		newOrderWithPartialMatchTrade();
	}

	public Order newOrderWithFullMatchTradeThenSell() throws InterruptedException {
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_ORDER;
		int quantity = 1000;
		Side side = Side.BUY;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
	
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(),
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
	
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedFilled = new AtomicBoolean(false);
		final AtomicBoolean receivedTradeSnapshotUpdate = new AtomicBoolean(false);
		final AtomicReference<Order> order = new AtomicReference<>();
		
		BinaryMessageHandler handler = (b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			switch (decoder.init(b)){
			case MessagePayloadFbs.OrderRequestCompletionFbs:
				OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
				if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
						&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
					receivedOrderCompletionUpdate.set(true);
					newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
					LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
				}
				else {
					LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
							OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
							OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
				}
				break;
			case MessagePayloadFbs.OrderFbs:
				OrderFbs orderFbs = decoder.asOrderFbs();
				if (orderFbs.quantity() == quantity && orderFbs.orderSid() == newOrderRequest.orderSid()){
					if (orderFbs.status() == OrderStatusFbs.NEW){
						LOG.debug("Received new order");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedOrderUpdate.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.FILLED){
						LOG.debug("Received new order filled");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedFilled.set(true);
					}
				}
				break;
			case MessagePayloadFbs.TradeFbs:
				TradeFbs tradeFbs = decoder.asTradeFbs();
				LOG.debug("Received new trade [orderSid:{}, orderStatus:{}, tradeStatus:{}]", tradeFbs.orderSid(), OrderStatusFbs.name(tradeFbs.status()), TradeStatusFbs.name(tradeFbs.tradeStatus()));
				if (tradeFbs.orderSid() == order.get().sid() && tradeFbs.tradeStatus() == TradeStatusFbs.NEW){
					LOG.debug("Received new trade");
					receivedTradeSnapshotUpdate.set(true);
				}
				break;
			}
		};
		Supplier<Boolean> latchResultSupplier = () -> {
			LOG.debug("Using old result supplier");
			return receivedFilled.get() && receivedTradeSnapshotUpdate.get();
		};
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(4, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to complete newOrderWithFullMatchTrade on time");
		}
		
		final AtomicBoolean receivedOrderUpdateSell = new AtomicBoolean(false);
		// Sell order
		receivedFilled.set(false);
		NewOrderRequest newSellOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(),
				secSid, 
				orderType, 
				quantity, 
				Side.SELL, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newSellOrderRequest);
		webClient.socket().resetLatch();
		BinaryMessageHandler handlerSell = (b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			switch (decoder.init(b)){
			case MessagePayloadFbs.OrderRequestCompletionFbs:
				OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
				if (orderRequestCompletionFbs.clientKey() == newSellOrderRequest.clientKey()
						&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
					receivedOrderCompletionUpdate.set(true);
					newSellOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
					LOG.debug("Received sell order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
				}
				else {
					LOG.debug("Failed to receive sell order request completion [completionType:{}, rejectType:{}]", 
							OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
							OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
				}
				break;
			case MessagePayloadFbs.OrderFbs:
				OrderFbs orderFbs = decoder.asOrderFbs();
				if (orderFbs.quantity() == quantity && orderFbs.orderSid() == newSellOrderRequest.orderSid()){
					if (orderFbs.status() == OrderStatusFbs.NEW){
						LOG.debug("Received new sell order");
						order.set(Order.of(newSellOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedOrderUpdateSell.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.FILLED){
						LOG.debug("Received new sell order filled");
						order.set(Order.of(newSellOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedFilled.set(true);
					}
				}
				break;
			}
		};
		Supplier<Boolean> latchResultSupplierSell = () -> {
			LOG.debug("Using new result supplier: {}", receivedFilled.get());
			return receivedFilled.get();
			};
		webClient.socket().binaryMessageHandler(handlerSell).latchResultSupplier(latchResultSupplierSell);
		webClient.socket().send(webClient.buffer().slice());
		latchResult = webClient.socket().latch().await(4, TimeUnit.SECONDS);
		if (!latchResult){
			LOG.debug("Not able to complete newOrderWithFullMatchTradeThenSell on time");
			throw new AssertionError("Not able to complete newOrderWithFullMatchTradeThenSell on time");
		}
		
		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		assertTrue(receivedFilled.get());
		return order.get();
	}
	
	public Order newOrderWithFullMatchTrade() throws InterruptedException {
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_ORDER;
		int quantity = 1000;
		Side side = Side.BUY;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
		
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(),
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
	
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedFilled = new AtomicBoolean(false);
		final AtomicBoolean receivedTradeSnapshotUpdate = new AtomicBoolean(false);
		final AtomicReference<Order> order = new AtomicReference<>();
		
		BinaryMessageHandler handler = (b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			switch (decoder.init(b)){
			case MessagePayloadFbs.OrderRequestCompletionFbs:
				OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
				if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
						&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
					receivedOrderCompletionUpdate.set(true);
					newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
					LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
				}
				else {
					LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
							OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
							OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
				}
				break;
			case MessagePayloadFbs.OrderFbs:
				OrderFbs orderFbs = decoder.asOrderFbs();
				if (orderFbs.quantity() == quantity && orderFbs.orderSid() == newOrderRequest.orderSid()){
					if (orderFbs.status() == OrderStatusFbs.NEW){
						LOG.debug("Received new order");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedOrderUpdate.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.FILLED){
						LOG.debug("Received new order filled");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedFilled.set(true);
					}
				}
				break;
			case MessagePayloadFbs.TradeFbs:
				TradeFbs tradeFbs = decoder.asTradeFbs();
				LOG.debug("Received new trade [orderSid:{}, orderStatus:{}, tradeStatus:{}]", tradeFbs.orderSid(), OrderStatusFbs.name(tradeFbs.status()), TradeStatusFbs.name(tradeFbs.tradeStatus()));
				if (tradeFbs.orderSid() == order.get().sid() && tradeFbs.tradeStatus() == TradeStatusFbs.NEW){
					LOG.debug("Received new trade");
					receivedTradeSnapshotUpdate.set(true);
				}
				break;
			}
		};
		Supplier<Boolean> latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedFilled.get() && receivedTradeSnapshotUpdate.get();
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(4, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to complete newOrderWithFullMatchTrade on time");
		}
		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		assertTrue(receivedOrderCompletionUpdate.get());
		assertFalse(receivedOrderUpdate.get());
		assertTrue(receivedFilled.get());
		assertTrue(receivedTradeSnapshotUpdate.get());
		return order.get();
	}
	
	public Order newOrderWithPartialMatchTrade() throws InterruptedException {
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_ORDER;
		int quantity = 10000;
		Side side = Side.BUY;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
		
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(), 
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
	
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedPartialFilled = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderExpired = new AtomicBoolean(false);
		final AtomicBoolean receivedTradeSnapshotUpdate = new AtomicBoolean(false);
		final AtomicReference<Order> order = new AtomicReference<>();
		
		BinaryMessageHandler handler = (b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			switch (decoder.init(b)){
			case MessagePayloadFbs.OrderRequestCompletionFbs:
				OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
				if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
						&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
					receivedOrderCompletionUpdate.set(true);
					newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
					LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
				}
				else {
					LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
							OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
							OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
				}
				break;
			case MessagePayloadFbs.OrderFbs:
				OrderFbs orderFbs = decoder.asOrderFbs();
				if (orderFbs.quantity() == quantity && orderFbs.orderSid() == newOrderRequest.orderSid()){
					if (orderFbs.status() == OrderStatusFbs.NEW){
						LOG.debug("Received new order");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedOrderUpdate.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.PARTIALLY_FILLED){
						// It is very likely that we may not be able to receive this update because it is likely to be masked by the up-coming-expired update
						LOG.debug("Received new order partial filled");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedPartialFilled.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.EXPIRED){
						LOG.debug("Received new order expired");
						if (orderFbs.leavesQty() == 0){
							order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
							LOG.debug("Received new order expired with correct info");
							receivedOrderExpired.set(true);
						}
					}
					else if (orderFbs.status() == OrderStatusFbs.CANCELLED){
						LOG.debug("Received new order cancelled");
						if (orderFbs.leavesQty() == 0){
							order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
							LOG.debug("Received new order cancelled with correct info");
							receivedOrderExpired.set(true);
						}
					}

				}
				break;
			case MessagePayloadFbs.TradeFbs:
				TradeFbs tradeFbs = decoder.asTradeFbs();
				LOG.debug("Received new trade [orderSid:{}, orderStatus:{}, tradeStatus:{}, execPrice:{}, execQty:{}]", tradeFbs.orderSid(), OrderStatusFbs.name(tradeFbs.status()), 
						TradeStatusFbs.name(tradeFbs.tradeStatus()),
						tradeFbs.executionPrice(),
						tradeFbs.executionQty());
				if (tradeFbs.orderSid() == order.get().sid() && tradeFbs.tradeStatus() == TradeStatusFbs.NEW){
					LOG.debug("Received new trade");
					receivedTradeSnapshotUpdate.set(true);
				}
				break;
			}
		};
		Supplier<Boolean> latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderExpired.get() && receivedTradeSnapshotUpdate.get();
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(4, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to complete newOrderWithPartialMatchTrade on time");
		}
		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		assertTrue(receivedOrderCompletionUpdate.get());
		assertFalse(receivedOrderUpdate.get());
		assertTrue(receivedTradeSnapshotUpdate.get());
		if (receivedPartialFilled.get()){
			LOG.debug("Received partially filled update");
		}
		return order.get();
	}
	
	public Order newThenSellOrder(boolean expectAccept) throws InterruptedException{
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_ORDER;
		int quantity = 1000;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
		
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(), 
				secSid, 
				orderType, 
				quantity, 
				Side.BUY, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);

		final AtomicReference<Order> order = new AtomicReference<>();

		BinaryMessageHandler handler;
		Supplier<Boolean> latchResultSupplier;
		if (expectAccept){
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
								OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
								OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.quantity() == quantity){
						LOG.debug("Received new order");
						receivedOrderUpdate.set(true);
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();
		}
		else {
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.REJECTED){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion rejection for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
								orderRequestCompletionFbs.completionType(),
								orderRequestCompletionFbs.rejectType());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.status() == OrderStatusFbs.REJECTED && orderFbs.orderSid() == newOrderRequest.orderSid()){
						LOG.debug("Received order update for order rejected [orderSid:{}]", orderFbs.orderSid());
						receivedOrderUpdate.set(true);
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();			
		}
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(4, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to receive order completion and order on time");
		}
		
		NewOrderRequest newSellOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(), 
				secSid, 
				orderType, 
				quantity, 
				Side.SELL, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newSellOrderRequest);
		webClient.socket().resetLatch();
		
		if (expectAccept){
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion for new sell order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive sell order request completion [completionType:{}, rejectType:{}]", 
								OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
								OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.quantity() == quantity){
						LOG.debug("Received new sell order");
						receivedOrderUpdate.set(true);
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();
		}
		else {
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.REJECTED){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion rejection for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
								orderRequestCompletionFbs.completionType(),
								orderRequestCompletionFbs.rejectType());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.status() == OrderStatusFbs.REJECTED && orderFbs.orderSid() == newOrderRequest.orderSid()){
						LOG.debug("Received order update for order rejected [orderSid:{}]", orderFbs.orderSid());
						receivedOrderUpdate.set(true);
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();			
		}
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		latchResult = webClient.socket().latch().await(4, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to receive order completion for sell order on time");
		}

		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		return order.get();
	}
	
	public Order newCompositeOrder(Side side, boolean expectAccept) throws InterruptedException{
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_THEN_CANCEL_ORDER;
		int quantity = 1000;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
		
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(), 
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);

		final AtomicReference<Order> order = new AtomicReference<>();

		BinaryMessageHandler handler;
		Supplier<Boolean> latchResultSupplier;
		if (expectAccept){
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
								OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
								OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.quantity() == quantity){
						LOG.debug("Received new order");
						receivedOrderUpdate.set(true);
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();
		}
		else {
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.REJECTED){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion rejection for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
								orderRequestCompletionFbs.completionType(),
								orderRequestCompletionFbs.rejectType());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.status() == OrderStatusFbs.REJECTED && orderFbs.orderSid() == newOrderRequest.orderSid()){
						LOG.debug("Received order update for order rejected [orderSid:{}]", orderFbs.orderSid());
						receivedOrderUpdate.set(true);
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();			
		}
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(4000, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to receive order completion and order on time");
		}
		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		assertTrue(receivedOrderCompletionUpdate.get());
		assertTrue(receivedOrderUpdate.get());
		return order.get();
	}
	
	public Order newOrder(Side side, boolean expectAccept) throws InterruptedException{
		return newOrder(side, 100000, expectAccept);
	}
	
	public Order newOrder(Side side, int limitPrice, boolean expectAccept) throws InterruptedException{
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_ORDER;
		int quantity = 1000;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
//		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
		
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(), 
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);

		final AtomicReference<Order> order = new AtomicReference<>();

		BinaryMessageHandler handler;
		Supplier<Boolean> latchResultSupplier;
		if (expectAccept){
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion here [completionType:{}, rejectType:{}]", 
								OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
								OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
						receivedOrderCompletionUpdate.set(true);
						receivedOrderUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.quantity() == quantity){
						LOG.debug("Received new order");
						receivedOrderUpdate.set(true);
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();
		}
		else {
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.REJECTED){
						receivedOrderCompletionUpdate.set(true);
						newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
						LOG.debug("Received order request completion rejection for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
					}
					else {
						LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
								orderRequestCompletionFbs.completionType(),
								orderRequestCompletionFbs.rejectType());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.status() == OrderStatusFbs.REJECTED && orderFbs.orderSid() == newOrderRequest.orderSid()){
						LOG.debug("Received order update for order rejected [orderSid:{}]", orderFbs.orderSid());
						receivedOrderUpdate.set(true);
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();			
		}
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(4000, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to receive order completion and order on time");
		}
		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		assertTrue(receivedOrderCompletionUpdate.get());
		assertTrue(receivedOrderUpdate.get());
		return order.get();
	}
	
	public void cancelOrder(int orderSid, long secSid, Side side, boolean expectCancel) throws InterruptedException{
		CancelOrderRequest cancelOrderRequest = CancelOrderRequest.of(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(),
				orderSid, 
				secSid,
				side);
		webClient.messageFbsEncoder().encodeCancelOrderRequest(webClient.buffer(), cancelOrderRequest);
		webClient.socket().resetLatch();
		final AtomicBoolean receivedOrderCompletionForCancel = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderForCancel = new AtomicBoolean(false);
		
		BinaryMessageHandler handler;
		Supplier<Boolean> latchResultSupplier;
		if (expectCancel){
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == cancelOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
						receivedOrderCompletionForCancel.set(true);
						LOG.debug("Received cancel order request completion");
					}
					else {
						LOG.debug("Faileld to receive order request completion. [completionType:{}, rejectType:{}]", 
								OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
								OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					LOG.debug("Received order update for cancel");
					OrderFbs orderFbs = decoder.asOrderFbs();
					if (orderFbs.status() == OrderStatusFbs.CANCELLED && orderFbs.leavesQty() == 0){
						receivedOrderForCancel.set(true);
					}
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionForCancel.get() && receivedOrderForCancel.get();
		}
		else{
			handler = (b)->{
				MessageFbsDecoder decoder = MessageFbsDecoder.of();
				switch (decoder.init(b)){
				case MessagePayloadFbs.OrderRequestCompletionFbs:
					OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
					if (orderRequestCompletionFbs.clientKey() == cancelOrderRequest.clientKey()
							&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.REJECTED){
						receivedOrderCompletionForCancel.set(true);
						LOG.debug("Received cancel order request completion");
					}
					else {
						LOG.debug("Faileld to receive order request completion. [completionType:{}, rejectType:{}]", 
								OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
								OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
					}
					break;
				case MessagePayloadFbs.OrderFbs:
					LOG.debug("Received order update for cancel");
					receivedOrderForCancel.set(true);
					break;
				}
			};
			latchResultSupplier = () -> receivedOrderCompletionForCancel.get() && !receivedOrderForCancel.get();
		}
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(2, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to cancel order");
		}
	}
	
	@BeforeClass
	public static void setupClass() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException, ConfigurationException{
		String host = "127.0.0.1";
		int port = 8192;
		int jmxPort = 8787;
		String systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, 
				URLDecoder.decode(LunarOrderRelatedServicesIT.class.getClassLoader().getResource(
						Paths.get("config", "it", "simple", "lunar.order.related.services.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
		
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
        UserControlledSystemClock systemClock = new UserControlledSystemClock(LocalDate.of(2015, 10, 2));
        stateHook = new RemoteStateHook();
		system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture, stateHook);
		readyFuture.get(2000, TimeUnit.SECONDS);
				
		// Start web client and send request to web service
		webClient.start();
	}
	
	@Before
	public void setup() throws InterruptedException {
		// Update purchasing power
		final long refNewPurchasingPower = 1_000_000_000;
		webClient.sendRequest("update purchasing power - setup", RequestType.UPDATE, 
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.PURCHASING_POWER, refNewPurchasingPower));

		// Request to subscribe to all order snapshots (since web client won't get individual order update)
		webClient.sendRequest("subscribe to order and trade snapshot", RequestType.SUBSCRIBE,
			Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderAndTradeSnapshotService.value()),
			Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()));
	}
	
	@AfterClass
	public static void cleanupClass(){
/*		webClient.stop();
		system.close();
		while (system.state() != LunarSystemState.STOPPED){
			LOG.info("current state: {}, waiting...", system.state());
			LockSupport.parkNanos(500_000_000);
		}
		LOG.info("current state: {}, done...", system.state());
		assertEquals(system.state(), LunarSystemState.STOPPED);
		
		while (!system.isCompletelyShutdown()){
			LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
			LockSupport.parkNanos(500_000_000);
		}*/		
	}
	
	@After
	public void cleanup() throws InterruptedException{
//		LOG.info("Cleanup");
//		webClient.sendCommand("Reset line handler", CommandType.LINE_HANDLER_ACTION,
//				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
//				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.RESET.value()));
	}
}
