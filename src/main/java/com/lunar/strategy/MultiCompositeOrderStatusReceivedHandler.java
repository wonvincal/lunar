package com.lunar.strategy;

import java.util.ArrayList;
import java.util.List;

import com.lunar.message.io.sbe.OrderRequestRejectType;

public class MultiCompositeOrderStatusReceivedHandler implements CompositeOrderStatusReceivedHandler {
    private List<OrderStatusReceivedHandler> m_handlers;
    
    public MultiCompositeOrderStatusReceivedHandler() {
        m_handlers = new ArrayList<OrderStatusReceivedHandler>();
    }

    @Override
    public void onOrderStatusReceived(final long nanoOfDay, final int price, final long quantity, final OrderRequestRejectType rejectType) throws Exception {
        for (final OrderStatusReceivedHandler handler : m_handlers) {
            handler.onOrderStatusReceived(nanoOfDay, price, quantity, rejectType);
        }
    }

    @Override
    public void registerOrderStatusReceivedHandler(final OrderStatusReceivedHandler updateHandler) {
        if (!m_handlers.contains(updateHandler)) {
            m_handlers.add(updateHandler);
        }
    }

    @Override
    public void unregisterOrderStatusReceivedHandler(final OrderStatusReceivedHandler updateHandler) {
        m_handlers.remove(updateHandler);
    }
}
