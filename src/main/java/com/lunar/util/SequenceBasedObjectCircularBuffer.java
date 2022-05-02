package com.lunar.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lunar.exception.OutsideOfBufferRangeException;

/**
 * <ul>
 * <li>size()<br>
 * Not supported.  One way is to walk from at items[Sequence] to items[maxAvailSeq], which is very slow.
 * <li>remove()<br>
 * Not supported.  If we remove items[maxAvailSeq], we need to walk the array to find a new maxAvailSeq.  
 * That's slow.
 * </ul>
 * @author wongca
 *
 * @param <T>
 */
public final class SequenceBasedObjectCircularBuffer<T> {
	public static long NULL = -1;
	public static class Element<T>{
		private long sequence;
		private T payload;
		
		Element(long sequence, T newInstance){
			this.sequence = sequence;
			this.payload = newInstance;
		}
	}
	
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(SequenceBasedObjectCircularBuffer.class);
	private final int capacity;
	private final int indexMask;
	@SuppressWarnings("rawtypes")
	private final Element[] entries;
	/*
	 * Sequence of current first element in the buffer
	 */
	private long atSequence;
	/*
	 * Max claimed sequence in the buffer at the moment
	 */
	private long maxClaimedSequence;
	/**
	 * An offset value added to the sequence of an element for indexing purpose.
	 * Each {@link #clear} call will increase seqOffset by the capacity of the
	 * buffer to make sure that getting the element of the same sequence is not
	 * possible.
	 * 
	 * For example,
	 * seqOffset = 0;
	 * buffer.claim(1).merge(1, 2, 3, 4, 5); // the indexing sequence is 1 + 0 = 1
	 * buffer.clear(); // seqOffset = 16 (let say size of buffer is 16)
	 * buffer.contains(1) will return false // the indexing sequence is 1 + 16 = 17
	 */
	private long seqOffset;
	
	public static <U> SequenceBasedObjectCircularBuffer<U> of(int capacity, EventFactory<U> eventFactory){
		return new SequenceBasedObjectCircularBuffer<>(capacity, eventFactory);
	}
	
	private SequenceBasedObjectCircularBuffer(int capacity, EventFactory<T> eventFactory){
		if (capacity <= 0){
			throw new IllegalArgumentException("Capacity must be greater than zero ");
		}
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.indexMask = this.capacity - 1;
		this.entries = new Element[this.capacity];
		this.seqOffset = 0;
		
        for (int i = 0; i < this.capacity; i++){
            entries[i] = new Element<T>(-1, eventFactory.newInstance());
        }
        this.atSequence = NULL;
        this.maxClaimedSequence = NULL;
	}
	
	/**
	 * Set current first element of the buffer
	 * @param atSequence
	 */
	public void startSequence(long atSequence){
		this.atSequence = atSequence + seqOffset;
	}
	
	/**
	 * 
	 * @return Sequence number of the current first slot of this buffer
	 */
	public long sequence(){
		return atSequence - seqOffset;
	}
	
	/**
	 * Get element with the specific sequence
	 * @param sequence
	 * @return Element if found, null otherwise.
	 */
	@SuppressWarnings("unchecked")
	public T get(long sequence){
		long effectiveSeq = sequence + seqOffset;
		Element<T> entry = (Element<T>)entries[(int)(effectiveSeq & indexMask)];
		return (entry.sequence == effectiveSeq) ? entry.payload : null;
	}

	/**
	 * Claim a particular element in the buffer for usage
	 * @param sequence
	 * @return Element if sequence is within a valid range (atSequence, atSequence + capacity)
	 * @throws OutsideOfBufferRangeException 
	 */
	@SuppressWarnings("unchecked")
	public T claim(long sequence) throws OutsideOfBufferRangeException {
		long effectiveSeq = sequence + seqOffset;
		if (effectiveSeq < atSequence || effectiveSeq >= (atSequence + capacity)){
			throw new OutsideOfBufferRangeException("sequence out of available range " + 
					"[sequence: " + sequence + ", minSequence:" + (atSequence - seqOffset) + ", maxSequence:"+ (atSequence - seqOffset + capacity - 1) +
					", effectiveMinSequence: " + atSequence + ", effectiveMaxSequence: " + (atSequence + capacity - 1) + "]");
		}
		
		Element<T> entry = (Element<T>)entries[(int)(effectiveSeq & indexMask)];
		entry.sequence = effectiveSeq;
		maxClaimedSequence = (effectiveSeq > maxClaimedSequence) ? effectiveSeq : maxClaimedSequence;
		return entry.payload;
	}
	
	/**
	 * Flush the current first element in the buffer up but not including the
	 * first empty element 
	 * @param handler
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public long flushTillNull(EventHandler<T> handler) throws Exception{
		Element<T> entry = (Element<T>)entries[(int)(atSequence & indexMask)];
		long nextSequence = atSequence + 1;
		Element<T> nextEntry = (Element<T>)entries[(int)(nextSequence & indexMask)];
		while (entry.sequence == atSequence){
			handler.onEvent(entry.payload, entry.sequence - this.seqOffset, (nextEntry.sequence == nextSequence) ? false : true);
			atSequence++;
			nextSequence = atSequence + 1;
			entry = nextEntry;
			nextEntry = (Element<T>)entries[(int)(nextSequence & indexMask)];
		}
		maxClaimedSequence = (atSequence > maxClaimedSequence) ? NULL : maxClaimedSequence;
		return atSequence - seqOffset;
	}

	@SuppressWarnings("unchecked")
	public boolean contains(long sequence){
		long effectiveSeq = sequence + seqOffset;
		return ((Element<T>)entries[(int)(effectiveSeq & indexMask)]).sequence == effectiveSeq;
	}
	
	/**
	 * Clear from current sequence up to and include the input sequence
	 * @param sequence
	 */
	public void clearAndStartSequenceAt(long sequenceOfFirstElement){
		this.seqOffset += this.capacity;
		this.atSequence = sequenceOfFirstElement + this.seqOffset;
		this.maxClaimedSequence = NULL;
		// In case seqOffset overflows
		if (this.seqOffset < 0){
			this.seqOffset = 0;
		}
	}

	/**
	 * Clear to up and include input sequence.
	 * Throw exception if input sequence exceeds the max of what the buffer can support that the current moment  
	 * @param sequence
	 * @throws OutsideOfBufferRangeException 
	 */
	public void clearUpTo(long sequence) throws OutsideOfBufferRangeException{
		// Check if sequence is within range
		long effectiveNextSeq = sequence + 1 + seqOffset;
		if (effectiveNextSeq < atSequence || effectiveNextSeq > (atSequence + capacity)){
			throw new OutsideOfBufferRangeException("Sequence is not in current range " + 
					"[sequence: " + sequence + ", minSequence: " + (atSequence - seqOffset) + ", maxSequence: " + (atSequence + capacity - seqOffset - 1) + 
					", effectiveMinSequence:" + atSequence + ", effectiveMaxSequence:"+ (atSequence + capacity - 1) + "]");
		}
		this.atSequence = effectiveNextSeq;
		this.maxClaimedSequence = (effectiveNextSeq > maxClaimedSequence) ? NULL : maxClaimedSequence;
	}
	
	public boolean isEmpty(){
		return maxClaimedSequence == NULL;
	}
	
	int capacity(){
		return capacity;
	}
}
