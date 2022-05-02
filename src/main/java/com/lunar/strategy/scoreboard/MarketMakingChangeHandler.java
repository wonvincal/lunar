package com.lunar.strategy.scoreboard;

public interface MarketMakingChangeHandler {
	void handleNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay);
	void handleBidLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay);
	void handleNumAskLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay);
	void handleAskLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay);
	void handleBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay);
	void handleMMBestLevelChange(int mmBidLevel, int mmAskLevel, boolean isWide, long nanoOfDay);
	
	public final static MarketMakingChangeHandler NULL_HANDLER = new MarketMakingChangeHandler() {
		@Override
		public void handleNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		}
		@Override
		public void handleBidLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
		}
		@Override
		public void handleNumAskLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay) {
		}
		@Override
		public void handleAskLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay) {
		}
		@Override
		public void handleBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay){
		}
		@Override
		public void handleMMBestLevelChange(int mmBidLevel, int mmAskLeve, boolean isWide, long nanoOfDay) {
		}
	};
}
