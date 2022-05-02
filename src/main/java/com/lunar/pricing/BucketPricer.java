package com.lunar.pricing;

import static org.apache.logging.log4j.util.Unbox.box;

import java.nio.BufferOverflowException;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.EventHandler;
import com.lunar.core.MutableBoolean;
import com.lunar.core.TriggerInfo;
import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.util.ImmutableLongInterval;
import com.lunar.util.LongInterval;
import com.lunar.util.NonOverlappingLongIntervalTreeSet;
import com.lunar.util.ObjectCircularBuffer;

import it.unimi.dsi.fastutil.ints.Int2LongMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * 
 * @author wongca
 *
 */
public class BucketPricer {
	public static final boolean BT_COMPARISON_MODE = false;
	public static final int PRICE_TYPE_MID = 0;
	public static final int PRICE_TYPE_WEIGHTED = 1;
	public static enum ViolationType {
		NO_VIOLATION,
		INCONSISTENT,
		DOWN_VOL,
		UP_VOL,
		PRICE_OVERLAPPED,
		ERROR, 
		BUCKET_TOO_BIG
	}
	
	static final Logger LOG = LogManager.getLogger(BucketPricer.class);
	public static final long MAX_INTERVAL_VALUE = 4294967295l; // 2^32 - 1
	static final long HIGHER_ORDER_MASK = 0xFFFFFFFF00000000l;
	static final long LOWER_ORDER_MASK = 0x00000000FFFFFFFFl;
	private final static int MAX_PRICE = 250;
	static IllegalArgumentException EMPTY_INTERVAL = new IllegalArgumentException("Empty interval");
	static int TICK_UNKNOWN = 0;
	static int TICK_UP = 1;
	static int TICK_DOWN = 2;
	static int TICK_FLAT = 4;	
	private final ObservedGreeks observedGreeks;
	private final ObjectCircularBuffer<LongTimedPrice> observedUndSpots;
	private NonOverlappingLongIntervalTreeSet observedIntervals;
	private Int2LongOpenHashMap observedIntervalsByDerivPrice;
	private NonOverlappingLongIntervalTreeSet extrapolatedIntervals;
	private Int2LongOpenHashMap extrapolatedIntervalsByDerivPrice;
	private final PricerValidator currentPriceValidator;
	private final SpreadTable derivSpreadTable;
    private final LongInterval prevObservedInterval = LongInterval.of();
//    private ChangeHandler changeHandler = ChangeHandler.NULL_HANDLER;
	private final IntervalExtractor extractor;
	private TriggerInfo lastTriggerInfo = TriggerInfo.of();

//	private final LongInterval intervalItem = LongInterval.of();
//    private final LongInterval prevReportedActiveInterval = LongInterval.of();
//    private final ImmutableLongInterval prevReported = ImmutableLongInterval.of(prevReportedActiveInterval);
	
	private final IntervalProcessor processor;
	
	private int lastDerivBidPrice = LongInterval.NULL_DATA_VALUE;
	private int lastDerivAskPrice = LongInterval.NULL_DATA_VALUE;
	private long lastDerivPriceNs = Long.MIN_VALUE;
	private int lastDerivSpreadInTick = Integer.MAX_VALUE;
	private int lastNonVerifiedDerivSpreadInTick = Integer.MAX_VALUE;

	private long minObservedUndSpotSinceLastReset = Long.MAX_VALUE;
	private long maxObservedUndSpotSinceLastReset = Long.MIN_VALUE;
	
	private int minDerivAskSinceLastTightDerivPrice = Integer.MAX_VALUE;
	private int maxDerivBidSinceLastTightDerivPrice = Integer.MIN_VALUE;
	
//	private int lastMinVolAdj = -1;
//	private int lastMaxVolAdj = -1;
//	private int minVolAdj = -1;
//	private int maxVolAdj = -1;
	
	// Transient fields for WeightedPrices in python
	private long issuerMaxLagNs;
	private long lastUndSpotProcessedTimeNs;
	
	// Transient fields for BucketPricer in python
	private final PutOrCall putOrCall;
	private final int conversionRatio;
	private final long undSecSid;
	private final long derivSecSid;
	private final String pricerTypeStr;
	private final String derivSecSidStr; 
	
	private final LongInterval[] searchOverlapResults = new LongInterval[64];
	private final Long2LongMap.Entry[] searchOverlapMapEntries = new Long2LongMap.Entry[64];
	private final LongInterval[] overlapResults = new LongInterval[64];
	private final LongInterval smallerSearchResult = LongInterval.of();
	private final LongInterval greaterSearchResult = LongInterval.of();
	private final LongInterval intervalResultByDeriv = LongInterval.of();
	private final BucketSizeInfo outBucketSizeInfo = new BucketSizeInfo();
	private final BucketSizeInfo outBucketSizeInfoForExtrapolation = new BucketSizeInfo();
//	private final BucketSizeInfo outBucketSizeInfoForVolAdj = new BucketSizeInfo();
	private final MutableBoolean outMutableChanged = new MutableBoolean();

	private boolean lastTightDerivPriceAvail;
	private int lastTightDerivBidPrice = LongInterval.NULL_DATA_VALUE;
	private int lastTightDerivAskPrice = LongInterval.NULL_DATA_VALUE;
	private long lastTightDerivPriceNs = Long.MIN_VALUE;
	private long undSpotAtLastTightDerivPrice = Long.MIN_VALUE;
	private long lastUndSpot = Long.MIN_VALUE;
	private boolean shouldRegisterInterval = false;
    private long prevReferredLastDerivPriceNs = -1;
	
	public static class LongTimedPrice {
		long ts;
		long price;
		boolean observedWhenTightSpread;
		
		public long ts(){
			return ts;
		}
		public long price(){
			return price;
		}
		public boolean observedWhenTightSpread(){
			return observedWhenTightSpread;
		}
		public LongTimedPrice ts(long ts){
			this.ts = ts;
			return this;
		}
		public LongTimedPrice price(long value){
			this.price = value;
			return this;
		}
		public LongTimedPrice observedWhenTightSpread(boolean value){
			this.observedWhenTightSpread = value;
			return this;
		}
		public LongTimedPrice set(long ts, long price, boolean isTightSpread){
			this.ts = ts;
			this.price = price;
			this.observedWhenTightSpread = isTightSpread;
			return this;
		}
	}
		
	/**
	 * @deprecated
	 * @param undSecSid
	 * @param derivSecSid
	 * @param putOrCall
	 * @param conversionRatio
	 * @return
	 */
	public static BucketPricer of(long undSecSid, long derivSecSid, PutOrCall putOrCall, SpreadTable derivSpreadTable, int conversionRatio){
		return new BucketPricer(BucketPricer.PRICE_TYPE_WEIGHTED, undSecSid, derivSecSid, putOrCall, derivSpreadTable, conversionRatio, 1024, ObservedGreeks.of(derivSecSid), 1000000, 1200);
	}
	
	/**
	 * @deprecated
	 * @param undSecSid
	 * @param derivSecSid
	 * @param putOrCall
	 * @param conversionRatio
	 * @param discardPeriodNs
	 * @param issuerMaxLagNs
	 * @param bucketSizeAllowance
	 * @return
	 */
	public static BucketPricer of(long undSecSid, long derivSecSid, PutOrCall putOrCall, SpreadTable derivSpreadTable, int conversionRatio, long discardPeriodNs, long issuerMaxLagNs, int deltaAllowance3Dp){
		return new BucketPricer(BucketPricer.PRICE_TYPE_WEIGHTED, undSecSid, derivSecSid, putOrCall, derivSpreadTable, conversionRatio, 1024, ObservedGreeks.of(derivSecSid), issuerMaxLagNs, deltaAllowance3Dp);
	}

	/**
	 * @deprecated
	 * @param undSecSid
	 * @param derivSecSid
	 * @param putOrCall
	 * @param conversionRatio
	 * @param issuerMaxLagNs
	 * @param deltaAllowance3Dp
	 * @return
	 */
	public static BucketPricer of(long undSecSid, long derivSecSid, PutOrCall putOrCall, SpreadTable derivSpreadTable, int conversionRatio, long issuerMaxLagNs, int deltaAllowance3Dp){
		return new BucketPricer(BucketPricer.PRICE_TYPE_WEIGHTED, undSecSid, derivSecSid, putOrCall, derivSpreadTable, conversionRatio, 1024, ObservedGreeks.of(derivSecSid), issuerMaxLagNs, deltaAllowance3Dp);
	}

//	public static BucketPricer of(int pricerType, long undSecSid, long derivSecSid, PutOrCall putOrCall, int conversionRatio){
//		return new BucketPricer(pricerType, undSecSid, derivSecSid, putOrCall, conversionRatio, 1024, ObservedGreeks.of(derivSecSid), 1000000, 1200);
//	}
//
	public static BucketPricer of(int pricerType, long undSecSid, long derivSecSid, PutOrCall putOrCall, SpreadTable derivSpreadTable, int conversionRatio, long issuerMaxLagNs, int deltaAllowance3Dp){
		return new BucketPricer(pricerType, undSecSid, derivSecSid, putOrCall, derivSpreadTable, conversionRatio, 1024, ObservedGreeks.of(derivSecSid), issuerMaxLagNs, deltaAllowance3Dp);
	}
	
	BucketPricer(int pricerType, long undSecSid, long derivSecSid, PutOrCall putOrCall, SpreadTable derivSpreadTable, int conversionRatio, int expectedUndSpots, ObservedGreeks observedGreeks, long issuerMaxLagNs, int deltaAllowance3Dp){
		if (putOrCall == PutOrCall.NULL_VAL){
			throw new IllegalArgumentException("putOrCall must not be NULL_VAL [undSecSid:" + undSecSid + ", derivSecSid:" + derivSecSid);
		}
		this.derivSpreadTable = derivSpreadTable;
		this.pricerTypeStr = String.valueOf(pricerType);
		this.undSecSid = undSecSid;
		this.derivSecSid = derivSecSid;
		this.derivSecSidStr = String.valueOf(derivSecSid);
		this.putOrCall = putOrCall;
		this.observedGreeks = observedGreeks; 
		this.observedUndSpots = ObjectCircularBuffer.of(expectedUndSpots, LongTimedPrice::new);
		this.issuerMaxLagNs = issuerMaxLagNs;
		this.observedIntervals = NonOverlappingLongIntervalTreeSet.of();
		this.observedIntervalsByDerivPrice = new Int2LongOpenHashMap(128);
		
		this.extrapolatedIntervals = NonOverlappingLongIntervalTreeSet.of();
		this.extrapolatedIntervalsByDerivPrice = new Int2LongOpenHashMap(128);
		
		this.extractor = new IntervalExtractor(this.issuerMaxLagNs);
		this.currentPriceValidator = (putOrCall == PutOrCall.CALL) ? 
				CallPricerValidator.of(derivSecSid, conversionRatio, deltaAllowance3Dp, this.derivSpreadTable) :
				PutPricerValidator.of(derivSecSid, conversionRatio, deltaAllowance3Dp, this.derivSpreadTable);
		this.conversionRatio = conversionRatio;
		for (int i = 0; i < this.searchOverlapResults.length; i++){
			this.searchOverlapResults[i] = LongInterval.of();
		}
		for (int i = 0; i < this.overlapResults.length; i++){
			this.overlapResults[i] = LongInterval.of();
		}
		this.processor = new IntervalProcessor(observedGreeks, currentPriceValidator);
//		this.storedObservedIntervalsByTargetSpread = new Int2ObjectOpenHashMap<>();
	}
	
	public BucketPricer issuerMaxLagNs(long issuerMaxLagNs){
		if (this.issuerMaxLagNs != issuerMaxLagNs){
			LOG.debug("Updated issuer max lag [from:{}, to:{}]", this.issuerMaxLagNs, issuerMaxLagNs);
			this.issuerMaxLagNs = issuerMaxLagNs;
			this.extractor.expectedLagNs(issuerMaxLagNs);
		}
		return this;
	}
	
	/**
	 * Observe a derivative tick.  If the observed tick conflicts with the ones we've observed,
	 * we have an option to clear the pricer.
	 * 
	 * No impact on bucket interval  
	 *   
	 * @param nanoOfDay
	 * @param bid3Dp
	 * @param ask3Dp
	 * @param mmBid3Dp
	 * @param mmAsk3Dp
	 * @param mmTightSpread
	 * @param resetOnViolation
	 * @return
	 */
    public ViolationType observeDerivTick(long nanoOfDay, int bid3Dp, int ask3Dp, int mmBid3Dp, int mmAsk3Dp, int spreadInTick, TriggerInfo triggerInfo) {
    	lastTriggerInfo = triggerInfo;
    	// Python
    	// Input
    	// 1) derivPrice
    	// 2) mmDerivPrice
    	// checkPricerViolation(mmDervPrice, lastTightDerivPrice)
    	// 1) Check underlying direction since lastTightDerivPrice
    	// 2) Validate mmDerivPrice to see if it makes sense
    	boolean isDerivTight = (spreadInTick == targetSpreadInTick && spreadInTick != Integer.MAX_VALUE);
    	mmBid3Dp = (mmBid3Dp != 0) ? mmBid3Dp : LongInterval.NULL_DATA_VALUE;
    	mmAsk3Dp = (mmAsk3Dp != 0) ? mmAsk3Dp : LongInterval.NULL_DATA_VALUE;
    	lastNonVerifiedDerivSpreadInTick = spreadInTick;
    	
    	ViolationType violationType = validateDerivPrice(mmBid3Dp, mmAsk3Dp, isDerivTight);
    	if (violationType == ViolationType.NO_VIOLATION){
    		processDerivTick(nanoOfDay, mmBid3Dp, mmAsk3Dp, spreadInTick, isDerivTight);
    	}
//    	else {
//			LOG.info("Violation found [pricer:{}, result:{}]", pricerTypeStr, violationType.name());
//		}
    	return violationType;
    }
    
    private boolean receivedDerivTick = false;
    
    private void processDerivTick(long nanoOfDay, int mmBid3Dp, int mmAsk3Dp, int spreadInTick, boolean isDerivTight){
    	receivedDerivTick = true;
    	if (isDerivTight){
//    		if (!shouldRegisterInterval){
//    			LOG.debug("Set shouldRegisterInterval to true, when derive price [{}/{}, t:{}, targetSpread:{}]", mmBid3Dp, mmAsk3Dp, this.pricerType, targetSpreadInTick);
//    		}    		
    		// If tight spread and L1 BidAsk has changed, we want to clear underlying spots that 
    		// we've captured
    		// BUG: first deriv tick will be cleared by the following line of logic
    		if (!(mmBid3Dp == lastDerivBidPrice && mmAsk3Dp == lastDerivAskPrice && mmBid3Dp != LongInterval.NULL_DATA_VALUE && mmAsk3Dp != LongInterval.NULL_DATA_VALUE && lastDerivBidPrice != LongInterval.NULL_DATA_VALUE && lastDerivAskPrice != LongInterval.NULL_DATA_VALUE)){
        		this.clearObservedUndSpots();
        		this.prevObservedInterval.clear();
        		if (LOG.isTraceEnabled()){
        			LOG.trace("Clear observed underlying spots [t:{}, secSid:{}, seqNum:{}, lastPrice:{}, lastPriceChangedTimeNs:{}]",
        					this.pricerTypeStr,
        					derivSecSidStr,
        					box(lastTriggerInfo.triggerSeqNum()),
        					box(extractor.lastPrice()),
        					box(extractor.lastPriceChangedTimeNs()));
        		}
        	}
    		lastTightDerivBidPrice = mmBid3Dp;
    		lastTightDerivAskPrice = mmAsk3Dp;
    		lastTightDerivPriceAvail = true;
    		lastTightDerivPriceNs = nanoOfDay;
    		undSpotAtLastTightDerivPrice = this.lastUndSpot;
    		shouldRegisterInterval = true;
    	}
    	else {
//    		if (shouldRegisterInterval){
//    			LOG.debug("Set shouldRegisterInterval to false, when derive price [{}/{}, t:{}, targetSpread:{}]", mmBid3Dp, mmAsk3Dp, this.pricerType, targetSpreadInTick);
//    		}
    		shouldRegisterInterval = false;
    	}
    	if (mmBid3Dp != LongInterval.NULL_DATA_VALUE){
    		if (mmBid3Dp > this.maxDerivBidSinceLastTightDerivPrice){
    			this.maxDerivBidSinceLastTightDerivPrice = mmBid3Dp;
    		}
    	}

    	if (mmAsk3Dp != LongInterval.NULL_DATA_VALUE){
    		if (mmAsk3Dp < this.minDerivAskSinceLastTightDerivPrice){
    			this.minDerivAskSinceLastTightDerivPrice = mmAsk3Dp;
    		}
    	}

    	lastDerivBidPrice = mmBid3Dp;
    	lastDerivAskPrice = mmAsk3Dp;
    	lastDerivSpreadInTick = spreadInTick;
    	lastDerivPriceNs = nanoOfDay;
    }
	
    ViolationType observeDerivTickWithoutTriggerInfo(long nanoOfDay, int bid3Dp, int ask3Dp, int mmBid3Dp, int mmAsk3Dp, boolean metTargetSpread) {
    	int spread = derivSpreadTable.priceToTick(mmAsk3Dp) - derivSpreadTable.priceToTick(mmBid3Dp);
    	this.targetSpreadInTick = spread;
    	currentPriceValidator.targetSpreadInTick(spread);
    	return observeDerivTick(nanoOfDay, bid3Dp, ask3Dp, mmBid3Dp, mmAsk3Dp, spread, lastTriggerInfo);
    }
    
    void processDerivTickWithViolationCheck(long nanoOfDay, int mmBid3Dp, int mmAsk3Dp){
    	int spreadInTick = derivSpreadTable.priceToTick(mmAsk3Dp) - derivSpreadTable.priceToTick(mmBid3Dp);
    	processDerivTick(nanoOfDay, mmBid3Dp, mmAsk3Dp, spreadInTick, (spreadInTick == targetSpreadInTick && spreadInTick != Integer.MAX_VALUE));
    }
    
    ViolationType observeUndTickWithoutTriggerInfo(long nanoOfDay, long undSpot6Dp) {
    	return observeUndTick(nanoOfDay, undSpot6Dp, true, lastTriggerInfo, temp);
    }
    
    private final LongInterval temp = LongInterval.of(); 
    public ViolationType observeUndTick(long nanoOfDay, long undSpot6Dp, boolean isTightSpread, TriggerInfo triggerInfo) {
    	return observeUndTick(nanoOfDay, undSpot6Dp, isTightSpread, triggerInfo, temp);
    }
        
	/**
	 * We can use orderBook.transactTime or orderBook.triggeredInfo().nanoTime()
	 * 
	 * Side effect on bucket interval
	 * 
	 * @param nanoOfDay
	 * @param orderBook
	 * @return
	 * @throws Exception 
	 */
	public ViolationType observeUndTick(long nanoOfDay, long undSpot6Dp, boolean isTightSpread, TriggerInfo triggerInfo, LongInterval outInterval) {
		lastTriggerInfo = triggerInfo;
		outInterval.clear();
		// TODO In python, we take tick only when lastDerivPrice is available, should we do this here?
		if (undSpot6Dp > MAX_INTERVAL_VALUE){
			throw new IllegalArgumentException("Input und spot exceeds maximum [undSpot: " + undSpot6Dp + ", max: " + MAX_INTERVAL_VALUE + "]");
		}
		
		try {
//			LOG.info("observeUndTick: [ts:{}, undSpot6Dp:{}]", nanoOfDay, undSpot6Dp);
			lastUndSpot = undSpot6Dp;
//			LOG.info("observeUndTick: first: Set lastUndSpot [{}]", lastUndSpot);
			observedUndSpots.claim().set(nanoOfDay, undSpot6Dp, isTightSpread);
			minObservedUndSpotSinceLastReset = Math.min(undSpot6Dp, minObservedUndSpotSinceLastReset);
			maxObservedUndSpotSinceLastReset = Math.max(undSpot6Dp, maxObservedUndSpotSinceLastReset);
			
			ViolationType result = ViolationType.NO_VIOLATION; 
			// observe: 100, 102, 104, 105
			// unprocessedMin: 100
			// unprocessedMax: 105
			if (prevReferredLastDerivPriceNs != this.lastDerivPriceNs || nanoOfDay >=  this.extractor.recordedEarliestEffectiveTimeNs() || (nanoOfDay >= (this.extractor.lastPriceChangedTimeNs() + this.issuerMaxLagNs))){
				ImmutableLongInterval interval = processObservedUndSpots(nanoOfDay, this.lastDerivBidPrice, this.lastDerivPriceNs);
				//LOG.debug("[t:{}, lastPrice:{}, lastPriceChangedTimeNs:{}, extractedInterval:{}]", this.pricerType, this.extractor.lastPrice(), this.extractor.lastPriceChangedTimeNs(), interval.toString());

				prevReferredLastDerivPriceNs = this.lastDerivPriceNs;
				long begin = interval.begin();
				long end = interval.endExclusive();
				result = currentPriceValidator.validateDerivAskPrice(observedGreeks, begin, end, lastDerivAskPrice, this.observedIntervals);

				// If register one point, [500, 501)
				// When registering a new interval and there is a bucket size violation, how do we decide whether it is a down vol or up vol?
				// It really depends on the path to 'last' point
				// [500, 501) - no bucket size violation (doesn't matter)
				// [500, 601) (path: 500 -> 600) - vol down
				// [500, 601) (path: 500 -> 600 -> 500) - there is no actually change.  
				//  	Originally, this will be detected as bucket_size violation.
				// 		Technically, this is a down vol then up vol within issuer lag.
				int data = interval.data();
				outInterval.setIntervalAndLast(begin, end, shouldRegisterInterval ? data : LongInterval.NULL_DATA_VALUE, interval.last());
				if (shouldRegisterInterval && result == ViolationType.NO_VIOLATION && interval.hasValidRange() && !interval.equalsToInterval(prevObservedInterval)){
					result = registerUndSpotInterval(nanoOfDay, begin, end, data, interval.last() > begin, outInterval, outMutableChanged);
					if (result == ViolationType.NO_VIOLATION && 
							outMutableChanged.booleanValue() &&
							(computeTheoIntervals(observedGreeks, conversionRatio, data, extrapolatedIntervals, extrapolatedIntervalsByDerivPrice) == ViolationType.NO_VIOLATION &&
							!extrapolatedIntervalsByDerivPrice.isEmpty())){
						
						// Swap
						NonOverlappingLongIntervalTreeSet tempInterval = observedIntervals;
						Int2LongOpenHashMap tempTree = observedIntervalsByDerivPrice;
						
						observedIntervals = extrapolatedIntervals;
						observedIntervalsByDerivPrice = extrapolatedIntervalsByDerivPrice;
						
						extrapolatedIntervals = tempInterval;
						extrapolatedIntervalsByDerivPrice = tempTree;
	        			LOG.debug("Use extrapolated intervals [t:{}, secSid:{}, seqNum:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
	        					this.pricerTypeStr,
	        					derivSecSidStr, 
	        					box(lastTriggerInfo.triggerSeqNum()),
	        					box(data),
	    		                box(this.observedGreeks.delta()),
	    		                box(this.observedGreeks.gamma()),
	    		                box(this.observedGreeks.refSpot()),
	        					this.observedIntervals.toString());
					}
					prevObservedInterval.copyFrom(interval);
				}
			}
 			return result;
		}
		catch (BufferOverflowException e){
			LOG.error("Caught buffer overflown error when handling [ts:{}]", nanoOfDay, e);
			return ViolationType.ERROR;
		}
		catch (Exception e){
			LOG.error("Caught exception when handling [ts:{}]", nanoOfDay, e);
			return ViolationType.ERROR;
		}
	}
    
	@SuppressWarnings("unused")
	static class BucketSizeInfo {
		private static final int NUM_DP_ADJ_DELTA = 5;
		private static final int NUM_DP_CURRENT_BUCKET_SIZE = 6;
		private static final int NUM_DP_MAX_BUCKET_SIZE = 6;
		private static final int NUM_DP_ADJ_MAX_BUCKET_SIZE = 6;
		
		private long adjDelta;
		private long currentBucketSize;
		private long maxBucketSize;
		private long adjMaxBucketSize;
		
		public long adjDelta(){
			return adjDelta;
		}
		
		public long currentBucketSize(){
			return currentBucketSize;
		}
		
		public long maxBucketSize(){
			return maxBucketSize;
		}
		
		public long adjMaxBucketSize(){
			return adjMaxBucketSize;
		}
		
		public BucketSizeInfo adjDelta(long value){
			this.adjDelta = value;
			return this;
		}
		
		public BucketSizeInfo currentBucketSize(long value){
			currentBucketSize = value;
			return this;
		}
		
		public BucketSizeInfo maxBucketSize(long value){
			maxBucketSize = value;
			return this;
		}
		
		public BucketSizeInfo adjMaxBucketSize(long value){
			adjMaxBucketSize = value;
			return this;
		}

		@Override
		public String toString() {
			return "adjDelta: " + adjDelta + ", currentBucketSize: " + currentBucketSize + ", maxBucketSize: " + maxBucketSize + ", adjMaxBucketSize: " + adjMaxBucketSize;
		}
		
		public void clear(){
			adjDelta = 0;
			currentBucketSize = 0;
			maxBucketSize = 0;
			adjMaxBucketSize = 0;
		}
	}

	public boolean observeGreeks(long nanoOfDay, Greeks greeks){
		this.observedGreeks.merge(nanoOfDay, greeks);
//		LOG.info("Current greeks: [refSpot:{}, delta:{}, gamma:{}]", this.observedGreeks.refSpot(), this.observedGreeks.delta(), this.observedGreeks.gamma());
		return true;
	}
	
	private ViolationType registerUndSpotInterval(long observedTs, long undSpotIntBegin, long undSpotIntEndExcl, int undSpotIntData, boolean createIntervalByExtendingEnd, LongInterval outInterval, MutableBoolean outChanged){
		outChanged.setValue(false);
		ViolationType violationType = currentPriceValidator.validateBucketSize(this.observedGreeks, undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, createIntervalByExtendingEnd, outBucketSizeInfo);
		/* begin, end, data have already been set previously!!! */
		outInterval.theoBucketSize(outBucketSizeInfo.maxBucketSize());
		if (violationType != ViolationType.NO_VIOLATION){
			return violationType;
		}
		return registerUndSpotInterval(observedTs, undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, outBucketSizeInfo, true, outChanged);
	}

	/**
	 * Register a mapping of [ interval ] <-> [ price ] into this pricer
	 * @param observedTs
	 * @param undSpotInterval
	 * @return
	 */
	private ViolationType registerUndSpotInterval(long observedTs, long undSpotIntBegin, long undSpotIntEndExcl, int undSpotIntData, boolean createIntervalByExtendingEnd, boolean validateBucketDistance, MutableBoolean outChanged){
		ViolationType violationType = currentPriceValidator.validateBucketSize(this.observedGreeks, undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, createIntervalByExtendingEnd, outBucketSizeInfo);
		if (violationType != ViolationType.NO_VIOLATION){
			return violationType;
		}
		return registerUndSpotInterval(observedTs, undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, outBucketSizeInfo, validateBucketDistance, outChanged);
	}
	
	private ViolationType registerUndSpotInterval(long observedTs, 
			long undSpotIntBegin, 
			long undSpotIntEndExcl, 
			int undSpotIntData, 
			BucketSizeInfo bucketSizeInfo,
			boolean validateBucketDistance,
			MutableBoolean outChanged){
		int numOverlaps = observedIntervals.search(undSpotIntBegin, 
				undSpotIntEndExcl,
				smallerSearchResult,
				greaterSearchResult,
				searchOverlapResults,
				searchOverlapMapEntries);
		
		// For each new interval, we want to do the following:
		// 1) Check consistency against the smaller non-overlapping interval
		// 2) Check consistency against the greater non-overlapping interval
		// 3) Replace all overlapping intervals with this new interval if applicable
		// If any one of these fails, return failure
		ViolationType violationType = ViolationType.NO_VIOLATION;
		if (numOverlaps == 0){
		    // Search to see if this interval already exists
		    long interval = observedIntervalsByDerivPrice.get(undSpotIntData);
		    if (interval == observedIntervalsByDerivPrice.defaultReturnValue()){

		        // Validate price consistency and bucket distance against smaller non-overlapping interval
		        if (!smallerSearchResult.isEmpty()){
		            violationType = currentPriceValidator.validatePriceConsistency(undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, bucketSizeInfo.maxBucketSize(), smallerSearchResult);
		            if (violationType != ViolationType.NO_VIOLATION){
		                return violationType;
		            }
		        }

		        // Validate price consistency and bucket distance against greater non-overlapping interval
		        if (!greaterSearchResult.isEmpty()){
		            violationType = currentPriceValidator.validatePriceConsistency(undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, bucketSizeInfo.maxBucketSize(), greaterSearchResult);
		            if (violationType != ViolationType.NO_VIOLATION){
		                return violationType;
		            }
		        }
		        
		        // Also checks against the first and last interval in the tree
		        // [5500, 5600)
		        // [5300, 5350) checks against [5500, 5600)
		        // [5200, 5230) checks against [5500, 5600)
		        // TODO: choose the widest interval to check against
		        if (validateBucketDistance){
		        	int intervals = observedIntervals.intervals(overlapResults);
		        	for (int i = 0; i < intervals; i++){
		        		ViolationType distanceResult = currentPriceValidator.validateBucketDistance(undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, bucketSizeInfo.maxBucketSize(), overlapResults[i]);
		        		if (distanceResult != ViolationType.NO_VIOLATION){
		        			return distanceResult;
		        		}
		        	}
		        }

		        // Add interval
		        observedIntervals.addWithNoOverlapCheck(undSpotIntBegin, undSpotIntEndExcl, undSpotIntData, bucketSizeInfo.maxBucketSize());
		        LOG.debug("Added interval into pricer [t:{}, secSid:{}, seqNum:{}, observedNs:{}, begin:{}, end:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
		        		this.pricerTypeStr,
		                derivSecSidStr,
		                box(lastTriggerInfo.triggerSeqNum()),
		                box(observedTs), 
		                box(undSpotIntBegin), 
		                box(undSpotIntEndExcl), 
		                box(undSpotIntData),
		                box(this.observedGreeks.delta()),
		                box(this.observedGreeks.gamma()),
		                box(this.observedGreeks.refSpot()),
		                this.observedIntervals.toString());

		        long value = (undSpotIntBegin << 32 | undSpotIntEndExcl);
		        observedIntervalsByDerivPrice.put(undSpotIntData, value);
		        outChanged.setValue(true);
		        return ViolationType.NO_VIOLATION;
		    }
		    else {
		        long overlappedBegin = (interval & HIGHER_ORDER_MASK) >>> 32;
		        long overlappedEndExcl = (interval & LOWER_ORDER_MASK);
		        
		        if (LOG.isTraceEnabled()){
			        LOG.trace("Search for actual interval with same data point: {}, size of derivPriceTree:{}, overlappedBegin:{}, overlappedEndExcl:{}] Intervals: {}", 
			        		undSpotIntData,
			                this.observedIntervalsByDerivPrice.size(),
			                overlappedBegin,
			                overlappedEndExcl,
			                observedIntervals.toString());
		        }
		        
		        return mergeIntervalWithValidation((interval & NonOverlappingLongIntervalTreeSet.HIGHER_ORDER_MASK) >>> 32, 
		                overlappedEndExcl,
		                observedTs, 
		                undSpotIntBegin, 
		                undSpotIntEndExcl, 
		                undSpotIntData, 
		                bucketSizeInfo.maxBucketSize(),
		                bucketSizeInfo.adjMaxBucketSize(),
		                outChanged);
		    }
		}
		else if (numOverlaps == 1){
			LongInterval overlappedInterval = searchOverlapResults[0];
			if (undSpotIntData > overlappedInterval.data()){
				return ViolationType.UP_VOL;
			}
			else if (undSpotIntData < overlappedInterval.data()){
				return ViolationType.DOWN_VOL;
			}
			
			// Merge with overlapping interval with underlying spot interval
			// 1) If same as existing interval, do nothing
			// 2) If same as input, no need to do any checking again
			// 3) Remove overlaps and insert the new one (need to do bucketSize check again)
			return mergeInterval(overlappedInterval.begin(), 
			        overlappedInterval.endExclusive(),
			        searchOverlapMapEntries[0],
			        observedTs, 
			        undSpotIntBegin, 
			        undSpotIntEndExcl, 
			        undSpotIntData, 
			        bucketSizeInfo.maxBucketSize(),
			        bucketSizeInfo.adjMaxBucketSize(),
			        outChanged);
		}
		else {
			return validateOverlappedIntervals(searchOverlapResults, numOverlaps, undSpotIntData);
		}
	}
	
	private ViolationType validateOverlappedIntervals(LongInterval[] searchResults, int numResults, int refData){
		boolean foundLower = false;
		boolean foundHigher = false;
		int rightMostData = searchOverlapResults[numResults - 1].data();
		int leftMostData = searchOverlapResults[0].data();
		if (refData < rightMostData){
			foundLower = true;
		}
		else if (refData > rightMostData){
			foundHigher = true;
		}
		if (refData < leftMostData){
			foundLower = true;
		}
		else if (refData > leftMostData){
			foundHigher = true;
		}
		if (foundLower){
			if (!foundHigher){
				return ViolationType.DOWN_VOL;
			}
			return ViolationType.PRICE_OVERLAPPED;
		}
		if (foundHigher){
			return ViolationType.UP_VOL;
		}
		LOG.error("Impossible");
		return ViolationType.ERROR;
	}
	
	private ViolationType mergeIntervalWithValidation(long overlappedIntervalBegin, 
	        long overlappedIntervalEndExcl,
	        long observedTs, 
	        long newIntervalBegin, 
	        long newIntervalEndExcl, 
	        int data, 
	        long theoBucketSize,
	        long theoBucketSizeWithAllowance,
	        MutableBoolean outChanged){
//		LOG.debug("Merge interval [fromBegin:{}, fromEnd:{}, overlapBegin:{}, overlapEnd:{}]", 
//				newIntervalBegin, 
//				newIntervalEndExcl,
//				overlappedIntervalBegin,
//				overlappedIntervalEndExcl);
        if (newIntervalEndExcl <= overlappedIntervalEndExcl){
            // Change in begin, we need to set new bucket size
            if (newIntervalBegin < overlappedIntervalBegin){
            	// At this point, we are not sure if newIntervalBegin overlaps with other interval
        		int numOverlaps = observedIntervals.search(newIntervalBegin, overlappedIntervalEndExcl, smallerSearchResult, greaterSearchResult, searchOverlapResults, searchOverlapMapEntries);
        		if (numOverlaps == 1){            		
    		        int intervals = observedIntervals.intervals(overlapResults);
    		        for (int i = 0; i < intervals; i++){
    		        	if (overlapResults[i].data() != data){
    		        		ViolationType distanceResult = currentPriceValidator.validateBucketDistance(newIntervalBegin, overlappedIntervalEndExcl, data, theoBucketSize, overlapResults[i]);
    		        		if (distanceResult != ViolationType.NO_VIOLATION){
    		        			return distanceResult;
    		        		}
    		        	}
    		        }
        			
                	// Changed interval [newIntervalBegin, overlappedIntervalEndExcl]
                	// Requires validation against bucket size
            		if (overlappedIntervalEndExcl - newIntervalBegin > theoBucketSizeWithAllowance){
            			// @TODO Put this logic in PricerValildator
            			// Extending begin
            			if (this.putOrCall == PutOrCall.CALL){
            				return ViolationType.UP_VOL;
            			}
            			else {
            				return ViolationType.DOWN_VOL;
            			}
//            			LOG.error("Bucket too big when merging interval in pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, overlapBegin:{}, overlapEnd:{}, newBegin:{}, newEnd:{}, targetBegin:{}, targetEnd:{}, data:{}, theoBucketSizeWithAllowance:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
//            					this.pricerTypeStr,
//            					derivSecSidStr, 
//            					box(lastTriggerInfo.triggerSeqNum()),
//            					box(observedTs), 
//            					box(overlappedIntervalBegin),
//            					box(overlappedIntervalEndExcl),
//            					box(newIntervalBegin), 
//            					box(newIntervalEndExcl), 
//            					box(newIntervalBegin), 
//            					box(overlappedIntervalEndExcl), 
//            					box(data),
//            					box(theoBucketSizeWithAllowance),
//        		                box(this.observedGreeks.delta()),
//        		                box(this.observedGreeks.gamma()),
//        		                box(this.observedGreeks.refSpot()),
//            					this.observedIntervals.toString());
//            			return ViolationType.BUCKET_TOO_BIG;
            		}
            		
        			long entryValue = NonOverlappingLongIntervalTreeSet.createValue(newIntervalBegin, theoBucketSize);
        			searchOverlapMapEntries[0].setValue(entryValue);
        			observedIntervalsByDerivPrice.put(data, (newIntervalBegin << 32 | overlappedIntervalEndExcl));
        			LOG.debug("Changed interval in pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
        					this.pricerTypeStr,
        					derivSecSidStr, 
        					box(lastTriggerInfo.triggerSeqNum()),
        					box(observedTs), 
        					box(overlappedIntervalBegin),
        					box(overlappedIntervalEndExcl),
        					box(newIntervalBegin), 
        					box(overlappedIntervalEndExcl), 
        					box(data),
    		                box(this.observedGreeks.delta()),
    		                box(this.observedGreeks.gamma()),
    		                box(this.observedGreeks.refSpot()),
        					this.observedIntervals.toString());
        			outChanged.setValue(true);
        		}
        		else {
        			assert numOverlaps != 0;
        			// Something is wrong, newIntervalBegin is mapped to another interval
        			LOG.debug("Wanted to change interval in pricer but detects UP_VOL [t:{}, secSid:{}, seqNum:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
        					this.pricerTypeStr,
        					derivSecSidStr, 
        					box(lastTriggerInfo.triggerSeqNum()),
        					box(observedTs), 
        					box(overlappedIntervalBegin),
        					box(overlappedIntervalEndExcl),
        					box(newIntervalBegin), 
        					box(overlappedIntervalEndExcl), 
        					box(data),
    		                box(this.observedGreeks.delta()),
    		                box(this.observedGreeks.gamma()),
    		                box(this.observedGreeks.refSpot()),
        					this.observedIntervals.toString());
        			return validateOverlappedIntervals(searchOverlapResults, numOverlaps, data);
        		}
            }
            return ViolationType.NO_VIOLATION;
        }
        else {
            if (newIntervalBegin < overlappedIntervalBegin){
            	// At this point:
            	// newIntervalBegin < overlappedIntervalBegin
            	// newIntervalEndExcl > overlappedIntervalEndExcl
            	// Use: [newIntervalBegin, newIntervalEndExcl] 
            	// No need to check bucket size
            	int numOverlaps = observedIntervals.search(newIntervalBegin, newIntervalEndExcl, smallerSearchResult, greaterSearchResult, searchOverlapResults, searchOverlapMapEntries);
            	if (numOverlaps == 1){
                    observedIntervals.remove(overlappedIntervalEndExcl);

                    // Replacing existing interval with [undSpotIntBegin, undSpotIntEndExclusive), 
                    // no need to go thru any validation again
                    observedIntervals.addWithNoOverlapCheck(newIntervalBegin, newIntervalEndExcl, data, theoBucketSize);
                    
                    observedIntervalsByDerivPrice.put(data, (newIntervalBegin << 32 | newIntervalEndExcl));
                    
                    LOG.debug("Extended existing interval in pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
                    		this.pricerTypeStr,
                            derivSecSidStr, 
                            box(lastTriggerInfo.triggerSeqNum()),
                            box(observedTs), 
                            box(overlappedIntervalBegin),
                            box(overlappedIntervalEndExcl),
                            box(newIntervalBegin), 
                            box(newIntervalEndExcl), 
                            box(data),
    		                box(this.observedGreeks.delta()),
    		                box(this.observedGreeks.gamma()),
    		                box(this.observedGreeks.refSpot()),
                            this.observedIntervals.toString());
                    outChanged.setValue(true);
                    return ViolationType.NO_VIOLATION;                  
            	}
            	else {
            		assert numOverlaps != 0;
            		ViolationType result = validateOverlappedIntervals(searchOverlapResults, numOverlaps, data);
            		
            		// Something is wrong, newIntervalBegin is mapped to another interval
            		LOG.debug("Wanted to change interval in pricer but detects change in vol [t:{}, secSid:{}, seqNum:{}, result:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
            				this.pricerTypeStr,
                            derivSecSidStr, 
                            box(lastTriggerInfo.triggerSeqNum()),
                            result,
                            box(observedTs), 
                            box(overlappedIntervalBegin),
                            box(overlappedIntervalEndExcl),
                            box(newIntervalBegin), 
                            box(overlappedIntervalEndExcl), 
                            box(data),
    		                box(this.observedGreeks.delta()),
    		                box(this.observedGreeks.gamma()),
    		                box(this.observedGreeks.refSpot()),
                            this.observedIntervals.toString());
            		return result;
            	}
            }
            else {
            	// At this point:
            	// newIntervalBegin > overlappedIntervalBegin
            	// newIntervalEndExcl > overlappedIntervalEndExcl
            	// Use: [overlappedIntervalBegin, newIntervalEndExcl] 
            	// Bucket size check will be performed in registerUndSpotInterval
            	int numOverlaps = observedIntervals.search(overlappedIntervalBegin, newIntervalEndExcl, smallerSearchResult, greaterSearchResult, searchOverlapResults, searchOverlapMapEntries);
            	if (numOverlaps == 1){
                	observedIntervals.remove(overlappedIntervalEndExcl);
                	observedIntervalsByDerivPrice.remove(data);
                	outChanged.setValue(true);
                    LOG.debug("Removed interval from pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, begin:{}, end:{}, data:{}]",
                    		this.pricerTypeStr,
                    		derivSecSidStr,
                    		box(lastTriggerInfo.triggerSeqNum()),
                    		box(observedTs), 
                            box(overlappedIntervalBegin), 
                            box(overlappedIntervalEndExcl), box(data));
                    return registerUndSpotInterval(observedTs, 
                            overlappedIntervalBegin, 
                            newIntervalEndExcl,
                            data,
                            true, // We are extending the end of an interval
                            true,
                            outChanged);
            	}
            	else {
            		assert numOverlaps != 0;
            		ViolationType result = validateOverlappedIntervals(searchOverlapResults, numOverlaps, data);
            		
            		// Something is wrong, newIntervalBegin is mapped to another interval
            		LOG.debug("Wanted to change interval in pricer but detects change in vol [t:{}, secSid:{}, seqNum:{}, result:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
            				this.pricerTypeStr,
                            derivSecSidStr, 
                            box(lastTriggerInfo.triggerSeqNum()),
                            result,
                            box(observedTs), 
                            box(overlappedIntervalBegin),
                            box(overlappedIntervalEndExcl),
                            box(newIntervalBegin), 
                            box(overlappedIntervalEndExcl), 
                            box(data),
    		                box(this.observedGreeks.delta()),
    		                box(this.observedGreeks.gamma()),
    		                box(this.observedGreeks.refSpot()),
                            this.observedIntervals.toString());
            		return result;
            	}
            }
        }	    
	}
	
	private ViolationType mergeInterval(long overlappedIntervalBegin, 
	        long overlappedIntervalEndExcl,
	        Long2LongMap.Entry entry,
	        long observedTs, 
	        long newIntervalBegin, 
	        long newIntervalEndExcl, 
	        int data, 
	        long theoBucketSize,
	        long theoBucketSizeWithAllowance,
	        MutableBoolean outChanged){
		
		// overlappedIntervalBegin and overlappedIntervalEndExcl have been checked
		// newIntervalBegin and newIntervalEndExcl have been checked
        if (newIntervalEndExcl <= overlappedIntervalEndExcl){
            // Change in begin, we need to set new bucket size
        	// Also, bucketSize might have violated
            if (newIntervalBegin < overlappedIntervalBegin){
            	// Changed interval [newIntervalBegin, overlappedIntervalEndExcl]
            	// Requires validation against bucket size
            	if (overlappedIntervalEndExcl - newIntervalBegin <= theoBucketSizeWithAllowance){ 
            		
                    int intervals = observedIntervals.intervals(overlapResults);
                    for (int i = 0; i < intervals; i++){
                    	if (overlapResults[i].data() != data){
                    		ViolationType distanceResult = currentPriceValidator.validateBucketDistance(newIntervalBegin, overlappedIntervalEndExcl, data, theoBucketSize, overlapResults[i]);
                    		if (distanceResult != ViolationType.NO_VIOLATION){
                    			return distanceResult;
                    		}
                    	}
                    }
            		
            		long entryValue = NonOverlappingLongIntervalTreeSet.createValue(newIntervalBegin, theoBucketSize);
            		entry.setValue(entryValue);
            		observedIntervalsByDerivPrice.put(data, (newIntervalBegin << 32 | overlappedIntervalEndExcl));
            		outChanged.setValue(true);
            		LOG.debug("Changed interval in pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
            				this.pricerTypeStr,
            				derivSecSidStr, 
            				box(lastTriggerInfo.triggerSeqNum()),
            				box(observedTs), 
            				box(overlappedIntervalBegin),
            				box(overlappedIntervalEndExcl),
            				box(newIntervalBegin), 
            				box(overlappedIntervalEndExcl), 
            				box(data),
    		                box(this.observedGreeks.delta()),
    		                box(this.observedGreeks.gamma()),
    		                box(this.observedGreeks.refSpot()),
            				this.observedIntervals.toString());
            	}
            	else {
            		if (this.putOrCall == PutOrCall.CALL){
            			return ViolationType.UP_VOL;
            		}
            		else {
            			return ViolationType.DOWN_VOL;
            		}
//            		LOG.error("Bucket too big detected when merging interval in pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, overlapBegin:{}, overlapEnd:{}, newBegin:{}, newEnd:{}, targetBegin:{}, targetEnd:{}, data:{}, theoBucketSize:{}, theoBucketSizeWithAllowance:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
//            				this.pricerTypeStr,
//            				derivSecSidStr, 
//            				box(lastTriggerInfo.triggerSeqNum()),
//            				box(observedTs), 
//            				box(overlappedIntervalBegin),
//            				box(overlappedIntervalEndExcl),
//            				box(newIntervalBegin), 
//            				box(newIntervalEndExcl), 
//            				box(newIntervalBegin), 
//            				box(overlappedIntervalEndExcl), 
//            				box(data),
//            				box(theoBucketSize),
//            				box(theoBucketSizeWithAllowance),
//    		                box(this.observedGreeks.delta()),
//    		                box(this.observedGreeks.gamma()),
//    		                box(this.observedGreeks.refSpot()),
//            				this.observedIntervals.toString());
//            		return ViolationType.BUCKET_TOO_BIG;
            	}
            }
            return ViolationType.NO_VIOLATION;
        }
        else {
        	// newIntervalEndExcl > overlappedIntervalEndExcl
        	
            observedIntervals.remove(overlappedIntervalEndExcl);
            observedIntervalsByDerivPrice.remove(data);

            if (newIntervalBegin <= overlappedIntervalBegin){
            	// Changed interval [newIntervalBegin, newIntervalEndExcl]
            	// Requires no validation against bucket size
            	
                int intervals = observedIntervals.intervals(overlapResults);
                for (int i = 0; i < intervals; i++){
                	if (overlapResults[i].data() != data){
                		ViolationType distanceResult = currentPriceValidator.validateBucketDistance(newIntervalBegin, newIntervalEndExcl, data, theoBucketSize, overlapResults[i]);
                		if (distanceResult != ViolationType.NO_VIOLATION){
                			return distanceResult;
                		}
                	}
                }
            	
            	// Replacing existing interval with [undSpotIntBegin, undSpotIntEndExclusive), 
                // no need to go thru any validation again, because we have already gone thru that in the parent method
                observedIntervals.addWithNoOverlapCheck(newIntervalBegin, newIntervalEndExcl, data, theoBucketSize);
                observedIntervalsByDerivPrice.put(data, (newIntervalBegin << 32 | newIntervalEndExcl));
                outChanged.setValue(true);
                LOG.debug("Extended existing interval in pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, fromBegin:{}, fromEnd:{}, toBegin:{}, toEnd:{}, data:{}, delta:{}, gamma:{}, refSpot:{}] Intervals: {}",
                		this.pricerTypeStr,
                        derivSecSidStr, 
                        box(lastTriggerInfo.triggerSeqNum()),
                        box(observedTs), 
                        box(overlappedIntervalBegin),
                        box(overlappedIntervalEndExcl),
                        box(newIntervalBegin), 
                        box(newIntervalEndExcl), 
                        box(data),
		                box(this.observedGreeks.delta()),
		                box(this.observedGreeks.gamma()),
		                box(this.observedGreeks.refSpot()),
                        this.observedIntervals.toString());
                return ViolationType.NO_VIOLATION;                  
            }
            else {
            	// Changed interval [overlappedIntervalBegin, newIntervalEndExcl]
            	// Requires no validation against bucket size because we are going to do that in registerUndSpot

            	// Replace with interval [overlappedIntervalBegin, undSpotIntEndExclusive)
            	// Actually, nothing needs to be done here as well
            	// Need to go thru bucket size check again
            	// TODO: Please think thru this
                LOG.debug("Removed interval from pricer [t:{}, secSid:{}, seqNum:{}, observedTs:{}, begin:{}, end:{}, data:{}]",
                		this.pricerTypeStr,
                		derivSecSidStr,
                		box(lastTriggerInfo.triggerSeqNum()),
                		box(observedTs), 
                        box(overlappedIntervalBegin), 
                        box(overlappedIntervalEndExcl), box(data));
                return registerUndSpotInterval(observedTs, 
                        overlappedIntervalBegin, 
                        newIntervalEndExcl, 
                        data,
                        true, // We are extending the end of an interval
                        true,
                        outChanged);                    
            }
        }	    
	}
	
	public boolean getIntervalByUndSpot(long undSpot, LongInterval outInterval){
		return this.observedIntervals.search(undSpot, outInterval);
	}
	
	public boolean getOverlapAndGreaterIntervalByUndSpot(long undSpot, LongInterval outInterval, LongInterval outGreaterInterval){
		return this.observedIntervals.searchOverlapAndGreater(undSpot, outInterval, outGreaterInterval);
	}

	public boolean getOverlapAndSmallerIntervalByUndSpot(long undSpot, LongInterval outInterval, LongInterval outGreaterInterval){
		return this.observedIntervals.searchOverlapAndSmaller(undSpot, outInterval, outGreaterInterval);
	}

	/**
	 * Please note that outInterval.dataInTick() and outInterval.theoBucketSize() are not available.
	 * If these fields are really needed, we can get it from observedIntervals
	 * 
	 * @param derivPrice
	 * @param outInterval Note dataInTick and theoBucketSize won't be available.  If you need these, please talk to me.
	 * @return
	 */
	public boolean getIntervalByDerivPrice(int derivPrice, LongInterval outInterval) {
		long value = this.observedIntervalsByDerivPrice.get(derivPrice);
		if (value != this.observedIntervalsByDerivPrice.defaultReturnValue()){
			outInterval.set((value & HIGHER_ORDER_MASK) >>> 32, 
					value & LOWER_ORDER_MASK, 
					derivPrice, 
					-1);
			return true;
		}
	    return false;
	}
	
	private boolean extrapolateBaseOnOnePrice(int derivPrice, LongInterval outInterval){
		int refDerivPrice = LongInterval.NULL_DATA_VALUE;
		long value = LongInterval.NULL_INTERVAL_BEGIN_VALUE;
		int currentMinDistance = Integer.MAX_VALUE;
		for (Entry entry : this.observedIntervalsByDerivPrice.int2LongEntrySet()){
			int min = Math.abs(entry.getIntKey() - derivPrice);
			if (min < currentMinDistance){
				refDerivPrice = entry.getIntKey();	
				currentMinDistance = min;
				value = entry.getLongValue();
			}
			else if (min == currentMinDistance && entry.getIntKey() < refDerivPrice){
				refDerivPrice = entry.getIntKey();
				value = entry.getLongValue();

				// If distance is the same, we want to pick the interval which has the longest bucket
			}
		}
		if (value != LongInterval.NULL_INTERVAL_BEGIN_VALUE){
			long refBegin =  (value & HIGHER_ORDER_MASK) >>> 32;
			long refEndExclusive = (value & LOWER_ORDER_MASK);
			long undSpotChange = currentPriceValidator.calculateUndSpotChange(refBegin, refEndExclusive, refDerivPrice, derivPrice, observedGreeks);

			//			outInterval.set(refBegin + undSpotChange, refEndExclusive + undSpotChange, derivPrice);
			//			LOG.debug("Extrapolated interval [{}, refAgainst:{}]", outInterval, refDerivPrice);
			//			return true;

			if (undSpotChange != PricerValidator.INVALID_UND_SPOT_CHANGE){
				outInterval.set(refBegin + undSpotChange, refEndExclusive + undSpotChange, derivPrice);
				LOG.debug("Extrapolated interval [t:{}, secSid:{}, {}, refAgainst:{}]", this.pricerTypeStr, this.derivSecSidStr, outInterval, refDerivPrice);
				return true;
			}
		}
		return false;		
	}
	
	private boolean extrapolateBaseOnMultiplePrices(int derivPrice, LongInterval outInterval){
		// BT matching code - can be ugly
		int[] refDerivPrices = new int[2];
		long[] refValues = new long[2];
		int offset = 0;
		int currentMinDistance = Integer.MAX_VALUE;
		
		for (Entry entry : this.observedIntervalsByDerivPrice.int2LongEntrySet()){
			int min = Math.abs(entry.getIntKey() - derivPrice);
			if (min < currentMinDistance){
				offset = 0;
				currentMinDistance = min;
				refDerivPrices[offset] = entry.getIntKey();
				refValues[offset] = entry.getLongValue();
				
				// Clear refDerivPrices
				offset++;
			}
			else if (min == currentMinDistance){
				refDerivPrices[offset] = entry.getIntKey();
				refValues[offset] = entry.getLongValue();
				offset++;
				// If distance is the same, we want to pick the interval which has the longest bucket
			}
		}

		long minBegin = Long.MAX_VALUE;
		long maxEnd = Long.MIN_VALUE;
		for (int i = 0; i < offset; i++){
			long value = refValues[i];
			if (value != LongInterval.NULL_INTERVAL_BEGIN_VALUE){
				int refDerivPrice = refDerivPrices[i];
				long refBegin =  (value & HIGHER_ORDER_MASK) >>> 32;
				long refEndExclusive = (value & LOWER_ORDER_MASK);
				long undSpotChange = currentPriceValidator.calculateUndSpotChange(refBegin, refEndExclusive, refDerivPrice, derivPrice, observedGreeks);

				if (undSpotChange != PricerValidator.INVALID_UND_SPOT_CHANGE){
					minBegin = Math.min(minBegin, refBegin + undSpotChange);
					maxEnd = Math.max(maxEnd, refEndExclusive + undSpotChange);
				}
			}
		}
		if (minBegin != Long.MAX_VALUE){
			outInterval.set(minBegin, maxEnd, derivPrice);
			LOG.debug("Extrapolated interval [t:{}, secSid:{}, {}]", this.pricerTypeStr, this.derivSecSidStr, outInterval);
			return true;
		}
		return false;
	}
	
	public boolean getIntervalByDerivPriceWithExtrapolation(int derivPrice, LongInterval outInterval) {
		long value = this.observedIntervalsByDerivPrice.get(derivPrice);
		if (value != this.observedIntervalsByDerivPrice.defaultReturnValue()){
			outInterval.set((value & HIGHER_ORDER_MASK) >>> 32, 
					value & LOWER_ORDER_MASK, 
					derivPrice, 
					-1);
			return true;
		}
		if (!observedGreeks.isReady()){
			return false;
		}
		if (!BT_COMPARISON_MODE){
			return extrapolateBaseOnOnePrice(derivPrice, outInterval);
		}
		else {
			return extrapolateBaseOnMultiplePrices(derivPrice, outInterval);
		}
	}
	
	private final StringBuilder builder = new StringBuilder();

	@Override
	public String toString() {
		builder.setLength(0);
		builder.append("[secSid:").append(derivSecSid).append(',')
		.append(" undSecSid:").append(undSecSid).append(',')
		.append(" putOrCall:").append(putOrCall).append(',')
		.append(" delta:").append(this.observedGreeks.delta()).append(',')
		.append(" gamma:").append(this.observedGreeks.gamma())
		.append(" refSpot:").append(this.observedGreeks.refSpot());
		if (observedIntervals.isEmpty()){
			builder.append(", intervals: none]");
			return builder.toString();
		}
		else {
			builder.append(", intervals: ").append(observedIntervals.toString()).append(']');
			return builder.toString();
		}
	}
	
	private static class TickDirectionFinder implements EventHandler<LongTimedPrice> {
		private long refPrice;
		private int tickDirection = TICK_UNKNOWN;
		
		public TickDirectionFinder init(long refPrice){
			this.refPrice = refPrice;
			this.tickDirection = TICK_UNKNOWN;
			return this;
		}
		
		public int tickDirection(){
			return tickDirection;
		}
		
		@Override
		public void onEvent(LongTimedPrice event, long sequence, boolean endOfBatch) throws Exception {
			if (event.price < refPrice){
				tickDirection |= TICK_DOWN;
			}
			if (event.price > refPrice){
				tickDirection |= TICK_UP;
			}
		}		
	}
	
	private final TickDirectionFinder directionFinder = new TickDirectionFinder();
	
	/**
	 * 
	 * @param isCall
	 * @param derivBidPrice
	 * @param derivAskPrice
	 * @param lastDerivBidPrice
	 * @param lastDerivAskPrice
	 * @return
	 */
	private ViolationType validateDerivPrice(int derivBidPrice, int derivAskPrice, boolean isDerivTight){
		if (!lastTightDerivPriceAvail){
			return ViolationType.NO_VIOLATION;
		}
		
		// Get price interval of last tight derivative price
		int tickDirection = TICK_UNKNOWN;
		if (!this.getIntervalByDerivPriceWithExtrapolation(lastTightDerivBidPrice, intervalResultByDeriv)){
            // If we dun have any bucket information for the lastTightBid, we can use the last price
            // iff the last price is observed AFTER the last tight Warrant Price
			if (!(this.extractor.lastPriceChangedTimeNs() != BucketPricer.NULL_TIME_NS && this.lastTightDerivPriceNs < this.extractor.lastPriceChangedTimeNs())){
				return ViolationType.NO_VIOLATION;
			}

			if (minObservedUndSpotSinceLastReset < this.undSpotAtLastTightDerivPrice){
				tickDirection |= TICK_DOWN;
			}
			if (maxObservedUndSpotSinceLastReset > this.undSpotAtLastTightDerivPrice){
				tickDirection |= TICK_UP;
			}
			
			try {
				this.observedUndSpots.peekTillEmpty(directionFinder.init(this.undSpotAtLastTightDerivPrice));
				tickDirection |= directionFinder.tickDirection();
			} 
			catch (Exception e) {
				LOG.error("Caught exception when peeking into observedUndSpots", e);
			}			
		}
		else {
			// Compare price interval with current observed und spot range
			if (minObservedUndSpotSinceLastReset < intervalResultByDeriv.begin()){
				tickDirection |= TICK_DOWN;
			}
			if (maxObservedUndSpotSinceLastReset >= intervalResultByDeriv.endExclusive()){
				tickDirection |= TICK_UP;
			}
		}
		
		return currentPriceValidator.validateDerivPrice(tickDirection, derivBidPrice, derivAskPrice, isDerivTight, lastTightDerivBidPrice, lastTightDerivAskPrice, maxDerivBidSinceLastTightDerivPrice, minDerivAskSinceLastTightDerivPrice);
	}
	
	static final long NULL_TIME_NS = 0l;
	
	private void clearObservedUndSpots() {
		try {
			if (!observedUndSpots.isEmpty()){
				this.observedUndSpots.flushTillEmpty(this.extractor.flusher());
			}
		} 
		catch (Exception e) {
			LOG.error("Caught exception when clearing observed und spot [derivSecSid:{}]", this.derivSecSid, e);
		}

//		LOG.info("Trying to set targetSpreadInTick [from:{}, to:{}]", this.targetSpreadInTick, targetSpreadInTick);
//		LOG.info("Set lastTightDerivPriceAvail to false [t:{}]", this.pricerType);
		this.lastTightDerivPriceAvail = false;
		this.lastTightDerivAskPrice = LongInterval.NULL_DATA_VALUE;
		this.lastTightDerivBidPrice = LongInterval.NULL_DATA_VALUE;
		this.lastTightDerivPriceNs = Long.MIN_VALUE;
		this.undSpotAtLastTightDerivPrice = Long.MIN_VALUE;
		this.minObservedUndSpotSinceLastReset = Long.MAX_VALUE;
		this.maxObservedUndSpotSinceLastReset = Long.MIN_VALUE;
		this.maxDerivBidSinceLastTightDerivPrice = Integer.MIN_VALUE;
		this.minDerivAskSinceLastTightDerivPrice = Integer.MAX_VALUE;
	}
	
	/**
	 * Reset pricer's intervals.
	 * Note: this does not clear observed underlying spots
	 * 
	 * Check python resetPricer
	 * 
	 * @param ts
	 * @return
	 */
	public boolean reset(long ts){
		LOG.debug("Reset pricer [t:{}, secSid:{}, seqNum:{}, time:{}]",
				this.pricerTypeStr,
				derivSecSidStr,
				box(lastTriggerInfo.triggerSeqNum()),
				box(ts));
		this.observedIntervals.clear();
		this.observedIntervalsByDerivPrice.clear();
		this.prevObservedInterval.clear();
		resetAndSetTargetSpreadInTick(this.lastNonVerifiedDerivSpreadInTick);
		
		return true;
	}
	
	   public boolean resetTargetSpread(long ts, int targetSpread){
	        LOG.debug("Reset pricer [t:{}, secSid:{}, seqNum:{}, time:{}]",
	                this.pricerTypeStr,
	                derivSecSidStr,
	                box(lastTriggerInfo.triggerSeqNum()),
	                box(ts));
	        this.observedIntervals.clear();
	        this.observedIntervalsByDerivPrice.clear();
	        this.prevObservedInterval.clear();
	        resetAndSetTargetSpreadInTick(targetSpread);
	        
	        return true;
	    }

	
	public boolean resetAndRegister(long ts, LongInterval interval){
		this.observedIntervals.clear();
		this.observedIntervalsByDerivPrice.clear();
		this.prevObservedInterval.clear();
		resetAndSetTargetSpreadInTick(this.lastNonVerifiedDerivSpreadInTick);

		// We should use only the last point in the input interval, and theoBucketSize should be same (if avail)
		if (interval.last() == LongInterval.NULL_INTERVAL_BEGIN_VALUE){
			throw new IllegalStateException("Unexpected last");
		}
		long begin = interval.last();
		long endExcl = begin + 1;
		if (interval.theoBucketSize() == LongInterval.NULL_INTERVAL_BEGIN_VALUE){
			// interval.last(), interval.last() + 1
			// We only register the last point in the interval
			currentPriceValidator.calculateBucketSize(this.observedGreeks, begin, endExcl, interval.data(), outBucketSizeInfo);
			interval.theoBucketSize(outBucketSizeInfo.maxBucketSize);
//			if (violationType == ViolationType.NO_VIOLATION){
//			}
//			else {
//				LOG.error("Could not get valid max bucket size of interval [{}]", interval);
//			}
		}
		observedIntervals.addWithNoOverlapCheck(begin, endExcl, interval.data(), interval.theoBucketSize());
//        observedIntervals.addWithNoOverlapCheck(interval);
        long value = (begin << 32 | endExcl);
//        long value = (interval.begin() << 32 | interval.endExclusive());
        observedIntervalsByDerivPrice.put(interval.data(), value);
		LOG.debug("Reset pricer and register [t:{}, secSid:{}, seqNum:{}, time:{}] Intervals: {}", 
				this.pricerTypeStr,
				derivSecSidStr,
				box(lastTriggerInfo.triggerSeqNum()),
				ts, 
				this.observedIntervals.toString());
		
		return true;
	}
	
	private ImmutableLongInterval processObservedUndSpots(long ts, int derivPrice, long derivPriceTimeNs) throws Exception{
//		LOG.info("Start processObservedUndSpots [ts:{}, cutoffTimeNs: {}, discardPeriodNs:{}, data:{}]", ts, cutoffTimeNs, discardPeriodNs, derivPrice);
		
		// Check if we need to register ticks
		extractor.init(ts, derivPrice, derivPriceTimeNs);
		this.observedUndSpots.flushTillEmptyOrCancelled(extractor, false);
		return extractor.interval();
	}
	
	ObservedGreeks observedGreeks(){
		return observedGreeks;
	}

	Int2LongOpenHashMap observedIntervalsByDerivPrice(){
		return observedIntervalsByDerivPrice;
	}
	
	int lastDerivBidPrice(){
		return lastDerivBidPrice;
	}
	
	int lastDerivAskPrice(){
		return lastDerivAskPrice;
	}

	int lastTightDerivBidPrice(){
		return lastTightDerivBidPrice;
	}

	int lastTightDerivAskPrice(){
		return lastTightDerivAskPrice;
	}
	
	ObjectCircularBuffer<LongTimedPrice> observedUndSpots(){
		return observedUndSpots;
	}

	NonOverlappingLongIntervalTreeSet observedIntervals(){
		return observedIntervals;
	}
	
	long minObservedUndSpotSinceLastReset(){
		return minObservedUndSpotSinceLastReset;
	}
	
	long maxObservedUndSpotSinceLastReset(){
		return maxObservedUndSpotSinceLastReset;
	}

	IntervalExtractor extractor(){
		return extractor;
	}
	
	public long undSecSid(){
		return undSecSid;
	}
	
	public long derivSecSid(){
		return derivSecSid;
	}

	long discardPeriodNs(){
		return issuerMaxLagNs;
	}
	
	PricerValidator priceValidator(){
		return currentPriceValidator;
	}
	
	private int targetSpreadInTick = Integer.MAX_VALUE;
	
	public int targetSpreadInTick(){
		return this.targetSpreadInTick; 
	}
	
	public void resetAndSetTargetSpreadInTick(int targetSpread){
		// Preset some fields if a new targetSpread is same as this.lastDerivSpreadInTick 
		this.targetSpreadInTick = targetSpread;
		this.clearObservedUndSpots();
		currentPriceValidator.targetSpreadInTick(targetSpread);			
		if (this.targetSpreadInTick == this.lastDerivSpreadInTick && this.lastDerivSpreadInTick != Integer.MAX_VALUE){
    		lastTightDerivBidPrice = this.lastDerivBidPrice;
    		lastTightDerivAskPrice = this.lastDerivAskPrice;
    		lastTightDerivPriceAvail = true;
    		lastTightDerivPriceNs = this.lastDerivPriceNs;
    		undSpotAtLastTightDerivPrice = this.lastUndSpot;
    		shouldRegisterInterval = true;
		}		
	}
	
	public boolean hasTargetSpreadInTickBeenChanged(){
//		LOG.debug("Set before targetSpreadInTick:{}, after targetSpreadInTick:{} [t:{}]", this.targetSpreadInTick, targetSpread, this.pricerType);
		return (this.targetSpreadInTick != this.lastDerivSpreadInTick);
	}

	/**
	 * This method is hacky....trying to accommodate what python does
	 * @param nanoOfDay
	 * @param mmBid3Dp
	 * @param mmAsk3Dp
	 * @param spreadInTick
	 * @return
	 */
	public boolean hasTargetSpreadInTickBeenChangedAndRegisterUndInterval(long nanoOfDay, int mmBid3Dp, int mmAsk3Dp, int spreadInTick){
		boolean result = (this.targetSpreadInTick != this.lastDerivSpreadInTick);
		processDerivTick(nanoOfDay, mmBid3Dp, mmAsk3Dp, spreadInTick, (spreadInTick == targetSpreadInTick && spreadInTick != Integer.MAX_VALUE));
		return result;
	}
	
	private static final int MAX_ACCEPTABLE_DISTANCE_FROM_REF_SPOT_PERCENT = 100;
	private static final int MIN_PRICE = 10;
//	private IntArrayList adjacentBuckets = new IntArrayList(5);
	private final ObjectArrayList<ExtrapolatingInterval> adjacentBuckets = new ObjectArrayList<>(new ExtrapolatingInterval[] {
			new ExtrapolatingInterval(),
			new ExtrapolatingInterval(),
			new ExtrapolatingInterval(),
			new ExtrapolatingInterval(),
			new ExtrapolatingInterval()
	} );
	
	ViolationType computeTheoIntervals(ObservedGreeks greeks, int conversionRatio, int currentDerivBid, NonOverlappingLongIntervalTreeSet outExtrapolatedIntervals, Int2LongOpenHashMap outExtrapolatedIntervalByDerivPrice){
		outExtrapolatedIntervals.clear();
		outExtrapolatedIntervalByDerivPrice.clear();

		if (currentDerivBid == LongInterval.NULL_DATA_VALUE || (currentDerivBid - 2) > MAX_PRICE){
			return ViolationType.NO_VIOLATION;
		}
		
		if (!(greeks.hasRefSpot() && greeks.hasDelta())){
			return ViolationType.NO_VIOLATION;
		}
		
		if (observedIntervals.size() <= 1){
			return ViolationType.NO_VIOLATION;
		}

		// Find min begin and max end that covers +/- 0.002 with respect to currentDerivBidPrice
		int numAdjacentLevels = currentPriceValidator.getAdjacentBuckets(currentDerivBid, 
				Math.max(currentDerivBid - 2, MIN_PRICE), // No extra Math.min to curb at 250, but that's alright, coz getAdjacentBuckets is a for-loop from begin to end
				Math.min(currentDerivBid  + 2, 250), // Extrapolation should only be run up to 250
				adjacentBuckets);
		
		// Traverse all existing intervals
		long partialCal = greeks.gamma() * conversionRatio;
//		LOG.info("Extrapolate [partialCal:{}, gamma:{}, cr:{}", partialCal, greeks.gamma(), conversionRatio);
		processor.init(currentDerivBid, adjacentBuckets, numAdjacentLevels, partialCal);
		observedIntervals.forEach(processor);

		if (!processor.hasSufficientIntervals){
			return ViolationType.NO_VIOLATION;
		}
		
		long minBegin = processor.minBegin();
		long maxEndExcl = processor.maxEndExcl();
		
		long adjDeltaBegin = greeks.calculateAdjDelta(minBegin);
		long adjDeltaEnd = greeks.calculateAdjDelta(maxEndExcl);

		// Start with adjacent buckets first
		long previousEndExcl = Long.MIN_VALUE;
		long beginOfAllAdjacents = Long.MAX_VALUE;
		long endExclOfAllAdjacents = Long.MIN_VALUE;
		
		for (int i = 0; i < numAdjacentLevels; i++){
			ExtrapolatingInterval interval = adjacentBuckets.get(i);
			int data = interval.data();
			int changeInDeriv = data - currentDerivBid; 
			//LOG.info("data:{}, currentDerivBid:{}", data, currentDerivBid);
			long changeInUndSpotBegin = currentPriceValidator.calculateUndSpotChange(
					adjDeltaBegin, 
					changeInDeriv, 
					greeks, 
					partialCal);
			
			long changeInUndSpotEnd = currentPriceValidator.calculateUndSpotChange(
					adjDeltaEnd, 
					changeInDeriv, 
					greeks, 
					partialCal);
			
//			long computedBegin = Math.min(minBegin + changeInUndSpotBegin, interval.currentBegin());
//			long computedEnd = Math.max(maxEndExcl + changeInUndSpotEnd, interval.currentEndExcl());
			
			// If we cannot calculate change in spot, it means the delta doesn't agree with gamma and we should not extrapolate further
			long computedBegin, computedEnd;
			if (changeInUndSpotBegin != PricerValidator.INVALID_UND_SPOT_CHANGE && changeInUndSpotBegin != PricerValidator.INVALID_UND_SPOT_CHANGE){				
				computedBegin = Math.min(minBegin + changeInUndSpotBegin, interval.currentBegin());
				computedEnd = Math.max(maxEndExcl + changeInUndSpotEnd, interval.currentEndExcl());			
			}
			else {
				computedBegin = interval.currentBegin();
				computedEnd = interval.currentEndExcl();				
			}

			if (computedBegin < previousEndExcl){ 
//				LOG.info("Overlap detected [computedBegin:{}, previousEndExcl:{}]", computedBegin, previousEndExcl);
				return ViolationType.PRICE_OVERLAPPED;
			}
			previousEndExcl = computedEnd; 
			beginOfAllAdjacents = Math.min(beginOfAllAdjacents, computedBegin);
			endExclOfAllAdjacents = Math.max(endExclOfAllAdjacents, computedEnd);
			
//			LOG.info("Add interval regular [begin:{}, end:{}, data:{}, bucketSize:{}, minBegin:{}, maxEndExcl:{}, changeInUndSpotBegin:{}, changeInUndSpotEnd:{}, adjDeltaBegin:{}, adjDeltaEnd:{}, partialCal:{}, delta:{}, gamma:{}, refSpot:{}]",
//					computedBegin, computedEnd, data, Math.abs(computedBegin - computedEnd),
//					minBegin,
//					maxEndExcl,
//					changeInUndSpotBegin,
//					changeInUndSpotEnd,
//					adjDeltaBegin,
//					adjDeltaEnd,
//					partialCal,
//					greeks.delta(),
//					greeks.gamma(),
//					greeks.refSpot());
			currentPriceValidator.calculateBucketSize(greeks, computedBegin, computedEnd, data, outBucketSizeInfoForExtrapolation);
			
			// Why computedBegin is larger than computedEnd?
			try {
				outExtrapolatedIntervals.addWithNoOverlapCheck(computedBegin, computedEnd, data, outBucketSizeInfoForExtrapolation.maxBucketSize());
			}
			catch (IllegalArgumentException exception){
				LOG.error("Caught exception when adding new interval", exception);
				LOG.error("Tried to add interval regular [begin:{}, end:{}, data:{}, bucketSize:{}, minBegin:{}, maxEndExcl:{}, changeInUndSpotBegin:{}, changeInUndSpotEnd:{}, adjDeltaBegin:{}, adjDeltaEnd:{}, partialCal:{}, delta:{}, gamma:{}, refSpot:{}]",
				computedBegin, computedEnd, data, Math.abs(computedBegin - computedEnd),
				minBegin,
				maxEndExcl,
				changeInUndSpotBegin,
				changeInUndSpotEnd,
				adjDeltaBegin,
				adjDeltaEnd,
				partialCal,
				greeks.delta(),
				greeks.gamma(),
				greeks.refSpot());
				outExtrapolatedIntervals.clear();
				outExtrapolatedIntervalByDerivPrice.clear();
				return ViolationType.NO_VIOLATION;
			}
			outExtrapolatedIntervalByDerivPrice.put(data, (computedBegin << 32 | computedEnd));
		}

		// Copy rest of buckets from main pricer
		for (int i = 0; i < processor.numExtraEntries; i++){
			it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry = processor.extraEntries.get(i);
			long intervalBegin = entry.getLongValue() & LOWER_ORDER_MASK;			
			long intervalEndExclusive = entry.getLongKey() & LOWER_ORDER_MASK;
			int data = (int)((entry.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
			long bucketSize = (entry.getLongValue() & HIGHER_ORDER_MASK) >>> 32;

			// Found overlapping
			if (intervalEndExclusive <= beginOfAllAdjacents || intervalBegin >= endExclOfAllAdjacents){
				outExtrapolatedIntervals.addWithNoOverlapCheck(intervalBegin, intervalEndExclusive, data, bucketSize);
				outExtrapolatedIntervalByDerivPrice.put(data, (intervalBegin << 32 | intervalEndExclusive));
			}
			else {
				return ViolationType.PRICE_OVERLAPPED;
			}
		}
		
		return ViolationType.NO_VIOLATION;
	}
	
	private static class IntervalProcessor implements Consumer<it.unimi.dsi.fastutil.longs.Long2LongMap.Entry>{
		private final ObservedGreeks greeks;
		private final PricerValidator validator;
		private long minBegin = Long.MAX_VALUE;
		private long maxEndExcl = Long.MIN_VALUE;
		private int refPrice;
		private ObjectArrayList<ExtrapolatingInterval> seqBucketsToBeExtrapolated;
		private int numIntervals;
		private long partialCal;
		private final ObjectArrayList<it.unimi.dsi.fastutil.longs.Long2LongMap.Entry> extraEntries = new ObjectArrayList<>(); // Default size: 16
		private int numExtraEntries = 0;
		private boolean hasSufficientIntervals;
		private int index;
		
		IntervalProcessor(ObservedGreeks greeks, PricerValidator validator){
			this.greeks = greeks;
			this.validator = validator;
		}
		
		public void init(int refPrice, ObjectArrayList<ExtrapolatingInterval> seqBucketsToBeExtrapolated, int numIntervals, long partialCal){
			minBegin = Long.MAX_VALUE;
			maxEndExcl = Long.MIN_VALUE;
			this.refPrice = refPrice;
			this.seqBucketsToBeExtrapolated = seqBucketsToBeExtrapolated;
			this.numIntervals = numIntervals;
			this.partialCal = partialCal;
			this.hasSufficientIntervals = false;
			extraEntries.clear();
			numExtraEntries = 0;
			index = 0;
		}
		
		/**
		 * For each entry, check if we need to extrapolate it.
		 */
		@Override
		public void accept(it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry) {
			long begin = entry.getLongValue() & LOWER_ORDER_MASK;
			long endExcl = entry.getLongKey() & LOWER_ORDER_MASK;
			int data = (int)((entry.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
			if (data == refPrice){
				minBegin = Math.min(begin, minBegin);
				maxEndExcl = Math.max(endExcl, maxEndExcl);
			}
			else {
				boolean found = false;
				for (int i = index; i < numIntervals; i++){
					ExtrapolatingInterval extrapolatingInterval = seqBucketsToBeExtrapolated.get(i);
					if (extrapolatingInterval.data() == data){
						found = true;
						index = i + 1;

						// Skip if further point in bucket is > 1% of current refSpot
						long refSpot = 1000L * greeks.refSpot();
						if (refSpot / Math.max(Math.abs(begin - refSpot), Math.abs(endExcl - refSpot)) >= MAX_ACCEPTABLE_DISTANCE_FROM_REF_SPOT_PERCENT){
//						if (refSpot == 1 || (refSpot / Math.max(Math.abs(begin - refSpot), Math.abs(endExcl - refSpot)) >= MAX_ACCEPTABLE_DISTANCE_FROM_REF_SPOT_PERCENT)){
							int changeInDeriv = refPrice - data;
							long changeInUndSpotBeginWrtCurrentBid = validator.calculateUndSpotChange(
									greeks.calculateAdjDelta(begin),
									changeInDeriv, 
									greeks, 
									partialCal);
							long changeInUndSpotEndWrtCurrentBid = validator.calculateUndSpotChange(
									greeks.calculateAdjDelta(endExcl),
									changeInDeriv, 
									greeks, 
									partialCal);
									
							long computedTheoBeginOfCurrentBid = begin + changeInUndSpotBeginWrtCurrentBid;
							long computedTheoEndOfCurrentBid = endExcl + changeInUndSpotEndWrtCurrentBid;
							minBegin = Math.min(computedTheoBeginOfCurrentBid, minBegin);
							maxEndExcl = Math.max(computedTheoEndOfCurrentBid, maxEndExcl);
//							LOG.info("Compute theo begin and end [data:{}, changeInUndSpotBegin:{}, changeInUndSpotEnd:{}]", data, changeInUndSpotBeginWrtCurrentBid, changeInUndSpotEndWrtCurrentBid);
							hasSufficientIntervals = true;
							
							extrapolatingInterval.currentBegin(begin).currentEndExcl(endExcl);
						}
						break;
					}
				}
				if (!found){
					extraEntries.add(numExtraEntries++, entry);					
				}
			}
		}

		public long minBegin(){
			return minBegin;
		}
		
		public long maxEndExcl(){
			return maxEndExcl;
		}
	}
	
//	private final Int2ObjectOpenHashMap<Int2LongOpenHashMap> storedObservedIntervalsByTargetSpread;
	
//	private void calculateVolAdj(int derivPrice){
//		Int2LongOpenHashMap observedIntervalsForTargetSpread = this.storedObservedIntervalsByTargetSpread.get(this.targetSpreadInTick);
//		if (observedIntervalsForTargetSpread != this.storedObservedIntervalsByTargetSpread.defaultReturnValue()){
//			if (this.getIntervalByDerivPriceWithExtrapolation(derivPrice, temp)){
//				currentPriceValidator.validateBucketSize(observedGreeks, temp.begin(), temp.endExclusive(), temp.data(), outBucketSizeInfoForVolAdj);
//				
//				SpreadTableDetails detailsAtPrice = spreadTable.detailsAtPrice(derivPrice);
//				int previousDerivPrice = derivPrice - detailsAtPrice.spread();
//				if (derivPrice == detailsAtPrice.fromPrice()){
//					previousDerivPrice = derivPrice - detailsAtPrice.previous().spread();
//				}
//				int nextDerivPrice = derivPrice + detailsAtPrice.spread();
//			}
//		}
//	}
//	
//	private void backup(){
//		
//	}
	
	ObjectArrayList<ExtrapolatingInterval> adjacentBuckets(){
		return adjacentBuckets;
	}
	
	long lastUndSpotProcessedTimeNs(){
		return lastUndSpotProcessedTimeNs;
	}
	
	long lastPrice(){
		return this.extractor.lastPrice();
	}

	public void clear(){
		observedGreeks.clear();
		observedUndSpots.clear();
		observedIntervals.clear();
		observedIntervalsByDerivPrice.clear();
		this.maxObservedUndSpotSinceLastReset = Long.MIN_VALUE;
		this.minObservedUndSpotSinceLastReset = Long.MAX_VALUE;
		this.maxDerivBidSinceLastTightDerivPrice = Integer.MIN_VALUE;
		this.minDerivAskSinceLastTightDerivPrice = Integer.MAX_VALUE;
		// finder - no need to clear
//		smallerSearchResult.clear();
//		greaterSearchResult.clear();
//		outBucketSizeInfo.clear();
		// extractor - no need to clear
//		for (int i = 0; i < this.searchOverlapResults.length; i++){
//			this.searchOverlapResults[i].clear();
//		}
//		for (int i = 0; i < this.overlapResults.length; i++){
//			this.overlapResults[i].clear();
//		}
//		for (int i = 0; i < this.searchOverlapMapEntries.length; i++){
//			this.searchOverlapMapEntries[i] = null;
//		}
	}
}
