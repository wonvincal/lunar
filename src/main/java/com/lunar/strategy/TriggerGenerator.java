package com.lunar.strategy;

public interface TriggerGenerator extends StrategySignalGenerator {
    public static final int NO_TRIGGER = 0;
    public static final int WEAK_TRIGGER = 1;
    public static final int MEDIUM_TRIGGER = 2;
    public static final int STRONG_TRIGGER = 3;
    
    public boolean isTriggeredForCall();
    public boolean isTriggeredForPut();
    
    public int getTriggerStrengthForCall();
    public int getTriggerStrengthForPut();
    
    public void reset();
    
    public long getExplainValue();
}