package com.lunar.core;

/**
 * Light weight object pool, no time-based eviction, no new garbage, no extra thread
 * @author wongca
 *
 * @param <T>
 */
public interface ObjectPool<T> {
	public T get();
	
	public void free(T item);
	
	public int numItemsCreated();
	
	public int numItemsInPool();

}

