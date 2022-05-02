package com.lunar.position;

import static org.junit.Assert.*;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.math.DoubleMath;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.PutOrCall;

public class SecurityPositionDetailsTest {
	private static final Logger LOG = LogManager.getLogger(SecurityPositionDetailsTest.class);
	private final double tolerance = 0.0000001;
	
	@Test
	public void test(){
		double levyInBps = 0.27;
		double stampInBps = 10;
		double tradingFeeInBps = 0.5;
		double tradingTariff = 0.5;
		double clearingInBps = 0.2;
		double minClearing = 2;
		double maxClearing = 1000;
		double commissionInBps = 1;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.inBps(
				levyInBps /* levy */, 
				stampInBps /* stamp */, 
				tradingFeeInBps /* trading fee */, 
				tradingTariff /* trading tariff */,
				clearingInBps /* clearing fee */, 
				minClearing /* min clearing fee */, 
				maxClearing /* max clearing fee */, 
				commissionInBps /* commission rate */);
		
		long entitySid = 12345;
		EntityType entityType = EntityType.SECURITY;
		PutOrCall putOrCall = PutOrCall.NULL_VAL;
		
		SecurityPositionDetails details = SecurityPositionDetails.of(schedule, entitySid, entityType, putOrCall);
		details.updateNetRealizedPnl();
		details.updateUnrealizedPnl();
		
		// Initial figures
		assertTrue(DoubleMath.fuzzyEquals(0, details.unrealizedPnl(), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(0, details.netRealizedPnl(), tolerance));
		assertFalse(details.hasMtmSellPrice());
		assertFalse(details.hasMtmBuyPrice());
		assertEquals(0, details.tradeCount());

		LOG.debug("netRealizedPnl:{}, unrealizedPnl:{}", details.netRealizedPnl(), details.unrealizedPnl());;
	}
	
	@Test
	public void testMissingBidAsk(){
		double levyInBps = 0.27;
		double stampInBps = 10;
		double tradingFeeInBps = 0.5;
		double tradingTariff = 0.5;
		double clearingInBps = 0.2;
		double minClearing = 2;
		double maxClearing = 1000;
		double commissionInBps = 1;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.inBps(
				levyInBps /* levy */, 
				stampInBps /* stamp */, 
				tradingFeeInBps /* trading fee */, 
				tradingTariff /* trading tariff */,
				clearingInBps /* clearing fee */, 
				minClearing /* min clearing fee */, 
				maxClearing /* max clearing fee */, 
				commissionInBps /* commission rate */);
		
		long entitySid = 12345;
		EntityType entityType = EntityType.SECURITY;
		PutOrCall putOrCall = PutOrCall.NULL_VAL;
		
		SecurityPositionDetails details = SecurityPositionDetails.of(schedule, entitySid, entityType, putOrCall);

		long buyQty = 1000000;
		long sellQty = 500000;
		int tradeCount = 101;
		details.buyQty(buyQty).sellQty(sellQty).tradeCount(tradeCount);
		assertEquals(buyQty, details.buyQty());
		assertEquals(sellQty, details.sellQty());
		assertEquals(tradeCount, details.tradeCount());
	}	
}
