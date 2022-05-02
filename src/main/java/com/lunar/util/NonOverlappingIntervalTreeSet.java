package com.lunar.util;

import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

public class NonOverlappingIntervalTreeSet {
	private static IllegalArgumentException BEGIN_LARGER_THAN_END_EXCEPTION = new IllegalArgumentException("Begin must not be larger than end");

	public static NonOverlappingIntervalTreeSet of(){
		ObjectRBTreeSet<LongInterval> intervals = new ObjectRBTreeSet<>((o1, o2) -> {
			return BitUtil.compare(o1.endExclusive(), o2.endExclusive());
		});
		return new NonOverlappingIntervalTreeSet(intervals);
	}

	private final ObjectRBTreeSet<LongInterval> intervals;

	NonOverlappingIntervalTreeSet(ObjectRBTreeSet<LongInterval> intervals){
		this.intervals = intervals;
	}

	private final LongInterval searchInterval = LongInterval.of();
	private static final LongInterval NULL_INTERVAL = LongInterval.of();
	
	/**
	 * TODO Create an object pool
	 * @param begin
	 * @param endExclusive
	 * @param data
	 * @return
	 */
	public boolean add(long begin, long endExclusive, int data){
		if (begin > endExclusive){
			throw BEGIN_LARGER_THAN_END_EXCEPTION;
		}
		
		// Check if this interval overlaps with any existing interval
		if (!intervals.isEmpty()){
			searchInterval.endExclusive(endExclusive + 1);
			ObjectBidirectionalIterator<LongInterval> iterator = intervals.iterator(searchInterval);
			while (iterator.hasPrevious()){
				LongInterval interval = iterator.previous();
				if (interval.begin() <= begin && endExclusive <= interval.endExclusive()){
					return false;
				}
				else if (interval.endExclusive() <= begin){
					break;
				}
			}
		}
		LongInterval newInterval = LongInterval.of(begin, endExclusive, data, LongInterval.NULL_DATA_VALUE);
		return intervals.add(newInterval);
	}
	
	public boolean addWithNoOverlapCheck(LongInterval newInterval){
		if (newInterval.begin() > newInterval.endExclusive()){
			throw BEGIN_LARGER_THAN_END_EXCEPTION;
		}
		return intervals.add(newInterval);
	}
	
	public boolean overlaps(long value, LongInterval outInterval){
		if (intervals.isEmpty()){
			return false;
		}
		
		LongInterval result = overlaps(value, false);
		if (result != NULL_INTERVAL){
			outInterval.copyFrom(result);
			return true;
		}
		return false;
	}
	
	private LongInterval overlaps(long value, boolean remove){
		// Check if this interval overlaps with any existing interval
		// Search for interval with endExclusive > value, that's the first interval
		// that can possibly cover the input value
		// E.g. Overlaps: 105,    
		// [100, 105]
		// [107, 115]
		// 1) Search first interval with endExclusive > 105, that's the one that can contain this value
		// 2) Find [107, 115]
		// 3) Not overlap
		searchInterval.endExclusive(value);
		ObjectBidirectionalIterator<LongInterval> iterator = intervals.iterator(searchInterval);
		while (iterator.hasNext()){
			LongInterval interval = iterator.next();
			if (isOverlapping(value, interval)){
				if (remove){
					iterator.remove();
				}
				return interval;
			}
			else if (interval.endExclusive() <= value){
				// Shortcut - if necessary
				return NULL_INTERVAL;
			}
		}
		return NULL_INTERVAL;
	}
	
	/**
	 * Search for intervals that contains input value
	 * @param value
	 * @param outIntervals
	 * @return
	 */
	public boolean search(long value, LongInterval outInterval){
		return overlaps(value, outInterval);
	}

	/**
	 * 
	 * @param begin
	 * @param endExclusive
	 * @param outIntervals
	 * @return Number of overlapping intervals
	 */
	public int removeOverlaps(long begin, long endExclusive, LongInterval[] outIntervals){
		if (begin >= endExclusive){
			return 0;
		}

		if (intervals.isEmpty()){
			return 0;
		}
		
		int index = -1;

		searchInterval.endExclusive(endExclusive);
		ObjectBidirectionalIterator<LongInterval> iterator = intervals.iterator(searchInterval);
		
		// Check hasNext to see if the interval overlaps with this interval, if yes, remove
		// Then go backward until iterator.interval.endExclusive <= begin
		if (iterator.hasNext()){
			LongInterval interval = iterator.next();
			if (isOverlapping(begin, endExclusive, interval)){
				outIntervals[++index] = interval;
				iterator.remove();
			}
			// Reverse back
			iterator.previous();
		}
		while (iterator.hasPrevious()){
			LongInterval interval = iterator.previous();
			if (isOverlapping(begin, endExclusive, interval)){
				outIntervals[++index] = interval;
				iterator.remove();
			}
			else if (interval.endExclusive() <= begin){
				break;
			}
		}
		return index + 1;
	}
	
	/**
	 * 
	 * @param begin
	 * @param endExclusive
	 * @param outIntervals
	 * @return Number of overlapping intervals
	 */
	public int removeOverlaps(long begin, long endExclusive){
		if (begin >= endExclusive){
			return 0;
		}

		if (intervals.isEmpty()){
			return 0;
		}
		
		int numOverlaps = 0;

		searchInterval.endExclusive(endExclusive);
		ObjectBidirectionalIterator<LongInterval> iterator = intervals.iterator(searchInterval);
		
		// Check hasNext to see if the interval overlaps with this interval, if yes, remove
		// Then go backward until iterator.interval.endExclusive <= begin
		if (iterator.hasNext()){
			LongInterval interval = iterator.next();
			if (isOverlapping(begin, endExclusive, interval)){
				iterator.remove();
				numOverlaps++;
			}
			// Reverse back
			iterator.previous();
		}
		while (iterator.hasPrevious()){
			LongInterval interval = iterator.previous();
			if (isOverlapping(begin, endExclusive, interval)){
				iterator.remove();
				numOverlaps++;
			}
			else if (interval.endExclusive() <= begin){
				break;
			}
		}
		return numOverlaps;
	}

	public int search(long begin, long endExclusive, 
			LongInterval outSmallerNonOverlappingInterval,
			LongInterval outGreaterNonOverlappingInterval,
			LongInterval[] outOverlappingIntervals){
		outSmallerNonOverlappingInterval.clear();
		outGreaterNonOverlappingInterval.clear();
		if (begin >= endExclusive || intervals.isEmpty()){
			return 0;
		}
		
		int index = -1;

		searchInterval.endExclusive(endExclusive);
		ObjectBidirectionalIterator<LongInterval> iterator = intervals.iterator(searchInterval);

		// Next - least greater than searchInterval
		// Previous - equal or less than searchInterval
		// Check hasNext to see if the interval overlaps with this interval, if yes, remove
		// Then go backward until iterator.interval.endExclusive <= begin
		if (iterator.hasNext()){
			// next().endExclusive > input.endExclusive if exists
			LongInterval interval = iterator.next();
			if (isOverlapping(begin, endExclusive, interval)){
				outOverlappingIntervals[++index].copyFrom(interval);
				if (iterator.hasNext()){
					interval = iterator.next();
					assert !isOverlapping(begin, endExclusive, interval);
					outGreaterNonOverlappingInterval.copyFrom(interval);
					
					// Reverse back
					iterator.back(2);
				}
			}
			else {
				outGreaterNonOverlappingInterval.copyFrom(interval);
				iterator.back(1);
			}
		}
		// previous().endExclusive <= input.endExclusive if exists
		while (iterator.hasPrevious()){
			LongInterval interval = iterator.previous();
			if (isOverlapping(begin, endExclusive, interval)){
				outOverlappingIntervals[++index].copyFrom(interval);
			}
			else {
				outSmallerNonOverlappingInterval.copyFrom(interval);
				break;
			}
		}
		return index + 1;
	}
	
	/**
	 * Line1: [A, B] - begin, endExclusive
	 * Line2: [C, D] - refInterval
	 * 
	 * @param begin
	 * @param endExclusive
	 * @param refInterval
	 * @return
	 */
	private boolean isOverlapping(long begin, long endExclusive, LongInterval refInterval){
		if (refInterval.endExclusive() <= begin || refInterval.begin() >= endExclusive){
			return false;
		}
		return true;
	}

	/**
	 * Line1: [A] - begin, endExclusive
	 * Line2: [C, D] - refInterval
	 * 
	 * @param value
	 * @param refInterval
	 * @return
	 */
	private boolean isOverlapping(long value, LongInterval refInterval){
		if (refInterval.endExclusive() <= value || refInterval.begin() > value){
			return false;
		}
		return true;
	}
	
	public boolean removeOverlap(long value){
		if (intervals.isEmpty()){
			return false;
		}
		
		LongInterval result = overlaps(value, true);
		if (result != NULL_INTERVAL){
			return true;
		}
		return false;
	}
	
	ObjectRBTreeSet<LongInterval> intervals(){
		return intervals;
	}
	
	public void clear(){
		intervals.clear();
	}
	
	public int size(){
		return intervals.size();
	}
	
	public boolean isEmpty(){
		return intervals.isEmpty();
	}

}
