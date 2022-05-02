package com.lunar.strategy.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class MultiCompositeOurTriggerHandler implements CompositeOurTriggerHandler {
    private List<OurTriggerHandler> m_handlers;

    public MultiCompositeOurTriggerHandler() {
        m_handlers = new ArrayList<OurTriggerHandler>();
    }

    @Override
    public void onOurTriggerBuyReceived(long timestamp, int price, OurTriggerExplain ourTriggerExplain, boolean isAdditionalTrigger, long triggerSeqNum) {
        for (final OurTriggerHandler handler : m_handlers) {
            handler.onOurTriggerBuyReceived(timestamp, price, ourTriggerExplain, isAdditionalTrigger, triggerSeqNum);
        }                
    }

    @Override
    public void onOurTriggerSellReceived(long timestamp, final int price, OurTriggerExplain ourTriggerExplain, long triggerSeqNum) {
        for (final OurTriggerHandler handler : m_handlers) {
            handler.onOurTriggerSellReceived(timestamp, price, ourTriggerExplain, triggerSeqNum);
        }        
    }

    @Override
    public void registerOurTriggerHandler(OurTriggerHandler ourTriggerHandler) {
        if (!m_handlers.contains(ourTriggerHandler)) {
            m_handlers.add(ourTriggerHandler);
        }
    }

    @Override
    public void unregisterOurTriggerHandler(OurTriggerHandler ourTriggerHandler) {
        m_handlers.remove(ourTriggerHandler);
    }

}
