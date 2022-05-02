package com.lunar.strategy.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class MultiCompositeVolatilityChangedHandler implements CompositeVolatilityChangedHandler {
    private List<VolatilityChangedHandler> m_handlers;
    
    public MultiCompositeVolatilityChangedHandler() {
        m_handlers = new ArrayList<VolatilityChangedHandler>();
    }

    @Override
    public void onVolDropped(final long nanoOfDay) {
        for (final VolatilityChangedHandler handler : m_handlers) {
            handler.onVolDropped(nanoOfDay);
        }
    }
    
    @Override
    public void onVolRaised(final long nanoOfDay) {
        for (final VolatilityChangedHandler handler : m_handlers) {
            handler.onVolRaised(nanoOfDay);
        }
    }

    @Override
    public void registerDropVolHandler(final VolatilityChangedHandler handler) {
        if (!m_handlers.contains(handler)) {
            m_handlers.add(handler);
        }
    }

    @Override
    public void unregisterDropVolHandler(final VolatilityChangedHandler handler) {
        m_handlers.remove(handler);
    }

}
