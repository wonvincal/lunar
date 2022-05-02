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

public class PutPricerValidator implements PricerValidator {
	static final Logger LOG = LogManager.getLogger(PutPricerValidator.class);

	private long secSid;
	private int conversionRatio;
	private int bucketDistanceAllowance = 100;
	private int deltaAllowance;
	private int targetSpread = Integer.MAX_VALUE;
	private int checkPricingAllowance = 950;
	private SpreadTable spreadTable;
	private final LongInterval outInterval = LongInterval.of();
	
	static PutPricerValidator of(long secSid, int conversionRatio, int deltaAllowance, SpreadTable spreadTable){
		return new PutPricerValidator(secSid, conversionRatio, deltaAllowance, spreadTable);
	}

	PutPricerValidator(long secSid, int conversionRatio, int deltaAllowance, SpreadTable spreadTable){
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
			double changeInUndSpotBegin = (- adjDeltaBegin - Math.sqrt(adjDeltaBegin * adjDeltaBegin + partialCal * tickSize))/greeks.gamma(); 
			double changeInUndSpotEnd = (- adjDeltaEndExcl - Math.sqrt(adjDeltaEndExcl * adjDeltaEndExcl + partialCal * tickSize))/greeks.gamma();
			minBegin += changeInUndSpotBegin;
			maxEndExclusive += changeInUndSpotEnd; 
			currentDerivBid += tickSize;
		}
		if (count == MAX_NUM_BUCKET_APPROXIMATION){
			LOG.warn("Could not extend theo bucket");
			return false;
		}
		if (currentUndSpot > maxEndExclusive){
			outInterval.begin(minBegin).endExclusive(currentUndSpot);
		}
		else{
			outInterval.begin(minBegin).endExclusive(maxEndExclusive);
		}
		return true;
	};
	
	@Override
	public ViolationType validatePriceConsistency(long toBegin, long toEndExcl, int data, long theoBucketSize, LongInterval from) {
		// drop in stock price, drop in warrant price
		if (from.begin() < toBegin && from.endExclusive() < toEndExcl && from.data() < data){
			LOG.warn("Up vol for put - interval up, data up [fromBegin:{}, fromEnd:{}, fromData:{}, toBegin:{}, toEndExcl:{}, toData:{}]", from.begin(), from.endExclusive(), from.data(), toBegin, toEndExcl, data);
			return ViolationType.UP_VOL;
		}
		// increase in stock price, increase in warrant price
		if (from.begin() > toBegin && from.endExclusive() > toEndExcl && from.data() > data){
			LOG.warn("Up vol for put - interval down, data down [fromBegin:{}, fromEnd:{}, fromData:{}, toBegin:{}, toEndExcl:{}, toData:{}]", from.begin(), from.endExclusive(), from.data(), toBegin, toEndExcl, data);
			return ViolationType.DOWN_VOL;
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
			if (((tickDirection & BucketPricer.TICK_UP) == 0) &&  derivBidPrice < lastDerivBidPrice){
				return ViolationType.DOWN_VOL;
			}
			if (((tickDirection & BucketPricer.TICK_DOWN) == 0) && derivBidPrice > lastDerivBidPrice){
				return ViolationType.UP_VOL;
			}
		}
		else {
			if (((tickDirection & BucketPricer.TICK_UP) == 0) && derivAskPrice != LongInterval.NULL_DATA_VALUE){
				if (derivAskPrice <= lastDerivBidPrice){
					return ViolationType.DOWN_VOL;
				}
			}
			if (((tickDirection & BucketPricer.TICK_DOWN) == 0) && hasDerivBid){
				if (derivBidPrice >= lastDerivAskPrice){
					return ViolationType.UP_VOL;
				}
			}			
		}
		return ViolationType.NO_VIOLATION;
	}

	@Override
	public ViolationType validateBucketDistance(long begin, long endExcl, int data, long theoBucketSize, LongInterval refInterval){
		if (refInterval.isEmpty()){
			throw BucketPricer.EMPTY_INTERVAL;
		}
		int dataInTick = spreadTable.priceToTick(data);
		int refDataInTick = spreadTable.priceToTick(refInterval.data());
		if (refInterval.data() < data){
			long numTicks = dataInTick - refDataInTick;
			long minDistance = ((numTicks - 1)*1000 - bucketDistanceAllowance) * Math.min(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((refInterval.begin() - endExcl) < minDistance) {
				LOG.debug("Difference between beginning of reference interval and end of input interval is less than minimum distance [secSid:{}, data:{}, refData:{}, refBegin:{}, endExcl:{}, minDistance:{}, diff:{}]",
						box(secSid),
						box(data),
						box(refInterval.data()),
						box(refInterval.begin()),
						box(endExcl),
						box(minDistance),
						box(refInterval.begin() - endExcl));
				return ViolationType.UP_VOL;
			}
			// [c, d] -> 136 (new), [a, b] -> 135 (ref) Put buckets are in reverse order 
			long maxDistance = ((numTicks + 1)*1000 + bucketDistanceAllowance) * Math.max(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((refInterval.endExclusive() - begin) > maxDistance){
				return ViolationType.DOWN_VOL;
			}
		}
		else if (refInterval.data() > data){
			long numTicks = refDataInTick - dataInTick;
			long minDistance = ((numTicks - 1)*1000 - bucketDistanceAllowance) * Math.min(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((begin - refInterval.endExclusive()) < minDistance){
				LOG.debug("Difference between beginning of input interval and end of reference interval is less than minimum distance [secSid:{}, begin:{}, refEndExcl:{}, minDistance:{}]",
						box(secSid),
						box(begin),
						box(refInterval.endExclusive()),
						box(minDistance));
				return ViolationType.DOWN_VOL;
			}
			// [c, d] -> 136 (ref), [a, b] -> 135 (new) Put buckets are in reverse order 
			long maxDistance = ((numTicks + 1)*1000 + bucketDistanceAllowance) * Math.max(theoBucketSize, refInterval.theoBucketSize()) / 1000;
			if ((endExcl - refInterval.begin()) > maxDistance){
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
			// Interval.begin has 6 implicit decimal places (x100000000)
			// Interval.end has 6 implicit decimal places (x100000000)
			// Interval.data has 3 implicit decimal places (x1000)
			long currentBucketSize = intervalEndExcl - intervalBegin; /* 6 dp */
			outInfo.currentBucketSize(currentBucketSize);
			int prevPrice = spreadTable.tickToPrice(spreadTable.priceToTick(data) - 1);
			long adjDelta = greeks.calculateAdjDelta(intervalEndExcl);
			outInfo.adjDelta(adjDelta);
			long maxBucketSize = Math.abs(((long)data - prevPrice) /* 3dp */ * 100_000 * conversionRatio /* 3 dp */ / adjDelta) /* 5 dp */ ; /* 8 dp */
			outInfo.maxBucketSize(maxBucketSize);
			long adjMaxBucketSize = maxBucketSize * deltaAllowance / 1000;
			outInfo.adjMaxBucketSize(adjMaxBucketSize);
			
			if (currentBucketSize > adjMaxBucketSize){
				return (extendEnd) ? ViolationType.UP_VOL : ViolationType.DOWN_VOL;
//
//				LOG.error("Bucket too big [secSid:{}, currentBucketSize:{}, begin:{}, end:{}, adjMaxBucketSize:{}]",
//						box(secSid),
//						box(currentBucketSize), 
//						box(intervalBegin), 
//						box(intervalEndExcl), 
//						box(adjMaxBucketSize));
//				return ViolationType.BUCKET_TOO_BIG;
			}
			return ViolationType.NO_VIOLATION;
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
				// Interval.begin has 6 implicit decimal places (x100000000)
				// Interval.end has 6 implicit decimal places (x100000000)
				// Interval.data has 3 implicit decimal places (x1000)
				long currentBucketSize = intervalEndExcl - intervalBegin; /* 6 dp */
				outInfo.currentBucketSize(currentBucketSize);
				int prevPrice = spreadTable.tickToPrice(spreadTable.priceToTick(data) - 1);
				long adjDelta = greeks.calculateAdjDelta(intervalEndExcl);
				outInfo.adjDelta(adjDelta);
				long maxBucketSize = Math.abs(((long)data - prevPrice) /* 3dp */ * 100_000 * conversionRatio /* 3 dp */ / adjDelta) /* 5 dp */ ; /* 8 dp */
				outInfo.maxBucketSize(maxBucketSize);
				long adjMaxBucketSize = maxBucketSize * deltaAllowance / 1000;
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
		
		if (!greeks.isReady() || minWeighted == LongInterval.NULL_INTERVAL_BEGIN_VALUE  || targetSpread == Integer.MAX_VALUE || derivAskPrice == LongInterval.NULL_DATA_VALUE){
			return ViolationType.NO_VIOLATION;
		}
		
		// Find the interval that contains 'end - 1', deduce a theoAsk from the interval.data, then check theoAsk against derivAskPrice
		ObjectBidirectionalIterator<Entry> iterator = existingIntervals.searchExactAndIterate(maxWeighted - 1);
		if (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
			int data = (int)((interval.getLongKey() & NonOverlappingLongIntervalTreeSet.HIGHER_ORDER_MASK) >>> 32);
			if (maxWeighted >= intervalBegin && maxWeighted <= intervalEndExclusive){
				int dataInTick = spreadTable.priceToTick(data);
				int theoAsk = spreadTable.tickToPrice(dataInTick + targetSpread);
				if (derivAskPrice < theoAsk){
					return ViolationType.DOWN_VOL;
				}
				return ViolationType.NO_VIOLATION;
			}
			else {
				long adjDelta = greeks.calculateAdjDelta(intervalEndExclusive);
				// (6 + 5 + 3) - (3 + 8) = 3
				int theoBid = data + (int)((maxWeighted - intervalEndExclusive) * adjDelta * this.checkPricingAllowance / ((long)this.conversionRatio * 100_000_000));
				SpreadTableDetails details = spreadTable.detailsAtPrice(theoBid);
				int dataInTick = 0;
				int theoAsk = -1;
				if (maxWeighted < intervalEndExclusive) {
					theoBid -= (theoBid % details.spread());
					dataInTick = spreadTable.priceToTick(theoBid);
					theoAsk = spreadTable.tickToPrice(dataInTick + targetSpread);
					if (derivAskPrice < theoAsk){
						return ViolationType.DOWN_VOL;
					}
				}
				
				while (iterator.hasNext()){
					interval = iterator.next();
					intervalEndExclusive = interval.getLongKey() & NonOverlappingLongIntervalTreeSet.LOWER_ORDER_MASK;
					if (maxWeighted < intervalEndExclusive) {
						data = (int)((interval.getLongKey() & NonOverlappingLongIntervalTreeSet.HIGHER_ORDER_MASK) >>> 32);

						adjDelta = greeks.calculateAdjDelta(intervalEndExclusive);
						// (6 + 5 + 3) - (3 + 8) = 3
						theoBid = data + (int)((maxWeighted - intervalEndExclusive) * adjDelta * this.checkPricingAllowance / ((long)this.conversionRatio * 100_000_000));
						details = spreadTable.detailsAtPrice(theoBid);
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

		return ViolationType.NO_VIOLATION;		
	}

	@Override
	public long calculateUndSpotChange(long refBegin, long refEndExcl, int refDerivPrice, int derivPrice /* T+1 - T */, ObservedGreeks greeks){
		long changeInDeriv = derivPrice - refDerivPrice;
		long changeInUndSpot = (long)(changeInDeriv) * this.conversionRatio * 100_000 / greeks.delta();
		if (greeks.gamma() == 0){
			return changeInUndSpot; 
		}
		if (changeInDeriv != 0 && Math.abs(changeInUndSpot) * greeks.gamma() > 1_000_000L){
			long adjDelta = greeks.calculateAdjDelta(refEndExcl);
//			return (long)((- adjDelta /* 5p */ - Math.sqrt(adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * greeks.gamma() /* 5dp */ * this.conversionRatio /* 3 dp*/ * (derivPrice - refDerivPrice) /* 3 dp */ / 10)) * 1000_000 )/greeks.gamma() /* 5dp */;
			long valueToBeSqrt = adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * greeks.gamma() /* 5dp */ * this.conversionRatio /* 3 dp*/ * (changeInDeriv) /* 3 dp */ / 10;
			if (valueToBeSqrt >= 0){
				return (long)((- adjDelta /* 5p */ - Math.sqrt(valueToBeSqrt)) * 1000_000 )/greeks.gamma() /* 5dp */;
			}
			else {
				return INVALID_UND_SPOT_CHANGE;
			}
		}
		return 0;
	}
	
	@Override
	public long calculateUndSpotChange(long delta, int changeInDeriv /* T+1 - T */, ObservedGreeks greeks, long partialCalculateValue){
		long changeInUndSpot = (long)(changeInDeriv) * this.conversionRatio * 100_000 / greeks.delta();
		if (greeks.gamma() == 0){
//			LOG.info("Calculated calculateUndSpotChange with zero gamma - put");
			return changeInUndSpot; 
		}
		// Math.abs(changeInUndSpot) * greeks.gamma() / 1_00_000_000_000L > 0.00001		
		if (changeInDeriv != 0 && Math.abs(changeInUndSpot) * greeks.gamma() > 1_000_000L){
//			long result = (long)((- delta /* 5p */ - Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp*/ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000) /greeks.gamma() /* 5dp */;
//			return result;
			long valueToBeSqrt = delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp*/ * (changeInDeriv) /* 3 dp */ / 5000;
			if (valueToBeSqrt >= 0){
				//		LOG.info("Calculated calculateUndSpotChange - put [delta:{}, partialCalculateValue:{}, gamma:{}, changeInDeriv:{}, changeInSpot:{}]", 
				//				delta,
				//				partialCalculateValue, 
				//				greeks.gamma(),
				//				changeInDeriv,
				//				result);
				return (long)((- delta /* 5p */ - Math.sqrt(valueToBeSqrt)) * 1_000_000_000) /greeks.gamma() /* 5dp */;
			}
			else {
				return INVALID_UND_SPOT_CHANGE;
			}
		}
		return 0;
	}

	@Override
	public int getAdjacentBuckets(int current, int begin, int end, ObjectArrayList<ExtrapolatingInterval> outList){
		int index = 0;
		for (int i = end; i >= begin; i--){
			outList.get(index++).init(i);
		}
		return index;
	}
}
