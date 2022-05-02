package com.lunar.marketdata.archive;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.marketdata.OrderBookSide;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.archive.BidMarketOrderBook;
import com.lunar.marketdata.archive.SingleSideMarketOrderBook;
import com.lunar.order.Tick;
import com.lunar.util.BitUtil;

public final class BidMarketOrderBookWithProxy implements SingleSideMarketOrderBook {
	private static final Logger LOG = LogManager.getLogger(BidMarketOrderBook.class);
	private static final int NUM_DELETE_PROXY = 2;
	private static final int NULL_DELETE = 1;
	private static final int NORMAL_DELETE = 0;
	private final int bookDepth;
	private final int capacity;
	private int topOfBookTickLevel;
	private int bottomOfBookTickLevel;
	private final Tick[] ticks;
	private final DeleteProxy[] deleteProxy;
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
	
	public BidMarketOrderBookWithProxy(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
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
		
		// create two delete proxy object to avoid branching while delete
		this.deleteProxy = new DeleteProxy[NUM_DELETE_PROXY];
		this.deleteProxy[NORMAL_DELETE] = new DeleteProxy() {
			@Override
			public void delete(int tickLevel) {
				ticks[tickLevel & tickLevelMask].qty(0).numOrders(0).price(nullPrice).tickLevel(nullTickLevel);
				
				// need to move best and top of book if necessary
				// delete can happen to top of the book or not.  it is really hard to predict for the compiler
				// better remove branching from here
				int deletedTop = (tickLevel == topOfBookTickLevel) ? 0 : -1; // compiler will turn this into something fast
				int newTopLevel = (deletedTop & topOfBookTickLevel) +
								  (~deletedTop & (ticks[(tickLevel - 1) & tickLevelMask].qty() != 0 ? -1 : 0) & (tickLevel - 1));
								
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
		};
		this.deleteProxy[NULL_DELETE] = new DeleteProxy() {
			@Override
			public void delete(int tickLevel) {}
		};
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
	
	@Override
	public int nullTickLevel(){
		return nullTickLevel;
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
		// keep using ? instead of bitwise compare, this is likely to be translated into
		// a conditional move in assembly
		this.deleteProxy[(topOfBookTickLevel - tickLevel > this.bookDepth) ? NULL_DELETE : NORMAL_DELETE].delete(tickLevel);
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
