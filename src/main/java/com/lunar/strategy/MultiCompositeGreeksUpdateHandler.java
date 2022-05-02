package com.lunar.strategy;

import java.util.ArrayList;
import java.util.List;

import com.lunar.pricing.Greeks;

public class MultiCompositeGreeksUpdateHandler implements CompositeGreeksUpdateHandler {
    private List<GreeksUpdateHandler> m_handlers;
    
    public MultiCompositeGreeksUpdateHandler() {
        m_handlers = new ArrayList<GreeksUpdateHandler>();
    }

    @Override
    public void onGreeksUpdated(final Greeks greeks) throws Exception {
        for (final GreeksUpdateHandler handler : m_handlers) {
            handler.onGreeksUpdated(greeks);
        }
    }

    @Override
    public void registerGreeksUpdateHandler(final GreeksUpdateHandler updateHandler) {
        if (!m_handlers.contains(updateHandler)) {
            m_handlers.add(updateHandler);
        }
    }

    @Override
    public void unregisterGreeksUpdateHandler(final GreeksUpdateHandler updateHandler) {
        m_handlers.remove(updateHandler);
    }
}
