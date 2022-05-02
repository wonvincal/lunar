package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import com.lunar.message.binary.MessageCodec;
import com.lunar.util.BitUtil;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * A buffer that wraps around an {@link UnsafeBuffer}.  It will wrap from the end to the start of the buffer, but it
 * won't fragment any continuous stream of byte from the end to the head (?)
 * 
 * Note: Write is forward progress, but Read is not.
 * Note: No capacity checking!  Make sure you synchronize your access
 * 
 * @author wongca
 *
 */
public class MultiProducerNoBoundaryCheckByteBasedRingBuffer {
	private final UnsafeBuffer buffer;
	private final int mask;
	private final int capacity;
	
	// sequence number wraps at 2^62 - 1 bytes, that's 17 million TB
	private final AtomicLong nextWriteSeq;
	private final AtomicLong committedWriteSeq;
	
	public static MultiProducerNoBoundaryCheckByteBasedRingBuffer of(int capacity){
		return new MultiProducerNoBoundaryCheckByteBasedRingBuffer(capacity);
	}
		
	MultiProducerNoBoundaryCheckByteBasedRingBuffer(int capacity){
		this.nextWriteSeq = new AtomicLong();
		this.committedWriteSeq = new AtomicLong();
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(this.capacity));
		this.mask = this.buffer.capacity() - 1;
	}

	/**
	 * Tightly pack bytes into this buffer
	 * @param srcBuffer
	 * @param offset
	 * @param length
	 * @return assigned sequence number of written bytes 
	 */
	public long write(UnsafeBuffer srcBuffer, int offset, int length){
		// claim buffer first
		// then determine if it exceeds the capacity
		long claimedWriteSeq = -1;
		long nextClaimedWriteSeq = -1;
		
		// claimed: 100, nextClaimed: 105
		do {
			claimedWriteSeq = nextWriteSeq.get();
			nextClaimedWriteSeq = claimedWriteSeq + length; 
		}
		while (!nextWriteSeq.compareAndSet(claimedWriteSeq, nextClaimedWriteSeq));
		
		int index = (int)claimedWriteSeq & mask;
		// most of the time, we won't be reaching the end of the buffer
		if (index + length < this.capacity){
			buffer.putBytes((int)index, srcBuffer, offset, length);			
		}
		else {
			// wrap
			int spaceBeforeEndOfBuffer = this.capacity - index;
			buffer.putBytes((int)index, srcBuffer, offset, spaceBeforeEndOfBuffer);
			buffer.putBytes(0, srcBuffer, offset + spaceBeforeEndOfBuffer, length - spaceBeforeEndOfBuffer);
		}

		// Thread A: i) original nextWriteSeq: 100, new nextWriteSeq: 105, 
		//			ii) original commitedWriteSeq = 100, expect to set commitedWriteSeq: 105
		// Thread B: i) original nextWriteSeq: 105, new nextWriteSeq: 108, 
		//			ii) original commitedWriteSeq = 100, expect to set commitedWriteSeq: 108
		// It is possible that the following sequence of events happened:
		// Thread A i)
		// Thread B i)
		// Thread B ii) - In this case, we want Thread A ii) to be processed first
		// Thread A ii)		
		// One second timeout(?)
		long timeoutAt = System.nanoTime() + 1_000_000_000l; 
		while (!committedWriteSeq.compareAndSet(claimedWriteSeq, nextClaimedWriteSeq)){
			LockSupport.parkNanos(1_000_000_000);
			if (System.nanoTime() > timeoutAt){
				throw new IllegalStateException("possible deadlock");
			}
		}
		return nextClaimedWriteSeq;
	}
	
	/**
	 * Read N bytes from bufferOffset into dstBuffer 
	 * @param bufferOffset
	 * @param length
	 * @param dstBuffer
	 */
	public void readBySeq(long readSeq, int length, MarketDataPublisher publisher, MessageCodec codec){
		if (readSeq + length < committedWriteSeq.get()){
			throw new IllegalArgumentException("cannot ready more than what we have");
		}
		int index = (int)readSeq & mask;
		// most of the time, we won't be reaching the end of the buffer
		if (index + length < this.capacity){
			publisher.publish(buffer, index, length, codec);
		}
		else {
			// wrapped around end to start, need to copy it out into a new buffer
			int spaceBeforeEndOfBuffer = this.capacity - index;
			UnsafeBuffer tempBuffer = new UnsafeBuffer(ByteBuffer.allocate(length));
			buffer.getBytes(index, tempBuffer, 0, spaceBeforeEndOfBuffer);
			buffer.getBytes(0, tempBuffer, spaceBeforeEndOfBuffer, length - spaceBeforeEndOfBuffer);
			publisher.publish(buffer, index, length, codec);
		}
	}	
}
