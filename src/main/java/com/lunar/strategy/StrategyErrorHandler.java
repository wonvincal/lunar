package com.lunar.strategy;

public interface StrategyErrorHandler {
    void onError(final long secSid, final String message);
}
