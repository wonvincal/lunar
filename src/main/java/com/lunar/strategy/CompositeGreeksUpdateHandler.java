package com.lunar.strategy;

public interface CompositeGreeksUpdateHandler extends GreeksUpdateHandler {
    void registerGreeksUpdateHandler(final GreeksUpdateHandler obUpdateHandler);
    void unregisterGreeksUpdateHandler(final GreeksUpdateHandler obUpdateHandler);

}
