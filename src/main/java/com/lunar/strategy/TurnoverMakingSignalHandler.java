package com.lunar.strategy;

public interface TurnoverMakingSignalHandler extends StrategySignalHandler {
    public void onTurnoverMakingDetected(final long nanoOfDay, final int price) throws Exception;
    
}
