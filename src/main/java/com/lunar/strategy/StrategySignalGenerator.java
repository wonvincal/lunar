package com.lunar.strategy;

public interface StrategySignalGenerator {
    public void registerHandler(final StrategySignalHandler handler);

    public void unregisterHandler(final StrategySignalHandler handler);
    
    public void reset();
}
