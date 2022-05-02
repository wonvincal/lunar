package com.lunar.strategy;

import com.lunar.core.TriggerInfo;

public interface UnderlyingSpotObserver {
    public void observeUndSpot(final long nanoOfDay, final long weightedAverage, final long midPrice, final boolean isTightSpread, final TriggerInfo triggerInfo) throws Exception;
    public void onUnderlyingTickSizeChanged(final int tickSize) throws Exception;

}
