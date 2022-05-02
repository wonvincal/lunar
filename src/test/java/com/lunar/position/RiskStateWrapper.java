package com.lunar.position;

public class RiskStateWrapper {
	private final RiskState state;
	public RiskStateWrapper(RiskState state){
		this.state = state;
	}
	public void updateOpenPosition(int newState, long newValue){
		state.updateOpenPosition(newState, newValue);
	} 	
	public void updateMaxProfit(int newMaxProfitState, double newTotalPnl){
		state.updateMaxProfit(newMaxProfitState, newTotalPnl);
	}
	public void updateMaxLoss(int newMaxLossState, double newTotalPnl){
		state.updateMaxLoss(newMaxLossState, newTotalPnl);
	}
	public void updateMaxCapUsed(int newMaxCapLimitState, double newCapUsed){
		state.updateMaxCapUsed(newMaxCapLimitState, newCapUsed);
	}
}
