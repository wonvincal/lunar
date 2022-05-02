package com.lunar.strategy.scoreboard;

public interface UnderlyingOrderBookUpdateHandler {
    public class UnderlyingOrderBook {
        public int bestBidPrice;
        public int bestBidLevel;
        public long bestBidSize;
        public int bestAskPrice;
        public int bestAskLevel;
        public long bestAskSize;
        public int spread;
        public long midPrice;
        public long weightedAverage;
        public long lastTimeMidPriceUp;
        public long lastTimeMidPriceDown;
        public long lastTimeWeightedAverageUp;
        public long lastTimeWeightedAverageDown;
    }
    void onUnderlyingOrderBookUpdated(final long nanoOfDay, final UnderlyingOrderBook underlyingOrderBook);
}
