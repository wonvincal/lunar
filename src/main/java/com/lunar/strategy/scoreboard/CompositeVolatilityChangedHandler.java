package com.lunar.strategy.scoreboard;

public interface CompositeVolatilityChangedHandler extends VolatilityChangedHandler {
    void registerDropVolHandler(final VolatilityChangedHandler handler);
    void unregisterDropVolHandler(final VolatilityChangedHandler handler);
}
