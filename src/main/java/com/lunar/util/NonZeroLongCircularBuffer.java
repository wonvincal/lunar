package com.lunar.util;

import org.agrona.BitUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Only support non zero values, because zero is used as null.
 * 
 * 
 * Thread safety: No
 * @author Calvin
 *
 */
public final class NonZeroLongCircularBuffer {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(NonZeroLongCircularBuffer.class);

	public static final long EMPTY_VALUE = 0;
	private long[] values;
	private int size;
	private final int capacity;
	private final int mask;
	private int readCursor;    
	private int writeCursor;
	
	public NonZeroLongCircularBuffer(int capacity){
		this.capacity = BitUtil.findNextPositivePowerOfTwo(capacity);
		values = new long[this.capacity];
		mask = this.capacity - 1;
		readCursor = 0;
		writeCursor = 0;
		size = 0;
	}
	
	int size(){
		return size;
	}
	
	public int capacity(){
		return capacity;
	}
	
	public boolean isEmpty(){
		// there is some kind of optimization to look ahead instead of working this
		// out on every call
		return size == 0;
	}
	
	public boolean isFull(){
		return size == capacity;
	}
	
	public long peek(){
		if (isEmpty()){
			return EMPTY_VALUE;
		}
		return values[readCursor & mask];
	}
	
	public long peek(int n){
		if (size < n){
			return EMPTY_VALUE;
		}
		return values[(readCursor + n - 1) & mask];
	}
				
	public long next(){
		if (isEmpty()){
			return EMPTY_VALUE;
		}
		long value = values[readCursor & mask];
		readCursor++;
		size--;
		return value;
	}

	/**
	 * Get value from next and clear the next n elements
	 * @param n
	 * @return
	 */
	public long getNextAndClear(int n){
		if (size < n){
			return EMPTY_VALUE;
		}
		long value = values[readCursor & mask];
		readCursor += n;
		size -= n;
		return value;
	}
	
	public void unsafeClear(int n){
		readCursor += n;
		size -= n;
	}
	
	public boolean add(long value){
		if (isFull()){
			return false;
		}
		assert value != EMPTY_VALUE;
		values[writeCursor & mask] = value;
		writeCursor++;
		size++;
		return true;
	}
}
