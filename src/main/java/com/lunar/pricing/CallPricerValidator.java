package com.lunar.pricing;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableDetails;
import com.lunar.pricing.BucketPricer.BucketSizeInfo;
import com.lunar.pricing.BucketPricer.ViolationType;
import com.lunar.util.LongInterval;
import com.lunar.util.NonOverlappingLongIntervalTreeSet;

import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;

public final class CallPricerValidator implements PricerValidator {
	static final Logger LOG = LogManager.getLogger(CallPricerValidator.class);
	
	private long secSid;
	private int conversionRatio;	
	private int bucketDistanceAllowance = 100;	
	// This is deltaAllowance in python
	private int deltaAllowance;
	private int targetSpread = Integer.MAX_VALUE;
	private int checkPricingAllowance = 950;
	private SpreadTable spreadTable;	
	private final LongInterval outInterval = LongInterval.of();

	static CallPricerValidator of(long secSid, int conversionRatio, int deltaAllowance, SpreadTable spreadTable){
		return new CallPricerValidator(secSid, conversionRatio, deltaAllowance, spreadTable);
	}

	CallPricerValidator(long secSid, int conversionRatio, int deltaAllowance, SpreadTable spreadTable){
		this.secSid = secSid;
		this.conversionRatio = conversionRatio;
		this.deltaAllowance = deltaAllowance;
		this.spreadTable = spreadTable;
	}
	
	@Override
	public void targetSpreadInTick(int targetSpread) {
		this.targetSpread = targetSpread;
	}
	
	@Override
	public boolean shiftTheoBucket(ObservedGreeks greeks, long currentUndSpot, int currentDerivBid, long minBegin, long maxEndExclusive, LongInterval outInterval) {
		int count = 0;
		long partialCal = greeks.gamma() * conversionRatio;
		while (currentUndSpot >= maxEndExclusive && count <= MAX_NUM_BUCKET_APPROXIMATION){
			long adjDeltaBegin = greeks.calculateAdjDelta(minBegin);
			long adjDeltaEndExcl = greeks.calculateAdjDelta(maxEndExclusive);
			int tickSize = spreadTable.priceToTickSize(currentDerivBid);
			double changeInUndSpotBegin = (- adjDeltaBegin + Math.sqrt(adjDeltaBegin * adjDeltaBegin + partialCal * tickSize))/greeks.gamma();
			double changeInUndSpotEnd = (- adjDeltaEndExcl + Math.sqrt(adjDeltaEndExcl * adjDeltaEndExcl + partialCal * tickSize))/greeks.gamma();
			minBegin += changeInUndSpotBegin;
			maxEndExclusive += changeInUndSpotEnd;
			currentDerivBid += tickSize;
		}
		if (count == MAX_NUM_BUCKET_APPROXIMATION){
			LOG.warn("Could not extend theo bucket");
			return false;
		}
		if (currentUndSpot < minBegin){
			outInterval.begin(currentUndSpot).endExclusive(maxEndExclusive);
		}
		else{
			outInterval.begin(minBegin).endExclusive(maxEndExclusive);
		}
		return true;
	};
	
	@Override
	public ViolationType validatePriceConsistency(long toBegin, long toEndExcl, int data, long theoBucketSize, LongInterval from) {
		// When o1 is definitely smaller than o2, o1.data must be smaller than o2.data
		// otherwise return false
		if (from.begin() < toBegin && from.endExclusive() < toEndExcl && from.data() > data){
			LOG.warn("Down vol for call - interval up, data down [fromBegin:{}, fromEnd:{}, fromData:{}, toBegin:{}, toEndExcl:{}, toData:{}]", from.begin(), from.endExclusive(), from.data(), toBegin, toEndExcl, data);
			return ViolationType.DOWN_VOL;
		}
		// When o1 is definitely greater than o2, o1.data must be larger than o2.data
		// otherwise return false
		if (from.begin() > toBegin && from.endExclusive() > toEndExcl && from.data() < data){
			LOG.warn("Up vol for call - interval down, data up [fromBegin:{}, fromEnd:{}, fromData:{}, toBegin:{}, toEndExcl:{}, toData:{}]", from.begin(), from.endExclusive(), from.data(), toBegin, toEndExcl, data);
			return ViolationType.UP_VOL;
		}
		return ViolationType.NO_VIOLATION;
	}
	
	@Override
	public ViolationType validateDerivPrice(int tickDirection, int derivBidPrice, int derivAskPrice, boolean isDerivTight, 
			int lastDerivBidPrice, 
			int lastDerivAskPrice,
			int maxDerivBidSinceLastTightDerivPrice,
			int minDerivAskSinceLastTightDerivPrice) {
		boolean hasDerivBid = derivBidPrice != LongInterval.NULL_DATA_VALUE;
		if (isDerivTight && hasDerivBid && derivBidPrice != lastDerivBidPrice){
			if (((tickDirection & BucketPricer.TICK_UP) == 0) && derivBidPrice > lastDerivBidPrice){
				return ViolationType.UP_VOL;
			}
			if (((tickDirection & BucketPricer.TICK_DOWN) == 0) && derivBidPrice < lastDerivBidPrice){
				return ViolationType.DOWN_VOL;
			}			
		}
		else {
			// Don't validate input prices (assume they are valid)
			if (((tickDirection & BucketPricer.TICK_UP) == 0) && hasDerivBid){
				if (derivBidPrice >= lastDerivAskPrice){
					return ViolationType.UP_VOL;
				}
			}
			if (((tickDirection & BucketPricer.TICK_DOWN) == 0) && derivAskPrice != LongInterval.NULL_DATA_VALUE){
				if (derivAskPrice <= lastDerivBidPrice){
					return ViolationType.DOWN_VOL;
				}
			}
		}
		return ViolationType.NO_VIOLATION;
	}

	@Override
	public ViolationType validateBucketDistance(long begin, long endExcl, int data, long theoBucketSize, LongInterval refInterval) {
		if (refInterval.isEmpty()){
			throw BucketPricer.EMPTY_INTERVAL;
		}
		int dataInTick = spreadTable.priceToTick(data);
		int refDataInTick = spreadTable.priceToTick(refInterval.data());
		if (refInterval.data() < data){
			long numTicks = dataInTick - refDataInTick;
//			LOG.info("Number of ticks in between [{}]", numTicks);
			long minDistance = ((numTicks - 1)*1000 - bucketDistanceAllowance) * Math.min(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((begin - refInterval.endExclusive()) < minDistance) {
				LOG.debug("Difference between beginning of new interval and end of reference interval is less than minimum distance [secSid:{}, data:{}, refData:{}, newBegin:{}, refEndExcl:{}, minDistance:{}]",
						box(secSid),
						box(data),
						box(refInterval.data()),
						box(begin),
						box(refInterval.endExclusive()),
						box(minDistance));
				return ViolationType.UP_VOL;
			}
			// [a, b] -> 135 (ref), [c, d] -> 136 (new)
			long maxDistance = ((numTicks + 1)*1000 + bucketDistanceAllowance) * Math.max(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((endExcl - refInterval.begin()) > maxDistance){
				return ViolationType.DOWN_VOL;
			}
		}
		else if (refInterval.data() > data){
			long numTicks = refDataInTick - dataInTick;
			long minDistance = ((numTicks - 1)*1000 - bucketDistanceAllowance) * Math.min(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((refInterval.begin() - endExcl) < minDistance){
				LOG.debug("Difference between beginning of reference interval and end of new interval is less than minimum distance [secSid:{}, data:{}, refData:{}, refBegin:{}, newEndExcl:{}, minDistance:{}]",
						box(secSid),
						box(data),
						box(refInterval.data()),
						box(refInterval.begin()),
						box(endExcl),
						box(minDistance));
				return ViolationType.DOWN_VOL;
			}
			// [a, b] -> 135 (new), [c, d] -> 136 (ref)
			long maxDistance = ((numTicks + 1)*1000 + bucketDistanceAllowance) * Math.max(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((refInterval.endExclusive() - begin) > maxDistance){
				return ViolationType.UP_VOL;
			}
		}
		return ViolationType.NO_VIOLATION;
	}

	@Override
	public ViolationType validateBucketSize(ObservedGreeks greeks, long intervalBegin, long intervalEndExcl, int data, boolean extendEnd, BucketSizeInfo outInfo) {
		try {
			outInfo.clear();
			if (!greeks.isReady()){
				return ViolationType.NO_VIOLATION;
			}
			// Delta has 5 implicit decimal places (x100000)
			// Gamma has 5 implicit decimal places (x100000)
			// RefSpot has 3 implicit decimal places (x1000)
			// Interval.begin has 6 implicit decimal places (x1000000)
			// Interval.end has 6 implicit decimal places (x1000000)
			// Interval.data has 3 implicit decimal places (x1000)
			long currentBucketSize = intervalEndExcl - intervalBegin; /* 6 dp */
			outInfo.currentBucketSize(currentBucketSize);
			int nextPrice = data + spreadTable.priceToTickSize(data);
			long adjDelta = greeks.calculateAdjDelta(intervalBegin);
			outInfo.adjDelta(adjDelta);
			long maxBucketSize = Math.abs(((long)nextPrice - data) /* 3dp */ * 100_000 * conversionRatio /* 3 dp */ / adjDelta) /* 5 dp */ ; /* 6 dp */
			outInfo.maxBucketSize(maxBucketSize);
			long adjMaxBucketSize = maxBucketSize * deltaAllowance /* 3 dp */ / 1000;
			outInfo.adjMaxBucketSize(adjMaxBucketSize);
			if (currentBucketSize <= adjMaxBucketSize){
//				LOG.debug("Bucket size info [currentBucketSize:{}, begin:{}, end:{}, theoBucketSize:{}]", currentBucketSize, intervalBegin, intervalEndExcl, maxBucketSize);
				return ViolationType.NO_VIOLATION;
			}
			return (extendEnd) ? ViolationType.DOWN_VOL : ViolationType.UP_VOL;
//			LOG.error("Bucket too big [t:{}, secSid:{}, currentBucketSize:{}, begin:{}, end:{}, adjMaxBucketSize:{}]",
//					pricerTypeStr,
//					box(secSid),
//					box(currentBucketSize), 
//					box(intervalBegin), 
//					box(intervalEndExcl), 
//					box(adjMaxBucketSize));
//			return ViolationType.BUCKET_TOO_BIG;
		}
		catch (Exception e){
			LOG.error("Caught exception in bucket size calculation [secSid:{}]", secSid, e);
			return ViolationType.ERROR;
		}
	}
	
	@Override
	public void calculateBucketSize(ObservedGreeks greeks, long intervalBegin, long intervalEndExcl, int data, BucketSizeInfo outInfo) {
		try {
			outInfo.clear();
			if (greeks.isReady()){
				// Delta has 5 implicit decimal places (x100000)
				// Gamma has 5 implicit decimal places (x100000)
				// RefSpot has 3 implicit decimal places (x1000)
				// Interval.begin has 6 implicit decimal places (x1000000)
				// Interval.end has 6 implicit decimal places (x1000000)
				// Interval.data has 3 implicit decimal places (x1000)
				long currentBucketSize = intervalEndExcl - intervalBegin; /* 6 dp */
				outInfo.currentBucketSize(currentBucketSize);
				int nextPrice = data + spreadTable.priceToTickSize(data);
				long adjDelta = greeks.calculateAdjDelta(intervalBegin);
				outInfo.adjDelta(adjDelta);
				long maxBucketSize = Math.abs(((long)nextPrice - data) /* 3dp */ * 100_000 * conversionRatio /* 3 dp */ / adjDelta) /* 5 dp */ ; /* 6 dp */
				outInfo.maxBucketSize(maxBucketSize);
				long adjMaxBucketSize = maxBucketSize * deltaAllowance /* 3 dp */ / 1000;
				outInfo.adjMaxBucketSize(adjMaxBucketSize);
			}
		}
		catch (Exception e){
			LOG.error("Caught exception in bucket size calculation [secSid:{}]", secSid, e);
		}
	}

	@Override
	public ViolationType validateDerivAskPrice(ObservedGreeks greeks, long minWeighted, long maxWeighted, int derivAskPrice, NonOverlappingLongIntervalTreeSet existingIntervals){
		outInterval.clear();
		
		if (!greeks.isReady() || minWeighted == LongInterval.NULL_INTERVAL_BEGIN_VALUE || targetSpread == Integer.MAX_VALUE || derivAskPrice == LongInterval.NULL_DATA_VALUE){
			return ViolationType.NO_VIOLATION;
		}
		
		// Find the interval that contains 'begin', deduce a theoAsk from the interval.data, then check theoAsk against derivAskPrice
		// If interval not found, go thru all intervals that are smaller than 'begin'.  Extrapolate theoAsk, then check theoAsk against derivAskPrice.
		ObjectBidirectionalIterator<Entry> iterator = existingIntervals.searchExactAndIterate(minWeighted + 1);
		
		boolean found = false;
		if (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
			int data = (int)((interval.getLongKey() & NonOverlappingLongIntervalTreeSet.HIGHER_ORDER_MASK) >>> 32);
			if (minWeighted >= intervalBegin && minWeighted < intervalEndExclusive){
				int dataInTick = spreadTable.priceToTick(data);
				int theoAsk = spreadTable.tickToPrice(dataInTick + targetSpread);
				if (derivAskPrice < theoAsk){
					return ViolationType.DOWN_VOL;
				}
				found = true;
			}
			iterator.back(1);
		}
		if (!found && iterator.hasPrevious()){
			Entry interval = iterator.previous();
			long intervalEndExclusive = interval.getLongKey() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
			int data = (int)((interval.getLongKey() & NonOverlappingLongIntervalTreeSet.HIGHER_ORDER_MASK) >>> 32);
			if (minWeighted >= intervalBegin && minWeighted < intervalEndExclusive){
				int dataInTick = spreadTable.priceToTick(data);
				int theoAsk = spreadTable.tickToPrice(dataInTick + targetSpread);
				if (derivAskPrice < theoAsk){
					return ViolationType.DOWN_VOL;
				}
			}
			else {
				long adjDelta = greeks.calculateAdjDelta(intervalBegin);
				int theoBid = data + (int)((minWeighted - intervalBegin) * adjDelta * this.checkPricingAllowance / ((long)this.conversionRatio * 100000000));
				SpreadTableDetails details = spreadTable.detailsAtPrice(theoBid);
				int dataInTick = 0;
				int theoAsk = -1;
				if (minWeighted > intervalBegin){
					// [ 10 --------- 18 ]  [20 -------- 30]  [35 -------- 40]
					// Find 32 (begin)
					// (6 + 5 + 3) - (3 + 8) = 3					
					theoBid -= (theoBid % details.spread());
					dataInTick = spreadTable.priceToTick(theoBid);
					theoAsk = spreadTable.tickToPrice(dataInTick + targetSpread);
					if (derivAskPrice < theoAsk){
						return ViolationType.DOWN_VOL;
					}
				}
				
				while (iterator.hasPrevious()){
					interval = iterator.previous();
					intervalBegin = interval.getLongValue() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
					if (minWeighted > intervalBegin){
						data = (int)((interval.getLongKey() & NonOverlappingLongIntervalTreeSet.HIGHER_ORDER_MASK) >>> 32);
						adjDelta = greeks.calculateAdjDelta(intervalBegin);
						theoBid = data + (int)((minWeighted - intervalBegin) * adjDelta * this.checkPricingAllowance / ((long)this.conversionRatio * 100000000));
						// If theoBid crosses spread table boundary, get new spread table details
						if (theoBid < details.fromPrice()){
							details = spreadTable.detailsAtPrice(theoBid);
						}
						theoBid -= (theoBid % details.spread());
						dataInTick = spreadTable.priceToTick(theoBid);
						theoAsk = spreadTable.tickToPrice(dataInTick + targetSpread);
						if (derivAskPrice < theoAsk){
							return ViolationType.DOWN_VOL;
						}
					}
				}
			}
		}
//		
//		if (existingIntervals.searchOverlapOrSmaller(endExcl, outInterval)){
//			// Calculate bucketSize of existingInterval
//			int tickOffset = (int)((endExcl - outInterval.begin()) / outInterval.theoBucketSize());
//			int dataInTick = spreadTable.priceToTick(outInterval.data());
//			int adjTheoAsk = spreadTable.tickToPrice(dataInTick + tickOffset + targetSpread - bucketTickAccuracyAllowance);
//			return (derivAskPrice < adjTheoAsk) ? ViolationType.DOWN_VOL : ViolationType.NO_VIOLATION; 
//		}
		return ViolationType.NO_VIOLATION;
	}

	@Override
	public long calculateUndSpotChange(long refBegin, long refEndExcl, int refDerivPrice, int derivPrice /* T+1 - T */, ObservedGreeks greeks){
//		int refNextPrice = refDerivPrice + spreadTable.priceToTickSize(refDerivPrice);
//		long adjRefDelta = greeks.calculateAdjDelta(refBegin);
//		long maxBucketSize = Math.abs(((long)refNextPrice - refDerivPrice) /* 3dp */ * 100_000 * conversionRatio /* 3 dp */ / adjRefDelta) /* 5 dp */ ; /* 6 dp */
//		LOG.info("Bucket size is {}", maxBucketSize);
		long changeInDeriv = derivPrice - refDerivPrice;
		long changeInUndSpot = (long)(changeInDeriv) * this.conversionRatio * 100_000 / greeks.delta();
		if (greeks.gamma() == 0){
			return changeInUndSpot; 
		}
		if (changeInDeriv != 0 && Math.abs(changeInUndSpot) * greeks.gamma() > 1_000_000L){
			long adjDelta = greeks.calculateAdjDelta(refBegin);
//			return (long)((- adjDelta /* 5p */ + Math.sqrt(adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * greeks.gamma() /* 5dp */ * this.conversionRatio /* 3 dp*/ * (derivPrice - refDerivPrice) /* 3 dp */ / 10)) * 1_000_000) /greeks.gamma() /* 5dp */;								 
			long valueToBeSqrt = adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * greeks.gamma() /* 5dp */ * this.conversionRatio /* 3 dp*/ * (changeInDeriv) /* 3 dp */ / 10;
			if (valueToBeSqrt >= 0){
				return (long)((- adjDelta /* 5p */ + Math.sqrt(valueToBeSqrt)) * 1_000_000) /greeks.gamma() /* 5dp */;
			}
			else {
				return INVALID_UND_SPOT_CHANGE;
			}
		}
		return 0;
	}
	
	@Override
	public long calculateUndSpotChange(long delta, int changeInDeriv /* T+1 - T */, ObservedGreeks greeks, long partialCalculateValue){
//		int refNextPrice = refDerivPrice + spreadTable.priceToTickSize(refDerivPrice);
//		long adjRefDelta = greeks.calculateAdjDelta(refBegin);
//		long maxBucketSize = Math.abs(((long)refNextPrice - refDerivPrice) /* 3dp */ * 100_000 * conversionRatio /* 3 dp */ / adjRefDelta) /* 5 dp */ ; /* 6 dp */
//		LOG.info("Bucket size is {}", maxBucketSize);
		long changeInUndSpot = (long)(changeInDeriv) * this.conversionRatio * 100_000 / greeks.delta();
		if (greeks.gamma() == 0){
//			LOG.info("Calculated calculateUndSpotChange with zero gamma - call");
			return changeInUndSpot; 
		}
		
		if (changeInDeriv != 0 && Math.abs(changeInUndSpot) * greeks.gamma() > 1_000_000L){
			// delta - 5dp
			// partialCalculateValue - this is /* greeks.gamma() 5dp * this.conversionRatio 3 dp */
//			long result = (long)((- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000 )/greeks.gamma() /* 5dp */;
//			return result;
			long valueToBeSqrt = delta * delta + partialCalculateValue * (changeInDeriv) /* 3 dp */ / 5000;
			if (valueToBeSqrt >= 0){
				//		LOG.info("Calculated calculateUndSpotChange - call [delta:{}, partialCalculateValue:{}, gamma:{}, changeInDeriv:{}, changeInSpot:{}]", 
				//				delta,
				//				partialCalculateValue, 
				//				greeks.gamma(),
				//				changeInDeriv,
				//				result);
				return (long)((- delta /* 5p */ + Math.sqrt(valueToBeSqrt)) * 1_000_000_000)/greeks.gamma() /* 5dp */;
			}
			else{
				return INVALID_UND_SPOT_CHANGE;
			}
		}
		return 0;
	}
	
	@Override
	public int getAdjacentBuckets(int current, int begin, int end, ObjectArrayList<ExtrapolatingInterval> outList){
		int index = 0;
		for (int i = begin; i <= end; i++){
			outList.get(index++).init(i);
		}
		return index;
	}
}
