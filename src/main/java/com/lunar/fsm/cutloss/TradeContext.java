package com.lunar.fsm.cutloss;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TradeContext {
	private static final Logger LOG = LogManager.getLogger(TradeContext.class);
	private final String tag;
	private final MarketContext marketContext;
	private boolean tradeInfoReady;
	private int refBidPrice;
	private int initRefBidTickLevel;
	private int initRefNumMmBidLevels;
	private int osQty;

	// These field record the time of which we entered into position
	private int initRefTradePrice;
	private int totalBoughtQuantity;
	private int initRefTradeTickLevel;
	private int initRefUndBidPrice;
	private int initRefUndAskPrice;
	private int initRefUndSpreadInTick;

	private final int maxAllowableLossInTicks;
	
	private int minDetectedUnexplainableBidLevel;
	
	private final int maxUnexplainableMoves;
	private int detectedUnexplainableMove;
	
	// Per trade set value
	private long buyNotional;
	private long sellNotional;
	private int suggestedSellAtBidLevel;
	
	private long actualPnl;
	private long theoPnl;
	
	/**
	 * Transient copy of {@link CutLossSignalDetector#expectedInitBidDownBuffer}
	 */
	private final int bidTickLevelBuffer;
	
	private SignalHandler signalHandler = SignalHandler.NULL_HANDLER;
	
	public static TradeContext of(String tag, MarketContext marketContext, int bidTickLevelBuffer, int maxAllowableLossInTicks){
		return new TradeContext(tag, marketContext, bidTickLevelBuffer, maxAllowableLossInTicks, 2);
	}

	public static TradeContext of(String tag, MarketContext marketContext, int bidTickLevelBuffer, int maxAllowableLossInTicks, int numUnexplainableMoveThreshold){
		return new TradeContext(tag, marketContext, bidTickLevelBuffer, maxAllowableLossInTicks, numUnexplainableMoveThreshold);
	}
	
	TradeContext(String tag, MarketContext marketContext, int bidTickLevelBuffer, int maxAllowableLossInTicks, int maxUnexplainableMoves){
		this.tag = tag;
		this.marketContext = marketContext;
		this.bidTickLevelBuffer = bidTickLevelBuffer;
		this.maxAllowableLossInTicks = maxAllowableLossInTicks;
		this.maxUnexplainableMoves = maxUnexplainableMoves;
		reset();
	}
	
	private State state;
	public State state(){
		return this.state;
	}
	public State state(State state){
		return (this.state = state);
	}
	public String tag(){
		return tag;
	}
	@Override
	public String toString() {
		return "initOsQty: " + totalBoughtQuantity +
				", initRefTradePrice: " + initRefTradePrice +
				", initRefTradeTickLevel: " + initRefTradeTickLevel +
				", initRefNumMmBidLevels: " + initRefNumMmBidLevels +
				", initRefUndBidPrice: " + initRefUndBidPrice +
				", initRefUndAskPrice: " + initRefUndAskPrice +
				", initRefUndSpreadInTick: " + initRefUndSpreadInTick +
				", refBidPrice: " + refBidPrice +
				", initRefBidTickLevel: " + initRefBidTickLevel +				
				", osQty: " + osQty + 
				", tradeInfoReady: " + tradeInfoReady;
	}
	public void reset(){
		this.osQty = 0;
		this.totalBoughtQuantity = 0;
		this.initRefBidTickLevel = marketContext.nullTickLevel();
		this.initRefNumMmBidLevels = 0;
		this.initRefTradePrice = marketContext.nullPrice();
		this.initRefTradeTickLevel = marketContext.nullTickLevel();
		this.initRefUndBidPrice = marketContext.nullPrice();
		this.initRefUndAskPrice = marketContext.nullPrice();
		this.initRefUndSpreadInTick = Integer.MAX_VALUE;
		this.refBidPrice = marketContext.nullPrice();
		this.tradeInfoReady = false;
		this.minDetectedUnexplainableBidLevel = marketContext.nullTickLevel();
		this.detectedUnexplainableMove = 0;
		this.suggestedSellAtBidLevel = marketContext.nullPrice();
		this.sellNotional = 0;
		this.buyNotional = 0;
		this.totalBoughtQuantity = 0;
	}
	
	public long buyNotional(){
		return buyNotional;
	}

	public long sellNotional(){
		return sellNotional;
	}

	/**
	 * Calculate actual pnl and theo pnl of current trade set
	 */
	public void calculatePnl(){
		// Precondition
		if (this.osQty != 0){
			return;
		}
		
		long pnl = (sellNotional - buyNotional);
		this.actualPnl += pnl;
		if (suggestedSellAtBidLevel != marketContext.nullPrice()){
			this.theoPnl += (totalBoughtQuantity * marketContext.secSpreadTable().tickToPrice(suggestedSellAtBidLevel) - buyNotional);			
		}
		else{
			this.theoPnl += pnl;
		}
	}
	
	public long actualPnl(){
		return actualPnl;
	}
	
	public long theoPnl(){
		return theoPnl;
	}

	public long addTheoPnl(long value){
		theoPnl += value;
		return theoPnl;
	}
	
	public void suggestedSellAtBidLevel(int bidLevel){
		this.suggestedSellAtBidLevel = bidLevel;
	}
	public int suggestedSellAtBidLevel(){
		return suggestedSellAtBidLevel;
	}	
	public int maxAllowableLossInTicks(){
		return maxAllowableLossInTicks;
	}
	int bidTickLevelBuffer(){
		return bidTickLevelBuffer;
	}
	int refBidPrice(){
		return refBidPrice;
	}
	public int initRefBidTickLevel(){
		return initRefBidTickLevel;
	}
	int initRefNumMmBidLevels(){
		return initRefNumMmBidLevels;
	}
	public boolean hasBuyTrade(){
		return tradeInfoReady;
	}
	public int osQty(){
		return osQty;
	}
	TradeContext osQty(int value){
		this.osQty = value;
		return this;
	}
	public int initRefTradePrice(){
		return initRefTradePrice;
	}
	public int totalBoughtQuantity(){
		return totalBoughtQuantity;
	}
	public int initRefTradeTickLevel(){
		return initRefTradeTickLevel;
	}
	public int initRefUndBidPrice(){
		return initRefUndBidPrice;
	}
	public int initRefUndAskPrice(){
		return initRefUndAskPrice;
	}
	public int initRefUndSpreadInTick(){
		return initRefUndSpreadInTick;
	}
	public int minDetectedUnexplainableBidLevel(){
		return minDetectedUnexplainableBidLevel;
	}
	public TradeContext minDetectedUnexplainableBidLevel(int value){
		this.minDetectedUnexplainableBidLevel = value;
		return this;
	}
	public int detectedUnexplainableMove(){
		return detectedUnexplainableMove;
	}
	
	public void resetDetectedUnexplainableMove(){
		detectedUnexplainableMove = 0;
	}
	
	public int addDetectedUnexplainableMove(){
		return ++this.detectedUnexplainableMove;
	}
	public int maxUnexplainableMoves(){
		return maxUnexplainableMoves;
	}
	
	public TradeContext initBuyTrade(int price, 
			int quantity){
		this.initRefTradePrice = price;
		this.totalBoughtQuantity = quantity;
		this.initRefTradeTickLevel = marketContext.secSpreadTable().priceToTick(price);
		
		// Populate init fields with other current fields
		this.initRefUndBidPrice = marketContext.currentUndBidPrice();
		this.initRefUndAskPrice = marketContext.currentUndAskPrice();		
		this.initRefUndSpreadInTick = Math.max(1, marketContext.currentUndSpreadInTick() - 1);
		this.initRefNumMmBidLevels = marketContext.currentMmBidLevels();		
		this.initRefBidTickLevel = marketContext.currentBidTickLevel();
				
		this.minDetectedUnexplainableBidLevel = marketContext.nullTickLevel();
		this.detectedUnexplainableMove = 0;

		this.tradeInfoReady = true;
		return this;
	}
	
	public TradeContext buyTrade(int price, int quantity){
		this.osQty += quantity;
		totalBoughtQuantity += quantity;
		this.buyNotional += (long)price * quantity;
		return this;
	}

	public TradeContext sellTrade(int price, int quantity){
		this.osQty -= quantity;
		this.sellNotional += (long)price * quantity;
		return this;
	}

	public MarketContext market(){
		return marketContext;
	}

	public SignalHandler signalHandler(){
		return signalHandler;
	}
	
	TradeContext signalHandler(SignalHandler handler){
		this.signalHandler = handler;
		return this;
	}
}
