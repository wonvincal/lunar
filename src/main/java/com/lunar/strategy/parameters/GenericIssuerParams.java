package com.lunar.strategy.parameters;

import org.agrona.MutableDirectBuffer;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

public class GenericIssuerParams extends ParamsSbeEncodable implements IssuerInputParams, IssuerOutputParams {
    private static Validator NULL_VALIDATOR = new IssuerInputParams.Validator() {
        @Override
        public boolean validateIssuerMaxLag(IssuerInputParams params, long issuerMaxLag) {
            return true;
        }

        @Override
        public boolean validateAllowStopLossOnFlashingBid(IssuerInputParams params, boolean allowStopLossOnFlashingBid) {
            return true;
        }

        @Override
        public boolean validateResetStopLossOnVolDown(IssuerInputParams params, boolean resetStopLossOnVolDown) {
            return true;
        }

        @Override
        public boolean validateIssuerMaxLagCap(IssuerInputParams params, long issuerLagCap) {
            return true;
        }

        @Override
        public boolean validateDefaultPricingMode(IssuerInputParams params, PricingMode pricingMode) {
            return true;
        }

        @Override
        public boolean validateStrategyTriggerType(IssuerInputParams params, StrategyTriggerType triggerType) {
            return true;
        }

        @Override
        public boolean validateRunTicksThreshold(IssuerInputParams params, int runTicksThreshold) {
            return true;
        }

        @Override
        public boolean validateSellToNonIssuer(IssuerInputParams params, boolean sellToNonIssuer) {
            return true;
        }

        @Override
        public boolean validateTickBuffer(IssuerInputParams params, int tickBufferTrigger) {
            return true;
        }

        @Override
        public boolean validateSellAtQuickProfit(IssuerInputParams params, boolean sellAtQuickProfit) {
            return true;
        }

        @Override
        public boolean validateStopLossTickBuffer(IssuerInputParams params, int stopLossTickBuffer) {
            return true;
        }

        @Override
        public boolean validateSellOnVolDown(IssuerInputParams params, boolean sellOnVolDown) {
            return true;
        }

        @Override
        public boolean validateSellOnVolDownBanPeriod(IssuerInputParams params, long sellOnVolDownBanPeriod) {
            return true;
        }

        @Override
        public boolean validateBaseOrderSize(IssuerInputParams params, int orderSize) {
            return true;
        }

        @Override
        public boolean validateUseHoldBidBan(IssuerInputParams params, boolean useHoldBidBan) {
            return true;
        }

        @Override
        public boolean validateTradesVolumeThreshold(IssuerInputParams params, long tradesVolumeThreshold) {
            return true;
        }

        @Override
        public boolean validateOrderSizeIncrement(IssuerInputParams params, int increment) {
            return true;
        }

        @Override
        public boolean validateMaxOrderSize(IssuerInputParams params, int maxOrderSize) {
            return true;
        }

        @Override
        public boolean validateCurrentOrderSize(IssuerInputParams params, int currentOrderSize) {
            return true;
        }

        @Override
        public boolean validateOrderSizeRemainder(IssuerInputParams params, long orderSizeRemainder) {
            return true;
        }

    };
    
    private static PostUpdateHandler NULL_POST_UPDATE_HANDLER = new IssuerInputParams.PostUpdateHandler() {
        @Override
        public void onUpdatedIssuerMaxLag(IssuerInputParams params) {
        }
        
        @Override
        public void onUpdatedAllowStopLossOnFlashingBid(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedResetStopLossOnVolDown(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedIssuerMaxLagCap(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedDefaultPricingMode(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedStrategyTriggerType(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedRunTicksThreshold(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedSellToNonIssuer(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedTickBuffer(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedSellAtQuickProfit(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedStopLossTickBuffer(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedSellOnVolDown(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedSellOnVolDownBanPeriod(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedBaseOrderSize(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedUseHoldBidBan(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedTradesVolumeThreshold(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedOrderSizeIncrement(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedMaxOrderSize(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedCurrentOrderSize(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedOrderSizeRemainder(IssuerInputParams params) {
        }

    };

    private Validator validator = NULL_VALIDATOR;
    private PostUpdateHandler postUpdateHandler = NULL_POST_UPDATE_HANDLER;    
    
    private long strategyId;
    private long issuerSid;
    private int numActiveWarrants;
    private int numTotalWarrants;
    
    private long issuerMaxLagCap;
    private long issuerMaxLag;
    private boolean allowStopLossOnFlashingBid;
    private boolean resetStopLossOnVolDown;
    private StrategyTriggerType strategyTriggerType = StrategyTriggerType.NULL_VAL;
    private PricingMode defaultPricingMode = PricingMode.NULL_VAL;
    private int runTicksThreshold;
    private boolean sellToNonIssuer;
    private int tickBuffer;
    private boolean sellAtQuickProfit;
    private int stopLossTickBuffer;
    private boolean sellOnVolDown;
    private long sellOnVolDownBanPeriod;
    private int baseOrderSize;
    private int orderSizeIncrement;
    private int maxOrderSize;
    private int currentOrderSize;
    private boolean useHoldBidBan;
    private long tradesVolumeThreshold;
    private int orderSizeRemainder;

    public void setValidator(final Validator validator) {
        this.validator = validator;
    }
    public void setPostUpdateHandler(final PostUpdateHandler handler) {
        this.postUpdateHandler = handler;
    }
    
    public long strategyId() {
        return strategyId;
    }
    public GenericIssuerParams strategyId(final long strategyId) {
        this.strategyId = strategyId;
        return this;
    }
    
    public long issuerSid() {
        return issuerSid;
    }
    public GenericIssuerParams issuerSid(final long issuerSid) {
        this.issuerSid = issuerSid;
        return this;
    }    
    
    @Override
    public int numActiveWarrants() {
        return numActiveWarrants;
    }
    @Override
    public GenericIssuerParams numActiveWarrants(final int numActiveWarrants) {
        this.numActiveWarrants = numActiveWarrants;
        return this;
    }
    @Override
    public void incActiveWarrants() {
        numActiveWarrants++;
    }
    @Override
    public void decActiveWarrants() {
        numActiveWarrants--;
    }    
    
    @Override
    public int numTotalWarrants() {
        return numTotalWarrants;
    }
    @Override
    public GenericIssuerParams numTotalWarrants(final int numTotalWarrants) {
        this.numTotalWarrants = numTotalWarrants;
        return this;
    }
    @Override
    public void incTotalWarrants() {
        numTotalWarrants++;
    }
    
    @Override
    public long issuerMaxLag() {
        return issuerMaxLag;
    }
    @Override
    public GenericIssuerParams issuerMaxLag(final long issuerMaxLag) {
        this.issuerMaxLag = issuerMaxLag;
        return this;
    }    
    @Override
    public GenericIssuerParams userIssuerMaxLag(final long issuerMaxLag) {
        if (validator.validateIssuerMaxLag(this, issuerMaxLag)) {
            issuerMaxLag(issuerMaxLag);
            postUpdateHandler.onUpdatedIssuerMaxLag(this);
        }
        return this;
    }
    
    @Override
    public boolean allowStopLossOnFlashingBid() {
        return allowStopLossOnFlashingBid;
    }
    @Override
    public GenericIssuerParams allowStopLossOnFlashingBid(final boolean allowStopLossOnFlashingBid) {
        this.allowStopLossOnFlashingBid = allowStopLossOnFlashingBid;
        return this;
    }    
    @Override
    public GenericIssuerParams userAllowStopLossOnFlashingBid(final boolean allowStopLossOnFlashingBid) {
        if (validator.validateAllowStopLossOnFlashingBid(this, allowStopLossOnFlashingBid)) {
            allowStopLossOnFlashingBid(allowStopLossOnFlashingBid);
            postUpdateHandler.onUpdatedAllowStopLossOnFlashingBid(this);
        }
        return this;
    }

    @Override
    public boolean resetStopLossOnVolDown() {
        return resetStopLossOnVolDown;
    }
    @Override
    public GenericIssuerParams resetStopLossOnVolDown(final boolean resetStopLossOnVolDown) {
        this.resetStopLossOnVolDown = resetStopLossOnVolDown;
        return this;
    }
    @Override
    public GenericIssuerParams userResetStopLossOnVolDown(final boolean resetStopLossOnVolDown) {
        if (validator.validateResetStopLossOnVolDown(this, resetStopLossOnVolDown)) {
            resetStopLossOnVolDown(resetStopLossOnVolDown);
            postUpdateHandler.onUpdatedResetStopLossOnVolDown(this);
        }
        return this;
    }
    
    @Override
    public PricingMode defaultPricingMode() {
        return defaultPricingMode;
    }
    @Override
    public GenericIssuerParams defaultPricingMode(final PricingMode pricingMode) {
        this.defaultPricingMode = pricingMode;
        return this;
    }
    @Override
    public GenericIssuerParams userDefaultPricingMode(final PricingMode pricingMode) {
        if (validator.validateDefaultPricingMode(this, pricingMode)) {
            defaultPricingMode(pricingMode);
            postUpdateHandler.onUpdatedDefaultPricingMode(this);
        }
        return this;
    }
    
    @Override
    public StrategyTriggerType strategyTriggerType() {
        return strategyTriggerType;
    }
    @Override
    public GenericIssuerParams strategyTriggerType(final StrategyTriggerType triggerType) {
        this.strategyTriggerType = triggerType;
        return this;
    }
    @Override
    public GenericIssuerParams userStrategyTriggerType(final StrategyTriggerType triggerType) {
        if (validator.validateStrategyTriggerType(this, triggerType)) {
            strategyTriggerType(triggerType);
            postUpdateHandler.onUpdatedStrategyTriggerType(this);
        }
        return this;
    }
    
    @Override
    public long issuerMaxLagCap() {
        return issuerMaxLagCap;
    }
    @Override
    public GenericIssuerParams issuerMaxLagCap(final long issuerMaxLagCap) {
        this.issuerMaxLagCap = issuerMaxLagCap;
        return this;
    }
    @Override
    public GenericIssuerParams userIssuerMaxLagCap(final long issuerMaxLagCap) {
        if (validator.validateIssuerMaxLagCap(this, issuerMaxLagCap)) {
            issuerMaxLagCap(issuerMaxLagCap);
            postUpdateHandler.onUpdatedIssuerMaxLagCap(this);
        }
        return this;
    }
    
    @Override
    public int runTicksThreshold() {
        return runTicksThreshold;
    }
    @Override
    public GenericIssuerParams runTicksThreshold(final int runTicksThreshold) {
        this.runTicksThreshold = runTicksThreshold;
        return this;
    }
    @Override
    public GenericIssuerParams userRunTicksThreshold(final int runTicksThreshold) {
        if (validator.validateRunTicksThreshold(this, runTicksThreshold)) {
            runTicksThreshold(runTicksThreshold);
            postUpdateHandler.onUpdatedRunTicksThreshold(this);
        }        
        return this;
    }

    @Override
    public boolean sellToNonIssuer() {
        return this.sellToNonIssuer;
    }
    @Override
    public GenericIssuerParams sellToNonIssuer(final boolean sellToNonIssuer) {
        this.sellToNonIssuer = sellToNonIssuer;
        return this;
    }
    @Override
    public GenericIssuerParams userSellToNonIssuer(final boolean sellToNonIssuer) {
        if (validator.validateSellToNonIssuer(this, sellToNonIssuer)) {
            sellToNonIssuer(sellToNonIssuer);
            postUpdateHandler.onUpdatedSellToNonIssuer(this);
        }
        return this;
    }
    
    @Override
    public int tickBuffer() {
        return this.tickBuffer;
    }
    @Override
    public GenericIssuerParams tickBuffer(final int tickBuffer) {
        this.tickBuffer = tickBuffer;
        return this;
    }
    @Override
    public GenericIssuerParams userTickBuffer(final int tickBuffer) {
        if (validator.validateTickBuffer(this, tickBuffer)) {
            tickBuffer(tickBuffer);
            postUpdateHandler.onUpdatedTickBuffer(this);
        }
        return this;
    }
    
    @Override
    public boolean sellAtQuickProfit() {
        return sellAtQuickProfit;
    }
    @Override
    public GenericIssuerParams sellAtQuickProfit(final boolean sellAtQuickProfit) {
        this.sellAtQuickProfit = sellAtQuickProfit;
        return this;
    }
    @Override
    public GenericIssuerParams userSellAtQuickProfit(final boolean sellAtQuickProfit) {
        if (validator.validateSellAtQuickProfit(this, sellAtQuickProfit)) {
            sellAtQuickProfit(sellAtQuickProfit);
            postUpdateHandler.onUpdatedSellAtQuickProfit(this);
        }
        return this;
    }
    
    @Override
    public int stopLossTickBuffer() {
        return stopLossTickBuffer;
    }
    @Override
    public GenericIssuerParams stopLossTickBuffer(final int stopLossTickBuffer) {
        this.stopLossTickBuffer = stopLossTickBuffer;
        return this;
    }
    @Override
    public GenericIssuerParams userStopLossTickBuffer(final int stopLossTickBuffer) {
        if (validator.validateStopLossTickBuffer(this, stopLossTickBuffer)) {
            stopLossTickBuffer(stopLossTickBuffer);
            postUpdateHandler.onUpdatedStopLossTickBuffer(this);
        }
        return this;
    }
    
    @Override
    public boolean sellOnVolDown() {
        return this.sellOnVolDown;
    }
    @Override
    public GenericIssuerParams sellOnVolDown(final boolean sellOnVolDown) {
        this.sellOnVolDown = sellOnVolDown;
        return this;
    }
    @Override
    public GenericIssuerParams userSellOnVolDown(final boolean sellOnVolDown) {
        if (validator.validateSellOnVolDown(this, sellOnVolDown)) {
            sellOnVolDown(sellOnVolDown);
            postUpdateHandler.onUpdatedSellOnVolDown(this);
        }
        return this;
    }

    @Override
    public long sellOnVolDownBanPeriod() {
        return sellOnVolDownBanPeriod;
    }
    @Override
    public GenericIssuerParams sellOnVolDownBanPeriod(final long sellOnVolDownBanPeriod) {
        this.sellOnVolDownBanPeriod = sellOnVolDownBanPeriod;
        return this;
    }
    @Override
    public GenericIssuerParams userSellOnVolDownBanPeriod(final long sellOnVolDownBanPeriod) {
        if (validator.validateSellOnVolDownBanPeriod(this, sellOnVolDownBanPeriod)) {
            sellOnVolDownBanPeriod(sellOnVolDownBanPeriod);
            postUpdateHandler.onUpdatedSellOnVolDownBanPeriod(this);
        }        
        return this;
    }    
    
    @Override
    public int baseOrderSize() {
        return baseOrderSize;
    }
    @Override
    public GenericIssuerParams baseOrderSize(final int orderSize) {
        this.baseOrderSize = orderSize;
        return this;
    }
    @Override
    public GenericIssuerParams userBaseOrderSize(final int orderSize) {
        if (validator.validateBaseOrderSize(this, orderSize)) {
            baseOrderSize(orderSize);
            postUpdateHandler.onUpdatedBaseOrderSize(this);
        }
        return this;
    }

    @Override
    public int orderSizeIncrement() {
        return orderSizeIncrement;
    }
    @Override
    public GenericIssuerParams orderSizeIncrement(final int orderSizeIncrement) {
        this.orderSizeIncrement = orderSizeIncrement;
        return this;
    }
    @Override
    public GenericIssuerParams userOrderSizeIncrement(final int orderSizeIncrement) {
        if (validator.validateOrderSizeIncrement(this, orderSizeIncrement)) {
            orderSizeIncrement(orderSizeIncrement);
            postUpdateHandler.onUpdatedOrderSizeIncrement(this);
        }
        return this;
    }
    
    @Override
    public int maxOrderSize() {
        return maxOrderSize;
    }
    @Override
    public GenericIssuerParams maxOrderSize(final int maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
        return this;
    }
    @Override
    public GenericIssuerParams userMaxOrderSize(final int maxOrderSize) {
        if (validator.validateMaxOrderSize(this, maxOrderSize)) {
            maxOrderSize(maxOrderSize);
            postUpdateHandler.onUpdatedMaxOrderSize(this);
        }
        return this;
    }
    
    @Override
    public int currentOrderSize() {
        return currentOrderSize;
    }
    @Override
    public GenericIssuerParams currentOrderSize(final int currentOrderSize) {
        this.currentOrderSize = currentOrderSize;
        return this;
    }
    @Override
    public GenericIssuerParams userCurrentOrderSize(final int currentOrderSize) {
        if (validator.validateCurrentOrderSize(this, currentOrderSize)) {
            currentOrderSize(currentOrderSize);
            postUpdateHandler.onUpdatedCurrentOrderSize(this);
        }
        return this;
    }
    
    @Override
    public boolean useHoldBidBan() {
        return useHoldBidBan;
    }
    @Override
    public GenericIssuerParams useHoldBidBan(final boolean useHoldBidBan) {
        this.useHoldBidBan = useHoldBidBan;
        return this;
    }
    @Override
    public GenericIssuerParams userUseHoldBidBan(final boolean useHoldBidBan) {
        if (validator.validateUseHoldBidBan(this, useHoldBidBan)) {
            useHoldBidBan(useHoldBidBan);
            postUpdateHandler.onUpdatedUseHoldBidBan(this);
        }
        return this;
    }
    
    @Override
    public long tradesVolumeThreshold() {
        return tradesVolumeThreshold;
    }
    @Override
    public GenericIssuerParams tradesVolumeThreshold(final long tradesVolumeThreshold) {
        this.tradesVolumeThreshold = tradesVolumeThreshold;
        return this;
    }
    @Override
    public GenericIssuerParams userTradesVolumeThreshold(final long tradesVolumeThreshold) {
        if (validator.validateTradesVolumeThreshold(this, tradesVolumeThreshold)) {
            tradesVolumeThreshold(tradesVolumeThreshold);
            postUpdateHandler.onUpdatedTradesVolumeThreshold(this);
        }
        return this;
    }
    
    @Override
    public int orderSizeRemainder() {
        return orderSizeRemainder;
    }
    @Override
    public GenericIssuerParams orderSizeRemainder(final int orderSizeRemainder) {
        this.orderSizeRemainder = orderSizeRemainder;
        return this;
    }
    @Override
    public GenericIssuerParams userOrderSizeRemainder(final int orderSizeRemainder) {
        if (validator.validateOrderSizeRemainder(this, orderSizeRemainder)) {
            orderSizeRemainder(orderSizeRemainder);
            postUpdateHandler.onUpdatedOrderSizeRemainder(this);
        }
        return this;
    }

    @Override
    public GenericIssuerParams clone() {
        final GenericIssuerParams clone = new GenericIssuerParams();
        copyTo((ParamsSbeEncodable)clone);
        return clone;
    }
    
    @Override
    public void copyTo(final ParamsSbeEncodable other) {
        if (other instanceof GenericIssuerParams) {
            final GenericIssuerParams o = (GenericIssuerParams)other;
            o.strategyId = this.strategyId;
            o.issuerSid = this.issuerSid;
            copyTo((IssuerInputParams)o);
            copyTo((IssuerOutputParams)o);
        }
    }
    
    @Override
    public TemplateType templateType() {
        return TemplateType.STRATISSUERPARAMUPDATE;
    }
    @Override
    public short blockLength() {
        return StrategyIssuerParamsSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeIssuerParamsWithoutHeader(buffer, offset, encoder.strategyIssuerParamsSbeEncoder(), this);
    }

    @Override
    public int expectedEncodedLength() {
        return StrategySender.expectedEncodedLength(this);
    }
    @Override
    public int schemaId() {
        return StrategyIssuerParamsSbeEncoder.SCHEMA_ID;
    }
    @Override
    public int schemaVersion() {
        return StrategyIssuerParamsSbeEncoder.SCHEMA_VERSION;
    }

}
