package com.lunar.marketdata;

import org.agrona.MutableDirectBuffer;

import com.lunar.core.SbeEncodable;
import com.lunar.core.TriggerInfo;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.MarketStatsSender;

public class MarketStats implements SbeEncodable {
    private long secSid;
    private long transactTime;
    private int seqNum;
    private int open;
    private int high;
    private int low;
    private int close;
    private int volume;
    private long turnover;
    private boolean isRecovery;
    private TriggerInfo triggerInfo;
    
    static public MarketStats of() {
        return new MarketStats();
    }
    
    private MarketStats() {
        triggerInfo = TriggerInfo.of();        
    }
    
    public MarketStats secSid(final long secSid) {
        this.secSid = secSid;
        return this;
    }
    public long secSid() {
        return this.secSid;
    }
    
    public MarketStats transactTime(final long transactTime) {
        this.transactTime = transactTime;
        return this;
    }
    public long transactTime() {
        return this.transactTime;        
    }
    
    public MarketStats seqNum(final int seqNum) {
        this.seqNum = seqNum;
        return this;
    }
    public int seqNum() {
        return this.seqNum;        
    }
    
    public MarketStats open(final int open) {
        this.open = open;
        return this;
    }
    public int open() {
        return this.open;
    }
    
    public MarketStats high(final int high) {
        this.high = high;
        return this;
    }
    public int high() {
        return this.high;
    }
    
    public MarketStats low(final int low) {
        this.low = low;
        return this;
    }
    public int low() {
        return this.low;
    }
    
    public MarketStats close(final int close) {
        this.close = close;
        return this;
    }
    public int close() {
        return this.close;
    }

    public MarketStats volume(final int volume) {
        this.volume = volume;
        return this;
    }
    public int volume() {
        return this.volume;
    }
    
    public MarketStats turnover(final long turnover) {
        this.turnover = turnover;
        return this;
    }
    public long turnover() {
        return this.turnover;
    }
    
    public MarketStats isRecovery(final boolean isRecovery) {
        this.isRecovery = isRecovery;
        return this;
        
    }
    public boolean isRecovery() {
        return this.isRecovery;
    }

    public MarketStats triggerInfo(final TriggerInfo triggerInfo) {
        this.triggerInfo = triggerInfo;
        return this;
    }
    public TriggerInfo triggerInfo() {
        return this.triggerInfo;
    }

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return MarketStatsSender.encodeMarketStatsWithoutHeader(buffer, offset, encoder.marketStatsSbeEncoder(), this);
    }

    @Override
    public TemplateType templateType() {
        return TemplateType.MARKET_STATS;
    }

    @Override
    public short blockLength() {
        return MarketStatsSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    public int expectedEncodedLength() {
        return MarketStatsSbeDecoder.BLOCK_LENGTH;
    }

    @Override
    public int schemaId() {
        return MarketStatsSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return MarketStatsSbeEncoder.SCHEMA_VERSION;
    }

}
