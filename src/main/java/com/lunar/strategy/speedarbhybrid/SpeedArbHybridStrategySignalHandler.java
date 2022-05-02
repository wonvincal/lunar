package com.lunar.strategy.speedarbhybrid;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Issuer;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyExplainType;
import com.lunar.message.io.sbe.StrategyStatusType;
import com.lunar.pricing.BucketPricer;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.AllowAllTriggerGenerator;
import com.lunar.strategy.DeltaLimitAlertGenerator;
import com.lunar.strategy.DeltaLimitAlertHandler;
import com.lunar.strategy.IssuerResponseTimeGenerator;
import com.lunar.strategy.IssuerResponseTimeHandler;
import com.lunar.strategy.OrderStatusReceivedHandler;
import com.lunar.strategy.StrategyControlHandler;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.TriggerGenerator;
import com.lunar.strategy.TurnoverMakingSignalGenerator;
import com.lunar.strategy.TurnoverMakingSignalHandler;
import com.lunar.strategy.VelocityTriggerGenerator;
import com.lunar.strategy.VelocityTriggerHandler;
import com.lunar.strategy.parameters.BucketOutputParams;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.statemachine.ConcreteState;
import com.lunar.strategy.statemachine.EventTranslator;
import com.lunar.strategy.statemachine.StateMachine;
import com.lunar.strategy.statemachine.StateMachineBuilder;
import com.lunar.util.LongInterval;

public class SpeedArbHybridStrategySignalHandler implements OrderStatusReceivedHandler, StrategyControlHandler, TurnoverMakingSignalHandler, VelocityTriggerHandler, IssuerResponseTimeHandler, DeltaLimitAlertHandler {
    static final Logger LOG = LogManager.getLogger(SpeedArbHybridStrategySignalHandler.class);
    
    public static boolean BT_COMPARISON_MODE = false;
    
    static final private int LARGE_WARRANT_PRICE = 250;
    static final private int VERY_LARGE_WARRANT_PRICE = 500;
    static final private int EXIT_LEVEL_ALLOWANCE = 3;
    static final private long MIN_ISSUER_MAX_LAG = 10_000_000L;
    static final private long MIN_ISSUER_WIDE_TIME = 30_000_000_000L;
    static final private long QUICK_PROFIT_TIME = 100_000_000L;
    static final private long DELTA_LIMIT_EFFECT_TIME = 1_000_000_000L;
    static final private long LARGE_OUTSTANDING_EFFECT_TIME = 1_000_000_000L;
    public class StateIds {
        static public final int NO_POSITION_HELD = 0;
        static public final int BUYING_POSITION = 1;
        static public final int POSITION_HELD = 2;
        static public final int SELLING_POSITION = 3;
        static public final int OFF = 4;
    }

    public class EventIds {
        static public final int STOCK_BID_UP_SIZE_THRESHOLD_TIGHT = 1;
        static public final int STOCK_ASK_DOWN_SIZE_THRESHOLD_TIGHT = 2;
        static public final int STOCK_ASK_UP_SPEED_THRESHOLD_WIDE = 3;
        static public final int STOCK_BID_DOWN_SPEED_THRESHOLD_WIDE = 4;
        static public final int WARRANT_TICK_RECEIVED = 5;
        static public final int STOCK_SPOT_UPDATED = 7;		
        static public final int ORDER_STATUS_UPDATED = 8;
        static public final int STRATEGY_SWITCHED_ON = 9;
        static public final int STRATEGY_SWITCHED_OFF = 10;
        static public final int CAPTURE_PROFIT = 11;
        static public final int ISSUER_DOWN_VOL_FROM_UNDERLYING_TICK = 12;
        static public final int TURNOVER_MAKING = 13;
        static public final int PLACE_SELL_ORDER = 14;
        static public final int PRICING_MODE_UPDATED = 15;
        static public final int ISSUER_DOWN_VOL_FOR_STANDBY_PRICER = 16;
        static public final int ISSUER_SMOOTHING_COMPLETED = 17;
        static public final int NON_DOWN_VOL_VIOLATION = 18;
        static public final int ISSUER_DOWN_VOL_FROM_WARRANT_TICK = 19;
        static public final int MARKET_TRADE_RECEIVED = 20;
        static public final int ALLOW_STOP_LOSS_ON_WIDE_SPREAD_UPDATED = 21;
        static public final int IGNORE_MM_SIZE_ON_SELL_UPDATED = 22;
        static public final int DELTA_LIMIT_ALERT_RECEIVED = 23;
    }

    private class TransitionIds {
        static public final int BUY_POSITION = 0;
        static public final int SELL_POSITION = 1;
        static public final int PROFIT_RUN = 2;
        static public final int ORDER_FILLED = 3;
        static public final int ORDER_NOT_FILLED = 4;
        static public final int ENTER_STRATEGY_WITH_POSITION = 5;
        static public final int ENTER_STRATEGY_WITHOUT_POSITION = 6;
        static public final int EXIT_STRATEGY = 7;
    }
    
    private interface CallPutBridge {
        boolean isTriggered();
        int getTriggerStrength();
        MarketOutlookType getDesirableOutlookType();
        MarketOutlookType getUndesirableOutlookType();
        long getBucketBegin(final LongInterval interval);
        long getPrevBidSpot();
        boolean isSpotHigherThan(final long value);
        boolean isSpotHigherThanOrEqualTo(final long value);
        boolean isSpotLesserThan(final long value);
        void updateBestSpot();
        long getSpotChangeRequired(final int priceChange);
        long getHigherStopLoss(final long value1, final long value2);
        long getLowerStopLoss(final long value1, final long value2);
        long lowerStopLoss(final long targetStopLoss, final long amount);
        boolean canUpdateStopLoss(final long targetStopLoss);
        boolean getOverlapAndNextIntervalByUndSpot(long undSpot, LongInterval outInterval, LongInterval outNextInterval);
        long getUnderlyingBidSpot();
        long getAvailableDeltaShares(final long currentDeltaShares, final long deltaSharesThreshold);
    }
    
    private class CallBridge implements CallPutBridge {
        @Override
        public boolean isTriggered() {
            return m_triggerGenerator.isTriggeredForCall();
        }
        
        @Override
        public int getTriggerStrength() {
            return m_triggerGenerator.getTriggerStrengthForCall();
        }

        @Override
        public MarketOutlookType getDesirableOutlookType() {
            return MarketOutlookType.BULLISH;
        }
        
        @Override
        public MarketOutlookType getUndesirableOutlookType() {
            return MarketOutlookType.BEARISH;
        }
        
        @Override
        public long getBucketBegin(final LongInterval interval) {
            return interval.begin();
        }
        
        @Override
        public long getPrevBidSpot() {
            return m_undSignalGenerator.getPrevBidPrice() * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        }
        
        @Override
        public boolean isSpotHigherThan(final long value) {
            return m_wrtSignalGenerator.getSpotPrice() > value;
        }
        
        @Override
        public boolean isSpotHigherThanOrEqualTo(final long value) {
            return m_wrtSignalGenerator.getSpotPrice() >= value;
        }

        @Override
        public boolean isSpotLesserThan(final long value) {
            return m_wrtSignalGenerator.getSpotPrice() < value;
        }
        
        @Override
        public void updateBestSpot() {
            m_bestSpot = Math.max(m_bestSpot, m_wrtSignalGenerator.getSpotPrice());                  
        }
        
        @Override
        public long getSpotChangeRequired(final int priceChange) {
            final float adjustedDelta = m_spreadTableScaleFormulaBridge.calcAdjustedDelta(m_wrtSignalGenerator.getSpotPrice(), m_speedArbWrtParams.greeks());
            return m_spreadTableScaleFormulaBridge.calcSpotChangeForPriceChangeForCall(priceChange, m_security.convRatio(), adjustedDelta, m_speedArbWrtParams.greeks());
        }

        @Override
        public long getHigherStopLoss(final long value1, final long value2) {
            return Math.max(value1, value2);
        }

        @Override
        public long getLowerStopLoss(final long value1, final long value2) {
            return Math.min(value1, value2);
        }

        @Override
        public long lowerStopLoss(final long targetStopLoss, final long amount) {
            return targetStopLoss - amount;
        }
        
        @Override
        public boolean canUpdateStopLoss(final long targetStopLoss) {
            return targetStopLoss >= m_speedArbWrtParams.stopLoss();
        }
        
        @Override
        public boolean getOverlapAndNextIntervalByUndSpot(long undSpot, LongInterval outInterval, LongInterval outNextInterval){
        	BucketPricer bucketPricer = m_wrtSignalGenerator.getBucketPricer();
        	if (bucketPricer != null){
                return bucketPricer.getOverlapAndGreaterIntervalByUndSpot(m_wrtSignalGenerator.getSpotPrice(), m_bucketUpdateOverlapInterval, m_bucketUpdateGreaterInterval);
            }        	
        	return false;
        }

        @Override
        public long getUnderlyingBidSpot() {
            return m_undSignalGenerator.getBidPrice() * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        }
        
        @Override
        public long getAvailableDeltaShares(final long currentDeltaShares, final long deltaSharesThreshold) {
            return deltaSharesThreshold - currentDeltaShares;
        }

    }
    
    private class PutBridge implements CallPutBridge {
        @Override
        public boolean isTriggered() {
            return m_triggerGenerator.isTriggeredForPut();
        }
        
        @Override
        public int getTriggerStrength() {
            return m_triggerGenerator.getTriggerStrengthForPut();
        }

        @Override
        public MarketOutlookType getDesirableOutlookType() {
            return MarketOutlookType.BEARISH;
        }
        
        @Override
        public MarketOutlookType getUndesirableOutlookType() {
            return MarketOutlookType.BULLISH;
        }
        
        @Override
        public long getBucketBegin(final LongInterval interval) {
            return interval.endExclusive() - 1;
        }
        
        @Override
        public long getPrevBidSpot() {
            return m_undSignalGenerator.getPrevAskPrice() * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        }
        
        @Override
        public boolean isSpotHigherThan(final long value) {
            return m_wrtSignalGenerator.getSpotPrice() < value;
        }

        @Override
        public boolean isSpotHigherThanOrEqualTo(final long value) {
            return m_wrtSignalGenerator.getSpotPrice() <= value;
        }
        
        @Override
        public boolean isSpotLesserThan(final long value) {
            return m_wrtSignalGenerator.getSpotPrice() > value;
        }
        
        @Override
        public void updateBestSpot() {
            if (m_wrtSignalGenerator.getSpotPrice() > 0) {
                m_bestSpot = m_bestSpot == 0 ? m_wrtSignalGenerator.getSpotPrice() : Math.min(m_bestSpot, m_wrtSignalGenerator.getSpotPrice());
            }
        }
        
        @Override
        public long getSpotChangeRequired(final int priceChange) {
            final float adjustedDelta = m_spreadTableScaleFormulaBridge.calcAdjustedDelta(m_wrtSignalGenerator.getSpotPrice(), m_speedArbWrtParams.greeks());
            return m_spreadTableScaleFormulaBridge.calcSpotChangeForPriceChangeForPut(priceChange, m_security.convRatio(), adjustedDelta, m_speedArbWrtParams.greeks());
        }

        @Override
        public long getHigherStopLoss(final long value1, final long value2) {
            return Math.min(value1, value2);
        }
        
        @Override
        public long getLowerStopLoss(final long value1, final long value2) {
            return Math.max(value1, value2);
        }

        @Override
        public long lowerStopLoss(final long targetStopLoss, final long amount) {
            return targetStopLoss + amount;
        }
        
        @Override
        public boolean canUpdateStopLoss(final long targetStopLoss) {
            return targetStopLoss <= m_speedArbWrtParams.stopLoss();
        }
        
        @Override
        public boolean getOverlapAndNextIntervalByUndSpot(long undSpot, LongInterval outInterval, LongInterval outNextInterval){
        	BucketPricer bucketPricer = m_wrtSignalGenerator.getBucketPricer();
        	if (bucketPricer != null){
                return bucketPricer.getOverlapAndSmallerIntervalByUndSpot(m_wrtSignalGenerator.getSpotPrice(), m_bucketUpdateOverlapInterval, m_bucketUpdateGreaterInterval);
            }        	
        	return false;
        }

        @Override
        public long getUnderlyingBidSpot() {
            return m_undSignalGenerator.getAskPrice() * ServiceConstant.WEIGHTED_AVERAGE_SCALE;
        }
        
        @Override
        public long getAvailableDeltaShares(final long currentDeltaShares, final long deltaSharesThreshold) {
            return deltaSharesThreshold + currentDeltaShares;
        }

    }
    
    private interface StrategyModeBridge {
        boolean isAvailable();
        int priorityLevel();
        StrategyStatusType defaultStrategyStatus();
        boolean sellOnHitExitLevel();
        boolean offWhenExitPosition();
        boolean allowStopLossOnWideSpread();
        boolean canReviseStopLoss();
        int onPositionBought();
        int onPositionNotBought();    
        int onPositionFullySold();
        int onPositionNotFullySold();
        int onStrategyModeEntered() throws Exception;
        int onStrategyModeEnteredOnPendingBuy() throws Exception;
        int onStrategyModeEnteredOnPendingSell() throws Exception;
        int onStrategyModeEnteredOnPosition() throws Exception;
        void sellPosition();
        int onWarrantPriceUpdatedOnPosition() throws Exception ;
        int onSpotUpdatedOnPosition() throws Exception ;
    }
    
    private class NormalStrategyMode implements StrategyModeBridge {
        @Override
        public boolean isAvailable() {
            return true;
        }
        
        @Override
        public int priorityLevel() {
            return -1;
        }
        
        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.ACTIVE;
        }
        
        @Override
        public boolean sellOnHitExitLevel() {
            return false;
        }
        
        @Override
        public boolean offWhenExitPosition() {
            return false;
        }
        
        @Override
        public boolean allowStopLossOnWideSpread() {
            return m_speedArbWrtParams.allowStopLossOnWideSpread() && (m_wrtSignalGenerator.getMMBidLevel() > 0 || m_speedArbWrtParams.ignoreMmSizeOnSell());
        }
        
        @Override
        public boolean canReviseStopLoss() {
            return true;
        }
        
        @Override
        public int onPositionBought() {
            return TransitionIds.ORDER_FILLED;
        }
        
        @Override
        public int onPositionNotBought() {
            return TransitionIds.ORDER_NOT_FILLED;
        }
        
        @Override
        public int onPositionFullySold() {
            return TransitionIds.ORDER_FILLED;
        }
        
        @Override
        public int onPositionNotFullySold() {
            return TransitionIds.ORDER_NOT_FILLED;
        }

        @Override 
        public int onStrategyModeEntered() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingBuy() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingSell() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPosition() throws Exception {
            return -1;
        }
        
        @Override
        public void sellPosition() {
            sell();
        }
        
        @Override
        public int onWarrantPriceUpdatedOnPosition() throws Exception {
            return handleWarrantPriceUpdateOnPosition();
        }

        @Override
        public int onSpotUpdatedOnPosition() throws Exception {
            return handleSpotUpdateOnPosition();
        }
    }
    
    abstract private class ExitStrategyMode implements StrategyModeBridge {
        @Override
        public boolean isAvailable() {
            return isOn();
        }

        @Override
        public boolean sellOnHitExitLevel() {
            return false;
        }
        
        @Override
        public boolean offWhenExitPosition() {
            return true;
        }
        
        @Override
        public boolean allowStopLossOnWideSpread() {
            if (BT_COMPARISON_MODE) {
                return false;
            }            
            return (m_wrtSignalGenerator.getMMBidLevel() > 0 || m_speedArbWrtParams.ignoreMmSizeOnSell());
        }

        @Override
        public boolean canReviseStopLoss() {
            return true;
        }

        @Override
        public int onPositionNotBought() {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override
        public int onPositionFullySold() {
            return TransitionIds.EXIT_STRATEGY;
        }

        @Override 
        public int onStrategyModeEntered() throws Exception {
            return -1;
        }

        @Override
        public int onWarrantPriceUpdatedOnPosition() throws Exception {
            return handleWarrantPriceUpdateOnPosition();
        }

        @Override
        public int onSpotUpdatedOnPosition() throws Exception {
            return handleSpotUpdateOnPosition();
        }
    }

    private class ErrorStrategyMode extends ExitStrategyMode {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public int priorityLevel() {
            return 1;
        }
        
        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.ERROR;
        }
        
        @Override
        public int onPositionBought() {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override
        public int onPositionNotFullySold() {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override 
        public int onStrategyModeEnteredOnPendingBuy() throws Exception {
            return TransitionIds.EXIT_STRATEGY;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingSell() throws Exception {
            return TransitionIds.EXIT_STRATEGY;
        }

        @Override 
        public int onStrategyModeEnteredOnPosition() throws Exception {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override
        public void sellPosition() {
        }
    }
    
    private class NoExitStrategyMode extends ExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 2;
        }
        
        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.ACTIVE;
        }
        
        @Override
        public int onPositionBought() {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override
        public int onPositionNotFullySold() {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override 
        public int onStrategyModeEnteredOnPendingBuy() throws Exception {
            return TransitionIds.EXIT_STRATEGY;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingSell() throws Exception {
            return TransitionIds.EXIT_STRATEGY;
        }

        @Override
        public int onStrategyModeEnteredOnPosition() throws Exception {
            return TransitionIds.EXIT_STRATEGY;
        }
        
        @Override
        public void sellPosition() {
        }
    }

    private class StrategyExitStrategyMode extends ExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 2;
        }

        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.STRATEGY_EXITING;
        }
        
        @Override
        public int onPositionBought() {
            return TransitionIds.ORDER_FILLED;
        }
        
        @Override
        public int onPositionNotFullySold() {
            return TransitionIds.ORDER_NOT_FILLED;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingBuy() throws Exception {
            return -1;
        }
        
        @Override 
        public int onStrategyModeEnteredOnPendingSell() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPosition() throws Exception {
            adjustSafeBidBuffer();
            return -1;
        }
        
        @Override
        public void sellPosition() {
            sell();
        }
    }
   
    private class ScoreBoardExitStrategyMode extends StrategyExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 4;
        }
        
        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.SCOREBOARD_EXITING;
        }

        @Override
        public boolean allowStopLossOnWideSpread() {
            return m_speedArbWrtParams.allowStopLossOnWideSpread() && (m_wrtSignalGenerator.getMMBidLevel() > 0 || m_speedArbWrtParams.ignoreMmSizeOnSell());
        }
    }
    
    private class ClosingStrategyExitStrategyMode extends StrategyExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 3;
        }

        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.CLOSING_STRATEGY_EXITING;
        }
        
        @Override
        public boolean sellOnHitExitLevel() {
            return true;
        }
        
        @Override
        public boolean allowStopLossOnWideSpread() {
            if (BT_COMPARISON_MODE) {
                return false;
            }            
            return (m_wrtSignalGenerator.getMMBidLevel() > 0 || m_speedArbWrtParams.ignoreMmSizeOnSell());
        }
        
    }
    
    private class PriceCheckExitStrategyMode extends ExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 2;
        }

        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.PRICE_CHECK_EXITING;
        }
        
        @Override
        public int onPositionBought() {
            return TransitionIds.ORDER_FILLED;
        }
        
        @Override
        public int onPositionNotFullySold() {
            return TransitionIds.ORDER_NOT_FILLED;
        }
         
        @Override 
        public int onStrategyModeEnteredOnPendingBuy() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingSell() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPosition() throws Exception {
            adjustSafeBidBuffer();
            if (m_orderService.canTrade() && isPriceSafeToExit(false)) {
                m_strategyExplain.strategyExplain(StrategyExplainType.EXIT_STRATEGY_SELL_SIGNAL);
                return TransitionIds.SELL_POSITION;
            }
            return -1;
        }
        
        @Override
        public void sellPosition() {
            sellToExit();
        }
        
        @Override
        public int onWarrantPriceUpdatedOnPosition() throws Exception {
            int transition = handleWarrantPriceUpdateOnPosition();
            if (transition == -1 && m_orderService.canTrade() && isPriceSafeToExit(false)) {
                return tryEnterSellToExitPositionState(StrategyExplainType.EXIT_STRATEGY_SELL_SIGNAL, transition);
            }
            return -1;
        }

        @Override
        public int onSpotUpdatedOnPosition() throws Exception {
            int transition = handleSpotUpdateOnPosition();
            if (transition == -1 && m_orderService.canTrade() && isPriceSafeToExit(false)) {
                return tryEnterSellToExitPositionState(StrategyExplainType.EXIT_STRATEGY_SELL_SIGNAL, transition);
            }
            return -1;                    
        }
    }
    
    private class ClosingPriceCheckExitStrategyMode extends PriceCheckExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 3;
        }

        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.CLOSING_PRICE_CHECK_EXITING;
        }
        
        @Override
        public boolean sellOnHitExitLevel() {
            return true;
        }
        
        @Override
        public boolean allowStopLossOnWideSpread() {
            if (BT_COMPARISON_MODE) {
                return false;
            }            
            return (m_wrtSignalGenerator.getMMBidLevel() > 0 || m_speedArbWrtParams.ignoreMmSizeOnSell());
        }
        
        @Override
        public int onStrategyModeEnteredOnPosition() throws Exception {
            final int nextTransition = super.onStrategyModeEnteredOnPosition();
            if (nextTransition == TransitionIds.SELL_POSITION && doNotSell()) {
                return -1;
            }
            return nextTransition;
        }
        
    }
    
    private class NoCheckExitStrategyMode extends ExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 2;
        }

        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.NO_CHECK_EXITING;
        }
        
        @Override
        public int onPositionBought() {
            return TransitionIds.ORDER_FILLED;
        }
        
        @Override
        public int onPositionNotFullySold() {
            return TransitionIds.ORDER_NOT_FILLED;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingBuy() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPendingSell() throws Exception {
            return -1;
        }

        @Override 
        public int onStrategyModeEnteredOnPosition() throws Exception {
            adjustSafeBidBuffer();
            if (m_orderService.canTrade() && m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.safeBidPrice()) {
                m_strategyExplain.strategyExplain(StrategyExplainType.EXIT_STRATEGY_SELL_SIGNAL);
                return TransitionIds.SELL_POSITION;
            }
            return -1;
        }
        
        @Override
        public void sellPosition() {
            sellToExitNoCheck();
        }
        
        @Override
        public int onWarrantPriceUpdatedOnPosition() throws Exception {
            int transition = handleWarrantPriceUpdateOnPosition();
            if (transition == -1 && m_orderService.canTrade() && m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.safeBidPrice()) {
                return tryEnterSellToExitPositionState(StrategyExplainType.EXIT_STRATEGY_SELL_SIGNAL, transition);
            }
            return -1;
        }

        @Override
        public int onSpotUpdatedOnPosition() throws Exception {
            int transition = handleSpotUpdateOnPosition();
            if (transition == -1 && m_orderService.canTrade() && m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.safeBidPrice()) {
                return tryEnterSellToExitPositionState(StrategyExplainType.EXIT_STRATEGY_SELL_SIGNAL, transition);
            }
            return -1;                    
        }
    }
    
    private class SemiManualExitStrategyMode extends StrategyExitStrategyMode {
        @Override
        public int priorityLevel() {
            return 2;
        }

        @Override
        public StrategyStatusType defaultStrategyStatus() {
            return StrategyStatusType.SEMI_MANUAL_EXITING;
        }

        @Override
        public boolean canReviseStopLoss() {
            return false;
        }
    }
    
    private class StrategyModeBridgeFactory {
        private final StrategyModeBridge m_normalStrategyMode = new NormalStrategyMode();
        private final StrategyModeBridge m_noExitStrategyMode = new NoExitStrategyMode();
        private final StrategyModeBridge m_errorStrategyMode = new ErrorStrategyMode();
        private final StrategyModeBridge m_strategyExitStrategyMode = new StrategyExitStrategyMode();
        private final StrategyModeBridge m_scoreBoardExitStrategyMode = new ScoreBoardExitStrategyMode();
        private final StrategyModeBridge m_closingStrategyExitStrategyMode = new ClosingStrategyExitStrategyMode();
        private final StrategyModeBridge m_priceCheckExitStrategyMode = new PriceCheckExitStrategyMode();
        private final StrategyModeBridge m_closingPriceCheckExitStrategyMode = new ClosingPriceCheckExitStrategyMode();
        private final StrategyModeBridge m_noCheckExitStrategyMode = new NoCheckExitStrategyMode();
        private final StrategyModeBridge m_semiManualStrategyMode = new SemiManualExitStrategyMode();
        
        public StrategyModeBridge getStrategyModeBridge(final StrategyExitMode exitMode) {
            switch (exitMode) {
            case ERROR:
                return m_errorStrategyMode;
            case NO_EXIT:
                return m_noExitStrategyMode;
            case STRATEGY_EXIT:
                return m_strategyExitStrategyMode;
            case SCOREBOARD_EXIT:
                return m_scoreBoardExitStrategyMode;
            case PRICE_CHECK_EXIT:
                return m_priceCheckExitStrategyMode;
            case NO_CHECK_EXIT:
                return m_noCheckExitStrategyMode;
            case CLOSING_STRATEGY_EXIT:
                return m_closingStrategyExitStrategyMode;
            case CLOSING_PRICE_CHECK_EXIT:
                return m_closingPriceCheckExitStrategyMode;
            case SEMI_MANUAL_EXIT:
                return m_semiManualStrategyMode;
            default:
                return m_normalStrategyMode;
            }
        }
    }

    private final StrategyType m_strategyType;
    private final StrategySecurity m_security;
    private final StrategySecurity m_underlying;
    private final Issuer m_issuer;
    
    private final SpeedArbHybridTriggerController m_triggerController;
    private final TurnoverMakingSignalGenerator m_turnoverMakingSignalGenerator;    
    private final DeltaLimitAlertGenerator m_deltaLimitAlertGenerator;
    private final IssuerResponseTimeGenerator m_issuerResponseLagMonitor;
    private final StateMachine m_stateMachine;
    private final SpeedArbHybridUndSignalGenerator m_undSignalGenerator;
    private final SpeedArbHybridWrtSignalGenerator m_wrtSignalGenerator;
    private final StrategyOrderService m_orderService;
    private final StrategyInfoSender m_strategyInfoSender;
    private final SpreadTable m_spreadTable;
    private final SpeedArbHybridExplain m_strategyExplain;  
    private final CallPutBridge m_callPutBridge;
    private final SpreadTableScaleFormulaBridge m_spreadTableScaleFormulaBridge;

    // configurations
    @SuppressWarnings("unused")
    private final GenericStrategyTypeParams m_speedArbParams;
    private final GenericWrtParams m_speedArbWrtParams;
    private final GenericUndParams m_speedArbUndParams;
    private final GenericIssuerParams m_speedArbIssuerParams;
    private final GenericIssuerUndParams m_speedArbIssuerUndParams;
    private final BucketOutputParams m_bucketOutputParams;

    private StrategyModeBridgeFactory m_strategyModeBridgeFactory;
    private StrategyModeBridge m_strategyModeBridge;

    private int m_highWarrantBid;
    private long m_bestSpot;
    private int m_targetSellPrice = Integer.MAX_VALUE;
    private int m_sellPrice;
    private long m_sellQty;
    private int m_sellFlags;
    
    private long m_turnoverMakingTime = 0;
    private int m_turnoverMakingPrice = 0;
    
    private long m_buyBanUntilNanoOfDay;
    private long m_sellBanUntilNanoOfDay;
    private long m_sellOnVolDownBanUntilNanoOfDay;
    private long m_quickProfitUntilNanoOfDay;
    
    private LongInterval m_intervalByPrice = LongInterval.of();
    private LongInterval m_intervalBySpot = LongInterval.of();
    private LongInterval m_prevReportedOverlapInterval = LongInterval.of();
    private LongInterval m_prevReportedGreaterInterval = LongInterval.of();
    private LongInterval m_bucketUpdateOverlapInterval = LongInterval.of();
    private LongInterval m_bucketUpdateGreaterInterval = LongInterval.of();
    
    private TriggerGenerator m_triggerGenerator;
    
    private long m_targetStopLoss;
    private long m_standByTargetStopLoss;
    private PricingMode m_targetStopLossPricingMode = PricingMode.UNKNOWN;

    private long m_orderStatusReceivedTime;
    private OrderRequestRejectType m_orderRequestRejectType;
    private int m_ourTradedPrice;
    
    private boolean m_reentryBan = true;
    private int m_mmBidLevelAtBuy = 0;
    private long m_pendingDeltaShares = 0;
    private int m_consecutiveWins = 0;
    
    private long m_tradesVolumeAtBuy;

    private int m_maxCurrentOrderSize;
    private int m_largePriceOrderSize;
    private int m_veryLargePriceOrderSize;
    private int m_maxLargePriceOrderSize;
    private int m_maxVeryLargePriceOrderSize;

    private long m_largePriceTradesVolumeThreshold;
    private long m_veryLargePriceTradesVolumeThreshold;
    
    private int m_orderSizeRemainder;
    private int m_largePriceOrderSizeRemainder;
    private int m_veryLargePriceOrderSizeRemainder;
    
    private long m_deltaLimitExceedExpiryTime;
    private long m_largeOutstandingExceedExpiryTime;
    
    private boolean m_hasStarted = false;

    
    SpeedArbHybridStrategySignalHandler(final StrategyType strategyType, final StrategySecurity security,
            final SpeedArbHybridUndSignalGenerator undSignalGenerator,
            final SpeedArbHybridWrtSignalGenerator wrtSignalGenerator,
            final SpreadTableScaleFormulaBridge spreadTableScaleFormulaBridge,
            final StrategyOrderService orderService,
            final StrategyInfoSender strategyInfoSender,
            final GenericWrtParams speedArbWrtParams,
            final GenericStrategyTypeParams speedArbParams,
            final GenericUndParams speedArbUndParams,
            final GenericIssuerParams speedArbIssuerParams,
            final GenericIssuerUndParams speedArbIssuerUndParams,
            final SpeedArbHybridTriggerController triggerController,
            final IssuerResponseTimeGenerator issuerResponseLagMonitor,
            final TurnoverMakingSignalGenerator turnoverMakingSignalGenerator,
            final DeltaLimitAlertGenerator deltaLimitAlertGenerator,
            final BucketOutputParams bucketOutputParams) {
        m_strategyType = strategyType;
        m_strategyModeBridgeFactory = new StrategyModeBridgeFactory();
        m_strategyModeBridge = m_strategyModeBridgeFactory.getStrategyModeBridge(StrategyExitMode.NULL_VAL);
        m_security = security;
        m_underlying = security.underlying();
        m_issuer = security.issuer();
        m_undSignalGenerator = undSignalGenerator;
        m_wrtSignalGenerator = wrtSignalGenerator;
        m_spreadTableScaleFormulaBridge = spreadTableScaleFormulaBridge;
        m_orderService = orderService;
        m_spreadTable = m_security.spreadTable();
        m_speedArbWrtParams = speedArbWrtParams;
        m_speedArbParams = speedArbParams;
        m_speedArbUndParams = speedArbUndParams;
        m_speedArbIssuerParams = speedArbIssuerParams;
        m_speedArbIssuerUndParams = speedArbIssuerUndParams;
        m_bucketOutputParams = bucketOutputParams;
        m_strategyInfoSender = strategyInfoSender;
        m_strategyExplain = new SpeedArbHybridExplain(m_security);  
        m_callPutBridge = m_security.putOrCall().equals(PutOrCall.CALL) ? new CallBridge() : new PutBridge();
        m_triggerController = triggerController;
        m_issuerResponseLagMonitor = issuerResponseLagMonitor;
        m_turnoverMakingSignalGenerator = turnoverMakingSignalGenerator;
        m_deltaLimitAlertGenerator = deltaLimitAlertGenerator;
        m_stateMachine = createStateMachineBuilder().buildMachine();
    }
    
    private void createStates(final StateMachineBuilder builder) {
        builder.registerState(new ConcreteState(StateIds.NO_POSITION_HELD));
        builder.registerState(new ConcreteState(StateIds.BUYING_POSITION, new ConcreteState.BeginStateRunnable() {
            @Override
            public void run(int prevState, int transitionId) throws Exception {
                buy();
            }
        }));
        builder.registerState(new ConcreteState(StateIds.POSITION_HELD));
        builder.registerState(new ConcreteState(StateIds.SELLING_POSITION, new ConcreteState.BeginStateRunnable() {
            @Override
            public void run(int prevState, int transitionId) throws Exception {
                m_strategyModeBridge.sellPosition();
            }
        }));
        builder.registerState(new ConcreteState(StateIds.OFF, new ConcreteState.BeginStateRunnable() {            
            @Override
            public void run(int prevState, int transitionId) {
                if (m_speedArbWrtParams.status() != StrategyStatusType.OFF && m_speedArbWrtParams.status() != StrategyStatusType.ERROR) {
                    // update default pricing mode to the current pricing mode
                    if (!m_speedArbWrtParams.pricingMode().equals(m_speedArbWrtParams.defaultPricingMode()) && (m_speedArbWrtParams.pricingMode().equals(PricingMode.MID) || m_speedArbWrtParams.pricingMode().equals(PricingMode.WEIGHTED))) {
                        if (!BT_COMPARISON_MODE) {
                            m_speedArbWrtParams.defaultPricingMode(m_speedArbWrtParams.pricingMode());
                        }
                    }
                    setParamStatusPersist(StrategyStatusType.OFF);                    
                }
                m_strategyModeBridge = m_strategyModeBridgeFactory.getStrategyModeBridge(StrategyExitMode.NULL_VAL);
                //TODO should be ok to continue collecting buckets
                //m_wrtSignalGenerator.disableCollectBuckets();
                printStats();
            }
        }));
    }
    
    private void setupStateNoPositionHeld(final StateMachineBuilder builder) {
        builder.linkStates(StateIds.NO_POSITION_HELD, TransitionIds.BUY_POSITION, StateIds.BUYING_POSITION);
        builder.linkStates(StateIds.NO_POSITION_HELD, TransitionIds.EXIT_STRATEGY, StateIds.OFF);
        
        builder.translateEvent(StateIds.NO_POSITION_HELD, EventIds.STOCK_SPOT_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                final StrategyExplainType strategyExplainType = hasBuyTrigger();
                switch (strategyExplainType) {
                case PREDICTION_BY_BID_BUCKET_BUY_SIGNAL:
                case PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL:
                    setInitialStopLosses(m_cachedAdjustedDeltaC);
                    break;
                case PREDICTION_BY_WAVG_SPOT_BUY_SIGNAL:
                    setInitialStopLossesUsingPrevSpot(m_cachedAdjustedDeltaC);
                    break;
                default:
                    return -1;
                }
                m_issuerResponseLagMonitor.onTriggerUp(m_wrtSignalGenerator.lastTickNanoOfDay());
                m_strategyExplain.strategyExplain(strategyExplainType);
                return TransitionIds.BUY_POSITION;
            }
        });
        builder.translateEvent(StateIds.NO_POSITION_HELD, EventIds.STRATEGY_SWITCHED_OFF, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                return TransitionIds.EXIT_STRATEGY;
            }
        });
    }
    
    private void setupStateBuyingPosition(final StateMachineBuilder builder) {
        builder.linkStates(StateIds.BUYING_POSITION, TransitionIds.ORDER_FILLED, StateIds.POSITION_HELD);
        builder.linkStates(StateIds.BUYING_POSITION, TransitionIds.PROFIT_RUN, StateIds.POSITION_HELD);
        builder.linkStates(StateIds.BUYING_POSITION, TransitionIds.ORDER_NOT_FILLED, StateIds.NO_POSITION_HELD);
        builder.linkStates(StateIds.BUYING_POSITION, TransitionIds.SELL_POSITION, StateIds.SELLING_POSITION);
        builder.linkStates(StateIds.BUYING_POSITION, TransitionIds.EXIT_STRATEGY, StateIds.OFF);
        
        builder.translateEvent(StateIds.BUYING_POSITION, EventIds.ORDER_STATUS_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                clearPendingDeltaShares();
                if (m_security.position() > 0) {
                    m_speedArbWrtParams.enterQuantity((int)m_security.position());
                    if (!BT_COMPARISON_MODE) {
                        int nextTransition = checkForExitOnWarrantPriceUpdate();
                        if (nextTransition != -1) {
                            return nextTransition;
                        }
                    }
                    return m_strategyModeBridge.onPositionBought();
                }
                m_buyBanUntilNanoOfDay = Math.max(m_buyBanUntilNanoOfDay, m_wrtSignalGenerator.lastTickNanoOfDay() + 10_000_000L);
                LOG.debug("Buying ban set due to order reject: secCode {}, banUntil {}, trigger seqNum {}", m_security.code(), box(m_buyBanUntilNanoOfDay), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                return m_strategyModeBridge.onPositionNotBought(); 
            }
        });

        builder.translateEvent(StateIds.BUYING_POSITION, EventIds.WARRANT_TICK_RECEIVED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                if (m_orderService.canTrade() && m_sellBanUntilNanoOfDay > 0) {
                    if (m_wrtSignalGenerator.getMMAskPrice() != m_wrtSignalGenerator.getPrevMMAskPrice() || m_wrtSignalGenerator.getMMBidPrice() != m_wrtSignalGenerator.getPrevMMBidPrice()) {
                        LOG.debug("Selling ban lifted: secCode {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                        m_sellBanUntilNanoOfDay = 0;
                    }
                }
                if (m_reentryBan && m_wrtSignalGenerator.getMMBidLevel() != m_mmBidLevelAtBuy) {
                    m_reentryBan = false;
                }
                m_highWarrantBid = Math.max(m_highWarrantBid, m_wrtSignalGenerator.getBidPrice());
                return -1;
            }
        });
        
        builder.translateEvent(StateIds.BUYING_POSITION, EventIds.MARKET_TRADE_RECEIVED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (updateTradesVolumeAtBuy()) {
                    hasLargeTradeVolumeSignal();
                }
                return -1;
            }
        });

        builder.translateEvent(StateIds.BUYING_POSITION, EventIds.STRATEGY_SWITCHED_OFF, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                return m_strategyModeBridge.onStrategyModeEnteredOnPendingBuy();
            }
        });        

    }
    
    private void broadcastBucketUpdate(){
    	// Find interval
    	if (m_callPutBridge.getOverlapAndNextIntervalByUndSpot(m_wrtSignalGenerator.getSpotPrice(), m_bucketUpdateOverlapInterval, m_bucketUpdateGreaterInterval)){
        	if (!m_prevReportedOverlapInterval.equals(m_bucketUpdateOverlapInterval) || !m_prevReportedGreaterInterval.equals(m_bucketUpdateGreaterInterval)){
        		m_prevReportedOverlapInterval.copyFrom(m_bucketUpdateOverlapInterval);
        		m_prevReportedGreaterInterval.copyFrom(m_bucketUpdateGreaterInterval);
            	m_bucketOutputParams.activeBucketInfo(m_prevReportedOverlapInterval);
            	m_bucketOutputParams.nextBucketInfo(m_prevReportedGreaterInterval);
            	m_wrtSignalGenerator.sendBucketUpdatesThrottled();
        	}    		
    	}
        else if (!m_prevReportedOverlapInterval.isEmpty() || !m_prevReportedGreaterInterval.isEmpty()){
        	m_prevReportedOverlapInterval.clear();
        	m_prevReportedGreaterInterval.clear();
        	m_bucketOutputParams.activeBucketInfo(m_prevReportedOverlapInterval);
        	m_bucketOutputParams.nextBucketInfo(m_prevReportedGreaterInterval);
        	m_wrtSignalGenerator.sendBucketUpdatesThrottled();
        }    	
    }
    
    private void setupStatePositionHeld(final StateMachineBuilder builder) {
        builder.linkStates(StateIds.POSITION_HELD, TransitionIds.PROFIT_RUN,  StateIds.POSITION_HELD);
        builder.linkStates(StateIds.POSITION_HELD, TransitionIds.SELL_POSITION, StateIds.SELLING_POSITION);
        builder.linkStates(StateIds.POSITION_HELD, TransitionIds.ORDER_FILLED, StateIds.NO_POSITION_HELD);
        builder.linkStates(StateIds.POSITION_HELD, TransitionIds.EXIT_STRATEGY, StateIds.OFF);
        
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.STRATEGY_SWITCHED_OFF, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                return m_strategyModeBridge.onStrategyModeEnteredOnPosition();                
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.ALLOW_STOP_LOSS_ON_WIDE_SPREAD_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_speedArbWrtParams.allowStopLossOnWideSpread()) {
                    adjustSafeBidBuffer();
                    if (m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss())) {
                        m_speedArbWrtParams.stopLoss(m_callPutBridge.getUnderlyingBidSpot());
                        LOG.info("Allow stop loss on wide spread turned on while stop loss is hit. Updating stop loss to bid: secCode {}, value {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                    }
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.IGNORE_MM_SIZE_ON_SELL_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_speedArbWrtParams.ignoreMmSizeOnSell()) {
                    adjustSafeBidBuffer();
                    if (m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss())) {
                        m_speedArbWrtParams.stopLoss(m_callPutBridge.getUnderlyingBidSpot());
                        LOG.info("Ignore mm size on sell turned on while stop loss is hit. Updating stop loss to bid: secCode {}, value {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                    }
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.STOCK_SPOT_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
            	final int result = m_strategyModeBridge.onSpotUpdatedOnPosition();            	
            	broadcastBucketUpdate();
                return result;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.WARRANT_TICK_RECEIVED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                return m_strategyModeBridge.onWarrantPriceUpdatedOnPosition();
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.PRICING_MODE_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {                
                if (m_targetStopLossPricingMode != m_wrtSignalGenerator.getPricingMode()) {
                    if (m_standByTargetStopLoss != m_speedArbWrtParams.stopLoss() && m_strategyModeBridge.canReviseStopLoss()) {
                        m_speedArbWrtParams.stopLoss(m_standByTargetStopLoss);
                        m_wrtSignalGenerator.sendStatsUpdatesBatched();
                        LOG.debug("Update stop loss: secCode {}, exitLevel {}, stopLoss {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.exitLevel()), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                    }
                    m_standByTargetStopLoss = m_targetStopLoss;
                    m_targetStopLoss = m_speedArbWrtParams.stopLoss();
                    m_targetStopLossPricingMode = m_wrtSignalGenerator.getPricingMode(); 
                }
                return -1;
            }            
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.CAPTURE_PROFIT, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                if (m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.enterPrice()) {
                    m_strategyExplain.strategyExplain(StrategyExplainType.USER_CAPTURE_PROFIT_SIGNAL);
                    m_sellPrice = m_wrtSignalGenerator.getBidPrice();
                    m_sellQty = m_security.availablePosition();
                    return TransitionIds.SELL_POSITION;
                }
                else {
                    LOG.debug("Cannot capture profit because bid price < enter price: secCode {}, bidPrice {}, enterPrice {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.getBidPrice()), box(m_speedArbWrtParams.enterPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                }
                return -1;
            }
        }); 
        builder.translateEvent(StateIds.POSITION_HELD,  EventIds.PLACE_SELL_ORDER,  new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_security.pendingSell() == 0) {
                    final int bidLevel = m_speedArbWrtParams.enterLevel() + m_speedArbWrtParams.manualOrderTicksFromEnterPrice();
                    final int bidPrice = m_spreadTable.tickToPrice(bidLevel);
                    final int lotSize = m_security.lotSize();
                    final long sellQty = Math.min(m_security.availablePosition(), Math.max(lotSize, ((m_speedArbWrtParams.enterQuantity() / (2 * lotSize)) * lotSize)));
                    m_strategyExplain.strategyExplain(StrategyExplainType.USER_PLACE_SELL_ORDER_SIGNAL);
                    updateStrategyExplain();
                    m_orderService.sellLimit(m_security, bidPrice, sellQty, m_strategyExplain);
                }
                return -1;
            }            
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.ISSUER_DOWN_VOL_FOR_STANDBY_PRICER, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                if (m_speedArbWrtParams.resetStopLossOnVolDown() && m_wrtSignalGenerator.getMMBidLevel() >= SpreadTable.SPREAD_TABLE_MIN_LEVEL) {
                    final int stopLossBid;
                    final long spotAdjustment;
                    final long stopLossBuffer;
                    if (m_speedArbWrtParams.marketOutlook() == m_callPutBridge.getDesirableOutlookType()) {
                        stopLossBid = m_wrtSignalGenerator.getMMBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getMMBidLevel() - 1) : m_wrtSignalGenerator.getMMBidPrice();
                        spotAdjustment = m_callPutBridge.getSpotChangeRequired(stopLossBid - m_wrtSignalGenerator.getMMBidPrice());
                        stopLossBuffer = 0;
                    }
                    else {
                        stopLossBid = m_wrtSignalGenerator.getMMBidPrice();
                        final int prevMmBidPrice = m_wrtSignalGenerator.getMMBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getMMBidLevel() - 1) : m_wrtSignalGenerator.getMMBidPrice();
                        spotAdjustment = 0;
                        stopLossBuffer = m_callPutBridge.getSpotChangeRequired(prevMmBidPrice - stopLossBid) / 2;
                    }
                    // give half tick buffer because buckets are reset after issuer dropped vol
                    m_standByTargetStopLoss = getRestrictedTargetStopLoss(m_wrtSignalGenerator.getStandbyBucketPricer(), stopLossBid, m_wrtSignalGenerator.getStandbySpotPrice(), spotAdjustment, m_standByTargetStopLoss, stopLossBuffer);
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.ISSUER_DOWN_VOL_FROM_UNDERLYING_TICK, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                int result = handleIssuerVolDownOnPosition(false);
                broadcastBucketUpdate();
                return result;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.ISSUER_DOWN_VOL_FROM_WARRANT_TICK, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                int result = handleIssuerVolDownOnPosition(true);
                broadcastBucketUpdate();
                return result;
            }
        });        
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.NON_DOWN_VOL_VIOLATION, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                if (m_speedArbWrtParams.stopLossAdjustment() != 0) {
                    m_speedArbWrtParams.stopLossAdjustment(0);
                    m_wrtSignalGenerator.sendStatsUpdatesThrottled();
                }
                return -1;
            }
        });        
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.TURNOVER_MAKING, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                m_buyBanUntilNanoOfDay = Math.max(m_buyBanUntilNanoOfDay, m_turnoverMakingTime + m_speedArbWrtParams.banPeriodToTurnoverMaking());
                m_sellFlags |= SpeedArbHybridExplain.Flags.TO_MAKING;
                LOG.debug("Buying ban set due to turnover making: secCode {}, banUntil {}, turnoverMakingPrice {}, trigger seqNum {}", m_security.code(), box(m_buyBanUntilNanoOfDay), box(m_turnoverMakingPrice), box(m_security.orderBook().triggerInfo().triggerSeqNum()));
                m_targetSellPrice = m_turnoverMakingPrice;
                if (m_wrtSignalGenerator.getBidPrice() >= m_turnoverMakingPrice) {
                    return tryEnterSellingPositionState(StrategyExplainType.TO_MAKING_SELL_SIGNAL, m_targetSellPrice, -1);
                }                
                return -1;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.DELTA_LIMIT_ALERT_RECEIVED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.enterPrice() && (m_targetSellPrice == Integer.MAX_VALUE || m_wrtSignalGenerator.getBidPrice() >= m_targetSellPrice)) {
                    return tryEnterSellingPositionStateAtEnterPrice(StrategyExplainType.DELTA_LIMIT_SELL_SIGNAL, -1);
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.ISSUER_SMOOTHING_COMPLETED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                if (m_speedArbWrtParams.issuerSmoothing() > MIN_ISSUER_WIDE_TIME) {
                    m_sellFlags |= SpeedArbHybridExplain.Flags.WIDE;
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.POSITION_HELD, EventIds.MARKET_TRADE_RECEIVED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (updateTradesVolumeAtBuy() && hasLargeTradeVolumeSignal() && (m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.enterPrice()) && (m_targetSellPrice == Integer.MAX_VALUE || m_wrtSignalGenerator.getBidPrice() >= m_targetSellPrice)) {
                    return trySellExcessPositionAtEnterPrice(StrategyExplainType.LARGE_OUTSTANDING_SELL_SIGNAL, -1);
                }
                return -1;
            }            
        });
    }
    
    private void setupStateSellingPosition(final StateMachineBuilder builder) {
        builder.linkStates(StateIds.SELLING_POSITION, TransitionIds.ORDER_FILLED, StateIds.NO_POSITION_HELD);
        builder.linkStates(StateIds.SELLING_POSITION, TransitionIds.ORDER_NOT_FILLED, StateIds.POSITION_HELD);
        builder.linkStates(StateIds.SELLING_POSITION, TransitionIds.SELL_POSITION, StateIds.SELLING_POSITION);
        builder.linkStates(StateIds.SELLING_POSITION, TransitionIds.EXIT_STRATEGY, StateIds.OFF);
        
        builder.translateEvent(StateIds.SELLING_POSITION, EventIds.STRATEGY_SWITCHED_OFF, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                return m_strategyModeBridge.onStrategyModeEnteredOnPendingSell();
            }
        });
        builder.translateEvent(StateIds.SELLING_POSITION, EventIds.ALLOW_STOP_LOSS_ON_WIDE_SPREAD_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_speedArbWrtParams.allowStopLossOnWideSpread()) {
                    adjustSafeBidBuffer();
                    if (m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss())) {
                        m_speedArbWrtParams.stopLoss(m_callPutBridge.getUnderlyingBidSpot());
                        LOG.info("Allow stop loss on wide spread turned on while stop loss is hit. Updating stop loss to bid: secCode {}, value {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                    }
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.SELLING_POSITION, EventIds.IGNORE_MM_SIZE_ON_SELL_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_speedArbWrtParams.ignoreMmSizeOnSell()) {
                    adjustSafeBidBuffer();
                    if (m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss())) {
                        m_speedArbWrtParams.stopLoss(m_callPutBridge.getUnderlyingBidSpot());
                        LOG.info("Ignore mm size on sell turned on while stop loss is hit. Updating stop loss to bid: secCode {}, value {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                    }
                }
                return -1;
            }
        });
        builder.translateEvent(StateIds.SELLING_POSITION, EventIds.STOCK_SPOT_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
            	broadcastBucketUpdate();
            	return -1; 
            }
        });
    }
    
    private void setupStateOff(final StateMachineBuilder builder) {
        builder.linkStates(StateIds.OFF, TransitionIds.ENTER_STRATEGY_WITH_POSITION, StateIds.POSITION_HELD);
        builder.linkStates(StateIds.OFF, TransitionIds.ENTER_STRATEGY_WITHOUT_POSITION, StateIds.NO_POSITION_HELD);
        builder.linkStates(StateIds.OFF, TransitionIds.EXIT_STRATEGY, StateIds.OFF);
        
        builder.translateEvent(StateIds.OFF, EventIds.STRATEGY_SWITCHED_ON, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_security.position() > 0) {
                    if (m_speedArbWrtParams.enterPrice() == 0) {
                        throw new Exception("Cannot switch on strategy on security with existing position and no known enter price");
                    }
                    else {
                        m_speedArbWrtParams.enterQuantity((int)m_security.position());
                        m_wrtSignalGenerator.enableCollectBuckets();
                        return TransitionIds.ENTER_STRATEGY_WITH_POSITION;
                    }
                }
                m_wrtSignalGenerator.enableCollectBuckets();
                return TransitionIds.ENTER_STRATEGY_WITHOUT_POSITION;
            }
        });        
        builder.translateEvent(StateIds.OFF, EventIds.STRATEGY_SWITCHED_OFF, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                return TransitionIds.EXIT_STRATEGY;
            }
        });

    }

    private StateMachineBuilder createStateMachineBuilder() {
        final StateMachineBuilder builder = new StateMachineBuilder("SpeedArbStrategyStateMachine." + m_underlying.code() + "." + m_security.code());
        // Create states
        createStates(builder);
        
        // Setup state transitions and state-specific event handlers
        setupStateNoPositionHeld(builder);
        setupStateBuyingPosition(builder);
        setupStatePositionHeld(builder);
        setupStateSellingPosition(builder);
        setupStateOff(builder);

        // Setup common event handlers for multiple states        
        final int[] activeStatesExceptPositionHeld = new int[] {StateIds.NO_POSITION_HELD, StateIds.BUYING_POSITION, StateIds.SELLING_POSITION};
        
        final EventTranslator turnoverMakingReceived = new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                m_buyBanUntilNanoOfDay = Math.max(m_buyBanUntilNanoOfDay, m_turnoverMakingTime + m_speedArbWrtParams.banPeriodToTurnoverMaking());
                LOG.debug("Buying ban set due to turnover making: secCode {}, banUntil {}, turnoverMakingPrice {}, trigger seqNum {}", m_security.code(), box(m_buyBanUntilNanoOfDay), box(m_turnoverMakingPrice), box(m_security.orderBook().triggerInfo().triggerSeqNum()));
                return -1;
            }
        };
        builder.translateEvent(activeStatesExceptPositionHeld, EventIds.TURNOVER_MAKING, turnoverMakingReceived);
        
        final EventTranslator issuerDownVolReceived = new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) {
                m_buyBanUntilNanoOfDay = Math.max(m_buyBanUntilNanoOfDay, m_wrtSignalGenerator.lastTickNanoOfDay() + m_speedArbWrtParams.banPeriodToDownVol());
                LOG.debug("Buying ban set due to issuer down vol: secCode {}, banUntil {}, trigger seqNum {}", m_security.code(), box(m_buyBanUntilNanoOfDay), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                if (m_speedArbWrtParams.stopLossAdjustment() != 0) {
                    m_speedArbWrtParams.stopLossAdjustment(0);
                    m_wrtSignalGenerator.sendStatsUpdatesThrottled();
                }
                return -1;
            }
        };
        builder.translateEvent(activeStatesExceptPositionHeld, EventIds.ISSUER_DOWN_VOL_FROM_UNDERLYING_TICK, issuerDownVolReceived);
        builder.translateEvent(activeStatesExceptPositionHeld, EventIds.ISSUER_DOWN_VOL_FROM_WARRANT_TICK, issuerDownVolReceived);
        
        final int[] sellStates = new int[] {StateIds.POSITION_HELD, StateIds.SELLING_POSITION};
        final EventTranslator onOrderStatusUpdatedForSell = new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                clearPendingDeltaShares();
                if (m_security.pendingSell() > 0) {
                    return -1;
                }
                if (m_orderRequestRejectType != null) {
                    switch (m_orderRequestRejectType) {
                    case NULL_VAL:
                        break;
                    case THROTTLED:
                    case TIMEOUT_BEFORE_THROTTLE:
                    case TIMEOUT_AFTER_THROTTLED:
                    case EXCEED_UNDERLYING_THROTTLE:
                        m_sellBanUntilNanoOfDay = m_orderStatusReceivedTime + 10_000_000L;
                        LOG.debug("Selling ban set due to order throttled: secCode {}, banUntil {}, trigger seqNum {}", m_security.code(), box(m_sellBanUntilNanoOfDay), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                        break;
                    case RMS_INSUFFICIENT_LONG_POSITION_DIRECT_MAP:
                        final int nextTransition = checkForExitOnWarrantPriceUpdate();
                        if (nextTransition != -1) {
                            LOG.debug("Selling rejected to RMS insufficient long position. Retry selling: secCode {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                            return nextTransition;
                        }
                    default:
                        m_sellBanUntilNanoOfDay = m_orderStatusReceivedTime + 10_000_000L;
                        LOG.debug("Selling ban set due to order reject: secCode {}, banUntil {}, trigger seqNum {}", m_security.code(), box(m_sellBanUntilNanoOfDay), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                        break;
                    }
                }
                if (m_security.position() > 0) {
                    return m_strategyModeBridge.onPositionNotFullySold();
                }
                final boolean needPersist = adjustOrderSizeOnPositionSold();
                m_speedArbWrtParams.enterMMSpread(Integer.MAX_VALUE);
                m_speedArbWrtParams.enterPrice(0);
                m_speedArbWrtParams.enterLevel(0);
                m_speedArbWrtParams.profitRun(0);
                m_speedArbWrtParams.exitLevel(0);
                m_speedArbWrtParams.stopLoss(0);
                m_speedArbWrtParams.stopLossTrigger(0);
                m_speedArbWrtParams.stopLossAdjustment(0);
                if (m_speedArbWrtParams.allowStopLossOnWideSpread()) {
                    setParamStatusOnly(strategyStatus());
                }
                m_speedArbWrtParams.allowStopLossOnWideSpread(false);
                m_speedArbWrtParams.doNotSell(false);
                m_speedArbWrtParams.sellAtBreakEvenOnly(false);
                m_speedArbWrtParams.ignoreMmSizeOnSell(false);
                m_speedArbWrtParams.safeBidLevelBuffer(GenericWrtParams.DEFAULT_SAFE_BID_BUFFER_FROM_ENTER_PRICE);
                m_targetStopLoss = 0;
                m_targetStopLossPricingMode = PricingMode.UNKNOWN;
                m_standByTargetStopLoss = 0;
                m_targetSellPrice = Integer.MAX_VALUE;
                m_largeOutstandingExceedExpiryTime = 0;
                m_wrtSignalGenerator.updateSpreadState();
                if (needPersist) {                    
                    m_wrtSignalGenerator.sendStatsUpdatesBatchedPerist();
                }
                else {
                    m_wrtSignalGenerator.sendStatsUpdatesBatched();
                }
                return m_strategyModeBridge.onPositionFullySold();
            }
        };
        builder.translateEvent(sellStates, EventIds.ORDER_STATUS_UPDATED, onOrderStatusUpdatedForSell);

        return builder;
    }
    
    public void start() throws Exception {
        m_hasStarted = true;
        m_issuerResponseLagMonitor.registerHandler(this);
        if (m_issuer.code().equals("GS")) {
            m_deltaLimitAlertGenerator.registerHandler(this);
        }        
        m_turnoverMakingSignalGenerator.registerHandler(this);
        subscribeTriggerGenerator();
        m_security.registerOrderStatusReceivedHandler(this);
        m_stateMachine.start(StateIds.OFF);
    }

    public void reset() throws Exception {
        m_issuerResponseLagMonitor.reset();
        m_turnoverMakingSignalGenerator.reset();
        m_deltaLimitAlertGenerator.reset();
        m_speedArbWrtParams.enterMMSpread(Integer.MAX_VALUE);
        m_speedArbWrtParams.enterPrice(0);
        m_speedArbWrtParams.enterLevel(0);
        m_speedArbWrtParams.exitLevel(0);
        m_speedArbWrtParams.stopLoss(0);
        m_speedArbWrtParams.stopLossAdjustment(0);
        m_speedArbWrtParams.profitRun(0);
        m_speedArbWrtParams.stopLossTrigger(0);
        m_speedArbWrtParams.allowStopLossOnWideSpread(false);
        m_speedArbWrtParams.doNotSell(false);
        m_speedArbWrtParams.sellAtBreakEvenOnly(false);
        m_speedArbWrtParams.ignoreMmSizeOnSell(false);
        m_targetSellPrice = Integer.MAX_VALUE;
        m_targetStopLoss = 0;
        m_targetStopLossPricingMode = PricingMode.UNKNOWN;
        m_standByTargetStopLoss = 0;
        m_wrtSignalGenerator.sendStatsUpdatesBatched();
        m_strategyModeBridge = m_strategyModeBridgeFactory.getStrategyModeBridge(StrategyExitMode.NULL_VAL);
        m_buyBanUntilNanoOfDay = 0;
        m_sellBanUntilNanoOfDay = 0;
        m_quickProfitUntilNanoOfDay = 0;
        m_sellOnVolDownBanUntilNanoOfDay = 0;
        m_sellFlags = 0;
        m_deltaLimitExceedExpiryTime = 0;
        m_largeOutstandingExceedExpiryTime = 0;        
        m_stateMachine.start(StateIds.OFF);
    }    

    public StateMachine getStateMachine() {
        return m_stateMachine;
    }
    
    @Override
    public void onSwitchedOn() throws Exception {
        m_strategyModeBridge = m_strategyModeBridgeFactory.getStrategyModeBridge(StrategyExitMode.NULL_VAL);
        m_strategyModeBridge.onStrategyModeEntered();
        setParamStatus(strategyStatus());
        m_stateMachine.onEventReceived(EventIds.STRATEGY_SWITCHED_ON);
    }

    @Override
    public void onSwitchedOff(final StrategyExitMode exitMode) throws Exception {
        final StrategyModeBridge newMode = m_strategyModeBridgeFactory.getStrategyModeBridge(exitMode);
        if (m_strategyModeBridge.isAvailable() && (m_strategyModeBridge.priorityLevel() == -1 || newMode.priorityLevel() <= m_strategyModeBridge.priorityLevel())) {
            m_strategyModeBridge = newMode;
            m_strategyModeBridge.onStrategyModeEntered();
            setParamStatus(strategyStatus());
            m_stateMachine.onEventReceived(EventIds.STRATEGY_SWITCHED_OFF);
        }
    }

    public StrategySecurity security() {
        return m_security;
    }

    @Override
    public boolean isOn() {
        return m_stateMachine.getCurrentStateId() != StateIds.OFF;
    }

    @Override
    public boolean isOff() {
        return m_stateMachine.getCurrentStateId() == StateIds.OFF;
    }

    @Override
    public boolean isExiting() {
        return m_strategyModeBridge.offWhenExitPosition() && m_stateMachine.getCurrentStateId() != StateIds.OFF;
    }
    
    @Override
    public void onOrderStatusReceived(final long nanoOfDay, final int price, final long quantity, final OrderRequestRejectType rejectType) throws Exception {
        m_orderStatusReceivedTime = nanoOfDay;
        m_orderRequestRejectType = rejectType;
        m_ourTradedPrice = price;
        m_stateMachine.onEventReceived(EventIds.ORDER_STATUS_UPDATED);
    }
    
    public void onCaptureProfit() throws Exception {
        LOG.debug("User capture profit: secCode {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_stateMachine.onEventReceived(EventIds.CAPTURE_PROFIT);
    }
    
    public void onPlaceSellOrder() throws Exception {
        m_stateMachine.onEventReceived(EventIds.PLACE_SELL_ORDER);
    }
    
    public void onStopLossExternallyUpdated() {
        m_speedArbWrtParams.stopLossAdjustment(0);
        m_targetStopLoss = m_speedArbWrtParams.stopLoss();
    }

    public int getCurrentStateId() {
        return m_stateMachine.getCurrentStateId();
    }

    public GenericWrtParams getSpeedArbWrtParams() {
        return m_speedArbWrtParams;
    }

    private void setParamStatusPersist(final StrategyStatusType status) {
        setParamStatusOnly(status);
        m_strategyInfoSender.broadcastStrategyParams(m_speedArbWrtParams);
    }

    private void setParamStatus(final StrategyStatusType status) {
        setParamStatusOnly(status);
        m_wrtSignalGenerator.sendStatsUpdates();
    }

    private void setParamStatusOnly(final StrategyStatusType status) {
        final StrategyStatusType prevStatus = m_speedArbWrtParams.status();
        LOG.debug("Strategy status updated: secCode {}, prevStatus {}, newStatus {}, trigger seqNum {}", m_security.code(), prevStatus, status, box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_speedArbWrtParams.status(status);
        if (prevStatus != StrategyStatusType.ACTIVE && status == StrategyStatusType.ACTIVE) {
            m_speedArbIssuerParams.incActiveWarrants(); 
            this.m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_speedArbIssuerParams);
            m_speedArbUndParams.incActiveWarrants();
            this.m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_speedArbUndParams);
        }
        if (prevStatus == StrategyStatusType.ACTIVE && status != StrategyStatusType.ACTIVE) {
            m_speedArbIssuerParams.decActiveWarrants();                
            this.m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_speedArbIssuerParams);            
            m_speedArbUndParams.decActiveWarrants();
            this.m_strategyInfoSender.broadcastStrategyParamsNoPersistThrottled(m_speedArbUndParams);
        }
    }
    
    private boolean isPriceSafeToExit(final boolean retry) {    	
        if (m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.enterPrice()) {
            return true;
        }
        if (m_wrtSignalGenerator.getSpotPrice() > 0 && m_wrtSignalGenerator.getBidPrice() > 0) {
            final float changeToSpotMove = m_spreadTableScaleFormulaBridge.calcPriceChangeFloatForSpotChange(m_wrtSignalGenerator.getSpotPrice() - m_speedArbWrtParams.enterSpotPrice(),
                    m_security.convRatio(),
                    m_spreadTableScaleFormulaBridge.calcAdjustedDelta(m_wrtSignalGenerator.getSpotPrice(), m_speedArbWrtParams.greeks()));
            final float vegaAllowance = m_speedArbWrtParams.greeks().vega() / (m_security.convRatio() * 10.0f);
            final float lowestBidAllowed = m_speedArbWrtParams.enterMMBidPrice() + changeToSpotMove - vegaAllowance;
            final int priceToCompare = m_spreadTable.tickToPrice(m_wrtSignalGenerator.getBidLevel() + EXIT_LEVEL_ALLOWANCE + 1);
            return lowestBidAllowed < priceToCompare;
        }
        return false;
    }

    @Override
    public void onTurnoverMakingDetected(long nanoOfDay, int price) throws Exception {
        m_turnoverMakingTime = nanoOfDay;
        m_turnoverMakingPrice = price;
        m_strategyInfoSender.sendEventBatched(m_strategyType, m_security, EventType.TO_MAKING_SIGNAL, nanoOfDay, EventValueType.TURNOVER_PRICE, price);
        LOG.debug("Turnover making signal detected: secCode {}, price {}, trigger seqNum {}", m_security.code(), box(price), box(m_security.orderBook().triggerInfo().triggerSeqNum()));
        m_stateMachine.onEventReceived(EventIds.TURNOVER_MAKING);
    }
    
    private void updateStrategyExplain() {
        m_strategyExplain.triggerSeqNum(m_wrtSignalGenerator.triggerInfo().triggerSeqNum());
        m_strategyExplain.setExplainValues(m_undSignalGenerator.getPrevBidPrice(), m_undSignalGenerator.getPrevAskPrice(), m_undSignalGenerator.getBidPrice(), m_undSignalGenerator.getAskPrice(),
                m_wrtSignalGenerator.getPrevBidPrice(), m_wrtSignalGenerator.getPrevAskPrice(), m_wrtSignalGenerator.getBidPrice(), m_wrtSignalGenerator.getAskPrice(),
                m_triggerGenerator.getExplainValue(), m_speedArbWrtParams.greeks().delta(), m_wrtSignalGenerator.getAskLevel() - m_wrtSignalGenerator.getBidLevel(), m_wrtSignalGenerator.getTickSensitivity(),
                m_highWarrantBid, m_bestSpot, m_wrtSignalGenerator.getSpotPrice(), m_wrtSignalGenerator.getPrevSpotPrice(), m_wrtSignalGenerator.getBucketSize(), m_speedArbWrtParams.pricingMode(), m_sellFlags);
    }
    
    private float m_cachedAdjustedDeltaC;    
    private StrategyExplainType hasBuyTrigger() throws Exception {
        final BucketPricer bucketPricer = m_wrtSignalGenerator.getBucketPricer();
        if (m_orderService.canTrade() && bucketPricer != null && m_wrtSignalGenerator.lastTickNanoOfDay() >= m_buyBanUntilNanoOfDay && m_speedArbWrtParams.tickSensitivity() >= m_speedArbWrtParams.tickSensitivityThreshold()) {                        
            if (m_callPutBridge.isTriggered() && 
                    m_wrtSignalGenerator.getMMBidLevel() >= SpreadTable.SPREAD_TABLE_MIN_LEVEL && 
                    m_wrtSignalGenerator.getMMAskLevel() >= SpreadTable.SPREAD_TABLE_MIN_LEVEL && 
                    m_wrtSignalGenerator.getMmSpread() <= m_speedArbWrtParams.allowedMaxSpread() && 
                    m_wrtSignalGenerator.getTargetSpread() == m_wrtSignalGenerator.getMmSpread()) {
                m_cachedAdjustedDeltaC = calcAdjustedDeltaC();
                if (m_cachedAdjustedDeltaC != 0) {
                    final long spotPriceBuffer = getTickBufferSpotAdjustment(m_cachedAdjustedDeltaC);
                    if (bucketPricer.getIntervalByUndSpot(m_wrtSignalGenerator.getSpotPrice() - spotPriceBuffer, m_intervalBySpot)) {
                        if (m_intervalBySpot.data() >= m_wrtSignalGenerator.getAskPrice()) {
                            return checkAndReturnHasBuyTrigger(StrategyExplainType.PREDICTION_BY_BID_BUCKET_BUY_SIGNAL);
                        }
                    }
                    else if (m_speedArbWrtParams.greeks().delta() != 0 ){            
                        // Case 2 the spot price is higher than the range of observed spot price for the current bucket
                        if (bucketPricer.getIntervalByDerivPrice(m_wrtSignalGenerator.getMMBidPrice(), m_intervalByPrice)) {
                            final long spotToCover = getTargetSpotMove(m_cachedAdjustedDeltaC) + spotPriceBuffer;
                            if (m_callPutBridge.isSpotHigherThan(m_callPutBridge.getBucketBegin(m_intervalByPrice) + spotToCover)) {
                                return checkAndReturnHasBuyTrigger(StrategyExplainType.PREDICTION_BY_WAVG_BUCKET_BUY_SIGNAL);
                            }
                        }
                        else if (m_wrtSignalGenerator.getPrevSpotPrice() != 0) {
                            final long spotToCover = getTargetSpotMove(m_cachedAdjustedDeltaC) + spotPriceBuffer;
                            if (m_callPutBridge.isSpotHigherThan(m_wrtSignalGenerator.getPrevSpotPrice() + spotToCover)) {                                
                                return checkAndReturnHasBuyTrigger(StrategyExplainType.PREDICTION_BY_WAVG_SPOT_BUY_SIGNAL);
                            }
                        }
                    }
                }
            }
        }
        return StrategyExplainType.NULL_VAL;
    }
    
    private long m_cachedBuyDeltaShares = 0;
    private int m_cachedBuyOrderSize = 0;
    private StrategyExplainType checkAndReturnHasBuyTrigger(final StrategyExplainType explainType) {
        m_cachedBuyDeltaShares = 0;
        m_cachedBuyOrderSize = 0;
        if (m_wrtSignalGenerator.getMMAskPrice() > m_wrtSignalGenerator.getHoldBidBanPrice()) {
            LOG.debug("Cannot buy due to hold bid ban: secCode {}, mmAskPrice {}, holdBidBanPrice {}, buyPrice {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.getMMAskPrice()), box(m_wrtSignalGenerator.getHoldBidBanPrice()), box(m_wrtSignalGenerator.getAskPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            return StrategyExplainType.NULL_VAL;
        }
        if (m_speedArbWrtParams.tradesVolumeThreshold() != 0) {
            final int buyOrderSize = getOrderSizeToBuy();
            final long tradesVolumeThreshold = adjustedTradesVolumeThreshold(m_wrtSignalGenerator.getAskPrice());
            if (m_wrtSignalGenerator.getAndRefresh20msNetTradesVolume() + buyOrderSize >= tradesVolumeThreshold) {
                LOG.debug("Cannot buy due to large outstanding: secCode {}, outstandingThreshold {}, outstanding {}, buyPrice {}, trigger seqNum {}", m_security.code(), box(tradesVolumeThreshold), box(m_wrtSignalGenerator.get20msNetTradesVolume()), box(m_wrtSignalGenerator.getAskPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                return StrategyExplainType.NULL_VAL;
            }
            m_cachedBuyOrderSize = buyOrderSize;
        }
        if (m_speedArbIssuerUndParams.undTradeVolThreshold() != 0) {
        	if (m_wrtSignalGenerator.lastTickNanoOfDay() <= this.m_deltaLimitExceedExpiryTime) {
                LOG.debug("Cannot buy because the max delta notional has exceeded the delta limit: secCode {}, undCode {}, issuerCode {}, trigger seqNum {}", m_security.code(), m_underlying.code(), m_issuer.code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                return StrategyExplainType.NULL_VAL;
        	}
        	else {
	            int buyOrderSize = getOrderSizeToBuy();
	            
	            final long deltaSharesThreshold = m_deltaLimitAlertGenerator.calcDeltaShares(m_speedArbIssuerUndParams.undTradeVolThreshold());
	            final long currentDeltaShares = m_speedArbIssuerUndParams.undDeltaShares() + m_speedArbIssuerUndParams.pendingUndDeltaShares();	            
	            if (Math.abs(currentDeltaShares) > deltaSharesThreshold) {
                    LOG.debug("Cannot buy because the buy quantity would cause the delta notional to exceed the delta limit: secCode {}, undCode {}, issuerCode {}, deltaShares {}, pendingDeltaShares {}, trigger seqNum {}",
                            m_security.code(), m_underlying.code(), m_issuer.code(), box(m_speedArbIssuerUndParams.undDeltaShares()), box(m_speedArbIssuerUndParams.pendingUndDeltaShares()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                        return StrategyExplainType.NULL_VAL;
	            }
	            else if (m_speedArbWrtParams.greeks().delta() != 0) {
	                final long availableDeltaShares = m_callPutBridge.getAvailableDeltaShares(currentDeltaShares, deltaSharesThreshold);
	                final int availableBuyQuantity = (int)((availableDeltaShares * m_security.convRatio() * 100) / Math.abs(m_speedArbWrtParams.greeks().delta()));
	                if (availableBuyQuantity < buyOrderSize) {
	                    buyOrderSize = (availableBuyQuantity / m_security.lotSize()) * m_security.lotSize();
	                    if (buyOrderSize > 0) {
                            LOG.debug("Buy order size adjusted to prevent exceeding the delta limit: secCode {}, undCode {}, issuerCode {}, deltaShares {}, pendingDeltaShares {}, trigger seqNum {}",
                                m_security.code(), m_underlying.code(), m_issuer.code(), box(m_speedArbIssuerUndParams.undDeltaShares()), box(m_speedArbIssuerUndParams.pendingUndDeltaShares()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
	                    }
	                    else {
	                        LOG.debug("Cannot buy because the buy quantity would cause the delta notional to exceed the delta limit: secCode {}, undCode {}, issuerCode {}, deltaShares {}, pendingDeltaShares {}, trigger seqNum {}",
	                                m_security.code(), m_underlying.code(), m_issuer.code(), box(m_speedArbIssuerUndParams.undDeltaShares()), box(m_speedArbIssuerUndParams.pendingUndDeltaShares()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
	                            return StrategyExplainType.NULL_VAL;
	                    }
	                }
	            }
	            m_cachedBuyOrderSize = buyOrderSize;
	            m_cachedBuyDeltaShares  = (long)buyOrderSize * m_speedArbWrtParams.greeks().delta() / (m_security.convRatio() * 100);
        	}            
        }
        return explainType;
    }
    
    private int getOrderSizeToBuy() {
        if (m_wrtSignalGenerator.getAskPrice() < LARGE_WARRANT_PRICE) {
            return m_speedArbWrtParams.orderSize();    
        }
        else if (m_wrtSignalGenerator.getAskPrice() < VERY_LARGE_WARRANT_PRICE) {
            return m_largePriceOrderSize;    
        }
        else {
            return m_veryLargePriceOrderSize;
        }
    }
    
    private void buy() {
        final int orderSize = m_cachedBuyOrderSize == 0 ? getOrderSizeToBuy() : m_cachedBuyOrderSize;
        buy(orderSize);
    }
    
    private void buyAdditional() {
        final int orderSize;
        if (m_wrtSignalGenerator.getAskPrice() < LARGE_WARRANT_PRICE) {
            orderSize = Math.min(m_speedArbWrtParams.orderSize(), m_speedArbWrtParams.maxOrderSize() - (int)m_security.position());
        }
        else if (m_wrtSignalGenerator.getAskPrice() < VERY_LARGE_WARRANT_PRICE) {
            orderSize = Math.min(m_largePriceOrderSize, m_maxLargePriceOrderSize - (int)m_security.position());
        }
        else {
            orderSize = Math.min(m_veryLargePriceOrderSize, m_maxVeryLargePriceOrderSize - (int)m_security.position());
        }
        if (orderSize > 0) {
            buyAdditional(orderSize);
        }
    }
    
    private void buy(final int orderSize) {
        m_reentryBan = true;
        updatePendingDeltaSharesForBuy();
        m_mmBidLevelAtBuy = m_wrtSignalGenerator.getMMBidLevel();
        m_highWarrantBid = 0;
        m_bestSpot = 0;
        m_sellFlags = 0;
        updateStrategyExplain();
        m_speedArbWrtParams.exitLevel(m_wrtSignalGenerator.getAskLevel()); // exit level needs to be called before m_orderService.buy. in fact, exit level should be set along with stoploss in the setInitialStopLoss method
        m_deltaLimitExceedExpiryTime = 0;
        m_largeOutstandingExceedExpiryTime = 0;
        m_speedArbWrtParams.enterMMSpread(m_wrtSignalGenerator.getMmSpread());
        m_speedArbWrtParams.enterPrice(m_wrtSignalGenerator.getAskPrice());
        m_speedArbWrtParams.enterLevel(m_wrtSignalGenerator.getAskLevel());
        m_speedArbWrtParams.profitRun(0);
        m_speedArbWrtParams.stopLossTrigger(0);
        m_speedArbWrtParams.enterMMBidPrice(m_wrtSignalGenerator.getMMBidPrice());
        m_speedArbWrtParams.enterBidLevel(m_wrtSignalGenerator.getBidLevel());
        m_speedArbWrtParams.enterSpotPrice(m_wrtSignalGenerator.getPrevSpotPrice());
        m_speedArbWrtParams.enterQuantity(orderSize);
        m_speedArbWrtParams.doNotSell(false);
        m_speedArbWrtParams.sellAtBreakEvenOnly(false);
        m_speedArbWrtParams.allowStopLossOnWideSpread(false);
        m_speedArbWrtParams.ignoreMmSizeOnSell(false);
        m_speedArbWrtParams.safeBidLevelBuffer(GenericWrtParams.DEFAULT_SAFE_BID_BUFFER_FROM_ENTER_PRICE);
        updateSafeBidPrice();
        m_sellBanUntilNanoOfDay = m_wrtSignalGenerator.lastTickNanoOfDay() + m_speedArbWrtParams.sellingBanPeriod();
        m_quickProfitUntilNanoOfDay = m_speedArbWrtParams.sellAtQuickProfit() ? m_wrtSignalGenerator.lastTickNanoOfDay() + QUICK_PROFIT_TIME : 0;
        m_sellOnVolDownBanUntilNanoOfDay = m_wrtSignalGenerator.lastTickNanoOfDay() + m_speedArbWrtParams.sellOnVolDownBanPeriod();
        m_tradesVolumeAtBuy = m_wrtSignalGenerator.get20msNetTradesVolume();
        m_orderService.buy(m_security, m_wrtSignalGenerator.getAskPrice(), orderSize, m_strategyExplain);
        m_wrtSignalGenerator.sendStatsUpdatesBatched();
        LOG.debug("Buying position: secCode {}, enterPrice {}, exitLevel {}, stopLoss {}, mmBid {}, spotPrice {}, safeBidPrice {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.getAskPrice()), box(m_speedArbWrtParams.exitLevel()), box(m_speedArbWrtParams.stopLoss()), box(m_speedArbWrtParams.enterMMBidPrice()), box(m_speedArbWrtParams.enterSpotPrice()), box(m_speedArbWrtParams.safeBidPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
    }
    
    private void buyAdditional(final int orderSize) {
        m_reentryBan = true;
        updatePendingDeltaSharesForBuy();
        m_mmBidLevelAtBuy = m_wrtSignalGenerator.getMMBidLevel();
        updateStrategyExplain();
        m_orderService.buy(m_security, m_wrtSignalGenerator.getAskPrice(), orderSize, m_strategyExplain);
        if (m_wrtSignalGenerator.getAskLevel() > m_speedArbWrtParams.enterLevel()) {
            m_speedArbWrtParams.enterPrice(m_wrtSignalGenerator.getAskPrice());
            m_speedArbWrtParams.enterLevel(m_wrtSignalGenerator.getAskLevel());    
        }
        m_wrtSignalGenerator.sendStatsUpdatesBatched();
        LOG.debug("Buying additional position: secCode {}, enterPrice {}, exitLevel {}, stopLoss {}, mmBid {}, spotPrice {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.getAskPrice()), box(m_speedArbWrtParams.exitLevel()), box(m_speedArbWrtParams.stopLoss()), box(m_speedArbWrtParams.enterMMBidPrice()), box(m_speedArbWrtParams.enterSpotPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
    }
    
    private void sell() {
        updateStrategyExplain();
        if (m_security.pendingSell() > 0) {
            if (m_sellQty > 0) {
                updatePendingDeltaSharesForSell(m_sellQty);
                m_orderService.sell(m_security, m_sellPrice, m_sellQty, m_strategyExplain);
            }
            m_orderService.cancelAndSellOutstandingSell(m_security, m_sellPrice, m_strategyExplain);
        }
        else {
            updatePendingDeltaSharesForSell(m_sellQty);
            m_orderService.sell(m_security, m_sellPrice, m_sellQty, m_strategyExplain);
        }
        m_sellPrice = 0;
        m_sellQty = 0;
    }
    
    private void sellToExit() {
        final int sellPrice = m_wrtSignalGenerator.getBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getBidLevel() - 1) : m_wrtSignalGenerator.getBidPrice();
        sellToExit(sellPrice);
    }
    
    private void sellToExitNoCheck() {
        int bidLevel = m_wrtSignalGenerator.getBidLevel() - 5;
        if (bidLevel < SpreadTable.SPREAD_TABLE_MIN_LEVEL) {
            bidLevel = SpreadTable.SPREAD_TABLE_MIN_LEVEL;
        }
        final int sellPrice = m_spreadTable.tickToPrice(bidLevel);
        sellToExit(sellPrice < m_speedArbWrtParams.safeBidPrice() ? m_speedArbWrtParams.safeBidPrice() : sellPrice);
    }
    
    private void sellToExit(final int sellPrice) {
        updateStrategyExplain();
        final long sellQty = m_security.availablePosition();
        if (m_security.pendingSell() > 0) {
            if (sellQty > 0) {
                updatePendingDeltaSharesForSell(sellQty);
                m_orderService.sellToExit(m_security, sellPrice, sellQty, m_strategyExplain);
            }
            m_orderService.cancelAndSellOutstandingSell(m_security, sellPrice, m_strategyExplain);
        }
        else {
            updatePendingDeltaSharesForSell(sellQty);
            m_orderService.sellToExit(m_security, sellPrice, sellQty, m_strategyExplain);
        }
    }
   
    private int tryEnterSellingPositionStateAtEnterPrice(final StrategyExplainType explainType, final int defaultTransition) {
        final int sellPrice = m_speedArbWrtParams.enterPrice();
        final long sellQty = m_security.availablePosition();
        return tryEnterSellingPositionState(explainType, sellPrice, sellQty, defaultTransition);
    }
 
    private int trySellExcessPositionAtEnterPrice(final StrategyExplainType explainType, final int defaultTransition) {
        if (m_wrtSignalGenerator.getMMBidPrice() == 0)
            return defaultTransition;
        final int remainder;
        if (m_wrtSignalGenerator.getMMBidPrice() < LARGE_WARRANT_PRICE) {
            remainder = m_orderSizeRemainder;
        }
        else if (m_wrtSignalGenerator.getMMBidPrice() < VERY_LARGE_WARRANT_PRICE) {
            remainder = m_largePriceOrderSizeRemainder;
        }
        else {
            remainder = m_veryLargePriceOrderSizeRemainder;
        }
        if (remainder == 0) {
            return tryEnterSellingPositionStateAtEnterPrice(explainType, defaultTransition);
        }
        else if (remainder >= m_security.availablePosition()) {
            if (remainder >= m_speedArbWrtParams.enterQuantity()) {
                return tryEnterSellingPositionStateAtEnterPrice(explainType, defaultTransition);
            }
        }
        else {
            m_strategyExplain.strategyExplain(explainType);
            if (doNotSell()) {
                return defaultTransition;
            }
            if (m_wrtSignalGenerator.lastTickNanoOfDay() <= m_sellBanUntilNanoOfDay) {            
                LOG.debug("Cannot sell position due to selling ban: secCode {}, reason {}, trigger seqNum {}", m_security.code(), box(m_strategyExplain.strategyExplain().value()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                return defaultTransition;
            }
            final int sellPrice = m_speedArbWrtParams.enterPrice();
            final long sellQty = m_security.availablePosition() - remainder;
            updateStrategyExplain();
            updatePendingDeltaSharesForSell(sellQty);
            m_orderService.sell(m_security, sellPrice, sellQty, m_strategyExplain);
        }
        return defaultTransition;
    }
    
    private int tryEnterSellingPositionStateAtOneBelowBidWithCheck(final StrategyExplainType explainType, final int defaultTransition) {
    	if (m_wrtSignalGenerator.getBidPrice() < m_speedArbWrtParams.safeBidPrice()) {
            LOG.debug("Cannot sell position due to bid price below safe bid price: secCode {}, reason {}, bidPrice {}, safeBidPrice {}, trigger seqNum {}", m_security.code(), box(m_strategyExplain.strategyExplain().value()), box(m_wrtSignalGenerator.getBidPrice()), box(m_speedArbWrtParams.safeBidPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            return defaultTransition;
    	}
        final int sellPrice = m_wrtSignalGenerator.getBidPrice() > m_speedArbWrtParams.safeBidPrice() ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getBidLevel() - 1) : m_speedArbWrtParams.safeBidPrice();
        final long sellQty = m_security.availablePosition();
        return tryEnterSellingPositionState(explainType, sellPrice, sellQty, defaultTransition);
    }

    private int tryEnterSellingPositionStateAtOneBelowBid(final StrategyExplainType explainType, final int defaultTransition) {
        final int sellPrice = m_wrtSignalGenerator.getBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getBidLevel() - 1) : m_wrtSignalGenerator.getBidPrice();
        final long sellQty = m_security.availablePosition();
        return tryEnterSellingPositionState(explainType, sellPrice, sellQty, defaultTransition);
    }

    private int tryEnterSellingPositionStateAtBid(final StrategyExplainType explainType, final int defaultTransition) {
        final int sellPrice = m_wrtSignalGenerator.getBidPrice();
        final long sellQty = m_security.availablePosition();
        return tryEnterSellingPositionState(explainType, sellPrice, sellQty, defaultTransition);
    }

    private int tryEnterSellingPositionState(final StrategyExplainType explainType, final int sellPrice, final int defaultTransition) {
        final long sellQty = m_security.availablePosition();
        return tryEnterSellingPositionState(explainType, sellPrice, sellQty, defaultTransition);
    }

    private int tryEnterSellingPositionState(final StrategyExplainType explainType, final int sellPrice, final long sellQty, final int defaultTransition) {
        m_sellPrice = sellPrice;
        m_sellQty = sellQty;
        return tryEnterSellToExitPositionState(explainType, defaultTransition);
    }
    
    private int tryEnterSellToExitPositionState(final StrategyExplainType explainType, final int defaultTransition) {
        m_strategyExplain.strategyExplain(explainType);
        if (doNotSell()) {
            return defaultTransition;
        }
        if (m_wrtSignalGenerator.lastTickNanoOfDay() > m_sellBanUntilNanoOfDay) {            
            return TransitionIds.SELL_POSITION;
        }
        LOG.debug("Cannot sell position due to selling ban: secCode {}, reason {}, trigger seqNum {}", m_security.code(), box(m_strategyExplain.strategyExplain().value()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        return defaultTransition;
    }

    private void updatePendingDeltaSharesForBuy() {
        m_pendingDeltaShares += m_cachedBuyDeltaShares;
        m_speedArbIssuerUndParams.pendingUndDeltaShares(m_speedArbIssuerUndParams.pendingUndDeltaShares() + m_cachedBuyDeltaShares);
    }
    
    private void updatePendingDeltaSharesForSell(final long quantity) {
        final long sellDeltaShares  = (long)quantity * m_speedArbWrtParams.greeks().delta() / (m_security.convRatio() * 100);
        m_pendingDeltaShares -= sellDeltaShares;
        m_speedArbIssuerUndParams.pendingUndDeltaShares(m_speedArbIssuerUndParams.pendingUndDeltaShares() - sellDeltaShares);
    }
    
    private void clearPendingDeltaShares() {
        m_speedArbIssuerUndParams.pendingUndDeltaShares(m_speedArbIssuerUndParams.pendingUndDeltaShares() - m_pendingDeltaShares);
        m_pendingDeltaShares = 0;
    }

    private long getTargetStopLoss(final BucketPricer bucketPricer, final int stopLossBid, final long spotPrice, final long spotAdjustment) {
        if (bucketPricer != null && bucketPricer.getIntervalByDerivPriceWithExtrapolation(stopLossBid, m_intervalByPrice)) {
            return m_callPutBridge.getLowerStopLoss(spotPrice + spotAdjustment, m_callPutBridge.getBucketBegin(m_intervalByPrice));
        }
        else {
            return spotPrice + spotAdjustment;
        }
    }
    
    private long getTargetStopLoss(final BucketPricer bucketPricer, final int stopLossBid, final long spotPrice, final long prevSpotPrice, final long spotAdjustment) {
        if (bucketPricer != null && bucketPricer.getIntervalByDerivPriceWithExtrapolation(stopLossBid, m_intervalByPrice)) {
            return m_callPutBridge.getLowerStopLoss(spotPrice + spotAdjustment, m_callPutBridge.getBucketBegin(m_intervalByPrice));
        }
        else {
            return prevSpotPrice + spotAdjustment;
        }
    }
    
    private long getTickBufferSpotAdjustment(final float adjustedDeltaC) {
        if (m_speedArbWrtParams.tickBuffer() == 0) {
            return 0;
        }
        else {
            return m_spreadTableScaleFormulaBridge.calcSpotBufferFromTickBuffer(m_spreadTable.priceToTickSize(m_wrtSignalGenerator.getAskPrice()), m_speedArbWrtParams.tickBuffer(), adjustedDeltaC);
        }
    }

    private long getStopLossAdjustment(final float adjustedDeltaC) {
        if (m_speedArbWrtParams.stopLossTickBuffer() == 0) {
            return 0;
        }
        else {
            return m_spreadTableScaleFormulaBridge.calcSpotBufferFromTickBuffer(m_spreadTable.priceToTickSize(m_wrtSignalGenerator.getAskPrice()), m_speedArbWrtParams.stopLossTickBuffer(), adjustedDeltaC);
        }
    }
    
    private long getTargetSpotMove(final float adjustedDeltaC) {
        return m_spreadTableScaleFormulaBridge.calcSpotChangeForPriceChange(m_wrtSignalGenerator.getAskPrice() - m_wrtSignalGenerator.getMMBidPrice(), adjustedDeltaC);
    }

    private long getRestrictedTargetStopLoss(final BucketPricer bucketPricer, final int stopLossBid, final long spotPrice, final long spotAdjustment, final long prevStopLoss, final long stopLossBuffer) {
        long targetStopLoss = getTargetStopLoss(bucketPricer, stopLossBid, spotPrice, spotAdjustment);
        targetStopLoss += stopLossBuffer;
        if (m_speedArbWrtParams.stopLossAdjustment() != 0) {
            targetStopLoss += m_speedArbWrtParams.stopLossAdjustment();
            targetStopLoss = m_callPutBridge.getLowerStopLoss(spotPrice, targetStopLoss);
        }
        if (!m_undSignalGenerator.isTightSpread() && targetStopLoss == spotPrice) {
            targetStopLoss = m_callPutBridge.lowerStopLoss(targetStopLoss, 10);
        }
        return m_callPutBridge.getHigherStopLoss(targetStopLoss, prevStopLoss);
    }

    private void setInitialStopLosses(final float adjustedDeltaC) {
        final int stopLossBid = m_wrtSignalGenerator.getMMBidPrice();
        this.m_speedArbWrtParams.stopLossAdjustment(getStopLossAdjustment(adjustedDeltaC));
        m_targetStopLossPricingMode = m_wrtSignalGenerator.getPricingMode();
        m_targetStopLoss = getTargetStopLoss(m_wrtSignalGenerator.getBucketPricer(), stopLossBid, m_wrtSignalGenerator.getSpotPrice(), 0);
        //m_targetStopLoss += m_speedArbWrtParams.stopLossAdjustment();
        m_standByTargetStopLoss = getTargetStopLoss(m_wrtSignalGenerator.getStandbyBucketPricer(), stopLossBid, m_wrtSignalGenerator.getStandbySpotPrice(), 0);
        //m_standByTargetStopLoss += m_speedArbWrtParams.stopLossAdjustment();
        m_speedArbWrtParams.stopLoss(m_targetStopLoss);
    }
    
    private void setInitialStopLossesUsingPrevSpot(final float adjustedDeltaC) {
        final int stopLossBid = m_wrtSignalGenerator.getMMBidPrice();
        this.m_speedArbWrtParams.stopLossAdjustment(getStopLossAdjustment(adjustedDeltaC));
        m_targetStopLossPricingMode = m_wrtSignalGenerator.getPricingMode();
        m_targetStopLoss = getTargetStopLoss(m_wrtSignalGenerator.getBucketPricer(), stopLossBid, m_wrtSignalGenerator.getSpotPrice(), m_wrtSignalGenerator.getPrevSpotPrice(), 0);
        //m_targetStopLoss += m_speedArbWrtParams.stopLossAdjustment();
        m_standByTargetStopLoss = getTargetStopLoss(m_wrtSignalGenerator.getStandbyBucketPricer(), stopLossBid, m_wrtSignalGenerator.getStandbySpotPrice(), m_wrtSignalGenerator.getStandbyPrevSpotPrice(), 0);
        //m_standByTargetStopLoss += m_speedArbWrtParams.stopLossAdjustment();
        m_speedArbWrtParams.stopLoss(m_targetStopLoss);
    }
    
    private void reviseStopLossAndExitLevel() {
        if (m_wrtSignalGenerator.getMMBidLevel() >= SpreadTable.SPREAD_TABLE_MIN_LEVEL) {
            final int stopLossBid;
            final long spotAdjustment;
            final long stopLossBuffer;
            if (m_speedArbWrtParams.marketOutlook() == m_callPutBridge.getDesirableOutlookType()) {
                stopLossBid = m_wrtSignalGenerator.getMMBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getMMBidLevel() - 1) : m_wrtSignalGenerator.getMMBidPrice();
                spotAdjustment = m_callPutBridge.getSpotChangeRequired(stopLossBid - m_wrtSignalGenerator.getMMBidPrice());
                stopLossBuffer = 0;
            }
            else {
                stopLossBid = m_wrtSignalGenerator.getMMBidPrice();
                spotAdjustment = 0;
                if (m_speedArbWrtParams.exitLevel() == 0) {
                    final int prevMmBidPrice = m_wrtSignalGenerator.getMMBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getMMBidLevel() - 1) : m_wrtSignalGenerator.getMMBidPrice();
                    stopLossBuffer = m_callPutBridge.getSpotChangeRequired(prevMmBidPrice - stopLossBid) / 2;
                }
                else {
                    stopLossBuffer = 0;
                }
            }
            m_targetStopLoss = getRestrictedTargetStopLoss(m_wrtSignalGenerator.getBucketPricer(), stopLossBid, m_wrtSignalGenerator.getSpotPrice(), spotAdjustment, m_targetStopLoss, stopLossBuffer);
            m_standByTargetStopLoss = getRestrictedTargetStopLoss(m_wrtSignalGenerator.getStandbyBucketPricer(), stopLossBid, m_wrtSignalGenerator.getStandbySpotPrice(), spotAdjustment, m_standByTargetStopLoss, stopLossBuffer);
            if (m_callPutBridge.canUpdateStopLoss(m_targetStopLoss)) {
                if (m_strategyModeBridge.canReviseStopLoss()) {
                    m_speedArbWrtParams.stopLoss(m_targetStopLoss);
                }
            }
            m_speedArbWrtParams.exitLevel(m_wrtSignalGenerator.getMMBidLevel() + 1);
            m_wrtSignalGenerator.sendStatsUpdatesBatched();
            LOG.debug("Update exit level and stop loss: secCode {}, exitLevel {}, stopLoss {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.exitLevel()), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
    }
    
    private float calcAdjustedDeltaC() {
        return m_spreadTableScaleFormulaBridge.calcAdjustedDelta(m_wrtSignalGenerator.getSpotPrice(), m_speedArbWrtParams.greeks()) / (m_security.convRatio() * 100.0f);
    }
    
    private boolean isStopLossTriggered() {
        return (m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss()) ||
                (!m_undSignalGenerator.isTightSpread() && 
                        (m_speedArbWrtParams.stopLoss() == m_wrtSignalGenerator.getSpotPrice())));
                        //(m_speedArbWrtParams.stopLoss() == m_wrtSignalGenerator.getSpotPrice() ||
                        //(m_speedArbWrtParams.allowStopLossOnFlashingBid() && m_wrtSignalGenerator.getTickSensitivity() >= 1000 && m_undSignalGenerator.isPrevTightSpread() && m_callPutBridge.isSpotLesserThan(m_wrtSignalGenerator.getPrevSpotPrice())))));
    }
    
    @Override
    public void onIssuerLagUpdated(long issuerLag) throws Exception {
        LOG.debug("Last issuer lag: secCode {}, issuerLag {}, trigger seqNum {}", m_security.code(), box(issuerLag), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_speedArbWrtParams.issuerLag(issuerLag);
        final long issuerMaxLag = Math.min(m_speedArbWrtParams.issuerMaxLagCap(),  Math.max(MIN_ISSUER_MAX_LAG,  issuerLag < m_speedArbWrtParams.issuerMaxLag() ? (m_speedArbWrtParams.issuerMaxLag() + issuerLag) / 2 : issuerLag));
        LOG.debug("Updated issuer max lag: secCode {}, issuerMaxLag {}, trigger seqNum {}", m_security.code(), box(issuerMaxLag), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_speedArbWrtParams.issuerMaxLag(issuerMaxLag);
        m_wrtSignalGenerator.refreshIssuerMaxLagForBucketPricer();
        m_wrtSignalGenerator.sendStatsUpdatesBatched();
    }
    
    @Override
    public void onIssuerSmoothingUpdated(final long issuerSmoothing) throws Exception {
        LOG.debug("Last issuer smoothing: secCode {}, issuerSmoothing {}, trigger seqNum {}", m_security.code(), box(issuerSmoothing), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        m_speedArbWrtParams.issuerSmoothing(issuerSmoothing);
        m_stateMachine.onEventReceived(EventIds.ISSUER_SMOOTHING_COMPLETED);
        m_wrtSignalGenerator.sendStatsUpdatesBatched();
    }
    
    public void onUpdatedAllowStopLossOnWideSpread() throws Exception {
        m_stateMachine.onEventReceived(EventIds.ALLOW_STOP_LOSS_ON_WIDE_SPREAD_UPDATED);
    }
    
    public void onUpdatedIgnoreMmSizeOnSell() throws Exception {
        m_stateMachine.onEventReceived(EventIds.IGNORE_MM_SIZE_ON_SELL_UPDATED);
    }
    
    public void onUpdatedSellAtBreakEvenOnly() throws Exception {
        
    }
    
    public void onUpdatedDoNotSell() {
        // why didn't use m_stateMachine.onEventReceived?
        if (isOn()) {
            if (!m_speedArbWrtParams.doNotSell() && m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss())) {
                m_speedArbWrtParams.stopLoss(m_wrtSignalGenerator.getSpotPrice());
                LOG.info("Do not sell turned off while stop loss is hit. Updating stop loss to spot: secCode {}, value {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            }
        }
    }
    
    public void onUpdatedSellOnBreakEvenOnly() {
        // why didn't use m_stateMachine.onEventReceived?
        if (isOn()) {
            if (!m_speedArbWrtParams.sellAtBreakEvenOnly() && m_callPutBridge.isSpotLesserThan(m_speedArbWrtParams.stopLoss())) {
                m_speedArbWrtParams.stopLoss(m_wrtSignalGenerator.getSpotPrice());
                LOG.info("Sell at breakeven only turned off while stop loss is hit. Updating stop loss to spot: secCode {}, value {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            }
        }
    }
    
    public void onUpdatedTriggerGenerator() {
        if (m_hasStarted) {
            subscribeTriggerGenerator();
        }
    }
    
    private void subscribeTriggerGenerator() {
        m_triggerController.subscribe(m_security, m_speedArbWrtParams.strategyTriggerType(), this);
    }

    private void printStats() {
        m_wrtSignalGenerator.printStats();
    }
    
    @Override
    public void onTriggerGeneratorSubscribed(final TriggerGenerator generator) {
        if (generator != m_triggerGenerator) {
            if (m_triggerGenerator != null) {
                m_triggerGenerator.unregisterHandler(this);
            }
            m_triggerGenerator = generator;
        }        
    }

    @Override
    public void onTriggerGeneratorUnsubscribed(final TriggerGenerator generator) {
        if (m_triggerGenerator == generator) {
            m_triggerGenerator = null;
        }
    }
    
    @Override
    public void onDeltaLimitExceeded(final Security triggerSecurity, final long nanoOfDay, final long netDelta) throws Exception {
        m_deltaLimitExceedExpiryTime = nanoOfDay + DELTA_LIMIT_EFFECT_TIME;
        // if trigger security is this, then we expect onOrderBookUpdate event on this cycle anyway
        if (triggerSecurity != m_security) {
            m_stateMachine.onEventReceived(EventIds.DELTA_LIMIT_ALERT_RECEIVED);
        }
    }
    
    private int handleWarrantPriceUpdateOnPosition() throws Exception {
        if (m_sellBanUntilNanoOfDay > 0 && (m_wrtSignalGenerator.getMMAskPrice() != m_wrtSignalGenerator.getPrevMMAskPrice() || m_wrtSignalGenerator.getMMAskPrice() == 0 || m_wrtSignalGenerator.getMMBidPrice() != m_wrtSignalGenerator.getPrevMMBidPrice() || m_wrtSignalGenerator.getMMBidPrice() == 0)) {
            LOG.debug("Selling ban lifted: secCode {}, trigger seqNum {}", m_security.code(), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            m_sellBanUntilNanoOfDay = 0;
        }
        if (m_reentryBan && m_wrtSignalGenerator.getMMBidLevel() != m_mmBidLevelAtBuy) {
            m_reentryBan = false;
        }
        m_highWarrantBid = Math.max(m_highWarrantBid, m_wrtSignalGenerator.getBidPrice());
        return checkForExitOnWarrantPriceUpdate();
    }
    
    private int checkForExitOnWarrantPriceUpdate() throws Exception {
        int nextTransition = -1;
        if (m_orderService.canTrade()) {
            if(m_wrtSignalGenerator.getBidPrice() > 0) {
                if (nextTransition != TransitionIds.SELL_POSITION && m_speedArbWrtParams.sellAtBreakEvenOnly() && m_wrtSignalGenerator.getBidLevel() >= m_speedArbWrtParams.enterLevel()) {
                    nextTransition = tryEnterSellingPositionState(StrategyExplainType.USER_CAPTURE_PROFIT_SIGNAL, m_wrtSignalGenerator.getBidPrice(), nextTransition);
                }
                if (m_wrtSignalGenerator.getBidLevel() >= m_speedArbWrtParams.exitLevel()) {
                    m_speedArbWrtParams.profitRun(m_wrtSignalGenerator.getBidLevel() - m_speedArbWrtParams.enterLevel());
                    if (nextTransition != TransitionIds.SELL_POSITION) {
                        if (m_speedArbWrtParams.profitRun() >= m_speedArbWrtParams.runTicksThreshold()) {
                            nextTransition = tryEnterSellingPositionStateAtOneBelowBid(StrategyExplainType.PROFIT_RUN_SELL_SIGNAL, nextTransition);
                        }
                        else if (!m_speedArbWrtParams.sellOnVolDown() && (m_security.availablePosition() * (m_wrtSignalGenerator.getBidPrice() - m_speedArbWrtParams.enterPrice())) >= m_speedArbWrtParams.stopProfit()) {
                            nextTransition = tryEnterSellingPositionStateAtOneBelowBid(StrategyExplainType.STOP_PROFIT_SELL_SIGNAL, nextTransition);
                        }
                        if (m_strategyModeBridge.sellOnHitExitLevel() && m_speedArbWrtParams.exitLevel() != 0) {
                            nextTransition = tryEnterSellingPositionStateAtOneBelowBidWithCheck(StrategyExplainType.CLOSING_PROFIT_RUN_SELL_SIGNAL, nextTransition);
                        }
                    }
                    if (m_wrtSignalGenerator.getBidPrice() == m_wrtSignalGenerator.getMMBidPrice()) {
                        reviseStopLossAndExitLevel();
                        if (nextTransition == -1) {
                            nextTransition = TransitionIds.PROFIT_RUN;
                        }
                    }
                    else if (nextTransition != TransitionIds.SELL_POSITION && m_wrtSignalGenerator.getBidPrice() > m_speedArbWrtParams.enterPrice() && m_speedArbWrtParams.sellToNonIssuer()) {
                        nextTransition = tryEnterSellingPositionStateAtBid(StrategyExplainType.NON_ISSUER_BID_SELL_SIGNAL, nextTransition);
                    }
                    if (nextTransition != TransitionIds.SELL_POSITION && m_wrtSignalGenerator.getBidPrice() > m_speedArbWrtParams.enterPrice() && m_wrtSignalGenerator.lastTickNanoOfDay() <= m_quickProfitUntilNanoOfDay) {
                        nextTransition = tryEnterSellingPositionStateAtOneBelowBid(StrategyExplainType.QUICK_PROFIT_SELL_SIGNAL, nextTransition);
                    }
                }
                if (nextTransition != TransitionIds.SELL_POSITION && isStopLossTriggered()) {
                    if (m_wrtSignalGenerator.getBidPrice() > m_speedArbWrtParams.enterPrice()) {
                        nextTransition = tryEnterSellingPositionStateAtOneBelowBid(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, nextTransition);
                    }
                    else if (m_wrtSignalGenerator.getBidPrice() == m_speedArbWrtParams.enterPrice() || m_wrtSignalGenerator.getMmSpread() <= m_speedArbWrtParams.enterMMSpread() || m_wrtSignalGenerator.isLooselyTight()){
                        nextTransition = tryEnterSellingPositionStateAtOneBelowBidWithCheck(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, nextTransition);
                    }
                    else if (m_strategyModeBridge.allowStopLossOnWideSpread()) {
                        nextTransition = tryEnterSellingPositionStateAtOneBelowBidWithCheck(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, nextTransition);
                    }                            
                }
                if (m_targetSellPrice != Integer.MAX_VALUE) {
                    if (nextTransition != TransitionIds.SELL_POSITION && m_wrtSignalGenerator.getBidPrice() >= m_targetSellPrice) {
                        nextTransition = tryEnterSellingPositionState(StrategyExplainType.TO_MAKING_SELL_SIGNAL, m_targetSellPrice, nextTransition);
                    }
                }
                else if (m_wrtSignalGenerator.getBidLevel() >= m_speedArbWrtParams.enterLevel()) {
                    if (nextTransition != TransitionIds.SELL_POSITION && m_speedArbIssuerUndParams.undTradeVolThreshold() != 0 && m_wrtSignalGenerator.lastTickNanoOfDay() <= this.m_deltaLimitExceedExpiryTime) {
                        nextTransition = tryEnterSellingPositionStateAtEnterPrice(StrategyExplainType.DELTA_LIMIT_SELL_SIGNAL, nextTransition);
                    }
                    if (nextTransition != TransitionIds.SELL_POSITION && m_speedArbWrtParams.tradesVolumeThreshold() != 0 && m_wrtSignalGenerator.lastTickNanoOfDay() <= this.m_largeOutstandingExceedExpiryTime) {
                        nextTransition = trySellExcessPositionAtEnterPrice(StrategyExplainType.LARGE_OUTSTANDING_SELL_SIGNAL, nextTransition);
                    }
                }
            }
        }
        return nextTransition;
    }
    
    private int handleSpotUpdateOnPosition() throws Exception {
        if (m_orderService.canTrade()) {
            m_callPutBridge.updateBestSpot();
            if (isStopLossTriggered()) {                        
                if (m_wrtSignalGenerator.getBidPrice() > m_speedArbWrtParams.enterPrice()) {
                    return tryEnterSellingPositionStateAtOneBelowBid(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, -1);
                }
                else if (m_wrtSignalGenerator.getBidPrice() == m_speedArbWrtParams.enterPrice() || m_wrtSignalGenerator.getMmSpread() <= m_speedArbWrtParams.enterMMSpread() || m_wrtSignalGenerator.isLooselyTight()){
                    return tryEnterSellingPositionStateAtOneBelowBidWithCheck(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, -1);
                }
                else if (m_strategyModeBridge.allowStopLossOnWideSpread()) {
                    return tryEnterSellingPositionStateAtOneBelowBidWithCheck(StrategyExplainType.STOP_LOSS_SELL_SIGNAL, -1);
                }
            }
            else if (m_speedArbWrtParams.stopLossTrigger() > 0 && m_callPutBridge.isSpotHigherThanOrEqualTo(m_speedArbWrtParams.stopLossTrigger())) {
                LOG.debug("Stop loss trigger activated: secCode {}, stopLossTrigger: {}, trigger seqNum {}",  m_security.code(), box(m_speedArbWrtParams.stopLossTrigger()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));                        
                m_speedArbWrtParams.stopLoss(m_speedArbWrtParams.stopLossTrigger());
                m_speedArbWrtParams.stopLossTrigger(0);
                onStopLossExternallyUpdated();
                m_wrtSignalGenerator.sendStatsUpdatesBatched();
            }
            if (!m_reentryBan && !m_strategyModeBridge.offWhenExitPosition() && m_speedArbWrtParams.allowAdditionalBuy()) {
                final StrategyExplainType strategyExplainType = hasBuyTrigger();
                if (strategyExplainType != StrategyExplainType.NULL_VAL) {
                    m_strategyExplain.strategyExplain(strategyExplainType);
                    buyAdditional();
                }
            }                    
        }
        return -1;
    }
    
    private int handleIssuerVolDownOnPosition(final boolean fromWarrantTick) {
        int nextTransition = -1;
        m_buyBanUntilNanoOfDay = Math.max(m_buyBanUntilNanoOfDay, m_wrtSignalGenerator.lastTickNanoOfDay() + m_speedArbWrtParams.banPeriodToDownVol());
        m_sellFlags |= SpeedArbHybridExplain.Flags.VOL_DOWN;
        LOG.debug("Buying ban set due to issuer down vol: secCode {}, banUntil {}, trigger seqNum {}", m_security.code(), box(m_buyBanUntilNanoOfDay), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        if (m_speedArbWrtParams.stopLossAdjustment() != 0) {
            m_speedArbWrtParams.stopLossAdjustment(0);
            m_wrtSignalGenerator.sendStatsUpdatesThrottled();
        }
        if (fromWarrantTick && m_speedArbWrtParams.sellOnVolDown() && m_wrtSignalGenerator.lastTickNanoOfDay() >= m_sellOnVolDownBanUntilNanoOfDay) {
            if (m_wrtSignalGenerator.getBidPrice() > 0 && m_wrtSignalGenerator.getBidPrice() >= m_speedArbWrtParams.enterPrice()) {
                if (m_targetSellPrice != Integer.MAX_VALUE && m_wrtSignalGenerator.getBidPrice() < m_targetSellPrice) {
                    LOG.debug("Cannot sell on down vol due to bid below turnover making price: secCode {}, turnoverMakingPrice {}, trigger seqNum {}", m_security.code(), box(m_targetSellPrice), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                }
                else {
                    nextTransition = tryEnterSellingPositionStateAtOneBelowBid(StrategyExplainType.ISSUER_DOWN_VOL_SELL_SIGNAL, nextTransition);
                }
            }
            else if (m_wrtSignalGenerator.isLooselyTight()) {
                nextTransition = tryEnterSellingPositionStateAtOneBelowBidWithCheck(StrategyExplainType.ISSUER_DOWN_VOL_SELL_SIGNAL, nextTransition);
            }
        }
        if (m_speedArbWrtParams.resetStopLossOnVolDown()) {
            if (m_wrtSignalGenerator.getMMBidLevel() >= SpreadTable.SPREAD_TABLE_MIN_LEVEL) {
                final int stopLossBid;
                final long spotAdjustment;
                final long stopLossBuffer;
                if (m_speedArbWrtParams.marketOutlook() == m_callPutBridge.getDesirableOutlookType()) {
                    stopLossBid = m_wrtSignalGenerator.getMMBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getMMBidLevel() - 1) : m_wrtSignalGenerator.getMMBidPrice();
                    spotAdjustment = m_callPutBridge.getSpotChangeRequired(stopLossBid - m_wrtSignalGenerator.getMMBidPrice());
                    stopLossBuffer = 0;
                }
                else {
                    stopLossBid = m_wrtSignalGenerator.getMMBidPrice();
                    final int prevMmBidPrice = m_wrtSignalGenerator.getMMBidLevel() > SpreadTable.SPREAD_TABLE_MIN_LEVEL ? m_spreadTable.tickToPrice(m_wrtSignalGenerator.getMMBidLevel() - 1) : m_wrtSignalGenerator.getMMBidPrice();
                    spotAdjustment = 0;
                    stopLossBuffer = m_callPutBridge.getSpotChangeRequired(prevMmBidPrice - stopLossBid) / 2;
                }
                // give half tick buffer because buckets are reset after issuer dropped vol
                m_targetStopLoss = getRestrictedTargetStopLoss(m_wrtSignalGenerator.getBucketPricer(), stopLossBid, m_wrtSignalGenerator.getSpotPrice(), spotAdjustment, m_targetStopLoss, stopLossBuffer);
                if (m_callPutBridge.canUpdateStopLoss(m_targetStopLoss)) {
                    m_speedArbWrtParams.exitLevel(m_wrtSignalGenerator.getMMBidLevel() + 1);
                    if (m_strategyModeBridge.canReviseStopLoss()) {
                        m_speedArbWrtParams.stopLoss(m_targetStopLoss);
                    }
                    m_wrtSignalGenerator.sendStatsUpdatesBatched();
                    LOG.debug("Update exit level and stop loss: secCode {}, exitLevel {}, stopLoss {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.exitLevel()), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
                }
            }
            else {
                m_speedArbWrtParams.exitLevel(0);
                m_wrtSignalGenerator.sendStatsUpdatesBatched();
                LOG.debug("Update exit level and stop loss: secCode {}, exitLevel {}, stopLoss {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.exitLevel()), box(m_speedArbWrtParams.stopLoss()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            }
        }
        return nextTransition;
    }

    private boolean hasLargeTradeVolumeSignal() {
        if (m_speedArbWrtParams.tradesVolumeThreshold() == 0) {
            return false;
        }
        final long tradesVolumeThreshold = adjustedTradesVolumeThreshold(m_security.lastMarketTrade().price());
        if (m_tradesVolumeAtBuy >= tradesVolumeThreshold && m_largeOutstandingExceedExpiryTime == 0) {
            m_largeOutstandingExceedExpiryTime = m_wrtSignalGenerator.lastTickNanoOfDay() + LARGE_OUTSTANDING_EFFECT_TIME;
            return true;
        }
        return false;
    }

    private boolean updateTradesVolumeAtBuy() {
        switch (m_security.lastMarketTrade().side()) {
        case MarketTrade.ASK:
            if (m_wrtSignalGenerator.getMmSpread() <= 3) {
                m_tradesVolumeAtBuy += m_security.lastMarketTrade().quantity();
                return true;
            }
            break;
        case MarketTrade.BID:
            m_tradesVolumeAtBuy -= m_security.lastMarketTrade().quantity();
        }
        return false;
    }
    
    private boolean adjustOrderSizeOnPositionSold() {
        if (m_ourTradedPrice != 0) {
            final boolean increaseOrderSize;
            final boolean decreaseOrderSize;
            if (m_ourTradedPrice > m_speedArbWrtParams.enterPrice()) {
                m_consecutiveWins++;
                increaseOrderSize = m_consecutiveWins > 1 || this.m_strategyExplain.strategyExplain() == StrategyExplainType.PROFIT_RUN_SELL_SIGNAL || this.m_strategyExplain.strategyExplain() == StrategyExplainType.QUICK_PROFIT_SELL_SIGNAL;
                decreaseOrderSize = false;
            }
            else if (m_ourTradedPrice == m_speedArbWrtParams.enterPrice()) {
                increaseOrderSize = false;
                decreaseOrderSize = false;
            }
            else {
                m_consecutiveWins = 0;
                increaseOrderSize = false;
                decreaseOrderSize = true;
            }
            if (m_speedArbWrtParams.orderSizeIncrement() > 0) {
                if (increaseOrderSize) {
                    if (m_speedArbWrtParams.currentOrderSize() < m_speedArbWrtParams.baseOrderSize()) {
                        adjustOrderSizeOnPositionSold(m_speedArbWrtParams.baseOrderSize());
                    }
                    else {
                        adjustOrderSizeOnPositionSold(Math.min(m_maxCurrentOrderSize, m_speedArbWrtParams.currentOrderSize() + m_speedArbWrtParams.orderSizeIncrement()));
                    }
                }
                else if (decreaseOrderSize) {
                    final int tradedPriceLevel = m_spreadTable.priceToTick(m_ourTradedPrice);
//                    LOG.info("Adjust order size: [lastTimeLoss:{}, tradedPrice:{}, tradedPriceLevel:{}, m_mmBidPriceAtBuy:{}, m_mmBidLevelAtBuy:{}, lastTimeLossInTicks:{}, m_spreadAtBuy:{}]", 
//                    		lastTimeLoss, m_ourTradedPrice, tradedPriceLevel, m_spreadTable.tickToPrice(m_mmBidLevelAtBuy), m_mmBidLevelAtBuy, lastTimeLossInTicks, m_spreadAtBuy);
                    if (m_speedArbWrtParams.currentOrderSize() > m_speedArbWrtParams.baseOrderSize() && (tradedPriceLevel < m_mmBidLevelAtBuy - 1)) {
                        adjustOrderSizeOnPositionSold(m_speedArbWrtParams.baseOrderSize() / 2);
                    }
                    else if (m_speedArbWrtParams.currentOrderSize() < m_speedArbWrtParams.baseOrderSize()) {
                        adjustOrderSizeOnPositionSold(m_speedArbWrtParams.currentOrderSize() / 2);
                    }
                    else {
                        adjustOrderSizeOnPositionSold(Math.max(0, m_speedArbWrtParams.currentOrderSize() - m_speedArbWrtParams.orderSizeIncrement()));
                    }
                }
            }
            return increaseOrderSize || decreaseOrderSize;
        }
        return false;
    }
    
    private void adjustOrderSizeOnPositionSold(final int newOrderSize) {
        m_speedArbWrtParams.currentOrderSize(newOrderSize);
        calculateOrderSize();
        LOG.debug("Order size updated: secCode {}, currentOrderSize {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.currentOrderSize()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        
    }
    
    public void initialize() {
        calculateMaxOrderSize();
        capAndCalculateOrderSize();
        calculateTradesVolumeThreshold();
        calculateOrderSizeRemainder();
    }
    
    private void calculateTradesVolumeThreshold() {
        this.m_largePriceTradesVolumeThreshold = m_speedArbWrtParams.tradesVolumeThreshold() / 5;
        this.m_veryLargePriceTradesVolumeThreshold = m_speedArbWrtParams.tradesVolumeThreshold() / 10;
    }
    
    public void calculateMaxOrderSize() {
        this.m_maxCurrentOrderSize = m_speedArbWrtParams.orderSizeIncrement() == 0 ? m_speedArbWrtParams.maxOrderSize() : (m_speedArbWrtParams.maxOrderSize() / m_speedArbWrtParams.orderSizeIncrement()) * m_speedArbWrtParams.orderSizeIncrement();
        if (this.m_maxCurrentOrderSize < m_speedArbWrtParams.maxOrderSize()) {
            this.m_maxCurrentOrderSize += m_speedArbWrtParams.orderSizeIncrement();
        }
        this.m_maxLargePriceOrderSize = m_speedArbWrtParams.maxOrderSize() / 5;
        this.m_maxVeryLargePriceOrderSize = m_speedArbWrtParams.maxOrderSize() / 10;
    }
    
    public void calculateOrderSize() {
        m_speedArbWrtParams.orderSize(Math.min((m_speedArbWrtParams.currentOrderSize() * m_speedArbWrtParams.orderSizeMultiplier()) / 1000, m_speedArbWrtParams.maxOrderSize()));
        this.m_largePriceOrderSize = Math.min((int)((m_speedArbWrtParams.currentOrderSize() * m_speedArbWrtParams.orderSizeMultiplier()) / 5000), this.m_maxLargePriceOrderSize);
        this.m_veryLargePriceOrderSize = Math.min((int)((m_speedArbWrtParams.currentOrderSize() * m_speedArbWrtParams.orderSizeMultiplier()) / 10_000), this.m_maxVeryLargePriceOrderSize);
        if (m_security.lotSize() > 0) {
            m_speedArbWrtParams.orderSize(Math.max(m_security.lotSize(), (m_speedArbWrtParams.orderSize() / m_security.lotSize()) * m_security.lotSize()));
            m_largePriceOrderSize = Math.max(m_security.lotSize(), (m_largePriceOrderSize / m_security.lotSize()) * m_security.lotSize());
            m_veryLargePriceOrderSize = Math.max(m_security.lotSize(), (m_veryLargePriceOrderSize / m_security.lotSize()) * m_security.lotSize());
        }
    }

    public void calculateOrderSizeRemainder() {
    	if (m_speedArbWrtParams.orderSizeRemainder() > 0) {
	        m_orderSizeRemainder = m_speedArbWrtParams.orderSizeRemainder();
	        m_largePriceOrderSizeRemainder = m_orderSizeRemainder / 5;
	        m_veryLargePriceOrderSizeRemainder = m_orderSizeRemainder / 10;
	        if (m_security.lotSize() > 0) {
	            m_orderSizeRemainder = Math.max(m_security.lotSize(), (m_orderSizeRemainder / m_security.lotSize()) * m_security.lotSize());
	            m_largePriceOrderSizeRemainder = Math.max(m_security.lotSize(), (m_largePriceOrderSizeRemainder / m_security.lotSize()) * m_security.lotSize());
	            m_veryLargePriceOrderSizeRemainder = Math.max(m_security.lotSize(), (m_veryLargePriceOrderSizeRemainder / m_security.lotSize()) * m_security.lotSize());
	        }
    	}
    	else {
	        m_orderSizeRemainder = 0;
	        m_largePriceOrderSizeRemainder = 0;
	        m_veryLargePriceOrderSizeRemainder = 0;
    	}
    }
    
    public void capAndCalculateOrderSize() {
        m_speedArbWrtParams.currentOrderSize(Math.min(m_speedArbWrtParams.currentOrderSize(), this.m_maxCurrentOrderSize));
        calculateOrderSize();
    }
    
    private StrategyStatusType strategyStatus() {
        return this.m_strategyModeBridge.defaultStrategyStatus();
    }
    
    private long adjustedTradesVolumeThreshold(final int price) {
        if (price < LARGE_WARRANT_PRICE) {
            return m_speedArbWrtParams.tradesVolumeThreshold();
        }
        else if (price < VERY_LARGE_WARRANT_PRICE) {
            return m_largePriceTradesVolumeThreshold;    
        }
        else {
            return m_veryLargePriceTradesVolumeThreshold;
        }        
    }
    
    private void adjustSafeBidBuffer() {
        final int safeBidLevelBuffer = m_speedArbWrtParams.enterBidLevel() - Math.max(SpreadTable.SPREAD_TABLE_MIN_LEVEL, this.m_wrtSignalGenerator.getBidLevel() - GenericWrtParams.DEFAULT_SAFE_BID_BUFFER_FROM_CURRENT_PRICE);
        if (safeBidLevelBuffer < m_speedArbWrtParams.safeBidLevelBuffer()) {
            m_speedArbWrtParams.safeBidLevelBuffer(safeBidLevelBuffer);
            updateSafeBidPrice();
            LOG.info("Safe bid level buffer and safe bid price updated: secCode {}, safeBidLevelBuffer {}, safeBidPrice {}, trigger seqNum {}", m_security.code(), box(m_speedArbWrtParams.safeBidLevelBuffer()), box(m_speedArbWrtParams.safeBidPrice()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
        }
    }
    
    public void updateSafeBidPrice() {
    	final int bidLevel = Math.max(SpreadTable.SPREAD_TABLE_MIN_LEVEL, this.m_speedArbWrtParams.enterBidLevel() - m_speedArbWrtParams.safeBidLevelBuffer());
    	m_speedArbWrtParams.safeBidPrice(m_spreadTable.tickToPrice(bidLevel));
    }
    
    private boolean doNotSell() {
        if (m_speedArbWrtParams.doNotSell() || (m_speedArbWrtParams.sellAtBreakEvenOnly() && m_wrtSignalGenerator.getBidPrice() < m_speedArbWrtParams.enterPrice())) {
            LOG.debug("Cannot sell position due to selling disallowed: secCode {}, reason {}, trigger seqNum {}", m_security.code(), box(m_strategyExplain.strategyExplain().value()), box(m_wrtSignalGenerator.triggerInfo().triggerSeqNum()));
            return true;
        }
        return false;
    }
    
    public void onUpdatedTradesVolumeThreshold() {
        calculateTradesVolumeThreshold();
        if (m_wrtSignalGenerator.lastTickNanoOfDay() > this.m_largeOutstandingExceedExpiryTime) {
            m_largeOutstandingExceedExpiryTime = 0;
        }
    }

}
