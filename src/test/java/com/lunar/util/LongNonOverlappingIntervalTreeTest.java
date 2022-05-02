package com.lunar.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.lunar.util.LongNonOverlappingIntervalTree.Interval;

public class LongNonOverlappingIntervalTreeTest {

	@Test
	public void testCreate(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		assertNotNull(tree);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void givenEmptyWhenAddBeginLargerThanEndThenThrowException(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		long data = 555000;
		long begin = 1003;
		long end = 1002;
		boolean result = tree.add(begin, end, data);
		assertFalse(result);
	}
	
	@Test
	public void givenEmptyWhenAddValidIntervalThenOK(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		long data = 555000;
		long begin = 1001;
		long end = 1002;
		boolean result = tree.add(begin, end, data);
		assertTrue(result);
		assertEquals(1, tree.intervals().size());
		assertTrue(tree.intervals().containsKey(end));
	}

	@Test
	public void givenEmptyWhenAddIntervalThatOverlapsWithExistingThenFalse(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		long data = 555000;
		long begin = 1001;
		long end = 1010;
		boolean result = tree.add(begin, end, data);
		assertTrue(result);
		assertEquals(1, tree.intervals().size());
		assertTrue(tree.intervals().containsKey(end));
		
		long data2 = 555000;
		long begin2 = 1001;
		long end2 = 1010;
		boolean result2 = tree.add(begin2, end2, data2);
		assertFalse(result2);
		assertEquals(1, tree.intervals().size());
		assertTrue(tree.intervals().containsKey(end));		
	}
	
	@Test
	public void givenExistingRangeWhenOverlapsOnBeginReturnTrue(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		long data = 555000;
		long begin = 1001;
		long end = 1002;
		Interval interval = LongNonOverlappingIntervalTree.Interval.of(begin, end, data);
		tree.add(begin, end, data);

		long value = 1001;
		LongNonOverlappingIntervalTree.Interval outInterval = LongNonOverlappingIntervalTree.Interval.of(); 
		assertTrue(tree.overlaps(value, outInterval));
		assertTrue(interval.equals(outInterval));
	}

	@Test
	public void givenExistingRangeWhenSearchWithIntervalReturnTrue(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		long data = 555000;
		long begin = 1001;
		long end = 1002;
		Interval interval = LongNonOverlappingIntervalTree.Interval.of(begin, end, data);
		tree.add(begin, end, data);

		long value = 1001;
		LongNonOverlappingIntervalTree.Interval outInterval = LongNonOverlappingIntervalTree.Interval.of();
		assertTrue(tree.search(value, outInterval));
		assertTrue(interval.equals(outInterval));
	}

	@Test
	public void givenExistingRangesWhenSearchWithIntervalReturnTrueAndAllOverlappingIntervals(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		Interval interval = LongNonOverlappingIntervalTree.Interval.of(95, 101, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());

		interval = LongNonOverlappingIntervalTree.Interval.of(105, 109, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());
		
		interval = LongNonOverlappingIntervalTree.Interval.of(110, 120, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());

		interval = LongNonOverlappingIntervalTree.Interval.of(120, 125, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());

		LongNonOverlappingIntervalTree.Interval[] outIntervals = new LongNonOverlappingIntervalTree.Interval[10];
		for (int i = 0; i < outIntervals.length; i++){
			outIntervals[i] = LongNonOverlappingIntervalTree.Interval.of();
		}
		int numMatches = tree.search(115, 120, outIntervals);
		assertEquals(1, numMatches);

		numMatches = tree.search(110, 111, outIntervals);
		assertEquals(1, numMatches);

		numMatches = tree.search(109, 110, outIntervals);
		assertEquals(0, numMatches);

		numMatches = tree.search(102, 103, outIntervals);
		assertEquals(0, numMatches);

		numMatches = tree.search(119, 121, outIntervals);
		assertEquals(2, numMatches);
	}
		
	@Test
	public void givenExistingRangeWhenOverlapsOnEndReturnFalse(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		Interval interval = LongNonOverlappingIntervalTree.Interval.of(95, 101, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());

		LongNonOverlappingIntervalTree.Interval outInterval = LongNonOverlappingIntervalTree.Interval.of();
		assertFalse(tree.overlaps(101, outInterval));
	}

	@Test
	public void givenExistingRangesWhenOverlapsNothingReturnFalse(){
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		Interval interval = LongNonOverlappingIntervalTree.Interval.of(95, 101, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());

		interval = LongNonOverlappingIntervalTree.Interval.of(105, 109, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());

		LongNonOverlappingIntervalTree.Interval outInterval = LongNonOverlappingIntervalTree.Interval.of();
		assertFalse(tree.overlaps(104, outInterval));
	}

	@Test
	public void givenExistingRangesWhenRemoveOverlapsThenReturnOK(){		
		LongNonOverlappingIntervalTree tree = LongNonOverlappingIntervalTree.of();
		Interval interval = LongNonOverlappingIntervalTree.Interval.of(95, 101, 555000);
		tree.add(interval.begin(), interval.end(), interval.data());
		
		assertFalse(tree.removeOverlap(101));
		assertEquals(1, tree.intervals().size());
		
		assertFalse(tree.removeOverlap(94));
		assertEquals(1, tree.intervals().size());

		assertTrue(tree.removeOverlap(95));
		assertEquals(0, tree.intervals().size());
		
		tree.add(interval.begin(), interval.end(), interval.data());
		assertTrue(tree.removeOverlap(100));
		assertEquals(0, tree.intervals().size());
	}
}
