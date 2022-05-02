package com.lunar.fsm.cutloss.mmbidslide;

import com.lunar.fsm.cutloss.MarketContext;
import com.lunar.fsm.cutloss.TradeContext;
import com.lunar.message.io.sbe.PutOrCall;

public class MmBidSlide {
	public static boolean isUnexplainableDownwardMoveDetected(TradeContext context){
		MarketContext market = context.market();
		// If call, no change to underlying or it moves up
		// work on expected warrant change now
		// if und spread is max (one side), this will be doomed
		// if und spread is <= 0,...anyway, we need spread to be valid in these cases
		if (market.currentMMBidTickLevel() == market.nullTickLevel()){
			return false;
		}
		
		// Do nothing is und spread <= 0
		if (market.currentUndSpreadInTick() <= 0){
			return false;
		}
		// We won't be able to detect unexplainable move without tickSense
		int tickSense = market.tickSense();
		if (tickSense <= 0){
			return false;
		}
		int spreadDiff = market.currentUndSpreadInTick() - context.initRefUndSpreadInTick();
		double tickChangeDueToSpreadDiff = (spreadDiff > 0) ? 
				(double)(spreadDiff * market.tickSense()) / 1000.00 : 0.0;
		double currentMid = (double)(market.currentUndBidPrice() + market.currentUndAskPrice()) / 2.0;
		double refMid = (double)(context.initRefUndBidPrice() + context.initRefUndAskPrice()) / 2.0;
		
		// Using current und bid price as a reference to get und tick size
		int lowestTickSize = context.market().undSpreadTable().priceToTickSize(market.currentUndBidPrice());
		double undDiff = (market.putOrCall() == PutOrCall.CALL) ? (refMid - currentMid) : (currentMid - refMid);
		double tickChangeDueToUndDiff = (undDiff > 0) ? undDiff / lowestTickSize * market.tickSense() / 1000.00 : 0.0;
		
		// What about the case where there is change in tickSense in tick size boundary?
		int totalExpectedDownTicks = (int)(tickChangeDueToSpreadDiff + tickChangeDueToUndDiff);		
		if (market.currentMMBidTickLevel() < context.initRefBidTickLevel() - totalExpectedDownTicks){
			return true;
		}
		return false;
	}
}
