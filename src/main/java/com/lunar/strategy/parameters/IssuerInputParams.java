package com.lunar.strategy.parameters;

import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.StrategyTriggerType;

public interface IssuerInputParams {
    public interface Validator {
        public boolean validateIssuerMaxLag(final IssuerInputParams params, final long issuerMaxLag);
        public boolean validateAllowStopLossOnFlashingBid(IssuerInputParams params, boolean allowStopLossOnFlashingBid);
        public boolean validateResetStopLossOnVolDown(IssuerInputParams params, boolean resetStopLossOnVolDown);
        public boolean validateIssuerMaxLagCap(IssuerInputParams params, final long issuerLagCap);
        public boolean validateDefaultPricingMode(IssuerInputParams params, final PricingMode pricingMode);
        public boolean validateStrategyTriggerType(IssuerInputParams params, final StrategyTriggerType triggerType);
        public boolean validateSellAtQuickProfit(final IssuerInputParams params, final boolean sellAtQuickProfit);
        public boolean validateRunTicksThreshold(final IssuerInputParams params, final int runTicksThreshold);
        public boolean validateSellToNonIssuer(IssuerInputParams params, final boolean sellToNonIssuer);
        public boolean validateTickBuffer(IssuerInputParams params, final int tickBuffer);
        public boolean validateStopLossTickBuffer(IssuerInputParams params, final int stopLossTickBuffer);
        public boolean validateSellOnVolDown(final IssuerInputParams params, final boolean sellOnVolDown);
        public boolean validateSellOnVolDownBanPeriod(IssuerInputParams params, final long sellOnVolDownBanPeriod); 
        public boolean validateBaseOrderSize(final IssuerInputParams params, final int orderSize);
        public boolean validateOrderSizeIncrement(final IssuerInputParams params, final int increment);
        public boolean validateMaxOrderSize(final IssuerInputParams params, final int maxOrderSize);
        public boolean validateCurrentOrderSize(final IssuerInputParams params, final int currentOrderSize);
        public boolean validateUseHoldBidBan(final IssuerInputParams params, final boolean useHoldBidBan);
        public boolean validateTradesVolumeThreshold(final IssuerInputParams params, final long tradesVolumeThreshold);
        public boolean validateOrderSizeRemainder(final IssuerInputParams params, final long orderSizeRemainder);
    }
    
    public interface PostUpdateHandler {
        public void onUpdatedIssuerMaxLag(IssuerInputParams params);
        public void onUpdatedAllowStopLossOnFlashingBid(IssuerInputParams params);
        public void onUpdatedResetStopLossOnVolDown(IssuerInputParams params);
        public void onUpdatedIssuerMaxLagCap(IssuerInputParams params);
        public void onUpdatedDefaultPricingMode(IssuerInputParams params);
        public void onUpdatedStrategyTriggerType(IssuerInputParams params);
        public void onUpdatedSellAtQuickProfit(IssuerInputParams params);
        public void onUpdatedRunTicksThreshold(IssuerInputParams params);
        public void onUpdatedSellToNonIssuer(IssuerInputParams params);
        public void onUpdatedTickBuffer(IssuerInputParams params);
        public void onUpdatedStopLossTickBuffer(IssuerInputParams params);
        public void onUpdatedSellOnVolDown(final IssuerInputParams params);
        public void onUpdatedSellOnVolDownBanPeriod(IssuerInputParams params);
        public void onUpdatedBaseOrderSize(final IssuerInputParams params);
        public void onUpdatedOrderSizeIncrement(final IssuerInputParams params);
        public void onUpdatedMaxOrderSize(final IssuerInputParams params);
        public void onUpdatedCurrentOrderSize(final IssuerInputParams params);
        public void onUpdatedUseHoldBidBan(final IssuerInputParams params);
        public void onUpdatedTradesVolumeThreshold(final IssuerInputParams params);
        public void onUpdatedOrderSizeRemainder(final IssuerInputParams params);
    }    
    
    public long issuerMaxLag();
    public IssuerInputParams issuerMaxLag(final long issuerMaxLag);
    public IssuerInputParams userIssuerMaxLag(final long issuerMaxLag);
    
    public boolean allowStopLossOnFlashingBid();
    public IssuerInputParams allowStopLossOnFlashingBid(final boolean allowStopLossOnFlashingBid);
    public IssuerInputParams userAllowStopLossOnFlashingBid(final boolean allowStopLossOnFlashingBid);

    public boolean resetStopLossOnVolDown();
    public IssuerInputParams resetStopLossOnVolDown(final boolean resetStopLossOnVolDown);
    public IssuerInputParams userResetStopLossOnVolDown(final boolean resetStopLossOnVolDown);
    
    public long issuerMaxLagCap();
    public IssuerInputParams issuerMaxLagCap(final long issuerMaxLagCap);
    public IssuerInputParams userIssuerMaxLagCap(final long issuerMaxLagCap);
    
    public PricingMode defaultPricingMode();
    public IssuerInputParams defaultPricingMode(final PricingMode pricingMode);
    public IssuerInputParams userDefaultPricingMode(final PricingMode pricingMode);
    
    public StrategyTriggerType strategyTriggerType();
    public IssuerInputParams strategyTriggerType(final StrategyTriggerType triggerType);
    public IssuerInputParams userStrategyTriggerType(final StrategyTriggerType triggerType);
    
    public boolean sellAtQuickProfit();
    public IssuerInputParams sellAtQuickProfit(final boolean sellAtQuickProfit);
    public IssuerInputParams userSellAtQuickProfit(final boolean sellAtQuickProfit);
    
    public int runTicksThreshold();
    public IssuerInputParams runTicksThreshold(final int runTicksThreshold);
    public IssuerInputParams userRunTicksThreshold(final int runTicksThreshold);

    public boolean sellToNonIssuer();
    public IssuerInputParams sellToNonIssuer(final boolean sellToNonIssuer);
    public IssuerInputParams userSellToNonIssuer(final boolean sellToNonIssuer);
    
    public int tickBuffer();
    public IssuerInputParams tickBuffer(final int tickBuffer);
    public IssuerInputParams userTickBuffer(final int tickBuffer);
    
    public int stopLossTickBuffer();
    public IssuerInputParams stopLossTickBuffer(final int stopLossTickBuffer);
    public IssuerInputParams userStopLossTickBuffer(final int stopLossTickBuffer);
    
    public boolean sellOnVolDown();
    public IssuerInputParams sellOnVolDown(final boolean sellOnVolDown);
    public IssuerInputParams userSellOnVolDown(final boolean sellOnVolDown);

    public long sellOnVolDownBanPeriod();
    public IssuerInputParams sellOnVolDownBanPeriod(final long sellOnVolDownBanPeriod);
    public IssuerInputParams userSellOnVolDownBanPeriod(final long sellOnVolDownBanPeriod);
    
    public int baseOrderSize();
    public IssuerInputParams baseOrderSize(final int orderSize);
    public IssuerInputParams userBaseOrderSize(final int orderSize);
    
    public int orderSizeIncrement();
    public IssuerInputParams orderSizeIncrement(final int increment);
    public IssuerInputParams userOrderSizeIncrement(final int increment);

    public int maxOrderSize();
    public IssuerInputParams maxOrderSize(final int maxOrderSize);
    public IssuerInputParams userMaxOrderSize(final int maxOrderSize);

    public int currentOrderSize();    
    public IssuerInputParams currentOrderSize(final int orderSize);
    public IssuerInputParams userCurrentOrderSize(final int orderSize);

    public boolean useHoldBidBan();
    public IssuerInputParams useHoldBidBan(final boolean useHoldBidBan);
    public IssuerInputParams userUseHoldBidBan(final boolean useHoldBidBan);
    
    public long tradesVolumeThreshold();
    public IssuerInputParams tradesVolumeThreshold(final long tradesVolumeThreshold);
    public IssuerInputParams userTradesVolumeThreshold(final long tradesVolumeThreshold);
    
    public int orderSizeRemainder();
    public IssuerInputParams orderSizeRemainder(final int orderSizeRemainder);
    public IssuerInputParams userOrderSizeRemainder(final int orderSizeRemainder);

    default public void copyTo(final IssuerInputParams o) {
        o.issuerMaxLag(this.issuerMaxLag());
        o.allowStopLossOnFlashingBid(this.allowStopLossOnFlashingBid());
        o.resetStopLossOnVolDown(this.resetStopLossOnVolDown());
        o.issuerMaxLagCap(this.issuerMaxLagCap());
        o.strategyTriggerType(this.strategyTriggerType());
        o.defaultPricingMode(this.defaultPricingMode());
        o.sellAtQuickProfit(this.sellAtQuickProfit());
        o.runTicksThreshold(this.runTicksThreshold());
        o.sellToNonIssuer(this.sellToNonIssuer());
        o.tickBuffer(this.tickBuffer());
        o.stopLossTickBuffer(this.stopLossTickBuffer());
        o.sellOnVolDown(this.sellOnVolDown());     
        o.sellOnVolDownBanPeriod(this.sellOnVolDownBanPeriod());
        o.baseOrderSize(this.baseOrderSize());
        o.orderSizeIncrement(this.orderSizeIncrement());
        o.maxOrderSize(this.maxOrderSize());
        o.currentOrderSize(this.currentOrderSize());
        o.useHoldBidBan(this.useHoldBidBan());
        o.tradesVolumeThreshold(this.tradesVolumeThreshold());
        o.orderSizeRemainder(this.orderSizeRemainder());
    }

}
