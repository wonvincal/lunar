package com.lunar.strategy;

import com.lunar.entity.LongEntityManager;
import com.lunar.entity.StrategyType;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public interface StrategyFactory {
    String getStrategyName();
    Strategy createStrategy(final StrategyContext strategyContext, final long securitySid, final long strategySid);
    StrategyContext createStrategyContext(final StrategyType strategyType, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final LongEntityManager<StrategyIssuer> issuers, final StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender, final StrategyScheduler scheduler);
    StrategyWarmupEngine createWarmupEngine();
    
    
}
