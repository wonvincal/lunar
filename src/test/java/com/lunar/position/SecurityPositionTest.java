package com.lunar.position;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.math.DoubleMath;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeEncoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeEncoder;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;
import com.lunar.message.sink.DummyMessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.Order;
import com.lunar.order.OrderUtil;
import com.lunar.order.Trade;
import com.lunar.order.TradeUtil;
import com.lunar.service.ServiceConstant;
import com.lunar.util.AssertUtil;

public class SecurityPositionTest {
	private static final Logger LOG = LogManager.getLogger(SecurityPositionTest.class);
	private static double tolerance = 0.0001;
	private static EnumMap<SecurityType, FeeAndCommissionSchedule> schedules;
	private static MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
	
	@BeforeClass
	public static void setup(){
		schedules = new EnumMap<>(SecurityType.class);
		schedules.put(SecurityType.STOCK, FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION);
		schedules.put(SecurityType.WARRANT, FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION);
		schedules.put(SecurityType.CBBC, FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION);
	}
	
	@Test
	public void testCreate(){
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		assertNotNull(position);
		assertEquals(entitySid, position.details().entitySid());
		assertEquals(EntityType.SECURITY, position.details().entityType());
		assertEquals(securityType, position.securityType());
		assertEquals(undSecSid, position.undSecSid());
		assertEquals(issuerSid, position.issuerSid());
	}
	
	@Test
	public void testAddRemoveAggregationPosition(){
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);

		long firmSid = 888888;
		AggregatedSecurityPosition aggregatedPosition = AggregatedSecurityPosition.of(firmSid, EntityType.FIRM);
		assertTrue(position.addHandler(aggregatedPosition));
		assertFalse(position.addHandler(aggregatedPosition));		
	}
	
	@Test
	public void testAddRemoveHandler(){
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);

		PositionChangeHandler positionHandler = new PositionChangeHandler() {
			
			@Override
			public void handleChange(PositionDetails current, PositionDetails previous) {
			}
			
			@Override
			public void handleChange(SecurityPositionDetails current, SecurityPositionDetails previous) {
			}
		};

		PositionChangeHandler nonExistPositionHandler = new PositionChangeHandler() {
			
			@Override
			public void handleChange(PositionDetails current, PositionDetails previous) {
			}
			
			@Override
			public void handleChange(SecurityPositionDetails current, SecurityPositionDetails previous) {
			}
		};

		assertTrue(position.addHandler(positionHandler));
		assertFalse(position.addHandler(positionHandler));
		assertFalse(position.removeHandler(nonExistPositionHandler));
		assertTrue(position.removeHandler(positionHandler));
	}

	private final AtomicInteger tradeSidSeq = new AtomicInteger(9000000);
	private final AtomicInteger executionIdSeq = new AtomicInteger(2000000);
	
	private void addTrade(SecurityPosition position, SecurityPositionChangeTracker changeTracker, long secSid, Side side, int price, int qty, TradeStatus tradeStatus){
		int orderSid = 80000001;
		int orderId = 7000000;
		Trade trade = Trade.of(tradeSidSeq.getAndIncrement(), 
				orderSid, orderId, secSid, side, 0, qty, Integer.toString(executionIdSeq.getAndIncrement()),
				price, qty, OrderStatus.FILLED, tradeStatus, LocalTime.now().toNanoOfDay(), LocalTime.now().toNanoOfDay());
		
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		TradeSbeEncoder encoder = new TradeSbeEncoder();
		TradeSbeDecoder decoder = new TradeSbeDecoder();
		TradeUtil.populateFrom(encoder, buffer, 0, trade, decoder, stringBuffer);
		position.handleTrade(decoder, changeTracker);
	}
	
	private void addSomeTradesWithZeroFeesAndCommission(SecurityPosition position, SecurityPositionChangeTracker changeTracker){
		// When
		// Buy 1000@15000
		int sid = 9000001;
		Side side = Side.BUY;
		int orderSid = 8000001;
		int orderId = 7000001;
		long secSid = 1000001;
		int leavesQty = 0;
		int cumulativeQty = 1000;
		int executionId = 250001;
		int executionPrice = 15000;
		int executionQty = 1000;
		OrderStatus orderStatus = OrderStatus.FILLED;
		TradeStatus tradeStatus = TradeStatus.NEW;
		long createTime = LocalTime.now().toNanoOfDay();
		long updateTime = LocalTime.now().toNanoOfDay();
		Trade trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, Integer.toString(executionId),
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);
		
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		TradeSbeEncoder encoder = new TradeSbeEncoder();
		TradeSbeDecoder decoder = new TradeSbeDecoder();
		TradeUtil.populateFrom(encoder, buffer, 0, trade, decoder, stringBuffer);
		position.handleTrade(decoder, changeTracker);
		
		assertEquals(executionQty, position.details().openPosition());
		assertDouble(0, position.details().netRealizedPnl(), tolerance, "netRealizedPnl");
		assertDouble(0, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertDouble(0, position.details().totalPnl(), tolerance, "totalPnl");
		assertEquals(0, position.details().osBuyQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osBuyNotional(), tolerance));
		assertEquals(0, position.details().osSellQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osSellNotional(), tolerance));
		assertEquals(executionQty, position.details().buyQty());
		assertTrue(DoubleMath.fuzzyEquals(executionQty * executionPrice * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().buyNotional(), tolerance));
		assertEquals(0, position.details().sellQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().sellNotional(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(executionPrice * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmSellPrice(), tolerance));
		assertFalse(position.details().hasMtmSellPrice());
		assertFalse(position.details().hasMtmBuyPrice());
		double capUsed = executionPrice * executionQty * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		double maxCapUsed = capUsed;
		AssertUtil.assertDouble(capUsed, position.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(maxCapUsed, position.details().maxCapUsed(), "maxCapUsed");
		int tradeCount = 0;
		assertEquals(++tradeCount, position.details().tradeCount());
		LOG.debug("{}", position.details());

		// Buy 1000@20000
		sid++;
		orderSid++;
		orderId++;
		executionId++;
		executionPrice = 20000;
		executionQty = 1000;
		trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, Integer.toString(executionId),
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);
		TradeUtil.populateFrom(encoder, buffer, 0, trade, decoder, stringBuffer);
		position.handleTrade(decoder, changeTracker);
		assertEquals(2000, position.details().openPosition());
		assertDouble(0, position.details().netRealizedPnl(), tolerance, "netRealizedPnl");
		assertDouble(0, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertDouble(0, position.details().totalPnl(), tolerance, "totalPnl");
		assertEquals(0, position.details().osBuyQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osBuyNotional(), tolerance));
		assertEquals(0, position.details().osSellQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osSellNotional(), tolerance));
		assertEquals(2000, position.details().buyQty());
		assertTrue(DoubleMath.fuzzyEquals(((20000 * 1000) + 15000 * 1000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().buyNotional(), tolerance));
		assertEquals(0, position.details().sellQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().sellNotional(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(((20000 * 1000) + 15000 * 1000) / 2000 * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmSellPrice(), tolerance));
		assertFalse(position.details().hasMtmSellPrice());
		assertFalse(position.details().hasMtmBuyPrice());
		capUsed += executionPrice * executionQty * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		maxCapUsed = capUsed;
		AssertUtil.assertDouble(capUsed, position.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(maxCapUsed, position.details().maxCapUsed(), "maxCapUsed");
		assertEquals(++tradeCount, position.details().tradeCount());

		// Sell 1000@20000
		side = Side.SELL;
		sid++;
		orderSid++;
		orderId++;
		executionId++;
		executionPrice = 20000;
		executionQty = 1000;
		trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, Integer.toString(executionId),
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);
		TradeUtil.populateFrom(encoder, buffer, 0, trade, decoder, stringBuffer);
		position.handleTrade(decoder, changeTracker);

		assertEquals(1000, position.details().openPosition());
		assertDouble(1000 * (20 - ((20000 * 1000) + 15000 * 1000) / 2000 * ServiceConstant.INT_PRICE_SCALING_FACTOR), position.details().netRealizedPnl(), tolerance, "netRealizedPnl");
		assertDouble(0, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertDouble(1000 * (20 - ((20000 * 1000) + 15000 * 1000) / 2000 * ServiceConstant.INT_PRICE_SCALING_FACTOR), position.details().totalPnl(), tolerance, "totalPnl");

		assertEquals(0, position.details().osBuyQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osBuyNotional(), tolerance));
		assertEquals(0, position.details().osSellQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osSellNotional(), tolerance));
		assertEquals(2000, position.details().buyQty());
		assertTrue(DoubleMath.fuzzyEquals(((20000 * 1000) + 15000 * 1000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().buyNotional(), tolerance));
		assertEquals(1000, position.details().sellQty());
		assertTrue(DoubleMath.fuzzyEquals(20000 * 1000 * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().sellNotional(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(((20000 * 1000) + 15000 * 1000) / 2000 * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(20, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmSellPrice(), tolerance));
		assertFalse(position.details().hasMtmSellPrice());
		assertFalse(position.details().hasMtmBuyPrice());
		capUsed -= executionPrice * executionQty * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		// maxCapUsed = capUsed; no change
		AssertUtil.assertDouble(capUsed, position.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(maxCapUsed, position.details().maxCapUsed(), "maxCapUsed");
		assertEquals(++tradeCount, position.details().tradeCount());


		// Sell 500@11000
		LOG.debug("{}", position.details());

		sid++;
		orderSid++;
		orderId++;
		executionId++;
		executionPrice = 11000;
		executionQty = 500;
		trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, Integer.toString(executionId),
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);
		TradeUtil.populateFrom(encoder, buffer, 0, trade, decoder, stringBuffer);
		position.handleTrade(decoder, changeTracker);

		LOG.debug("{}", position.details());
		assertEquals(500, position.details().openPosition());
		assertTrue(DoubleMath.fuzzyEquals(1500 * ((((11000 * 500) + (20000 * 1000)) / 1500) - 
				(((20000 * 1000) + 15000 * 1000) / 2000)) * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().netRealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(1500 * ((((11000 * 500) + (20000 * 1000)) / 1500) - 
				(((20000 * 1000) + 15000 * 1000) / 2000)) * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().totalPnl(), tolerance));
		assertEquals(0, position.details().osBuyQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osBuyNotional(), tolerance));
		assertEquals(0, position.details().osSellQty());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().osSellNotional(), tolerance));
		assertEquals(2000, position.details().buyQty());
		assertTrue(DoubleMath.fuzzyEquals(((20000 * 1000) + 15000 * 1000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().buyNotional(), tolerance));
		assertEquals(1500, position.details().sellQty());
		assertTrue(DoubleMath.fuzzyEquals((11000 * 500 + 20000 * 1000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().sellNotional(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(((20000 * 1000) + 15000 * 1000) / 2000 * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(((11000 * 500) + (20000 * 1000)) / 1500 * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().mtmSellPrice(), tolerance));
		assertFalse(position.details().hasMtmSellPrice());
		assertFalse(position.details().hasMtmBuyPrice());
		capUsed -= executionPrice * executionQty * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		// maxCapUsed = capUsed; no change
		AssertUtil.assertDouble(capUsed, position.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(maxCapUsed, position.details().maxCapUsed(), "maxCapUsed");
		assertEquals(++tradeCount, position.details().tradeCount());
	}
	
	@Test
	public void testHandleTrade(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();

		// When
		addSomeTradesWithZeroFeesAndCommission(position, changeTracker);
	}
	
	@Test
	public void testHandleOrder(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		addSomeTradesWithZeroFeesAndCommission(position, changeTracker);
		
		// When: net buy quantity
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		OrderSbeEncoder orderEncoder = new OrderSbeEncoder();
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();
		
		int sid = 9000001;
		Side side = Side.BUY;
		long secSid = 1000001;
		int limitPrice = 15000;
		int stopPrice = 0;
		int quantity = 1000;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderStatus status = OrderStatus.NEW;
		TimeInForce tif = TimeInForce.DAY;
		OrderType orderType = OrderType.LIMIT_ORDER;
		MessageSinkRef ownerSinkRef = DummyMessageSink.refOf(1, 1, "dummy", ServiceType.ClientService);
		Order order = Order.of(secSid, ownerSinkRef, sid, quantity, isAlgo, orderType, side, limitPrice, stopPrice, tif, status);
	    int channelId = 5;
		order.channelId(channelId);
		long channelSeq = 123456;
		order.channelSeq(channelSeq);
		OrderUtil.populateFrom(orderEncoder, buffer, 0, order, stringBuffer);
		orderDecoder.wrap(buffer, 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		position.handleOrder(orderDecoder, changeTracker);
		
		int tradeCount = 4;
		assertEquals(tradeCount, position.details().tradeCount());
		LOG.debug("{}", position.details());

	}
	
	@Test
	public void testHandlePriceForOneTrade(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.inBps(0.27, 10, 0.5, 0.5, 0.2, 2, 100, 1);
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		Side side = Side.BUY;
		int price = 140;
		int qty = 300_000;
		TradeStatus tradeStatus = TradeStatus.NEW;
		addTrade(position, changeTracker, entitySid, side, price, qty, tradeStatus);
		
		int expectedOpenPosition = 300_000;
		double expectedAvgBuyPrice = 0.140;
		double expectedAvgSellPrice = 0;
		double expectedNetRealizedPnl = -51.934;
		double expectedUnrealizedPnl = 0.0;
		double expectedTotalPnl = expectedNetRealizedPnl + expectedUnrealizedPnl;
		int expectedBuyQty = 300_000;
		int expectedSellQty = 0;
		double expectedCapUsed = ServiceConstant.INT_PRICE_SCALING_FACTOR * price * qty;
		assertEquals(expectedOpenPosition, position.details().openPosition());
		assertDouble(expectedAvgSellPrice, position.details().avgSellPrice(), tolerance, "avgSellPrice");
		assertDouble(expectedAvgBuyPrice, position.details().avgBuyPrice(), tolerance, "avgBuyPrice");
		assertDouble(expectedNetRealizedPnl, position.details().netRealizedPnl(), tolerance, "netRealizedPnl");
		assertDouble(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertDouble(expectedTotalPnl, position.details().totalPnl(), tolerance, "totalPnl");
		assertEquals(expectedBuyQty, position.details().buyQty());
		assertEquals(expectedSellQty, position.details().sellQty());
		assertFalse(position.details().hasMtmBuyPrice());
		assertFalse(position.details().hasMtmSellPrice());
		AssertUtil.assertDouble(expectedCapUsed, position.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(expectedCapUsed, position.details().maxCapUsed(), "maxCapUsed");
	}
	
	@Test
	public void testHandlePrice(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		addSomeTradesWithZeroFeesAndCommission(position, changeTracker);
		int tradeCount = 4;

		int expectedOpenPosition = 500;
		double expectedAvgBuyPrice = 17.5;
		double expectedAvgSellPrice = 17.0;
		double expectedNetRealizedPnl = -750.0;
		double expectedUnrealizedPnl = 0.0;
		double expectedTotalPnl = expectedNetRealizedPnl + expectedUnrealizedPnl;
		int expectedBuyQty = 2000;
		int expectedSellQty = 1500;
		assertEquals(expectedOpenPosition, position.details().openPosition());
		assertTrue(DoubleMath.fuzzyEquals(expectedAvgSellPrice, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedAvgBuyPrice, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedNetRealizedPnl, position.details().netRealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedTotalPnl, position.details().totalPnl(), tolerance));
		assertEquals(expectedBuyQty, position.details().buyQty());
		assertEquals(expectedSellQty, position.details().sellQty());
		assertFalse(position.details().hasMtmBuyPrice());
		assertFalse(position.details().hasMtmSellPrice());

		// When: net buy quantity
		int ask = 15000;
		int bid = 15000;
		int last = 0;
		updatePrice(position, changeTracker, bid, ask, last);
		expectedUnrealizedPnl = (expectedBuyQty - expectedSellQty) * (ServiceConstant.INT_PRICE_SCALING_FACTOR * ask - expectedAvgBuyPrice);
		
		assertEquals(ask, (int)(position.details().mtmSellPrice() / ServiceConstant.INT_PRICE_SCALING_FACTOR));
		assertEquals(bid, (int)(position.details().mtmBuyPrice() / ServiceConstant.INT_PRICE_SCALING_FACTOR));
		assertTrue(position.details().hasMtmBuyPrice());
		assertTrue(position.details().hasMtmSellPrice());
		assertTrue(DoubleMath.fuzzyEquals(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedNetRealizedPnl, position.details().netRealizedPnl(), tolerance));

		// When set to zero to see if anything breaks
		ask = 0;
		bid = 0;
		last = 0;
		updatePrice(position, changeTracker, bid, ask, last);

		// Expect no change
		// expectedUnrealizedPnl = (expectedBuyQty - expectedSellQty) * (- expectedAvgBuyPrice);
		assertTrue(position.details().hasMtmBuyPrice());
		assertTrue(position.details().hasMtmSellPrice());
		assertTrue(DoubleMath.fuzzyEquals(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance));

		// Changing bid has no impact
		expectedUnrealizedPnl = 41250;
		bid = 100000;
		updatePrice(position, changeTracker, bid, ask, last);
		assertTrue(position.details().hasMtmBuyPrice());
		assertTrue(position.details().hasMtmSellPrice());
		assertDouble(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertDouble(expectedNetRealizedPnl, position.details().netRealizedPnl(), tolerance, "netRealizedPnl");
		
		// When: net quantity is zero (+500 sell)
		int sid = 9000010;
		Side side = Side.SELL;
		int orderSid = 8000011;
		int orderId = 7000011;
		long secSid = 1000001;
		int leavesQty = 0;
		int cumulativeQty = 1000;
		String executionId = "250011";
		int executionPrice = (int)(expectedAvgSellPrice / ServiceConstant.INT_PRICE_SCALING_FACTOR); 
		int executionQty = 500;
		OrderStatus orderStatus = OrderStatus.FILLED;
		TradeStatus tradeStatus = TradeStatus.NEW;
		long createTime = LocalTime.now().toNanoOfDay();
		long updateTime = LocalTime.now().toNanoOfDay();
		Trade trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, executionId,
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);

		MutableDirectBuffer tradeBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		TradeSbeEncoder tradeEncoder = new TradeSbeEncoder();
		TradeSbeDecoder tradeDecoder = new TradeSbeDecoder();
		TradeUtil.populateFrom(tradeEncoder, tradeBuffer, 0, trade, tradeDecoder, stringBuffer);
		position.handleTrade(tradeDecoder, changeTracker);
		tradeCount++;
		
		expectedUnrealizedPnl = 0;
		expectedNetRealizedPnl = -1000;
		assertDouble(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertDouble(expectedNetRealizedPnl, position.details().netRealizedPnl(), tolerance, "netRealizedPnl");
		
		// When : net sell quantity
		sid = 9000012;
		orderSid = 8000012;
		orderId = 7000012;
		executionId = "250012";
		trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, executionId,
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);
		TradeUtil.populateFrom(tradeEncoder, tradeBuffer, 0, trade, tradeDecoder, stringBuffer);
		position.handleTrade(tradeDecoder, changeTracker);
		tradeCount++;
		
		// net sell * (avg sell - mtm buy)
		expectedUnrealizedPnl = (500) * (expectedAvgSellPrice - ServiceConstant.INT_PRICE_SCALING_FACTOR * bid);
		expectedUnrealizedPnl = 8500;
		assertDouble(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		
		// When: change in ask price doesn't change unrealized pnl
		ask = 200000;
		updatePrice(position, changeTracker, bid, ask, last);
		assertTrue(position.details().hasMtmBuyPrice());
		assertTrue(position.details().hasMtmSellPrice());
		expectedUnrealizedPnl = -91500;
		assertDouble(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		
		// When: change in bid price, change in unrealized pnl
		bid = 10000;
		updatePrice(position, changeTracker, bid, ask, last);
		expectedUnrealizedPnl = -91500;
		assertDouble(expectedUnrealizedPnl, position.details().unrealizedPnl(), tolerance, "unrealizedPnl");
		assertEquals(tradeCount, position.details().tradeCount());
	}

	private void updatePrice(SecurityPosition position, SecurityPositionChangeTracker changeTracker, int bid, int ask, int last){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		BoobsSbeEncoder encoder = new BoobsSbeEncoder();
		BoobsSbeDecoder decoder = new BoobsSbeDecoder();
		encoder.wrap(buffer, 0)
		.ask(ask)
		.bid(bid)
		.last(last);
		decoder.wrap(buffer, 0, BoobsSbeDecoder.BLOCK_LENGTH, BoobsSbeDecoder.SCHEMA_VERSION);
		position.handleBoobs(decoder, changeTracker);
	}

	private void updateSecurity(SecurityPosition position, SecurityPositionChangeTracker changeTracker, long secSid, int issuerSid, long undSid, SecurityType secType){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		SecuritySbeEncoder encoder = new SecuritySbeEncoder();
		SecuritySbeDecoder decoder = new SecuritySbeDecoder();
		encoder.wrap(buffer, 0)
		.sid(secSid)
		.issuerSid(issuerSid)
		.undSid(undSid)
		.securityType(secType);
		decoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);
		position.handleSecurity(decoder, schedules, changeTracker);
	}
	
	@Test
	public void giveNoneWhenReceiveOneTradeThenOK(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		
		// When
		addTrade(position, changeTracker, entitySid, Side.BUY, 100000, 1000, TradeStatus.NEW);
		
		// Then
		assertEquals(1000, position.details().openPosition());
		assertEquals(1000, position.details().buyQty());
		assertEquals(0, position.details().sellQty());
		assertEquals(1, position.details().tradeCount());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(100000 * ServiceConstant.INT_PRICE_SCALING_FACTOR, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().netRealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().totalPnl(), tolerance));
		assertFalse(position.details().hasMtmBuyPrice());
		assertFalse(position.details().hasMtmSellPrice());
	}
	
	@Test
	public void givenOneTradeWhenReceiveOneTradeOfSameSecurityThenFiguresAddUp(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		addTrade(position, changeTracker, entitySid, Side.BUY, 100000, 1000, TradeStatus.NEW);

		// When
		addTrade(position, changeTracker, entitySid, Side.SELL, 200000, 5000, TradeStatus.NEW);
		
		// Then
		assertEquals(-4000, position.details().openPosition());
		assertEquals(1000, position.details().buyQty());
		assertEquals(5000, position.details().sellQty());
		assertTrue(DoubleMath.fuzzyEquals(100, position.details().avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(200, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(1000 * (200 - 100), position.details().netRealizedPnl(), tolerance));
		assertEquals(2, position.details().tradeCount());
	}
	
	@Test
	public void givenOneTradeWhenReceiveOneTradeOfOppositeSideOfSameSecurityThenFiguresAddUp(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		addTrade(position, changeTracker, entitySid, Side.BUY, 100000, 1000, TradeStatus.NEW);
		
		// When
		addTrade(position, changeTracker, entitySid, Side.BUY, 100000, 1000, TradeStatus.CANCELLED);
		
		// Then
		assertEquals(0, position.details().openPosition());
		assertEquals(0, position.details().buyQty());
		assertEquals(0, position.details().sellQty());
		assertEquals(0, position.details().tradeCount());
		
		assertTrue("Expect NaN but " + position.details().avgBuyPrice(), Double.isNaN(position.details().avgBuyPrice()));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().netRealizedPnl(), tolerance));
	}

	@Test
	public void givenTypicalFlowsNoPriceThenPrice(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		int quantity = 300_000;
		int price = 217;
		Side side = Side.BUY;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		
		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(0, position.details().experimentalNetRealizedPnl(), "expNetRealized");
		
		int last = 0;
		int ask = 217;
		int bid = 214;
		updatePrice(position, changeTracker, bid, ask, last);
		
	}

	@Test
	public void givenTypicalFlowsThatReceiveSecurityUpdate(){
		long secSid = 10000;
		SecurityPosition position = SecurityPosition.of(secSid);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		int quantity = 300_000;
		int price = 217;
		Side side = Side.BUY;
		addTrade(position, changeTracker, secSid, side, price, quantity, TradeStatus.NEW);
		
		int bid = 215;
		int ask = 217;
		int last = 217;
		updatePrice(position, changeTracker, bid, ask, last);
		
		AssertUtil.assertDouble(-600, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(0, position.details().experimentalNetRealizedPnl(), "expNetRealized");
		
		SecurityType secType = SecurityType.CBBC;
		long undSid = 10001;
		int issuerSid = 3000;
		updateSecurity(position, changeTracker, secSid, issuerSid, undSid, secType);

		AssertUtil.assertDouble(-600, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(0, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 215;
		side = Side.SELL;
		addTrade(position, changeTracker, secSid, side, price, quantity, TradeStatus.NEW);
		bid = 216;
		ask = 218;
		updatePrice(position, changeTracker, bid, ask, last);

		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-600, position.details().experimentalNetRealizedPnl(), "expNetRealized");
		
		updateSecurity(position, changeTracker, secSid, issuerSid, undSid, secType);

		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-600, position.details().experimentalNetRealizedPnl(), "expNetRealized");
	}
	
	@Test
	public void givenTypicalFlows(){
		// Given
		long entitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition position = SecurityPosition.of(entitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		int quantity = 300_000;
		int price = 217;
		Side side = Side.BUY;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		
		int bid = 215;
		int ask = 217;
		int last = 217;
		updatePrice(position, changeTracker, bid, ask, last);
		
		AssertUtil.assertDouble(-600, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(0, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 215;
		side = Side.SELL;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		bid = 216;
		ask = 218;
		updatePrice(position, changeTracker, bid, ask, last);

		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-600, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 200;
		side = Side.BUY;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		bid = 217;
		ask = 219;
		updatePrice(position, changeTracker, bid, ask, last);

		AssertUtil.assertDouble(5100, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-600, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 198;
		side = Side.SELL;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);

		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-1200, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 201;
		side = Side.BUY;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);

		AssertUtil.assertDouble(4800, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-1200, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 204;
		side = Side.SELL;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		bid = 216;
		ask = 218;
		updatePrice(position, changeTracker, bid, ask, last);

		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-300, position.details().experimentalNetRealizedPnl(), "expNetRealized");
		
		price = 205;
		side = Side.BUY;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		
		AssertUtil.assertDouble(3300, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(-300, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 206;
		side = Side.SELL;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		
		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(0, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 205;
		side = Side.BUY;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		
		AssertUtil.assertDouble(3300, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(0, position.details().experimentalNetRealizedPnl(), "expNetRealized");

		price = 207;
		side = Side.SELL;
		addTrade(position, changeTracker, entitySid, side, price, quantity, TradeStatus.NEW);
		bid = 215;
		ask = 217;
		updatePrice(position, changeTracker, bid, ask, last);

		AssertUtil.assertDouble(0, position.details().experimentalUnrealizedPnl(), "expUnrealized");
		AssertUtil.assertDouble(600, position.details().experimentalNetRealizedPnl(), "expNetRealized");

	}

	public void givenOneTradeWhenReceiveOneTradeOfSameUnderlyingThenFiguresAddUp() {
		
	}
	
	public void givenOneTradeWhenReceiveOneTradeOfSameIssuerThenFiguresAddUp(){
		
	}

	public void givenOneTradeWhenReceiveOneTradeOfSameFirmThenFiguresAddUp(){
		
	}
	
	public static void assertDouble(double expected, double actual, double tolerance, String field){
		assertTrue(field + ": expected(" + expected + ")" + ", actual(" + actual + ")", DoubleMath.fuzzyEquals(expected, actual, tolerance));
	}
}