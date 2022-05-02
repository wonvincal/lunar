package com.lunar.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

/**
 * Since each interval cannot overlap with other, we can implement this
 * with simple binary tree.
 * 
 * If we allow overlapping intervals, we will need to get/implement one of these:
 * https://github.com/stanfordnlp/CoreNLP/blob/master/src/edu/stanford/nlp/util/IntervalTree.java
 * http://algs4.cs.princeton.edu/93intersection/IntervalST.java.html
 * http://www.dgp.toronto.edu/people/JamesStewart/378notes/22intervals/ 
 * 
 * @author wongca
 *
 */
public class LongNonOverlappingIntervalTree {
	public static LongNonOverlappingIntervalTree of(){
		return new LongNonOverlappingIntervalTree(new Long2ObjectRBTreeMap<>());
	}
	public static class Interval {
		public static final long NULL_INTERVAL_VALUE = Long.MIN_VALUE;
		private long begin = NULL_INTERVAL_VALUE;
		private long endExclusive = NULL_INTERVAL_VALUE;
		private long data = NULL_INTERVAL_VALUE;
		
		public static Interval of(){
			return new Interval(NULL_INTERVAL_VALUE, NULL_INTERVAL_VALUE, NULL_INTERVAL_VALUE);
		}
		
		public static Interval of(long begin, long end, long data){
			return new Interval(begin, end, data);
		}
		
		Interval(long begin, long endExclusive, long data){
			this.begin = begin;
			this.endExclusive = endExclusive;
			this.data = data;
		}
		
		public long begin(){
			return begin;
		}
		
		public long end(){
			return endExclusive;
		}

		public long data(){
			return data;
		}

		public Interval begin(long value){
			this.begin = value;
			return this;
		}
		
		public Interval endExclusive(long value){
			this.endExclusive = value;
			return this;
		}

		public Interval data(long value){
			this.data = value;
			return this;
		}

		public Interval copyFrom(Interval interval){
			this.begin = interval.begin;
			this.endExclusive = interval.endExclusive;
			this.data = interval.data;
			return this;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (begin ^ (begin >>> 32));
			result = prime * result + (int) (data ^ (data >>> 32));
			result = prime * result + (int) (endExclusive ^ (endExclusive >>> 32));
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Interval other = (Interval) obj;
			if (begin != other.begin)
				return false;
			if (data != other.data)
				return false;
			if (endExclusive != other.endExclusive)
				return false;
			return true;
		}
		
	}

	private final Long2ObjectRBTreeMap<Interval> intervals;

	LongNonOverlappingIntervalTree(Long2ObjectRBTreeMap<Interval> intervals){
		this.intervals = intervals;
	}
	
	/**
	 * 
	 * @param value
	 * @param outInterval
	 * @return
	 */
	public boolean overlaps(long value, Interval outInterval){
		if (intervals.isEmpty()){
			return false;
		}
		
		// Check if this interval overlaps with any existing interval
		if (intervals.lastLongKey() >= value){
			Long2ObjectSortedMap<Interval> tailMap = intervals.tailMap(value + 1);
			ObjectBidirectionalIterator<Entry<Interval>> iterator = tailMap.long2ObjectEntrySet().iterator();
			while (iterator.hasNext()){
				Interval interval = iterator.next().getValue();
				if (overlapsImpl(value, interval)){
					outInterval.copyFrom(interval);
					return true;
				}
				else if (interval.end() <= value){
					return false;
				}
			}
		}
		return false;
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
	private boolean overlapsImpl(long begin, long endExclusive, Interval refInterval){
		if (refInterval.endExclusive <= begin || refInterval.begin >= endExclusive){
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
	private boolean overlapsImpl(long value, Interval refInterval){
		if (refInterval.endExclusive <= value || refInterval.begin > value){
			return false;
		}
		return true;
	}
	
	/**
	 * Search for intervals that contains input value
	 * @param value
	 * @param outIntervals
	 * @return
	 */
	public boolean search(long value, Interval outInterval){
		return overlaps(value, outInterval);
	}
	
	/**
	 * Search for intervals that contains input interval
	 * @param value
	 * @param outIntervals
	 * @return
	 */
	public int search(long begin, long endExclusive, Interval[] outIntervals){
		if (intervals.isEmpty()){
			return 0;
		}
		
		int index = -1;
		
		// Check 
		if (intervals.lastLongKey() > begin){
			// Find the first interval that can potentially cover 'begin'
			Long2ObjectSortedMap<Interval> tailMap = intervals.tailMap(begin + 1);
			ObjectBidirectionalIterator<Entry<Interval>> iterator = tailMap.long2ObjectEntrySet().iterator();
			while (iterator.hasNext()){
				Interval interval = iterator.next().getValue();
				if (overlapsImpl(begin, endExclusive, interval)){
					outIntervals[++index].copyFrom(interval);
				}
				else {
					break;
				}
			}
		}
		return index + 1;
	}
	
	/**
	 * If interval already exists, return false
	 * @param begin
	 * @param endExclusive
	 * @param data
	 * @return true if a new interval is added.  otherwise false
	 */
	public boolean add(long begin, long endExclusive, long data){
		if (begin > endExclusive){
			throw BEGIN_LARGER_THAN_END_EXCEPTION;
		}
		
		// Check if this interval overlaps with any existing interval
		if (!intervals.isEmpty()){
			if (intervals.lastLongKey() >= endExclusive + 1){
				Long2ObjectSortedMap<Interval> headMap = intervals.headMap(endExclusive + 1);
				ObjectBidirectionalIterator<Entry<Interval>> iterator = headMap.long2ObjectEntrySet().iterator();
				while (iterator.hasPrevious()){
					Interval interval = iterator.previous().getValue();
					if (interval.begin() <= begin && endExclusive <= interval.end()){
						return false;
					}
					else if (interval.end() <= begin){
						break;
					}
				}
			}
			else {
				Interval interval = intervals.get(intervals.lastLongKey());
				if (interval.begin() <= begin && endExclusive <= interval.end()){
					return false;
				}
			}
		}
		Interval newInterval = Interval.of(begin, endExclusive, data);
		intervals.put(endExclusive, newInterval);
		return true;
	}

	private static IllegalArgumentException BEGIN_LARGER_THAN_END_EXCEPTION = new IllegalArgumentException("Begin must not be larger than end");
	
	public ObjectCollection<Interval> values(){
		return intervals.values();
	}
	
	public boolean removeOverlap(long value){
		if (intervals.isEmpty()){
			return false;
		}
		
		// Check if this interval overlaps with any existing interval
		if (intervals.lastLongKey() >= value){
			Long2ObjectSortedMap<Interval> tailMap = intervals.tailMap(value + 1);
			ObjectBidirectionalIterator<Entry<Interval>> iterator = tailMap.long2ObjectEntrySet().iterator();
			while (iterator.hasNext()){
				Interval interval = iterator.next().getValue();
				if (overlapsImpl(value, interval)){
					iterator.remove();
					return true;
				}
				else if (interval.end() <= value){
					return false;
				}
			}
		}
		return false;
	}
	
	public void clear(){
		intervals.clear();
	}
	
	Long2ObjectRBTreeMap<Interval> intervals(){
		return intervals;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((intervals == null) ? 0 : intervals.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LongNonOverlappingIntervalTree other = (LongNonOverlappingIntervalTree) obj;
		if (intervals == null) {
			if (other.intervals != null)
				return false;
		} else if (!intervals.equals(other.intervals))
			return false;
		return true;
	}
}
