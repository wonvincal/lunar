package com.lunar.pricing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.pricing.BucketPricer.BucketSizeInfo;
import com.lunar.pricing.BucketPricer.ViolationType;
import com.lunar.util.LongInterval;
import com.lunar.util.NonOverlappingLongIntervalTreeSet;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

public class BucketPricerTest {
	static final Logger LOG = LogManager.getLogger(BucketPricerTest.class);
	private BucketPricer pricer;
	private long undSecSid;
	private long derivSecSid;
	private PutOrCall putOrCall;
	private int conversionRatio;
	private long discardPeriodNs;
	private long issuerMaxLagNs;
	private int bucketSizeAllowance;
	private static SpreadTable spreadTable; 
	private static LongInterval[] outIntervals = new LongInterval[64];
	
	@BeforeClass
	public static void beforeClass(){
		spreadTable = SpreadTableBuilder.get(SecurityType.WARRANT);
		for (int i = 0; i < outIntervals.length; i++){
			outIntervals[i] = LongInterval.of();
		}
	}
	
	@Before
	public void setup(){
		int pricerType = BucketPricer.PRICE_TYPE_WEIGHTED;
		undSecSid = 1001;
		derivSecSid = 2001;
		putOrCall = PutOrCall.CALL;
		conversionRatio = 10000;
		discardPeriodNs = 100_000_000; // 100 ms
		issuerMaxLagNs = discardPeriodNs;
		bucketSizeAllowance = 1200;
		pricer = BucketPricer.of(pricerType,
				undSecSid, 
				derivSecSid, 
				putOrCall, 
				SpreadTableBuilder.get(SecurityType.WARRANT),
				conversionRatio,
				issuerMaxLagNs,
				bucketSizeAllowance);
	}
	
	@After
	public void clear(){
		Arrays.stream(outIntervals).forEach(i -> i.clear());
	}
	
	@Test
	public void testCreate(){
		int pricerType = BucketPricer.PRICE_TYPE_MID;
		long undSecSid = 1001;
		long derivSecSid = 2001;
		PutOrCall putOrCall = PutOrCall.CALL;
		int conversionRatio = 10000;
		long issuerMaxLagNs = 100000;
		int bucketSizeAllowance = 1100;
		BucketPricer pricer = BucketPricer.of(pricerType, undSecSid, derivSecSid, putOrCall, SpreadTableBuilder.get(SecurityType.WARRANT), conversionRatio, issuerMaxLagNs, bucketSizeAllowance);
		assertNotNull(pricer);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCreateWithInvalidPutCall(){
		int pricerType = BucketPricer.PRICE_TYPE_MID;
		long undSecSid = 1001;
		long derivSecSid = 2001;
		PutOrCall putOrCall = PutOrCall.NULL_VAL;
		int conversionRatio = 10000;
		long issuerMaxLagNs = 100000;
		int bucketSizeAllowance = 1100;

		BucketPricer pricer = BucketPricer.of(pricerType, undSecSid, derivSecSid, putOrCall, SpreadTableBuilder.get(SecurityType.WARRANT), conversionRatio, issuerMaxLagNs, bucketSizeAllowance);
		assertNotNull(pricer);
	}

	@Test
	public void givenEmptyWhenGreeksArrivedThenGreeksStored(){
		Greeks greeks = Greeks.of(derivSecSid);
		int gamma = 5555;
		int impliedVol = 4444;
		int refSpot = 3333;
		int vega = 2222;
		int delta = 1111;
		greeks.gamma(gamma)
			.impliedVol(impliedVol)
			.refSpot(refSpot)
			.vega(vega)
			.delta(delta);
		assertTrue(pricer.observeGreeks(System.nanoTime(), greeks));
		ObservedGreeks observedGreeks = pricer.observedGreeks();
		assertEquals(observedGreeks.impliedVol(), greeks.impliedVol());
		assertEquals(observedGreeks.gamma(), greeks.gamma());
		assertEquals(observedGreeks.refSpot(), greeks.refSpot());
		assertEquals(observedGreeks.vega(), greeks.vega());
		assertEquals(observedGreeks.delta(), greeks.delta());
	}
	
	@Test
	public void givenEmptyWhenDerivTickAndNonMMTightArrivedThenNotStore(){
		long nanoOfDay = System.nanoTime();
		int bid = 100;
		int ask = 101;
		int mmBid = 99;
		int mmAsk = 102;
		boolean mmTightSpread = false;
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(nanoOfDay, bid, ask, mmBid, mmAsk, mmTightSpread);
		assertEquals(result, ViolationType.NO_VIOLATION);
		assertEquals(mmBid, pricer.lastDerivBidPrice());
		assertEquals(mmAsk, pricer.lastDerivAskPrice());
		assertEquals(mmAsk, pricer.lastTightDerivAskPrice());
		assertEquals(mmBid, pricer.lastTightDerivBidPrice());
		assertEquals(0, pricer.observedIntervalsByDerivPrice().size());
		assertTrue(pricer.observedIntervals().isEmpty());
	}

	@Test
	public void givenEmptyWhenDerivTickAndMMTightArrivedThenStore(){
		long nanoOfDay = System.nanoTime();
		int bid = 100;
		int ask = 101;
		int mmBid = 99;
		int mmAsk = 102;
		boolean mmTightSpread = true;
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(nanoOfDay, bid, ask, mmBid, mmAsk, mmTightSpread);
		/* Extra observe to circumvent bug */
		result = pricer.observeDerivTickWithoutTriggerInfo(nanoOfDay, bid, ask, mmBid, mmAsk, mmTightSpread);
		assertEquals(ViolationType.NO_VIOLATION, result);
		assertEquals(mmBid, pricer.lastDerivBidPrice());
		assertEquals(mmAsk, pricer.lastDerivAskPrice());
		assertEquals(mmAsk, pricer.lastTightDerivAskPrice());
		assertEquals(mmBid, pricer.lastTightDerivBidPrice());
		assertEquals(0, pricer.observedIntervalsByDerivPrice().size());
		assertTrue(pricer.observedIntervals().isEmpty());
	}

	@Test
	public void givenEmptyWhenUndTickArrivesThenNotStored(){
		long undSpot = 1000001;
		long observedTs = System.nanoTime();
		ViolationType result = pricer.observeUndTickWithoutTriggerInfo(observedTs, undSpot);
		assertEquals(result, ViolationType.NO_VIOLATION);
		
		long undSpot2 = 1000020;
		long observedTs2 = System.nanoTime();
		result = pricer.observeUndTickWithoutTriggerInfo(observedTs2, undSpot2);
		
		assertFalse(pricer.observedUndSpots().isEmpty());
		assertTrue(pricer.observedIntervals().isEmpty());
		
		LOG.info("{}", pricer.toString());
	}
	
	@Test
	public void givenTightDerivAvailWhenReceiveUndTicksThenNothingHappen(){
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(System.nanoTime(), 100, 101, 99, 102, true);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(System.nanoTime(), 1000000);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(System.nanoTime(), 1000001);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(System.nanoTime(), 1000002);
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		assertEquals(1000000, pricer.minObservedUndSpotSinceLastReset());
		assertEquals(1000002, pricer.maxObservedUndSpotSinceLastReset());
		assertEquals(3, pricer.observedUndSpots().size());
	}
	
	@Test
	public void givenTightDerivAvailWhenReceiveUndTicksExceedingDiscardPeriodThenAddIntervalCaseOnePoint(){
		long time = 9000000000l;
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time, 1000000);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs + 1, 1000002);
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		assertEquals(1000000, pricer.minObservedUndSpotSinceLastReset());
		assertEquals(1000002, pricer.maxObservedUndSpotSinceLastReset());
		assertEquals(1, pricer.observedUndSpots().size());
		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
		
		LOG.info("{}", pricer.toString());
	}

	@Test
	public void givenTightDerivAvailWhenReceiveUndTicksExceedingDiscardPeriodThenAddIntervalCaseTwoPoints(){
		long time = 9000000000l;
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000000);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + 2, 1000001);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs + 3, 1000002);
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		// Expect [1000000, 1000002) -> [ 99 ]
		assertEquals(1000000, pricer.minObservedUndSpotSinceLastReset());
		assertEquals(1000002, pricer.maxObservedUndSpotSinceLastReset());
		assertEquals(1, pricer.observedUndSpots().size());
		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
	}

	@Test
	public void givenTightDerivAvailWhenReceiveUndTicksExceedingDiscardPeriodThenAddIntervalCaseMoreThanTwoPoints(){
		long time = 9000000000l;
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000000);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + 2, 1000001);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + 3, 1000002);
		assertEquals(ViolationType.NO_VIOLATION, result);

		result = pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs + 4, 1000003);
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		// Expect [1000000, 1000003) -> [ 99 ]
		assertEquals(1000000, pricer.minObservedUndSpotSinceLastReset());
		assertEquals(1000003, pricer.maxObservedUndSpotSinceLastReset());
		assertEquals(1, pricer.observedUndSpots().size());
		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
	}

	@Test
	public void givenEmptyWhenReceiveTicksAndResetThenEmpty(){
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000000);
		pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs + 2, 1000001);

		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
		pricer.reset(time + discardPeriodNs + 3);
		assertEquals(0, pricer.observedIntervals().size());
		assertEquals(0, pricer.observedIntervalsByDerivPrice().size());
	}
	
	@Test
	public void givenDerivAndUndTicksAvailWhenDiffTightDerivPriceArrivesAtThenRemoveObservedUndTicks(){
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1003); // Lock in an interval
		
		pricer.observeDerivTickWithoutTriggerInfo(time + 3 + discardPeriodNs, 100, 101, 100, 103, true);
		assertEquals(0, pricer.observedUndSpots().size());
		assertEquals(1003, pricer.extractor().lastPrice());
		assertEquals(time + 2 + discardPeriodNs, pricer.extractor().lastPriceChangedTimeNs());
		
		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
	}

	@Test
	public void givenDerivPriceAvailAndReceivedUndSpotWhenDerivPriceArrivesThenValidate(){
		long time = 9000000000l;
		observeGreeks(pricer, time);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1003); // Lock in an interval - current time must be greater than cutoff
		
		pricer.observeDerivTickWithoutTriggerInfo(time + 3 + discardPeriodNs, 100, 101, 100, 103, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 4 + discardPeriodNs + 1, 1004);
		pricer.observeUndTickWithoutTriggerInfo(time + 5 + discardPeriodNs, 1005);
		ViolationType result = pricer.observeUndTickWithoutTriggerInfo(time + 6 + 2 * discardPeriodNs, 1006); // Lock in an interval
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		assertEquals(2, pricer.observedIntervals().size());
		assertEquals(2, pricer.observedIntervalsByDerivPrice().size());
	}

	@Test
	public void givenIntervalExists(){
		LongInterval outInterval = LongInterval.of(); 		
		
		assertFalse(pricer.getIntervalByDerivPriceWithExtrapolation(99, outInterval));
		assertTrue(outInterval.isEmpty());

		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs, 1001);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs, 1002);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 * discardPeriodNs, 1005);
		pricer.observeUndTickWithoutTriggerInfo(time + 4 * discardPeriodNs, 1010);

		assertFalse(pricer.getIntervalByDerivPriceWithExtrapolation(100, outInterval));

	}
	
	@Test
	public void givenIntervalExistsWhenUndTickArrivesThenKeepMergingWithExistingInterval(){
		LongInterval outInterval = LongInterval.of(); 		
		assertFalse(pricer.getIntervalByDerivPrice(99, outInterval));
		assertTrue(outInterval.isEmpty());
		
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1001);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1002);
		pricer.observeUndTickWithoutTriggerInfo(time + 4 + 3 * discardPeriodNs, 1005);
		pricer.observeUndTickWithoutTriggerInfo(time + 5 + 4 * discardPeriodNs, 1010);
		
		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
		assertTrue(pricer.getIntervalByDerivPrice(99, outInterval));
		assertInterval(1000, 1006, 99, outInterval);
		
		assertTrue(pricer.getIntervalByUndSpot(1005, outInterval));
		assertInterval(1000, 1006, 99, outInterval);
		
		assertTrue(pricer.getIntervalByUndSpot(1000, outInterval));
		assertInterval(1000, 1006, 99, outInterval);
	}

	@Test
	public void givenIntervalExistsWhenDerivAndUndTickArrivesAndOverlapsWithExistingIntervalWithDifferentDerivPriceThenReturnPriceOverlapped(){
		LongInterval outInterval = LongInterval.of(); 
		assertFalse(pricer.getIntervalByDerivPrice(99, outInterval));
		assertTrue(outInterval.isEmpty());
		
		long time = 9000000000l;
		observeGreeks(pricer, time);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true); /* To circumvent bug */
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1001);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1002);
		pricer.observeUndTickWithoutTriggerInfo(time + 4 + 3 * discardPeriodNs, 1005);
		pricer.observeUndTickWithoutTriggerInfo(time + 5 + 4 * discardPeriodNs, 1010);
		assertEquals(1, pricer.observedIntervals().size());
		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
		
		pricer.observeDerivTickWithoutTriggerInfo(time + 6 + 4 * discardPeriodNs, 100, 101, 100, 103, true);
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 7 + 5 * discardPeriodNs, 1000));
		assertEquals(ViolationType.UP_VOL, pricer.observeUndTickWithoutTriggerInfo(time + 8 + 6 * discardPeriodNs, 1001)); // Same underlying spot is mapped to a higher deriv price
	}

	@Test
	public void givenIntervalExistsWhenUndTickArrivesAndExtendBeginningOFExistingIntervalThenReturnOK(){
		LongInterval outInterval = LongInterval.of(); 
		assertFalse(pricer.getIntervalByDerivPrice(99, outInterval));
		assertTrue(outInterval.isEmpty());
		
		long time = 9000000000l;
		observeGreeks(pricer, time);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time, 1000000);
		pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs, 10010000);
//		pricer.observeUndTick(time + 2 * discardPeriodNs, 10020000);
//		pricer.observeUndTick(time + 3 * discardPeriodNs, 10050000);
//		pricer.observeUndTick(time + 4 * discardPeriodNs, 9900000);
//		assertEquals(1, pricer.observedIntervals().size());
//		assertEquals(1, pricer.observedIntervalsByDerivPrice().size());
//		
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 5 * discardPeriodNs, 9900000));
//		pricer.observedIntervalsByDerivPrice().get(99);
	}

	@Test
	public void givenIntervalExistsWhenNewUndTickArrivsButDoesNotExtendExistingIntervalThenReturnOK(){
		LongInterval outInterval = LongInterval.of(); 
		assertFalse(pricer.getIntervalByDerivPrice(99, outInterval));
		assertTrue(outInterval.isEmpty());
		
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + discardPeriodNs, 1001);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs, 1002);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 * discardPeriodNs, 1005);
		assertEquals(1, pricer.observedIntervals().size());
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 4 * discardPeriodNs, 1001));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 4 * discardPeriodNs + 100, 1003));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 5 * discardPeriodNs + 100, 1004));

	}
	
	@Test
	public void testScenarioOne(){
		int pricerType = BucketPricer.PRICE_TYPE_WEIGHTED;
		undSecSid = 1001;
		derivSecSid = 2001;
		putOrCall = PutOrCall.CALL;
		conversionRatio = 15000;
		discardPeriodNs = 100_000_000; // 100 ms
		issuerMaxLagNs = discardPeriodNs;
		bucketSizeAllowance = 1100;
		BucketPricer pricer = BucketPricer.of(pricerType,
				undSecSid, 
				derivSecSid, 
				putOrCall, 
				SpreadTableBuilder.get(SecurityType.WARRANT),
				conversionRatio,
				discardPeriodNs,
				bucketSizeAllowance);		
		
		long time = 9000000000l;
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		
		Greeks greeks = Greeks.of(derivSecSid);
		int gamma = 500;
		int impliedVol = 4444;
		int refSpot = 95100;
		int vega = 2222;
		int delta = 50000;
		greeks.gamma(gamma)
			.impliedVol(impliedVol)
			.refSpot(refSpot)
			.vega(vega)
			.delta(delta);
		assertTrue(pricer.observeGreeks(time, greeks));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(1), 95025000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(2), 95049950));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(2950), 95066667));
		
		LongInterval searchResult = LongInterval .of(); 
		pricer.getIntervalByDerivPrice(100, searchResult);
		assertEquals(95025000, searchResult.begin());
		assertEquals(95049951, searchResult.endExclusive());
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(2950), 101, 102, 101, 102, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(4), 95066667));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(5), 95085454));
		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(6), 95085454));
		pricer.getIntervalByDerivPrice(101, searchResult);
		assertEquals(95055022, searchResult.begin());
		assertEquals(95079966, searchResult.endExclusive());
		
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(7), 95085667));
		assertEquals(5, pricer.observedIntervals().size());
		assertEquals(5, pricer.observedIntervalsByDerivPrice().size());
		
		LOG.info(pricer.toString());
	}
	
	@Test
	public void testScenarioTenDropVol(){
		int pricerType = BucketPricer.PRICE_TYPE_WEIGHTED;
		undSecSid = 1001;
		derivSecSid = 2001;
		putOrCall = PutOrCall.CALL;
		conversionRatio = 15000;
		discardPeriodNs = 100_000_000; // 100 ms
		issuerMaxLagNs = discardPeriodNs;
		bucketSizeAllowance = 1100;
		BucketPricer pricer = BucketPricer.of(pricerType,
				undSecSid, 
				derivSecSid, 
				putOrCall, 
				SpreadTableBuilder.get(SecurityType.WARRANT),
				conversionRatio,
				discardPeriodNs,
				bucketSizeAllowance);		
		
		long time = 9000000000l;

		Greeks greeks = Greeks.of(derivSecSid);
		int gamma = 500;
		int impliedVol = 4444;
		int refSpot = 95100;
		int vega = 2222;
		int delta = 50000;
		greeks.gamma(gamma)
			.impliedVol(impliedVol)
			.refSpot(refSpot)
			.vega(vega)
			.delta(delta);
		assertTrue(pricer.observeGreeks(time, greeks));

		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true)); /* To circumvent bug */
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(1), 95025000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(2), 95049950));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(2950), 95066667));

		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(3), 101, 102, 101, 102, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(4), 95066667));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(5), 95085455));
		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(6), 95085455));
		
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(7), 95041667));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(8), 100, 101, 100, 101, true));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(8050), 95071667));
//		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(10), 95071667));
	}	
	
	@Test
	public void testScenarioNineDropVol(){
		int pricerType = BucketPricer.PRICE_TYPE_WEIGHTED;
		undSecSid = 1001;
		derivSecSid = 2001;
		putOrCall = PutOrCall.CALL;
		conversionRatio = 15000;
		discardPeriodNs = 100_000_000; // 100 ms
		issuerMaxLagNs = discardPeriodNs;
		bucketSizeAllowance = 1100;
		BucketPricer pricer = BucketPricer.of(pricerType,
				undSecSid, 
				derivSecSid, 
				putOrCall, 
				SpreadTableBuilder.get(SecurityType.WARRANT),
				conversionRatio,
				discardPeriodNs,
				bucketSizeAllowance);		
		
		long time = 9000000000l;

		Greeks greeks = Greeks.of(derivSecSid);
		int gamma = 500;
		int impliedVol = 4444;
		int refSpot = 95100;
		int vega = 2222;
		int delta = 50000;
		greeks.gamma(gamma)
			.impliedVol(impliedVol)
			.refSpot(refSpot)
			.vega(vega)
			.delta(delta);
		assertTrue(pricer.observeGreeks(time, greeks));
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true)); /* To circumvent bug */
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(1), 95025000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(2), 95049950));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(2950), 95039950));

		assertEquals(ViolationType.DOWN_VOL, pricer.observeDerivTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(3), 99, 100, 99, 100, true));
		pricer.processDerivTickWithViolationCheck(time + TimeUnit.SECONDS.toNanos(3), 99, 100);
		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(4), 95049950));
		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(5), 95049950));
	}	
	
	@Test
	public void testScenarioFive(){
		int pricerType = BucketPricer.PRICE_TYPE_WEIGHTED;
		undSecSid = 1001;
		derivSecSid = 2001;
		putOrCall = PutOrCall.PUT;
		conversionRatio = 15000;
		discardPeriodNs = 100_000_000; // 100 ms
		issuerMaxLagNs = discardPeriodNs;
		bucketSizeAllowance = 1100;
		BucketPricer pricer = BucketPricer.of(pricerType,
				undSecSid, 
				derivSecSid, 
				putOrCall, 
				SpreadTableBuilder.get(SecurityType.WARRANT),
				conversionRatio,
				discardPeriodNs,
				bucketSizeAllowance);		
		
		long time = 9000000000l;
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		
		Greeks greeks = Greeks.of(derivSecSid);
		int gamma = 500;
		int impliedVol = 4444;
		int refSpot = 95100;
		int vega = 2222;
		int delta = 50000;
		greeks.gamma(gamma)
			.impliedVol(impliedVol)
			.refSpot(refSpot)
			.vega(vega)
			.delta(delta);
		assertTrue(pricer.observeGreeks(time, greeks));

		LongInterval searchResult = LongInterval.of();
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(1), 95095455));
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(2), 95066667));
		LOG.info(pricer.getIntervalByDerivPrice(100, searchResult));
		assertEquals(95095455, searchResult.begin());
		assertEquals(95095456, searchResult.endExclusive());
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(2950), 95049950));
		LOG.info(pricer.getIntervalByDerivPrice(100, searchResult));
		assertEquals(95066667, searchResult.begin());
		assertEquals(95095456, searchResult.endExclusive());
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 101, 102, 101, 102, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(4), 95049950));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(5), 95033000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(6), 95033000));
		LOG.info(pricer.getIntervalByDerivPrice(101, searchResult));
		assertEquals(95033000, searchResult.begin());
		assertEquals(95049951, searchResult.endExclusive());

		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.SECONDS.toNanos(7), 95091667));
		assertEquals(2, pricer.observedIntervals().size());
		assertEquals(2, pricer.observedIntervalsByDerivPrice().size());
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + TimeUnit.MILLISECONDS.toNanos(9001), 95041667));
		assertEquals(2, pricer.observedIntervals().size());
	}
	
	@Test
	public void givenIntervalExistsWhenDiffDerivTickWithSameUndTickArrivesThenOverlappingDetected(){
		long time = 9000000000l;
		observeGreeks(pricer, time);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true); /* To circumvent bug */
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1001);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1002); // Lock in an interval of [1000, 1002) -> 99
		
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time + 4 + 2 * discardPeriodNs, 100, 101, 100, 103, true);
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		result = pricer.observeUndTickWithoutTriggerInfo(time + 5 + 3 * discardPeriodNs, 1000); // Lock in [1002, 1003) -> 100
		assertEquals(ViolationType.NO_VIOLATION, result);
		
		result = pricer.observeUndTickWithoutTriggerInfo(time + 6 + 4 * discardPeriodNs, 1001); // Try to lock in [1000, 1003) -> 100, overlap with two intervals
		assertEquals(ViolationType.UP_VOL, result);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testExceedMaxUndSpot(){
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(System.nanoTime(), BucketPricer.MAX_INTERVAL_VALUE - 1));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(System.nanoTime(), BucketPricer.MAX_INTERVAL_VALUE));
		pricer.observeUndTickWithoutTriggerInfo(System.nanoTime(), BucketPricer.MAX_INTERVAL_VALUE + 1);
	}
	
	// -----------------------------
	// Validation related test cases
	// -----------------------------
	@Test
	public void givenDerivAndUndTicksAvailWhenUndTickWithOffSpotComesInThenViolationIsFound(){
		long time = 9000000000l;
		observeGreeks(pricer, time);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 900); // Lock in an interval at [1000 1001) -> 99

		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time + 3 + discardPeriodNs, 100, 101, 100, 103, true); // Deriv price changes from 99 to 100
//		ViolationType result = pricer.observeUndTickWithoutTriggerInfo(time + 4 + 2 * discardPeriodNs, 901); // [900 901) -> 100 
		assertEquals(ViolationType.UP_VOL, result);
	}

	@Test
	public void givenDerivAndUndTicksAvailWhenUndTickDropsAndDerivPriceUpThenViolationDetectedVolUpIsFound(){
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true); // To circumvent bug
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1005); 
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1004); // Lock in an interval at [1000 1006) -> 99
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 4, 1003));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 5, 1002));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 6, 1001));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 7, 1000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 8, 998));
		
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 9, 100, 101, 102, 105, true); 
		assertEquals(ViolationType.UP_VOL, result);
	}
	
	@Test
	public void givenDerivAndUndTicksAvailWhenUndTickIncAndDerivPriceDownThenViolationDetectedDownUpIsFound(){
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true);
		pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 99, 102, true); // To circumvent bug
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1005); 
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1006); // Lock in an interval at [1000 1006) -> 99
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 4, 1007));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 5, 1008));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 6, 1009));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 7, 1010));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 8, 1011));
		
		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 9, 100, 101, 96, 99, true); 
		assertEquals(ViolationType.DOWN_VOL, result);
	}

	@Test
	public void givenExistingIntervalsWhenUndTickAndFailBucketSizeCheckThenReturnViolation(){
		long time = 9000000000l;
		long lastTickTime = populate(pricer, time, 99, 100, true);
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(lastTickTime + 1 + pricer.discardPeriodNs(), 110572000));
//		assertEquals(ViolationType.BUCKET_TOO_BIG, pricer.observeUndTickWithoutTriggerInfo(lastTickTime + 2 + 2 * pricer.discardPeriodNs(), 110572000));
	}
	
	@Test
	public void testBucketSizeCalculationBucketTooBig(){
		BucketSizeInfo outInfo = new BucketSizeInfo();
		ObservedGreeks og = ObservedGreeks.of(pricer.derivSecSid()); 
		Greeks greeks = Greeks.of(derivSecSid);
		greeks.delta(50000); // 0.50
		greeks.gamma(10000); // 0.10
		greeks.refSpot(110550); // 110.55
		og.merge(System.nanoTime(), greeks);
		long intervalBegin = 110_500_000l;
		long intervalEndExcl = 110_650_000l;
		int data = 250;
		ViolationType result = pricer.priceValidator().validateBucketSize(og, intervalBegin, intervalEndExcl, data, false, outInfo);
		assertEquals(ViolationType.UP_VOL, result);
		LOG.info(outInfo);
		assertEquals(49500, outInfo.adjDelta());
		assertEquals(150000, outInfo.currentBucketSize());
		assertEquals(101010, outInfo.maxBucketSize());
		assertEquals(121212, outInfo.adjMaxBucketSize());
	}
	
	@Test
	public void testBucketSizeCalculationNoViolation(){
		BucketSizeInfo outInfo = new BucketSizeInfo();
		ObservedGreeks og = ObservedGreeks.of(derivSecSid);
		Greeks greeks = Greeks.of(derivSecSid);
		greeks.delta(50000); // 0.50
		greeks.gamma(10000); // 0.10
		greeks.refSpot(110550); // 110.55
		og.merge(System.nanoTime(), greeks);
		long intervalBegin = 110_500_000l;
		long intervalEndExcl = 110_650_000l;
		int data = 2500;
		ViolationType result = pricer.priceValidator().validateBucketSize(og, intervalBegin, intervalEndExcl, data, false, outInfo);
		assertEquals(ViolationType.NO_VIOLATION, result);
		LOG.info(outInfo);
		assertEquals(49500, outInfo.adjDelta());
		assertEquals(150000, outInfo.currentBucketSize());
		assertEquals(202020, outInfo.maxBucketSize());
		assertEquals(242424, outInfo.adjMaxBucketSize());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testBucketDistanceWithNoEmptyIntervalThenThrowException(){
		PricerValidator validator = CallPricerValidator.of(derivSecSid, conversionRatio, bucketSizeAllowance, spreadTable);
		
		long begin = 11050_000_000l;
		long endExcl = 11065_000_000l;
		int data = 2500;
		LongInterval o2 = LongInterval.of(); 
		long maxBucketSize = 20202020;
		ViolationType result = validator.validateBucketDistance(begin, endExcl, data, maxBucketSize, o2);
		assertEquals(ViolationType.NO_VIOLATION, result);
	}
	
	@Test
	public void giveIntervalsWithBucketDistanceThenNoViolationIsDetected(){
		PricerValidator validator = CallPricerValidator.of(derivSecSid, conversionRatio, bucketSizeAllowance, spreadTable);
		
		long begin = 11050_000l;
		long endExcl = 11070_000l;
		int data = 2500;
		int maxBucketSize = 20_000;
		
		LongInterval o2 = LongInterval.of(endExcl + 200_000, endExcl + 200_000, data + 100, maxBucketSize);
		ViolationType result = validator.validateBucketDistance(begin, endExcl, data, maxBucketSize, o2);
		assertEquals(ViolationType.NO_VIOLATION, result);
	}
	
	@Test
	public void giveIntervalsWithInconsistentBucketDistanceThenViolationIsDetected(){
		PricerValidator validator = CallPricerValidator.of(derivSecSid, conversionRatio, bucketSizeAllowance, spreadTable);
		
		long begin = 11040_000l;
		long endExcl = 11090_000l;
		int data = 2500;
		int maxBucketSize = 50_000;
		
		LongInterval o2 = LongInterval.of(endExcl + 5_000, endExcl + 10_000, data + 120, maxBucketSize);
		ViolationType result = validator.validateBucketDistance(begin, endExcl, data, maxBucketSize, o2);
		assertEquals(ViolationType.DOWN_VOL, result);
	}

	@Test
	public void givenPutAndIntervalsWithInconsistentBucketDistanceThenViolationIsDetected(){
		PricerValidator validator = PutPricerValidator.of(derivSecSid, conversionRatio, bucketSizeAllowance, spreadTable);
		
		long begin = 11050_000_000l;
		long endExcl = 11065_000_000l;
		int data = 2500;
		int maxBucketSize = 500000;
		
		LongInterval o2 = LongInterval.of(endExcl + 5_000_000, endExcl + 10_000_000, data + 120, maxBucketSize);
		ViolationType result = validator.validateBucketDistance(begin, endExcl, data, maxBucketSize, o2);
		assertEquals(ViolationType.DOWN_VOL, result);
	}

	@Test
	public void givenExistingIntervalsWhenL1BidAskHasChangedButStillTightThenClearObservedUndSpots(){
		long lastTickTime = populate(pricer, 9000000000l, 99, 102, true);
		assertEquals(99, pricer.lastTightDerivBidPrice());
		
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(lastTickTime + 1, 110572000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(lastTickTime + 1, 100, 101, 100, 103, true));
		assertEquals(110572000, pricer.extractor().lastPrice());
		assertEquals(0, pricer.observedUndSpots().size());
	}
	
	private static void assertInterval(long begin, long endExclusive, int data, LongInterval actual){
		assertEquals("Begin(" + begin + ") != Actual(" +actual.begin() + ")", begin, actual.begin());
		assertEquals("EndExclusive(" + endExclusive + ") != Actual(" +actual.endExclusive() + ")", endExclusive, actual.endExclusive());
		assertEquals("Data(" + data + ") != Actual(" +actual.data() + ")", data, actual.data());
	}
	
	@Test
	public void testX(){
		// When merging interval,
		// merged interval overlaps with the interval on the right,
		// return DOWN_VOL for call, UP_VOL for put.
		
		long time = 9000000000l;
		observeGreeks(pricer, time);
		
		// Observe derivative tick
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, 100, 101, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 1, 110_550_000l)); // 110.55
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 110_590_000l)); // 110.59
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 110_550_000l)); // 110.55
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 4 + 3 * discardPeriodNs, 110_610_000l)); // 110.61
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time + 5 + 3 * discardPeriodNs, 101, 102, 101, 102, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 6 + 4 * discardPeriodNs, 110_610_000l)); // 110.61
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 7 + 5 * discardPeriodNs, 110_640_000l)); // 110.64
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 8 + 6 * discardPeriodNs, 110_670_000l)); // 110.67
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time + 9 + 6 * discardPeriodNs, 102, 103, 102, 103, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 10 + 7 * discardPeriodNs, 110_670_000l)); // 110.67
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 11 + 8 * discardPeriodNs, 110_700_000l)); // 110.70
		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + 12 + 9 * discardPeriodNs, 110_670_000l)); // 110.67
		
		// Go back to the middle interval
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 13 + 10 * discardPeriodNs, 110_640_000l)); // 110.64
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time + 14 + 10 * discardPeriodNs, 101, 102, 101, 102, true));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 15 + 11 * discardPeriodNs, 110_640_000l)); // 110.64
		
		// Now, observe a tick that goes into next interval on the right
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 16 + 12 * discardPeriodNs, 110_670_000l)); // 110.67
		assertEquals(ViolationType.DOWN_VOL, pricer.observeUndTickWithoutTriggerInfo(time + 17 + 13 * discardPeriodNs, 110_670_000l)); // 110.67
	}

	private static void observeGreeks(BucketPricer pricer, long time){
		ObservedGreeks og = ObservedGreeks.of(pricer.derivSecSid()); 
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(20000); // 0.50
		greeks.gamma(10000); // 0.10
		greeks.refSpot(110550); // 110.55
		og.merge(time, greeks);
		assertTrue(pricer.observeGreeks(og.updatedTimeNs(), greeks));
	}
	
	private static long populate(BucketPricer pricer, long time, int mmBid, int mmAsk, boolean isMMTight){
		ObservedGreeks og = ObservedGreeks.of(pricer.derivSecSid()); 
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(50000); // 0.50
		greeks.gamma(10000); // 0.10
		greeks.refSpot(110550); // 110.55
		
		og.merge(System.nanoTime(), greeks);
		
		assertTrue(pricer.observeGreeks(og.updatedTimeNs(), greeks));
		
		// Observe derivative tick
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, mmBid, mmAsk, isMMTight));
		
		/* Extra observe for the first tick, the circumvent a bug - See BucketPricer - bug */
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeDerivTickWithoutTriggerInfo(time, 100, 101, mmBid, mmAsk, isMMTight)); 
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 1, 110550000l)); // 110.55
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 + pricer.discardPeriodNs(), 110560000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * pricer.discardPeriodNs(), 110570000));
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 4 + 3 * pricer.discardPeriodNs(), 110571000));
		
		long lastTickTime = time + 5 + 4 * pricer.discardPeriodNs();
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(lastTickTime, 110571000));
		LongInterval outInterval = LongInterval.of(); 
		assertTrue(pricer.getIntervalByDerivPrice(mmBid, outInterval));
		assertEquals(-1, outInterval.theoBucketSize());
		assertTrue(pricer.getIntervalByUndSpot(110550000, outInterval));
		LOG.info(outInterval.toString());
		assertEquals(20000, outInterval.theoBucketSize());
		return lastTickTime;
	}

	@Test
	public void givenDerivAndUndTicksAvailWhenUndTickIncAndDerivPriceDownThenOK(){
		long time = 9000000000l;
		pricer.observeDerivTickWithoutTriggerInfo(time, 191, 192, 191, 192, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 25576271);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 25577678); 
		assertEquals(25577678, pricer.lastPrice());
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTickWithoutTriggerInfo(time + 2 * discardPeriodNs, 25563157));
//		pricer.observeDerivTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 1, 190, 191, 190, 191, true);
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 3 * discardPeriodNs + 1, 25558196));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 4 * discardPeriodNs + 1, 25525101));
//		pricer.observeDerivTickWithoutTriggerInfo(time + 4 * discardPeriodNs + 2, 189, 190, 189, 190, true);
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 5 * discardPeriodNs + 2, 25482500));
//		pricer.observeDerivTickWithoutTriggerInfo(time + 5 * discardPeriodNs + 3, 187, 188, 187, 188, true);
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 6 * discardPeriodNs + 3, 25469158));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 7 * discardPeriodNs + 3, 25453779));
//		pricer.observeDerivTickWithoutTriggerInfo(time + 7 * discardPeriodNs + 4, 186, 187, 186, 187, true);
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 8 * discardPeriodNs + 4, 25450621));
//
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 9 * discardPeriodNs + 4, 25450314));
//		assertEquals(25450621, pricer.lastPrice());
//		
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 10 * discardPeriodNs + 4, 25450314));
//		assertEquals(25450314, pricer.lastPrice());

		/*
		// Register interval [25450621]
		// Updated last und spot time: time + 9 * discardPeriodNs + 4
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 9 * discardPeriodNs + 4, 25450555));
		assertEquals(time + 9 * discardPeriodNs + 4, pricer.lastUndSpotProcessedTimeNs());
		assertEquals(25450621, pricer.lastPrice());

		// Observe und spot within 100_000_000, so that we cannot register 25450555
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 9 * discardPeriodNs + 4 + 90_000_000, 25450556));
		assertEquals(time + 9 * discardPeriodNs + 4, pricer.lastUndSpotProcessedTimeNs());
		assertEquals(25450621, pricer.lastPrice());

		// Process 25450555, since it didn't last for issuerLag, it won't be used
		// Observe und spot within 100_000_000, so that we cannot register 25450556
		// Updated last und spot time: time + 10 * discardPeriodNs + 4
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 10 * discardPeriodNs + 4, 25450314));
		assertEquals(time + 10 * discardPeriodNs + 4, pricer.lastUndSpotProcessedTimeNs());
		assertEquals(25450555, pricer.lastPrice());
		
		// Process 25450556 and 25450314
		// Create new interval [25450314, 25450315)
		// Updated last und spot time: time + 11 * discardPeriodNs + 4
		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 11 * discardPeriodNs + 4, 25450314));

		assertEquals(time + 11 * discardPeriodNs + 4, pricer.lastUndSpotProcessedTimeNs());
		assertEquals(25450314, pricer.lastPrice());
		*/

//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 10 * discardPeriodNs + 3, 25450314));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 11 * discardPeriodNs + 3, 25450314));
		
//		[25450746,25450782]->[186, theoBucketSize: 0]
//				[25469158,25482501]->[187, theoBucketSize: 0]
//				[25525101,25525102]->[189, theoBucketSize: 0]
//				[25558196,25563158]->[190, theoBucketSize: 0]
//				[25576271,25577679]->[191, theoBucketSize: 0]

		// 25450621
		
		// "nanoOfDay"	34297_804151000	
		// "undSpot6Dp"	25453779	

		// "nanoOfDay"	34297_976684000	
		// "undSpot6Dp"	25453779	

		// "nanoOfDay"	34297_978308000	
		// "undSpot6Dp"	25453779	

		// "nanoOfDay"	34297_980227000	
		// "undSpot6Dp"	25453779	

		// "nanoOfDay"	34298_236801000	
		// "undSpot6Dp"	25453801	

		// "nanoOfDay"	34298_238029000	
		// "undSpot6Dp"	25448437	

		// "nanoOfDay"	34298_240267000	
		// "undSpot6Dp"	25448437	

		// "nanoOfDay"	34298_240472000	
		// "undSpot6Dp"	25448437
		
		// "nanoOfDay"	34298_240515000	
		// "undSpot6Dp"	25448437
		
		// "nanoOfDay"	34298_240996000	
		// "undSpot6Dp"	25448437

		// "nanoOfDay"	34298_245420000	
		// "undSpot6Dp"	25448437

		// "nanoOfDay"	34298_245484000	
		// "undSpot6Dp"	25450314	

		// "nanoOfDay"	34298_245675000	
		// "undSpot6Dp"	25450314	

		// "nanoOfDay"	34298_245703000	
		// "undSpot6Dp"	25450314
		
		// "nanoOfDay"	34298_247126000	
		// "undSpot6Dp"	25450314

		// "nanoOfDay"	34298_247298000	
		// "undSpot6Dp"	25450314
		
		// "nanoOfDay"	34298_249856000	
		// "undSpot6Dp"	25450314

		// "nanoOfDay"	34298_249891000	
		// "undSpot6Dp"	25450314

		// "nanoOfDay"	34298_255171000	
		// "undSpot6Dp"	25450314
		
		// "nanoOfDay"	34298_322155000	
		// "undSpot6Dp"	25450314
		
		// "nanoOfDay"	34298_329874000	
		// "undSpot6Dp"	25450314

		// "nanoOfDay"	34298_373797000	
		// "undSpot6Dp"	25450314

		// "nanoOfDay"	34298_725250000	
		// "undSpot6Dp"	25450314
		
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 2 * discardPeriodNs + 1, 1007));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 2 * discardPeriodNs + 2, 1008));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 2 * discardPeriodNs + 3, 1009));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 2 * discardPeriodNs + 4, 1010));
//		assertEquals(ViolationType.NO_VIOLATION, pricer.observeUndTick(time + 2 * discardPeriodNs + 5, 1011));
//		
//		ViolationType result = pricer.observeDerivTickWithoutTriggerInfo(time + 2 * discardPeriodNs + 6, 100, 101, 96, 99, true); 
//		assertEquals(ViolationType.DOWN_VOL, result);
	}
	
	@Test
	public void givenNoRefSpotWhenExtrapolatedThenNothingReturn(){
		long time = 9000000000l;
		ObservedGreeks og = ObservedGreeks.of(pricer.derivSecSid()); 
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(20000); // 0.50
		greeks.gamma(10000); // 0.10
		og.merge(time, greeks);
		NonOverlappingLongIntervalTreeSet outExtrapolatedIntervals = NonOverlappingLongIntervalTreeSet.of(); 
		Int2LongOpenHashMap outExtrapolatedIntervalByDerivPrice = new Int2LongOpenHashMap();
		int currentDerivBid = 101;
		pricer.computeTheoIntervals(og, conversionRatio, currentDerivBid, outExtrapolatedIntervals, outExtrapolatedIntervalByDerivPrice);
		assertTrue(outExtrapolatedIntervals.isEmpty());
		assertTrue(outExtrapolatedIntervalByDerivPrice.isEmpty());
	}
	
	@Test
	public void givenNoIntervalWhenExtrapolateThenNothingReturn(){
		long time = 9000000000l;
		observeGreeks(pricer, time);
		ObservedGreeks greeks = pricer.observedGreeks();
		NonOverlappingLongIntervalTreeSet outExtrapolatedIntervals = NonOverlappingLongIntervalTreeSet.of(); 
		Int2LongOpenHashMap outExtrapolatedIntervalByDerivPrice = new Int2LongOpenHashMap();
		int currentDerivBid = 101;
		pricer.computeTheoIntervals(greeks, conversionRatio, currentDerivBid, outExtrapolatedIntervals, outExtrapolatedIntervalByDerivPrice);
		assertTrue(outExtrapolatedIntervals.isEmpty());
		assertTrue(outExtrapolatedIntervalByDerivPrice.isEmpty());
	}
	
	@Test
	public void givenNoDerivBidWhenExtrapolateThenNothingReturn(){
		long time = 9000000000l;
		ObservedGreeks og = ObservedGreeks.of(pricer.derivSecSid()); 
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(20000); // 0.20
		greeks.gamma(10000); // 0.10
		og.merge(time, greeks);
		NonOverlappingLongIntervalTreeSet outExtrapolatedIntervals = NonOverlappingLongIntervalTreeSet.of(); 
		Int2LongOpenHashMap outExtrapolatedIntervalByDerivPrice = new Int2LongOpenHashMap();
		int currentDerivBid = LongInterval.NULL_DATA_VALUE;
		pricer.computeTheoIntervals(og, conversionRatio, currentDerivBid, outExtrapolatedIntervals, outExtrapolatedIntervalByDerivPrice);
		assertTrue(outExtrapolatedIntervals.isEmpty());
		assertTrue(outExtrapolatedIntervalByDerivPrice.isEmpty());
	}
	
	@Test
	public void givenPresetIntervalsWhenExtrapolateThenOK(){
		// Given
		long time = 9000000000l;
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(20000); // 0.20
		greeks.gamma(10000); // 0.10
		greeks.refSpot(1000);
		pricer.observeGreeks(time, greeks);

		// Create intervals
		
	}
	
	@Test
	public void givenMoreExistingIntervalsWhenThen(){
		// Given
		long time = 9000000000l;
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(20000); // 0.20
		greeks.gamma(10000); // 0.10
		greeks.refSpot(1000);
		pricer.observeGreeks(time, greeks);
		
		int currentDerivBid = 200;
		int currentDerivAsk = currentDerivBid + 2; 
		pricer.observeDerivTickWithoutTriggerInfo(time, currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true);
		pricer.observeDerivTickWithoutTriggerInfo(time, currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true); // To circumvent bug
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1030000);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1046000); // Lock in an interval at [1000 1006) -> 200

		currentDerivBid++; // 201
		currentDerivAsk = currentDerivBid + 2;
		pricer.observeDerivTickWithoutTriggerInfo(time + 4 + 3 * discardPeriodNs , currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 5 + 4 * discardPeriodNs, 1046000);
		pricer.observeUndTickWithoutTriggerInfo(time + 6 + 5 * discardPeriodNs, 1077000);
		pricer.observeUndTickWithoutTriggerInfo(time + 7 + 6 * discardPeriodNs, 1106000);

		currentDerivBid++; // 202
		currentDerivAsk = currentDerivBid + 2;
		pricer.observeDerivTickWithoutTriggerInfo(time + 8 + 7 * discardPeriodNs , currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 9 + 8 * discardPeriodNs, 1106000);
		pricer.observeUndTickWithoutTriggerInfo(time + 10 + 9 * discardPeriodNs, 1126000);
		pricer.observeUndTickWithoutTriggerInfo(time + 11 + 10 * discardPeriodNs, 1126000);
		pricer.observeUndTickWithoutTriggerInfo(time + 12 + 11 * discardPeriodNs, 1156000);

		currentDerivBid++; // 203
		currentDerivAsk = currentDerivBid + 2;
		pricer.observeDerivTickWithoutTriggerInfo(time + 13 + 12 * discardPeriodNs , currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 14 + 13 * discardPeriodNs, 1166000);
		pricer.observeUndTickWithoutTriggerInfo(time + 15 + 14 * discardPeriodNs, 1175000);
		pricer.observeUndTickWithoutTriggerInfo(time + 16 + 15 * discardPeriodNs, 1185000);
		pricer.observeUndTickWithoutTriggerInfo(time + 17 + 16 * discardPeriodNs, 1201500);

		currentDerivBid++; // 204
		currentDerivAsk = currentDerivBid + 2;
		pricer.observeDerivTickWithoutTriggerInfo(time + 18 + 17 * discardPeriodNs , currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 19 + 18 * discardPeriodNs, 1221500);
		pricer.observeUndTickWithoutTriggerInfo(time + 20 + 19 * discardPeriodNs, 1221501);
		pricer.observeUndTickWithoutTriggerInfo(time + 21 + 20 * discardPeriodNs, 1255000);
		
		currentDerivBid++;
		currentDerivAsk = currentDerivBid + 2;
		pricer.observeDerivTickWithoutTriggerInfo(time + 22 + 21 * discardPeriodNs , currentDerivBid, currentDerivAsk, currentDerivBid, currentDerivAsk, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 23 + 22 * discardPeriodNs, 1261500);
		
		// When
//		NonOverlappingLongIntervalTreeSet outExtrapolatedIntervals = NonOverlappingLongIntervalTreeSet.of(); 
//		Int2LongOpenHashMap outExtrapolatedIntervalByDerivPrice = new Int2LongOpenHashMap();
//		pricer.computeTheoIntervals(pricer.observedGreeks(), conversionRatio, 202, outExtrapolatedIntervals, outExtrapolatedIntervalByDerivPrice);
		
		NonOverlappingLongIntervalTreeSet observedIntervals = pricer.observedIntervals();

		LOG.info("Intervals: {}", observedIntervals.toString());
		int numIntervals = observedIntervals.intervals(outIntervals);
		assertEquals(6, numIntervals);
		assertLongInterval(200, 1000000,1030001, outIntervals[0]);
		assertLongInterval(201, 1046000,1077001, outIntervals[1]);
		assertLongInterval(202, 1106000,1126001, outIntervals[2]);
		assertLongInterval(203, 1156000,1185001, outIntervals[3]);
		assertLongInterval(204, 1201500,1221502, outIntervals[4]);
		assertLongInterval(205, 1255000,1255001, outIntervals[5]);
	}
	
//	@Test
//	public void givenEmptyWhenNewIntervalIsAddedThenChangeHandlerIsCalled(){
//		// Given
//		long time = 9000000000l;
//		Greeks greeks = Greeks.of(pricer.derivSecSid());
//		greeks.delta(20000); // 0.20
//		greeks.gamma(10000); // 0.10
//		greeks.refSpot(1000);
//		pricer.observeGreeks(time, greeks);
//		
//		BucketPricer.ChangeHandler changeHandler = new ChangeHandler() {
//			
//			@Override
//			public void handleActiveIntervalChange(long secSid, long undSpot, ImmutableLongInterval interval) {
//				LOG.info("secSid: {}, undSpot: {}, interval: {}", secSid, undSpot, interval);
//			}
//		};
//		pricer.changeHandler(changeHandler);
//
//		int currentDerivBid = 200;
//		pricer.observeDerivTickWithoutTriggerInfo(time, 201, 202, currentDerivBid, 203, true);
//		pricer.observeDerivTickWithoutTriggerInfo(time, 201, 202, currentDerivBid, 203, true); // To circumvent bug
//		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000000);
//		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1030000);
//		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1036000); // Lock in an interval at [1000 1006) -> 200
//		pricer.observeUndTickWithoutTriggerInfo(time + 4 + 3 * discardPeriodNs, 1036001);
//		pricer.observeUndTickWithoutTriggerInfo(time + 5 + 4 * discardPeriodNs, 1036000);
//
//		pricer.changeHandler(BucketPricer.ChangeHandler.NULL_HANDLER);
//		
//	}
	
	@Test
	public void givenWhenThen(){
		// Given
		long time = 9000000000l;
		Greeks greeks = Greeks.of(pricer.derivSecSid());
		greeks.delta(20000); // 0.20
		greeks.gamma(10000); // 0.10
		greeks.refSpot(1000);
		pricer.observeGreeks(time, greeks);
		
		int currentDerivBid = 200;
		pricer.observeDerivTickWithoutTriggerInfo(time, 201, 202, currentDerivBid, 203, true);
		pricer.observeDerivTickWithoutTriggerInfo(time, 201, 202, currentDerivBid, 203, true); // To circumvent bug
		pricer.observeUndTickWithoutTriggerInfo(time + 1, 1000000);
		pricer.observeUndTickWithoutTriggerInfo(time + 2 + discardPeriodNs, 1030000);
		pricer.observeUndTickWithoutTriggerInfo(time + 3 + 2 * discardPeriodNs, 1036000); // Lock in an interval at [1000 1006) -> 200
		
		// When
		NonOverlappingLongIntervalTreeSet outExtrapolatedIntervals = NonOverlappingLongIntervalTreeSet.of(); 
		Int2LongOpenHashMap outExtrapolatedIntervalByDerivPrice = new Int2LongOpenHashMap();
		pricer.computeTheoIntervals(pricer.observedGreeks(), conversionRatio, currentDerivBid, outExtrapolatedIntervals, outExtrapolatedIntervalByDerivPrice);
		
		// Then
		assertEquals(5, pricer.adjacentBuckets().size());
		assertEquals(0, pricer.adjacentBuckets().get(0).data());
		
		// When - lock in another interval
		pricer.observeDerivTickWithoutTriggerInfo(time, 201, 204, currentDerivBid + 1, 204, true);
		pricer.observeUndTickWithoutTriggerInfo(time + 4 + 3 * discardPeriodNs, 1040001);
		pricer.observeUndTickWithoutTriggerInfo(time + 5 + 4 * discardPeriodNs, 1040001);

		// Then
		Int2LongOpenHashMap observedIntervalsByDerivPrice = pricer.observedIntervalsByDerivPrice();
		assertEquals(2, observedIntervalsByDerivPrice.size());	
//		assertLongPrimitiveInterval(937766, 983068, observedIntervalsByDerivPrice.get(199));
		assertLongPrimitiveInterval(1000000,1030001, observedIntervalsByDerivPrice.get(200));
		assertLongPrimitiveInterval(1036000,1040002, observedIntervalsByDerivPrice.get(201));
//		assertLongPrimitiveInterval(1085115,1127355, observedIntervalsByDerivPrice.get(202));
//		assertLongPrimitiveInterval(1134229,1175449, observedIntervalsByDerivPrice.get(203));
		
		NonOverlappingLongIntervalTreeSet observedIntervals = pricer.observedIntervals();
		int numIntervals = observedIntervals.intervals(outIntervals);
		assertEquals(2, numIntervals);
		assertLongInterval(200, 1000000,1030001, outIntervals[0]);
		assertLongInterval(201, 1036000,1040002, outIntervals[1]);
	}
	
	public static void assertLongPrimitiveInterval(long expectedBegin, long expectedEnd, long actualValue){
		assertEquals(expectedBegin, (actualValue & BucketPricer.HIGHER_ORDER_MASK) >>> 32);
		assertEquals(expectedEnd, (actualValue & BucketPricer.LOWER_ORDER_MASK));		
	}
	
	public static void assertLongInterval(int expectedData, long expectedBegin, long expectedEnd, LongInterval actualValue){
		assertEquals(expectedBegin, actualValue.begin());
		assertEquals(expectedEnd, actualValue.endExclusive());
		assertEquals(expectedData, actualValue.data());
	}

	@Test
	public void testCalculateUndSpotChange(){
		int gamma = 1319;
		long changeInDeriv = -2;
		long delta = 12903;
		long cr = 10000;
		if (gamma == 0){
			//			LOG.info("Calculated calculateUndSpotChange with zero gamma - call");
			long result = (long)(changeInDeriv) * cr * 100_000 / delta; 
			LOG.info("Result is {}", result);
			System.out.println("Result is " + result);
		}
		long partialCalculateValue = gamma * cr;
//		long result = (- delta /* 5p */ + (long)Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000L /gamma /* 5dp */;
//		long result = (long)((- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000) /gamma /* 5dp */;
		long result = (long)(- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)  * 1_000_000_000) /gamma ;
		double inter = (- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000L));
		LOG.info("Result 2 is {}, intermediate not working:{}, intermediate working:{}", 
				result,
				- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)  * 1_000_000_000,
				(long)((- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000));
		System.out.println("Result 2 is " + result
				+ ", inter:" 
				+ inter
				+ ", intermediate not working:" 
				+ (- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000L))  * 1_000_000_000L 
				+ ", intermediate working:" + ((long)((- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000))
				+ ", final: " + 
				(long)((- delta /* 5p */ + Math.sqrt(delta /* 5dp */ * delta /* 5dp */ + partialCalculateValue /* greeks.gamma() 5dp * this.conversionRatio 3 dp */ * (changeInDeriv) /* 3 dp */ / 5000)) * 1_000_000_000) /gamma /* 5dp */);
		System.out.println("Expected: " + (long)((inter * 1_000_000_000) / gamma));
		//Result 2 is -155344
	}

	@Test
	public void testAnother(){
		int adjDelta = 12903;
		long gamma = 1319;
		int derivPrice = 125;
		int refDerivPrice = 125;
//		long result = (- adjDelta /* 5p */ + (long)Math.sqrt(adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * gamma /* 5dp */ * this.conversionRatio /* 3 dp*/ * (derivPrice - refDerivPrice) /* 3 dp */ / 10)) * 1_000_000 /gamma /* 5dp */;
//		long result = (long)((- adjDelta /* 5p */ + Math.sqrt(adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * gamma /* 5dp */ * this.conversionRatio /* 3 dp*/ * (derivPrice - refDerivPrice) /* 3 dp */ / 10)) * 1_000_000 /gamma) /* 5dp */;
		long result = (long)((- adjDelta /* 5p */ + Math.sqrt(adjDelta /* 5dp */ * adjDelta /* 5dp */ + 2L * gamma /* 5dp */ * this.conversionRatio /* 3 dp*/ * (derivPrice - refDerivPrice) /* 3 dp */ / 10)) * 1_000_000) /gamma /* 5dp */;
		System.out.println("Result is " + result);
		//Result is -160454
		//Result is -160349
	}
}
