package com.lunar.marketdata;

import java.util.Iterator;

import com.lunar.order.Tick;

public interface OrderBookSide {
	public int numPriceLevels();
	public boolean isEmpty();
	public void clear();

	public void create(int price, long qty);
	public void create(int tickLevel, int price, int priceLevel, long qty, int numOrder);
	public void update(int tickLevel, int price, int priceLevel, long qty, int numOrder);
	public void delete(int tickLevel, int price, int priceLevel);
	
	public Tick bestOrNullIfEmpty();
	public int bestTickLevel();
	
	public Iterator<Tick> localPriceLevelsIterator();
	public Iterator<Tick> localTickLevelsIterator();
	
	public void copyFrom(final OrderBookSide other);
	
	public int nullTickLevel();
}
