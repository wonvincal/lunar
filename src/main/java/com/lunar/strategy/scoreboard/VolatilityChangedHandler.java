package com.lunar.strategy.scoreboard;

public interface VolatilityChangedHandler {
    void onVolDropped(final long nanoOfDay);
    void onVolRaised(final long nanoOfDay);
}
