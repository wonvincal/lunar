package com.lunar.order;

public interface ExchangeService {
	boolean isStopped();
	void updateListener(LineHandlerEngineOrderUpdateListener updateListener);
}
