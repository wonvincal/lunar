package com.lunar.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Light weight object pool, no time-based eviction, no new garbage, no extra thread
 * @author wongca
 *
 * @param <T>
 */
public class RingBufferObjectPool<T extends Object> implements ObjectPool<T> {
	private static final Logger LOG = LogManager.getLogger(RingBufferObjectPool.class);

	private final ObjectFactory<T> factory;
	private final Object[] objectRingBuffer;
	private final int maxPoolSize;
	private final int mask;
	private int startIndex;
    private int numItemsCreated;
    private int numItemsFree;
	private final String name;
	
	public static <U> RingBufferObjectPool<U> of(final ObjectFactory<U> factory, final String name, final int initialPoolSize, final int maxPoolSize) {
		return new RingBufferObjectPool<>(factory, name, initialPoolSize, maxPoolSize);
	}
	
	RingBufferObjectPool(final ObjectFactory<T> factory, final String name, final int initialPoolSize, final int maxPoolSize) {
		this.factory = factory;
		this.objectRingBuffer = new Object[maxPoolSize];
        this.maxPoolSize = maxPoolSize;
        this.mask = this.maxPoolSize - 1;
        this.startIndex = 0;
		this.numItemsCreated = initialPoolSize;
		this.numItemsFree = 0;
		this.name = name;
		for (int i = 0; i < initialPoolSize; i++) {
		    free(factory.create(this));
		}
	}
	
	@SuppressWarnings("unchecked")
    public T get() {
	    final T item;
	    if (numItemsFree > 0) {
	        item = (T)objectRingBuffer[startIndex];
	        startIndex = (startIndex + 1) & mask;
	        numItemsFree--;
	    }
	    else if (numItemsCreated < maxPoolSize) {
	        item = factory.create(this);
	        numItemsCreated++;
	    }
	    else {
	        item = null;
            LOG.error("Object pool full [name:{}, maxPoolSize:{}]", name, maxPoolSize);
	    }
	    return item;
	}
	
	public void free(final T item) {
	    final int index = (startIndex + numItemsFree) & mask;
	    objectRingBuffer[index] = item;
	    numItemsFree++;
	}
	
	public int numItemsCreated(){
		return numItemsCreated;
	}
	
	public int numItemsInPool(){
		return numItemsCreated - numItemsFree;
	}

}

