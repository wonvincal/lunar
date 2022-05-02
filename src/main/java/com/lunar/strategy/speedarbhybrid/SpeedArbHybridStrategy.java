package com.lunar.strategy.speedarbhybrid;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyStatusType;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.strategy.TurnoverMakingSignalGenerator;
import com.lunar.strategy.VelocityTriggerGenerator;
import com.lunar.strategy.parameters.IssuerInputParams;
import com.lunar.strategy.parameters.IssuerUndInputParams;
import com.lunar.strategy.parameters.UndInputParams;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.WrtInputParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.AllowAllTriggerGenerator;
import com.lunar.strategy.Strategy;
import com.lunar.strategy.StrategyContext;
import com.lunar.strategy.StrategySecurity;

import static org.apache.logging.log4j.util.Unbox.box;

public class SpeedArbHybridStrategy extends Strategy {
    private static final Logger LOG = LogManager.getLogger(SpeedArbHybridStrategy.class);
    static final String SPEEDARB_HYBRID_VERSION = "2.3.8.9";
    static {
        LOG.info("SpeedArbHybrid version {}...", SPEEDARB_HYBRID_VERSION);
    }
     
    abstract private class WrtParamsValidator implements WrtInputParams.Validator {
        @Override
        public boolean validateMmBidSize(final WrtInputParams params, final int mmBidSize) {
            return true;
        }

        @Override
        public boolean validateMmAskSize(final WrtInputParams params, final int mmAskSize) {
            return true;
        }

        @Override
        public boolean validateBaseOrderSize(final IssuerInputParams params, final int orderSize) {
            return orderSize <= ((WrtInputParams)params).maxOrderSize();
        }

        @Override
        public boolean validateOrderSizeIncrement(final IssuerInputParams params, final int increment) {
            return increment >= 0;
        }

        @Override
        public boolean validateCurrentOrderSize(final IssuerInputParams params, final int orderSize) {
            return orderSize >= 0;
        }

        @Override
        public boolean validateMaxOrderSize(final IssuerInputParams params, final int maxOrderSize) {
            return maxOrderSize <= 1000000;
        }

        @Override
        public boolean validateOrderSizeMultiplier(final WrtInputParams params, final int orderSizeMultiplier) {
            return orderSizeMultiplier >= 0 && orderSizeMultiplier <= 4000;
        }
        
        @Override
        public boolean validateRunTicksThreshold(final IssuerInputParams params, final int runTicksThreshold) {
            return runTicksThreshold >= 0;
        }

        @Override
        public boolean validateTickSensitivityThreshold(final WrtInputParams params, final int ticksSensitivityThreshold) {
            return true;
        }

        @Override
        public boolean validateAllowedMaxSpread(final WrtInputParams params, final int allowedMaxSpread) {
            return true;
        }

        @Override
        public boolean validateTurnoverMakingSize(final WrtInputParams params, final int turnoverMakingSize) {
            return true;
        }

        @Override
        public boolean validateTurnoverMakingPeriod(final WrtInputParams params, final long turnoverMakingPeriod) {
            return true;
        }

        @Override
        public boolean validateBanPeriodToDownVol(final WrtInputParams params, final long banPeriodToDownVol) {
            return true;
        }

        @Override
        public boolean validateBanPeriodToTurnoverMaking(final WrtInputParams params, final long banPeriodToTurnoverMaking) {
            return true;
        }

        @Override
        public boolean validateSpreadObservationPeriod(final WrtInputParams params, final long spreadObservationPeriod) {
            return true;
        }

        @Override
        public boolean validateMarketOutlook(final WrtInputParams params, final MarketOutlookType marketOutlook) {
            return true;
        }

        @Override
        public boolean validateSellOnVolDown(final IssuerInputParams params, final boolean sellOnVolDown) {
            return true;
        }

        @Override
        public boolean validateIssuerMaxLag(final IssuerInputParams params, final long issuerMaxLag) {
            return true;
        }

        @Override
        public boolean validateStopProfit(final WrtInputParams params, final long stopProfit) {
            return true;
        }
        
        @Override
        public boolean validateSellingBanPeriod(final WrtInputParams params, final long sellingBanPeriod) {
            return true;
        }
        
        @Override
        public boolean validateHoldingPeriod(final WrtInputParams params, final long holdingPeriod) {
            return true;     
        }
        
        @Override
        public boolean validateAllowStopLossOnFlashingBid(final IssuerInputParams params, final boolean allowStopLossOnFlashingBid) {
            return true;
        }

        @Override
        public boolean validateResetStopLossOnVolDown(final IssuerInputParams params, final boolean resetStopLossOnVolDown) {
            return true;
        }
        
        @Override
        public boolean validateDefaultPricingMode(final IssuerInputParams params, final PricingMode pricingMode) {
            return true;
        }
        
        @Override
        public boolean validateStrategyTriggerType(final IssuerInputParams params, final StrategyTriggerType triggerType) {
            return true;
        }
        
        @Override
        public boolean validateIssuerMaxLagCap(final IssuerInputParams params, final long issuerLagCap) {
            return true;
        }
        
        @Override
        public boolean validateSellToNonIssuer(final IssuerInputParams params, final boolean sellToNonIssuer) {
            return true;
        }
        
        @Override
        public boolean validateTickBuffer(final IssuerInputParams params, final int tickBuffer) {
            return tickBuffer >= 0 && tickBuffer >= params.stopLossTickBuffer();
        }
        
        @Override
        public boolean validateManualOrderTicksFromEnterPrice(final WrtInputParams params, final int manualOrderBidLevelIncrease) {
            return true;
        }

        @Override
        public boolean validateSellAtQuickProfit(final IssuerInputParams params, final boolean sellAtQuickProfit) {
            return true;
        }

        @Override
        public boolean validateStopLossTickBuffer(final IssuerInputParams params, final int stopLossTickBuffer) {
            return (stopLossTickBuffer >= 0 && stopLossTickBuffer <= params.tickBuffer());
        }
        
        @Override
        public boolean validateWideSpreadBuffer(final WrtInputParams params, final int wideSpreadBuffer) {
            return wideSpreadBuffer >= 0;
        }
        
        @Override
        public boolean validateAllowAdditionalBuy(final WrtInputParams params, final boolean allowAdditionalBuy) {
            return true;
        }
        
        @Override
        public boolean validateSellOnVolDownBanPeriod(final IssuerInputParams params, final long sellOnVolDownBanPeriod) {
            return true;
        }

        @Override
        public boolean validateUseHoldBidBan(final IssuerInputParams params, final boolean useHoldBidBan) {
            return true;
        }

        @Override
        public boolean validateAllowStopLossOnWideSpread(final WrtInputParams params, final boolean allowStopLossOnWideSpread) {
            return true;
        }
        
        @Override
        public boolean validateTradesVolumeThreshold(final IssuerInputParams params, final long tradesVolumeThreshold) {
            return tradesVolumeThreshold >= 0;
        }
        
        @Override
        public boolean validateDoNotSell(final WrtInputParams params, final boolean doNotSell) {
            return true;
        }
        
        @Override
        public boolean validateSellAtBreakEvenOnly(final WrtInputParams params, final boolean sellAtBreakEvenOnly) {
            return true;
        }
        
        @Override
        public boolean validateOrderSizeRemainder(IssuerInputParams params, long orderSizeRemainder) {
            return true;
        }

        @Override
        public boolean validateSafeBidLevelBuffer(final WrtInputParams params, final int safeBidLevelBuffer) {
            return true;
        }

        @Override
        public boolean validateIgnoreMmSizeOnSell(final WrtInputParams params, final boolean ignoreMmSizeOnSell) {
            return true;
        }
    }
    
    abstract private class WrtParamsPostUpdateHandler implements WrtInputParams.PostUpdateHandler {
        @Override
        public void onUpdatedMmBidSize(WrtInputParams params) {
        	LOG.info("User updated mm bid size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.mmBidSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        	try {
        	    m_wrtSignalGenerator.updateWarrantOrderBook();
        	}
        	catch (final Exception e) {
        	    LOG.error("Error updating warrant orderbook...", e);
        	}
        }

        @Override
        public void onUpdatedMmAskSize(WrtInputParams params) {
        	LOG.info("User updated mm ask size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.mmAskSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            try {
                m_wrtSignalGenerator.updateWarrantOrderBook();
            }
            catch (final Exception e) {
                LOG.error("Error updating warrant orderbook...", e);
            }
        }

        @Override
        public void onUpdatedBaseOrderSize(IssuerInputParams params) {
            LOG.info("User updated base order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.baseOrderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedMaxOrderSize(IssuerInputParams params) {
            m_strategySignalHandler.calculateMaxOrderSize();
            m_strategySignalHandler.capAndCalculateOrderSize();
            LOG.info("User updated max order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.maxOrderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            LOG.info("User updated max order size, new current order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(((GenericWrtParams)params).currentOrderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            LOG.info("User updated max order size, new order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(((GenericWrtParams)params).orderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedOrderSizeMultiplier(WrtInputParams params) {
            m_strategySignalHandler.calculateOrderSize();
            LOG.info("User updated order size multiplier: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.orderSizeMultiplier()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            LOG.info("User updated order size multiplier, new order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(((GenericWrtParams)params).orderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedCurrentOrderSize(IssuerInputParams params) {
            m_strategySignalHandler.capAndCalculateOrderSize();
            LOG.info("User updated current order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.currentOrderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            LOG.info("User updated current order size, new order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(((GenericWrtParams)params).orderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedOrderSizeIncrement(IssuerInputParams params) {
            m_strategySignalHandler.calculateMaxOrderSize();
            m_strategySignalHandler.capAndCalculateOrderSize();
            LOG.info("User updated order size increment: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.orderSizeIncrement()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            LOG.info("User updated order size increment, new current order size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(((GenericWrtParams)params).currentOrderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedRunTicksThreshold(IssuerInputParams params) {
        	LOG.info("User updated profit run threshold: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.runTicksThreshold()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedTickSensitivityThreshold(WrtInputParams params) {
        	LOG.info("User updated tick sensitivity threshold: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.tickSensitivityThreshold()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedStopLossTrigger(WrtInputParams params) {
            LOG.info("User updated stop loss trigger: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.stopLossTrigger()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedAllowedMaxSpread(WrtInputParams params) {
            LOG.info("User updated allowed max spread: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.allowedMaxSpread()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedTurnoverMakingSize(WrtInputParams params) {
        	LOG.info("User updated turnover making size: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.turnoverMakingSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedTurnoverMakingPeriod(WrtInputParams params) {
        	LOG.info("User updated turnover making period: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.turnoverMakingPeriod()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedBanPeriodToDownVol(WrtInputParams params) {
        	LOG.info("User updated ban period to down vol: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.banPeriodToDownVol()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedBanPeriodToTurnoverMaking(WrtInputParams params) {
        	LOG.info("User updated ban period to turnover making: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.banPeriodToTurnoverMaking()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedSpreadObservationPeriod(WrtInputParams params) {
        	LOG.info("User updated spread observation period: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.spreadObservationPeriod()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedMarketOutlook(WrtInputParams params) {
            LOG.info("User updated market outlook: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.marketOutlook().name(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedSellOnVolDown(IssuerInputParams params) {
            LOG.info("User updated sell on vol down: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.sellOnVolDown()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedIssuerMaxLag(IssuerInputParams params) {
        	LOG.info("User updated issuer max lag: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.issuerMaxLag()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        	m_wrtSignalGenerator.refreshIssuerMaxLagForBucketPricer();
        }

        @Override
        public void onUpdatedStopProfit(WrtInputParams params) {
            LOG.info("User updated stop profit: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.stopProfit()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedSellingBanPeriod(final WrtInputParams params) {
            LOG.info("User updated selling ban period: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.sellingBanPeriod()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedHoldingPeriod(final WrtInputParams params) {
            LOG.info("User updated holding period: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.holdingPeriod()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedAllowStopLossOnFlashingBid(IssuerInputParams params) {
            LOG.info("User updated allow stoploss on flashing bid: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.allowStopLossOnFlashingBid()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedResetStopLossOnVolDown(IssuerInputParams params) {
            LOG.info("User updated reset stoploss on vol down: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.resetStopLossOnVolDown()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedDefaultPricingMode(IssuerInputParams params) {
            LOG.info("User updated default pricing mode: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.defaultPricingMode(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedStrategyTriggerType(IssuerInputParams params) {
            LOG.info("User updated strategy trigger type: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.strategyTriggerType(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_strategySignalHandler.onUpdatedTriggerGenerator();
        }

        @Override
        public void onUpdatedIssuerMaxLagCap(IssuerInputParams params) {
            LOG.info("User updated issuer lag cap: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.issuerMaxLagCap()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedSellToNonIssuer(IssuerInputParams params) {
            LOG.info("User updated sell to non issuer: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.sellToNonIssuer(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedTickBuffer(IssuerInputParams params) {
            LOG.info("User updated tick buffer: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.tickBuffer()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedManualOrderTicksFromEnterPrice(WrtInputParams params) {
            LOG.info("User updated manual order ticks from enter price: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.manualOrderTicksFromEnterPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedSellAtQuickProfit(IssuerInputParams params) {
            LOG.info("User updated sell at quick profit: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.sellAtQuickProfit()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));            
        }

        @Override
        public void onUpdatedStopLossTickBuffer(IssuerInputParams params) {
            LOG.info("User updated stop loss tick buffer: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.stopLossTickBuffer()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));            
        }
        
        @Override
        public void onUpdatedWideSpreadBuffer(WrtInputParams params) {
            LOG.info("User updated wide spread buffer: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.wideSpreadBuffer()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_wrtSignalGenerator.updateIsLooselyTight();
        }
        
        @Override
        public void onUpdatedAllowAdditionalBuy(WrtInputParams params) {
            LOG.info("User updated allow additional buy: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.allowAdditionalBuy()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));            
        }
        
        @Override
        public void onUpdatedSellOnVolDownBanPeriod(IssuerInputParams params) {
            LOG.info("User updated sell on vol down ban period: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.sellOnVolDownBanPeriod()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
        
        @Override
        public void onUpdatedUseHoldBidBan(IssuerInputParams params) {
            LOG.info("User updated use hold bid ban: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.useHoldBidBan(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            if (!params.useHoldBidBan()) {
                m_wrtSignalGenerator.resetHoldBidBanPrice();
            }
        }
        
        @Override
        public void onUpdatedAllowStopLossOnWideSpread(WrtInputParams params) {
            LOG.info("User updated allow stop loss on wide spread: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.allowStopLossOnWideSpread(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            try {
                m_strategySignalHandler.onUpdatedAllowStopLossOnWideSpread();
            }
            catch (final Exception e) {
                LOG.error("Error attempting to update allow stop loss on wide spread...", e);
            }
        }
        
        @Override
        public void onUpdatedTradesVolumeThreshold(IssuerInputParams params) {
            LOG.info("User updated outstanding threshold: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.tradesVolumeThreshold()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_strategySignalHandler.onUpdatedTradesVolumeThreshold();
        }

        @Override
        public void onUpdatedDoNotSell(WrtInputParams params) {
            LOG.info("User updated do not sell: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.doNotSell(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_strategySignalHandler.onUpdatedDoNotSell();
        }
        
        @Override
        public void onUpdatedSellAtBreakEvenOnly(WrtInputParams params) {
            LOG.info("User updated sell at breakeven only: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), params.sellAtBreakEvenOnly(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_strategySignalHandler.onUpdatedSellOnBreakEvenOnly();
        }

        @Override
        public void onUpdatedOrderSizeRemainder(IssuerInputParams params) {
            LOG.info("User updated order size remainder: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.orderSizeRemainder()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_strategySignalHandler.calculateOrderSizeRemainder();
        }
        
        @Override
        public void onUpdatedSafeBidLevelBuffer(WrtInputParams params) {
            LOG.info("User updated safe bid level buffer: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.safeBidLevelBuffer()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_strategySignalHandler.updateSafeBidPrice();
            LOG.info("User updated safe bid price: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(((GenericWrtParams)params).safeBidPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }

        @Override
        public void onUpdatedIgnoreMmSizeOnSell(WrtInputParams params) {
            LOG.info("User updated ignore mm size on sell: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.ignoreMmSizeOnSell()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            try {
                m_strategySignalHandler.onUpdatedIgnoreMmSizeOnSell();
            }
            catch (final Exception e) {
                LOG.error("Error attempting to update ignore mm size on sell...", e);
            }            
        }

    }
    
    static final private int NUM_MARKET_DATA_UPDATE_HANDLERS = 4;
    
    final private SpeedArbHybridUndSignalGenerator m_undSignalGenerator;
	final private SpeedArbHybridWrtSignalGenerator m_wrtSignalGenerator;
	final private SpeedArbHybridStrategySignalHandler m_strategySignalHandler;
    final private GenericStrategyTypeParams m_speedArbParams;
	final private GenericWrtParams m_speedArbWrtParams;
	final private GenericUndParams m_speedArbUndParams;
	final private GenericIssuerUndParams m_speedArbIssuerUndParams;
	
    private boolean pendingSwitchOn;

    public static SpeedArbHybridStrategy of(final StrategyContext strategyContext, final long securitySid, final long strategySid) {
        final SpeedArbHybridContext speedArbContext = (SpeedArbHybridContext)strategyContext;
        final StrategySecurity warrant = speedArbContext.getWarrants().get(securitySid);
        final StrategySecurity underlying = warrant.underlying();
        speedArbContext.initializeContextForSecurity(warrant);
        final SpeedArbHybridUndSignalGenerator undSignalGenerator = speedArbContext.getUndSignalGenerator(underlying);
        final SpeedArbHybridWrtSignalGenerator wrtSignalGenerator = speedArbContext.getWrtSignalGenerator(warrant);
        final SpeedArbHybridStrategySignalHandler strategySignalHandler = speedArbContext.getStrategySignalHandler(warrant);

        final GenericStrategyTypeParams speedArbParams = speedArbContext.getStrategyTypeParams();
        final GenericUndParams speedArbUndParams = speedArbContext.getStrategyUndParams(underlying.sid(), false);
        final GenericWrtParams speedArbWrtParams = speedArbContext.getStrategyWrtParams(warrant.sid(), false);
        final GenericIssuerParams speedArbIssuerParams = speedArbContext.getStrategyIssuerParams(warrant.issuerSid(), false);
        final GenericIssuerUndParams speedArbIssuerUndParams = speedArbContext.getStrategyIssuerUndParams(warrant.issuerSid(), underlying.sid(), false);

        final SpeedArbHybridStrategy strategy = new SpeedArbHybridStrategy(strategySid, warrant, underlying, undSignalGenerator, wrtSignalGenerator, strategySignalHandler,
                speedArbParams, speedArbUndParams, speedArbWrtParams, speedArbIssuerParams, speedArbIssuerUndParams);

        return strategy;
    }
	
	public SpeedArbHybridStrategy(final long strategyId, final StrategySecurity security, final StrategySecurity underlying,
	        final SpeedArbHybridUndSignalGenerator  undSignalGenerator,
	        final SpeedArbHybridWrtSignalGenerator wrtSignalGenerator,
	        final SpeedArbHybridStrategySignalHandler strategySignalHandler,
	        final GenericStrategyTypeParams speedArbParams, 
	        final GenericUndParams speedArbUndParams, 
	        final GenericWrtParams speedArbWrtParams, 
	        final GenericIssuerParams speedArbIssuerParams,
	        final GenericIssuerUndParams speedArbIssuerUndParams) {
		super(strategyId, security);
		m_strategySignalHandler = strategySignalHandler;
		m_wrtSignalGenerator = wrtSignalGenerator;
		m_undSignalGenerator = undSignalGenerator;
		m_speedArbParams = speedArbParams;
		m_speedArbWrtParams = speedArbWrtParams;
		m_speedArbUndParams = speedArbUndParams;
		m_speedArbIssuerUndParams = speedArbIssuerUndParams;
		m_strategySignalHandler.initialize();
		
        speedArbUndParams.incTotalWarrants();
        speedArbIssuerParams.incTotalWarrants();

        pendingSwitchOn = false;
        
        if (security.putOrCall().equals(PutOrCall.CALL)) {
    		speedArbWrtParams.setValidator(new WrtParamsValidator() {
                @Override
                public boolean validateStopLoss(WrtInputParams params, long stopLoss) {
                    if (stopLoss <= m_wrtSignalGenerator.getSpotPrice()) {
                        return true;
                    }
                    LOG.error("Validation failed for stop loss because it is higher than the spotPrice: secCode {}, value {}, spotPrice {}", getSecurity().code(), box(stopLoss), box(m_wrtSignalGenerator.getSpotPrice()));
                    return false;
                }

                @Override
                public boolean validateStopLossTrigger(WrtInputParams params, long stopLossTrigger) {
                    if (stopLossTrigger > m_wrtSignalGenerator.getSpotPrice()) {
                        return true;
                    }
                    LOG.error("Validation failed for stop loss trigger because it is lower or equal to the spotPrice: secCode {}, value {}, spotPrice {}", getSecurity().code(), box(stopLossTrigger), box(m_wrtSignalGenerator.getSpotPrice()));
                    return false;
                }
    		});
    		speedArbWrtParams.setPostUpdateHandler(new WrtParamsPostUpdateHandler() {
                public void onUpdatedStopLoss(WrtInputParams params) {
                    if (params.stopLoss() == 0) {
                        params.stopLoss(m_wrtSignalGenerator.getSpotPrice());
                    }
                    m_strategySignalHandler.onStopLossExternallyUpdated();
                    LOG.info("User updated stop loss: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                }
    		});
        }
        else {
    		speedArbWrtParams.setValidator(new WrtParamsValidator() {
                @Override
                public boolean validateStopLoss(WrtInputParams params, long stopLoss) {
                    if (stopLoss >= m_wrtSignalGenerator.getSpotPrice() || stopLoss == 0) {
                        return true;
                    }
                    LOG.error("Validation failed for stop loss because it is lower than the spotPrice: secCode {}, value {}, spotPrice {}", getSecurity().code(), box(stopLoss), box(m_wrtSignalGenerator.getSpotPrice()));
                    return false;
                }

                @Override
                public boolean validateStopLossTrigger(WrtInputParams params, long stopLossTrigger) {
                    if (stopLossTrigger < m_wrtSignalGenerator.getSpotPrice()) {
                        return true;
                    }
                    LOG.error("Validation failed for stop loss trigger because it is higher or equal to the spotPrice: secCode {}, value {}, spotPrice {}", getSecurity().code(), box(stopLossTrigger), box(m_wrtSignalGenerator.getSpotPrice()));
                    return false;
                }
            });
    		speedArbWrtParams.setPostUpdateHandler(new WrtParamsPostUpdateHandler() {
                @Override
                public void onUpdatedStopLoss(WrtInputParams params) {
                    if (params.stopLoss() == 0) {
                        params.stopLoss(m_wrtSignalGenerator.getSpotPrice());
                    }
                    m_strategySignalHandler.onStopLossExternallyUpdated();
                    LOG.info("User updated stop loss: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                }
            });   
        }
        speedArbUndParams.setPostUpdateHandler(new UndInputParams.PostUpdateHandler() {
            @Override
            public void onUpdatedSizeThreshold(UndInputParams params) {
                LOG.info("User updated stop loss: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.sizeThreshold()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));                
            }

            @Override
            public void onUpdatedVelocityThreshold(UndInputParams params) {
                LOG.info("User updated velocity threshold: secCode {}, value {}, trigger seqNum {}", getSecurity().code(), box(params.velocityThreshold()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));                
            }
            
        });
        speedArbIssuerUndParams.setValidator(new IssuerUndInputParams.Validator() {
			
			@Override
			public boolean validateUndTradeVolThreshold(IssuerUndInputParams params, long value) {
				boolean result = (value >= 0);
				if (!result){
					LOG.info("Invalid und trade vol threshold, no update: secCode {}, value {}", getSecurity().code(), value);
				}
				return result;
			}
		});
        speedArbIssuerUndParams.setPostUpdateHandler(new IssuerUndInputParams.PostUpdateHandler() {
			
			@Override
			public void onUpdatedUndTradeVolThreshold(IssuerUndInputParams params) {
				LOG.info("User updated und trade vol threshold: secCode {}, value {}", getSecurity().code(), box(params.undTradeVolThreshold()));
			}
		});
	}
	
	private void logInitialParameters() {
        LOG.info("Initial mm bid size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.mmBidSize()));
        LOG.info("Initial mm ask size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.mmAskSize()));
        LOG.info("Initial base order size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.baseOrderSize()));
        LOG.info("Initial max order size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.maxOrderSize()));
        LOG.info("Initial current order size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.currentOrderSize()));
        LOG.info("Initial order size multiplier: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.orderSizeMultiplier()));
        LOG.info("Initial order increment: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.orderSizeIncrement()));
        LOG.info("Initial order size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.orderSize()));
        LOG.info("Initial profit run threshold: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.runTicksThreshold()));
        LOG.info("Initial tick sensitivity threshold: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.tickSensitivityThreshold()));
        LOG.info("Initial stop loss trigger: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.stopLossTrigger()));
        LOG.info("Initial allowed max spread: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.allowedMaxSpread()));
        LOG.info("Initial turnover making size: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.turnoverMakingSize()));
        LOG.info("Initial turnover making period: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.turnoverMakingPeriod()));
        LOG.info("Initial ban period to down vol: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.banPeriodToDownVol()));
        LOG.info("Initial ban period to turnover making: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.banPeriodToTurnoverMaking()));
        LOG.info("Initial spread observation period: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.spreadObservationPeriod()));
        LOG.info("Initial market outlook: secCode {}, value {}", getSecurity().code(), m_speedArbWrtParams.marketOutlook().name());
        LOG.info("Initial sell on vol down: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.sellOnVolDown()));
        LOG.info("Initial issuer max lag: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.issuerMaxLag()));
        LOG.info("Initial stop profit: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.stopProfit()));
        LOG.info("Initial selling ban period: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.sellingBanPeriod()));
        LOG.info("Initial holding period: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.holdingPeriod()));
        LOG.info("Initial allow stoploss on flashing bid: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.allowStopLossOnFlashingBid()));
        LOG.info("Initial reset stoploss on vol down: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.resetStopLossOnVolDown()));
        LOG.info("Initial default pricing mode: secCode {}, value {}", getSecurity().code(), m_speedArbWrtParams.defaultPricingMode());
        LOG.info("Initial strategy trigger type: secCode {}, value {}", getSecurity().code(), m_speedArbWrtParams.strategyTriggerType());
        LOG.info("Initial issuer lag cap: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.issuerMaxLagCap()));
        LOG.info("Initial tick buffer: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.tickBuffer()));
        LOG.info("Initial manual order ticks from enter price: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.manualOrderTicksFromEnterPrice()));
        LOG.info("Initial sell at quick profit: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.sellAtQuickProfit()));
        LOG.info("Initial stop loss tick buffer: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.stopLossTickBuffer()));
        LOG.info("Initial wide spread buffer: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.wideSpreadBuffer()));
        LOG.info("Initial ban period to sell on vol down: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.sellOnVolDownBanPeriod()));
        LOG.info("Initial velocity threshold: secCode {}, value {}",  getSecurity().code(), box(m_speedArbUndParams.velocityThreshold()));
        LOG.info("Initial use hold bid ban: secCode {}, value {}",  getSecurity().code(), box(m_speedArbWrtParams.useHoldBidBan()));
        LOG.info("Initial outstanding threshold: secCode {}, value {}",  getSecurity().code(), box(m_speedArbWrtParams.tradesVolumeThreshold()));
        LOG.info("Initial order size remainder: secCode {}, value {}",  getSecurity().code(), box(m_speedArbWrtParams.orderSizeRemainder()));
        LOG.info("Initial safe bid level buffer: secCode {}, value {}", getSecurity().code(), box(m_speedArbWrtParams.safeBidLevelBuffer()));
        LOG.info("Initial und trade vol threshold: secCode {}, value {}", getSecurity().code(), box(m_speedArbIssuerUndParams.undTradeVolThreshold()));
	}
	
	@Override
	public void start() throws Exception {
	    logInitialParameters();
        m_undSignalGenerator.start();
        m_wrtSignalGenerator.start();
        m_strategySignalHandler.start();
	}

	@Override
	public void reset() throws Exception {
		m_undSignalGenerator.reset();
		m_wrtSignalGenerator.reset();
		m_strategySignalHandler.reset();
	}

    @Override
    public StrategyStatusType getStatus() {
        return m_speedArbWrtParams.status();
    }
    
    @Override
    public void pendingSwitchOn() {
        pendingSwitchOn = true;
        LOG.info("Pending switching on SpeedArb strategy: secCode {}, trigger seqNum {}", this.getSecurity().code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
    }

    @Override
    public void cancelSwitchOn() {
        if (pendingSwitchOn) {
            pendingSwitchOn = false;
            LOG.info("Canceled switching on SpeedArb strategy: secCode {}, trigger seqNum {}", this.getSecurity().code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
    }

    @Override
    public void proceedSwitchOn() throws Exception {
        if (pendingSwitchOn) {
            switchOn();
        }
        else {
            LOG.info("Cannot proceed to switch on SpeedArb strategy: secCode {}, trigger seqNum {}", this.getSecurity().code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
    }

    @Override
    public void switchOn() throws Exception {
        switchOnInternal();
        pendingSwitchOn = false;
    }

    @Override
    public void switchOff() throws Exception {
        switchOffInternal();
        cancelSwitchOn();
    }

    @Override
    public void switchOff(final StrategyExitMode exitMode) throws Exception {
        switchOffInternal(exitMode);
        cancelSwitchOn();
    }

    protected void switchOnInternal() throws Exception {
        LOG.info("Switched on SpeedArb strategy: secCode {}, trigger seqNum {}", this.getSecurity().code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_strategySignalHandler.onSwitchedOn();
    }

    protected void switchOffInternal() throws Exception {
        LOG.info("Switched off SpeedArb strategy: secCode {}, exitMode {}, trigger seqNum {}", this.getSecurity().code(), m_speedArbParams.exitMode(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_strategySignalHandler.onSwitchedOff(m_speedArbParams.exitMode());
    }
    
    protected void switchOffInternal(final StrategyExitMode exitMode) throws Exception {
        LOG.info("Switched off SpeedArb strategy: secCode {}, exitMode {}, trigger seqNum {}", this.getSecurity().code(), exitMode, box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_strategySignalHandler.onSwitchedOff(exitMode);
    }
    
    @Override
    public void captureProfit() throws Exception {
        m_strategySignalHandler.onCaptureProfit();
    }
    
    @Override
    public void placeSellOrder() throws Exception {
        m_strategySignalHandler.onPlaceSellOrder();
    }
    
    @Override
    public boolean isOn() {
        return m_strategySignalHandler.isOn();
    }

}
