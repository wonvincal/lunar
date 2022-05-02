package com.lunar.strategy;

import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.StrategyExplainType;

public interface StrategyExplain {
	StrategyExplainType strategyExplain();
	EventValueType[] eventValueTypes();
	long[] eventValues();
	StrategyExplain orderSid(long orderSid);
	void logExplainForBuyOrder();
	void logExplainForSellOrder();
	long triggerSeqNum();
	void triggerSeqNum(long triggerSeqNum);
}
