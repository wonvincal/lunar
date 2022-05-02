package com.lunar.position;

import com.lunar.message.io.sbe.EntityType;

public interface RiskControlStateChangeHandler {
	public void handleOpenPositionStateChange(long entitySid, EntityType entityType, long current, long previous, boolean exceeded);
	public void handleMaxProfitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded);
	public void handleMaxLossStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded);
	public void handleMaxCapLimitStateChange(long entitySid, EntityType entityType, double current, double previous, boolean exceeded);
}
