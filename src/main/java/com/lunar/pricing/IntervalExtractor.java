package com.lunar.pricing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.EventHandler;
import com.lunar.pricing.BucketPricer.LongTimedPrice;
import com.lunar.util.ImmutableLongInterval;
import com.lunar.util.LongInterval;
import com.lunar.util.ObjectCircularBuffer;

class IntervalExtractor implements ObjectCircularBuffer.CancellableEventHandler<BucketPricer.LongTimedPrice>{
	static final Logger LOG = LogManager.getLogger(IntervalExtractor.class);
	
	private final ImmutableLongInterval intervalWrapper;
	private final LongInterval interval;
	private long expectedLagNs;
	private long lastPrice;
	private boolean isLastPriceObservedWhenTightSpread;
	private long lastPriceChangedTimeNs;
	private long earliestEffectiveTimeNs;
	private long cutoffTimeNs;
	private long derivPriceTimeNs;
	private final EventHandler<BucketPricer.LongTimedPrice> flusher;

	IntervalExtractor(long expectedLagNs) {
		interval = LongInterval.of();
		intervalWrapper = ImmutableLongInterval.of(interval);
		this.expectedLagNs = expectedLagNs;
		this.flusher = new EventHandler<BucketPricer.LongTimedPrice>() {
			@Override
			public void onEvent(LongTimedPrice event, long sequence, boolean endOfBatch) throws Exception {
				if (lastPrice != event.price()){
					lastPriceChangedTimeNs = event.ts();
				}
				lastPrice = event.price();
				isLastPriceObservedWhenTightSpread = event.observedWhenTightSpread();
			}
		};
	}

	public EventHandler<BucketPricer.LongTimedPrice> flusher(){
		return flusher;
	}
	
	public IntervalExtractor init(long ts, int derivPrice, long derivPriceTimeNs){
		this.interval.clear();
		this.cutoffTimeNs = ts - expectedLagNs;
		this.derivPriceTimeNs = derivPriceTimeNs;
		this.interval.data(derivPrice);
		this.interval.begin(LongInterval.NULL_INTERVAL_BEGIN_VALUE);
		this.interval.endExclusive(LongInterval.NULL_INTERVAL_END_VALUE);
		this.earliestEffectiveTimeNs = BucketPricer.NULL_TIME_NS;
		return this;
	}

	public IntervalExtractor lastPrice(long lastPrice, boolean isLastPriceObservedWhenTightSpread){
		this.lastPrice = lastPrice;
		this.isLastPriceObservedWhenTightSpread = isLastPriceObservedWhenTightSpread;
		LOG.trace("Reset lastPrice [{}]", lastPrice);
		return this;
	}

	public IntervalExtractor expectedLagNs(long expectedLagNs){
		if (this.expectedLagNs != expectedLagNs){
			this.expectedLagNs = expectedLagNs;
			LOG.debug("Set expected lag [{}]", expectedLagNs);
		}
		return this;
	}
	
	public IntervalExtractor lastPriceChangedTimeNs(long lastPriceChangedTimeNs){
		this.lastPriceChangedTimeNs = lastPriceChangedTimeNs;
		return this;
	}

	ImmutableLongInterval interval(){
		return intervalWrapper;
	}

	long lastPrice(){
		return lastPrice;
	}

	long lastPriceChangedTimeNs(){
		return lastPriceChangedTimeNs;
	}

	
	
	
	/**
	 * Goal is to take a price only if it hasn't changed for more than expectedLagNs time
	 * The process should stop as soon as the cutoff time is reached.
	 */
	@Override
	public boolean onEvent(LongTimedPrice event, long sequence, boolean endOfBatch) throws Exception {
//		LOG.info("Processing: [eventTs:{}, cutoffTimeNs:{}, diff:{}, data:{}, lastPrice:{}, lastPriceChangedTime:{}]", event.ts, cutoffTimeNs, cutoffTimeNs - event.ts, 
//				event.price,
//				lastPrice,
//				lastPriceChangedTimeNs);
		if (lastPriceChangedTimeNs != BucketPricer.NULL_TIME_NS && isLastPriceObservedWhenTightSpread && event.ts > derivPriceTimeNs){
			long duration = event.ts() - lastPriceChangedTimeNs;
			if (duration >= expectedLagNs){
				if (lastPrice < interval.begin()){
					interval.begin(lastPrice);
					if (interval.endExclusive() == LongInterval.NULL_INTERVAL_END_VALUE){
						interval.endExclusive(lastPrice + 1);
					}
				}
				if (lastPrice >= interval.endExclusive()){
					interval.endExclusive(lastPrice + 1);
				}
				interval.last(lastPrice);
			}
		}

		if (lastPrice != event.price()){
			lastPriceChangedTimeNs = event.ts();
		}

		lastPrice = event.price();
		isLastPriceObservedWhenTightSpread = event.observedWhenTightSpread();

		//LOG.debug("Set lastPrice [{}]", lastPrice);

		if (event.ts >= cutoffTimeNs){
			//LOG.info("Processed: [firstTickAtOrAfterCutoffTimeNs:{}, cutoffTimeNs:{}, diff:{}, data:{}]", event.ts, cutoffTimeNs, cutoffTimeNs - event.ts, event.price);
			earliestEffectiveTimeNs = event.ts + this.expectedLagNs;
			return false;
		}

		//return (event.ts() <= cutoffTimeNs);
		return true;
	}

	public long recordedEarliestEffectiveTimeNs() {
		return earliestEffectiveTimeNs;
	}		
}