package com.lunar.marketdata;

import java.util.Iterator;

import com.lunar.order.Tick;

abstract public class OrderBookSideSnapshot implements OrderBookSide {
	private final int bookDepth;
	private final SpreadTable spreadTable;	
	private final Tick[] ticksPool;
	private final Tick[] ticksByTickLevel;
	private final Tick[] ticksByPriceLevel;
	private final Tick nullTick;
	private final int nullTickLevel;
	private final TicksIterator ticksByTickLevelIterator;
	private final TicksIterator ticksByPriceLevelIterator;
	private int bestTickLevel;
	private int numPriceLevels;
	private final StringBuilder stringBuilder;
	
	abstract class TicksIterator implements Iterator<Tick> {
		protected int index = 0;
		private Tick[] ticks;
		
		TicksIterator(final Tick[] ticks) {
			this.ticks = ticks;
		}
		
		public void reset() {
			index = 0;
		}
		
		@Override
		public Tick next() {
			return this.ticks[index++];
		}
	}
	
	class TicksByTickLevelIterator extends TicksIterator {
		TicksByTickLevelIterator() {
			super(ticksByTickLevel);
		}

		@Override
		public boolean hasNext() {
			return index < bookDepth; 
		}
	}
	
	class TicksByPriceLevelIterator extends TicksIterator {
		TicksByPriceLevelIterator() {
			super(ticksByPriceLevel);
		}

		@Override
		public boolean hasNext() {
			return index < numPriceLevels;
		}
	}
	
	OrderBookSideSnapshot(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice) {
		this.bookDepth = bookDepth;
		this.spreadTable = spreadTable;		
		this.ticksPool = new Tick[bookDepth];
		this.ticksByTickLevel = new Tick[bookDepth];
		this.ticksByPriceLevel = new Tick[bookDepth];
		this.ticksByTickLevelIterator = new TicksByTickLevelIterator();
		this.ticksByPriceLevelIterator = new TicksByPriceLevelIterator();
		this.nullTick = Tick.of(nullTickLevel, nullPrice);
		this.nullTickLevel = nullTickLevel;
		this.bestTickLevel = nullTickLevel;
		this.numPriceLevels = 0;
		this.stringBuilder = new StringBuilder();
		
		for (int i = 0; i < bookDepth; i++) {
			ticksByTickLevel[i] = nullTick;
			ticksByPriceLevel[i] = nullTick;
			ticksPool[i] = Tick.of(nullTickLevel, nullPrice);			
		}		
	}

	public boolean isEmpty() {
		return numPriceLevels == 0;
	}
	
	@Override
	public int nullTickLevel(){
		return nullTickLevel;
	}
	
	public int numPriceLevels() {
		return numPriceLevels;
	}

	public Tick bestOrNullIfEmpty() {
		return numPriceLevels > 0 ? ticksByTickLevel[0] : null;
	}

	/** 
	 * Assumption: only one call to the create method per tick level
	 * The calls to the create method is in descending order by tick level
	 * @param tickLevel ignored
	 * @param price price
	 * @param priceLevel ignored
	 * @param qty quantity
	 * @param numOrder ignored
	 */
	public void create(int price, long qty) {
	    if (price == 0)
	        return;
		final int tickLevel = spreadTable.priceToTick(price);
		if (this.bestTickLevel == this.nullTickLevel) {
			this.bestTickLevel = tickLevel;
		}
		int index = getIndexFromTickLevel(this.bestTickLevel, tickLevel);		
		if (index < this.bookDepth) {
			ticksByTickLevel[index] = ticksPool[index];
			ticksByTickLevel[index].price(price).qty(qty).tickLevel(tickLevel).priceLevel(this.numPriceLevels).numOrders(1);
			ticksByPriceLevel[this.numPriceLevels++] = ticksByTickLevel[index];
		}
	}
	
	public void create(int tickLevel, int price, int priceLevel, long qty, int numOrder) {
		throw new UnsupportedOperationException();
	}
	
	public void update(int tickLevel, int price, int priceLevel, long qty, int numOrder) {
		throw new UnsupportedOperationException();
	}
	
	public void delete(int tickLevel, int price, int priceLevel) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int bestTickLevel() {
		return this.bestTickLevel;
	}

	public void clear() {
		this.numPriceLevels = 0;
		this.bestTickLevel = this.nullTickLevel;
		for (int i = 0; i < this.bookDepth; i++){
			ticksByTickLevel[i] = this.nullTick;
		}
	}

	/*
	 * Does not support multi-threading
	 */
	public Iterator<Tick> localPriceLevelsIterator() {
		ticksByPriceLevelIterator.reset();
		return ticksByPriceLevelIterator;
	}
	
	/*
	 * Does not support multi-threading
	 */
	public Iterator<Tick> localTickLevelsIterator() {
		ticksByTickLevelIterator.reset();
		return ticksByTickLevelIterator;
	}
	
	abstract protected int getIndexFromTickLevel(final int bestTickLevel, final int tickLevel);

	@Override
	public String toString() {
	    stringBuilder.setLength(0);
        final Iterator<Tick> iterator = localPriceLevelsIterator();
        boolean isFirst = true;
        while (iterator.hasNext()) {
            final Tick tick = iterator.next();
            if (isFirst) {
                isFirst = false;
            }
            else {
                stringBuilder.append(",");
            }
            stringBuilder.append(tick.toString());            
        }
        return stringBuilder.toString();
	}	

	public void copyFrom(final OrderBookSide other) {
	    if (other instanceof OrderBookSideSnapshot) {
            final OrderBookSideSnapshot otherSide = (OrderBookSideSnapshot)other;

	        this.numPriceLevels = 0;
	        this.bestTickLevel = this.nullTickLevel;
	        // can only copy if book depth are the same or this book has lesser book depth
	        for (int i = 0; i < this.bookDepth; i++) {
	            final Tick tick = otherSide.ticksByTickLevel[i];	            
	            if (tick.tickLevel() == this.nullTickLevel) {
	                ticksByTickLevel[i] = this.nullTick;
	            }
	            else {
    	            if (this.bestTickLevel == this.nullTickLevel) {
    	                this.bestTickLevel = tick.tickLevel();
    	            }
                    ticksByTickLevel[i] = ticksPool[i];
                    ticksByTickLevel[i].price(tick.price()).qty(tick.qty()).tickLevel(tick.tickLevel()).priceLevel(this.numPriceLevels).numOrders(1);
                    ticksByPriceLevel[this.numPriceLevels++] = ticksByTickLevel[i];
	            }
	        }
	    }
	    else {
	        throw new UnsupportedOperationException();
	    }
	}
}
