package com.lunar.strategy.scoreboard;

public interface CompositeOurTriggerHandler extends OurTriggerHandler {
    void registerOurTriggerHandler(final OurTriggerHandler ourTriggerHandler);
    void unregisterOurTriggerHandler(final OurTriggerHandler ourTriggerHandler);

}
