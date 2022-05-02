package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.math.DoubleMath;
import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeEncoder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.sender.PositionSender;
import com.lunar.position.FeeAndCommissionSchedule;
import com.lunar.position.SecurityPositionDetails;
import com.lunar.service.ServiceConstant;
import com.lunar.util.AssertUtil;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PositionFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(SecurityFbsEncoderTest.class);

	private static double tolerance = 0.00001d;

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final PositionSbeEncoder sbeEncoder = new PositionSbeEncoder();
	private final PositionSbeDecoder sbeDecoder = new PositionSbeDecoder();
	private final UnsafeBuffer sbeStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE));
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
		long refEntitySid = 1001;
		EntityType refEntityType = EntityType.SECURITY;
		PutOrCall refPutOrCall = PutOrCall.NULL_VAL;
		long openPosition = 100;
		long openCallPosition = 0;
		long openPutPosition = 0;
		long buyQty = 200;
		double buyNotional = 2000;
		long sellQty = 100;
		double sellNotional = 1000;
		long osBuyQty = 300;
		double osBuyNotional = 6000;
		long osSellQty = 400;
		double osSellNotional = 8000;
		double fees = 0;
		double commission = 0;
		double totalPnl = 100;
		double avgBuyPrice = 10;
		double avgSellPrice = 10;
		double mtmBidPrice = 10;
		double mtmAskPrice = 10;
		int tradeCount = 1;
		double capUsed = 100;
		double maxCapUsed = 200;
		SecurityPositionDetails current = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall, openPosition, openCallPosition, openPutPosition, buyQty, buyNotional, sellQty, sellNotional, 
				osBuyQty, osBuyNotional, osSellQty, osSellNotional, capUsed, maxCapUsed, fees, commission, 
				totalPnl, avgBuyPrice, avgSellPrice, mtmBidPrice, mtmAskPrice, tradeCount);
		
		double netRealizedPnl = 123.45;
		double unrealizedPnl = -111.11;
		current.netRealizedPnl(netRealizedPnl);
		current.unrealizedPnl(unrealizedPnl);
		
		double expNetRealizedPnl = 876.45;
		double expUnrealizedPnl = -222.11;
		current.experimentalNetRealizedPnl(expNetRealizedPnl);
		current.unrealizedPnl(expUnrealizedPnl);
		
		PositionSender.encodeSecurityPositionOnly(buffer, 0, sbeStringBuffer, sbeEncoder, current);
		sbeDecoder.wrap(buffer, 0, PositionSbeDecoder.BLOCK_LENGTH, PositionSbeDecoder.SCHEMA_VERSION);
		
		int offset = PositionFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		PositionFbs positionFbs = PositionFbs.getRootAsPositionFbs(builder.dataBuffer());
		assertPosition(current, positionFbs);
	}
	
	public static void assertPosition(SecurityPositionDetails details, PositionFbs positionFbs){
		assertTrue(DoubleMath.fuzzyEquals(details.commission(), positionFbs.commission(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.fees(), positionFbs.fees(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.mtmBuyPrice(), positionFbs.mtmBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.mtmSellPrice(), positionFbs.mtmSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.avgBuyPrice(), positionFbs.avgBuyPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.avgSellPrice(), positionFbs.avgSellPrice(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.netRealizedPnl(), positionFbs.netRealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.unrealizedPnl(), positionFbs.unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.experimentalNetRealizedPnl(), positionFbs.experimentalNetRealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.experimentalUnrealizedPnl(), positionFbs.experimentalUnrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.totalPnl(), positionFbs.totalPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(details.experimentalTotalPnl(), positionFbs.experimentalTotalPnl(), tolerance));
		AssertUtil.assertDouble(details.buyNotional(), positionFbs.buyNotional(), "buyNotional");
		AssertUtil.assertDouble(details.sellNotional(), positionFbs.sellNotional(), "sellNotional");
		AssertUtil.assertDouble(details.capUsed(), positionFbs.capUsed(), "capUsed");
		AssertUtil.assertDouble(details.maxCapUsed(), positionFbs.maxCapUsed(), "maxCapUsed");
		assertEquals(details.entitySid(), positionFbs.entitySid());
		assertEquals(details.entityType().value(), positionFbs.entityType());
		assertEquals(details.buyQty(), positionFbs.buyQty());
		assertEquals(details.sellQty(), positionFbs.sellQty());
		assertEquals(details.osBuyQty(), positionFbs.osBuyQty());
		assertEquals(details.osSellQty(), positionFbs.osSellQty());
		assertEquals(details.tradeCount(), positionFbs.tradeCount());
		assertEquals(details.openPosition(), positionFbs.openPosition());
	}
}
