package com.lunar.strategy.scoreboard;

public interface CompositeMarketOrderHandler extends MarketOrderHandler {
    void registerMarketOrderHandler(final MarketOrderHandler marketOrderHandler);
    void unregisterMarketOrderHandler(final MarketOrderHandler marketOrderHandler);

}
