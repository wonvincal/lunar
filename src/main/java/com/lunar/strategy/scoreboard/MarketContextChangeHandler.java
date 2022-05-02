package com.lunar.strategy.scoreboard;

public interface MarketContextChangeHandler {
	void handleNumBidLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay);
	void handleBidLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay);
	void handleNumAskLevelChange(int numBestLevels, int numNonBestLevels, long nanoOfDay);
	void handleAskLevelQuantityChange(long bestQty, long nonBestQty, long nanoOfDay);
	void handleBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay);
	void handleMMBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay);
	void handleUndBestLevelChange(long nanoOfDay, int bestBidPrice, int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread);
	
	public final static MarketContextChangeHandler NULL_HANDLER = new MarketContextChangeHandler() {
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
		public void handleUndBestLevelChange(long nanoOfDay, int bestBidPrice, int bestBidLevel, int bestAskPrice, int bestAskLevel, int spread) {
		}
		@Override
		public void handleMMBestLevelChange(int bidLevel, int askLevel, boolean isWide, long nanoOfDay) {
		}
	};
}
