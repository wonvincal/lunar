package com.lunar.core;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Light weight object pool, no time-based eviction, no new garbage, no extra thread
 * @author wongca
 *
 * @param <T>
 */
public class ConcurrentObjectPool<T> implements ObjectPool<T> {
	private static final Logger LOG = LogManager.getLogger(ConcurrentObjectPool.class);

	private final ObjectFactory<T> factory;
	private final ConcurrentLinkedQueue<T> pool;
	private final AtomicInteger numItemsCreated;
	private final AtomicInteger numItemsInPool;
	private final int numItemsCreatedReportThreshold;
	private final String name;
	
	public static <U> ConcurrentObjectPool<U> of(ObjectFactory<U> factory, String name, int numItemsCreatedReportThreshold){
		return new ConcurrentObjectPool<>(factory, name, numItemsCreatedReportThreshold);
	}
	
	ConcurrentObjectPool(ObjectFactory<T> factory, String name, int numItemsCreatedReportThreshold){
		this.factory = factory;
		this.pool = new ConcurrentLinkedQueue<>();
		this.numItemsCreated = new AtomicInteger(0);
		this.numItemsInPool = new AtomicInteger(0);
		this.numItemsCreatedReportThreshold = numItemsCreatedReportThreshold;
		this.name = name;
	}
	
	public T get(){
		T item = this.pool.poll();
		if (item == null){
			item = factory.create(this);
			numItemsCreated.incrementAndGet();
			if (numItemsCreated.get() % numItemsCreatedReportThreshold == 0){
				LOG.debug("Object pool stats [name:{}, numItemsCreated:{}]", name, numItemsCreated.get());
			}
		}
		else{
			this.numItemsInPool.decrementAndGet();
		}
		return item;
	}
	
	public void free(T item){
		this.pool.offer(item);
		this.numItemsInPool.incrementAndGet();
	}
	
	public int numItemsCreated(){
		return numItemsCreated.get();
	}
	
	public int numItemsInPool(){
		return numItemsInPool.get();
	}

	ObjectFactory<T> factory(){
		return factory;
	}
}

