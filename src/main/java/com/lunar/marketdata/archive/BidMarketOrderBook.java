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
public final class BidMarketOrderBook implements SingleSideMarketOrderBook {
	private static final Logger LOG = LogManager.getLogger(BidMarketOrderBook.class);
	private final int nullPrice;
	private final int nullTickLevel;
	private final SpreadTable spreadTable;
	private final int bookDepth;
	private final int capacity;
	private final int tickLevelMask;
	private int topOfBookTickLevel;
	private int bottomOfBookTickLevel;
	private final Tick[] ticks;
	
	int size(){
		return ticks.length;
	}
	
	int topOfBookTickLevel(){
		return topOfBookTickLevel;
	}
	
	int bottomOfBookTickLevel(){
		return bottomOfBookTickLevel;
	}
	
	BidMarketOrderBook(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
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
		
		setBookBoundary(SpreadTable.SPREAD_TABLE_MIN_LEVEL);
	}
	
	private void setBookBoundary(int top){
		topOfBookTickLevel = top;
		bottomOfBookTickLevel = Math.max(this.topOfBookTickLevel - this.bookDepth + 1, 1);
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#hasBest()
	 */
	@Override
	public boolean isEmpty(){
		return (ticks[topOfBookTickLevel & tickLevelMask].qty() == 0);
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#best()
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

	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#updateByPrice(int, int, int, long)
	 */
	@Override
	public void updateByPrice(int price, int priceLevel, long qty, int numOrder){
		update(spreadTable.priceToTick(price), price, priceLevel, qty, numOrder);
	}
	
	@Override
	public void create(int tickLevel, int price, int priceLevel, long qty, int numOrder){
		if (Math.abs(tickLevel - topOfBookTickLevel) <= this.bookDepth){
			// calculate one level below the bottom of book
			// e.g. orig bottom: 190
			//      tick level: 204
			//      new bottom: 195
			//      we will then need to clear 194 (tickLevel - depth), 193, 192, 191, 190
			int newBottomOfBookTickLevel = (tickLevel - this.bookDepth > SpreadTable.SPREAD_TABLE_MIN_LEVEL) ? 
											tickLevel - this.bookDepth : 
											SpreadTable.SPREAD_TABLE_MIN_LEVEL;  
			
			// to clear the order book from new level to one level above topOfBook, or
			// to clear the order book for the whole book, all elements
			for (int i = newBottomOfBookTickLevel; i >= bottomOfBookTickLevel; i--){
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
		setBookBoundary((tickLevel > topOfBookTickLevel) ? tickLevel : topOfBookTickLevel);
	}
	
		/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#update(int, int, int, int, long)
	 */
	@Override
	public void update(int tickLevel, int price, int priceLevel, long qty, int numOrder){
		ticks[tickLevel & tickLevelMask].tickLevel(tickLevel).price(price).qty(qty).numOrders(numOrder);
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#deleteByPrice(int, int, int, long)
	 */
	@Override
	public void deleteByPrice(int price, int priceLevel){
		delete(spreadTable.priceToTick(price), price, priceLevel);
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#delete(int, int, int, long)
	 */
	@Override
	public void delete(int tickLevel, int price, int priceLevel){
		// No normal if (branch prediction) is better in this case because majority of
		// the time we would get delete due to cancel instead of delte
		// a conditional move in assembly
		if ((tickLevel <= topOfBookTickLevel) && (topOfBookTickLevel - tickLevel < this.bookDepth)){
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
		//				  (~deletedTop & (ticks[(tickLevel - 1) & tickLevelMask].qty() != 0 ? -1 : 0) & (tickLevel - 1));
		if (tickLevel == topOfBookTickLevel) {
			int newTopLevel = ticks[(tickLevel - 1) & tickLevelMask].qty() == 0 ? 0 : tickLevel - 1;
			
			// newTopLevel covers the topLevel value of two scenarios:
			// 1) topLevel is not changed
			// 2) (topLevel - 1) becomes the new top level
			if (newTopLevel != 0){
				setBookBoundary(newTopLevel);
				return;
			}
			// traverse remaining of the order book to determine the new top level
			for (int i = tickLevel - 2; i >= bottomOfBookTickLevel; i--){
				if (ticks[i & tickLevelMask].qty() != 0){
					setBookBoundary(i);
					return;
				}
			}					
			setBookBoundary(SpreadTable.SPREAD_TABLE_MIN_LEVEL);			
		}
	
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#clear()
	 */
	@Override
	public void clear(){
		for (int i = 0; i < this.capacity; i++){
			ticks[i].numOrders(0).qty(0).tickLevel(nullTickLevel).price(nullPrice);
		}
		setBookBoundary(SpreadTable.SPREAD_TABLE_MIN_LEVEL);
	}

	@Override
	public int nullTickLevel(){
		return nullTickLevel;
	}
	
	public static interface DeleteProxy {
		void delete(int tickLevel);
	}
	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#iterator()
	 */
	@Override
	public Iterator<Tick> iterator(){
		return new MarketDataOrderBookIterator(topOfBookTickLevel, bottomOfBookTickLevel);
	}

	/* (non-Javadoc)
	 * @see com.lunar.marketdata.SingleSideMarketOrderBook#iteratorForWhole()
	 */
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
			return this.cursor > end;
		}
		public Tick next(){
			if (this.hasNext()){
				return ticks[cursor-- & tickLevelMask];
			}
			throw new NoSuchElementException();
		}
		
		public void remove(){
			throw new UnsupportedOperationException();
		}
	}

	private final class MarketDataWholeOrderBookIterator implements Iterator<Tick> {
		private int cursorTickLevel;
		private final int imaginaryTickLevel;
		
		private MarketDataWholeOrderBookIterator(){
			int max = SpreadTable.SPREAD_TABLE_MIN_LEVEL;
			for (int i = 0; i < ticks.length; i++){
				if (ticks[i].tickLevel() != nullTickLevel){
					max = Math.max(max, ticks[i].tickLevel());
				}
			}
			this.cursorTickLevel = max;
			LOG.trace("max tick level: {}", max);
			this.imaginaryTickLevel = max - ticks.length + 1;
		}
		public boolean hasNext(){
			return this.cursorTickLevel >= imaginaryTickLevel;
		}
		public Tick next(){
			if (this.hasNext()){
				return ticks[cursorTickLevel-- & tickLevelMask];
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
