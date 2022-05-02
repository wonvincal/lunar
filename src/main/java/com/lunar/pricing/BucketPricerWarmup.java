package com.lunar.pricing;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.TriggerInfo;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.pricing.BucketPricer.ViolationType;
import com.lunar.util.LongInterval;

public class BucketPricerWarmup {
	static final Logger LOG = LogManager.getLogger(BucketPricerWarmup.class);
    private static final int COMPILE_WARMUP_COUNTS = 20000; // default compile threshold is 10000
    private static final int PREDICTIVE_WARMUP_COUNTS = 120000;
    private static Greeks greeks;
    private static LongInterval outInterval;
    private static LongInterval outIntervalForDeriv;
    
    private static boolean run(BucketPricer pricer, AtomicLong count){
    	long nanoOfDay = System.nanoTime();
    	boolean result = false;
    	result = pricer.observeGreeks(nanoOfDay, greeks);
    	TriggerInfo triggerInfo = TriggerInfo.of();
    	
    	pricer.resetAndSetTargetSpreadInTick(1);
    	
    	// refSpot is 3dp
    	long undSpot6Dp = greeks.refSpot() * 1000L;
    	if (pricer.observeUndTick(nanoOfDay, undSpot6Dp, true, triggerInfo, outInterval) != ViolationType.NO_VIOLATION){
    		throw new IllegalStateException();
    	}
    	
		// Observe underlying tick, return NO_VIOLATION
    	if (pricer.observeDerivTick(nanoOfDay, 100, 101, 100, 101, 1, triggerInfo) != ViolationType.NO_VIOLATION){
    		throw new IllegalStateException();
    	}
    	
    	if (pricer.observeUndTick(nanoOfDay + TimeUnit.MILLISECONDS.toNanos(100), undSpot6Dp, true, triggerInfo, outInterval) != ViolationType.NO_VIOLATION){
    		throw new IllegalStateException();
    	}

    	if (pricer.observeUndTick(nanoOfDay + TimeUnit.MILLISECONDS.toNanos(200),
    			undSpot6Dp + 25, true, triggerInfo, outInterval) != ViolationType.NO_VIOLATION){
    		throw new IllegalStateException();
    	}

    	if (pricer.observeDerivTick(nanoOfDay + TimeUnit.MILLISECONDS.toNanos(200), 101, 102, 101, 102, 1, triggerInfo) == ViolationType.NO_VIOLATION){
        	if (pricer.observeUndTick(nanoOfDay + TimeUnit.MILLISECONDS.toNanos(300),
        			undSpot6Dp + 25, true, triggerInfo, outInterval) != ViolationType.NO_VIOLATION){
        		throw new IllegalStateException();
        	}
    	}

    	if (pricer.getIntervalByDerivPrice(100, outIntervalForDeriv)){
    		count.incrementAndGet();
    	}
    	
    	if (pricer.getIntervalByDerivPriceWithExtrapolation(100, outIntervalForDeriv)){
    		count.incrementAndGet();
    	}
    	
    	if (outInterval.isEmpty()){
    		pricer.reset(nanoOfDay);
    	}
    	else{
    		pricer.resetAndRegister(nanoOfDay, outInterval);
    	}
    	pricer.clear();
    	return result;
    }
    
	public static void warmup(){
        LOG.info("Warming up BucketPricer");
        
        long undSecSid = 1;
		long derivSecSid = 26525;
		int conversionRatio = 10;

		greeks = Greeks.of(derivSecSid);
    	greeks.delta(45229);
    	greeks.gamma(12411);
    	greeks.refSpot(11650);
    	greeks.vega(2222);
    	greeks.impliedVol(1111);
        
    	outInterval = LongInterval.of();
    	outIntervalForDeriv = LongInterval.of();
    	
		BucketPricer callBucketPricer = BucketPricer.of(undSecSid, derivSecSid, PutOrCall.CALL, SpreadTableBuilder.get(SecurityType.WARRANT), conversionRatio);
		BucketPricer putBucketPricer = BucketPricer.of(undSecSid, derivSecSid, PutOrCall.PUT, SpreadTableBuilder.get(SecurityType.WARRANT), conversionRatio);
    	int successCount = 0;
    	int failCount = 0;
        try {
        	BucketPricer[] pricers = { callBucketPricer, putBucketPricer };
        	AtomicLong count = new AtomicLong();
        	for (BucketPricer pricer : pricers){
                for (int i = 0; i < COMPILE_WARMUP_COUNTS; i++) {
                	if (run(pricer, count)){
    					successCount++;
    				}
    				else{
    					failCount++;
    				}
                }
        	}
            LOG.info("Done warming up BucketPricer [successCount:{}, failCount:{}]", successCount, failCount);

            // Warmup for call and put
        	for (int i = 0; i < PREDICTIVE_WARMUP_COUNTS; i++) {
        	}
            LOG.info("Done predictive warming up BucketPricer [successCount:{}, failCount:{}]", successCount, failCount);
        }
        catch (final Exception e) {
            LOG.error("Error encountered while trying to warmup BucketPricer", e);
        }
        LOG.info("Done warming up BucketPricer [successCount:{}, failCount:{}]", successCount, failCount);
	}
}
