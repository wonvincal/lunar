package com.lunar.strategy;

import com.lunar.core.SystemClock;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.StrategyType;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public interface StrategyWarmupEngine {
	public void initialize(final SystemClock systemClock, final StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender, final LongEntityManager<StrategyType> strategyTypes, final LongEntityManager<StrategyIssuer> issuers, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final Long2ObjectOpenHashMap<OrderStatusReceivedHandler> osReceivedHandlers);
	public void doWarmup();
	public void release();
}
