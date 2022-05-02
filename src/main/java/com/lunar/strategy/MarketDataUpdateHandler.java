package com.lunar.strategy;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;

public interface MarketDataUpdateHandler {
    final static int BID = MarketTrade.BID;
    final static int ASK = MarketTrade.ASK;

	void onOrderBookUpdated(final Security security, final long timestamp, final MarketOrderBook orderBook) throws Exception;
	void onTradeReceived(final Security security, final long timestamp, final MarketTrade marketTrade) throws Exception;

}
