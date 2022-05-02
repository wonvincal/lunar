package com.lunar.strategy.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class MultiCompositeMarketOrderHandler implements CompositeMarketOrderHandler {
    private List<MarketOrderHandler> m_handlers;
    
    public MultiCompositeMarketOrderHandler() {
        m_handlers = new ArrayList<MarketOrderHandler>();
    }

    @Override
    public void onBuyTrade(final long nanoOfDay, final MarketOrder marketOrder) {
        for (final MarketOrderHandler handler : m_handlers) {
            handler.onBuyTrade(nanoOfDay, marketOrder);
        }        
    }

    @Override
    public void onSellTrade(long nanoOfDay, MarketOrder marketOrder, MarketOrder matchedBuyOrder) {
        for (final MarketOrderHandler handler : m_handlers) {
            handler.onSellTrade(nanoOfDay, marketOrder, matchedBuyOrder);
        }        
    }

    @Override
    public void registerMarketOrderHandler(MarketOrderHandler marketOrderHandler) {
        if (!m_handlers.contains(marketOrderHandler)) {
            m_handlers.add(marketOrderHandler);
        }        
    }

    @Override
    public void unregisterMarketOrderHandler(MarketOrderHandler marketOrderHandler) {
        m_handlers.remove(marketOrderHandler);        
    }
}
