package com.lunar.marketdata.archive;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.OrderBookSide;
import com.lunar.marketdata.SpreadTable;
import com.lunar.order.Tick;
import com.lunar.util.BitUtil;

/**
 * HKEx order book security premium with maximum 10 tick levels.
 * We cannot really use this with derivative premium (DP) because DP may have an
 * extra aggregated level at the bottom of the book. 
 * 
 * @author Calvin
 *
 */
public final class AskMarketOrderBook implements SingleSideMarketOrderBook {
	private static final Logger LOG = LogManager.getLogger(AskMarketOrderBook.class);
	private final int bookDepth;
	private final int capacity;
	private int topOfBookTickLevel;
	private int bottomOfBookTickLevel;
	private final Tick[] ticks;
	private final SpreadTable spreadTable;
	private final int tickLevelMask;
	private final int nullPrice;
	private final int nullTickLevel;
	
	int size(){
		return ticks.length;
	}
	
	int topOfBookTickLevel(){
		return topOfBookTickLevel;
	}
	
	int bottomOfBookTickLevel(){
		return bottomOfBookTickLevel;
	}
	
	AskMarketOrderBook(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
		this.spreadTable = spreadTable;
		this.capacity = BitUtil.nextPowerOfTwo(bookDepth);
		this.tickLevelMask = this.capacity - 1;
		this.bookDepth = bookDepth;
		this.nullPrice = nullPrice;
		this.nullTickLevel = nullTickLevel;
		this.ticks = new Tick[this.capacity];
		
		// create tick for level 1 to level capacity (which is likely to be 16 for OMD-C, 
		// given the next power of 2 for 10 is 16)
		for (int i = 0; i < this.capacity; i++){
			this.ticks[i] = Tick.of(nullTickLevel, nullPrice);
			LOG.trace("created null: index:{}, tick:{}", i, this.ticks[i]);
		}
		
		setBookBoundary(spreadTable.maxLevel());
	}
	
	private void setBookBoundary(int top){
		topOfBookTickLevel = top;
		bottomOfBookTickLevel = Math.min(topOfBookTickLevel + bookDepth - 1, spreadTable.maxLevel());
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#hasBest()
	 */
	@Override
	public boolean isEmpty(){
		return (ticks[topOfBookTickLevel & tickLevelMask].qty() == 0);
	}

	@Override
	public int nullTickLevel(){
		return nullTickLevel;
	}
	
	/**
	 * This method returns an Tick object at the top of the book, it is not necessary the
	 * best
	 * 
	 * Please change this according to your needs.
	 * @return
	 */
	@Override
	public Tick bestOrNullIfEmpty(){
		Tick tick = ticks[topOfBookTickLevel & tickLevelMask];
		return (tick.qty() != 0) ? tick : null;
	}

	@Override
	public int bestTickLevel() {
		Tick tick = ticks[topOfBookTickLevel & tickLevelMask];
		return tick.price() != nullPrice ? tick.price() : nullPrice; 
	}

	@Override
	public void updateByPrice(int price, int priceLevel, long qty, int numOrder){
		update(spreadTable.priceToTick(price), price, priceLevel, qty, numOrder);
	}

	@Override
	public void create(int tickLevel, int price, int priceLevel, long qty, int numOrder){
		if (Math.abs(tickLevel - topOfBookTickLevel) <= this.bookDepth){
			int newBottomOfBookTickLevel = (tickLevel + this.bookDepth) < spreadTable.maxLevel() ?
										   (tickLevel + this.bookDepth) :
										   spreadTable.maxLevel();
			// to clear the order book from new level to one level above topOfBook, or
			// to clear the order book for the whole book, all elements  
			for (int i = newBottomOfBookTickLevel; i <= bottomOfBookTickLevel; i++){
				ticks[i & tickLevelMask].numOrders(0).qty(0).tickLevel(this.nullTickLevel).price(this.nullPrice); // remove setting of price later
			}
		}
		else {
			// when update happens > exchange allowance
			for (int i = 0; i < this.capacity; i++){
				ticks[i].numOrders(0).qty(0).tickLevel(nullTickLevel).price(nullPrice);
			}			
		}
		ticks[tickLevel & tickLevelMask].tickLevel(tickLevel).price(price).qty(qty).numOrders(numOrder);
		setBookBoundary(tickLevel < topOfBookTickLevel ? tickLevel : topOfBookTickLevel);
	}
	
	@Override
	public void update(int tickLevel, int price, int priceLevel, long qty, int numOrder){
		ticks[tickLevel & tickLevelMask].tickLevel(tickLevel).price(price).qty(qty).numOrders(numOrder);
	}
	
	/**
	 * This is a OMD-C specific implementation! 
	 * we receive delete for the following scenarios:
	 *	 1) within the order book of 10 tick levels
	 *	 2) outside of order book of 10 tick levels, but within 10 price levels
	 *
	 * Anything outside of 10 ticks have been cleared automatically inside the update method.
	 * 
	 * @param price
	 * @param priceLevel
	 * @param numOrder
	 * @param qty
	 */
	@Override
	public void deleteByPrice(int price, int priceLevel){
		delete(spreadTable.priceToTick(price), price, priceLevel);
	}
	
	@Override
	public void delete(int tickLevel, int price, int priceLevel){
		// No normal if (branch prediction) is better in this case because majority of
		// the time we would get delete due to cancel instead of delte
		// a conditional move in assembly
		if ((tickLevel >= topOfBookTickLevel) && (tickLevel - topOfBookTickLevel < this.bookDepth)){
			delete(tickLevel);
		}
	}

	private void delete(int tickLevel){
		ticks[tickLevel & tickLevelMask].qty(0).numOrders(0).price(nullPrice).tickLevel(nullTickLevel);
		
		// need to move best and top of book if necessary
		// delete can happen to top of the book or not.  it is really hard to predict for the compiler
		// better remove branching from here
		//int deletedTop = (tickLevel == topOfBookTickLevel) ? 0 : -1; // compiler will turn this into something fast
		//int newTopLevel = (deletedTop & topOfBookTickLevel) +
		//				 (~deletedTop & (ticks[(tickLevel + 1) & tickLevelMask].qty() != 0 ? 1 : 0) & (tickLevel + 1));

		if (tickLevel == topOfBookTickLevel) {
			int newTopLevel = ticks[(tickLevel + 1) & tickLevelMask].qty() == 0 ? 0 : tickLevel + 1;
			// newTopLevel covers the topLevel value of two scenarios:
			// 1) topLevel is not changed
			// 2) (topLevel - 1) becomes the new top level
			if (newTopLevel != 0){
				setBookBoundary(newTopLevel);
				return;
			}
			// traverse remaining of the order book to determine the new top level
			for (int i = tickLevel + 2; i <= bottomOfBookTickLevel; i++){
				if (ticks[i & tickLevelMask].qty() != 0){
					setBookBoundary(i);
					return;
				}
			}
			setBookBoundary(spreadTable.maxLevel());
		}
	}
	
	@Override
	public void clear(){
		for (int i = 0; i < this.capacity; i++){
			ticks[i].numOrders(0).qty(0).tickLevel(nullTickLevel).price(nullPrice);
		}
		setBookBoundary(spreadTable.maxLevel());
	}

	public static interface DeleteProxy {
		void delete(int tickLevel);
	}
	
	@Override
	public Iterator<Tick> iterator(){
		return new MarketDataOrderBookIterator(topOfBookTickLevel, bottomOfBookTickLevel);
	}

	@Override
	public Iterator<Tick> iteratorForWhole(){
		return new MarketDataWholeOrderBookIterator();
	}

	private final class MarketDataOrderBookIterator implements Iterator<Tick> {
		private int cursor;
		private final int end;
		
		private MarketDataOrderBookIterator(int start, int end){
			this.cursor = start;
			this.end = end;
		}
		public boolean hasNext(){
			return this.cursor < end;
		}
		public Tick next(){
			if (this.hasNext()){
				return ticks[cursor++ & tickLevelMask];
			}
			throw new NoSuchElementException();
		}
		
		public void remove(){
			throw new UnsupportedOperationException();
		}
	}

	private final class MarketDataWholeOrderBookIterator implements Iterator<Tick> {
		private int cursorTickLevel;
		private final int imaginaryEndTickLevel;
		
		private MarketDataWholeOrderBookIterator(){
			int min = spreadTable.maxLevel();
			for (int i = 0; i < ticks.length; i++){
				if (ticks[i].tickLevel() != nullTickLevel){
					min = Math.min(min, ticks[i].tickLevel());
				}
			}
			this.cursorTickLevel = min;
			LOG.trace("min tick level: {}", min);
			this.imaginaryEndTickLevel = min + ticks.length - 1;
		}
		public boolean hasNext(){
			return this.cursorTickLevel <= imaginaryEndTickLevel;
		}
		public Tick next(){
			if (this.hasNext()){
				return ticks[cursorTickLevel++ & tickLevelMask];
			}
			throw new NoSuchElementException();
		}
		
		public void remove(){
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public int numPriceLevels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void create(int price, long qty) {
		this.create(spreadTable.priceToTick(price), price, 0, qty, 1);
	}

	@Override
	public Iterator<Tick> localPriceLevelsIterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Tick> localTickLevelsIterator() {
		throw new UnsupportedOperationException();
	}

    @Override
    public void copyFrom(OrderBookSide other) {
        throw new UnsupportedOperationException();        
    }
}
