package com.lunar.strategy.scoreboard;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.order.Tick;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.MarketDataUpdateHandler;

public class UnderlyingSignalGenerator implements MarketDataUpdateHandler {
    private final ScoreBoardSecurityInfo security;
    final UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook underlyingOrderBook;

    private long prevMidPrice;
    private long prevWeightedAverage;
    
    public UnderlyingSignalGenerator(final ScoreBoardSecurityInfo security) {
        this.security = security;
        this.underlyingOrderBook = new UnderlyingOrderBookUpdateHandler.UnderlyingOrderBook();
        security.registerMdUpdateHandler(this);
    }
    
    @Override
    public void onOrderBookUpdated(final Security srcSecurity, final long nanoOfDay, final MarketOrderBook orderBook) throws Exception {
        final Tick bestBid = security.orderBook().bestBidOrNullIfEmpty();
        final Tick bestAsk = security.orderBook().bestAskOrNullIfEmpty();
        final int bidTickSize;
        if (bestBid != null) {
            underlyingOrderBook.bestBidPrice = bestBid.price();
            underlyingOrderBook.bestBidLevel = bestBid.tickLevel();
            underlyingOrderBook.bestBidSize = bestBid.qty();
            bidTickSize = this.security.spreadTable().priceToTickSize(underlyingOrderBook.bestBidPrice);
        }
        else {
            underlyingOrderBook.bestBidPrice = 0;
            underlyingOrderBook.bestBidLevel = 0;
            underlyingOrderBook.bestBidSize = 0;
            bidTickSize = 0;
        }
        if (bestAsk != null) {
            underlyingOrderBook.bestAskPrice = bestAsk.price();
            underlyingOrderBook.bestAskLevel = bestAsk.tickLevel();
            underlyingOrderBook.bestAskSize = bestAsk.qty();
            if (bestBid != null) {
                underlyingOrderBook.spread = underlyingOrderBook.bestAskLevel - underlyingOrderBook.bestBidLevel;
            }
            else {
                underlyingOrderBook.spread = 0;
            }
        }
        else {
            underlyingOrderBook.bestAskPrice = 0;
            underlyingOrderBook.bestAskLevel = 0;
            underlyingOrderBook.bestAskSize = 0;
            underlyingOrderBook.spread = 0;
        }     
        if (underlyingOrderBook.bestBidPrice != 0 && underlyingOrderBook.bestAskPrice != 0) {
            underlyingOrderBook.midPrice = (long)((underlyingOrderBook.bestBidPrice + underlyingOrderBook.bestAskPrice) * ServiceConstant.WEIGHTED_AVERAGE_SCALE) / 2L;
            if (underlyingOrderBook.spread == 1) {
                underlyingOrderBook.weightedAverage = (long)((double)(underlyingOrderBook.bestBidPrice * underlyingOrderBook.bestBidSize + underlyingOrderBook.bestAskPrice * underlyingOrderBook.bestAskSize) / (underlyingOrderBook.bestBidSize + underlyingOrderBook.bestAskSize) * ServiceConstant.WEIGHTED_AVERAGE_SCALE);
            }
            else {
                underlyingOrderBook.weightedAverage = underlyingOrderBook.midPrice;
            }
            if (this.prevMidPrice != 0) {
                if (underlyingOrderBook.midPrice > this.prevMidPrice) {
                    underlyingOrderBook.lastTimeMidPriceUp = nanoOfDay;
                }
                else if (underlyingOrderBook.midPrice < this.prevMidPrice) {
                    underlyingOrderBook.lastTimeMidPriceDown = nanoOfDay;
                }
            }
            if (this.prevWeightedAverage != 0) {
                final long changeRequired = bidTickSize * ServiceConstant.WEIGHTED_AVERAGE_SCALE / 4;
                if (underlyingOrderBook.weightedAverage > this.prevWeightedAverage + changeRequired) {
                    underlyingOrderBook.lastTimeWeightedAverageUp = nanoOfDay;
                }
                else if (underlyingOrderBook.weightedAverage < this.prevWeightedAverage - changeRequired) {
                    underlyingOrderBook.lastTimeWeightedAverageDown = nanoOfDay;
                }
            }
        }
        else {
            underlyingOrderBook.midPrice = 0;
            underlyingOrderBook.weightedAverage = 0;
        }        
        security.underlyingOrderBookUpdateHandler().onUnderlyingOrderBookUpdated(nanoOfDay, underlyingOrderBook);
        this.prevMidPrice = underlyingOrderBook.midPrice;
        this.prevWeightedAverage = underlyingOrderBook.weightedAverage;
    }

    @Override
    public void onTradeReceived(final Security srcSecurity, final long timestamp, final MarketTrade trade) throws Exception {
        
    }

}
