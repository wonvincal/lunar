package com.lunar.marketdata.archive;

import java.util.concurrent.atomic.AtomicBoolean;

import com.lunar.message.binary.MessageCodec;
import com.lunar.util.BitUtil;

import org.agrona.concurrent.UnsafeBuffer;

public class ByteBasedSpool {
	private final int mask;
	private final int capacity;
	private final MultiProducerNoBoundaryCheckByteBasedRingBuffer buffer;
	private final Slot[] slots;
	
	/**
	 * This is set by the reader, anything before this should be cleared
	 */
	private volatile long minAllowableSeq;
	private final RawMessageWalker walker;
	
	ByteBasedSpool(int capacity, int byteBufferCapacity, RawMessageWalker walker){
		this.minAllowableSeq = Long.MAX_VALUE;
		this.buffer = MultiProducerNoBoundaryCheckByteBasedRingBuffer.of(byteBufferCapacity);
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.slots = new Slot[this.capacity];
		for (int i = 0; i < this.capacity; i++){
			this.slots[i] = new Slot();
		}
		this.mask = this.capacity - 1;
		this.walker = walker;
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
				slot.set(buffer.write(srcBuffer, offset, length), length);
				
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
			buffer.readBySeq(slot.key(), slot.length(), publisher, codec);
			slot.clear();
			seq++;
			slot = this.slots[(int)seq & mask];
		}
		return seq;
	}
	
	private static class Slot {
		private long key;
		private int length;
		private final AtomicBoolean dirty;
		Slot(){
			dirty = new AtomicBoolean();
		}
		public Slot set(long key, int length){
			this.key = key;
			this.length = length;
			return this;
		}
		public boolean dirty(){ return dirty.get(); }
		public long key() { return key;}
		public int length() { return length;}
		public void clear() { dirty.set(false); }
		public boolean claim() { return dirty.compareAndSet(false, true); }
	}
}
