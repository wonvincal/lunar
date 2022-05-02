package com.lunar.strategy;

import com.lunar.message.io.sbe.OrderType;

public interface StrategyOrderService {
    boolean canTrade();
	void buy(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain);
	void sell(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain);
	void sellLimit(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain);
	void sellToExit(final StrategySecurity security, final int price, final long quantity, final StrategyExplain explain);
	void cancelAndSellOutstandingSell(final StrategySecurity security, final int price, final StrategyExplain explain);
	default void setBuyOrderType(final OrderType orderType) {
	    
	}
}
