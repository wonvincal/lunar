package com.lunar.strategy.parameters;

import com.lunar.message.io.sbe.MarketOutlookType;

public interface WrtInputParams extends IssuerInputParams {
    public interface Validator extends GenericIssuerParams.Validator {
        boolean validateMmBidSize(final WrtInputParams params, final int mmBidSize);
        boolean validateMmAskSize(final WrtInputParams params, final int mmAskSize);
        boolean validateOrderSizeMultiplier(WrtInputParams params, final int tickBufferTrigger);
        boolean validateTickSensitivityThreshold(final WrtInputParams params, final int runTicksThreshold);
        boolean validateStopLoss(final WrtInputParams params, final long stopLoss);
        boolean validateStopLossTrigger(final WrtInputParams params, final long stopLossTrigger);
        boolean validateStopProfit(final WrtInputParams params, final long stopProfit);
        boolean validateAllowedMaxSpread(final WrtInputParams params, final int allowedMaxSpread);
        boolean validateTurnoverMakingSize(final WrtInputParams params, final int turnoverMakingSize);
        boolean validateTurnoverMakingPeriod(final WrtInputParams params, final long turnoverMakingPeriod);
        boolean validateBanPeriodToDownVol(final WrtInputParams params, final long banPeriodToDownVol);
        boolean validateBanPeriodToTurnoverMaking(final WrtInputParams params, final long banPeriodToTurnoverMaking);
        boolean validateSellingBanPeriod(final WrtInputParams params, final long sellingBanPeriod);
        boolean validateHoldingPeriod(final WrtInputParams params, final long holdingPeriod);
        boolean validateSpreadObservationPeriod(final WrtInputParams params, final long spreadObservationPeriod);
        boolean validateMarketOutlook(final WrtInputParams params, final MarketOutlookType marketOutlook);
        boolean validateManualOrderTicksFromEnterPrice(final WrtInputParams params, final int manualOrderTicksFromBid);
        boolean validateWideSpreadBuffer(final WrtInputParams params, final int wideSpreadBuffer);
        boolean validateAllowAdditionalBuy(final WrtInputParams params, final boolean allowAdditionalBuy);
        boolean validateAllowStopLossOnWideSpread(final WrtInputParams params, final boolean allowStopLossWideSpread);
        boolean validateDoNotSell(final WrtInputParams params, final boolean doNotSell);
        boolean validateSellAtBreakEvenOnly(final WrtInputParams params, final boolean sellAtBreakEvenOnly);
        boolean validateSafeBidLevelBuffer(final WrtInputParams params, final int safeBidLevelBuffer);
        boolean validateIgnoreMmSizeOnSell(final WrtInputParams params, final boolean ignoreMmSizeOnSell);
    }

    public interface PostUpdateHandler extends GenericIssuerParams.PostUpdateHandler {
        void onUpdatedMmBidSize(final WrtInputParams params);
        void onUpdatedMmAskSize(final WrtInputParams params);
        void onUpdatedOrderSizeMultiplier(WrtInputParams params);
        void onUpdatedTickSensitivityThreshold(final WrtInputParams params);
        void onUpdatedStopLoss(final WrtInputParams params);
        void onUpdatedStopLossTrigger(final WrtInputParams params);
        void onUpdatedStopProfit(final WrtInputParams params);
        void onUpdatedAllowedMaxSpread(final WrtInputParams params);        
        void onUpdatedTurnoverMakingSize(final WrtInputParams params);
        void onUpdatedTurnoverMakingPeriod(final WrtInputParams params);
        void onUpdatedBanPeriodToDownVol(final WrtInputParams params);
        void onUpdatedBanPeriodToTurnoverMaking(final WrtInputParams params);
        void onUpdatedSellingBanPeriod(final WrtInputParams params);
        void onUpdatedHoldingPeriod(final WrtInputParams params);        
        void onUpdatedSpreadObservationPeriod(final WrtInputParams params);
        void onUpdatedMarketOutlook(final WrtInputParams params);
        void onUpdatedManualOrderTicksFromEnterPrice(final WrtInputParams params);
        void onUpdatedWideSpreadBuffer(final WrtInputParams params);
        void onUpdatedAllowAdditionalBuy(final WrtInputParams params);
        void onUpdatedAllowStopLossOnWideSpread(final WrtInputParams params);
        void onUpdatedDoNotSell(final WrtInputParams params);
        void onUpdatedSellAtBreakEvenOnly(final WrtInputParams params);
        void onUpdatedSafeBidLevelBuffer(final WrtInputParams params);
        void onUpdatedIgnoreMmSizeOnSell(final WrtInputParams params);
    }
    
    public int mmBidSize();
    public WrtInputParams mmBidSize(final int mmBidSize);
    public WrtInputParams userMmBidSize(final int mmBidSize);

    public int mmAskSize();
    public WrtInputParams mmAskSize(final int mmAskSize);
    public WrtInputParams userMmAskSize(final int mmAskSize);

    public int orderSizeMultiplier();    
    public WrtInputParams orderSizeMultiplier(final int multiplier);
    public WrtInputParams userOrderSizeMultiplier(final int multiplier);
    
    public int tickSensitivityThreshold();
    public WrtInputParams tickSensitivityThreshold(final int tickSensitivityThreshold);
    public WrtInputParams userTickSensitivityThreshold(final int tickSensitivityThreshold);

    public long stopLoss();
    public WrtInputParams stopLoss(final long stopLoss);
    public WrtInputParams userStopLoss(final long stopLoss);

    public long stopLossTrigger();
    public WrtInputParams stopLossTrigger(final long stopLossTrigger);
    public WrtInputParams userStopLossTrigger(final long stopLossTrigger);

    public long stopProfit();
    public WrtInputParams stopProfit(final long stopProfit);
    public WrtInputParams userStopProfit(final long stopProfit);

    public int allowedMaxSpread();
    public WrtInputParams allowedMaxSpread(final int allowedMaxSpread);
    public WrtInputParams userAllowedMaxSpread(final int allowedMaxSpread);

    public int turnoverMakingSize();
    public WrtInputParams turnoverMakingSize(final int turnoverMakingSize);
    public WrtInputParams userTurnoverMakingSize(final int turnoverMakingSize);

    public long turnoverMakingPeriod();
    public WrtInputParams turnoverMakingPeriod(final long turnoverMakingPeriod);
    public WrtInputParams userTurnoverMakingPeriod(final long turnoverMakingPeriod);

    public long banPeriodToDownVol();
    public WrtInputParams banPeriodToDownVol(final long banPeriodToDownVol);
    public WrtInputParams userBanPeriodToDownVol(final long banPeriodToDownVol);

    public long banPeriodToTurnoverMaking();
    public WrtInputParams banPeriodToTurnoverMaking(final long banPeriodToTurnoverMaking);
    public WrtInputParams userBanPeriodToTurnoverMaking(final long banPeriodToTurnoverMaking);

    public long sellingBanPeriod();
    public WrtInputParams sellingBanPeriod(final long sellingBanPeriod);
    public WrtInputParams userSellingBanPeriod(final long sellingBanPeriod);

    public long holdingPeriod();
    public WrtInputParams holdingPeriod(final long holdingPeriod);
    public WrtInputParams userHoldingPeriod(final long holdingPeriod);

    public long spreadObservationPeriod();
    public WrtInputParams spreadObservationPeriod(final long observationPeriod);
    public WrtInputParams userSpreadObservationPeriod(final long observationPeriod);

    public MarketOutlookType marketOutlook();
    public WrtInputParams marketOutlook(final MarketOutlookType marketOutlook);
    public WrtInputParams userMarketOutlook(final MarketOutlookType marketOutlook);

    public int manualOrderTicksFromEnterPrice();
    public WrtInputParams manualOrderTicksFromEnterPrice(final int manualOrderTicksFromBid);
    public WrtInputParams userManualOrderTicksFromEnterPrice(final int manualOrderTicksFromBid);
    
    public int wideSpreadBuffer();
    public WrtInputParams wideSpreadBuffer(final int wideSpreadBuffer);
    public WrtInputParams userWideSpreadBuffer(final int wideSpreadBuffer);
    
    public boolean allowAdditionalBuy();
    public WrtInputParams allowAdditionalBuy(final boolean allowAdditionalBuy);
    public WrtInputParams userAllowAdditionalBuy(final boolean allowAdditionalBuy);

    public boolean allowStopLossOnWideSpread();
    public WrtInputParams allowStopLossOnWideSpread(final boolean allowStopLossOnWideSpread);
    public WrtInputParams userAllowStopLossOnWideSpread(final boolean allowStopLossOnWideSpread);
    
    public boolean doNotSell();
    public WrtInputParams doNotSell(final boolean doNotSell);
    public WrtInputParams userDoNotSell(final boolean doNotSell);
    
    public boolean sellAtBreakEvenOnly();
    public WrtInputParams sellAtBreakEvenOnly(final boolean sellAtBreakEvenOnly);
    public WrtInputParams userSellAtBreakEvenOnly(final boolean sellAtBreakEvenOnly);
    
    public int safeBidLevelBuffer();
    public WrtInputParams safeBidLevelBuffer(final int safeBidLevelBuffer);
    public WrtInputParams userSafeBidLevelBuffer(final int safeBidLevelBuffer);
    
    public boolean ignoreMmSizeOnSell();
    public WrtInputParams ignoreMmSizeOnSell(final boolean ignoreMmSizeOnSell);
    public WrtInputParams userIgnoreMmSizeOnSell(final boolean ignoreMmSizeOnSell);
    
    
    default public void copyTo(final WrtInputParams o) {
        copyTo((IssuerInputParams)o);
        o.mmBidSize(this.mmBidSize());
        o.mmAskSize(this.mmAskSize());
        o.orderSizeMultiplier(this.orderSizeMultiplier());    
        o.tickSensitivityThreshold(this.tickSensitivityThreshold());
        o.stopLoss(this.stopLoss());
        o.stopLossTrigger(this.stopLossTrigger());
        o.stopProfit(this.stopProfit());
        o.allowedMaxSpread(this.allowedMaxSpread());
        o.turnoverMakingSize(this.turnoverMakingSize());
        o.turnoverMakingPeriod(this.turnoverMakingPeriod());
        o.banPeriodToDownVol(this.banPeriodToDownVol());
        o.banPeriodToTurnoverMaking(this.banPeriodToTurnoverMaking());
        o.sellingBanPeriod(this.sellingBanPeriod());
        o.holdingPeriod(this.holdingPeriod());
        o.spreadObservationPeriod(this.spreadObservationPeriod());
        o.marketOutlook(this.marketOutlook());
        o.manualOrderTicksFromEnterPrice(this.manualOrderTicksFromEnterPrice());
        o.wideSpreadBuffer(this.wideSpreadBuffer());
        o.allowAdditionalBuy(this.allowAdditionalBuy());
        o.allowStopLossOnWideSpread(this.allowStopLossOnWideSpread());
        o.doNotSell(this.doNotSell());
        o.sellAtBreakEvenOnly(this.sellAtBreakEvenOnly());
        o.safeBidLevelBuffer(this.safeBidLevelBuffer());
        o.ignoreMmSizeOnSell(this.ignoreMmSizeOnSell());
    }
}
