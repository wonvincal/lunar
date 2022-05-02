package com.lunar.util;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentInt2ObjMap<T> {
	// TODO To be optimized with a RingBuffer
	private final ConcurrentHashMap<Integer, T> items;
	
	public ConcurrentInt2ObjMap(){
		this.items = new ConcurrentHashMap<>();
	}
	
	public ConcurrentInt2ObjMap(int initialCapacity){
		this.items = new ConcurrentHashMap<>(initialCapacity);
	}
	
	public ConcurrentInt2ObjMap(int initialCapacity, float loadFactor, int concurrencyLevel){
		this.items = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
	}
	
	public Collection<T> values(){
	    return items.values();
	}
	
	public T get(int key){
		// boxing
		return this.items.get(key);
	}
	
	public T put(int key, T value){
		// boxing
		return this.items.put(key, value);
	}
	
	public boolean containsKey(int key){
		return this.items.containsKey(key);
	}
	
	public boolean containsValue(T value){
		return this.items.containsValue(value);
	}
	
	public T remove(int key){
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
