package com.lunar.marketdata;

import com.lunar.core.SbeEncodable;
import com.lunar.core.TriggerInfo;
import com.lunar.order.Tick;

public interface MarketOrderBook extends SbeEncodable {
	public static final int BID_INDEX = 0;
	public static final int ASK_INDEX = 1;
	
	public static MarketOrderBook of(int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice) {
		return new OrderBookSnapshot(bookDepth, spreadTable, nullTickLevel, nullPrice);
	}
	
	public static MarketOrderBook of(long secSid, int bookDepth, SpreadTable spreadTable, int nullTickLevel, int nullPrice) {
		return new OrderBookSnapshot(secSid, bookDepth, spreadTable, nullTickLevel, nullPrice);
	}	

	public long secSid();
	public MarketOrderBook secSid(final long secSid);
	
	public OrderBookSide bidSide();	
	public OrderBookSide askSide();
	public OrderBookSide side(final int index);

	public Tick bestBidOrNullIfEmpty();
	public Tick bestAskOrNullIfEmpty();
	public boolean isEmpty();
	
	public TriggerInfo triggerInfo();
	
	public MarketOrderBook channelSeqNum(final int seqNum);	
	public int channelSeqNum();
	
	public MarketOrderBook transactNanoOfDay(final long timestamp);
	public long transactNanoOfDay();
	
	public MarketOrderBook isRecovery(final boolean isRecovery);
	public boolean isRecovery();
	
	public void copyFrom(final MarketOrderBook other);
	
}
