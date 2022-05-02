package com.lunar.strategy.scoreboard;

public interface MarketOrderHandler {
    public class MarketOrder {
        long triggerSeqNum;
        long orderTime;
        int side;
        int price;
        int mmBid;
        int tickLevel;
        long grossPrice;
        long quantity;
        long spread_3L;
        long quantityOnMarket;
        int numTrades;
        boolean hasDoubt;
        int orderId;
        int punterTriggerId;
    }

    void onBuyTrade(final long nanoOfDay, final MarketOrder marketOrder);
    void onSellTrade(final long nanoOfDay, final MarketOrder marketOrder, final MarketOrder matchedBuyOrder);
}
