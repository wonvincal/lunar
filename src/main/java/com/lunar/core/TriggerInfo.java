package com.lunar.core;

import com.lunar.message.io.sbe.TriggerInfoDecoder;

public class TriggerInfo {
    private byte triggeredBy;
    private int triggerSeqNum;
    private long triggerNanoOfDay;
    
    static public TriggerInfo of() {
        return new TriggerInfo();
    }
    
    static public TriggerInfo of(final TriggerInfoDecoder decoder) {
        return new TriggerInfo(decoder);
    }
    
    static public TriggerInfo of(final TriggerInfo triggerInfo) {
        return new TriggerInfo(triggerInfo);
    }
    
    TriggerInfo() {
        
    }
    
    TriggerInfo(final TriggerInfoDecoder decoder) {
        decode(decoder);
    }
    
    TriggerInfo(final TriggerInfo triggerInfo) {
        copyFrom(triggerInfo);
    }
    
    public byte triggeredBy() {
        return this.triggeredBy;
    }
    public TriggerInfo triggeredBy(final byte triggeredBy) {
        this.triggeredBy = triggeredBy;
        return this;
    }
    
    public int triggerSeqNum() {
        return this.triggerSeqNum;
    }
    public TriggerInfo triggerSeqNum(final int triggerSeqNum) {
        this.triggerSeqNum = triggerSeqNum;
        return this;
    }
    
    public long triggerNanoOfDay() {
        return this.triggerNanoOfDay;
    }
    public TriggerInfo triggerNanoOfDay(final long triggerNanoOfDay) {
        this.triggerNanoOfDay = triggerNanoOfDay;
        return this;
    }
    
    public TriggerInfo decode(final TriggerInfoDecoder decoder) {
        this.triggeredBy = decoder.triggeredBy();
        this.triggerSeqNum = decoder.triggerSeqNum();
        this.triggerNanoOfDay = decoder.nanoOfDay();
        return this;
    }
    
    public TriggerInfo copyFrom(final TriggerInfo triggerInfo) {
        this.triggeredBy = triggerInfo.triggeredBy();
        this.triggerSeqNum = triggerInfo.triggerSeqNum();
        this.triggerNanoOfDay = triggerInfo.triggerNanoOfDay();
        return this;
    }
        
}
