package com.lunar.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;

public class NonOverlappingIntervalTreeSetTest {
	static final Logger LOG = LogManager.getLogger(NonOverlappingIntervalTreeSetTest.class);

	private LongInterval outSmallerNonOverlappingInterval = LongInterval.of(); 
	private LongInterval outGreaterNonOverlappingInterval = LongInterval.of();
	private LongInterval[] outOverlappingIntervals = new LongInterval[10];

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
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		assertNotNull(tree);
	}

	@Test(expected=IllegalArgumentException.class)
	public void givenEmptyWhenAddBeginLargerThanEndThenThrowException(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		int data = 555000;
		long begin = 1003;
		long end = 1002;
		boolean result = tree.add(begin, end, data);
		assertFalse(result);
	}

	@Test
	public void givenEmptyWhenAddValidIntervalThenOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		int data = 555000;
		long begin = 1001;
		long end = 1002;
		boolean result = tree.add(begin, end, data);
		assertTrue(result);
		assertEquals(1, tree.intervals().size());
		
		LongInterval interval = LongInterval.of(begin, end, data, LongInterval.NULL_DATA_VALUE);
		ObjectBidirectionalIterator<LongInterval> iterator = tree.intervals().iterator(interval);
		assertTrue(iterator.hasPrevious());
		assertFalse(iterator.hasNext());
		assertEquals(interval.endExclusive(), iterator.previous().endExclusive());
	}
	
	@Test
	public void givenEmptyWhenAddIntervalThatOverlapsWithExistingThenFalse(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		int data = 555000;
		long begin = 1001;
		long end = 1010;
		boolean result = tree.add(begin, end, data);
		assertTrue(result);
		assertEquals(1, tree.intervals().size());
		
		int data2 = 555000;
		long begin2 = 1001;
		long end2 = 1010;
		boolean result2 = tree.add(begin2, end2, data2);
		assertFalse(result2);
		assertEquals(1, tree.intervals().size());
	}
	
	@Test
	public void givenExistingRangeWhenOverlapsOnBeginReturnTrue(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		int data = 555000;
		long begin = 1001;
		long end = 1002;
		LongInterval interval = LongInterval.of(begin, end, data, LongInterval.NULL_DATA_VALUE);
		tree.add(begin, end, data);

		long value = 1001;
		LongInterval outInterval = LongInterval.of(); 
		assertTrue(tree.overlaps(value, outInterval));
		assertTrue(interval.equals(outInterval));
	}
	
	@Test
	public void givenExistingRangeWhenSearchWithIntervalReturnTrue(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		int data = 555000;
		long begin = 1001;
		long end = 1003;
		LongInterval interval = LongInterval.of(begin, end, data, LongInterval.NULL_DATA_VALUE);
		tree.add(begin, end, data);

		long value = 1002;
		LongInterval outInterval = LongInterval.of();
		assertTrue(tree.search(value, outInterval));
		assertTrue(interval.equals(outInterval));
	}
	
	@Test
	public void givenExistingRangesWhenSearchWithIntervalReturnTrue(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(100, 110, 900);
		tree.add(115, 125, 900);
		tree.add(128, 140, 900);
		tree.add(142, 160, 900);
		tree.add(163, 173, 900);
		tree.add(175, 180, 900);

		LongInterval outInterval = LongInterval.of();
		assertTrue(tree.search(115, outInterval));
	}
	

	@Test
	public void givenExistingRangesWhenSearchWithIntervalReturnTrueAndAllOverlappingIntervals(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		LongInterval interval = LongInterval.of(95, 101, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data());

		interval = LongInterval.of(105, 109, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data());
		
		interval = LongInterval.of(110, 120, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data());

		interval = LongInterval.of(120, 125, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data());

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
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		LongInterval interval = LongInterval.of(95, 101, 555000, LongInterval.NULL_DATA_VALUE);
		tree.add(interval.begin(), interval.endExclusive(), interval.data());
		
		assertFalse(tree.removeOverlap(101));
		assertEquals(1, tree.intervals().size());
		
		assertFalse(tree.removeOverlap(94));
		assertEquals(1, tree.intervals().size());

		assertTrue(tree.removeOverlap(95));
		assertEquals(0, tree.intervals().size());
		
		tree.add(interval.begin(), interval.endExclusive(), interval.data());
		assertTrue(tree.removeOverlap(100));
		assertEquals(0, tree.intervals().size());
	}

	@Test
	public void givenExistingIntervalWhenClearThenEmpty(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 107, 257);
		tree.add(108, 115, 258);
		tree.add(116, 121, 259);

		assertEquals(4, tree.intervals().size());
		
		tree.clear();
		assertEquals(0, tree.intervals().size());
	}
	
	@Test
	public void givenExistingRangeWhenSearchBeforeAfterAndOverlapsThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 107, 257);
		tree.add(108, 115, 258);
		tree.add(116, 121, 259);
	
		long begin = 106;
		long endExclusive = 109; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(2, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, outSmallerNonOverlappingInterval);
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(116, 121, 259, outGreaterNonOverlappingInterval);
		int i = 0;
		assertInterval(108, 115, 258, outOverlappingIntervals[i++]);
		assertInterval(97, 107, 257, outOverlappingIntervals[i++]);
		
	}
	
	@Test
	public void givenExistingRangeWhenSearchBeforeAfterWithNoOverlapsThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 104, 257);
		tree.add(110, 115, 258);
		tree.add(116, 121, 259);

		long begin = 106;
		long endExclusive = 109; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(0, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertInterval(97, 104, 257, outSmallerNonOverlappingInterval);
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(110, 115, 258, outGreaterNonOverlappingInterval);
	}
	
	@Test
	public void givenEmptyWhenSearchBeforeAfterWithNoOverlapsThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
	
		long begin = 106;
		long endExclusive = 109; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(0, numOverlaps);
		assertTrue(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchLeftMostWithNoOverlapThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 104, 257);
	
		long begin = 88;
		long endExclusive = 89; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(0, numOverlaps);
		assertTrue(outSmallerNonOverlappingInterval.isEmpty());
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, outGreaterNonOverlappingInterval);
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchRightMostWithNoOverlapThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 104, 257);
	
		long begin = 105;
		long endExclusive = 106; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(0, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(97, 104, 257, outSmallerNonOverlappingInterval);
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchLeftMostWithOverlapThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 104, 257);
	
		long begin = 89;
		long endExclusive = 91; 
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(1, numOverlaps);
		assertTrue(outSmallerNonOverlappingInterval.isEmpty());
		assertFalse(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(97, 104, 257, outGreaterNonOverlappingInterval);
	}
	
	@Test
	public void givenTwoIntervalExistsWhenSearchRightMostWithOverlapThenReturnOK(){
		NonOverlappingIntervalTreeSet tree = NonOverlappingIntervalTreeSet.of();
		tree.add(90, 96, 256);
		tree.add(97, 104, 257);
	
		long begin = 103;
		long endExclusive = 104;
		int numOverlaps = tree.search(begin, endExclusive, outSmallerNonOverlappingInterval, outGreaterNonOverlappingInterval, outOverlappingIntervals);
		assertEquals(1, numOverlaps);
		assertFalse(outSmallerNonOverlappingInterval.isEmpty());
		assertTrue(outGreaterNonOverlappingInterval.isEmpty());
		assertInterval(90, 96, 256, outSmallerNonOverlappingInterval);
	}
	
	private static void assertInterval(long begin, long endExclusive, int data, LongInterval actual){
		assertEquals("Begin(" + begin + ") != Actual(" +actual.begin() + ")", begin, actual.begin());
		assertEquals("EndExclusive(" + endExclusive + ") != Actual(" +actual.endExclusive() + ")", endExclusive, actual.endExclusive());
		assertEquals("Data(" + data + ") != Actual(" +actual.data() + ")", data, actual.data());
	}
	
}
