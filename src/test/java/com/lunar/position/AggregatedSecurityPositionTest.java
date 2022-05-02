package com.lunar.position;

import static org.junit.Assert.assertEquals;
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

public class AggregatedSecurityPositionTest {
	private static final Logger LOG = LogManager.getLogger(AggregatedSecurityPositionTest.class);
	private static double tolerance = 0.0001;
	private static MutableDirectBuffer stringBuffer = new UnsafeBuffer(ByteBuffer.allocate(128));
	
	@Test
	public void testCreate(){
		long entitySid = 10000;
		EntityType entityType = EntityType.ISSUER;
		AggregatedSecurityPosition position = AggregatedSecurityPosition.of(entitySid, entityType);
		assertNotNull(position);
		assertEquals(entitySid, position.details().entitySid());
		assertEquals(EntityType.ISSUER, position.details().entityType());
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().commission(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().fees(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().netRealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, position.details().totalPnl(), tolerance));
		assertEquals(0, position.details().tradeCount());
	}

	@Test
	public void testUpdateFromOnePosition(){
		long entitySid = 10000;
		EntityType entityType = EntityType.ISSUER;
		AggregatedSecurityPosition issuerPosition = AggregatedSecurityPosition.of(entitySid, entityType);
		
		long secEntitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition secPosition = SecurityPosition.of(secEntitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		
		secPosition.addHandler(issuerPosition);
		
		Side side = Side.BUY;
		int executionPrice = 15000;
		int executionQty = 1000;
		addTrade(secPosition, changeTracker, secEntitySid, side, executionPrice, executionQty, TradeStatus.NEW);
				
		assertEquals(1, secPosition.details().tradeCount());
		assertEquals(1, issuerPosition.details().tradeCount());
	}
	

	private AtomicInteger orderSidSeq = new AtomicInteger(7000000);
	private AtomicInteger orderIDSeq = new AtomicInteger(8000000);
	private AtomicInteger executionIdSeq = new AtomicInteger(9000000);
	public void addTrade(SecurityPosition position, SecurityPositionChangeTracker changeTracker, long secSid, Side side, int executionPrice, int executionQty, TradeStatus tradeStatus){
		int sid = tradeSidSeq.getAndIncrement();
		int orderSid = orderSidSeq.getAndIncrement();
		int orderId = orderIDSeq.getAndIncrement();
		int leavesQty = 0;
		int cumulativeQty = executionQty;
		String executionId = Integer.toString(executionIdSeq.getAndIncrement());
		OrderStatus orderStatus = OrderStatus.FILLED;
		long createTime = LocalTime.now().toNanoOfDay();
		long updateTime = LocalTime.now().toNanoOfDay();
		Trade trade = Trade.of(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, executionId,
				executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);	
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		TradeSbeEncoder encoder = new TradeSbeEncoder();
		TradeSbeDecoder decoder = new TradeSbeDecoder();
		TradeUtil.populateFrom(encoder, buffer, 0, trade, decoder, stringBuffer);
		position.handleTrade(decoder, changeTracker);
	}

	@Test
	public void testUpdateFromMultiplePositions(){
		long entitySid = 10000;
		EntityType entityType = EntityType.ISSUER;
		AggregatedSecurityPosition issuerPosition = AggregatedSecurityPosition.of(entitySid, entityType);
		
		long secEntitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition secPosition = SecurityPosition.of(secEntitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		secPosition.addHandler(issuerPosition);

		long secEntitySid2 = 10001;
		SecurityPosition secPosition2 = SecurityPosition.of(secEntitySid2, securityType, putOrCall, undSecSid, issuerSid, schedule);
		secPosition2.addHandler(issuerPosition);
		
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		
		Side side = Side.BUY;
		int executionPrice = 15000;
		int executionQty = 1000;
		addTrade(secPosition, changeTracker, secEntitySid, side, executionPrice, executionQty, TradeStatus.NEW);
		
		addTrade(secPosition2, changeTracker, secEntitySid2, side, executionPrice, executionQty, TradeStatus.NEW);
		
		addTrade(secPosition2, changeTracker, secEntitySid2, side, executionPrice, executionQty, TradeStatus.NEW);
		
		assertEquals(1, secPosition.details().tradeCount());
		assertEquals(2, secPosition2.details().tradeCount());
		assertEquals(3, issuerPosition.details().tradeCount());
	}

	private final AtomicInteger tradeSidSeq = new AtomicInteger(9000000);

	private void addOrder(SecurityPosition position, SecurityPositionChangeTracker changeTracker, long secSid, Side side, int price, int quantity, OrderStatus orderStatus){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		OrderSbeEncoder orderEncoder = new OrderSbeEncoder();
		OrderSbeDecoder orderDecoder = new OrderSbeDecoder();

		int sid = orderSidSeq.get();
		int stopPrice = 0;
		BooleanType isAlgo = BooleanType.FALSE;
		OrderStatus status = OrderStatus.NEW;
		TimeInForce tif = TimeInForce.DAY;
		OrderType orderType = OrderType.LIMIT_ORDER;
		MessageSinkRef ownerSinkRef = DummyMessageSink.refOf(1, 1, "dummy", ServiceType.ClientService);
		Order order = Order.of(secSid, ownerSinkRef, sid, quantity, isAlgo, orderType, side, price, stopPrice, tif, status);
	    int channelId = 5;
		order.channelId(channelId);
		long channelSeq = 123456;
		order.channelSeq(channelSeq);
		OrderUtil.populateFrom(orderEncoder, buffer, 0, order, stringBuffer);
		orderDecoder.wrap(buffer, 0, OrderSbeDecoder.BLOCK_LENGTH, OrderSbeDecoder.SCHEMA_VERSION);
		position.handleOrder(orderDecoder, changeTracker);
	}

	private void updatePrice(SecurityPosition position, SecurityPositionChangeTracker changeTracker, long secSid, int bid, int ask, int last){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		BoobsSbeEncoder encoder = new BoobsSbeEncoder();
		BoobsSbeDecoder decoder = new BoobsSbeDecoder();
		encoder.wrap(buffer, 0)
		.ask(ask)
		.bid(bid)
		.last(last)
		.secSid(secSid);
		decoder.wrap(buffer, 0, BoobsSbeDecoder.BLOCK_LENGTH, BoobsSbeDecoder.SCHEMA_VERSION);
		position.handleBoobs(decoder, changeTracker);
	}
	
	private void updateSecurity(SecurityPosition position, 
			EnumMap<SecurityType, FeeAndCommissionSchedule> schedules,
			SecurityPositionChangeTracker changeTracker, 
			long secSid,
			SecurityType secType,
			long undSid,
			int issuerSid,
			int firmId){
		MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		SecuritySbeEncoder encoder = new SecuritySbeEncoder();
		SecuritySbeDecoder decoder = new SecuritySbeDecoder();
		encoder.wrap(buffer, 0)
			.sid(secSid)
			.undSid(undSid)
			.issuerSid(issuerSid)
			.securityType(secType);
		decoder.wrap(buffer, 0, SecuritySbeDecoder.BLOCK_LENGTH, SecuritySbeDecoder.SCHEMA_VERSION);	
		position.handleSecurity(decoder, schedules, changeTracker);
	}
	
	@Test
	public void testUpdatesOfDifferentTypesFromOneSecurityPosition(){
		// Given
		long entitySid = 10000;
		EntityType entityType = EntityType.ISSUER;
		AggregatedSecurityPosition issuerPosition = AggregatedSecurityPosition.of(entitySid, entityType);

		// When
		long secEntitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition secPosition = SecurityPosition.of(secEntitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		secPosition.addHandler(issuerPosition);
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		
		int quantity = 500;
		double capUsed = 100000 * quantity * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		double maxCapUsed = capUsed;
		addTrade(secPosition, changeTracker, secEntitySid, Side.BUY, 100000, quantity, TradeStatus.NEW);
		assertEquals(1, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().experimentalNetRealizedPnl(), "expNetRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().experimentalUnrealizedPnl(), "expUnrealizedPnl");
		AssertUtil.assertDouble(capUsed, issuerPosition.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(maxCapUsed, issuerPosition.details().maxCapUsed(), "maxCapUsed");
		
		addOrder(secPosition, changeTracker, secEntitySid, Side.BUY, 100000, 100, OrderStatus.NEW);
		capUsed += 100000 * 100 * ServiceConstant.INT_PRICE_SCALING_FACTOR;
		maxCapUsed = capUsed;
		assertEquals(1, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().experimentalNetRealizedPnl(), "expNetRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().experimentalUnrealizedPnl(), "expUnrealizedPnl");
		AssertUtil.assertDouble(capUsed, issuerPosition.details().capUsed(), "capUsed");
		AssertUtil.assertDouble(maxCapUsed, issuerPosition.details().maxCapUsed(), "maxCapUsed");

		updatePrice(secPosition, changeTracker, secEntitySid, 110000, 120000, 120000);
		assertEquals(1, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(5000, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().experimentalNetRealizedPnl(), "expNetRealizedPnl");
		AssertUtil.assertDouble(5000, issuerPosition.details().experimentalUnrealizedPnl(), "expUnrealizedPnl");
				
		addTrade(secPosition, changeTracker, secEntitySid, Side.BUY, 100000, 100, TradeStatus.NEW);
		assertEquals(2, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(6000, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
		
		// No change
		addOrder(secPosition, changeTracker, secEntitySid, Side.BUY, 100000, 100, OrderStatus.NEW);
		assertEquals(2, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(6000, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");

		updatePrice(secPosition, changeTracker, secEntitySid, 120000, 130000, 130000);
		assertEquals(2, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(12000, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");

		addTrade(secPosition, changeTracker, secEntitySid, Side.SELL, 120000, 100, TradeStatus.NEW);
		assertEquals(3, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(100 * 20, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(10000, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
		AssertUtil.assertDouble(100 * 20, issuerPosition.details().experimentalNetRealizedPnl(), "expNetRealizedPnl");
		AssertUtil.assertDouble(10000, issuerPosition.details().experimentalUnrealizedPnl(), "expUnrealizedPnl");
		LOG.info("Issuer position [{}]", issuerPosition.details());
	}

	@Test
	public void testUpdatesOfDifferentTypesFromDifferentSecurityPosition(){
		// Given
		long entitySid = 10000;
		EntityType entityType = EntityType.ISSUER;
		AggregatedSecurityPosition issuerPosition = AggregatedSecurityPosition.of(entitySid, entityType);

		// When
		long secEntitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		PutOrCall putOrCall = PutOrCall.CALL;
		long undSecSid = 10001;
		int issuerSid = 3000;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION;
		SecurityPosition secPosition = SecurityPosition.of(secEntitySid, securityType, putOrCall, undSecSid, issuerSid, schedule);
		secPosition.addHandler(issuerPosition);

		long secEntitySid2 = 10000;
		SecurityPosition secPosition2 = SecurityPosition.of(secEntitySid2, securityType, putOrCall, undSecSid, issuerSid, schedule);
		secPosition2.addHandler(issuerPosition);
		
		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		addTrade(secPosition, changeTracker, secEntitySid, Side.BUY, 100000, 500, TradeStatus.NEW);
		assertEquals(1, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");

		addTrade(secPosition2, changeTracker, secEntitySid, Side.BUY, 100000, 500, TradeStatus.NEW);
		assertEquals(2, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(0, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");

		addTrade(secPosition, changeTracker, secEntitySid, Side.SELL, 90000, 400, TradeStatus.NEW);
		assertEquals(3, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(400 * (90000 - 100000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");

		addOrder(secPosition, changeTracker, secEntitySid, Side.SELL, 110000, 100, OrderStatus.NEW);
		assertEquals(3, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(400 * (90000 - 100000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(0, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
		
		updatePrice(secPosition, changeTracker, secEntitySid, 120000, 130000, 130000);
		LOG.debug("secPosition: {}", secPosition.details());
		LOG.debug("issuerPosition: {}", issuerPosition.details());
		assertEquals(3, issuerPosition.details().tradeCount());
		AssertUtil.assertDouble(0, issuerPosition.details().commission(), "commission");
		AssertUtil.assertDouble(0, issuerPosition.details().fees(), "fees");
		AssertUtil.assertDouble(400 * (90000 - 100000) * ServiceConstant.INT_PRICE_SCALING_FACTOR, issuerPosition.details().netRealizedPnl(), "netRealizedPnl");
		AssertUtil.assertDouble(2000, issuerPosition.details().unrealizedPnl(), "unrealizedPnl");
	}
		
	@Test
	public void givenNoSecInfoAndReceiveTradeWhenReceiveSecInfoThenAggPositionIsStillValid(){
		long secEntitySid = 10000;
		SecurityType securityType = SecurityType.CBBC;
		long undSid = 10001;
		int issuerSid = 3000;
		int firmId = 1212121;
		SecurityPosition secPos = SecurityPosition.of(secEntitySid);

		SecurityPositionChangeTracker changeTracker = SecurityPositionChangeTracker.of();
		addTrade(secPos, changeTracker, secEntitySid, Side.BUY, 100000, 500, TradeStatus.NEW);
		assertEquals(1, secPos.details().tradeCount());
		assertEquals(500, secPos.details().buyQty());
		
		EnumMap<SecurityType, FeeAndCommissionSchedule> schedules = new EnumMap<>(SecurityType.class);
		schedules.put(SecurityType.STOCK, FeeAndCommissionSchedule.inBps(0.27, 10, 0.5, 0.5, 0.2, 2, 100, 1));
		FeeAndCommissionSchedule warrantFees = FeeAndCommissionSchedule.inBps(0.27, 0, 0.5, 0.5, 0.2, 2, 100, 1);
		schedules.put(SecurityType.WARRANT, warrantFees);
		schedules.put(SecurityType.CBBC, warrantFees);

		AggregatedSecurityPosition undPos = AggregatedSecurityPosition.of(undSid, EntityType.UNDERLYING);
		secPos.addHandler(undPos);
		undPos.handleChange(secPos.details(), SecurityPositionDetails.of());
		assertEquals(500, undPos.details().buyQty());
		
		updateSecurity(secPos, schedules, changeTracker, secEntitySid, securityType, undSid, issuerSid, firmId);
		assertEquals(500, secPos.details().buyQty());
		assertEquals(500, undPos.details().buyQty());
	}
}
