package com.lunar.strategy.statemachine;

public interface EventTranslator {
	public int translateToTransitionId(final int eventId) throws Exception;
}
