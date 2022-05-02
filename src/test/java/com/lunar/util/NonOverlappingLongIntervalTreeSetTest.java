package com.lunar.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;

public class NonOverlappingLongIntervalTreeSetTest {
	static final Logger LOG = LogManager.getLogger(NonOverlappingLongIntervalTreeSetTest.class);
	private LongInterval outSmallerNonOverlappingInterval = LongInterval.of(); 
	private LongInterval outGreaterNonOverlappingInterval = LongInterval.of();
	private LongInterval[] outOverlappingIntervals = new LongInterval[10];
	private Long2LongMap.Entry[] outOverlappingEntries = new Long2LongMap.Entry[10];

	@Before
	public void setup(){
		outSmallerNonOverlappingInterval = LongInterval.of(); 
		outGreaterNonOverlappingInterval = LongInterval.of();
		outOverlappingIntervals = new LongInterval[10];
		for (int i = 0; i < outOverlappingIntervals.length; i++){
			outOverlappingIntervals[i] = LongInterval.of();
		}
	}
	
	@Test
	public void testCreate(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		assertNotNull(tree);
	}

	@Test(expected=IllegalArgumentException.class)
	public void givenEmptyWhenAddBeginLargerThanEndThenThrowException(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		int data = 555000;
		int dataInTick = 600;
		long begin = 1003;
		long end = 1002;
		boolean result = tree.add(begin, end, data, dataInTick);
		assertFalse(result);
	}
	
	@Test
	public void testEmptyTree(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		assertEquals(0, tree.removeOverlaps(1001, 1002, outOverlappingIntervals));
		assertEquals(0, tree.removeOverlaps(1002, 1001, outOverlappingIntervals));
	}
	
	@Test
	public void givenEmptyWhenAddValidIntervalThenOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		int data = 555000;
		long begin = 1001;
		long end = 1002;
		int dataInTick = 600;
		boolean result = tree.add(begin, end, data, dataInTick);
		assertTrue(result);
		assertEquals(1, tree.intervals().size());
		
		assertEquals(1, tree.intervals().size());
	}
	
	@Test
	public void givenEmptyWhenAddIntervalThatOverlapsWithExistingThenFalse(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		int data = 555000;
		int dataInTick = 600;
		long begin = 1001;
		long end = 1010;
		boolean result = tree.add(begin, end, data, dataInTick);
		assertTrue(result);
		assertEquals(1, tree.intervals().size());
		
		int data2 = 555000;
		int dataInTick2 = 600;
		long begin2 = 1001;
		long end2 = 1010;
		boolean result2 = tree.add(begin2, end2, data2, dataInTick2);
		assertFalse(result2);
		assertEquals(1, tree.intervals().size());
	}
	
	@Test
	public void givenExistingRangeWhenOverlapsOnBeginReturnTrue(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		int data = 555000;
		int dataInTick = 600;
		long begin = 1001;
		long end = 1002;
		LongInterval interval = LongInterval.of(begin, end, data, LongInterval.NULL_DATA_VALUE);
		tree.add(begin, end, data, dataInTick);

		long value = 1001;
		LongInterval outInterval = LongInterval.of(); 
		assertTrue(tree.overlaps(value, outInterval));
		assertTrue(interval.equals(outInterval));
	}
	
	@Test
	public void givenExistingRangeWhenSearchWithIntervalReturnTrue(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		int data = 555000;
		int dataInTick = 600;
		long begin = 1001;
		long end = 1003;
		LongInterval interval = LongInterval.of(begin, end, data, LongInterval.NULL_DATA_VALUE);
		tree.add(begin, end, data, dataInTick);

		long value = 1002;
		LongInterval outInterval = LongInterval.of();
		assertTrue(tree.search(value, outInterval));
		assertTrue(interval.equals(outInterval));
	}
	
	@Test
	public void givenExisting(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(100, 110, 900, 500);
		tree.add(115, 125, 900, 500);

		LongInterval outInterval = LongInterval.of();
		LongInterval outNextInterval = LongInterval.of();
		assertTrue(tree.searchOverlapAndGreater(109, outInterval, outNextInterval));
		assertEquals(100, outInterval.begin());
		assertEquals(115, outNextInterval.begin());

		assertTrue(tree.searchOverlapAndGreater(110, outInterval, outNextInterval));
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outInterval.begin());
		assertEquals(115, outNextInterval.begin());

		assertTrue(tree.searchOverlapAndGreater(111, outInterval, outNextInterval));
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outInterval.begin());
		assertEquals(115, outNextInterval.begin());

		assertTrue(tree.searchOverlapAndGreater(115, outInterval, outNextInterval));
		assertEquals(115, outInterval.begin());
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outNextInterval.begin());
		
		assertTrue(tree.searchOverlapAndGreater(124, outInterval, outNextInterval));
		assertEquals(115, outInterval.begin());
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outNextInterval.begin());

		assertFalse(tree.searchOverlapAndGreater(125, outInterval, outNextInterval));
	}
	
	@Test
	public void givenExistingAndSmaller(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(100, 110, 900, 500);
		tree.add(115, 125, 900, 500);

		LongInterval outInterval = LongInterval.of();
		LongInterval outNextInterval = LongInterval.of();
		assertTrue(tree.searchOverlapAndSmaller(109, outInterval, outNextInterval));
		assertEquals(100, outInterval.begin());
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outNextInterval.begin());

		assertTrue(tree.searchOverlapAndSmaller(110, outInterval, outNextInterval));
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outInterval.begin());
		assertEquals(100, outNextInterval.begin());

		assertTrue(tree.searchOverlapAndSmaller(111, outInterval, outNextInterval));
		assertEquals(LongInterval.NULL_INTERVAL_BEGIN_VALUE, outInterval.begin());
		assertEquals(100, outNextInterval.begin());

		assertTrue(tree.searchOverlapAndSmaller(115, outInterval, outNextInterval));
		assertEquals(115, outInterval.begin());
		assertEquals(100, outNextInterval.begin());
		
		assertTrue(tree.searchOverlapAndSmaller(124, outInterval, outNextInterval));
		assertEquals(115, outInterval.begin());
		assertEquals(100, outNextInterval.begin());

		assertFalse(tree.searchOverlapAndSmaller(99, outInterval, outNextInterval));
	}
	
	@Test
	public void givenExistingRangesWhenSearchWithIntervalReturnTrue(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(100, 110, 900, 500);
		tree.add(115, 125, 900, 500);
		tree.add(128, 140, 900, 500);
		tree.add(142, 160, 900, 500);
		tree.add(163, 173, 900, 500);
		tree.add(175, 180, 900, 500);

		LongInterval outInterval = LongInterval.of();
		assertTrue(tree.search(115, outInterval));
	}

	@Test
	public void givenExistingRangesWhenSearchExactReturnTrue(){
	    NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
	    tree.add(100, 110, 900, 500);
	    tree.add(115, 125, 900, 500);
	    tree.add(128, 140, 900, 500);
	    tree.add(142, 160, 900, 500);
	    tree.add(163, 173, 900, 500);
	    tree.add(175, 180, 900, 500);

	    Entry searchExact = tree.searchExact(173);
	    assertNotNull(searchExact);
	}

	@Test
	public void givenExistingRangesWhenSearchWithIntervalReturnTrueAndAllOverlappingIntervals(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		LongInterval interval = LongInterval.of(95, 101, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());

		interval = LongInterval.of(105, 109, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());
		
		interval = LongInterval.of(110, 120, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());

		interval = LongInterval.of(120, 125, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());

		LongInterval[] outIntervals = new LongInterval[10];
		for (int i = 0; i < outIntervals.length; i++){
			outIntervals[i] = LongInterval.of();
		}
		int numMatches = tree.removeOverlaps(115, 120, outIntervals);
		assertEquals(1, numMatches);

		numMatches = tree.removeOverlaps(110, 111, outIntervals);
		assertEquals(0, numMatches);

		numMatches = tree.removeOverlaps(109, 110, outIntervals);
		assertEquals(0, numMatches);

		numMatches = tree.removeOverlaps(108, 109, outIntervals);
		assertEquals(1, numMatches);

		numMatches = tree.removeOverlaps(102, 103, outIntervals);
		assertEquals(0, numMatches);

		numMatches = tree.removeOverlaps(119, 121, outIntervals);
		assertEquals(1, numMatches);
	}
	
	@Test
	public void givenExistingRangesWhenRemoveOverlapsThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		LongInterval interval = LongInterval.of(95, 101, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());
		
		assertFalse(tree.removeOverlap(101));
		assertEquals(1, tree.intervals().size());
		
		assertFalse(tree.removeOverlap(94));
		assertEquals(1, tree.intervals().size());

		assertTrue(tree.removeOverlap(95));
		assertEquals(0, tree.intervals().size());
		
		tree.add(interval.begin(), interval.endExclusive(), interval.data(), interval.theoBucketSize());
		assertTrue(tree.removeOverlap(100));
		assertEquals(0, tree.intervals().size());
	}
	
	@Test
	public void givenExistingIntervalWhenClearThenEmpty(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 107, 257, 50);
		tree.add(108, 115, 258, 50);
		tree.add(116, 121, 259, 50);

		assertEquals(4, tree.intervals().size());
		
		tree.clear();
		assertEquals(0, tree.intervals().size());
	}
	
	@Test
	public void givenExistingRangeWhenSearchBeforeAfterAndOverlapsThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 107, 257, 50);
		tree.add(108, 115, 258, 50);
		tree.add(116, 121, 259, 50);
	
		long begin = 106;
		long endExclusive = 109; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(2, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, 50, outSmallerNonOverlappingInterval);
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(116, 121, 259, 50, outGreaterNonOverlappingInterval);
		int i = 0;
		assertInterval(108, 115, 258, 50, outOverlappingIntervals[i++]);
		assertInterval(97, 107, 257, 50, outOverlappingIntervals[i++]);
	}

	@Test
	public void givenExistingRangeWhenSearchBeforeAfterWithNoOverlapsThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 104, 257, 51);
		tree.add(110, 115, 258, 52);
		tree.add(116, 121, 259, 53);

		long begin = 106;
		long endExclusive = 109; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(0, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertInterval(97, 104, 257, 51, outSmallerNonOverlappingInterval);
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(110, 115, 258, 52, outGreaterNonOverlappingInterval);
	}
	
	@Test
	public void givenEmptyWhenSearchBeforeAfterWithNoOverlapsThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
	
		long begin = 106;
		long endExclusive = 109; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(0, numOverlaps);
		assertTrue(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchLeftMostWithNoOverlapThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 104, 257, 51);
	
		long begin = 88;
		long endExclusive = 89; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(0, numOverlaps);
		assertTrue(outSmallerNonOverlappingInterval.isEmpty());
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, 50, outGreaterNonOverlappingInterval);
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchRightMostWithNoOverlapThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 104, 257, 51);
	
		long begin = 105;
		long endExclusive = 106; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(0, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(97, 104, 257, 51, outSmallerNonOverlappingInterval);
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchLeftMostWithOverlapThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 104, 257, 51);
	
		long begin = 89;
		long endExclusive = 91; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(1, numOverlaps);
		assertTrue(outSmallerNonOverlappingInterval.isEmpty());
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(97, 104, 257, 51, outGreaterNonOverlappingInterval);
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchRightMostWithOverlapThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 104, 257, 51);
	
		long begin = 103;
		long endExclusive = 104;
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(1, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, 50, outSmallerNonOverlappingInterval);
	}

	@Test
	public void givenTwoIntervalExistsWhenSearchForRightMostOverlappingIntervalThenReturnOK(){
		NonOverlappingLongIntervalTreeSet tree = NonOverlappingLongIntervalTreeSet.of();
		tree.add(90, 96, 256, 50);
		tree.add(97, 104, 257, 51);
	
		long begin = 102;
		long endExclusive = 103;
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals, outOverlappingEntries);
		assertEquals(1, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, 50, outSmallerNonOverlappingInterval);
	}
	
	private static void assertInterval(long begin, long endExclusive, int data, int dataInTick, LongInterval actual){
		assertEquals("Begin(" + begin + ") != Actual(" +actual.begin() + ")", begin, actual.begin());
		assertEquals("EndExclusive(" + endExclusive + ") != Actual(" +actual.endExclusive() + ")", endExclusive, actual.endExclusive());
		assertEquals("Data(" + data + ") != Actual(" +actual.data() + ")", data, actual.data());
		assertEquals("DataInTick(" + dataInTick + ") != Actual(" +actual.theoBucketSize() + ")", dataInTick, actual.theoBucketSize());
	}
	
}
