package com.lunar.fsm.cutloss;

public interface SignalHandler {
	void handleCutLossSignal(TradeContext tradeContext, int tickLevel, long nanoOfDay);
	public static final SignalHandler NULL_HANDLER = new SignalHandler() {
		@Override
		public void handleCutLossSignal(TradeContext tradeContext, int tickLevel, long nanoOfDay) {
		}
	};
}
