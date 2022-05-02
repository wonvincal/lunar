package com.lunar.marketdata;

public class OrderBookAskSideSnapshot extends OrderBookSideSnapshot {

	OrderBookAskSideSnapshot(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice) {
		super(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}

	@Override
	protected int getIndexFromTickLevel(int bestTickLevel, int tickLevel) {
		return tickLevel - bestTickLevel;		
	}

}
