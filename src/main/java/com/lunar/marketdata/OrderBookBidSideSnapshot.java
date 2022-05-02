package com.lunar.marketdata;

public class OrderBookBidSideSnapshot extends OrderBookSideSnapshot {

	OrderBookBidSideSnapshot(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice) {
		super(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

	@Override
	protected int getIndexFromTickLevel(int bestTickLevel, int tickLevel) {
		return bestTickLevel - tickLevel;		
	}

}
