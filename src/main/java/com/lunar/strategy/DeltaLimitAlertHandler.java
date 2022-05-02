package com.lunar.strategy;

import com.lunar.entity.Security;

public interface DeltaLimitAlertHandler extends StrategySignalHandler {
    public void onDeltaLimitExceeded(final Security triggerSecurity, final long nanoOfDay, final long netDelta) throws Exception;

}
