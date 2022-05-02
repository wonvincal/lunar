package com.lunar.strategy;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class AllowAllTriggerGenerator implements TriggerGenerator {
    final ObjectArrayList<TriggerHandler> m_handlers;

    public AllowAllTriggerGenerator() {
        m_handlers = ObjectArrayList.wrap(new TriggerHandler[32]);
    }
    
    @Override
    public void registerHandler(final StrategySignalHandler handler) {
        if (handler instanceof TriggerHandler) {
            final TriggerHandler triggerHandler = (TriggerHandler)handler;
            if (!m_handlers.contains(triggerHandler)) {
                m_handlers.add(triggerHandler);
                triggerHandler.onTriggerGeneratorSubscribed(this);
            }
        }
    }

    @Override
    public void unregisterHandler(final StrategySignalHandler handler) {
        if (handler instanceof TriggerHandler) {
            final TriggerHandler triggerHandler = (TriggerHandler)handler;
            if (m_handlers.remove(triggerHandler)) {
                triggerHandler.onTriggerGeneratorUnsubscribed(this);
            }
        }
    }

    @Override
    public boolean isTriggeredForCall() {
        return true;
    }

    @Override
    public boolean isTriggeredForPut() {
        return true;
    }

    @Override
    public int getTriggerStrengthForCall() {
        return 0;
    }

    @Override
    public int getTriggerStrengthForPut() {
        return 0;
    }

    @Override
    public void reset() {
    }

    @Override
    public long getExplainValue() {
        return 0;
    }

}
