package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
public class MultiProducerFixedSizeBufferBasedHashMap {
	private static final class FixedSizeBuffer {
		private final AtomicBoolean dirty;
		private long sequence;
		private UnsafeBuffer buffer;
		public static FixedSizeBuffer of(int bufferSize){
			return new FixedSizeBuffer(bufferSize);
		}
		FixedSizeBuffer(int bufferSize){
			dirty = new AtomicBoolean(false);
			sequence = -1;
			buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferSize));
		}
		public long sequence(){ return sequence; }
		@SuppressWarnings("unused")
        public UnsafeBuffer buffer() { return buffer; }
		public void put(long sequence, UnsafeBuffer srcBuffer, int offset, int length){
			if (dirty.compareAndSet(false, true)){
				this.sequence = sequence;
				this.buffer.putBytes(0, srcBuffer, offset, length);				
			}
			// to extend this to become an open hash map - lock free open hashmap 
			throw new IllegalStateException("cannot write to a dirty buffer: seqeucen(" + sequence + ")");
		}
		public void removeAndPublish(long sequence, MarketDataPublisher publisher, MessageCodec codec){
			dirty.set(false);
		}
		public void remove(long sequence){
			dirty.set(false);
		}
	}
	
	private final FixedSizeBuffer[] buffer;
	private final int mask;
	private final int capacity;
	
	// sequence number wraps at 2^62 - 1 bytes, that's 17 million TB
	@SuppressWarnings("unused")
    private final AtomicLong nextWriteSeq;
	@SuppressWarnings("unused")
    private final AtomicLong committedWriteSeq;
	
	public static MultiProducerFixedSizeBufferBasedHashMap of(int capacity, int bufferSize){
		return new MultiProducerFixedSizeBufferBasedHashMap(capacity, bufferSize);
	}
		
	MultiProducerFixedSizeBufferBasedHashMap(int capacity, int bufferSize){
		this.nextWriteSeq = new AtomicLong();
		this.committedWriteSeq = new AtomicLong();
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.mask = this.capacity - 1;
		this.buffer = new FixedSizeBuffer[this.capacity];
		for (int i = 0; i < this.capacity; i++){
			this.buffer[i] = FixedSizeBuffer.of(bufferSize);
		}
	}

	/**
	 * Tightly pack bytes into this buffer
	 * @param srcBuffer
	 * @param offset
	 * @param length
	 * @return assigned sequence number of written bytes 
	 */
	public long put(long seq, UnsafeBuffer srcBuffer, int offset, int length){
		int index = (int)seq & mask;
		this.buffer[index].put(seq, srcBuffer, offset, length);
		return seq;
	}
	
	/**
	 * Read N bytes from bufferOffset into dstBuffer 
	 * @param bufferOffset
	 * @param length
	 * @param dstBuffer
	 */
	public void removeAndPublish(long readSeq, int length, MarketDataPublisher publisher, MessageCodec codec){
		int index = (int)readSeq & mask;
		FixedSizeBuffer found = buffer[index];
		if (readSeq == found.sequence()){
			found.removeAndPublish(readSeq, publisher, codec);
		}
		else {
			throw new IllegalStateException("cannot remove and publish (" + readSeq + ") from map");
		}
	}

	public void remove(long readSeq){
		int index = (int)readSeq & mask;
		FixedSizeBuffer found = buffer[index];
		if (readSeq == found.sequence()){
			found.remove(readSeq);
		}
		else {
			throw new IllegalStateException("cannot remove (" + readSeq + ") from map");
		}
	}	
	
}
