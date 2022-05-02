package com.lunar.order;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import java.time.LocalTime;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.lunar.core.SequencingOnlyChannel;
import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ExecutionType;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeEncoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeEncoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

@RunWith(MockitoJUnitRunner.class)
public class OrderContextTest {
	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
	
	@Mock
	private OrderUpdateHandler stateChangeHandler;
	
	@Mock
	private MessageSink selfSink;

	private OrderContext orderContext;
	private MessageSinkRef self;
	private Security security;
	
	private Order precreatedOrder;
	
	@Before
	public void setup(){
		orderContext = OrderContext.of(stateChangeHandler, SequencingOnlyChannel.of(1));
		self = MessageSinkRef.of(selfSink);
		security = Security.of(12345678L, SecurityType.WARRANT, "61727", 101, false, SpreadTableBuilder.get(SecurityType.WARRANT));
		precreatedOrder = Order.of(12345678L, 
				self, 
				1, 
				10000, 
				BooleanType.FALSE, 
				OrderType.LIMIT_ORDER,
				Side.BUY, 
				65000, 
				65000,
				TimeInForce.DAY,
				OrderStatus.NEW);
	}
	
	@Test
	public void testCreate(){
		OrderContext.of(OrderUpdateHandler.NULL_HANDLER, SequencingOnlyChannel.of(1)); 
	}
	
	@Test
	public void testOrderAccepted(){
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.NEW;
		final int orderSid = 123456;
		final int orderId = 11111;
		final int quantity = 1000000;
		final int leavesQty = 1000000;
		final int limitPrice = 60400;
		final int stopPrice = 60400;
		final Side side = Side.BUY;
		final TimeInForce tif = TimeInForce.DAY;
		final OrderStatus status = OrderStatus.NEW;
		final long updateTime = LocalTime.now().toNanoOfDay();
		
		int clientKey = 22222;
		int portSid = 55555;
		NewOrderRequest request = NewOrderRequest.of(
				clientKey,
				self, 
				security, 
				OrderType.LIMIT_ORDER, 
				quantity, 
				side, 
				tif, 
				BooleanType.FALSE, 
				limitPrice, 
				stopPrice, portSid);
		request.orderSid(orderSid);
		
		OrderAcceptedSbeEncoder orderAcceptedEncoder = new OrderAcceptedSbeEncoder();
		orderAcceptedEncoder.wrap(this.buffer, 0)
		.cumulativeQty(cumulativeQty)
		.execType(executionType)
		.orderSid(orderSid)
		.orderId(orderId)
		.leavesQty(leavesQty)
		.price(limitPrice)
		.secSid(security.sid())
		.side(side)
		.status(status);
	
		OrderAcceptedSbeDecoder orderAcceptedDecoder = new OrderAcceptedSbeDecoder();
		orderAcceptedDecoder.wrap(this.buffer, 0, OrderAcceptedSbeEncoder.BLOCK_LENGTH, OrderAcceptedSbeEncoder.SCHEMA_VERSION);
		
		orderContext.onOrderAcceptedAsFirstUpdate(request, buffer, 0, orderAcceptedDecoder, updateTime);
		
		Order order = orderContext.order();
		assertEquals(orderId, order.orderId());
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(leavesQty, order.leavesQty());
		assertEquals(orderSid, order.sid());
		
		verify(stateChangeHandler, times(1)).onAcceptedAsFirstUpdate(orderContext, 
				this.buffer, 
				0, 
				orderAcceptedDecoder);
	}
	
	@Test
	public void testOrderRejectedAsFirstUpdate(){
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.REJECT;
		final int orderSid = 123456;
		final int quantity = 1000000;
		final int leavesQty = 1000000;
		final int limitPrice = 60400;
		final int stopPrice = 60400;
		final Side side = Side.BUY;
		final TimeInForce tif = TimeInForce.DAY;
		final OrderStatus status = OrderStatus.REJECTED;
		
		int clientKey = 22222;
		int portSid = 55555;
		NewOrderRequest request = NewOrderRequest.of(
				clientKey,
				self, 
				security, 
				OrderType.LIMIT_ORDER, 
				quantity, 
				side, 
				tif, 
				BooleanType.FALSE, 
				limitPrice, 
				stopPrice, portSid);
		request.orderSid(orderSid);
		
		OrderRejectedSbeEncoder orderRejectedEncoder = new OrderRejectedSbeEncoder();
		orderRejectedEncoder.wrap(this.buffer, 0)
			.cumulativeQty(0)
			.execType(executionType)
			.leavesQty(leavesQty)
			.secSid(security.sid())
			.status(OrderStatus.REJECTED);

		OrderRejectedSbeDecoder orderRejectedDecoder = new OrderRejectedSbeDecoder();
		orderRejectedDecoder.wrap(this.buffer, 0, OrderRejectedSbeEncoder.BLOCK_LENGTH, OrderRejectedSbeEncoder.SCHEMA_VERSION);

		orderContext.onOrderRejectedAsFirstUpdate(request, buffer, 0, orderRejectedDecoder, "");
		Order order = orderContext.order();
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(leavesQty, order.leavesQty());
		assertEquals(security.sid(), order.secSid());
		assertEquals(orderSid, order.sid());
		
		verify(stateChangeHandler, times(1)).onRejectedAsFirstUpdate(orderContext, this.buffer, 0, orderRejectedDecoder);
	}
	
	@Test
	public void testOrderCancelled(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSid = 123457;
		precreatedOrder.orderId(orderId).sid(123457);
		orderContext.order(precreatedOrder);
		
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.CANCEL;
		final int orderSidToBeCancelled = 123456;
		final OrderStatus status = OrderStatus.CANCELLED;
		
		int clientKey = 22222;
		CancelOrderRequest request = CancelOrderRequest.of(
				clientKey,
				self,
				orderSidToBeCancelled,
				security.sid(),
				Side.NULL_VAL);
		request.orderSid(orderSid);
		
		OrderCancelledSbeEncoder orderCancelledEncoder = new OrderCancelledSbeEncoder();
		orderCancelledEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.origOrderSid(orderSidToBeCancelled)
			.cumulativeQty(0)
			.execType(executionType)
			.status(status);

		OrderCancelledSbeDecoder orderCancelledDecoder = new OrderCancelledSbeDecoder();
		orderCancelledDecoder.wrap(this.buffer, 0, OrderCancelledSbeEncoder.BLOCK_LENGTH, OrderCancelledSbeEncoder.SCHEMA_VERSION);

		int overrideOrderSid = 22223;
		orderContext.onOrderCancelled(buffer, 0, orderCancelledDecoder, overrideOrderSid);
		Order order = orderContext.order();
		assertEquals(orderId, order.orderId());
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSid, order.sid());
		
		verify(stateChangeHandler, times(1)).onCancelled(orderContext, this.buffer, 0, orderCancelledDecoder, overrideOrderSid);
	}
	
	@Test
	public void testOrderAmended(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSid = 123457;
		precreatedOrder.orderId(orderId).sid(123457);
		orderContext.order(precreatedOrder);
		
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.CANCEL;
		final int orderSidToBeAmended = 123456;
		final OrderStatus status = OrderStatus.NEW;
		
		int clientKey = 22222;
		AmendOrderRequest request = AmendOrderRequest.of(
				clientKey,
				self,
				orderSidToBeAmended,
				100,
				100,
				Side.BUY);
		request.orderSid(orderSid);
		
		OrderAmendedSbeEncoder orderAmendedEncoder = new OrderAmendedSbeEncoder();
		orderAmendedEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.origOrderSid(orderSidToBeAmended)
			.cumulativeQty(0)
			.execType(executionType)
			.status(status);

		OrderAmendedSbeDecoder orderAmendedDecoder = new OrderAmendedSbeDecoder();
		orderAmendedDecoder.wrap(this.buffer, 0, OrderAmendedSbeEncoder.BLOCK_LENGTH, OrderAmendedSbeEncoder.SCHEMA_VERSION);

		orderContext.onOrderAmended(request, buffer, 0, orderAmendedDecoder);
		Order order = orderContext.order();
		assertEquals(orderId, order.orderId());
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSid, order.sid());
		
		verify(stateChangeHandler, times(1)).onAmended(orderContext, this.buffer, 0, orderAmendedDecoder);
	}
	
	@Test
	public void testOrderExpired(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSid = 123457;
		precreatedOrder.orderId(orderId).sid(123457);
		orderContext.order(precreatedOrder);
		
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.EXPIRE;
		final OrderStatus status = OrderStatus.EXPIRED;
		
		OrderExpiredSbeEncoder orderExpiredEncoder = new OrderExpiredSbeEncoder();
		orderExpiredEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.cumulativeQty(0)
			.execType(executionType)
			.status(status);

		OrderExpiredSbeDecoder orderExpiredDecoder = new OrderExpiredSbeDecoder();
		orderExpiredDecoder.wrap(this.buffer, 0, OrderExpiredSbeEncoder.BLOCK_LENGTH, OrderExpiredSbeEncoder.SCHEMA_VERSION);

		orderContext.onOrderExpired(buffer, 0, orderExpiredDecoder);
		Order order = orderContext.order();
		assertEquals(orderId, order.orderId());
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSid, order.sid());
		
		verify(stateChangeHandler, times(1)).onExpired(orderContext, this.buffer, 0, orderExpiredDecoder);
	}
	
	@Test
	public void testOrderCancelRejected(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSidToBeCancelled = 123456;
		precreatedOrder.orderId(orderId).sid(orderSidToBeCancelled);
		orderContext.order(precreatedOrder);

		
		final int orderSid = 123457;
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.CANCEL_REJECT;
		final OrderStatus status = OrderStatus.NEW;
		
		int clientKey = 22222;
		CancelOrderRequest request = CancelOrderRequest.of(
				clientKey,
				self,
				orderSidToBeCancelled,
				security.sid(),
				Side.NULL_VAL);
		request.orderSid(orderSid);
		
		OrderCancelRejectedSbeEncoder orderCancelRejectedEncoder = new OrderCancelRejectedSbeEncoder();
		orderCancelRejectedEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.cumulativeQty(0)
			.execType(executionType)
			.status(status);

		OrderCancelRejectedSbeDecoder orderCancelRejectedDecoder = new OrderCancelRejectedSbeDecoder();
		orderCancelRejectedDecoder.wrap(this.buffer, 0, OrderCancelRejectedSbeEncoder.BLOCK_LENGTH, OrderCancelRejectedSbeEncoder.SCHEMA_VERSION);

		orderContext.onOrderCancelRejected(request, buffer, 0, orderCancelRejectedDecoder);
		Order order = orderContext.order();
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSidToBeCancelled, order.sid());		
	}
	
	@Test
	public void testOrderAmendRejected(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSidToBeAmended = 123456;
		precreatedOrder.orderId(orderId).sid(orderSidToBeAmended);
		orderContext.order(precreatedOrder);

		final int orderSid = 123457;
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.AMEND_REJECT;
		final OrderStatus status = OrderStatus.NEW;
		
		int clientKey = 22222;
		AmendOrderRequest request = AmendOrderRequest.of(
				clientKey,
				self,
				orderSidToBeAmended,
				100,
				100,
				Side.BUY);
		request.orderSid(orderSid);
		
		OrderAmendRejectedSbeEncoder orderAmendRejectedEncoder = new OrderAmendRejectedSbeEncoder();
		orderAmendRejectedEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.cumulativeQty(0)
			.execType(executionType)
			.status(status);

		OrderAmendRejectedSbeDecoder orderAmendRejectedDecoder = new OrderAmendRejectedSbeDecoder();
		orderAmendRejectedDecoder.wrap(this.buffer, 0, OrderAmendRejectedSbeEncoder.BLOCK_LENGTH, OrderAmendRejectedSbeEncoder.SCHEMA_VERSION);

		orderContext.onOrderAmendRejected(request, buffer, 0, orderAmendRejectedDecoder);
		Order order = orderContext.order();
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSidToBeAmended, order.sid());		
	}
	
	@Test
	public void testTrade(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSidToBeTraded = 123456;
		precreatedOrder.orderId(orderId).sid(orderSidToBeTraded);
		orderContext.order(precreatedOrder);

		final int orderSid = 123457;
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.TRADE;
		final OrderStatus status = OrderStatus.NEW;
		
		TradeCreatedSbeEncoder tradeCreatedEncoder = new TradeCreatedSbeEncoder();
		tradeCreatedEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.cumulativeQty(100)
			.execType(executionType)
			.status(status);

		TradeCreatedSbeDecoder tradeCreatedDecoder = new TradeCreatedSbeDecoder();
		tradeCreatedDecoder.wrap(this.buffer, 0, TradeCreatedSbeEncoder.BLOCK_LENGTH, TradeCreatedSbeEncoder.SCHEMA_VERSION);

		int tradeSid = 44999999;
		orderContext.onTradeCreated(buffer, 0, tradeCreatedDecoder, tradeSid);
		Order order = orderContext.order();
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSidToBeTraded, order.sid());	

		verify(stateChangeHandler, times(1)).onTradeCreated(orderContext, this.buffer, 0, tradeCreatedDecoder, tradeSid);
//		verify(stateChangeHandler, times(0)).onFilled(order, this.buffer, 0, tradeDecoder);
	}

	@Test
	public void testTradeFilled(){
		// An order should already exist
		final int orderId = 11111;
		final int orderSidToBeTraded = 123456;
		precreatedOrder.orderId(orderId).sid(orderSidToBeTraded);
		orderContext.order(precreatedOrder);

		final int orderSid = 123457;
		final int cumulativeQty = 0;
		final ExecutionType executionType = ExecutionType.TRADE;
		final OrderStatus status = OrderStatus.FILLED;
		
		TradeCreatedSbeEncoder TradeEncoder = new TradeCreatedSbeEncoder();
		TradeEncoder.wrap(this.buffer, 0)
			.orderSid(orderSid)
			.cumulativeQty(100)
			.execType(executionType)
			.status(status);

		TradeCreatedSbeDecoder tradeCreatedDecoder = new TradeCreatedSbeDecoder();
		tradeCreatedDecoder.wrap(this.buffer, 0, TradeSbeEncoder.BLOCK_LENGTH, TradeSbeEncoder.SCHEMA_VERSION);

		int tradeSid = 49000000;
		orderContext.onTradeCreated(buffer, 0, tradeCreatedDecoder, tradeSid);
		Order order = orderContext.order();
		assertEquals(status, order.status());
		assertEquals(cumulativeQty, order.cumulativeExecQty());
		assertEquals(orderSidToBeTraded, order.sid());	

		verify(stateChangeHandler, times(1)).onTradeCreated(orderContext, this.buffer, 0, tradeCreatedDecoder, tradeSid);
//		verify(stateChangeHandler, times(1)).onFilled(order, this.buffer, 0, tradeDecoder);
	}
}
