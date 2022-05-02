package com.lunar.marketdata.archive;

import java.util.Iterator;

import com.lunar.marketdata.OrderBookSide;
import com.lunar.marketdata.SpreadTable;
import com.lunar.order.Tick;

public interface SingleSideMarketOrderBook extends OrderBookSide {

	/**
	 * Whether this order book is empty or not
	 * @return
	 */
	public abstract boolean isEmpty();

	/**
	 * This method returns an Tick object at the top of the book, it is not necessary the
	 * best
	 * 
	 * Please change this according to your needs.
	 * @return
	 */
	public abstract Tick bestOrNullIfEmpty();

	public abstract void updateByPrice(int price, int priceLevel, long qty, int numOrder);

	public abstract void update(int tickLevel, int price, int priceLevel, long qty, int numOrder);

	public abstract void create(int tickLevel, int price, int priceLevel, long qty, int numOrder);

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
	public abstract void deleteByPrice(int price, int priceLevel);

	public abstract void delete(int tickLevel, int price, int priceLevel);

	public abstract void clear();

	public abstract Iterator<Tick> iterator();

	public abstract Iterator<Tick> iteratorForWhole();
	
	public static SingleSideMarketOrderBook forBid(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
		return new BidMarketOrderBook(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

	public static SingleSideMarketOrderBook forAsk(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
		return new AskMarketOrderBook(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

	public static SingleSideMarketOrderBook forPriceLevelBased(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice){
		return new PriceLevelBasedMarketOrderBook(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

}