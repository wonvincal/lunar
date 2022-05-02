package com.lunar.fsm.cutloss;

import static org.apache.logging.log4j.util.Unbox.box;

import java.lang.reflect.Array;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.strategy.parameters.WrtOutputParams;
import com.lunar.strategy.scoreboard.MarketContextChangeHandler;
import com.lunar.strategy.scoreboard.MarketMakingChangeHandler;
import com.lunar.strategy.scoreboard.ScoreBoardSecurityInfo;
import com.lunar.strategy.scoreboard.UnderlyingOrderBookUpdateHandler;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

/**
 * Holds all relevant information of this trade from received market data
 * @author wongca
 *
 */
public class MarketContext {
	private static final Logger LOG = LogManager.getLogger(MarketContext.class);
	private final static int NULL_SPREAD = -1;
	private final ScoreBoardSecurityInfo security;
	private final ScoreBoardSecurityInfo underlying;
	/**
	 * We should have some logic to determine if issuer has moved the bid price in an
	 * unfavorable way
	 */
	private final PutOrCall putOrCall;
	private final SpreadTable secSpreadTable;
	private final SpreadTable undSpreadTable;

	/*
	 * Allowable initial bid down - when we just bought, it is possible that issuer may down bid by 
	 * certain amount.  This buffer will prevent us from exiting in that scenario.  However this 
	 * buffer should only be used initially
	 * 
	 * TODO Only make use of this buffer in the initial phase of this trade set
	 * 
	 * This should be issuer specific based on backtesting result
	 */
	private int currentBidTickLevel;
	private int currentAskTickLevel;
	private boolean isSecWide;

	private int currentMMBidTickLevel;
	private int currentMMAskTickLevel;
	private boolean isMMSecWide;

	private int currentUndBidPrice;
	private int currentUndBidTickLevel;
	private int currentUndAskPrice;
	private int currentUndAskTickLevel;
	private int currentUndSpreadInTick;
	private final int nullPrice;
	private final int nullTickLevel;
	
	/**
	 * Number of MM bid levels
	 */
	private int currentMmBidLevels;
	private boolean isUndWide;
	private final ReferenceArrayList<MarketContextChangeHandler> marketContextChangeHandlers;
	
	private int currentMmAskLevels;
	private long currentMmBidBestQty;
	private long currentMmBidNonBestQty;
	private long currentMmAskBestQty;
	private long currentMmAskNonBestQty;
	
	// numbers to compare with the price level quantity to determine if the price level is placed by issuer
	private long mmBidSizeThreshold;
	private long mmAskSizeThreshold;

	public static MarketContext of(ScoreBoardSecurityInfo security, ScoreBoardSecurityInfo underlying, PutOrCall putOrCall, SpreadTable secSpreadTable, SpreadTable undSpreadTable, int nullTickLevel, int nullPrice, int initialHandlerCapacity){
		return new MarketContext(security, underlying, putOrCall, secSpreadTable, undSpreadTable, nullTickLevel, nullPrice, initialHandlerCapacity);
	}
	
	MarketContext(ScoreBoardSecurityInfo security, ScoreBoardSecurityInfo underlying, PutOrCall putOrCall, SpreadTable secSpreadTable, SpreadTable undSpreadTable, int nullTickLevel, int nullPrice, int initialHandlerCapacity){
		this.security = security;
		this.underlying = underlying;
		this.putOrCall = putOrCall;
		this.undSpreadTable = undSpreadTable;
		this.secSpreadTable = secSpreadTable;
		this.nullPrice = nullPrice;
		this.nullTickLevel = nullTickLevel;
		
		MarketContextChangeHandler[] item = (MarketContextChangeHandler[])(Array.newInstance(MarketContextChangeHandler.class, initialHandlerCapacity));
		marketContextChangeHandlers = ReferenceArrayList.wrap(item);
		marketContextChangeHandlers.size(0);
		reset();
	}

	private final UnderlyingOrderBookUpdateHandler undOrderBookChangeHandler = new UnderlyingOrderBookUpdateHandler() {
		@Override
		public void onUnderlyingOrderBookUpdated(long nanoOfDay, UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook underlyingOrderBook) {
			if (underlyingOrderBook.spread <= 0){
				return;
			}
			if (currentUndBidPrice != underlyingOrderBook.bestBidPrice || currentUndAskPrice != underlyingOrderBook.bestAskPrice){
				currentUndBidPrice = underlyingOrderBook.bestBidPrice;
				currentUndBidTickLevel = underlyingOrderBook.bestBidLevel;
				currentUndAskPrice = underlyingOrderBook.bestAskPrice;
				currentUndAskTickLevel = underlyingOrderBook.bestAskLevel;
				currentUndSpreadInTick = underlyingOrderBook.spread; 
				isUndWide = (underlyingOrderBook.spread > 1);
				MarketContextChangeHandler[] handlers = marketContextChangeHandlers.elements();
				int size = marketContextChangeHandlers.size();
				for (int i = 0; i < size; i++){
					handlers[i].handleUndBestLevelChange(nanoOfDay, underlyingOrderBook.bestBidPrice, underlyingOrderBook.bestBidLevel, underlyingOrderBook.bestAskPrice, underlyingOrderBook.bestAskLevel, underlyingOrderBook.spread);
				}
			}
		}
	};
	
	/**
	 * This listener listens to MarketMakingChangeTracker
	 */
	private final MarketMakingChangeHandler secMMChangeListener = new MarketMakingChangeHandler() {
		
		@Override
		public void handleNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			int total = numBestLevels + numNonBestLevels;
			if (total != currentMmBidLevels){
				currentMmBidLevels = total;
				MarketContextChangeHandler[] handlers = marketContextChangeHandlers.elements();
				int size = marketContextChangeHandlers.size();
				for (int i = 0; i < size; i++){
					handlers[i].handleNumBidLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
				}
			}
		}
		
		@Override
		public void handleNumAskLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			int total = numBestLevels + numNonBestLevels;
			if (total != currentMmAskLevels){
				currentMmAskLevels = total;
				MarketContextChangeHandler[] handlers = marketContextChangeHandlers.elements();
				int size = marketContextChangeHandlers.size();
				for (int i = 0; i < size; i++){
					handlers[i].handleNumAskLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
				}
			}
		}
		
		@Override
		public void handleBidLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			currentMmBidBestQty = bestQty;
			currentMmBidNonBestQty = nonBestQty;
//			LOG.debug("MM bid quantity changed [secSid:{}, bestQty:{}, nonBestQty:{}, time:{}]", box(security.sid()), box(bestQty), box(nonBestQty), LocalTime.ofNanoOfDay(nanoOfDay));
			LOG.debug("MM bid quantity changed [secSid:{}, bestQty:{}, nonBestQty:{}]", box(security.sid()), box(bestQty), box(nonBestQty));
			updateMmBidSizeThreshold(nanoOfDay);
		}
		
		@Override
		public void handleBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
			if (currentBidTickLevel != bidLevel || currentAskTickLevel != askLevel){
				currentBidTickLevel = bidLevel;
				currentAskTickLevel = askLevel;
				
				// Since MarketContext has underlying information, we revise the definition of
				// isWide here with tickSense information
				WrtOutputParams params = (WrtOutputParams)security.wrtParams();
				if (currentUndSpreadInTick != 0 && params != null && params.tickSensitivity() != 0){
					final double tickSense = (double)params.tickSensitivity() / 1000.0;
					isSecWide = (currentAskTickLevel - currentBidTickLevel) > ((double)currentUndSpreadInTick / tickSense);
				}
				else {
					isSecWide = ((currentAskTickLevel - currentBidTickLevel) > 1);
				}
				
				MarketContextChangeHandler[] handlers = marketContextChangeHandlers.elements();
				int size = marketContextChangeHandlers.size();
				for (int i = 0; i < size; i++){
					handlers[i].handleBestLevelChange(currentBidTickLevel, currentAskTickLevel, isSecWide, nanoOfDay);
				}
			}
		}
		
		@Override
		public void handleAskLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			currentMmAskBestQty = bestQty;
			currentMmAskNonBestQty = nonBestQty;
//			LOG.debug("MM ask quantity changed [secSid:{}, bestQty:{}, nonBestQty:{}, time:{}]", box(security.sid()), box(bestQty), box(nonBestQty), LocalTime.ofNanoOfDay(nanoOfDay));
			LOG.debug("MM ask quantity changed [secSid:{}, bestQty:{}, nonBestQty:{}]", box(security.sid()), box(bestQty), box(nonBestQty));
			updateMmAskSizeThreshold(nanoOfDay);
		}

		@Override
		public void handleMMBestLevelChange(int mmBidLevel, int mmAskLevel, boolean isWide, long nanoOfDay) {
			if (currentMMBidTickLevel != mmBidLevel || currentMMAskTickLevel != mmAskLevel){
				currentMMBidTickLevel = mmBidLevel;
				currentMMAskTickLevel = mmAskLevel;
				
				WrtOutputParams params = (WrtOutputParams)security.wrtParams();
				if (currentUndSpreadInTick != 0 && params != null && params.tickSensitivity() > 0){
					final double tickSense = (double)params.tickSensitivity() / 1000.0;
					isMMSecWide = (currentMMAskTickLevel - currentMMBidTickLevel) > Math.ceil(((double)currentUndSpreadInTick / tickSense)); 
				}
				else {
					isMMSecWide = ((currentAskTickLevel - currentBidTickLevel) > 1);
				}
				
				MarketContextChangeHandler[] handlers = marketContextChangeHandlers.elements();
				int size = marketContextChangeHandlers.size();
				for (int i = 0; i < size; i++){
					handlers[i].handleMMBestLevelChange(currentMMBidTickLevel, currentMMAskTickLevel, isMMSecWide, nanoOfDay);
				}
			}
		}
		
		private void updateMmBidSizeThreshold(final long nanoOfDay) {
            long threshold = Math.max(currentMmBidBestQty, currentMmBidNonBestQty) / 2;
            if (security.lotSize() > 1) {
                threshold = Math.max((threshold / security.lotSize()) * security.lotSize(), security.lotSize());
            }
            if (mmBidSizeThreshold != threshold) {
                mmBidSizeThreshold = threshold;
                LOG.debug("MM bid quantity threshold set [secSid:{}, mmBidSizeThreshold:{}]", box(security.sid()), box(mmBidSizeThreshold));
            }
        }

		private void updateMmAskSizeThreshold(final long nanoOfDay) {
            long threshold = Math.max(currentMmAskBestQty, currentMmAskNonBestQty) / 2;
            if (security.lotSize() > 1) {
                threshold = Math.max((threshold / security.lotSize()) * security.lotSize(), security.lotSize());
            }
            if (mmAskSizeThreshold != threshold) {
                mmAskSizeThreshold = threshold;
                LOG.debug("MM ask quantity threshold set [secSid:{}, mmAskSizeThreshold:{}]", box(security.sid()), box(mmAskSizeThreshold));
            }
        }

	};
	
	public boolean addChangeHandler(MarketContextChangeHandler handler){
		if (!marketContextChangeHandlers.contains(handler)){
			return marketContextChangeHandlers.add(handler);
		}
		return true;
	}
	
	public ScoreBoardSecurityInfo security(){
		return security;
	}
	
	public ScoreBoardSecurityInfo underlying(){
		return underlying;
	}

	public PutOrCall putOrCall(){
		return putOrCall;
	}
	
	public SpreadTable undSpreadTable(){
		return undSpreadTable;
	}
	
	public SpreadTable secSpreadTable(){
		return secSpreadTable;
	}
	
	public int nullPrice(){
		return nullPrice;
	}

	public int nullTickLevel(){
		return nullTickLevel;
	}

	public void reset(){
		this.currentUndBidTickLevel = nullTickLevel;
		this.currentUndBidPrice = nullPrice;
		this.currentUndAskTickLevel = nullTickLevel;
		this.currentUndAskPrice = nullPrice;
		this.currentUndSpreadInTick = NULL_SPREAD;
		this.currentMmBidLevels = -1;
		this.currentMmAskLevels = -1;
		this.currentBidTickLevel = nullTickLevel;
		this.currentAskTickLevel = nullTickLevel;
		this.currentMmBidBestQty = 0;
		this.currentMmBidNonBestQty = 0;
		this.currentMmAskBestQty = 0;
		this.currentMmAskNonBestQty = 0;
		this.isUndWide = true;
		this.isSecWide = true;
	}
	
	public long currentMmBidBestQty(){
		return currentMmBidBestQty;
	}

	public long currentMmBidNonBestQty(){
		return currentMmBidNonBestQty;
	}
	
	public long currentMmAskBestQty(){
		return currentMmAskBestQty;
	}
	
	public long currentMmAskNonBestQty(){
		return currentMmAskNonBestQty;
	}
	
	public int currentBidTickLevel(){
		return currentBidTickLevel;
	}
	
	public int currentAskTickLevel(){
		return currentAskTickLevel;
	}
	
	public boolean isSecWide(){
		return isSecWide;
	}
	
	public int currentMMBidTickLevel(){
		return currentMMBidTickLevel;
	}
	
	public int currentMMAskTickLevel(){
		return currentMMAskTickLevel;
	}

	public int currentUndBidPrice(){
		return currentUndBidPrice;
	}
	
	public int currentUndBidTickLevel(){		
		return currentUndBidTickLevel;
	}
	
	public int currentUndAskPrice(){
		return currentUndAskPrice;
	}
	
	public int currentUndAskTickLevel(){
		return currentUndAskTickLevel;
	}
	
	public int currentUndSpreadInTick(){
		return currentUndSpreadInTick;
	}
	
	public int currentMmBidLevels(){
		return currentMmBidLevels;
	}
	
	public int currentMmAskLevels(){
		return currentMmAskLevels;
	}

	public boolean hasMmBidLevels(){
		return currentMmBidLevels != -1;
	}

	public boolean hasMmAskLevels(){
		return currentMmAskLevels != -1;
	}
	
	public boolean isUndWide(){
		return isUndWide;
	}
	
	/**
	 * Tick sense x 1000
	 * @return -1 if not available, basically anything <= 0 is not usable
	 */
	public int tickSense(){
		WrtOutputParams params = (WrtOutputParams)security.wrtParams();
		return (params != null) ? params.tickSensitivity() : -1;
	}
	
	public MarketMakingChangeHandler secMMChangeListener(){
		return secMMChangeListener;
	}
	
	public UnderlyingOrderBookUpdateHandler undOrderBookChangeHandler(){
		return undOrderBookChangeHandler;
	}

	public boolean hasValidBidLevel() {
		return currentBidTickLevel != nullTickLevel;
	}
	
	public long mmBidSizeThreshold() {
	    return mmBidSizeThreshold;
	}
	
    public long mmAskSizeThreshold() {
        return mmAskSizeThreshold;
    }

}
