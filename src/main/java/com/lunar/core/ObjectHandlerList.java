package com.lunar.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class ObjectHandlerList<T> {
	private final ObjectArrayList<T> handlers;

    public static ObjectHandlerList of(int initialCapacity){
        return new ObjectHandlerList(initialCapacity);
    }
	
	public static ObjectHandlerList of(){
		return new ObjectHandlerList();
	}
	
	ObjectHandlerList(){
	    this(0);
	}
	
    ObjectHandlerList(int initialCapacity){
    	T[] elements = (T[])new Object[initialCapacity];
        this.handlers = ObjectArrayList.wrap(elements);
        this.handlers.size(0);
    }

	public boolean add(T sink){
		if (!handlers.contains(sink)){
			return handlers.add(sink);
		}
		return false;
	}

	public boolean remove(T sink){
		return handlers.rem(sink);
	}
	
	public int size(){
		return handlers.size();
	}

	public T[] elements(){
		return this.handlers.elements();
	}

	public boolean contains(T sink){
		return handlers.contains(sink);
	}
	
	public void clear(){
		handlers.clear();
	}

}
