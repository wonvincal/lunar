package com.lunar.strategy.speedarbhybrid;

import com.lunar.entity.LongEntityManager;
import com.lunar.entity.StrategyType;
import com.lunar.strategy.Strategy;
import com.lunar.strategy.StrategyContext;
import com.lunar.strategy.StrategyFactory;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyIssuer;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategyScheduler;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.StrategyWarmupEngine;
import com.lunar.strategy.VelocityTriggerGenerator;
import com.lunar.strategy.parameters.GenericUndParams;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class SpeedArbHybridFactory implements StrategyFactory {
    public static String STRATEGY_NAME = "SpeedArb1";
    
    public SpeedArbHybridFactory() {        
    }
    
    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
    
    @Override
    public Strategy createStrategy(final StrategyContext strategyContext, final long securitySid, final long strategySid) {
        return SpeedArbHybridStrategy.of(strategyContext, securitySid, strategySid);
    }

    @Override
    public StrategyContext createStrategyContext(final StrategyType strategyType, final Long2ObjectOpenHashMap<StrategySecurity> underlyings, final Long2ObjectOpenHashMap<StrategySecurity> warrants, final LongEntityManager<StrategyIssuer> issuers, StrategyOrderService orderService, final StrategyInfoSender strategyInfoSender, final StrategyScheduler scheduler) {
        return new SpeedArbHybridContext(strategyType, underlyings, warrants, issuers, orderService, strategyInfoSender, scheduler, this);
    }

	@Override
	public StrategyWarmupEngine createWarmupEngine() {
		return new SpeedArbHybridWarmupEngine();
	}
	
	public VelocityTriggerGenerator createVelocityTriggerGenerator(final StrategySecurity security, final GenericUndParams params, final long windowTime, final int maxVelocityWindowEntries) {
	    return new VelocityTriggerGenerator(security, params, windowTime, maxVelocityWindowEntries);
	}
   
}

