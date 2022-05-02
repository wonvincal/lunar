package com.lunar.strategy.parameters;

import org.agrona.MutableDirectBuffer;

import com.lunar.entity.Security;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.SpreadState;
import com.lunar.message.io.sbe.StrategyStatusType;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;
import com.lunar.pricing.Greeks;
import com.lunar.service.ServiceConstant;

public class GenericWrtParams extends ParamsSbeEncodable implements WrtInputParams, WrtOutputParams {
    static private Validator NULL_VALIDATOR = new WrtInputParams.Validator() {
        @Override
        public boolean validateMmBidSize(WrtInputParams params, int mmBidSize) {
            return true;
        }

        @Override
        public boolean validateMmAskSize(WrtInputParams params, int mmAskSize) {
            return true;
        }

        @Override
        public boolean validateOrderSizeMultiplier(WrtInputParams params, int tickBufferTrigger) {
            return true;
        }

        @Override
        public boolean validateBaseOrderSize(IssuerInputParams params, int orderSize) {
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
        public boolean validateRunTicksThreshold(IssuerInputParams params, int runTicksThreshold) {
            return true;
        }

        @Override
        public boolean validateTickSensitivityThreshold(WrtInputParams params, int runTicksThreshold) {
            return true;
        }

        @Override
        public boolean validateStopLoss(WrtInputParams params, long stopLoss) {
            return true;
        }

        @Override
        public boolean validateStopLossTrigger(WrtInputParams params, long stopLossTrigger) {
            return true;
        }

        @Override
        public boolean validateStopProfit(WrtInputParams params, long stopProfit) {
            return true;
        }

        @Override
        public boolean validateAllowedMaxSpread(WrtInputParams params, int allowedMaxSpread) {
            return true;
        }

        @Override
        public boolean validateTurnoverMakingSize(WrtInputParams params, int turnoverMakingSize) {
            return true;
        }

        @Override
        public boolean validateTurnoverMakingPeriod(WrtInputParams params, long turnoverMakingPeriod) {
            return true;
        }

        @Override
        public boolean validateBanPeriodToDownVol(WrtInputParams params, long banPeriodToDownVol) {
            return true;
        }

        @Override
        public boolean validateBanPeriodToTurnoverMaking(WrtInputParams params, long banPeriodToTurnoverMaking) {
            return true;
        }

        @Override
        public boolean validateSpreadObservationPeriod(WrtInputParams params, long spreadObservationPeriod) {
            return true;
        }

        @Override
        public boolean validateMarketOutlook(WrtInputParams params, MarketOutlookType marketOutlook) {
            return true;
        }

        @Override
        public boolean validateSellOnVolDown(IssuerInputParams params, boolean sellOnVolDown) {
            return true;
        }

        @Override
        public boolean validateIssuerMaxLag(IssuerInputParams params, long issuerMaxLag) {
            return true;
        }

        @Override
        public boolean validateSellingBanPeriod(WrtInputParams params, long sellingBanPeriod) {
            return true;
        }

        @Override
        public boolean validateHoldingPeriod(WrtInputParams params, long holdingPeriod) {
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
        public boolean validateDefaultPricingMode(IssuerInputParams params, final PricingMode pricingMode) {
            return true;
        }

        @Override
        public boolean validateStrategyTriggerType(IssuerInputParams params, final StrategyTriggerType triggerType) {
            return true;
        }

        @Override
        public boolean validateIssuerMaxLagCap(IssuerInputParams params, long issuerLagCap) {
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
        public boolean validateManualOrderTicksFromEnterPrice(WrtInputParams params, int manualOrderTicksFromBid) {
            return true;
        }

        @Override
        public boolean validateWideSpreadBuffer(WrtInputParams params, int wideSpreadBuffer) {
            return true;
        }

        @Override
        public boolean validateAllowAdditionalBuy(WrtInputParams params, boolean allowAdditionalBuy) {
            return true;
        }

        @Override
        public boolean validateSellOnVolDownBanPeriod(IssuerInputParams params, long sellOnVolDownBanPeriod) {
            return true;
        }

        @Override
        public boolean validateUseHoldBidBan(IssuerInputParams params, boolean useHoldBidBan) {
            return true;
        }

        @Override
        public boolean validateAllowStopLossOnWideSpread(WrtInputParams params, boolean allowStopLossWideSpread) {
            return true;
        }

        @Override
        public boolean validateTradesVolumeThreshold(IssuerInputParams params, long tradesVolumeThreshold) {
            return true;
        }

        @Override
        public boolean validateDoNotSell(WrtInputParams params, boolean doNotSell) {
            return true;
        }

        @Override
        public boolean validateOrderSizeRemainder(IssuerInputParams params, long orderSizeRemainder) {
            return true;
        }
        
        @Override
        public boolean validateSafeBidLevelBuffer(WrtInputParams params, int safeBidLevelBuffer) {
            return true;
        }

		@Override
		public boolean validateIgnoreMmSizeOnSell(WrtInputParams params, boolean ignoreMmSizeOnSell) {
			return true;
		}

        @Override
        public boolean validateSellAtBreakEvenOnly(WrtInputParams params, boolean sellAtBreakEvenOnly) {
            return true;
        }

    };

    static private PostUpdateHandler NULL_POST_UPDATE_HANDLER = new WrtInputParams.PostUpdateHandler() {
        @Override
        public void onUpdatedMmBidSize(WrtInputParams params) {
        }

        @Override
        public void onUpdatedMmAskSize(WrtInputParams params) {
        }

        @Override
        public void onUpdatedOrderSizeMultiplier(WrtInputParams params) {
        }
        
        @Override
        public void onUpdatedBaseOrderSize(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedMaxOrderSize(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedOrderSizeIncrement(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedCurrentOrderSize(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedRunTicksThreshold(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedTickSensitivityThreshold(WrtInputParams params) {
        }

        @Override
        public void onUpdatedStopLoss(WrtInputParams params) {
        }

        @Override
        public void onUpdatedStopLossTrigger(WrtInputParams params) {
        }

        @Override
        public void onUpdatedStopProfit(WrtInputParams params) {
        }

        @Override
        public void onUpdatedAllowedMaxSpread(WrtInputParams params) {
        }

        @Override
        public void onUpdatedTurnoverMakingSize(WrtInputParams params) {
        }

        @Override
        public void onUpdatedTurnoverMakingPeriod(WrtInputParams params) {
        }

        @Override
        public void onUpdatedBanPeriodToDownVol(WrtInputParams params) {
        }

        @Override
        public void onUpdatedBanPeriodToTurnoverMaking(WrtInputParams params) {
        }

        @Override
        public void onUpdatedSpreadObservationPeriod(WrtInputParams params) {
        }

        @Override
        public void onUpdatedMarketOutlook(WrtInputParams params) {
        }

        @Override
        public void onUpdatedSellOnVolDown(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedIssuerMaxLag(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedSellingBanPeriod(WrtInputParams params) {
        }

        @Override
        public void onUpdatedHoldingPeriod(WrtInputParams params) {
        }

        @Override
        public void onUpdatedAllowStopLossOnFlashingBid(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedResetStopLossOnVolDown(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedDefaultPricingMode(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedStrategyTriggerType(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedIssuerMaxLagCap(IssuerInputParams params) {
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
        public void onUpdatedManualOrderTicksFromEnterPrice(WrtInputParams params) {
        }
        
        @Override
        public void onUpdatedWideSpreadBuffer(WrtInputParams params) {
        }

        @Override
        public void onUpdatedAllowAdditionalBuy(WrtInputParams params) {
        }

        @Override
        public void onUpdatedSellOnVolDownBanPeriod(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedUseHoldBidBan(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedAllowStopLossOnWideSpread(WrtInputParams params) {
        }

        @Override
        public void onUpdatedTradesVolumeThreshold(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedDoNotSell(WrtInputParams params) {
        }

        @Override
        public void onUpdatedOrderSizeRemainder(IssuerInputParams params) {
        }

        @Override
        public void onUpdatedSafeBidLevelBuffer(WrtInputParams params) {
        }

		@Override
		public void onUpdatedIgnoreMmSizeOnSell(WrtInputParams params) {
		}

        @Override
        public void onUpdatedSellAtBreakEvenOnly(WrtInputParams params) {
        }

    };

    public static final int DEFAULT_SAFE_BID_BUFFER_FROM_ENTER_PRICE = 20;
    public static final int DEFAULT_SAFE_BID_BUFFER_FROM_CURRENT_PRICE = 5;
    
    private Validator validator = NULL_VALIDATOR;
    private PostUpdateHandler postUpdateHandler = NULL_POST_UPDATE_HANDLER;

    private StrategyStatusType status = StrategyStatusType.OFF;
    private long strategyId;
    private long secSid;

    // User Input
    private int mmBidSize;
    private int mmAskSize;
    private int baseOrderSize;
    private int maxOrderSize;
    private int orderSizeIncrement;
    private int currentOrderSize;
    private int orderSizeMultiplier = 1000;
    private int runTicksThreshold;
    private int tickSensitivityThreshold;
    private long stopLoss;
    private long stopLossTrigger;
    private long stopProfit;
    private int allowedMaxSpread;    
    private int turnoverMakingSize;
    private long turnoverMakingPeriod;
    private long banPeriodToDownVol;
    private long banPeriodToTurnoverMaking;
    private long sellingBanPeriod;
    private long holdingPeriod;
    private long spreadObservationPeriod;
    private MarketOutlookType marketOutlook = MarketOutlookType.NULL_VAL;
    private boolean sellOnVolDown;
    private long issuerMaxLag;
    private boolean allowStopLossOnFlashingBid;
    private boolean resetStopLossOnVolDown;
    private PricingMode defaultPricingMode = PricingMode.NULL_VAL;
    private StrategyTriggerType triggerType = StrategyTriggerType.NULL_VAL;
    private long issuerMaxLagCap;
    private boolean sellToNonIssuer;
    private int tickBuffer;
    private int tickBufferInt;
    private int tickBufferFraction;    
    private boolean sellAtQuickProfit;
    private int stopLossTickBuffer;
    private int manualOrderTicksFromEnterPrice = 0;
    private int safeBidLevelBuffer = DEFAULT_SAFE_BID_BUFFER_FROM_ENTER_PRICE;
    private int wideSpreadBuffer;
    private boolean allowAdditionalBuy;
    private long sellOnVolDownBanPeriod;
    private boolean useHoldBidBan;
    private boolean allowStopLossOnWideSpread;
    private long tradesVolumeThreshold;
    private boolean doNotSell;
    private boolean sellAtBreakEvenOnly;
    private int orderSizeRemainder;
    private boolean ignoreMmSizeOnSell;

    // Strategy Output
    private int tickSensitivity;
    private int warrantSpread = Integer.MAX_VALUE;
    private PricingMode pricingMode = PricingMode.NULL_VAL;
    private Greeks greeks = Greeks.of(Security.INVALID_SECURITY_SID);
    private boolean canSellOnWide;
    private int orderSize;
    
    // Strategy Output for Positions
    private int enterMMSpread = Integer.MAX_VALUE;
    private int enterPrice;
    private int enterLevel;
    private int enterQuantity;
    private int exitLevel;
    private int profitRun;
    private int enterMMBidPrice;
    private int enterBidLevel;
    private long enterSpotPrice;
    private long stopLossAdjustment;
    private int safeBidPrice;
    
    // Stats
    private SpreadState spreadState = SpreadState.NULL_VAL;
    private long issuerLag;
    private long issuerSmoothing;
    private int numSpreadResets;
    private int numWAvgDownVols;
    private int numWAvgUpVols;
    private int numMPrcDownVols;
    private int numMPrcUpVols;
    
    public void setValidator(final Validator validator) {
        this.validator = validator;
    }
    public void setPostUpdateHandler(final PostUpdateHandler handler) {
        this.postUpdateHandler = handler;
    }
    
    public long strategyId() {
        return strategyId;
    }
    public GenericWrtParams strategyId(final long strategyId) {
        this.strategyId = strategyId;
        return this;
    }
    
    public long secSid() {
        return secSid;
    }
    public GenericWrtParams secSid(final long secSid) {
        this.secSid = secSid;
        return this;
    } 
    
    @Override
    public StrategyStatusType status() {
        return status;
    }
    @Override
    public GenericWrtParams status(final StrategyStatusType status) {
        this.status = status;
        return this;
    }
    
    // User Input
    @Override
    public int mmBidSize() {
        return mmBidSize;
    }
    @Override
    public GenericWrtParams mmBidSize(final int mmBidSize) {
        this.mmBidSize = mmBidSize;
        return this;
    }
    @Override
    public GenericWrtParams userMmBidSize(final int mmBidSize) {
        if (validator.validateMmBidSize(this, mmBidSize)) {
            mmBidSize(mmBidSize);
            postUpdateHandler.onUpdatedMmBidSize(this);
        }
        return this;
    }

    @Override
    public int mmAskSize() {
        return mmAskSize;
    }
    @Override
    public GenericWrtParams mmAskSize(final int mmAskSize) {
        this.mmAskSize = mmAskSize;
        return this;
    }
    @Override
    public GenericWrtParams userMmAskSize(final int mmAskSize) {
        if (validator.validateMmAskSize(this, mmAskSize)) {
            mmAskSize(mmAskSize);
            postUpdateHandler.onUpdatedMmAskSize(this);
        }        
        return this;
    }

    @Override
    public int orderSizeMultiplier() {
        return orderSizeMultiplier;
    }
    @Override
    public GenericWrtParams orderSizeMultiplier(final int multiplier) {
        this.orderSizeMultiplier = multiplier;
        return this;
    }
    @Override
    public GenericWrtParams userOrderSizeMultiplier(final int multiplier) {
        if (validator.validateOrderSizeMultiplier(this, multiplier)) {
            orderSizeMultiplier(multiplier);
            postUpdateHandler.onUpdatedOrderSizeMultiplier(this);
        }
        return this;
    }
    
    @Override
    public int baseOrderSize() {
        return baseOrderSize;
    }
    @Override
    public GenericWrtParams baseOrderSize(final int orderSize) {
        this.baseOrderSize = orderSize;
        return this;
    }
    @Override
    public GenericWrtParams userBaseOrderSize(final int orderSize) {
        if (validator.validateBaseOrderSize(this, orderSize)) {
            baseOrderSize(orderSize);
            postUpdateHandler.onUpdatedBaseOrderSize(this);
        }
        return this;
    }
    
    @Override
    public int maxOrderSize() {
        return maxOrderSize;
    }
    @Override
    public GenericWrtParams maxOrderSize(final int maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
        return this;
    }
    @Override
    public GenericWrtParams userMaxOrderSize(final int maxOrderSize) {
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
    public GenericWrtParams currentOrderSize(final int currentOrderSize) {
        this.currentOrderSize = currentOrderSize;
        return this;
    }
    @Override
    public GenericWrtParams userCurrentOrderSize(final int currentOrderSize) {
        if (validator.validateCurrentOrderSize(this, currentOrderSize)) {
            currentOrderSize(currentOrderSize);
            postUpdateHandler.onUpdatedCurrentOrderSize(this);
        }
        return this;
    }
    
    @Override
    public int orderSizeIncrement() {
        return orderSizeIncrement;
    }
    @Override
    public IssuerInputParams orderSizeIncrement(int increment) {
        this.orderSizeIncrement = increment;
        return this;
    }
    @Override
    public IssuerInputParams userOrderSizeIncrement(int increment) {
        if (validator.validateOrderSizeIncrement(this, increment)) {
            orderSizeIncrement(increment);
            postUpdateHandler.onUpdatedOrderSizeIncrement(this);
        }
        return this;
    }

    @Override
    public int orderSize() {
        return orderSize;
    }
    @Override
    public GenericWrtParams orderSize(final int orderSize) {
        this.orderSize = orderSize;
        return this;
    }
    
    @Override
    public int runTicksThreshold() {
        return runTicksThreshold;
    }
    @Override
    public GenericWrtParams runTicksThreshold(final int runTicksThreshold) {
        this.runTicksThreshold = runTicksThreshold;
        return this;
    }
    @Override
    public GenericWrtParams userRunTicksThreshold(final int runTicksThreshold) {
        if (validator.validateRunTicksThreshold(this, runTicksThreshold)) {
            runTicksThreshold(runTicksThreshold);
            postUpdateHandler.onUpdatedRunTicksThreshold(this);
        }        
        return this;
    }

    @Override
    public int tickSensitivityThreshold() {
        return tickSensitivityThreshold;
    }
    @Override
    public GenericWrtParams tickSensitivityThreshold(final int tickSensitivityThreshold) {
        this.tickSensitivityThreshold = tickSensitivityThreshold;
        return this;
    }
    @Override
    public GenericWrtParams userTickSensitivityThreshold(final int tickSensitivityThreshold) {
        if (validator.validateTickSensitivityThreshold(this, tickSensitivityThreshold)) {
            tickSensitivityThreshold(tickSensitivityThreshold);
            postUpdateHandler.onUpdatedTickSensitivityThreshold(this);
        }        
        return this;
    }

    @Override
    public long stopLoss() {
        return stopLoss;
    }
    @Override
    public GenericWrtParams stopLoss(final long stopLoss) {
        this.stopLoss = stopLoss;
        return this;
    }
    @Override
    public GenericWrtParams userStopLoss(final long stopLoss) {
        if (validator.validateStopLoss(this, stopLoss)) {
            stopLoss(stopLoss);
            postUpdateHandler.onUpdatedStopLoss(this);
        }
        return this;
    }
    
    @Override
    public long stopLossAdjustment() {
        return stopLossAdjustment;
    }
    @Override
    public GenericWrtParams stopLossAdjustment(long stopLossAdjustment) {
        this.stopLossAdjustment = stopLossAdjustment;
        return this;
    }

    @Override
    public long stopLossTrigger() {
        return stopLossTrigger;
    }
    @Override
    public GenericWrtParams stopLossTrigger(final long stopLossTrigger) {
        this.stopLossTrigger = stopLossTrigger;
        return this;
    }
    @Override
    public GenericWrtParams userStopLossTrigger(final long stopLossTrigger) {
        if (validator.validateStopLossTrigger(this, stopLossTrigger)) {
            stopLossTrigger(stopLossTrigger);
            postUpdateHandler.onUpdatedStopLossTrigger(this);
        }
        return this;
    }

    @Override
    public long stopProfit() {
        return stopProfit;
    }
    @Override
    public GenericWrtParams stopProfit(final long stopProfit) {
        this.stopProfit = stopProfit;
        return this;
    }
    @Override
    public GenericWrtParams userStopProfit(final long stopProfit) {
        if (validator.validateStopProfit(this, stopProfit)) {
            stopProfit(stopProfit);
            postUpdateHandler.onUpdatedStopProfit(this);
        }
        return this;
    }

    @Override
    public int allowedMaxSpread() {
        return this.allowedMaxSpread;
    }
    @Override
    public GenericWrtParams allowedMaxSpread(final int allowedMaxSpread) {
        this.allowedMaxSpread = allowedMaxSpread;
        return this;
    }
    @Override
    public GenericWrtParams userAllowedMaxSpread(final int allowedMaxSpread) {
        if (validator.validateAllowedMaxSpread(this, allowedMaxSpread)) {
            allowedMaxSpread(allowedMaxSpread);
            postUpdateHandler.onUpdatedAllowedMaxSpread(this);
        }
        return this;
    }

    @Override
    public int turnoverMakingSize() {
        return this.turnoverMakingSize;
    }
    @Override
    public GenericWrtParams turnoverMakingSize(final int turnoverMakingSize) {
        this.turnoverMakingSize = turnoverMakingSize;
        return this;
    }
    @Override
    public GenericWrtParams userTurnoverMakingSize(final int turnoverMakingSize) {
        if (validator.validateTurnoverMakingSize(this, turnoverMakingSize)) {
            turnoverMakingSize(turnoverMakingSize);
            postUpdateHandler.onUpdatedTurnoverMakingSize(this);
        }
        return this;
    }

    @Override
    public long turnoverMakingPeriod() {
        return this.turnoverMakingPeriod;
    }
    @Override
    public GenericWrtParams turnoverMakingPeriod(final long turnoverMakingPeriod) {
        this.turnoverMakingPeriod = turnoverMakingPeriod;
        return this;
    }
    @Override
    public GenericWrtParams userTurnoverMakingPeriod(final long turnoverMakingPeriod) {
        if (validator.validateTurnoverMakingPeriod(this, turnoverMakingPeriod)) {
            turnoverMakingPeriod(turnoverMakingPeriod);
            postUpdateHandler.onUpdatedTurnoverMakingPeriod(this);
        }
        return this;
    }

    @Override
    public long banPeriodToDownVol() {
        return this.banPeriodToDownVol;
    }
    @Override
    public GenericWrtParams banPeriodToDownVol(final long banPeriodToDownVol) {
        this.banPeriodToDownVol = banPeriodToDownVol;
        return this;
    }
    @Override
    public GenericWrtParams userBanPeriodToDownVol(final long banPeriodToDownVol) {
        if (validator.validateBanPeriodToDownVol(this, banPeriodToDownVol)) {        
            banPeriodToDownVol(banPeriodToDownVol);
            postUpdateHandler.onUpdatedBanPeriodToDownVol(this);
        }        
        return this;
    }

    @Override
    public long banPeriodToTurnoverMaking() {
        return this.banPeriodToTurnoverMaking;
    }
    @Override
    public GenericWrtParams banPeriodToTurnoverMaking(final long banPeriodToTurnoverMaking) {
        this.banPeriodToTurnoverMaking = banPeriodToTurnoverMaking;
        return this;
    }
    @Override
    public GenericWrtParams userBanPeriodToTurnoverMaking(final long banPeriodToTurnoverMaking) {
        if (validator.validateBanPeriodToTurnoverMaking(this, banPeriodToTurnoverMaking)) {        
            banPeriodToTurnoverMaking(banPeriodToTurnoverMaking);
            postUpdateHandler.onUpdatedBanPeriodToTurnoverMaking(this);
        }
        return this;
    }

    @Override
    public long sellingBanPeriod() {
        return this.sellingBanPeriod;
    }
    @Override
    public GenericWrtParams sellingBanPeriod(final long sellingBanPeriod) {
        this.sellingBanPeriod = sellingBanPeriod;
        return this;
    }
    @Override
    public GenericWrtParams userSellingBanPeriod(final long sellingBanPeriod) {
        if (validator.validateSellingBanPeriod(this, sellingBanPeriod)) {        
            sellingBanPeriod(sellingBanPeriod);
            postUpdateHandler.onUpdatedSellingBanPeriod(this);
        }
        return this;
    }

    @Override
    public long holdingPeriod() {
        return this.holdingPeriod;
    }
    @Override
    public GenericWrtParams holdingPeriod(final long holdingPeriod) {
        this.holdingPeriod = holdingPeriod;
        return this;
    }    
    @Override
    public GenericWrtParams userHoldingPeriod(final long holdingPeriod) {
        if (validator.validateHoldingPeriod(this, holdingPeriod)) {        
            holdingPeriod(holdingPeriod);
            postUpdateHandler.onUpdatedHoldingPeriod(this);
        }        
        return this;
    }    

    @Override
    public long spreadObservationPeriod() {
        return this.spreadObservationPeriod;
    }
    @Override
    public GenericWrtParams spreadObservationPeriod(final long observationPeriod) {
        this.spreadObservationPeriod = observationPeriod;
        return this;
    }
    @Override
    public GenericWrtParams userSpreadObservationPeriod(final long observationPeriod) {
        if (validator.validateSpreadObservationPeriod(this, observationPeriod)) {
            spreadObservationPeriod(observationPeriod);
            postUpdateHandler.onUpdatedSpreadObservationPeriod(this);
        }        
        return this;
    }

    @Override
    public MarketOutlookType marketOutlook() {
        return this.marketOutlook;
    }
    @Override
    public GenericWrtParams marketOutlook(final MarketOutlookType marketOutlook) {
        this.marketOutlook = marketOutlook;
        return this;
    }
    @Override
    public GenericWrtParams userMarketOutlook(final MarketOutlookType marketOutlook) {
        if (validator.validateMarketOutlook(this, marketOutlook)) {
            marketOutlook(marketOutlook);
            postUpdateHandler.onUpdatedMarketOutlook(this);
        }        
        return this;
    }

    @Override
    public boolean sellOnVolDown() {
        return this.sellOnVolDown;
    }
    @Override
    public GenericWrtParams sellOnVolDown(final boolean sellOnVolDown) {
        this.sellOnVolDown = sellOnVolDown;
        return this;
    }
    @Override
    public GenericWrtParams userSellOnVolDown(final boolean sellOnVolDown) {
        if (validator.validateSellOnVolDown(this, sellOnVolDown)) {
            sellOnVolDown(sellOnVolDown);
            postUpdateHandler.onUpdatedSellOnVolDown(this);
        }
        return this;
    }

    @Override
    public long issuerMaxLag() {
        return this.issuerMaxLag;
    }
    @Override
    public GenericWrtParams issuerMaxLag(final long issuerMaxLag) {
        this.issuerMaxLag = issuerMaxLag;
        return this;
    }
    public GenericWrtParams userIssuerMaxLag(final long issuerMaxLag) {
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
    public GenericWrtParams allowStopLossOnFlashingBid(final boolean allowStopLossOnFlashingBid) {
        this.allowStopLossOnFlashingBid = allowStopLossOnFlashingBid;
        return this;
    }
    @Override
    public GenericWrtParams userAllowStopLossOnFlashingBid(final boolean allowStopLossOnFlashingBid) {
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
    public GenericWrtParams resetStopLossOnVolDown(final boolean resetStopLossOnVolDown) {
        this.resetStopLossOnVolDown = resetStopLossOnVolDown;
        return this;
    }
    @Override
    public GenericWrtParams userResetStopLossOnVolDown(final boolean resetStopLossOnVolDown) {
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
    public GenericWrtParams defaultPricingMode(final PricingMode pricingMode) {
        this.defaultPricingMode = pricingMode;
        return this;
    }
    @Override
    public GenericWrtParams userDefaultPricingMode(final PricingMode pricingMode) {
        if (validator.validateDefaultPricingMode(this, pricingMode)) {
            defaultPricingMode(pricingMode);
            postUpdateHandler.onUpdatedDefaultPricingMode(this);
        }
        return this;
    }

    @Override
    public StrategyTriggerType strategyTriggerType() {
        return triggerType;
    }
    @Override
    public GenericWrtParams strategyTriggerType(final StrategyTriggerType triggerType) {
        this.triggerType = triggerType;
        return this;
    }
    @Override
    public GenericWrtParams userStrategyTriggerType(final StrategyTriggerType triggerType) {
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
    public GenericWrtParams issuerMaxLagCap(final long issuerMaxLagCap) {
        this.issuerMaxLagCap = issuerMaxLagCap;
        return this;
    }
    @Override
    public GenericWrtParams userIssuerMaxLagCap(final long issuerMaxLagCap) {
        if (validator.validateIssuerMaxLagCap(this, issuerMaxLagCap)) {
            issuerMaxLagCap(issuerMaxLagCap);
            postUpdateHandler.onUpdatedIssuerMaxLagCap(this);
        }
        return this;
    }
    
    @Override
    public boolean sellToNonIssuer() {
        return this.sellToNonIssuer;
    }
    @Override
    public GenericWrtParams sellToNonIssuer(final boolean sellToNonIssuer) {
        this.sellToNonIssuer = sellToNonIssuer;
        return this;
    }
    @Override
    public GenericWrtParams userSellToNonIssuer(final boolean sellToNonIssuer) {
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
    public GenericWrtParams tickBuffer(final int tickBuffer) {
        this.tickBuffer = tickBuffer;
        this.tickBufferInt = this.tickBuffer / (int)ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        this.tickBufferFraction = this.tickBuffer % (int)ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        return this;
    }
    @Override
    public GenericWrtParams userTickBuffer(final int tickBuffer) {
        if (validator.validateTickBuffer(this, tickBuffer)) {
            tickBuffer(tickBuffer);
            postUpdateHandler.onUpdatedTickBuffer(this);
        }
        return this;
    }
    
    public int tickBufferInt() {
        return this.tickBufferInt;
    }

    public int tickBufferFraction() {
        return this.tickBufferFraction;
    }
    
    @Override
    public boolean sellAtQuickProfit() {
        return sellAtQuickProfit;
    }    
    @Override
    public GenericWrtParams sellAtQuickProfit(final boolean sellAtQuickProfit) {
        this.sellAtQuickProfit = sellAtQuickProfit;
        return this;
    }
    @Override
    public GenericWrtParams userSellAtQuickProfit(final boolean sellAtQuickProfit) {
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
    public GenericWrtParams stopLossTickBuffer(final int stopLossTickBuffer) {
        this.stopLossTickBuffer = stopLossTickBuffer;
        return this;
    }
    @Override
    public GenericWrtParams userStopLossTickBuffer(final int stopLossTickBuffer) {
        if (validator.validateStopLossTickBuffer(this, stopLossTickBuffer)) {
            stopLossTickBuffer(stopLossTickBuffer);
            postUpdateHandler.onUpdatedStopLossTickBuffer(this);
        }
        return this;
    }    

    @Override
    public int manualOrderTicksFromEnterPrice() {
        return manualOrderTicksFromEnterPrice;
    }
    @Override
    public GenericWrtParams manualOrderTicksFromEnterPrice(final int manualOrderTicksFromEnterPrice) {
        this.manualOrderTicksFromEnterPrice = manualOrderTicksFromEnterPrice;
        return this;
    }
    @Override
    public GenericWrtParams userManualOrderTicksFromEnterPrice(final int manualOrderTicksFromEnterPrice) {
        if (validator.validateManualOrderTicksFromEnterPrice(this, manualOrderTicksFromEnterPrice)) {
            manualOrderTicksFromEnterPrice(manualOrderTicksFromEnterPrice);
            postUpdateHandler.onUpdatedManualOrderTicksFromEnterPrice(this);
        }
        return this;
    }
    
    @Override
    public int wideSpreadBuffer() {
        return wideSpreadBuffer;
    }
    @Override
    public GenericWrtParams wideSpreadBuffer(final int wideSpreadBuffer) {
        this.wideSpreadBuffer = wideSpreadBuffer;
        return this;
    }
    @Override
    public GenericWrtParams userWideSpreadBuffer(int wideSpreadBuffer) {
        if (validator.validateWideSpreadBuffer(this, wideSpreadBuffer)) {
            wideSpreadBuffer(wideSpreadBuffer);
            postUpdateHandler.onUpdatedWideSpreadBuffer(this);
        }
        return this;
    }
    
    @Override
    public boolean allowAdditionalBuy() {
        return allowAdditionalBuy;
    }
    @Override
    public GenericWrtParams allowAdditionalBuy(final boolean allowAdditionalBuy) {
        this.allowAdditionalBuy = allowAdditionalBuy;
        return this;
    }
    @Override
    public GenericWrtParams userAllowAdditionalBuy(final boolean allowAdditionalBuy) {
        if (validator.validateAllowAdditionalBuy(this, allowAdditionalBuy)) {
            allowAdditionalBuy(allowAdditionalBuy);
            postUpdateHandler.onUpdatedAllowAdditionalBuy(this);
        }        
        return this;
    }
    
    @Override
    public boolean allowStopLossOnWideSpread() {
        return allowStopLossOnWideSpread;
    }
    @Override
    public GenericWrtParams allowStopLossOnWideSpread(final boolean allowStopLossOnWideSpread) {
        this.allowStopLossOnWideSpread = allowStopLossOnWideSpread;
        return this;
    }
    @Override
    public GenericWrtParams userAllowStopLossOnWideSpread(final boolean allowStopLossOnWideSpread) {
        if (validator.validateAllowStopLossOnWideSpread(this, allowStopLossOnWideSpread)) {
            allowStopLossOnWideSpread(allowStopLossOnWideSpread);
            postUpdateHandler.onUpdatedAllowStopLossOnWideSpread(this);
        }        
        return this;
    }
    
    @Override
    public boolean doNotSell() {
        return doNotSell;
    }
    @Override
    public GenericWrtParams doNotSell(final boolean doNotSell) {
        this.doNotSell = doNotSell;
        return this;
    }
    @Override
    public GenericWrtParams userDoNotSell(final boolean doNotSell) {
        if (validator.validateDoNotSell(this, doNotSell)) {
            doNotSell(doNotSell);
            postUpdateHandler.onUpdatedDoNotSell(this);
        }        
        return this;
    }

    @Override
    public boolean sellAtBreakEvenOnly() {
        return sellAtBreakEvenOnly;
    }
    @Override
    public GenericWrtParams sellAtBreakEvenOnly(final boolean sellAtBreakEvenOnly) {
        this.sellAtBreakEvenOnly = sellAtBreakEvenOnly;
        return this;
    }
    @Override
    public GenericWrtParams userSellAtBreakEvenOnly(final boolean sellAtBreakEvenOnly) {
        if (validator.validateSellAtBreakEvenOnly(this, sellAtBreakEvenOnly)) {
            sellAtBreakEvenOnly(sellAtBreakEvenOnly);
            postUpdateHandler.onUpdatedSellAtBreakEvenOnly(this);
        }        
        return this;
    }

    @Override
    public long tradesVolumeThreshold() {
        return tradesVolumeThreshold;
    }
    @Override
    public GenericWrtParams tradesVolumeThreshold(final long tradesVolumeThreshold) {
        this.tradesVolumeThreshold = tradesVolumeThreshold;
        return this;
    }
    @Override
    public GenericWrtParams userTradesVolumeThreshold(final long tradesVolumeThreshold) {
        if (validator.validateTradesVolumeThreshold(this, tradesVolumeThreshold)) {
            tradesVolumeThreshold(tradesVolumeThreshold);
            postUpdateHandler.onUpdatedTradesVolumeThreshold(this);
        }        
        return this;
    }

    @Override
    public long sellOnVolDownBanPeriod() {
        return sellOnVolDownBanPeriod;
    }
    @Override
    public GenericWrtParams sellOnVolDownBanPeriod(final long sellOnVolDownBanPeriod) {
        this.sellOnVolDownBanPeriod = sellOnVolDownBanPeriod;
        return this;
    }
    @Override
    public GenericWrtParams userSellOnVolDownBanPeriod(final long sellOnVolDownBanPeriod) {
        if (validator.validateSellOnVolDownBanPeriod(this, sellOnVolDownBanPeriod)) {
            sellOnVolDownBanPeriod(sellOnVolDownBanPeriod);
            postUpdateHandler.onUpdatedSellOnVolDownBanPeriod(this);
        }        
        return this;
    }
    
    @Override
    public boolean useHoldBidBan() {
        return useHoldBidBan;
    }
    @Override
    public GenericWrtParams useHoldBidBan(final boolean useHoldBidBan) {
        this.useHoldBidBan = useHoldBidBan;
        return this;
    }
    @Override
    public GenericWrtParams userUseHoldBidBan(final boolean useHoldBidBan) {
        if (validator.validateUseHoldBidBan(this, useHoldBidBan)) {
            useHoldBidBan(useHoldBidBan);
            postUpdateHandler.onUpdatedUseHoldBidBan(this);
        }
        return this;
    }
    
    @Override
    public int orderSizeRemainder() {
        return orderSizeRemainder;
    }
    @Override
    public GenericWrtParams orderSizeRemainder(final int orderSizeRemainder) {
        this.orderSizeRemainder = orderSizeRemainder;
        return this;
    }
    @Override
    public GenericWrtParams userOrderSizeRemainder(final int orderSizeRemainder) {
        if (validator.validateOrderSizeRemainder(this, orderSizeRemainder)) {
            orderSizeRemainder(orderSizeRemainder);
            postUpdateHandler.onUpdatedOrderSizeRemainder(this);
        }
        return this;
    }

    @Override
    public boolean ignoreMmSizeOnSell() {
    	return ignoreMmSizeOnSell;
    	
    }
    @Override
    public WrtInputParams ignoreMmSizeOnSell(final boolean ignoreMmSizeOnSell) {
        this.ignoreMmSizeOnSell = ignoreMmSizeOnSell;
        return this;
    }
    @Override
    public WrtInputParams userIgnoreMmSizeOnSell(final boolean ignoreMmSizeOnSell) {
        if (validator.validateIgnoreMmSizeOnSell(this, ignoreMmSizeOnSell)) {
        	ignoreMmSizeOnSell(ignoreMmSizeOnSell);
            postUpdateHandler.onUpdatedIgnoreMmSizeOnSell(this);
        }
        return this;	
    }

    // Strategy Output
    @Override
    public int tickSensitivity() {
        return this.tickSensitivity;
    }
    @Override
    public GenericWrtParams tickSensitivity(final int tickSensitivity) {
        this.tickSensitivity = tickSensitivity;
        return this;
    }

    @Override
    public int warrantSpread() {
        return this.warrantSpread;
    }
    @Override
    public GenericWrtParams warrantSpread(final int warrantSpread) {
        this.warrantSpread = warrantSpread;
        return this;
    }

    @Override
    public PricingMode pricingMode() {
        return pricingMode;
    }
    @Override
    public GenericWrtParams pricingMode(final PricingMode pricingMode) {
        this.pricingMode = pricingMode;
        return this;
    }

    @Override
    public Greeks greeks() {
        return greeks;        
    }
    @Override
    public GenericWrtParams greeks(final Greeks greeks) {
        this.greeks.copyFrom(greeks);
        return this;
    }
    
    @Override
    public int safeBidLevelBuffer() {
        return safeBidLevelBuffer;
    }
    @Override
    public GenericWrtParams safeBidLevelBuffer(final int safeBidLevelBuffer) {
        this.safeBidLevelBuffer = safeBidLevelBuffer;
        return this;
    }
    @Override
    public GenericWrtParams userSafeBidLevelBuffer(int safeBidLevelBuffer) {
        if (validator.validateSafeBidLevelBuffer(this, safeBidLevelBuffer)) {
            safeBidLevelBuffer(safeBidLevelBuffer);
            postUpdateHandler.onUpdatedSafeBidLevelBuffer(this);
        }
        return this;
    }
    
    // Strategy Output for Positions
    @Override
    public int enterMMSpread() {
        return enterMMSpread;
    }
    @Override
    public GenericWrtParams enterMMSpread(final int enterMMSpread) {
        this.enterMMSpread = enterMMSpread;
        return this;
    }
    
    @Override
    public int enterPrice() {
        return enterPrice;
    }
    @Override
    public GenericWrtParams enterPrice(final int enterPrice) {
        this.enterPrice = enterPrice;
        return this;
    }

    @Override
    public int enterLevel() {
        return enterLevel;
    }
    @Override
    public GenericWrtParams enterLevel(final int enterLevel) {
        this.enterLevel = enterLevel;
        return this;
    }

    @Override
    public int exitLevel() {
        return exitLevel;
    }
    @Override
    public GenericWrtParams exitLevel(final int exitLevel) {
        this.exitLevel = exitLevel;
        return this;
    }

    @Override
    public int profitRun() {
        return profitRun;    
    }
    @Override
    public GenericWrtParams profitRun(final int profitRun) {
        this.profitRun = profitRun;
        return this;
    }
    @Override
    public GenericWrtParams incrProfitRun(final int inc) {
        this.profitRun += inc;
        return this;
    }

    @Override
    public int enterMMBidPrice() {
        return this.enterMMBidPrice;
    }
    @Override
    public GenericWrtParams enterMMBidPrice(final int enterBidPrice) {
        this.enterMMBidPrice = enterBidPrice;
        return this;
    }

    @Override
    public int enterBidLevel() {
        return this.enterBidLevel;
    }
    @Override
    public GenericWrtParams enterBidLevel(final int enterBidLevel) {
        this.enterBidLevel = enterBidLevel;
        return this;
    }

    @Override
    public long enterSpotPrice() {
        return this.enterSpotPrice;
    }
    @Override
    public GenericWrtParams enterSpotPrice(final long enterSpotPrice) {
        this.enterSpotPrice = enterSpotPrice;
        return this;
    }
    
    @Override
    public int enterQuantity() {
        return this.enterQuantity;
    }
    @Override
    public GenericWrtParams enterQuantity(final int enterQuantity) {
        this.enterQuantity = enterQuantity;
        return this;
    }
    
    @Override
    public boolean canSellOnWide() {
        return this.canSellOnWide;
    }
    @Override
    public GenericWrtParams canSellOnWide(final boolean canSellOnWide) {
        this.canSellOnWide = canSellOnWide;
        return this;
    }

    // Stats
    @Override
    public long issuerLag() {
        return this.issuerLag;
    }
    @Override
    public GenericWrtParams issuerLag(final long issuerLag) {
        this.issuerLag = issuerLag;
        return this;
    }

    @Override
    public long issuerSmoothing() {
        return this.issuerSmoothing;
    }
    @Override
    public GenericWrtParams issuerSmoothing(final long issuerSmoothing) {
        this.issuerSmoothing = issuerSmoothing;
        return this;
    }
    
    @Override
    public int numSpreadResets() {
        return this.numSpreadResets;
    }
    @Override
    public GenericWrtParams numSpreadResets(final int numSpreadResets) {
        this.numSpreadResets = numSpreadResets;
        return this;
    }
    @Override
    public GenericWrtParams incNumSpreadResets() {
        this.numSpreadResets++;
        return this;
    }

    @Override
    public int numWAvgDownVols() {
        return this.numWAvgDownVols;
    }
    @Override
    public GenericWrtParams numWAvgDownVols(final int numDownVols) {
        this.numWAvgDownVols = numDownVols;
        return this;
    }
    @Override
    public GenericWrtParams incNumWAvgDownVols() {
        this.numWAvgDownVols++;
        return this;
    }

    @Override
    public int numWAvgUpVols() {
        return this.numWAvgUpVols;
    }
    @Override
    public GenericWrtParams numWAvgUpVols(final int numUpVols) {
        this.numWAvgUpVols = numUpVols;
        return this;
    }
    @Override
    public GenericWrtParams incNumWAvgUpVols() {
        this.numWAvgUpVols++;
        return this;
    }

    @Override
    public int numMPrcDownVols() {
        return this.numMPrcDownVols;
    }
    @Override
    public GenericWrtParams numMPrcDownVols(final int numDownVols) {
        this.numMPrcDownVols = numDownVols;
        return this;
    }
    @Override
    public GenericWrtParams incNumMPrcDownVols() {
        this.numMPrcDownVols++;
        return this;
    }

    @Override
    public int numMPrcUpVols() {
        return this.numMPrcUpVols;
    }
    @Override
    public GenericWrtParams numMPrcUpVols(final int numUpVols) {
        this.numMPrcUpVols = numUpVols;
        return this;
    }
    @Override
    public GenericWrtParams incNumMPrcUpVols() {
        this.numMPrcUpVols++;
        return this;
    }
    
    @Override
    public SpreadState spreadState() {
        return spreadState;
    }
    @Override
    public GenericWrtParams spreadState(final SpreadState spreadState) {
        this.spreadState = spreadState;
        return this;
    }
    
    @Override
    public int safeBidPrice() {
        return safeBidPrice;
    }
    @Override
    public GenericWrtParams safeBidPrice(final int safeBidPrice) {
        this.safeBidPrice = safeBidPrice;
        return this;
    }

    @Override
    public GenericWrtParams clone() {
        final GenericWrtParams clone = new GenericWrtParams();
        copyTo((ParamsSbeEncodable)clone);
        return clone;
    }
    
    @Override
    public void copyTo(final ParamsSbeEncodable other) {
        if (other instanceof GenericWrtParams) {
            final GenericWrtParams o = (GenericWrtParams)other;
            o.strategyId = this.strategyId;
            o.secSid = this.secSid;
            
            copyTo((WrtInputParams)o);
            copyTo((WrtOutputParams)o);
        }
    }
    
    @Override
    public TemplateType templateType() {
        return TemplateType.STRATWRTPARAMUPDATE;
    }
    @Override
    public short blockLength() {
        return StrategyWrtParamsSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeWrtParamsWithoutHeader(buffer, offset, encoder.strategyWrtParamsSbeEncoder(), this);
    }

    @Override
    public int expectedEncodedLength() {
        return StrategySender.expectedEncodedLength(this);
    }
    @Override
    public int schemaId() {
        return StrategyWrtParamsSbeEncoder.SCHEMA_ID;
    }
    @Override
    public int schemaVersion() {
        return StrategyWrtParamsSbeEncoder.SCHEMA_VERSION;
    }

}
