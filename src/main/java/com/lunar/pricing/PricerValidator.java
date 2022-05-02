package com.lunar.pricing;

import com.lunar.pricing.BucketPricer.BucketSizeInfo;
import com.lunar.pricing.BucketPricer.ViolationType;
import com.lunar.util.LongInterval;
import com.lunar.util.NonOverlappingLongIntervalTreeSet;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface PricerValidator {
	static final int MAX_NUM_BUCKET_APPROXIMATION = 5;
	public static final Long INVALID_UND_SPOT_CHANGE = Long.MIN_VALUE;
	
	ViolationType validateDerivPrice(int tickDirection, int derivBidPrice, int derivAskPrice, boolean isDerivTight, 
			int lastDerivBidPrice,
			int lastDerivAskPrice,
			int maxDerivBidSinceLastTightDerivPrice,
			int minDerivAskSinceLastTightDerivPrice);
	ViolationType validatePriceConsistency(long begin, long endExcl, int data, long theoBucketSize, LongInterval refInterval);
	ViolationType validateBucketSize(ObservedGreeks greeks, long intervalBegin, long intervalEndExcl, int data, boolean extendEnd, BucketSizeInfo outInfo);
	void calculateBucketSize(ObservedGreeks greeks, long intervalBegin, long intervalEndExcl, int data, BucketSizeInfo outInfo);
	/**
	 * Throw exception if input interval is empty
	 * @param begin
	 * @param endExcl
	 * @param data
	 * @param dataInTick
	 * @param o2
	 * @param maxBucketSize
	 * @return ViolationType.NO_VIOLATION if data = input.data
	 */
	ViolationType validateBucketDistance(long begin, long endExcl, int data, long theoBucketSize, LongInterval refInterval);
	
	/**
	 * Main intention of this check is to find out a change in vol when issuer widens warrant's spread
	 * @param begin
	 * @param endExcl
	 * @param derivAskPrice
	 * @param existingIntervals
	 * @return
	 */
	ViolationType validateDerivAskPrice(ObservedGreeks greeks, long begin, long endExcl, int derivAskPrice, NonOverlappingLongIntervalTreeSet existingIntervals);
	
	boolean shiftTheoBucket(ObservedGreeks greeks, long currentUndSpot, int currentDerivBid, long minBegin, long maxEndExclusive, LongInterval outInterval);
	
	long calculateUndSpotChange(long refBegin, long refEndExcl, int refDerivPrice, int derivPrice /* T+1 - T */, ObservedGreeks greeks);
	long calculateUndSpotChange(long delta, int changeInDeriv, ObservedGreeks greeks, long partialCalculateValue);
	void targetSpreadInTick(int targetSpread);
	int getAdjacentBuckets(int current, int begin, int end, ObjectArrayList<ExtrapolatingInterval> outList);
}
