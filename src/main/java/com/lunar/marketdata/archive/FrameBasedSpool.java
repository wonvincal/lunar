package com.lunar.marketdata.archive;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lunar.message.binary.MessageCodec;
import com.lunar.util.BitUtil;

import org.agrona.concurrent.UnsafeBuffer;

public class FrameBasedSpool {
	private final int mask;
	private final int capacity;
	private final Slot[] slots;
	private final MultiProducerFixedSizeBufferBasedHashMap extension;
	
	/**
	 * This is set by the reader, anything before this should be cleared
	 */
	private volatile long minAllowableSeq;
	private final RawMessageWalker walker;
	
	FrameBasedSpool(int capacity, int byteBufferCapacity, RawMessageWalker walker, int frameSize, int maxMessageSize){
		this.minAllowableSeq = Long.MAX_VALUE;
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.slots = new Slot[this.capacity];
		for (int i = 0; i < this.capacity; i++){
			this.slots[i] = new Slot(frameSize);
		}
		this.mask = this.capacity - 1;
		this.walker = walker;
		this.bufferSize = frameSize;
		this.extension = new MultiProducerFixedSizeBufferBasedHashMap(capacity, maxMessageSize);
	}

	public boolean isAvailable(long seq){
		return this.slots[(int)seq & mask].dirty();
	}
	
	public boolean store(long minAllowableSeq, long seq, int msgCount, UnsafeBuffer srcBuffer, int offset, int length){
		if (seq < minAllowableSeq){
			throw new IllegalArgumentException("seq(" + seq + ") must be >= minAllowableSeq(" + minAllowableSeq +")");
		}
		if (capacity < (seq + msgCount - minAllowableSeq)){
			throw new IllegalArgumentException("exceed capacity(" + capacity + ")");
		}
		// clear up slots that are no longer relevant
		long origMinAllowableSeq = this.minAllowableSeq;
		this.minAllowableSeq = minAllowableSeq;
		for (long i = origMinAllowableSeq; i < minAllowableSeq; i++){
			this.slots[(int) i & mask].clear();
		}
		for (long i = seq; i < seq + msgCount; i++){
			// claim a slot
			Slot slot = this.slots[(int)seq & mask];
			if (slot.claim()){
				length = walker.getLength(srcBuffer, offset);
				slot.write(seq, srcBuffer, offset, length);				
				// get length from reading message
				// can for a method reference, but i am afraid that inlining won't work				
				offset = walker.getNextOffset(srcBuffer, offset);
			}
		}
		return true;
	}
	
	// spool has to have a minimum allowable seq reference, anything lower than that won't be stored
	// Arb waiting for 101: [ ]
	// spool:                   [X] [X] [X] [X] [X] [ ]
	//                      101 102 103 104 105 105 106
	// Thread A: Receives 101, start to process spooled messages
	// Thread B: Receives 102, 103, 104, 105, 106
	/**
	 * Client has the control for at which sequence to start publishing
	 * @param fromSeq
	 * @param publisher
	 * @return sequence number of next empty slot
	 */
	public final long loadAndPublish(long seq, MarketDataPublisher publisher, MessageCodec codec){
		Slot slot = this.slots[(int)seq & mask];
		while (slot.dirty()){
			// publish one chunk of bytes at a time
			slot.clear();
			seq++;
			slot = this.slots[(int)seq & mask];
			slot.publish(publisher, codec);
		}
		return seq;
	}

	private final int bufferSize;
	private class LinkedUnsafeBuffer{
		private int length;
		private final UnsafeBuffer buffer;
		private boolean extended;
		
		LinkedUnsafeBuffer(boolean allocateDirect){
			if (allocateDirect){
				buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferSize));
			}
			else{
				buffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
			}
			extended = false;
		}
		
		public void write(long seq, UnsafeBuffer srcBuffer, int offset, int length){
			this.length = length;
			if (bufferSize > length){
				buffer.putBytes(0, srcBuffer, offset, length);
			}
			else{
				// this may have an significant impact on the performance
				// one way is to connect this to build a RingBuffer with 
				// UnsafeBuffer of 1500 bytes (Good, let's do this!)
				extended = true;
				extension.put(seq, srcBuffer, offset, length);
			}
		}
		
		public void removeAndPublish(long seq, MarketDataPublisher publisher, MessageCodec codec){
			if (!extended){
				publisher.publish(buffer, 0, length, codec);				
			}
			else{
				extension.removeAndPublish(seq, length, publisher, codec);
			}			
		}
		
		public void clear(long seq){
			if (extended){
				extension.remove(seq);
				extended = false;
			}
		}
	}
	
	/**
	 * Each slot corresponds to one message
	 * @author wongca
	 *
	 */
	private class Slot {
		private LinkedUnsafeBuffer linkedBuffer;
		private long seq;
		private final AtomicBoolean dirty;
		Slot(int frameSize){
			dirty = new AtomicBoolean();
			linkedBuffer = new LinkedUnsafeBuffer(true);
		}
		public void write(long seq, UnsafeBuffer srcBuffer, int offset, int length){
			this.seq = seq;
			linkedBuffer.write(seq, srcBuffer, offset, length);
		}
		public void publish(MarketDataPublisher publisher, MessageCodec codec){
			linkedBuffer.removeAndPublish(seq, publisher, codec);
			dirty.set(false);
		}
		public boolean dirty(){ return dirty.get(); }

		public void clear() {
			linkedBuffer.clear(seq);
			dirty.set(false);
		}
		public boolean claim() { return dirty.compareAndSet(false, true); }
	}
}
