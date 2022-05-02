package com.lunar.marketdata.archive;

import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.SecurityType;

/**
 * A builder class to create security specific subscribable order book
 * @author wongca
 *
 */
public class SubscribableMarketOrderBookBuilder {
	public SubscribableMarketOrderBookBuilder(){}
	public SubscribableMarketOrderBook build(long secSid){
		return new SubscribableMarketOrderBook(secSid, MarketOrderBook.of(10, SpreadTableBuilder.get(SecurityType.WARRANT), -1, -1));
	}
}
