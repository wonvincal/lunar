package com.lunar.strategy.scoreboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Doubles;
import com.lunar.core.AverageLongValue;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.OrderBookSide;
import com.lunar.message.io.sbe.Side;
import com.lunar.order.Tick;
import com.lunar.service.MarketDataSnapshotService;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.MarketDataUpdateHandler;

/**
 * Detect changes related to market making of a security
 * 1) Detect number of market making levels
 * 2) Detect quantity that can be considered as MM level
 * 3) Detect change in best bid and ask and whether is it wide or not wrt tick sensitivity
 * 
 * If an order book has 4 MM levels and all of a sudden all quantities have been pull away.
 * It should call listener with numLevels = 0.  The client should decide how to make use of this info.
 * 
 * How to determine 'normal' number of MM levels?
 * Time based.  If changed for more than N minutes, changed.
 * 
 * @author wongca
 *
 */
public class MarketMakingChangeTracker {
	static final Logger LOG = LogManager.getLogger(MarketMakingChangeTracker.class);
	public static long DEFAULT_MM_SIZE = 500_000L;
	public static long DEFAULT_MM_HOLD_THRESHOLD_NS = 1_000_000_000L; // Change needs to hold for one second to get registered
	public static long DEFAULT_MM_BIG_CHANGE_TAKE_EFFECT_TIME_NS = 15_000_000_000L; // Big change needs to hold for 15 seconds to get registered
	public static long DEFAULT_MM_ZERO_QUANTITY_TAKE_EFFECT_TIME_NS = 15_000_000_000L; // If zero quantity is hold for 15 seconds, reset 
	public static double DEFAULT_SIGNIFICANT_RATIO = 0.30;
	public static double DEFAULT_MM_SIZE_DISCOUNT = 0.80;
	public static double DEFAULT_REPORTING_TOLERANCE = 0.10;
	
	private final ScoreBoardSecurityInfo security;
	private final SideSpecificTracker bidSide;
	private final SideSpecificTracker askSide;
	private int currentBestBidTickLevel;
	private int currentBestAskTickLevel;
	private int currentBestBidTickLevelWithMMQty;
	private int currentBestAskTickLevelWithMMQty;
	
	/**
	 * Quantity must be held longer than this value in order to be accepted
	 */
	private final long mmSizeTakeEffectTimeInNs;
	private final long minMmSize;
	@SuppressWarnings("unused")
	private final double bigChangeRatio;
	private final double bigChangeLowerBound;
	private final double bigChangeHigherBound;
	/**
	 * If a significant change has been observed for longer than this value.  Average
	 * will be reset.
	 */
	private final long mmBigChangeTakeEffectTimeInNs;
	
	private final long mmZeroTakeEffectTimeInNs;
	private final QuantitySort sort = new QuantitySort();

	/**
	 * A level that has quantity >= best * qtyTolerance is counted as best levels (similar for non-best level)
	 */
	private final double mmSizeDiscount;
	@SuppressWarnings("unused")
	private final double mmSizeReportingTolerance;
	private final double mmSizeReportingToleranceLowerBound;
	private final double mmSizeReportingToleranceUpperBound;
	
	private MarketMakingChangeHandler changeHandler = MarketMakingChangeHandler.NULL_HANDLER;
	private class MultiMarketMakingChangeHandler implements MarketMakingChangeHandler {
	    private List<MarketMakingChangeHandler> handlers = new ArrayList<MarketMakingChangeHandler>(8);
	    
	    public void registerHandler(final MarketMakingChangeHandler handler) {
	        handlers.add(handler);
	    }

        public void unregisterHandler(final MarketMakingChangeHandler handler) {
            handlers.remove(handler);
        }

        @Override
        public void handleNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
            for (final MarketMakingChangeHandler handler : handlers) {
                handler.handleNumBidLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
            }
        }

        @Override
        public void handleBidLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
            for (final MarketMakingChangeHandler handler : handlers) {
                handler.handleBidLevelQuantityChange(bestQty, nonBestQty, nanoOfDay);
            }
        }

        @Override
        public void handleNumAskLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
            for (final MarketMakingChangeHandler handler : handlers) {
                handler.handleNumAskLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
            }
        }

        @Override
        public void handleAskLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
            for (final MarketMakingChangeHandler handler : handlers) {
                handler.handleAskLevelQuantityChange(bestQty, nonBestQty, nanoOfDay);
            }
        }

        @Override
        public void handleBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
            for (final MarketMakingChangeHandler handler : handlers) {
                handler.handleBestLevelChange(bidLevel, askLevel, isWide, nanoOfDay);
            }            
        }

        @Override
        public void handleMMBestLevelChange(int mmBidLevel, int mmAskLevel, boolean isWide, long nanoOfDay) {
            for (final MarketMakingChangeHandler handler : handlers) {
                handler.handleMMBestLevelChange(mmBidLevel, mmAskLevel, isWide, nanoOfDay);
            }            
        }
	    
	}
	
	private final SideSpecificMarketMakingChangeHandler bidSideChangeHandler = new SideSpecificMarketMakingChangeHandler(){

		@Override
		public void handleNumLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			changeHandler.handleNumBidLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
		}

		@Override
		public void handleLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			changeHandler.handleBidLevelQuantityChange(bestQty, nonBestQty, nanoOfDay);
		}
		
	};
	
	private final SideSpecificMarketMakingChangeHandler askSideChangeHandler = new SideSpecificMarketMakingChangeHandler(){

		@Override
		public void handleNumLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			changeHandler.handleNumAskLevelChange(numBestLevels, numNonBestLevels, nanoOfDay);
		}

		@Override
		public void handleLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			changeHandler.handleAskLevelQuantityChange(bestQty, nonBestQty, nanoOfDay);
		}
		
	};

	public static class QuantitySort {
		long[] quantities;
		int index;
		
		QuantitySort(){
			quantities = new long[5];
			index = 0;
		}
		
		void reset(){
			index = 0;
		}

		int size(){
			return index;
		}
		
		long[] elements(){
			return quantities;
		}
		
		void add(long quantity){
			if (index != 0){
				// Not empty
				for (int i = 0; i < index; i++){
					if (quantities[i] == quantity){
						return;
					}
					else if (quantity > quantities[i]){
						System.arraycopy(quantities, i, quantities, i + 1, index - i);							
						quantities[i] = quantity;
						index++;
						return;
					}
				}
			}
			quantities[index++] = quantity;
		}
	}

	private static class Context {
		private String tag;
		private long qty;
		private long qtyChangeStartTime;
		private final AverageLongValue twaMmSize;
		private long lastObservedTime;
		private long bigChangeObservedTime;
		private long zeroObservedTime;
		private long numLevels;
		private long numLevelsChangeStartTime;
		private final AverageLongValue twaNumMmLevels;
		private long lastNumLevelsObservedTime;
		
		Context(){
			this.tag = "";
			this.qty = 0;
			this.qtyChangeStartTime = ServiceConstant.NULL_TIME_NS;
			this.twaMmSize = new AverageLongValue();
			this.lastObservedTime = Long.MAX_VALUE;
			this.lastNumLevelsObservedTime = Long.MAX_VALUE;
			this.bigChangeObservedTime = Long.MAX_VALUE;
			this.zeroObservedTime = Long.MAX_VALUE;
			this.numLevels = 0;
			this.numLevelsChangeStartTime = ServiceConstant.NULL_TIME_NS;
			this.twaNumMmLevels = new AverageLongValue();
		}
		String tag(){ return tag;}
		Context tag(String value){
			this.tag = value;
			return this;
		}
		long qty(){ return qty; }
		long qtyChangeStartTime() { return qtyChangeStartTime; }
		AverageLongValue twaMmSize() { return twaMmSize; }
		
		long numLevels(){ return numLevels; }
		long numLevelsChangeStartTime() { return numLevelsChangeStartTime; }
		AverageLongValue twaNumMmLevels() { return twaNumMmLevels; }
		
		long lastObservedTime() { return lastObservedTime; }
		long bigChangeObservedTime() { return bigChangeObservedTime; }
		long zeroObservedTime() { return zeroObservedTime; }
		Context qty(long value) { this.qty = value; return this; }
		Context qtyChangeStartTime(long value) { this.qtyChangeStartTime = value; return this; }
		Context lastObservedTime(long value) { this.lastObservedTime = value; return this; }
		Context bigChangeObservedTime(long value) { this.bigChangeObservedTime = value; return this; }
		Context zeroObservedTime(long value) { this.zeroObservedTime = value; return this; }
		Context numLevels(long value) { this.numLevels = value; return this; }
		Context numLevelsChangeStartTime(long value) { this.numLevelsChangeStartTime = value; return this; }
		long lastNumLevelsObservedTime() { return lastNumLevelsObservedTime; }
		Context lastNumLevelsObservedTime(long value) { this.lastNumLevelsObservedTime = value; return this; }
	}
	
	public interface SideSpecificMarketMakingChangeHandler {
		void handleNumLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay);
		void handleLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay);
		
		public final static SideSpecificMarketMakingChangeHandler NULL_HANDLER = new SideSpecificMarketMakingChangeHandler() {
			@Override
			public void handleNumLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
			}
			@Override
			public void handleLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
			}
		};
	}
	
	private class SideSpecificTracker {
		public class LightWeightOrderBookSide {
			private final Tick[] ticksByPriceLevel;
			private final TicksIterator ticksByPriceLevelIterator;
			private int numPriceLevels;

			LightWeightOrderBookSide(int bookDepth){
				this.ticksByPriceLevel = new Tick[bookDepth];
				this.numPriceLevels = 0;
				this.ticksByPriceLevelIterator = new TicksByPriceLevelIterator();
				for (int i = 0; i < bookDepth; i++) {
					ticksByPriceLevel[i] = Tick.of(nullTickLevel, nullPrice);
				}		
			}
			
			Iterator<Tick> localPriceLevelsIterator() {
				ticksByPriceLevelIterator.reset();
				return ticksByPriceLevelIterator;
			}

			void numPriceLevels(int value){
				this.numPriceLevels = value;
			}
			
			void replaceTick(int index, Tick tick){
				if (tick.tickLevel() != nullTickLevel){
					ticksByPriceLevel[index].price(tick.price()).qty(tick.qty())
						.tickLevel(tick.tickLevel()).priceLevel(index).numOrders(1);
				}
				else {
					ticksByPriceLevel[index].tickLevel(nullTickLevel).numOrders(0);
				}
			}
			
			abstract class TicksIterator implements Iterator<Tick> {
				protected int index = 0;
				private Tick[] ticks;
				
				TicksIterator(final Tick[] ticks) {
					this.ticks = ticks;
				}
				
				public void reset() {
					index = 0;
				}
				
				@Override
				public Tick next() {
					return this.ticks[index++];
				}
			}

			class TicksByPriceLevelIterator extends TicksIterator {
				TicksByPriceLevelIterator() {
					super(ticksByPriceLevel);
				}

				@Override
				public boolean hasNext() {
					return index < numPriceLevels;
				}
			}
		}

		public class ObservedResult {
			int bestTickLevel;
			int bestTickLevelWithMMQty;
			boolean changed;

			public boolean changed(){ return changed;}
			public int bestTickLevel(){ return bestTickLevel;}
			public int bestTickLevelWithMMQty(){ return bestTickLevelWithMMQty;}
			public void clear(){
				this.changed = false;
				this.bestTickLevel = nullTickLevel;
				this.bestTickLevelWithMMQty = nullTickLevel;
			}
			@Override
			public String toString() {
				return "bestTickLevel: " + bestTickLevel + ", bestTickLevelWtihMMQty: " + bestTickLevelWithMMQty;
			}
		}		

		private final Side side;
		private final LightWeightOrderBookSide ownSide;
		private Context best;
		private Context nonBest;
		private int reportedNumBestLevels;
		private int reportedNumNonBestLevels;
		private double reportedBestMmQty;
		private double reportedNonBestMmQty;
		private SideSpecificMarketMakingChangeHandler listener;
		private final ObservedResult result;
		private final int nullTickLevel;
		private final int nullPrice;
		
		SideSpecificTracker(Side side, SideSpecificMarketMakingChangeHandler listener, int bookDepth, int nullTickLevel, int nullPrice){
			this.side = side;
			this.best = new Context().tag((this.side == Side.BUY) ? "bid best" : "ask best");
			this.nonBest = new Context().tag((this.side == Side.BUY) ? "bid non best" : "ask non best");
			this.reportedNumBestLevels = 0;
			this.reportedNumNonBestLevels = 0;
			this.reportedBestMmQty = 0.0;
			this.reportedNonBestMmQty = 0.0;
			this.listener = listener;
			this.nullPrice = nullPrice;
			this.nullTickLevel = nullTickLevel;
			this.ownSide = new LightWeightOrderBookSide(bookDepth);
			this.result = new ObservedResult();
		}
		
		/**
		 * To save processing power, we can work out best and nonBest only when observing
		 * time tick, not order book update
		 * @param nanoOfDay
		 */
		ObservedResult observeNanoOfDay(long nanoOfDay){
			result.clear();
			takeQty(best, nanoOfDay, best.qty());
			takeQty(nonBest, nanoOfDay, nonBest.qty());
			if (best.numLevels() != 0){
				takeNumLevels(best, nanoOfDay, best.numLevels());
			}
			if (nonBest.numLevels() != 0){
				takeNumLevels(nonBest, nanoOfDay, nonBest.numLevels());
			}
			
			// Check if reported mm sizes have changed
			if ((Doubles.compare(best.twaMmSize.averageValue(), reportedBestMmQty * mmSizeReportingToleranceLowerBound) < 0 || Doubles.compare(best.twaMmSize.averageValue(), reportedBestMmQty * mmSizeReportingToleranceUpperBound) > 0) || 
				(Doubles.compare(nonBest.twaMmSize.averageValue(), reportedNonBestMmQty * mmSizeReportingToleranceLowerBound) < 0 || Doubles.compare(nonBest.twaMmSize.averageValue(), reportedNonBestMmQty * mmSizeReportingToleranceUpperBound) > 0)){
				reportedBestMmQty = best.twaMmSize.averageValue();
				reportedNonBestMmQty = nonBest.twaMmSize.averageValue();
				listener.handleLevelQuantityChange((long)reportedBestMmQty, (long)reportedNonBestMmQty, nanoOfDay);
				
				// Found out if any level or num levels have been changed
				int bestCount = 0;
				int nonBestCount = 0;
				final Iterator<Tick> iterator = this.ownSide.localPriceLevelsIterator();
				while (iterator.hasNext()){
					Tick tick = iterator.next();					
					long qty = tick.qty();
					if (result.bestTickLevel == nullTickLevel){
						result.bestTickLevel = tick.tickLevel();
						result.changed = true;
					}
					if (best.twaMmSize.isValidAverage() && qty >= best.twaMmSize.averageValue() * mmSizeDiscount ){
						bestCount++;
						if (result.bestTickLevelWithMMQty == nullTickLevel){
							result.bestTickLevelWithMMQty = tick.tickLevel();
							result.changed = true;
						}
					}
					else if (nonBest.twaMmSize.isValidAverage() && qty >= nonBest.twaMmSize.averageValue() * mmSizeDiscount){
						if (result.bestTickLevelWithMMQty == nullTickLevel){
							result.bestTickLevelWithMMQty = tick.tickLevel();
							result.changed = true;
						}
						nonBestCount++;
					}
				}
				takeNumLevels(best, nanoOfDay, bestCount);
				takeNumLevels(nonBest, nanoOfDay, nonBestCount);
				if (nonBestCount != reportedNumNonBestLevels || bestCount != reportedNumBestLevels){
					reportedNumNonBestLevels = nonBestCount;
					reportedNumBestLevels = bestCount;
					listener.handleNumLevelChange(reportedNumBestLevels, reportedNumNonBestLevels, nanoOfDay);
				}
			}
			return result;
		}
		
		ObservedResult observeOrderBookSide(long nanoOfDay, OrderBookSide side){
			// iterate and copy order book side
			// Loop thru all levels to find out
			boolean processedBest = false;
			boolean processedNonBest = false;
			
			// Find best with MM Size
			result.clear();
			int bestCount = 0;
			int nonBestCount = 0;
			if (!side.isEmpty()){
				final Iterator<Tick> iterator = side.localPriceLevelsIterator();
				int numPriceLevels = 0;

				// Loop thru all levels
				// The highest quantity we feed it off to best
				// The quantity that is lower than N% of highest quality should be group to non-best
				// Since each quantity >= best * MM_SIZE_DISCOUNT will be treated as best, I think
				// one starting point is to group the highest quantity < best * MM_SIZE_DISCOUNT into non_best
				sort.reset();
				while (iterator.hasNext()){
					Tick tick = iterator.next();					
					long qty = tick.qty();
					if (tick.tickLevel() != side.nullTickLevel() && qty >= minMmSize){
						sort.add(qty);
					}
					ownSide.replaceTick(numPriceLevels, tick);
					numPriceLevels++;
				}

				if (sort.size() > 0){
					long highestQty = sort.elements()[0];
					takeQty(best, nanoOfDay, highestQty);
					processedBest = true;
					if (best.twaMmSize.isValidAverage()){
						for (int i = 1; i < sort.size(); i++){
							if (sort.elements()[i] < best.twaMmSize.averageValue() * mmSizeDiscount){
								takeQty(nonBest, nanoOfDay, sort.elements()[i]);
								processedNonBest = true;
								break;
							}
						}
					}
					else {
						for (int i = 1; i < sort.size(); i++){
							if (sort.elements()[i] < highestQty * mmSizeDiscount){
								takeQty(nonBest, nanoOfDay, sort.elements()[i]);
								processedNonBest = true;
								break;
							}
						}						
					}
					
					final Iterator<Tick> localPriceLevelsIterator = side.localPriceLevelsIterator();

					// Loop thru all levels
					// The highest quantity we feed it off to best
					// The quantity that is lower than N% of highest quality should be group to non-best
					// Since each quantity >= best * MM_SIZE_DISCOUNT will be treated as best, I think
					// one starting point is to group the highest quantity < best * MM_SIZE_DISCOUNT into non_best
					while (localPriceLevelsIterator.hasNext()){
						Tick tick = localPriceLevelsIterator.next();					
						long qty = tick.qty();
						if (result.bestTickLevel == nullTickLevel){
							result.bestTickLevel = tick.tickLevel();
							result.changed = true;
						}
						if (best.twaMmSize.isValidAverage() && qty >= best.twaMmSize.averageValue() * mmSizeDiscount ){
							bestCount++;						
							if (result.bestTickLevelWithMMQty == nullTickLevel){
								result.bestTickLevelWithMMQty = tick.tickLevel();
								result.changed = true;
							}
						}
						else if (nonBest.twaMmSize.isValidAverage() && qty >= nonBest.twaMmSize.averageValue() * mmSizeDiscount){
							if (result.bestTickLevelWithMMQty == nullTickLevel){
								result.bestTickLevelWithMMQty = tick.tickLevel();
								result.changed = true;
							}
							nonBestCount++;
						}
					}
					
				}
				ownSide.numPriceLevels = numPriceLevels;
			}

			// Best has disappeared, count as zero
			if (!processedBest){
				takeQty(best, nanoOfDay, 0);
			}
			
			// Non best bid has disappeared, count as zero
			if (!processedNonBest){
				takeQty(nonBest, nanoOfDay, 0);
			}
			
			// Since average might have changed
			takeNumLevels(best, nanoOfDay, bestCount);
			takeNumLevels(nonBest, nanoOfDay, nonBestCount);
			
			// Tell if number of levels have changed
			// Do we need to look into each levels each time we observe an order book update?
			if ((Doubles.compare(best.twaMmSize.averageValue(), reportedBestMmQty * mmSizeReportingToleranceLowerBound) < 0 || 
				Doubles.compare(best.twaMmSize.averageValue(), reportedBestMmQty * mmSizeReportingToleranceUpperBound) > 0) || 
				(Doubles.compare(nonBest.twaMmSize.averageValue(), reportedNonBestMmQty * mmSizeReportingToleranceLowerBound) < 0 || 
				Doubles.compare(nonBest.twaMmSize.averageValue(), reportedNonBestMmQty * mmSizeReportingToleranceUpperBound) > 0)){
				reportedBestMmQty = best.twaMmSize.averageValue();
				reportedNonBestMmQty = nonBest.twaMmSize.averageValue();
				listener.handleLevelQuantityChange((long)reportedBestMmQty, (long)reportedNonBestMmQty, nanoOfDay);
			}

			if (nonBestCount != reportedNumNonBestLevels || bestCount != reportedNumBestLevels){
				reportedNumNonBestLevels = nonBestCount;
				reportedNumBestLevels = bestCount;
				listener.handleNumLevelChange(reportedNumBestLevels, reportedNumNonBestLevels, nanoOfDay);
			}

			return result;
		}
		
		private void resetAvgIfDeviateTooMuch(Context context, long qty, long currentNanoOfDay){
			AverageLongValue twa = context.twaMmSize();
			if (!twa.isValidAverage()){
				return;
			}
			double ratio = (double)qty / twa.averageValue();
			if (Doubles.compare(ratio, bigChangeLowerBound) < 0 || Doubles.compare(ratio, bigChangeHigherBound) > 0){
				if (context.bigChangeObservedTime() != Long.MAX_VALUE){
					// Reset average if current
					if (currentNanoOfDay -  context.bigChangeObservedTime() >= mmBigChangeTakeEffectTimeInNs){
//						LOG.info("Accept big change [context:{}, qty:{}, currentTime:{}", context.tag, qty, currentNanoOfDay);
						context.bigChangeObservedTime(Long.MAX_VALUE);
						context.twaMmSize.reset();
					}
				}
				else {
//					LOG.info("Observed big change [context:{}, qty:{}, avg:{}, ratio:{}, currentTime:{}]", context.tag, qty, twa.averageValue(), ratio, currentNanoOfDay);
					context.bigChangeObservedTime(currentNanoOfDay);
				}
			}
			else {
				context.bigChangeObservedTime(Long.MAX_VALUE);
			}
		}
		
		private void updateTwa(Context context, long qty, long timeElapsedInMs, long nanoOfDay){
//			if (context.tag == "bid best"){
//				LOG.info("try updating twa [context:{}, qty:{}, timeElapsedInMs:{}]", context.tag, qty, timeElapsedInMs);				
//			}
			if (qty != 0){
				AverageLongValue twa = context.twaMmSize();
				if (twa.isValidAverage()){
					double ratio = (double)qty / twa.averageValue();
					if (Doubles.compare(ratio, bigChangeLowerBound) < 0 || Doubles.compare(ratio, bigChangeHigherBound) > 0){
						if (context.bigChangeObservedTime() != Long.MAX_VALUE){
							// Reset average if current
							if (nanoOfDay -  context.bigChangeObservedTime() >= mmBigChangeTakeEffectTimeInNs){
//								LOG.info("Accept big change [context:{}, qty:{}, currentTime:{}", context.tag, qty, nanoOfDay);
								context.bigChangeObservedTime(Long.MAX_VALUE);
//								LOG.info("Reset average due to bigChangeObserved [context:{}]", context.tag);
								context.twaMmSize.reset();
							}
						}
						else {
//							LOG.info("Observed big change [context:{}, qty:{}, avg:{}, ratio:{}, currentTime:{}]", context.tag, qty, twa.averageValue(), ratio, nanoOfDay);
//							LOG.info("Observed big change [context:{}, qty:{}, currentTime:{}]", context.tag, qty, nanoOfDay);
							context.bigChangeObservedTime(nanoOfDay);
						}
					}
					else {
						// Reset if big change is no longer observed
						context.bigChangeObservedTime(Long.MAX_VALUE);
					}					
				}
				context.zeroObservedTime(Long.MAX_VALUE);
				context.twaMmSize.addValue(qty * timeElapsedInMs, timeElapsedInMs);	
			}
			else{
				if (context.zeroObservedTime() != Long.MAX_VALUE){
					// Reset if zero quantity has been observed for some time
					if (nanoOfDay - context.zeroObservedTime() >= mmZeroTakeEffectTimeInNs){
						context.zeroObservedTime(Long.MAX_VALUE);
//						LOG.info("Reset average due to zeroObserved [context:{}]", context.tag);						
						// No need to observe again if it has already been reset, but it doesn't hurt 
						context.twaMmSize.reset();
					}
				}
				else {
					context.zeroObservedTime(nanoOfDay);
				}
			}				
		}

		private void updateNumLevelsTwa(Context context, long qty, long timeElapsedInMs, long nanoOfDay){
			context.twaNumMmLevels.addValue(qty * timeElapsedInMs, timeElapsedInMs);
		}

		private void takeNumLevels(Context context, long nanoOfDay, long value){
			if (context.numLevelsChangeStartTime() != ServiceConstant.NULL_TIME_NS){
				long timeElapsed = nanoOfDay - context.numLevelsChangeStartTime();
				
				if (context.numLevels() != value){
					//if (timeElapsed >= mmSizeTakeEffectTimeInNs && context.numLevels() != 0){
					if (context.numLevels() != 0){
						updateNumLevelsTwa(context, context.numLevels(), timeElapsed / 1_000_000, nanoOfDay);
					}
					context.numLevels(value)
						.numLevelsChangeStartTime(nanoOfDay)
						.lastNumLevelsObservedTime(nanoOfDay);

				}
				else {
					long timeSinceLastObserved = nanoOfDay - context.lastNumLevelsObservedTime();
					if (context.numLevels() != 0){
						updateNumLevelsTwa(context, context.numLevels(), timeSinceLastObserved / 1_000_000, nanoOfDay);
					}
					context.lastNumLevelsObservedTime(nanoOfDay);
				}					
			}
			else {
				context.numLevels(value)
					.numLevelsChangeStartTime(nanoOfDay)
					.lastNumLevelsObservedTime(nanoOfDay);
			}
		}
		
		private void takeQty(Context context, long nanoOfDay, long value){
			if (context.qtyChangeStartTime() != ServiceConstant.NULL_TIME_NS){
				long timeElapsed = nanoOfDay - context.qtyChangeStartTime();
				
				// Do not take any zero value into average
				if (context.qty() != value){
					if (timeElapsed >= mmSizeTakeEffectTimeInNs){
						updateTwa(context, context.qty(), timeElapsed / 1_000_000, nanoOfDay);
					}					
					context.qty(value)
						.qtyChangeStartTime(nanoOfDay)
						.lastObservedTime(nanoOfDay);
				}
				else {
					// If the same value has lasted for more than mmSizeTakeEffectTimeInNs
					// updateTwa for that
					if (timeElapsed >= mmSizeTakeEffectTimeInNs){
						long timeSinceLastObserved = nanoOfDay - context.lastObservedTime();
						if (timeSinceLastObserved >= 1_000_000){
							updateTwa(context, context.qty(), timeSinceLastObserved / 1_000_000, nanoOfDay);
							context.lastObservedTime(nanoOfDay);
						}
					}				
				}
			}
			else {
				context.qty(value)
					.qtyChangeStartTime(nanoOfDay)
					.lastObservedTime(nanoOfDay);
			}
		}
		
		long twaNumMmLevels(){
			return best.twaNumMmLevels().averageValue() + nonBest.twaNumMmLevels().averageValue();
		}
	}
	
	public static MarketMakingChangeTracker of(ScoreBoardSecurityInfo security){
		return new MarketMakingChangeTracker(security, DEFAULT_MM_SIZE, DEFAULT_MM_HOLD_THRESHOLD_NS, DEFAULT_SIGNIFICANT_RATIO, DEFAULT_MM_BIG_CHANGE_TAKE_EFFECT_TIME_NS, DEFAULT_MM_ZERO_QUANTITY_TAKE_EFFECT_TIME_NS, DEFAULT_MM_SIZE_DISCOUNT, DEFAULT_REPORTING_TOLERANCE, MarketDataSnapshotService.BOOK_DEPTH, Integer.MIN_VALUE, Integer.MIN_VALUE);
	}
	
	public static MarketMakingChangeTracker of(ScoreBoardSecurityInfo security, long minMmSize, long mmHoldThresholdInNs, double significantChangeRatio, long mmBigChangeTakeEffectTimeInNs, long mmZeroTakeEffectTimeInNs, double mmSizeDiscount, double avgTolerance, int bookDepth, int nullTickLevel, int nullPrice){
		return new MarketMakingChangeTracker(security, minMmSize, mmHoldThresholdInNs, significantChangeRatio, mmBigChangeTakeEffectTimeInNs, mmZeroTakeEffectTimeInNs, mmSizeDiscount, avgTolerance, bookDepth, nullTickLevel, nullPrice);
	}

	/**
	 * 
	 * @param security
	 * @param minMmSize	Minimum size to be considered as an MM level
	 * @param mmSizeTakeEffectTimeInNs	Duration required to register a quantity as a valid MM size
	 * @param bigChangeRatio	Amount of change relative to current MM size to be considered as a significant change
	 * @param mmBigChangeTakeEffectTimeInNs	Duration required to clear previously determined MM size and register a big-changed-quantity as a valid MM size
	 * @param mmSizeDiscount A quantity is considered as a MM size if it is >= qtyTolerance * MM size 
	 * @param mmSizeReportingTolerance	Report change in MM size only if it has been changed more than this tolerance level
	 * @param bookDepth
	 * @param nullTickLevel
	 * @param nullPrice
	 */
	MarketMakingChangeTracker(ScoreBoardSecurityInfo security, long minMmSize, long mmSizeTakeEffectTimeInNs, double bigChangeRatio, long mmBigChangeTakeEffectTimeInNs, long mmZeroTakeEffectTimeInNs, double mmSizeDiscount, double mmSizeReportingTolerance, int bookDepth, int nullTickLevel, int nullPrice){
		this.security = security;
		this.minMmSize = minMmSize;
		this.mmSizeTakeEffectTimeInNs = mmSizeTakeEffectTimeInNs;
		this.mmBigChangeTakeEffectTimeInNs = mmBigChangeTakeEffectTimeInNs;
		this.mmZeroTakeEffectTimeInNs = mmZeroTakeEffectTimeInNs;
		this.bidSide = new SideSpecificTracker(Side.BUY, bidSideChangeHandler, bookDepth, nullTickLevel, nullPrice);
		this.askSide = new SideSpecificTracker(Side.SELL, askSideChangeHandler, bookDepth, nullTickLevel, nullPrice);
		this.currentBestAskTickLevel = nullTickLevel;
		this.currentBestBidTickLevel = nullTickLevel;
		this.currentBestAskTickLevelWithMMQty = nullTickLevel;
		this.currentBestBidTickLevelWithMMQty = nullTickLevel;
		if (Doubles.compare(bigChangeRatio, 0) <= 0){
			throw new IllegalArgumentException("significantChangeRatio must be greater than zero");
		}
		if (Doubles.compare(bigChangeRatio, 1) >= 0){
			throw new IllegalArgumentException("significantChangeRatio must be less than one");
		}
		this.bigChangeRatio = bigChangeRatio;
		this.bigChangeLowerBound = 1.00 - bigChangeRatio;
		this.bigChangeHigherBound = 1.00 + bigChangeRatio;
		this.mmSizeDiscount = mmSizeDiscount;
		this.mmSizeReportingTolerance = mmSizeReportingTolerance; // 0.9
		this.mmSizeReportingToleranceLowerBound = 1.00 - mmSizeReportingTolerance;
		this.mmSizeReportingToleranceUpperBound = 1.00 + mmSizeReportingTolerance;
		
		// Register itself to security
		this.security.registerMdUpdateHandler(marketDataUpdateHandler);
		this.security.marketMakingChangeTracker(this);
	}
	
	public void observeNanoOfDay(long nanoOfDay){
		SideSpecificTracker.ObservedResult observedBidSideResult = this.bidSide.observeNanoOfDay(nanoOfDay);
		SideSpecificTracker.ObservedResult observedAskSideResult = this.askSide.observeNanoOfDay(nanoOfDay);

		if ((observedBidSideResult.changed && observedBidSideResult.bestTickLevelWithMMQty() != this.currentBestBidTickLevelWithMMQty) ||
			(observedAskSideResult.changed && observedAskSideResult.bestTickLevelWithMMQty() != this.currentBestAskTickLevelWithMMQty)){
			this.currentBestAskTickLevelWithMMQty = observedAskSideResult.bestTickLevelWithMMQty;
			this.currentBestBidTickLevelWithMMQty = observedBidSideResult.bestTickLevelWithMMQty;
			
			this.changeHandler.handleMMBestLevelChange(currentBestBidTickLevelWithMMQty, 
				currentBestAskTickLevelWithMMQty,
				(currentBestBidTickLevelWithMMQty - currentBestAskTickLevelWithMMQty) > 1,
				nanoOfDay);
		}
}

	private final MarketDataUpdateHandler marketDataUpdateHandler = new MarketDataUpdateHandler() {
		
		@Override
		public void onTradeReceived(Security srcSecurity, long timestamp, MarketTrade trade) throws Exception {
			// NOOP
		}
		
		@Override
		public void onOrderBookUpdated(Security srcSecurity, long transactTime, MarketOrderBook orderBook) throws Exception {
			observeOrderBook(transactTime, security.orderBook());
		}
		
	};
	
	void observeOrderBook(long nanoOfDay, MarketOrderBook orderBook){
		SideSpecificTracker.ObservedResult observedBidSideResult = this.bidSide.observeOrderBookSide(nanoOfDay, orderBook.bidSide());
		SideSpecificTracker.ObservedResult observedAskSideResult = this.askSide.observeOrderBookSide(nanoOfDay, orderBook.askSide());
		
		// TODO Determine if we want to use only price level with quantity >= MM Quantity
		if ((observedBidSideResult.changed && (observedBidSideResult.bestTickLevel() != this.currentBestBidTickLevel)) || 
			(observedAskSideResult.changed && (observedAskSideResult.bestTickLevel() != this.currentBestAskTickLevel))){
			this.currentBestBidTickLevel = observedBidSideResult.bestTickLevel();
			this.currentBestAskTickLevel = observedAskSideResult.bestTickLevel();
			
			this.changeHandler.handleBestLevelChange(this.currentBestBidTickLevel, 
				this.currentBestAskTickLevel, 
				(this.currentBestAskTickLevel - this.currentBestBidTickLevel) > 1,
				nanoOfDay);
		}
		if ((observedBidSideResult.changed && (observedBidSideResult.bestTickLevelWithMMQty() != this.currentBestBidTickLevelWithMMQty)) ||
			(observedAskSideResult.changed && (observedAskSideResult.bestTickLevelWithMMQty() != this.currentBestAskTickLevelWithMMQty))){
			this.currentBestAskTickLevelWithMMQty = observedAskSideResult.bestTickLevelWithMMQty;
			this.currentBestBidTickLevelWithMMQty = observedBidSideResult.bestTickLevelWithMMQty;
			
			this.changeHandler.handleMMBestLevelChange(currentBestBidTickLevelWithMMQty, 
				currentBestAskTickLevelWithMMQty,
				(currentBestBidTickLevelWithMMQty - currentBestAskTickLevelWithMMQty) > 1,
				nanoOfDay);
		}
	}

	public void registerChangeHandler(MarketMakingChangeHandler item){
	    if (this.changeHandler == MarketMakingChangeHandler.NULL_HANDLER) {
	        this.changeHandler = item;    
	    }
	    else {
	        if (!(this.changeHandler instanceof MultiMarketMakingChangeHandler)) {
	            MultiMarketMakingChangeHandler multiHandler = new MultiMarketMakingChangeHandler();
	            multiHandler.registerHandler(this.changeHandler);
	            this.changeHandler = multiHandler;
	        }
            ((MultiMarketMakingChangeHandler)this.changeHandler).registerHandler(item);
	    }		
	}

	public boolean isTwaBestBidMmSizeValid(){
		return this.bidSide.best.twaMmSize.isValidAverage();
	}
	
	public long getTwaBestBidMmSize(){
		return this.bidSide.best.twaMmSize.averageValue();
	}

	public boolean isTwaNonBestBidMmSizeValid(){
		return this.bidSide.nonBest.twaMmSize.isValidAverage();
	}
	
	public long getTwaNonBestBidMmSize(){
		return this.bidSide.nonBest.twaMmSize.averageValue();
	}
	
	AverageLongValue twaBestBidMmSize(){
		return this.bidSide.best.twaMmSize;
	}

	AverageLongValue twaNonBestBidMmSize(){
		return this.bidSide.nonBest.twaMmSize;
	}

	public boolean isTwaBestAskMmSizeValid(){
		return this.askSide.best.twaMmSize.isValidAverage();
	}
	
	public long getTwaBestAskMmSize(){
		return this.askSide.best.twaMmSize.averageValue();
	}

	public boolean isTwaNonBestAskMmSizeValid(){
		return this.askSide.nonBest.twaMmSize.isValidAverage();
	}
	
	public long getTwaNonBestAskMmSize(){
		return this.askSide.nonBest.twaMmSize.averageValue();
	}
	
	public long getTwaMmBidLevels(){
		return this.bidSide.twaNumMmLevels();
	}
	
	public long getTwaMmAskLevels(){
		return this.askSide.twaNumMmLevels();
	}

	AverageLongValue twaBestAskMmSize(){
		return this.askSide.best.twaMmSize;
	}

	AverageLongValue twaNonBestAskMmSize(){
		return this.askSide.nonBest.twaMmSize;
	}

	long mmHoldThresholdInNs(){
		return mmSizeTakeEffectTimeInNs;
	}
	
	long mmBigChangeTakeEffectTimeInNs(){
		return mmBigChangeTakeEffectTimeInNs;
	}
	
	@Override
	public String toString() {
		return "twaBestBid: " + (bidSide.best.twaMmSize.isValidAverage() ? bidSide.best.twaMmSize.averageValue() : -1) + 
				", twaNonBestBid: " + (bidSide.nonBest.twaMmSize.isValidAverage() ? bidSide.nonBest.twaMmSize.averageValue() : -1) +
				", twaBestAsk: " + (askSide.best.twaMmSize.isValidAverage() ? askSide.best.twaMmSize.averageValue() : -1) +
				", twaNonBestAsk: " + (askSide.nonBest.twaMmSize.isValidAverage() ? askSide.nonBest.twaMmSize.averageValue() : -1);
	}
}
