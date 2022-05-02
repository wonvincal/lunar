package com.lunar.strategy;

public interface UnderlyingPrice {
    public int getBidPrice();
    
    public int getAskPrice();

    public int getPrevBidPrice();

    public int getPrevAskPrice();
    
    public boolean isTightSpread();
    
    public boolean isPrevTightSpread();
    
    public int getAskTickSize();

    public int getBidTickSize();
    
    public long getWeightedAverage();

    public long getPrevWeightedAverage();
    
    public long getMidPrice();
    
    public long getPrevMidPrice();
}
