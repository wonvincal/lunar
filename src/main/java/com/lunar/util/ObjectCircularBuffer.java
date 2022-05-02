package com.lunar.util;

import java.nio.BufferOverflowException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lunar.service.ServiceConstant;

public class ObjectCircularBuffer <T> {
	public static long NULL = -1;
	public static class Element<T>{
		private boolean dirty;
		private T payload;
		
		Element(boolean dirty, T newInstance){
			this.dirty = dirty;
			this.payload = newInstance;
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(ObjectCircularBuffer.class);
	private final int capacity;
	private final int indexMask;
	@SuppressWarnings("rawtypes")
	private final Element[] entries;
	/**
	 * Next read cursor
	 */
	private long readCursor;
	/**
	 * Next write cursor
	 */
	private long writeCursor;

	public static <U> ObjectCircularBuffer<U> of(int capacity, EventFactory<U> eventFactory){
		return new ObjectCircularBuffer<>(capacity, eventFactory);
	}
	
	private ObjectCircularBuffer(int capacity, EventFactory<T> eventFactory){
		if (capacity <= 0){
			throw new IllegalArgumentException("Capacity must be greater than zero ");
		}
		this.capacity = BitUtil.nextPowerOfTwo(capacity);
		this.indexMask = this.capacity - 1;
		this.entries = new Element[this.capacity];
		this.readCursor = 0;
		this.writeCursor = 0;
		
        for (int i = 0; i < this.capacity; i++){
            entries[i] = new Element<T>(false, eventFactory.newInstance());
        }
	}
	
	@SuppressWarnings("unchecked")
	public T claim() throws BufferOverflowException {
		// When there is not space, throw BufferOverflow
		Element<T> entry = (Element<T>)entries[(int)(writeCursor & indexMask)];
		if (entry.dirty){
			throw new BufferOverflowException();
		}
		entry.dirty = true;
		writeCursor++;
		return entry.payload;
	}
	
	@SuppressWarnings("unchecked")
	public int flushTillEmpty(EventHandler<T> handler) throws Exception{
		int sequence = ServiceConstant.START_RESPONSE_SEQUENCE;
		Element<T> entry = (Element<T>)entries[(int)(readCursor & indexMask)];
		Element<T> nextEntry = (Element<T>)entries[(int)((readCursor + 1) & indexMask)];
		while (entry.dirty){
			handler.onEvent(entry.payload, sequence, (nextEntry.dirty) ? false : true);
			readCursor++;
			entry.dirty = false;
			entry = nextEntry;
			nextEntry = (Element<T>)entries[(int)((readCursor + 1)& indexMask)];
			sequence++;
		}
		return sequence - 1; 
	}
	
	@SuppressWarnings("unchecked")
	public int flush(int count, EventHandler<T> handler) throws Exception{
		int sequence = ServiceConstant.START_RESPONSE_SEQUENCE;
		Element<T> entry = (Element<T>)entries[(int)(readCursor & indexMask)];
		Element<T> nextEntry = (Element<T>)entries[(int)((readCursor + 1) & indexMask)];
		while (entry.dirty && sequence <= count){
			handler.onEvent(entry.payload, sequence, (nextEntry.dirty) ? false : true);
			readCursor++;
			entry.dirty = false;
			entry = nextEntry;
			nextEntry = (Element<T>)entries[(int)((readCursor + 1)& indexMask)];
			sequence++;
		}
		return sequence - 1; 
	}

	public static interface CancellableEventHandler<T> {
		boolean onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
	}

	@SuppressWarnings("unchecked")
	public int flushTillEmptyOrCancelled(CancellableEventHandler<T> handler, boolean advanceCursorOnCancel) throws Exception {
		int sequence = ServiceConstant.START_RESPONSE_SEQUENCE;
		Element<T> entry = (Element<T>)entries[(int)(readCursor & indexMask)];
		Element<T> nextEntry = (Element<T>)entries[(int)((readCursor + 1) & indexMask)];
		boolean keepGoing = true;
		while (entry.dirty && keepGoing){
			keepGoing = handler.onEvent(entry.payload, sequence, (nextEntry.dirty) ? false : true);
			if (!keepGoing && !advanceCursorOnCancel){
				break;
			}
			readCursor++;
			entry.dirty = false;
			entry = nextEntry;
			nextEntry = (Element<T>)entries[(int)((readCursor + 1)& indexMask)];
			sequence++;
		}
		return sequence - 1; 
	}

	@SuppressWarnings("unchecked")
	public int peekTillEmpty(EventHandler<T> handler) throws Exception{
		int sequence = ServiceConstant.START_RESPONSE_SEQUENCE;
		long cursor = readCursor;
		Element<T> entry = (Element<T>)entries[(int)(cursor & indexMask)];
		Element<T> nextEntry = (Element<T>)entries[(int)((cursor + 1) & indexMask)];
		
		// This will break when writeCursor overflow
		while (cursor < writeCursor){
			handler.onEvent(entry.payload, sequence, (nextEntry.dirty) ? false : true);
			cursor++;
			entry = nextEntry;
			nextEntry = (Element<T>)entries[(int)((cursor + 1)& indexMask)];
			sequence++;
		}
		return sequence - 1;
	}
	
	@SuppressWarnings("unchecked")
	public T peek(){
		long cursor = readCursor;
		Element<T> entry = (Element<T>)entries[(int)(cursor & indexMask)];
		if (entry.dirty){
			return entry.payload;			
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public T next(){
		long cursor = readCursor;
		Element<T> entry = (Element<T>)entries[(int)(cursor & indexMask)];
		if (entry.dirty){
			readCursor++;
			entry.dirty = false;
			return entry.payload;			
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public T flushAndReturnLast(){
		Element<T> entry = (Element<T>)entries[(int)(readCursor & indexMask)];
		T payload = null;
		while (entry.dirty){
			readCursor++;
			entry.dirty = false;
			payload = entry.payload;
			entry = (Element<T>)entries[(int)(readCursor & indexMask)];
		}
		return payload;
	}
	
	public boolean isEmpty(){
		return readCursor == writeCursor;
	}
	
	int capacity(){
		return capacity;
	}
	
	@SuppressWarnings("unchecked")
	public void clear(){
		Element<T> entry = (Element<T>)entries[(int)(readCursor & indexMask)];
		while (entry.dirty){
			readCursor++;
			entry.dirty = false;
			entry = (Element<T>)entries[(int)((readCursor)& indexMask)];
		}
	}

	/**
	 * O(N) - becareful when using this
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public int size() {
		long cursor = readCursor;
		Element<T> entry = (Element<T>)entries[(int)(cursor & indexMask)];
		int num = 0;
		while (entry.dirty){
			cursor++;
			num++;
			entry = (Element<T>)entries[(int)((cursor)& indexMask)];
		}
		return num;
	}
}
