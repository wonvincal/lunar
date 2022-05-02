package com.lunar.util;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentLong2ObjMap<T> {
	// TODO To be optimized with a RingBuffer
	private final ConcurrentHashMap<Long, T> items;
	
	public ConcurrentLong2ObjMap(){
		this.items = new ConcurrentHashMap<>();
	}
	
	public ConcurrentLong2ObjMap(int initialCapacity){
		this.items = new ConcurrentHashMap<>(initialCapacity);
	}
	
	public ConcurrentLong2ObjMap(int initialCapacity, float loadFactor, int concurrencyLevel){
		this.items = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
	}
	
	public Collection<T> values(){
		return items.values();
	}
	
	public T get(long key){
		// boxing
		return this.items.get(key);
	}
	
	public T put(long key, T value){
		// boxing
		return this.items.put(key, value);
	}
	
	public boolean containsKey(long key){
		return this.items.containsKey(key);
	}
	
	public boolean containsValue(T value){
		return this.items.containsValue(value);
	}
	
	public T remove(long key){
		return this.items.remove(key);
	}
	
	public void clear(){
		this.items.clear();
	}
	
	public boolean isEmpty(){
		return this.items.isEmpty();
	}
	
	public long mappingCount(){
		return this.items.mappingCount();
	}
	
}
