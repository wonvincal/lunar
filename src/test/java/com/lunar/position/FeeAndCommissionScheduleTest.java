package com.lunar.position;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.math.DoubleMath;

public class FeeAndCommissionScheduleTest {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(FeeAndCommissionScheduleTest.class);
	private final double bpsFactor = 0.01 * 0.01;
	private final double tolerance = 0.000001;

	@Test
	public void testZero(){
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.of();
		double notional = 100d;
		
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.clearingFeeOf(notional), 0.0000001d));
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.allFeesAndCommissionOf(notional), 0.0000001d));
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.commissionOf(notional), 0.0000001d));
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.feesOf(notional), 0.0000001d));
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.levyOf(notional), 0.0000001d));
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.stampOf(notional), 0.0000001d));
		assertTrue(DoubleMath.fuzzyEquals(0, schedule.tradingFeeOf(notional), 0.0000001d));
	}

	@Test
	public void testStock(){
		double levyInBps = 0.27;
		double stampInBps = 10;
		double tradingInBps = 0.5;
		double tradingTariff = 0.5;
		double clearingInBps = 0.2;
		double minClearing = 2;
		double maxClearing = 1000;
		double commissionInBps = 1;
		FeeAndCommissionSchedule schedule = FeeAndCommissionSchedule.inBps(
				levyInBps /* levy */, 
				stampInBps /* stamp */, 
				tradingInBps /* trading fee */, 
				tradingTariff /* trading tariff */,
				clearingInBps /* clearing fee */, 
				minClearing /* min clearing fee */, 
				maxClearing /* max clearing fee */, 
				commissionInBps /* commission rate */);
		double notional = 1000000d;
		
		double expectedLevy = notional * levyInBps * bpsFactor;
		double expectedStamp = notional * stampInBps * bpsFactor;
		double expectedTradingFee = notional * tradingInBps * bpsFactor;
		double expectedTradingTariff = 0.5;		
		double expectedCommission = notional * commissionInBps * bpsFactor;
		double expectedClearing = notional * clearingInBps * bpsFactor;
		expectedClearing = Math.max(minClearing, expectedClearing);
		expectedClearing = Math.min(maxClearing, expectedClearing);
		double expectedFees = expectedClearing + expectedLevy + expectedStamp + expectedTradingFee + expectedTradingTariff;
		assertTrue(DoubleMath.fuzzyEquals(expectedLevy, schedule.levyOf(notional), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedStamp, schedule.stampOf(notional), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedTradingFee, schedule.tradingFeeOf(notional), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedCommission, schedule.commissionOf(notional), tolerance));
		double expectedFeesAndCommission = expectedFees + expectedCommission;
		
		assertTrue(DoubleMath.fuzzyEquals(expectedFees, schedule.feesOf(notional), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(expectedFeesAndCommission, schedule.allFeesAndCommissionOf(notional), tolerance));
		
		double smallNotional = 100;
		double bigNotional = 1000000000d;
		assertTrue(DoubleMath.fuzzyEquals(minClearing, schedule.clearingFeeOf(smallNotional), tolerance));
		assertTrue(DoubleMath.fuzzyEquals(maxClearing, schedule.clearingFeeOf(bigNotional), tolerance));
	}
}
