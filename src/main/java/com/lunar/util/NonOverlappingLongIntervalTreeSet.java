package com.lunar.util;

import java.util.function.Consumer;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2LongRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;

/**
 * Key: [Data (32 bits - int) EndExclusive (32 bits - long)]
 * Value: [TheoBucketSize (32 bits - int) Begin (32 bits - long)]
 * @author wongca
 *
 */
public class NonOverlappingLongIntervalTreeSet {
	private final Long2LongRBTreeMap intervals;
	
	public static final long HIGHER_ORDER_MASK = 0xFFFFFFFF00000000l;
	public static final long LOWER_ORDER_MASK = 0x00000000FFFFFFFFl;
	
	public static NonOverlappingLongIntervalTreeSet of(){
		return new NonOverlappingLongIntervalTreeSet();
	}

	NonOverlappingLongIntervalTreeSet(){
		intervals = new Long2LongRBTreeMap(new LongComparator() {
			
			@Override
			public int compare(Long o1, Long o2) {
				return Long.signum((o1 & LOWER_ORDER_MASK) - (o2 & LOWER_ORDER_MASK));
			}
			
			@Override
			public int compare(long k1, long k2) {
				return Long.signum((k1 & LOWER_ORDER_MASK) - (k2 & LOWER_ORDER_MASK));
			}
		});
	}

	private static class LongIntervalEntry implements Long2LongMap.Entry {
		private long key;
		private long value;
		
		public void key(long key){
			this.key = key;
		}
		@Override
		public Long setValue(Long value) {
			this.value = value;
			return value;
		}

		@Override
		public Long getKey() {
			return key;
		}

		@Override
		public long getLongKey() {
			return key;
		}

		@Override
		public Long getValue() {
			return value;
		}

		@Override
		public long setValue(long value) {
			this.value = value;
			return value;
		}

		@Override
		public long getLongValue() {
			return value;
		}
		
	}
	
	private final LongIntervalEntry searchInterval = new LongIntervalEntry();
	
//	public void replace(Entry entry, long begin, long endExclusive, int data, long theoBucketSize){
//		// If there is no change to endExclusive, replace
//		// Else, remove and add
//		long key = ((long)data << 32) | endExclusive;
//		long value = ((long)theoBucketSize << 32) | begin;
//		
//		if (entry.getLongKey() != key){
//			entry.setValue(value);
//		}
//		else {
//			intervals.
//		}
//	}
//	
	public boolean add(long begin, long endExclusive, int data, long theoBucketSize){
		if (begin > endExclusive){
			throw new IllegalArgumentException("Begin must not be larger than end");
		}
		// TODO - a configurable safety check
		
		if (!intervals.isEmpty()){
//			System.out.println("First long key: " + intervals.firstLongKey() + ", value: " + intervals.get(intervals.firstLongKey()));
//			System.out.println("First long key: " + Long.toBinaryString(intervals.firstLongKey()));
			searchInterval.key(endExclusive + 1);
			ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
			if (iterator.hasNext()){
				Entry interval = iterator.next();
				long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
				if (intervalBegin < endExclusive){
					return false;
				}
				iterator.previous();
			}
			while (iterator.hasPrevious()){
				Entry interval = iterator.previous();
				long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
				long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
				if (isOverlapping(begin, endExclusive, intervalBegin, intervalEndExclusive)){
					return false;
				}
				else if (intervalEndExclusive <= begin){
					break;
				}
			}
		}
		long key = ((long)data << 32) | endExclusive; 
//		System.out.println("Added: key: " + Long.toBinaryString(key));
//		System.out.println("Added: endExclusive: " + Long.toBinaryString(endExclusive));
		long value = ((long)theoBucketSize << 32) | begin;
		intervals.put(key, value);
		return true;
	}
	
	public boolean addWithNoOverlapCheck(long begin, long endExclusive, int data, long theoBucketSize){
		if (begin > endExclusive){
			throw new IllegalArgumentException("Begin must not be larger than end");
		}
		long key = ((long)data << 32) | endExclusive; 
		long value = ((long)theoBucketSize << 32) | begin;
		intervals.put(key, value);
		return true;
	}

	public boolean addWithNoOverlapCheck(LongInterval newInterval){
		if (newInterval.begin() > newInterval.endExclusive()){
			throw new IllegalArgumentException("Begin must not be larger than end");
		}
		long key = ((long)newInterval.data() << 32) | newInterval.endExclusive(); 
		long value = ((long)newInterval.theoBucketSize() << 32) | newInterval.begin();
		intervals.put(key, value);
		return true;
	}
	
	public boolean remove(long value){
		if (intervals.remove(value) != intervals.defaultReturnValue()){
			return true;
		}
		return false;
	}

	// If overlaps, return overlaps + next
	// If not overlap, return greater
	
	private boolean overlaps(long value, boolean remove, LongInterval outInterval){
		// Check if this interval overlaps with any existing interval
		// Search for interval with endExclusive > value, that's the first interval
		// that can possibly cover the input value
		// E.g. Overlaps: 105,
		// [100, 105]
		// [107, 115]
		// 1) Search first interval with endExclusive > 105, that's the one that can contain this value
		// 2) Find [107, 115]
		// 3) Not overlap
		searchInterval.key(value);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		while (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(value, intervalBegin, intervalEndExclusive)){
				if (remove){
					iterator.remove();
				}
				outInterval.begin(intervalBegin);
				outInterval.endExclusive(intervalEndExclusive);
				outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));
				
				return true;
			}
			else {
				// intervalEndExclusive must be greater than value
//				if (intervalEndExclusive <= value){
//			}
				// Shortcut - if necessary
				return false;
			}
		}
		return false;
	}

	public boolean overlaps(long value, LongInterval outInterval){
		if (intervals.isEmpty()){
			return false;
		}
		return overlaps(value, false, outInterval);
	}

	private boolean isOverlapping(long begin, long endExclusive, long refBegin, long refEndExclusive){
		if (refEndExclusive <= begin || refBegin >= endExclusive){
			return false;
		}
		return true;
	}
	
	private boolean isOverlapping(long value, long refBegin, long refEndExclusive){
		if (refEndExclusive <= value || refBegin > value){
			return false;
		}
		return true;
	}
	
	public boolean search(long value, LongInterval outInterval){
		return overlaps(value, outInterval);
	}
	
	public boolean searchOverlapOrSmaller(long value, LongInterval outInterval){
		searchInterval.key(value + 1);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		if (iterator.hasPrevious()){
			Entry interval = iterator.previous();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;			
			outInterval.begin(intervalBegin);
			outInterval.endExclusive(intervalEndExclusive);
			outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
			outInterval.theoBucketSize((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32);
			return true;
		}
		return false;
	}
	
	public boolean searchOverlapOrGreater(long value, LongInterval outInterval){
		searchInterval.key(value + 1);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		if (iterator.hasPrevious()){
			Entry interval = iterator.previous();
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;			
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			if (isOverlapping(value, intervalBegin, intervalEndExclusive)){
				// Found overlapping
				outInterval.begin(intervalBegin);
				outInterval.endExclusive(intervalEndExclusive);
				outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outInterval.theoBucketSize((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32);
				return true;
			}
			iterator.skip(1);
		}
		if (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;			
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			// Found overlapping
			outInterval.begin(intervalBegin);
			outInterval.endExclusive(intervalEndExclusive);
			outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
			outInterval.theoBucketSize((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32);
			return true;
		}
		return false;
	}

	public boolean searchOverlapAndGreater(long value, LongInterval outInterval, LongInterval outNextInterval){
		if (intervals.isEmpty()){
			return false;
		}

		// Check if this interval overlaps with any existing interval
		// Search for interval with endExclusive > value, that's the first interval
		// that can possibly cover the input value
		// E.g. Overlaps: 105,
		// [100, 105]
		// [107, 115]
		// 1) Search first interval with endExclusive > 105, that's the one that can contain this value
		// 2) Find [107, 115]
		// 3) Not overlap
		searchInterval.key(value);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		if (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(value, intervalBegin, intervalEndExclusive)){
				outInterval.begin(intervalBegin);
				outInterval.endExclusive(intervalEndExclusive);
				outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));
				
				// Next bucket is here
				if (iterator.hasNext()){
					interval = iterator.next();
					intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
					intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
					outNextInterval.begin(intervalBegin);
					outNextInterval.endExclusive(intervalEndExclusive);
					outNextInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
					outNextInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));					
				}
				else{
					outNextInterval.clear();
				}
				return true;
			}
			else {
				outInterval.clear();
				outNextInterval.begin(intervalBegin);
				outNextInterval.endExclusive(intervalEndExclusive);
				outNextInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outNextInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));				
				return true;
			}
		}
		return false;
	}

	public boolean searchOverlapAndSmaller(long value, LongInterval outInterval, LongInterval outNextInterval){
		if (intervals.isEmpty()){
			return false;
		}

		// Check if this interval overlaps with any existing interval
		// Search for interval with endExclusive > value, that's the first interval
		// that can possibly cover the input value
		// E.g. Overlaps: 105,
		// [100, 105]
		// [107, 115]
		// 1) Search first interval with endExclusive > 105, that's the one that can contain this value
		// 2) Find [107, 115]
		// 3) Not overlap
		searchInterval.key(value);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		if (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(value, intervalBegin, intervalEndExclusive)){
				outInterval.begin(intervalBegin);
				outInterval.endExclusive(intervalEndExclusive);
				outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));
				
				iterator.back(1);
				
				// Next bucket is here
				if (iterator.hasPrevious()){
					interval = iterator.previous();
					intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
					intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
					outNextInterval.begin(intervalBegin);
					outNextInterval.endExclusive(intervalEndExclusive);
					outNextInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
					outNextInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));					
				}
				else{
					outNextInterval.clear();
				}
				return true;
			}
			iterator.back(1);
		}
		if (iterator.hasPrevious()){
			outInterval.clear();
			
			Entry interval = iterator.previous();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			outNextInterval.begin(intervalBegin);
			outNextInterval.endExclusive(intervalEndExclusive);
			outNextInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
			outNextInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));
			return true;
		}
		/*
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		while (iterator.hasPrevious()){ // Has at or less than search key
			Entry interval = iterator.previous();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(value, intervalBegin, intervalEndExclusive)){
				outInterval.begin(intervalBegin);
				outInterval.endExclusive(intervalEndExclusive);
				outInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));
				
				// Next bucket is here
				if (iterator.hasPrevious()){
					interval = iterator.previous();
					intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
					intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
					outNextInterval.begin(intervalBegin);
					outNextInterval.endExclusive(intervalEndExclusive);
					outNextInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
					outNextInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));					
				}
				else{
					outNextInterval.clear();
				}
				return true;
			}
			else {
				outInterval.clear();
				outNextInterval.begin(intervalBegin);
				outNextInterval.endExclusive(intervalEndExclusive);
				outNextInterval.data((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32));
				outNextInterval.theoBucketSize(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));				
				return true;
			}
		}
		*/
		return false;
	}
	
	public Entry searchExact(long endExclusive){
        searchInterval.key(endExclusive);
        ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
        if (iterator.hasPrevious()){
            Entry interval = iterator.previous();
            long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
            if (endExclusive == intervalEndExclusive){
                return interval;
            }
        }
        return null;
	}
	
	/**
	 * This is not a good method, exposing too much interval stuff
	 * @param value
	 * @return
	 */
	public ObjectBidirectionalIterator<Entry> searchExactAndIterate(long value){
		searchInterval.key(value + 1);
		return intervals.long2LongEntrySet().iterator(searchInterval);
	}
	
	public void forEach(Consumer<Entry> action){
		intervals.long2LongEntrySet().forEach(action);
	}
	
	public int search(long begin, long endExclusive,
			LongInterval outSmallerNonOverlappingInterval,
			LongInterval outGreaterNonOverlappingInterval,
			LongInterval[] outOverlappingIntervals,
			Entry[] outOverlappingMapEntries){
		outSmallerNonOverlappingInterval.clear();
		outGreaterNonOverlappingInterval.clear();
		if (begin >= endExclusive || intervals.isEmpty()){
			return 0;
		}
		
		int index = -1;

		searchInterval.key(endExclusive);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);

		// Next - least greater than searchInterval
		// Previous - equal or less than searchInterval
		// Check hasNext to see if the interval overlaps with this interval, if yes, remove
		// Then go backward until iterator.interval.endExclusive <= begin
		if (iterator.hasNext()){
			// next().endExclusive > input.endExclusive if exists
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(begin, endExclusive, intervalBegin, intervalEndExclusive)){
				int data = (int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
				long theoBucketSize = (interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32;
				outOverlappingIntervals[++index].set(intervalBegin, intervalEndExclusive, data, theoBucketSize);				
				outOverlappingMapEntries[index] = interval;
				int backCount = 1;
				if (iterator.hasNext()){
					interval = iterator.next();
					intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
					intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
					data = (int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
					theoBucketSize = (interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32;
					assert !isOverlapping(begin, endExclusive, intervalBegin, intervalEndExclusive);
					outGreaterNonOverlappingInterval.set(intervalBegin, intervalEndExclusive, data, theoBucketSize);
					backCount++;
				}
				// Reverse back
				iterator.back(backCount);
			}
			else {
				int data = (int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
				int theoBucketSize = (int)((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32);
				outGreaterNonOverlappingInterval.set(intervalBegin, intervalEndExclusive, data, theoBucketSize);
				iterator.back(1);
			}
		}
		// previous().endExclusive <= input.endExclusive if exists
		while (iterator.hasPrevious()){
			Entry interval = iterator.previous();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			int data = (int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
			long theoBucketSize = (interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32;
			if (isOverlapping(begin, endExclusive, intervalBegin, intervalEndExclusive)){
				outOverlappingIntervals[++index].set(intervalBegin, intervalEndExclusive, data, theoBucketSize);
				outOverlappingMapEntries[index] = interval;
			}
			else {
				outSmallerNonOverlappingInterval.set(intervalBegin, intervalEndExclusive, data, theoBucketSize);
				break;
			}
		}
		return index + 1;
	}
	
	public int removeOverlaps(long begin, long endExclusive, LongInterval[] outIntervals){
		if (begin >= endExclusive){
			return 0;
		}

		if (intervals.isEmpty()){
			return 0;
		}
		
		int index = -1;

		searchInterval.key(endExclusive);
		ObjectBidirectionalIterator<Entry> iterator = intervals.long2LongEntrySet().iterator(searchInterval);
		
		// Check hasNext to see if the interval overlaps with this interval, if yes, remove
		// Then go backward until iterator.interval.endExclusive <= begin
		if (iterator.hasNext()){
			Entry interval = iterator.next();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(begin, endExclusive, intervalBegin, intervalEndExclusive)){
				int data = (int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
				long theoBucketSize = (interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32;
				outIntervals[++index].set(intervalBegin, intervalEndExclusive, data, theoBucketSize);
				iterator.remove();
			}
			// Reverse back
			iterator.previous();
		}
		while (iterator.hasPrevious()){
			Entry interval = iterator.previous();
			long intervalEndExclusive = interval.getLongKey() & LOWER_ORDER_MASK;
			long intervalBegin = interval.getLongValue() & LOWER_ORDER_MASK;
			if (isOverlapping(begin, endExclusive, intervalBegin, intervalEndExclusive)){
				int data = (int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32);
				long theoBucketSize = (interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32;
				outIntervals[++index].set(intervalBegin, intervalEndExclusive, data, theoBucketSize);
				iterator.remove();
			}
			else if (intervalEndExclusive <= begin){
				break;
			}
		}
		return index + 1;
	}
	
	private final LongInterval wasted = LongInterval.of();
	public boolean removeOverlap(long value){
		if (intervals.isEmpty()){
			return false;
		}
		
		return overlaps(value, true, wasted);
	}
	
	Long2LongRBTreeMap intervals(){
		return intervals;
	}

	public void replaceWith(NonOverlappingLongIntervalTreeSet from){
		this.intervals.clear();
		for (Entry entry : from.intervals.long2LongEntrySet()){
			this.intervals.put(entry.getLongKey(), entry.getLongValue());
		}
	}
	
	public int intervals(LongInterval[] outIntervals){
		int num = 0;
		for (Entry interval : intervals.long2LongEntrySet()){
			outIntervals[num].set(interval.getLongValue() & LOWER_ORDER_MASK,
					interval.getLongKey() & LOWER_ORDER_MASK,
					(int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32),
					((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32));
			num++;
		}
		return num;
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

	public static long createValue(long begin, long theoBucketSize){
		return (theoBucketSize << 32) | begin;
	}

	private final StringBuilder builder = new StringBuilder();
	
	@Override
	public String toString() {
		builder.setLength(0);
		for (Entry interval : intervals.long2LongEntrySet()){
			builder.append('[')
				.append(interval.getLongValue() & LOWER_ORDER_MASK)
				.append(',')
				.append(interval.getLongKey() & LOWER_ORDER_MASK)
				.append("]->[")
				.append((int)((interval.getLongKey() & HIGHER_ORDER_MASK) >>> 32))
				.append(", theoBucketSize: ")
				.append(((interval.getLongValue() & HIGHER_ORDER_MASK) >>> 32))
				.append("]|");
		}
		return builder.toString();
	}
}
