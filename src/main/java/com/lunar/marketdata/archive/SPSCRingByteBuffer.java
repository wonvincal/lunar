package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;

import com.lunar.util.BitUtil;

import org.agrona.concurrent.UnsafeBuffer;

public class SPSCRingByteBuffer {
	private final UnsafeBuffer buffer;
	private final int mask;
	private final int capacity;
	
	// sequence number wraps at .... so large
	private volatile long nextWriteSeq = 0;
	private volatile long nextReadSeq = 0;
	
	public static SPSCRingByteBuffer of(int capacity){
		return new SPSCRingByteBuffer(capacity);
	}
		
	SPSCRingByteBuffer(int capacity){
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(this.capacity));
		this.mask = this.buffer.capacity() - 1;
	}
	
	public boolean isEmpty(){
		return nextWriteSeq == nextReadSeq;
	}
	
	public long write(ByteBuffer srcBuffer, int length){
		if (nextWriteSeq - nextReadSeq > this.capacity){
			throw new IllegalArgumentException("out of bound");
		}
		int index = (int)nextWriteSeq & mask;
		// most of the time, we won't be reaching the end of the buffer
		if (index + length < this.capacity){
			buffer.putBytes((int)index, srcBuffer, length);			
		}
		else {
			// wrap
			int spaceBeforeEndOfBuffer = this.capacity - index;
			buffer.putBytes((int)index, srcBuffer, spaceBeforeEndOfBuffer);
			buffer.putBytes(0, srcBuffer, spaceBeforeEndOfBuffer, length - spaceBeforeEndOfBuffer);
		}
		long value = nextWriteSeq;
		nextWriteSeq += length;
		return value;
	}
	
	public void read(UnsafeBuffer dstBuffer, int length){
		if (nextWriteSeq - nextReadSeq < length){
			throw new IllegalArgumentException("not enough bytes");
		}
		int index = (int)nextReadSeq & mask;
		// most of the time, we won't be reaching the end of the buffer
		if (index + length < this.capacity){
			buffer.getBytes(index, dstBuffer, 0, length);			
		}
		else {
			// wrap
			int spaceBeforeEndOfBuffer = this.capacity - index;
			buffer.getBytes(index, dstBuffer, 0, spaceBeforeEndOfBuffer);
			buffer.getBytes(0, dstBuffer, spaceBeforeEndOfBuffer, length - spaceBeforeEndOfBuffer);
		}
		nextReadSeq += length;
	}
}
