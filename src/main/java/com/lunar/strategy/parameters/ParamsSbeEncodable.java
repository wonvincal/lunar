package com.lunar.strategy.parameters;

import com.lunar.core.SbeEncodable;

abstract public class ParamsSbeEncodable implements SbeEncodable {
    private long lastSendTime;
    
    public long lastSendTime() {
        return lastSendTime;
    }
    public void lastSendTime(final long lastSendTime) {
        this.lastSendTime = lastSendTime;
    }
    
    abstract public ParamsSbeEncodable clone();
    
    abstract public void copyTo(final ParamsSbeEncodable other);

}
