package com.lunar.position;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.lunar.entity.RiskControlSetting;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.PutOrCall;

public class RiskStateTest {
	private static final Logger LOG = LogManager.getLogger(RiskStateTest.class);
	@Test
	public void testCreate(){
		EntityType entityType = EntityType.FIRM;
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(entityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		long entitySid = 121211;
		RiskState.of(entitySid, entityType, setting);
	}
	
	@Test
	public void testDifferentChecks(){
		EntityType entityType = EntityType.FIRM;
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(entityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		long entitySid = 121211;
		RiskState state = RiskState.of(entitySid, entityType, setting);
	
		long openPosition = maxOpenPosition.get().longValue();
		assertEquals(RiskState.RESULT_OK, state.checkOpenPosition(openPosition));
		assertEquals(RiskState.RESULT_OK, state.checkOpenPosition(openPosition - 1));
		assertEquals(RiskState.RESULT_EXCEEDED, state.checkOpenPosition(openPosition + 1));
	
		double pnl = maxProfit.get().doubleValue();
		assertEquals(RiskState.RESULT_OK, state.checkMaxProfit(pnl));
		assertEquals(RiskState.RESULT_OK, state.checkMaxProfit(pnl - 1));
		assertEquals(RiskState.RESULT_EXCEEDED, state.checkMaxProfit(pnl + 1));

		pnl = maxLoss.get().doubleValue();
		assertEquals(RiskState.RESULT_OK, state.checkMaxLoss(pnl));
		assertEquals(RiskState.RESULT_OK, state.checkMaxLoss(pnl + 1));
		assertEquals(RiskState.RESULT_EXCEEDED, state.checkMaxLoss(pnl - 1));
	}

	@Test
	public void testDifferentChecksWithNoControl(){
		EntityType entityType = EntityType.FIRM;
		Optional<Long> maxOpenPosition = Optional.empty();
		Optional<Double> maxProfit = Optional.empty();
		Optional<Double> maxLoss = Optional.empty();
		Optional<Double> maxCapLimit = Optional.empty();
		RiskControlSetting setting = RiskControlSetting.of(entityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		long entitySid = 121211;
		RiskState state = RiskState.of(entitySid, entityType, setting);
	
		long openPosition = 100;;
		assertEquals(RiskState.RESULT_OK, state.checkOpenPosition(openPosition));
		assertEquals(RiskState.RESULT_OK, state.checkOpenPosition(openPosition - 1));
		assertEquals(RiskState.RESULT_OK, state.checkOpenPosition(openPosition + 1));
	
		double pnl = 100.00;
		assertEquals(RiskState.RESULT_OK, state.checkMaxProfit(pnl));
		assertEquals(RiskState.RESULT_OK, state.checkMaxProfit(pnl - 1));
		assertEquals(RiskState.RESULT_OK, state.checkMaxProfit(pnl + 1));

		pnl = -100.00;
		assertEquals(RiskState.RESULT_OK, state.checkMaxLoss(pnl));
		assertEquals(RiskState.RESULT_OK, state.checkMaxLoss(pnl + 1));
		assertEquals(RiskState.RESULT_OK, state.checkMaxLoss(pnl - 1));
		
		double maxCapUsed = 100;
		assertEquals(RiskState.RESULT_OK, state.checkMaxCapUsed(maxCapUsed));
		assertEquals(RiskState.RESULT_OK, state.checkMaxCapUsed(maxCapUsed + 1));
		assertEquals(RiskState.RESULT_OK, state.checkMaxCapUsed(maxCapUsed - 1));
	}

	@Test
	public void testHandleSecurityPositionDetailsChangeOnOpenPositionChange(){
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
		double capUsed = 0;
		double maxCapUsed = 0;
		double fees = 0;
		double commission = 0;
		double totalPnl = 100;
		double avgBuyPrice = 10;
		double avgSellPrice = 10;
		double mtmBidPrice = 10;
		double mtmAskPrice = 10;
		int tradeCount = 1;
		SecurityPositionDetails current = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall, openPosition, openCallPosition, openPutPosition, buyQty, buyNotional, sellQty, sellNotional, 
				osBuyQty, osBuyNotional, osSellQty, osSellNotional, capUsed, maxCapUsed, fees, commission, 
				totalPnl, avgBuyPrice, avgSellPrice, mtmBidPrice, mtmAskPrice, tradeCount);
		SecurityPositionDetails previous = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall);
		
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(refEntityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		RiskState state = RiskState.of(refEntitySid, refEntityType, setting);
		
		// Test openPosition
		AtomicInteger expectedNumChanges = new AtomicInteger(2);
		AtomicInteger expectedZeroChange = new AtomicInteger(0);
		state.addHandler(new RiskControlStateChangeHandler() {
			
			@Override
			public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedNumChanges.decrementAndGet();
				LOG.debug("Open position state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedZeroChange.decrementAndGet();
			}
			
			@Override
			public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedZeroChange.decrementAndGet();
			}

			@Override
			public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedZeroChange.decrementAndGet();
			}
		});
		state.handleChange(current, previous);

		// Exceed max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.openPosition(current.openPosition() + 1);
		state.handleChange(current, previous);
		
		// Still exceeds max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.openPosition(current.openPosition() + 1);
		state.handleChange(current, previous);

		// Get back to max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.openPosition(current.openPosition() - 2);
		state.handleChange(current, previous);

		// Below max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.openPosition(current.openPosition() - 2);
		state.handleChange(current, previous);
		
		assertEquals(0, expectedNumChanges.get());
		assertEquals(0, expectedZeroChange.get());
	}
	
	@Test
	public void testHandleSecurityPositionDetailsChangeOnMaxProfitChange(){
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
		double capUsed = 0;
		double maxCapUsed = 0;
		SecurityPositionDetails current = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall, openPosition, openCallPosition, openPutPosition, buyQty, buyNotional, sellQty, sellNotional, 
				osBuyQty, osBuyNotional, osSellQty, osSellNotional, capUsed, maxCapUsed, fees, commission, 
				totalPnl, avgBuyPrice, avgSellPrice, mtmBidPrice, mtmAskPrice, tradeCount);
		
		SecurityPositionDetails previous = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall);
		
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(refEntityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		RiskState state = RiskState.of(refEntitySid, refEntityType, setting);
		
		AtomicInteger expectedNumChanges = new AtomicInteger(2);
		AtomicInteger expectedOtherChange = new AtomicInteger(0);
		state.addHandler(new RiskControlStateChangeHandler() {
			
			@Override
			public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Open position state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedNumChanges.decrementAndGet();
				LOG.debug("Max profit state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Max loss state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
			}

		});
		current.netRealizedPnl(1000).unrealizedPnl(1000);
		state.handleChange(current, previous);

		// Exceed max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(1001);
		state.handleChange(current, previous);
		
		// Still exceeds max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1001).unrealizedPnl(1001);
		state.handleChange(current, previous);

		// Get back to max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(1000);
		state.handleChange(current, previous);

		// Below max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(999);
		state.handleChange(current, previous);
		
		assertEquals(0, expectedNumChanges.get());
		assertEquals(0, expectedOtherChange.get());
	}
	
	@Test
	public void testHandleSecurityPositionDetailsChangeOnMaxLossChange(){
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
		double capUsed = 0;
		double maxCapUsed = 0;
		SecurityPositionDetails current = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall, openPosition, openCallPosition, openPutPosition, buyQty, buyNotional, sellQty, sellNotional, 
				osBuyQty, osBuyNotional, osSellQty, osSellNotional, capUsed, maxCapUsed, fees, commission, 
				totalPnl, avgBuyPrice, avgSellPrice, mtmBidPrice, mtmAskPrice, tradeCount);
		
		SecurityPositionDetails previous = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall);
		
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(refEntityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		RiskState state = RiskState.of(refEntitySid, refEntityType, setting);
		
		AtomicInteger expectedNumChanges = new AtomicInteger(2);
		AtomicInteger expectedOtherChange = new AtomicInteger(0);
		state.addHandler(new RiskControlStateChangeHandler() {
			
			@Override
			public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Open position state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Max profit state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedNumChanges.decrementAndGet();
				LOG.debug("Max loss state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
			}
		});
		current.netRealizedPnl(1000).unrealizedPnl(-2000);
		state.handleChange(current, previous);

		// Exceed max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(-2001);
		state.handleChange(current, previous);
		
		// Still exceeds max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(999).unrealizedPnl(-2001);
		state.handleChange(current, previous);

		// Get back to max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(-2000);
		state.handleChange(current, previous);

		// Not exceed max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(-1999);
		state.handleChange(current, previous);
		
		assertEquals(0, expectedNumChanges.get());
		assertEquals(0, expectedOtherChange.get());
	}
	
	@Test
	public void testHandleSecurityPositionDetailsChangeOnMaxCapUsedChange(){
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
		double capUsed = 0;
		double maxCapUsed = 0;
		
		SecurityPositionDetails current = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall, openPosition, openCallPosition, openPutPosition, buyQty, buyNotional, sellQty, sellNotional, 
				osBuyQty, osBuyNotional, osSellQty, osSellNotional, capUsed, maxCapUsed, fees, commission, 
				totalPnl, avgBuyPrice, avgSellPrice, mtmBidPrice, mtmAskPrice, tradeCount);
		
		SecurityPositionDetails previous = SecurityPositionDetails.of(FeeAndCommissionSchedule.ZERO_FEES_AND_COMMISSION, 
				refEntitySid, refEntityType, refPutOrCall);
		
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(refEntityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		RiskState state = RiskState.of(refEntitySid, refEntityType, setting);
		
		AtomicInteger expectedNumChanges = new AtomicInteger(1);
		AtomicInteger expectedOtherChange = new AtomicInteger(0);
		state.addHandler(new RiskControlStateChangeHandler() {
			
			@Override
			public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Open position state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Max profit state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Max loss state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedNumChanges.decrementAndGet();
				LOG.debug("Max cap limit state change [current:{}, previous:{}, exceeded:{}", current, previous, exceeded);
			}
		});
		current.capUsed(1000);
		
		state.handleChange(current, previous);

		// Exceed max, expect state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.capUsed(1001);
		state.handleChange(current, previous);
		
		// Still exceeds max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.capUsed(1002);
		state.handleChange(current, previous);

		// Although capUsed is reduced, maxCapUsed stays the same, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.capUsed(1000);
		state.handleChange(current, previous);

		// Not exceed max, expect no state change
		previous = current;
		current = SecurityPositionDetails.cloneOf(current);
		current.capUsed(999);
		state.handleChange(current, previous);
		
		assertEquals(0, expectedNumChanges.get());
		assertEquals(0, expectedOtherChange.get());
	}
	
	@Test
	public void testHandlePositionDetailsChangeOnMaxProfitChange(){
		long refEntitySid = 1001;
		EntityType refEntityType = EntityType.ISSUER;
		PositionDetails current = PositionDetails.of(refEntitySid, refEntityType);
		PositionDetails previous = PositionDetails.of(refEntitySid, refEntityType);
		
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(refEntityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		RiskState state = RiskState.of(refEntitySid, refEntityType, setting);
		
		AtomicInteger expectedNumChanges = new AtomicInteger(2);
		AtomicInteger expectedOtherChange = new AtomicInteger(0);
		state.addHandler(new RiskControlStateChangeHandler() {
			
			@Override
			public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Open position state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedNumChanges.decrementAndGet();
				LOG.debug("Max profit state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Max loss state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
			}
		});
		current.netRealizedPnl(1000).unrealizedPnl(1000);
		state.handleChange(current, previous);

		// Exceed max, expect state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(1001);
		state.handleChange(current, previous);
		
		// Still exceeds max, expect no state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1001).unrealizedPnl(1001);
		state.handleChange(current, previous);

		// Get back to max, expect state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(1000);
		state.handleChange(current, previous);

		// Below max, expect no state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(999);
		state.handleChange(current, previous);
		
		assertEquals(0, expectedNumChanges.get());
		assertEquals(0, expectedOtherChange.get());
	}
	
	@Test
	public void testHandlePositionDetailsChangeOnMaxLossChange(){
		long refEntitySid = 1001;
		EntityType refEntityType = EntityType.ISSUER;
		PositionDetails current = PositionDetails.of(refEntitySid, refEntityType);
		PositionDetails previous = PositionDetails.of(refEntitySid, refEntityType);
		
		Optional<Long> maxOpenPosition = Optional.of(100l);
		Optional<Double> maxProfit = Optional.of(2000.00);
		Optional<Double> maxLoss = Optional.of(-1000.00);
		Optional<Double> maxCapLimit = Optional.of(1000.00);
		RiskControlSetting setting = RiskControlSetting.of(refEntityType, 
				maxOpenPosition, 
				maxProfit, 
				maxLoss,
				maxCapLimit);
		RiskState state = RiskState.of(refEntitySid, refEntityType, setting);
		
		AtomicInteger expectedNumChanges = new AtomicInteger(2);
		AtomicInteger expectedOtherChange = new AtomicInteger(0);
		state.addHandler(new RiskControlStateChangeHandler() {
			
			@Override
			public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Open position state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
				LOG.debug("Max profit state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedNumChanges.decrementAndGet();
				LOG.debug("Max loss state change [current:{}, previous:{}, exceeded:{}]", current, previous, exceeded);
			}
			
			@Override
			public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded) {
				assertEquals(refEntitySid, entitySid);
				assertEquals(refEntityType, entityType);
				expectedOtherChange.decrementAndGet();
			}
		});
		current.netRealizedPnl(1000).unrealizedPnl(-2000);
		state.handleChange(current, previous);

		// Exceed max, expect state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(-2001);
		state.handleChange(current, previous);
		
		// Still exceeds max, expect no state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(999).unrealizedPnl(-2001);
		state.handleChange(current, previous);

		// Get back to max, expect state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(-2000);
		state.handleChange(current, previous);

		// Not exceed max, expect no state change
		previous = current;
		current = PositionDetails.cloneOf(current);
		current.netRealizedPnl(1000).unrealizedPnl(-1999);
		state.handleChange(current, previous);
		
		assertEquals(0, expectedNumChanges.get());
		assertEquals(0, expectedOtherChange.get());
	}
}
