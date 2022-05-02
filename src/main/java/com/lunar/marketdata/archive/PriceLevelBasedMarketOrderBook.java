package com.lunar.marketdata.archive;

import java.util.Iterator;

import com.lunar.marketdata.OrderBookSide;
import com.lunar.marketdata.SpreadTable;
import com.lunar.order.Tick;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;

public class PriceLevelBasedMarketOrderBook implements SingleSideMarketOrderBook {
	private final ObjectArrayList<Tick> ticks;
	private final int bookDepth;
	private final int nullPrice;
	
	PriceLevelBasedMarketOrderBook(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
		ticks = new ObjectArrayList<Tick>();
		this.bookDepth = bookDepth;
		this.nullPrice = nullPrice;
	}
	
	@Override
	public boolean isEmpty() {
		return ticks.isEmpty();
	}

	@Override
	public Tick bestOrNullIfEmpty() {
		return (ticks.isEmpty()) ? null : ticks.get(0);
	}

	@Override
	public int bestTickLevel() {
		return (ticks.isEmpty()) ? nullPrice: ticks.get(0).price();
	}

	@Override
	public void updateByPrice(int price, int priceLevel, long qty, int numOrder) {
	}

	@Override
	public void update(int tickLevel, int price, int priceLevel, long qty, int numOrder) {
		Tick tick = ticks.get(priceLevel - 1);
		tick.price(price).tickLevel(tickLevel).qty(qty).numOrders(numOrder).priceLevel(priceLevel);
	}

	@Override
	public void create(int tickLevel, int price, int priceLevel, long qty, int numOrder) {
		Tick created = Tick.of(tickLevel, price, priceLevel, qty, numOrder);
		ticks.add(priceLevel - 1, created);
		
		// need to handle implicit deletes - anything with priceLevel > 10
		if (ticks.size() > this.bookDepth){
			ObjectListIterator<Tick> iterator = ticks.listIterator(this.bookDepth);
			while (iterator.hasNext()){
				iterator.next();
				iterator.remove();
			}
		}
	}

	@Override
	public void deleteByPrice(int price, int priceLevel) {
	}

	@Override
	public void delete(int tickLevel, int price, int priceLevel) {
		ticks.remove(priceLevel - 1);
	}

	@Override
	public void clear() {
		ticks.clear();
	}
	
	@Override
	public int nullTickLevel(){
		return Integer.MIN_VALUE;
	}	

	public static class OrderBookIterator implements Iterator<Tick>{
		private final ObjectListIterator<Tick> it;
		
		private OrderBookIterator(ObjectListIterator<Tick> it){
			this.it = it;
		}
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public Tick next() {
			return it.next();
		}
		
	}
	@Override
	public Iterator<Tick> iterator() {
		return new OrderBookIterator(ticks.iterator());
	}

	@Override
	public Iterator<Tick> iteratorForWhole() {
		return new OrderBookIterator(ticks.iterator());
	}

	@Override
	public int numPriceLevels() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void create(int price, long qty) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Iterator<Tick> localPriceLevelsIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<Tick> localTickLevelsIterator() {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public void copyFrom(OrderBookSide other) {
        throw new UnsupportedOperationException();        
    }
}
