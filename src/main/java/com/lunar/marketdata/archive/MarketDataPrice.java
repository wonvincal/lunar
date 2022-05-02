package com.lunar.marketdata.archive;

/**
 * A structure to hold price info of a market data order book.  Chose primitive boolean flag over {@link Optional}
 * because {@link Optional} encourages small memory allocations. 
 * @author wongca
 *
 */
public class MarketDataPrice {
	private boolean hasBestBid;
	private int bestBid;
	private boolean hasBestAsk;
	private int bestAsk;
	private boolean hasLast;
	private int last;
	
	public static MarketDataPrice of(){
		return new MarketDataPrice();
	}
	
	MarketDataPrice(){
		bestBid = Integer.MAX_VALUE;
		bestAsk = Integer.MIN_VALUE;
		last = Integer.MIN_VALUE;
	}
	
	public boolean hasBestBid() { return hasBestBid; }
	public boolean hasBestAsk() { return hasBestAsk; }
	public boolean hasLast() { return hasLast; }
	public double bestBid(){ return bestBid;}
	public double bestAsk(){ return bestAsk;}
	public double last(){ return last;}
	public void clear(){
		this.hasBestAsk = false;
		this.hasBestBid = false;
		this.hasLast = false;
	}
	public MarketDataPrice bestBid(int value){ 
		this.bestBid = value;
		this.hasBestBid = true;
		return this;
	}
	public MarketDataPrice bestAsk(int value){
		this.bestAsk = value;
		return this;
	}
	
	public MarketDataPrice last(int value){
		this.last = value;
		return this;
	}
}
