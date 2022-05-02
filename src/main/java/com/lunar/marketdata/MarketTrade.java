package com.lunar.marketdata;

import com.lunar.core.SbeDecodable;
import com.lunar.core.TriggerInfo;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.TradeType;

public class MarketTrade implements SbeDecodable<MarketDataTradeSbeDecoder> {
    final public static int BID = 1;
    final public static int ASK = -1;
    
    private long secSid;
    private long tradeNanoOfDay;
    private int side;
    private int price;
    private int quantity;
    private TradeType tradeType;
    private boolean isRecovery;
    private int numActualTrades;
    private TriggerInfo triggerInfo;
    
    static public MarketTrade of() {
        return new MarketTrade();
    }
    
    static public MarketTrade of(final TriggerInfo triggerInfo) {
        return new MarketTrade(triggerInfo);
    }
    
    private MarketTrade() {
        this(TriggerInfo.of());
    }
    
    private MarketTrade(final TriggerInfo triggerInfo) {
        this.triggerInfo = triggerInfo;
    }
    
    public MarketTrade secSid(final long secSid) {
        this.secSid = secSid;
        return this;
    }
    public long secSid() {
        return this.secSid;
    }
    
    public MarketTrade tradeNanoOfDay(final long tradeNanoOfDay) {
        this.tradeNanoOfDay = tradeNanoOfDay;
        return this;
    }
    public long tradeNanoOfDay() {
        return this.tradeNanoOfDay;        
    }
    
    public MarketTrade side(final int side) {
        this.side = side;
        return this;
    }
    public int side() {
        return this.side;
    }
    
    public MarketTrade price(final int price) {
        this.price = price;
        return this;
    }
    public int price() {
        return this.price;
    }
    
    public MarketTrade quantity(final int quantity) {
        this.quantity = quantity;
        return this;
    }
    public int quantity() {
        return this.quantity;
    }
    
    public MarketTrade tradeType(final TradeType tradeType) {
        this.tradeType = tradeType;
        return this;
    }
    public TradeType tradeType() {
        return this.tradeType;
    }

    public MarketTrade numActualTrades(final int numActualTrades) {
        this.numActualTrades = numActualTrades;
        return this;
    }
    public int numActualTrades() {
        return numActualTrades;
    }

    public MarketTrade isRecovery(final boolean isRecovery) {
        this.isRecovery = isRecovery;
        return this;
        
    }
    public boolean isRecovery() {
        return this.isRecovery;
    }

    public TriggerInfo triggerInfo() {
        return this.triggerInfo;
    }
    
    public void detectSide(final MarketOrderBook orderBook) {
        side = ASK;
        if (!orderBook.bidSide().isEmpty()) {
            if (orderBook.bidSide().bestOrNullIfEmpty().price() >= price) {
                side = BID;
            }
        }
    }
    
    public void copyFrom(final MarketTrade marketTrade) {
        this.secSid = marketTrade.secSid;
        this.tradeNanoOfDay = marketTrade.tradeNanoOfDay;
        this.side = marketTrade.side;
        this.price = marketTrade.price;
        this.quantity = marketTrade.quantity;
        this.tradeType = marketTrade.tradeType;
        this.isRecovery = marketTrade.isRecovery;
        this.numActualTrades = marketTrade.numActualTrades;
        this.triggerInfo.copyFrom(marketTrade.triggerInfo);
    }
    
    @Override
    public void decodeFrom(final MarketDataTradeSbeDecoder decoder) {
        this.secSid(decoder.secSid());
        this.tradeNanoOfDay(decoder.tradeTime());
        this.side(0);
        this.price(decoder.price());
        this.quantity(decoder.quantity());
        this.tradeType(decoder.tradeType());
        this.isRecovery(decoder.isRecovery() == BooleanType.TRUE) ;
        this.numActualTrades(decoder.numActualTrades());
        this.triggerInfo.decode(decoder.triggerInfo());
    }

}
