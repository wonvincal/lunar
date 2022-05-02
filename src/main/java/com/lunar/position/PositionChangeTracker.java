package com.lunar.position;

import com.google.common.math.DoubleMath;
import com.lunar.message.io.sbe.EntityType;

public class PositionChangeTracker {
	public static double PRICE_EQUALITY_TOLERANCE = 0.0001;
	public static double PNL_EQUALITY_TOLERANCE = 10;
	public static double FEES_AND_COMMISSION_EQUALITY_TOLERANCE = 1;
	public static int TRADE_COUNT_TOLERANCE = 1;
	public static int QUANTITY_TOLERANCE = 1;
	private PositionDetails current;
	private final PositionDetails previous;
	private double previouslyReportedFees = 0.0;
	private double previouslyReportedCommission = 0.0;
	private double previouslyReportedUnrealizedPnl = 0.0;
	private double previouslyReportedNetRealizedPnl = 0.0;
	private double previouslyReportedExperimentalUnrealizedPnl = 0.0;
	private double previouslyReportedExperimentalNetRealizedPnl = 0.0;
	private int previouslyReportedTradeCount = 0;
	private long previouslyReportedBuyQty = 0;
	private long previouslyReportedSellQty = 0;
	private long previouslyReportedOpenPosition = 0;
	private long previouslyReportedOpenCallPosition = 0;
	private long previouslyReportedOpenPutPosition = 0;
	private double previouslyReportedBuyNotional = 0.0;
	private double previouslyReportedSellNotional = 0.0;
	private double previouslyReportedCapUsed = 0.0;
	private boolean hasChange;
		
	public static PositionChangeTracker of(long entitySid, EntityType entityType){
		return new PositionChangeTracker(PositionDetails.of(entitySid, entityType));
	}

	PositionChangeTracker(PositionDetails current){
		this.current = current;
		this.previous = PositionDetails.of();
	}
	
	long entitySid(){
		return current.entitySid();
	}
	
	EntityType entityType(){
		return current.entityType();
	}
	
	public void clearChanges(){
		this.hasChange = false;
	}

	PositionChangeTracker addBuyQty(long value){
		this.previous.buyQty(this.current.buyQty());
		this.current.buyQty(this.current.buyQty() + value);
		if (Math.abs(previouslyReportedBuyQty - this.current.buyQty()) >= QUANTITY_TOLERANCE){
			this.hasChange = true;
			this.previouslyReportedBuyQty = this.current.buyQty();
		}
		return this;
	}
	
	PositionChangeTracker addSellQty(long value){
		this.previous.sellQty(this.current.sellQty());
		this.current.sellQty(this.current.sellQty() + value);
		if (Math.abs(previouslyReportedSellQty - this.current.sellQty()) >= QUANTITY_TOLERANCE){
			this.hasChange = true;
			this.previouslyReportedSellQty = this.current.sellQty();
		}
		return this;
	}

	PositionChangeTracker addOpenPosition(long value){
		this.previous.openPosition(this.current.openPosition());
		this.current.openPosition(this.current.openPosition() + value);
		if (Math.abs(previouslyReportedOpenPosition - this.current.openPosition()) >= QUANTITY_TOLERANCE){
			this.hasChange = true;
			this.previouslyReportedOpenPosition = this.current.openPosition();
		}
		return this;
	}
	
	PositionChangeTracker addOpenCallPosition(long value){
		this.previous.openCallPosition(this.current.openCallPosition());
		this.current.openCallPosition(this.current.openCallPosition() + value);
		if (Math.abs(previouslyReportedOpenCallPosition - this.current.openCallPosition()) >= QUANTITY_TOLERANCE){
			this.hasChange = true;
			this.previouslyReportedOpenCallPosition = this.current.openCallPosition();
		}
		return this;
	}
	
	PositionChangeTracker addOpenPutPosition(long value){
		this.previous.openPutPosition(this.current.openPutPosition());
		this.current.openPutPosition(this.current.openPutPosition() + value);
		if (Math.abs(previouslyReportedOpenPutPosition - this.current.openPutPosition()) >= QUANTITY_TOLERANCE){
			this.hasChange = true;
			this.previouslyReportedOpenPutPosition = this.current.openPutPosition();
		}
		return this;
	}	
	
	PositionChangeTracker addFees(double value){
		this.previous.fees(this.current.fees());
		this.current.fees(this.current.fees() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedFees, this.current.fees(), FEES_AND_COMMISSION_EQUALITY_TOLERANCE)){
			previouslyReportedFees = this.current.fees();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addCommission(double value){
		this.previous.commission(this.current.commission());
		this.current.commission(this.current.commission() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedCommission, this.current.commission(), FEES_AND_COMMISSION_EQUALITY_TOLERANCE)){
			previouslyReportedCommission = this.current.commission();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addBuyNotional(double value){
		this.previous.buyNotional(this.current.buyNotional());
		this.current.buyNotional(this.current.buyNotional() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedBuyNotional, this.current.buyNotional(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedBuyNotional = this.current.buyNotional();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addSellNotional(double value){
		this.previous.sellNotional(this.current.sellNotional());
		this.current.sellNotional(this.current.sellNotional() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedSellNotional, this.current.sellNotional(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedSellNotional = this.current.sellNotional();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addCapUsed(double value){
		this.previous.capUsed(this.current.capUsed());
		this.current.capUsed(this.current.capUsed() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedCapUsed, this.current.capUsed(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedCapUsed = this.current.capUsed();
			this.hasChange = true;
		}
		return this;
	}
	
	PositionChangeTracker addUnrealizedPnl(double value){
		this.previous.unrealizedPnl(this.current.unrealizedPnl());
		this.current.unrealizedPnl(this.current.unrealizedPnl() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedUnrealizedPnl, this.current.unrealizedPnl(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedUnrealizedPnl = this.current.unrealizedPnl();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addExperimentalUnrealizedPnl(double value){
		this.previous.experimentalUnrealizedPnl(this.current.experimentalUnrealizedPnl());
		this.current.experimentalUnrealizedPnl(this.current.experimentalUnrealizedPnl() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedExperimentalUnrealizedPnl, this.current.experimentalUnrealizedPnl(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedExperimentalUnrealizedPnl = this.current.experimentalUnrealizedPnl();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addNetRealizedPnl(double value){
		this.previous.netRealizedPnl(this.current.netRealizedPnl());
		this.current.netRealizedPnl(this.current.netRealizedPnl() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedNetRealizedPnl, this.current.netRealizedPnl(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedNetRealizedPnl = this.current.netRealizedPnl();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addExperimentalNetRealizedPnl(double value){
		this.previous.experimentalNetRealizedPnl(this.current.experimentalNetRealizedPnl());
		this.current.experimentalNetRealizedPnl(this.current.experimentalNetRealizedPnl() + value);
		if (!DoubleMath.fuzzyEquals(previouslyReportedExperimentalNetRealizedPnl, this.current.experimentalNetRealizedPnl(), PNL_EQUALITY_TOLERANCE)){
			previouslyReportedExperimentalNetRealizedPnl = this.current.experimentalNetRealizedPnl();
			this.hasChange = true;
		}
		return this;
	}

	PositionChangeTracker addTradeCount(int value){
		this.previous.tradeCount(this.current.tradeCount());
		this.current.tradeCount(this.current.tradeCount() + value);
		if (Math.abs(previouslyReportedTradeCount - this.current.tradeCount()) >= TRADE_COUNT_TOLERANCE){
			this.hasChange = true;
			this.previouslyReportedTradeCount = this.current.tradeCount();
		}
		return this;
	}	

	boolean hasChange(){
		return hasChange;
	}

	public PositionDetails previous(){
		return this.previous;
	}

	public PositionDetails current(){
		return this.current;
	}
}
