package com.lunar.strategy.scoreboard;

public interface CompositeUnderlyingOrderBookUpdateHandler extends UnderlyingOrderBookUpdateHandler {
    void registerUnderlyingOrderBookUpdateHandler(final UnderlyingOrderBookUpdateHandler handler);
    void unregisterUnderlyingOrderBookUpdateHandler(final UnderlyingOrderBookUpdateHandler handler);

}
