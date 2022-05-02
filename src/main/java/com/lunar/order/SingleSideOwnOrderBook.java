package com.lunar.order;

public interface SingleSideOwnOrderBook {
	public abstract boolean isEmpty();
	
	public abstract void create(Order order);
	public abstract void update(Order order);
}
