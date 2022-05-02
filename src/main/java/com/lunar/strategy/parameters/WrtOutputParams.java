package com.lunar.strategy.parameters;

import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.SpreadState;
import com.lunar.message.io.sbe.StrategyStatusType;
import com.lunar.pricing.Greeks;

public interface WrtOutputParams {
    public StrategyStatusType status();
    public GenericWrtParams status(final StrategyStatusType status);
    
    public long stopLoss();
    public WrtOutputParams stopLoss(final long stopLoss);
    
    public long stopLossAdjustment();
    public WrtOutputParams stopLossAdjustment(final long stopLossAdjustment);
    
    public long stopLossTrigger();
    public WrtOutputParams stopLossTrigger(final long stopLossTrigger);

    public long issuerMaxLag();
    public WrtOutputParams issuerMaxLag(final long issuerMaxLag);
    
    public int tickSensitivity();
    public WrtOutputParams tickSensitivity(final int tickSensitivity);

    public int warrantSpread();
    public WrtOutputParams warrantSpread(final int warrantSpread);

    public PricingMode pricingMode();
    public WrtOutputParams pricingMode(final PricingMode pricingMode);

    public Greeks greeks();
    public WrtOutputParams greeks(final Greeks greeks);

    public int enterMMSpread();
    public WrtOutputParams enterMMSpread(final int enterSpread);

    public int enterPrice();
    public WrtOutputParams enterPrice(final int enterPrice);

    public int enterLevel();
    public WrtOutputParams enterLevel(final int enterLevel);
    
    public int enterQuantity();
    public WrtOutputParams enterQuantity(final int enterQuantity);
    
    public int exitLevel();
    public WrtOutputParams exitLevel(final int exitLevel);

    public int profitRun();
    public WrtOutputParams profitRun(final int profitRun);
    public WrtOutputParams incrProfitRun(final int inc);

    public int enterMMBidPrice();
    public WrtOutputParams enterMMBidPrice(final int enterBidPrice);

    public int enterBidLevel();
    public WrtOutputParams enterBidLevel(final int enterBidLevel);

    public long enterSpotPrice();
    public WrtOutputParams enterSpotPrice(final long enterSpotPrice);

    public long issuerLag();
    public WrtOutputParams issuerLag(final long issuerLag);

    public long issuerSmoothing();
    public WrtOutputParams issuerSmoothing(final long issuerSmoothing);
    
    public int numSpreadResets();
    public WrtOutputParams numSpreadResets(final int numSpreadResets);
    public WrtOutputParams incNumSpreadResets();

    public int numWAvgDownVols();
    public WrtOutputParams numWAvgDownVols(final int numDownVols);
    public WrtOutputParams incNumWAvgDownVols();

    public int numWAvgUpVols();
    public WrtOutputParams numWAvgUpVols(final int numUpVols);
    public WrtOutputParams incNumWAvgUpVols();

    public int numMPrcDownVols();
    public WrtOutputParams numMPrcDownVols(final int numDownVols);
    public WrtOutputParams incNumMPrcDownVols();

    public int numMPrcUpVols();
    public WrtOutputParams numMPrcUpVols(final int numUpVols);
    public WrtOutputParams incNumMPrcUpVols();
    
    public SpreadState spreadState();
    public WrtOutputParams spreadState(final SpreadState spreadState);
    
    public boolean canSellOnWide();
    public WrtOutputParams canSellOnWide(final boolean canSellOnWide);
    
    public boolean allowStopLossOnWideSpread();
    public WrtOutputParams allowStopLossOnWideSpread(final boolean allowStopLossOnWideSpread);

    public boolean doNotSell();
    public WrtInputParams doNotSell(final boolean doNotSell);

    public boolean sellAtBreakEvenOnly();
    public WrtInputParams sellAtBreakEvenOnly(final boolean sellAtBreakEvenOnly);

    public boolean ignoreMmSizeOnSell();
    public WrtInputParams ignoreMmSizeOnSell(final boolean ignoreMmSizeOnSell);
    
    public int orderSize();
    public WrtOutputParams orderSize(final int orderSize);

    public int safeBidLevelBuffer();
    public WrtInputParams safeBidLevelBuffer(final int safeBidLevelBuffer);

    public int safeBidPrice();
    public WrtOutputParams safeBidPrice(final int safeBidPrice);
    
    default public void copyTo(final WrtOutputParams o) {
        o.status(this.status());
        o.stopLoss(this.stopLoss());
        o.stopLossAdjustment(this.stopLossAdjustment());
        o.stopLossTrigger(this.stopLossTrigger());
        o.issuerMaxLag(this.issuerMaxLag());
        o.tickSensitivity(this.tickSensitivity());
        o.warrantSpread(this.warrantSpread());
        o.pricingMode(this.pricingMode());
        o.greeks().copyFrom(this.greeks());
        o.enterMMSpread(this.enterMMSpread());
        o.enterPrice(this.enterPrice());
        o.enterLevel(this.enterLevel());
        o.enterQuantity(this.enterQuantity());
        o.exitLevel(this.exitLevel());
        o.profitRun(this.profitRun());
        o.enterMMBidPrice(this.enterMMBidPrice());
        o.enterSpotPrice(this.enterSpotPrice());
        o.issuerLag(this.issuerLag());
        o.issuerSmoothing(this.issuerSmoothing());
        o.numSpreadResets(this.numSpreadResets());
        o.numWAvgDownVols(this.numWAvgDownVols());
        o.numWAvgUpVols(this.numWAvgUpVols());
        o.numMPrcDownVols(this.numMPrcDownVols());
        o.numMPrcUpVols(this.numMPrcUpVols());
        o.spreadState(this.spreadState());
        o.canSellOnWide(this.canSellOnWide());
        o.allowStopLossOnWideSpread(this.allowStopLossOnWideSpread());
        o.ignoreMmSizeOnSell(this.ignoreMmSizeOnSell());
        o.orderSize(this.orderSize());
        o.safeBidLevelBuffer(o.safeBidLevelBuffer());
        o.safeBidPrice(this.safeBidPrice());
        o.doNotSell(this.doNotSell());
        o.sellAtBreakEvenOnly(this.sellAtBreakEvenOnly());
    }
}
