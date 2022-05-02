package com.lunar.strategy;

/*
 * This is a weird one. The trigger generators don't push events, instead the handlers poll the generators
 * Therefore, the handlers will need to add a reference to the generators upon subscription
 */
public interface TriggerHandler extends StrategySignalHandler {
    void onTriggerGeneratorSubscribed(final TriggerGenerator generator);
    void onTriggerGeneratorUnsubscribed(final TriggerGenerator generator);
}
