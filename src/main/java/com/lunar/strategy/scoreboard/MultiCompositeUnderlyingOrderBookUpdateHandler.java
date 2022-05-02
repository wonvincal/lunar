package com.lunar.strategy.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class MultiCompositeUnderlyingOrderBookUpdateHandler implements CompositeUnderlyingOrderBookUpdateHandler {
    private List<UnderlyingOrderBookUpdateHandler> m_handlers;

    public MultiCompositeUnderlyingOrderBookUpdateHandler() {
        m_handlers = new ArrayList<UnderlyingOrderBookUpdateHandler>();
    }
    
    @Override
    public void onUnderlyingOrderBookUpdated(final long nanoOfDay, final UnderlyingOrderBook underlyingOrderBook) {
        for (final UnderlyingOrderBookUpdateHandler handler : m_handlers) {
            handler.onUnderlyingOrderBookUpdated(nanoOfDay, underlyingOrderBook);
        }
    }

    @Override
    public void registerUnderlyingOrderBookUpdateHandler(final UnderlyingOrderBookUpdateHandler handler) {
        if (!m_handlers.contains(handler)) {
            m_handlers.add(handler);
        }
    }

    @Override
    public void unregisterUnderlyingOrderBookUpdateHandler(final UnderlyingOrderBookUpdateHandler handler) {
        m_handlers.remove(handler);        
    }

}
