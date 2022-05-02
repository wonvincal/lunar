package com.lunar.strategy;

import com.lunar.message.io.sbe.StrategyExitMode;

public interface StrategyControlHandler {
	void onSwitchedOn() throws Exception;
	void onSwitchedOff(final StrategyExitMode exitMode) throws Exception;
	boolean isOn();
	boolean isOff();
	boolean isExiting();
}
