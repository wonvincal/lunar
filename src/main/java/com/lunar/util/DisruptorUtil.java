package com.lunar.util;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

public class DisruptorUtil {
	public static WaitStrategy convertToDisruptorStrategy(com.lunar.core.WaitStrategy strategy){
		switch (strategy){
		case BLOCKING_WAIT:
			return new BlockingWaitStrategy();
		case BUSY_SPIN:
			return new BusySpinWaitStrategy();
		case LITE_BLOCKING:
			return new LiteBlockingWaitStrategy();
		case YIELDING_WAIT:
			return new YieldingWaitStrategy();
		default:
			throw new IllegalArgumentException("Unexpected strategy: " + strategy.name());
		}
	}
}
