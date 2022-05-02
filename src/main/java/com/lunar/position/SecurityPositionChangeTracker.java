package com.lunar.position;

import com.google.common.math.DoubleMath;
import com.lunar.message.io.sbe.PutOrCall;

public class SecurityPositionChangeTracker {
	private SecurityPositionDetails current;
	private final SecurityPositionDetails previous;
	private boolean hasNetRealizedPnlImpact;
	private boolean hasUnrealizedPnlImpact;
	private boolean hasReportableChange;
		
	public static SecurityPositionChangeTracker of(){
		return new SecurityPositionChangeTracker(null);
	}
	
	public static PositionChangeTracker of(SecurityPositionDetails current){
		return new PositionChangeTracker(current);
	}

	SecurityPositionChangeTracker(SecurityPositionDetails current){
		this.current = current;
		this.previous = SecurityPositionDetails.of();
	}
	
	public void init(SecurityPositionDetails current){
		this.current = current;
		this.previous.avgBuyPrice = this.current.avgBuyPrice;
		this.previous.avgSellPrice = this.current.avgSellPrice;
		this.previous.buyNotional = this.current.buyNotional;
		this.previous.buyQty = this.current.buyQty;
		this.previous.commission = this.current.commission;
		this.previous.fees = this.current.fees;
		this.previous.hasMtmBuyPrice = this.current.hasMtmBuyPrice;
		this.previous.hasMtmSellPrice = this.current.hasMtmSellPrice;
		this.previous.mtmBuyPrice = this.current.mtmBuyPrice;
		this.previous.mtmSellPrice = this.current.mtmSellPrice;
		this.previous.netRealizedPnl = this.current.netRealizedPnl;
		this.previous.openPosition = this.current.openPosition;
		this.previous.openCallPosition = this.current.openCallPosition;
		this.previous.openPutPosition = this.current.openPutPosition;
		this.previous.osBuyNotional = this.current.osBuyNotional;
		this.previous.osBuyQty = this.current.osBuyQty;
		this.previous.osSellNotional = this.current.osSellNotional;
		this.previous.osSellQty = this.current.osSellQty;
		this.previous.sellNotional = this.current.sellNotional;
		this.previous.sellQty = this.current.sellQty;
		this.previous.tradeCount = this.current.tradeCount;
		this.previous.unrealizedPnl = this.current.unrealizedPnl;
		this.previous.experimentalNetRealizedPnl = this.current.experimentalNetRealizedPnl;
		this.previous.experimentalUnrealizedPnl = this.current.experimentalUnrealizedPnl;
		this.previous.feesAndCommssionSchedule(this.current.feesAndCommssionSchedule());
		this.previous.capUsed = this.current.capUsed;
		this.previous.maxCapUsed = this.current.maxCapUsed;
		this.hasReportableChange = false;
		this.hasNetRealizedPnlImpact = false;
		this.hasUnrealizedPnlImpact = false;
	}
	
	boolean hasReportableChange(){
		return hasReportableChange;
	}

	public boolean hasNetRealizedPnlImpact(){
		return hasNetRealizedPnlImpact;
	}
	
	public boolean hasUnrealizedPnlImpact(){
		return hasUnrealizedPnlImpact;
	}

	SecurityPositionChangeTracker putOrCall(PutOrCall putOrCall) {
		this.current.putOrCall(putOrCall);
		return this;
	}
	
	SecurityPositionChangeTracker feesAndCommssionSchedule(FeeAndCommissionSchedule schedule){
		if (!this.current.feesAndCommssionSchedule().equals(schedule)){
			this.current.feesAndCommssionSchedule(schedule);
			this.hasNetRealizedPnlImpact = true;
			this.hasReportableChange = true;
		}
		return this;
	}
	
	SecurityPositionChangeTracker avgBuyPrice(double value){
		this.current.avgBuyPrice(value);
		return this;
	}
	
	SecurityPositionChangeTracker avgSellPrice(double value){
		this.current.avgSellPrice(value);
		return this;
	}

	SecurityPositionChangeTracker buyQty(long value){
		this.current.openPosition(this.current.openPosition - this.current.buyQty + value);
		if (this.current.putOrCall() == PutOrCall.CALL) {
			this.current.openCallPosition(this.current.openCallPosition - this.current.buyQty + value);
		}
		else if (this.current.putOrCall() == PutOrCall.PUT) {
			this.current.openPutPosition(this.current.openPutPosition - this.current.buyQty + value);
		} 		
		
		this.current.buyQty(value);
		return this;
	}
	
	SecurityPositionChangeTracker fees(double value){
		this.current.fees(value);
		return this;
	}	

	SecurityPositionChangeTracker commission(double value){
		this.current.commission(value);
		return this;
	}	

	SecurityPositionChangeTracker sellQty(long value){
		this.current.openPosition(this.current.openPosition + this.current.sellQty - value);
		if (this.current.putOrCall() == PutOrCall.CALL) {
			this.current.openCallPosition(this.current.openCallPosition + this.current.sellQty - value);
		}
		else if (this.current.putOrCall() == PutOrCall.PUT) {
			this.current.openPutPosition(this.current.openPutPosition + this.current.sellQty - value);
		}		
		this.current.sellQty(value);
		return this;
	}

	SecurityPositionChangeTracker buyNotional(double value){
		this.current.buyNotional(value);
		return this;
	}

	SecurityPositionChangeTracker sellNotional(double value){
		this.current.sellNotional(value);
		return this;
	}

	SecurityPositionChangeTracker tradeCount(int value){
		this.current.tradeCount(value);
		return this;
	}

	SecurityPositionChangeTracker osBuyQty(long value){
		this.current.osBuyQty(value);
		return this;
	}

	SecurityPositionChangeTracker osSellQty(long value){
		this.current.osSellQty(value);
		return this;
	}

	SecurityPositionChangeTracker osBuyNotional(double value){
		this.current.osBuyNotional(value);
		return this;
	}

	SecurityPositionChangeTracker osSellNotional(double value){
		this.current.osSellNotional(value);
		return this;
	}

	SecurityPositionChangeTracker capUsed(double value){
		this.current.capUsed(value);
		return this;
	}
	
	public SecurityPositionChangeTracker addBuyTrade(int tradeSid, double price, long quantity){
		double notional = price * quantity;
		double fees = this.current.feesAndCommssionSchedule().feesOf(notional);
		double commission = this.current.feesAndCommssionSchedule().commissionOf(notional);
				
		this.current.addBuyTrade(tradeSid, price, quantity, fees, commission);
				
		buyQty(this.current.buyQty() + quantity);
		buyNotional(this.current.buyNotional() + notional);
		avgBuyPrice(this.current.buyNotional() / this.current.buyQty());
		tradeCount(this.current.tradeCount() + 1);
		fees(this.current.fees() + fees);
		commission(this.current.commission() + commission);
		capUsed(this.current.capUsed() + notional);
		hasNetRealizedPnlImpact = true;
		hasUnrealizedPnlImpact = true;
		hasReportableChange = true;
		return this;
	}

//	private static double calculateCapUsed(double osBuyNotional, double buyNotional, double sellNotional){
//		return osBuyNotional + buyNotional - sellNotional;
//	}
//	
	public SecurityPositionChangeTracker removeBuyTrade(int tradeSid, double price, long quantity){
		this.current.removeBuyTrade(tradeSid);
		
		double notional = price * quantity;
		buyQty(this.current.buyQty() - quantity);
		buyNotional(this.current.buyNotional() - notional);
		capUsed(this.current.capUsed() - notional);
		avgBuyPrice(this.current.buyNotional() / this.current.buyQty());
		tradeCount(this.current.tradeCount() - 1);
		fees(this.current.fees() - this.current.feesAndCommssionSchedule().feesOf(notional));
		commission(this.current.commission() - this.current.feesAndCommssionSchedule().commissionOf(notional));
		hasNetRealizedPnlImpact = true;
		hasUnrealizedPnlImpact = true;
		hasReportableChange = true;
		return this;
	}

	public SecurityPositionChangeTracker addSellTrade(int tradeSid, double price, long quantity){
		double notional = price * quantity;
		double fees = this.current.feesAndCommssionSchedule().feesOf(notional);
		double commission = this.current.feesAndCommssionSchedule().commissionOf(notional);		
		
		this.current.addSellTrade(tradeSid, price, quantity, fees, commission);
				
		sellQty(this.current.sellQty() + quantity);
		sellNotional(this.current.sellNotional() + notional);
		capUsed(this.current.capUsed() - notional);
		avgSellPrice(this.current.sellNotional() / this.current.sellQty());
		tradeCount(this.current.tradeCount() + 1);
		fees(this.current.fees() + fees);
		commission(this.current.commission() + commission);
		hasNetRealizedPnlImpact = true;
		hasUnrealizedPnlImpact = true;
		hasReportableChange = true;
		return this;
	}

	public SecurityPositionChangeTracker removeSellTrade(int tradeSid, double price, long quantity){
		this.current.removeSellTrade(tradeSid);
		
		double notional = price * quantity;
		
		sellQty(this.current.sellQty() - quantity);
		sellNotional(this.current.sellNotional() - notional);
		capUsed(this.current.capUsed() + notional);
		avgSellPrice(this.current.sellNotional() / this.current.sellQty());
		tradeCount(this.current.tradeCount() - 1);
		fees(this.current.fees() - this.current.feesAndCommssionSchedule().feesOf(notional));
		commission(this.current.commission() - this.current.feesAndCommssionSchedule().commissionOf(notional));
		hasNetRealizedPnlImpact = true;
		hasUnrealizedPnlImpact = true;
		hasReportableChange = true;
		return this;
	}
	
	public SecurityPositionChangeTracker addBuyOrder(double price, long quantity){
		double notional = (price * quantity);
		osBuyQty(this.current.osBuyQty() + quantity);
		osBuyNotional(this.current.osBuyNotional() + notional);
		capUsed(this.current.capUsed() + notional);
		hasReportableChange = true;
		return this;
	}
	
	public SecurityPositionChangeTracker removeBuyOrder(double price, long quantity){
		return addBuyOrder(price, -quantity);
	}

	public SecurityPositionChangeTracker addSellOrder(double price, long quantity){
		osSellQty(this.current.osSellQty() + quantity);
		osSellNotional(this.current.osSellNotional() + (price * quantity));
		
		/**
		 * capUsed - wont' be impacted by sellOrder, we only want to count capUsed induced by sell trades
		 */
		
		hasReportableChange = true;
		return this;
	}
	
	public SecurityPositionChangeTracker removeSellOrder(double price, long quantity){
		return addSellOrder(price, -quantity);
	}

	public SecurityPositionChangeTracker bidAsk(double bid, double ask){
		// Update bidAsk may affect unrealized pnl
		this.current.bidAsk(bid, ask);

		this.current.mtmBuyPrice(bid);
		this.current.mtmSellPrice(ask);
		
		if (!DoubleMath.fuzzyEquals(this.previous.experimentalUnrealizedPnl(), this.current.pnlCalculator.unrealizedPnl(), PositionChangeTracker.PNL_EQUALITY_TOLERANCE)){
			hasUnrealizedPnlImpact = true;
			hasReportableChange = true;
		}

		return this;
	}
	
	public SecurityPositionDetails previous(){
		return this.previous;
	}

	public SecurityPositionDetails current(){
		return this.current;
	}
}
