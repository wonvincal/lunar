package com.lunar.strategy;

public interface CompositeMarketDataUpdateHandler extends MarketDataUpdateHandler {
	void registerOrderBookUpdateHandler(final MarketDataUpdateHandler obUpdateHandler);
	void unregisterOrderBookUpdateHandler(final MarketDataUpdateHandler obUpdateHandler);

}
