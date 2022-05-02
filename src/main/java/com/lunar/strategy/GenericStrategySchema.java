package com.lunar.strategy;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.Collection;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketOutlookType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.SpreadState;
import com.lunar.message.io.sbe.StrategyExitMode;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategyParamType;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyStatusType;
import com.lunar.message.io.sbe.StrategyTriggerType;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.parameters.IssuerInputParams;
import com.lunar.strategy.parameters.IssuerOutputParams;
import com.lunar.strategy.parameters.IssuerUndInputParams;
import com.lunar.strategy.parameters.IssuerUndOutputParams;
import com.lunar.strategy.parameters.ParamsSbeEncodable;
import com.lunar.strategy.parameters.StrategyTypeInputParams;
import com.lunar.strategy.parameters.UndInputParams;
import com.lunar.strategy.parameters.UndOutputParams;
import com.lunar.strategy.parameters.WrtInputParams;
import com.lunar.strategy.parameters.WrtOutputParams;

@SuppressWarnings("unchecked")
public class GenericStrategySchema implements StrategySchema {
    private static final Logger LOG = LogManager.getLogger(GenericStrategySchema.class);

    private static final int MAX_ID = 106;

    public static final int STATUS_ID = 1;
    public static final int ACTIVE_STRATEGY_ID = 2;
    public static final int EXIT_MODE_ID = 14;

    public static final int VELOCITY_THRESHOLD_ID = 16;

    public static final int MM_BID_SIZE_ID = 20;
    public static final int MM_ASK_SIZE_ID = 21;
    public static final int IN_OUT_CURRENT_ORDER_SIZE_ID = 22;
    public static final int ORDER_SIZE_MULTIPLIER_ID = 90;
    public static final int PROFIT_RUN_TICKS_ID = 23;
    public static final int SELL_AT_QUICK_PROFIT_ID = 26;
    public static final int TICK_SENSITIVITY_THRESHOLD_ID = 10;
    public static final int IN_OUT_STOP_LOSS_ID = 51;
    public static final int IN_OUT_STOP_LOSS_TRIGGER_ID = 24;
    public static final int STOP_PROFIT_ID = 76;    
    public static final int ALLOWED_MAX_SPREAD_ID = 25;
    public static final int WIDE_SPREAD_BUFFER_ID = 27;
    public static final int TURNOVER_MAKING_SIZE_ID = 71;
    public static final int TURNOVER_MAKING_PERIOD_ID = 72;
    public static final int BAN_TO_DOWNVOL_PERIOD_ID = 78;
    public static final int BAN_TO_TOMAKE_PERIOD_ID = 79;
    public static final int SELLING_BAN_PERIOD_ID = 81;
    public static final int HOLDING_PERIOD_ID = 82;
    public static final int SPREAD_OBSERVE_PERIOD_ID = 77;
    public static final int MARKET_OUTLOOK_ID = 73;
    public static final int SELL_ON_VOL_DOWN_ID = 80;    
    public static final int IN_OUT_ISSUER_MAX_LAG_ID = 75;
    public static final int ALLOW_STOPLOSS_ON_FLASHING_BID_ID = 83;
    public static final int RESET_STOPLOSS_ON_DOWNVOL_ID = 84;
    public static final int DEFAULT_PRICING_MODE_ID = 87;
    public static final int STRATEGY_TRIGGER_TYPE_ID = 86;
    public static final int ISSUER_MAX_LAG_CAP_ID = 85;
    public static final int SELL_TO_NON_ISSUER_ID = 88;
    public static final int TICK_BUFFER_ID = 89;
    public static final int MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID = 92;
    public static final int IN_OUT_SAFE_BID_LEVEL_BUFFER_ID = 93;
    public static final int STOP_LOSS_TICK_BUFFER_ID = 96;
    public static final int BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID = 98;
    public static final int USE_HOLD_BID_BAN_ID = 32; 
    public static final int IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID = 33;
    public static final int TRADES_VOLUME_THRESHOLD_ID = 34;
    public static final int BASE_ORDER_SIZE_ID = 35;
    public static final int MAX_ORDER_SIZE_ID = 36;
    public static final int ORDER_SIZE_INCREMENT_ID = 37;
    public static final int IN_OUT_DO_NOT_SELL_ID = 38;
    public static final int ORDER_SIZE_REMAINDER_ID = 39;
    public static final int IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID = 40;
    public static final int IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID = 41;

    public static final int OUT_ACTUAL_ORDER_SIZE_ID = 91; 
    public static final int OUT_TICK_SENSITIVITY_ID = 53;
    public static final int OUT_WARRANT_SPREAD_ID = 55;
    public static final int OUT_PRICING_MODE_ID = 67;    
    public static final int OUT_PRICING_DELTA_ID = 30;
    public static final int OUT_IMPLIED_VOL_ID = 31;

    public static final int OUT_ENTER_PRICE_ID = 54;
    public static final int OUT_EXIT_LEVEL_ID = 50;    
    public static final int OUT_PROFIT_RUN_ID = 52;

    public static final int OUT_NUM_SPREAD_RESETS_ID = 62;
    public static final int OUT_NUM_WAVG_DOWNVOLS_ID = 63;
    public static final int OUT_NUM_WAVG_UPVOLS_ID = 64;
    public static final int OUT_NUM_MPRC_DOWNVOLS_ID = 65;
    public static final int OUT_NUM_MPRC_UPVOLS_ID = 66;
    public static final int OUT_ISSUER_LAG_ID = 68;
    public static final int OUT_ISSUER_SMOOTHING_ID = 69;
    public static final int OUT_SAFE_BID_PRICE_ID = 94;
    public static final int OUT_SPREAD_STATE_ID = 95;
    public static final int OUT_STOP_LOSS_ADJUSTMENT_ID = 97;    

    public static final int OUT_NUM_ACTIVE_WARRANTS_ID = 60;
    public static final int OUT_NUM_TOTAL_WARRANTS_ID = 61;

    public static final int OUT_ACTIVE_BUCKET_INTERVAL_BEGIN_ID = 99;
    public static final int OUT_ACTIVE_BUCKET_INTERVAL_END_EXCL_ID = 100;
    public static final int OUT_ACTIVE_BUCKET_INTERVAL_DATA_ID = 101;

    public static final int OUT_NEXT_BUCKET_INTERVAL_BEGIN_ID = 102;
    public static final int OUT_NEXT_BUCKET_INTERVAL_END_EXCL_ID = 103;
    public static final int OUT_NEXT_BUCKET_INTERVAL_DATA_ID = 104;
    
    public static final int UND_TRADE_VOL_THRESHOLD_ID = 105;
    public static final int OUT_UND_TRADE_VOL_ID = 106; // LAST ID is 106
    
    private final long[] longParameterValues;

    public GenericStrategySchema() {
        longParameterValues = new long[MAX_ID + 1];
    }

    @Override
    public void handleStratTypeParamUpdateRequest(final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters, final StrategyTypeInputParams params) {
        parameters.next();
        if (parameters.parameterType() == ParameterType.PARAM_ID) {
            final long paramId = parameters.parameterValueLong();
            parameters.next();
            if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                final long paramValue = parameters.parameterValueLong();
                if (paramId == EXIT_MODE_ID) {
                    params.userExitMode(StrategyExitMode.get((byte)paramValue));
                }
                else {
                    throw new IllegalArgumentException("Invalid STRATPARAMUPDATE parameter Id " + paramId);
                }
                return;
            }
        }
        throw new IllegalArgumentException("Received GET request for STRATPARAMUPDATE with unsupported parameters");
    }

    @Override
    public void handleStratUndParamUpdateRequest(final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters, final UndInputParams params) {
        parameters.next();
        if (parameters.parameterType() == ParameterType.PARAM_ID) {
            final long paramId = parameters.parameterValueLong();
            parameters.next();
            if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                final long paramValue = parameters.parameterValueLong();
                if (paramId == VELOCITY_THRESHOLD_ID) {
                    params.userVelocityThreshold(paramValue);
                }
                else {
                    throw new IllegalArgumentException("Invalid STRATUNDPARAMUPDATE parameter Id " + paramId);
                }
                return;
            }
        }                        
        throw new IllegalArgumentException("Received GET request for STRATUNDPARAMUPDATE with unsupported parameters");
    }
    
    @Override
    public void handleStratIssuerUndParamUpdateRequest(final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters, final IssuerUndInputParams params) {
        parameters.next();
        if (parameters.parameterType() == ParameterType.PARAM_ID) {
            final long paramId = parameters.parameterValueLong();
            parameters.next();
            if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                final long paramValue = parameters.parameterValueLong();
                if (paramId == UND_TRADE_VOL_THRESHOLD_ID) {
                	LOG.info("Updated strategy issuer und param in strategy service [threshold:{}]", paramValue);
                    params.userUndTradeVolThreshold(paramValue);
                }
                else {
                    throw new IllegalArgumentException("Invalid STRATISSUERUNDPARAMUPDATE parameter Id " + paramId);
                }
                return;
            }
        }                        
        throw new IllegalArgumentException("Received GET request for STRATISSUERUNDPARAMUPDATE with unsupported parameters");
    }

    @Override
    public void handleStratIssuerParamUpdateRequest(com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters, IssuerInputParams strategyIssuerParams) {

    }
    
    private static final BiConsumer<WrtInputParams, com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder>[] SINGLE_SPEED_ARG_WRT_UPDATES;
    private static final BiConsumer<Collection<WrtInputParams>, com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder>[] MULTIPLE_SPEED_ARG_WRT_UPDATES;
    private static final String[] FIELD_DESCRIPTIONS;

    static {
        final int maxNumElements = MAX_ID + 1;
        FIELD_DESCRIPTIONS = new String[maxNumElements];
        FIELD_DESCRIPTIONS[GenericStrategySchema.EXIT_MODE_ID] = "Exit Mode";         
        FIELD_DESCRIPTIONS[GenericStrategySchema.VELOCITY_THRESHOLD_ID] = "Velocity Threshold";
        FIELD_DESCRIPTIONS[GenericStrategySchema.STATUS_ID] = "Status";
        FIELD_DESCRIPTIONS[GenericStrategySchema.MM_BID_SIZE_ID] = "MM Bid Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.MM_ASK_SIZE_ID] = "MM Ask Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.BASE_ORDER_SIZE_ID] = "Base Order Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_CURRENT_ORDER_SIZE_ID] = "Current Order Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.MAX_ORDER_SIZE_ID] = "Max Order Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.ORDER_SIZE_INCREMENT_ID] = "Order Size Increment";
        FIELD_DESCRIPTIONS[GenericStrategySchema.ORDER_SIZE_REMAINDER_ID] = "Order Size Remainder";
        FIELD_DESCRIPTIONS[GenericStrategySchema.ORDER_SIZE_MULTIPLIER_ID] = "Order size multiplier";
        FIELD_DESCRIPTIONS[GenericStrategySchema.PROFIT_RUN_TICKS_ID] = "Profit Run Threshold";
        FIELD_DESCRIPTIONS[GenericStrategySchema.SELL_AT_QUICK_PROFIT_ID] = "Sell at Quick Profit";
        FIELD_DESCRIPTIONS[GenericStrategySchema.TICK_SENSITIVITY_THRESHOLD_ID] = "Tick Sens Thres";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_STOP_LOSS_ID] = "Stop Loss";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_STOP_LOSS_TRIGGER_ID] = "Stop Loss Trigger";
        FIELD_DESCRIPTIONS[GenericStrategySchema.STOP_PROFIT_ID] = "Stop Profit";
        FIELD_DESCRIPTIONS[GenericStrategySchema.ALLOWED_MAX_SPREAD_ID] = "Allowed Max Spread";
        FIELD_DESCRIPTIONS[GenericStrategySchema.WIDE_SPREAD_BUFFER_ID] = "Wide Spread Buffer";
        FIELD_DESCRIPTIONS[GenericStrategySchema.TURNOVER_MAKING_SIZE_ID] = "Turnover Making Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.TURNOVER_MAKING_PERIOD_ID] = "Turnover Making Period";
        FIELD_DESCRIPTIONS[GenericStrategySchema.BAN_TO_DOWNVOL_PERIOD_ID] = "Ban Period to Down Vol";
        FIELD_DESCRIPTIONS[GenericStrategySchema.BAN_TO_TOMAKE_PERIOD_ID] = "Ban Period to TO Make";
        FIELD_DESCRIPTIONS[GenericStrategySchema.SELLING_BAN_PERIOD_ID] = "Selling Ban Period";
        FIELD_DESCRIPTIONS[GenericStrategySchema.HOLDING_PERIOD_ID] = "Holding Period";
        FIELD_DESCRIPTIONS[GenericStrategySchema.SPREAD_OBSERVE_PERIOD_ID] = "Observe Period";
        FIELD_DESCRIPTIONS[GenericStrategySchema.MARKET_OUTLOOK_ID] = "Market Outlook";
        FIELD_DESCRIPTIONS[GenericStrategySchema.SELL_ON_VOL_DOWN_ID] = "Sell on Down Vol";
        FIELD_DESCRIPTIONS[GenericStrategySchema.BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID] = "Sell on Vol Down Ban Period";
        FIELD_DESCRIPTIONS[GenericStrategySchema.USE_HOLD_BID_BAN_ID] = "Use Hold Bid Ban";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID] = "Allow Stop Loss on Wide Spread";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_DO_NOT_SELL_ID] = "Do Not Sell";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID] = "Ignore Mm Size On Sell";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID] = "Sell only when BreakEven";        
        FIELD_DESCRIPTIONS[GenericStrategySchema.TRADES_VOLUME_THRESHOLD_ID] = "Outstandings Threshold";        
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_ISSUER_MAX_LAG_ID] = "Issuer Max Lag";
        FIELD_DESCRIPTIONS[GenericStrategySchema.ALLOW_STOPLOSS_ON_FLASHING_BID_ID] = "StopLoss Flash Bid";
        FIELD_DESCRIPTIONS[GenericStrategySchema.RESET_STOPLOSS_ON_DOWNVOL_ID] = "StopLoss Down Vol";
        FIELD_DESCRIPTIONS[GenericStrategySchema.DEFAULT_PRICING_MODE_ID] = "Default Pricing Mode";
        FIELD_DESCRIPTIONS[GenericStrategySchema.STRATEGY_TRIGGER_TYPE_ID] = "Strategy Trigger Type";
        FIELD_DESCRIPTIONS[GenericStrategySchema.ISSUER_MAX_LAG_CAP_ID] = "Issuer Max Lag Cap";
        FIELD_DESCRIPTIONS[GenericStrategySchema.SELL_TO_NON_ISSUER_ID] = "Sell to Non Issuer";
        FIELD_DESCRIPTIONS[GenericStrategySchema.TICK_BUFFER_ID] = "Tick Buffer";
        FIELD_DESCRIPTIONS[GenericStrategySchema.STOP_LOSS_TICK_BUFFER_ID] = "Stop Loss Tick Buffer";
        FIELD_DESCRIPTIONS[GenericStrategySchema.MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID] = "Manual Order Ticks from Enter Price";
        FIELD_DESCRIPTIONS[GenericStrategySchema.IN_OUT_SAFE_BID_LEVEL_BUFFER_ID] = "Safe Bid Level Buffer";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ACTUAL_ORDER_SIZE_ID] = "Actual Order Size";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_TICK_SENSITIVITY_ID] = "Tick Sensitivity";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_WARRANT_SPREAD_ID] = "Warrant Spread";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_PRICING_MODE_ID] = "Pricing Mode";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_PRICING_DELTA_ID] = "Delta";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_IMPLIED_VOL_ID] = "Implied Vol";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ENTER_PRICE_ID] = "Enter Price";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_EXIT_LEVEL_ID] = "Exit Level";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_STOP_LOSS_ADJUSTMENT_ID] = "Stop Loss Adjustment";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_PROFIT_RUN_ID] = "Profit Run";        
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_SPREAD_RESETS_ID] = "Target Spread Resets";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_WAVG_DOWNVOLS_ID] = "Num WAvg DownVols";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_WAVG_UPVOLS_ID] = "Num WAvg UpVols";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_MPRC_DOWNVOLS_ID] = "Num MPrc DownVols";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_MPRC_UPVOLS_ID] = "Num MPrc UpVols";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_ACTIVE_WARRANTS_ID] = "Num Active Warrants";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NUM_TOTAL_WARRANTS_ID] = "Num Total Warrants";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ISSUER_LAG_ID] = "Issuer Lag";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ISSUER_SMOOTHING_ID] = "Issuer Smoothing";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_SAFE_BID_PRICE_ID] = "Safe Bid Price";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_SPREAD_STATE_ID] = "Spread State";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ACTIVE_BUCKET_INTERVAL_BEGIN_ID] = "Active Bucket Interval Begin";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ACTIVE_BUCKET_INTERVAL_END_EXCL_ID] = "Active Bucket Interval End";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_ACTIVE_BUCKET_INTERVAL_DATA_ID] = "Active Bucket Interval Data";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NEXT_BUCKET_INTERVAL_BEGIN_ID] = "Next Bucket Interval Begin";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NEXT_BUCKET_INTERVAL_END_EXCL_ID] = "Next Bucket Interval End";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_NEXT_BUCKET_INTERVAL_DATA_ID] = "Next Bucket Interval Data";
        FIELD_DESCRIPTIONS[GenericStrategySchema.UND_TRADE_VOL_THRESHOLD_ID] = "Und Trade Vol Threshold";
        FIELD_DESCRIPTIONS[GenericStrategySchema.OUT_UND_TRADE_VOL_ID] = "Und Trade Vol";

        SINGLE_SPEED_ARG_WRT_UPDATES = (BiConsumer<WrtInputParams, com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder>[])new BiConsumer[maxNumElements];
        for (int i = 0; i < maxNumElements; i++){
            final int paramId = i;
            SINGLE_SPEED_ARG_WRT_UPDATES[paramId] = (items, decoder) -> { throw new IllegalArgumentException("Invalid STRATWRTPARAMUPDATE parameter Id " + paramId); };
        }
        SINGLE_SPEED_ARG_WRT_UPDATES[MM_BID_SIZE_ID ] = (params, decoder) -> { params.userMmBidSize((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[MM_ASK_SIZE_ID ] = (params, decoder) -> { params.userMmAskSize((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[BASE_ORDER_SIZE_ID ] = (params, decoder) -> { params.userBaseOrderSize((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_CURRENT_ORDER_SIZE_ID ] = (params, decoder) -> { params.userCurrentOrderSize((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[MAX_ORDER_SIZE_ID ] = (params, decoder) -> { params.userMaxOrderSize((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[ORDER_SIZE_INCREMENT_ID ] = (params, decoder) -> { params.userOrderSizeIncrement((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[ORDER_SIZE_MULTIPLIER_ID ] = (params, decoder) -> { params.userOrderSizeMultiplier((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[ORDER_SIZE_REMAINDER_ID ] = (params, decoder) -> { params.userOrderSizeRemainder((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[PROFIT_RUN_TICKS_ID ] = (params, decoder) -> { params.userRunTicksThreshold((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[SELL_AT_QUICK_PROFIT_ID ] = (params, decoder) -> { params.userSellAtQuickProfit(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[TICK_SENSITIVITY_THRESHOLD_ID ] = (params, decoder) -> { params.userTickSensitivityThreshold((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_STOP_LOSS_ID ] = (params, decoder) -> { params.userStopLoss(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_STOP_LOSS_TRIGGER_ID ] = (params, decoder) -> { params.userStopLossTrigger(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[STOP_PROFIT_ID ] = (params, decoder) -> { params.userStopProfit(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[ALLOWED_MAX_SPREAD_ID ] = (params, decoder) -> { params.userAllowedMaxSpread((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[WIDE_SPREAD_BUFFER_ID ] = (params, decoder) -> { params.userWideSpreadBuffer((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[TURNOVER_MAKING_SIZE_ID ] = (params, decoder) -> { params.userTurnoverMakingSize((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[TURNOVER_MAKING_PERIOD_ID ] = (params, decoder) -> { params.userTurnoverMakingPeriod(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[BAN_TO_DOWNVOL_PERIOD_ID ] = (params, decoder) -> { params.userBanPeriodToDownVol(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[BAN_TO_TOMAKE_PERIOD_ID ] = (params, decoder) -> { params.userBanPeriodToTurnoverMaking(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[SELLING_BAN_PERIOD_ID ] = (params, decoder) -> { params.userSellingBanPeriod(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[HOLDING_PERIOD_ID ] = (params, decoder) -> { params.userHoldingPeriod(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[SPREAD_OBSERVE_PERIOD_ID ] = (params, decoder) -> { params.userSpreadObservationPeriod(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[MARKET_OUTLOOK_ID ] = (params, decoder) -> { params.userMarketOutlook(MarketOutlookType.get((byte)decoder.parameterValueLong())); };
        SINGLE_SPEED_ARG_WRT_UPDATES[SELL_ON_VOL_DOWN_ID ] = (params, decoder) -> { params.userSellOnVolDown(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID] = (params, decoder) -> { params.userSellOnVolDownBanPeriod(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[USE_HOLD_BID_BAN_ID] = (params, decoder) -> { params.userUseHoldBidBan(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID] = (params, decoder) -> { params.userAllowStopLossOnWideSpread(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_DO_NOT_SELL_ID] = (params, decoder) -> { params.userDoNotSell(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID] = (params, decoder) -> { params.userIgnoreMmSizeOnSell(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID] = (params, decoder) -> { params.userSellAtBreakEvenOnly(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[TRADES_VOLUME_THRESHOLD_ID] = (params, decoder) -> { params.userTradesVolumeThreshold(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_ISSUER_MAX_LAG_ID ] = (params, decoder) -> { params.userIssuerMaxLag(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[ALLOW_STOPLOSS_ON_FLASHING_BID_ID ] = (params, decoder) -> { params.userAllowStopLossOnFlashingBid(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[RESET_STOPLOSS_ON_DOWNVOL_ID ] = (params, decoder) -> { params.userResetStopLossOnVolDown(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[DEFAULT_PRICING_MODE_ID] = (params, decoder) -> { params.userDefaultPricingMode(PricingMode.get((byte)decoder.parameterValueLong())); };
        SINGLE_SPEED_ARG_WRT_UPDATES[STRATEGY_TRIGGER_TYPE_ID] = (params, decoder) -> { params.userStrategyTriggerType(StrategyTriggerType.get((byte)decoder.parameterValueLong())); };
        SINGLE_SPEED_ARG_WRT_UPDATES[ISSUER_MAX_LAG_CAP_ID] = (params, decoder) -> { params.userIssuerMaxLagCap(decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[SELL_TO_NON_ISSUER_ID] = (params, decoder) -> { params.userSellToNonIssuer(decoder.parameterValueLong() == 1); };
        SINGLE_SPEED_ARG_WRT_UPDATES[TICK_BUFFER_ID] = (params, decoder) -> { params.userTickBuffer((int)decoder.parameterValueLong()); };        
        SINGLE_SPEED_ARG_WRT_UPDATES[STOP_LOSS_TICK_BUFFER_ID] = (params, decoder) -> { params.userStopLossTickBuffer((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID] = (params, decoder) -> { params.userManualOrderTicksFromEnterPrice((int)decoder.parameterValueLong()); };
        SINGLE_SPEED_ARG_WRT_UPDATES[IN_OUT_SAFE_BID_LEVEL_BUFFER_ID] = (params, decoder) -> { params.userSafeBidLevelBuffer((int)decoder.parameterValueLong()); };

        MULTIPLE_SPEED_ARG_WRT_UPDATES = (BiConsumer<Collection<WrtInputParams>, com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder>[])new BiConsumer[maxNumElements];
        for (int index = 0; index < maxNumElements; index++){
            final int paramId = index;
            MULTIPLE_SPEED_ARG_WRT_UPDATES[paramId] = (list, decoder) -> { throw new IllegalArgumentException("Invalid STRATWRTPARAMUPDATE parameter Id for multiple updates" + paramId); };
        }
        MULTIPLE_SPEED_ARG_WRT_UPDATES[MM_BID_SIZE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userMmBidSize((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[MM_ASK_SIZE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userMmAskSize((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[BASE_ORDER_SIZE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userBaseOrderSize((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_CURRENT_ORDER_SIZE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userCurrentOrderSize((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[MAX_ORDER_SIZE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userMaxOrderSize((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[ORDER_SIZE_INCREMENT_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userOrderSizeIncrement((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[ORDER_SIZE_MULTIPLIER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userOrderSizeMultiplier((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[ORDER_SIZE_REMAINDER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userOrderSizeRemainder((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[PROFIT_RUN_TICKS_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userRunTicksThreshold((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[SELL_AT_QUICK_PROFIT_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSellAtQuickProfit(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[TICK_SENSITIVITY_THRESHOLD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userTickSensitivityThreshold((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_STOP_LOSS_ID] = (list, decoder) -> { LOG.info("Bulk update of stop loss is not allowed"); };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_STOP_LOSS_TRIGGER_ID] = (list, decoder) -> {LOG.info("Bulk update of stop loss trigger is not allowed");};
        MULTIPLE_SPEED_ARG_WRT_UPDATES[STOP_PROFIT_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userStopProfit(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[ALLOWED_MAX_SPREAD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userAllowedMaxSpread((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[WIDE_SPREAD_BUFFER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userWideSpreadBuffer((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[TURNOVER_MAKING_SIZE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userTurnoverMakingSize((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[TURNOVER_MAKING_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userTurnoverMakingPeriod(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[BAN_TO_DOWNVOL_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userBanPeriodToDownVol(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[BAN_TO_TOMAKE_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userBanPeriodToTurnoverMaking(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[SELLING_BAN_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSellingBanPeriod(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[HOLDING_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userHoldingPeriod(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[SPREAD_OBSERVE_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSpreadObservationPeriod(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[MARKET_OUTLOOK_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userMarketOutlook(MarketOutlookType.get((byte)decoder.parameterValueLong())); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[SELL_ON_VOL_DOWN_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSellOnVolDown(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSellOnVolDownBanPeriod(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[USE_HOLD_BID_BAN_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userUseHoldBidBan(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID] = (list, decoder) -> { LOG.info("Bulk update of do not sell is not allowed"); };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_DO_NOT_SELL_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userAllowStopLossOnWideSpread(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID] = (params, decoder) -> { LOG.info("Bulk update of ignore mm size on sell is not allowed"); };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID] = (params, decoder) -> { LOG.info("Bulk update of sell on breakeven only is not allowed"); };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[TRADES_VOLUME_THRESHOLD_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userTradesVolumeThreshold(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_ISSUER_MAX_LAG_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userIssuerMaxLag(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[ALLOW_STOPLOSS_ON_FLASHING_BID_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userAllowStopLossOnFlashingBid(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[RESET_STOPLOSS_ON_DOWNVOL_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userResetStopLossOnVolDown(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[DEFAULT_PRICING_MODE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userDefaultPricingMode(PricingMode.get((byte)decoder.parameterValueLong())); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[STRATEGY_TRIGGER_TYPE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userStrategyTriggerType(StrategyTriggerType.get((byte)decoder.parameterValueLong())); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[ISSUER_MAX_LAG_CAP_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userIssuerMaxLagCap(decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[SELL_TO_NON_ISSUER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSellToNonIssuer(decoder.parameterValueLong() == 1); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[TICK_BUFFER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userTickBuffer((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[STOP_LOSS_TICK_BUFFER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userStopLossTickBuffer((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userManualOrderTicksFromEnterPrice((int)decoder.parameterValueLong()); } };
        MULTIPLE_SPEED_ARG_WRT_UPDATES[IN_OUT_SAFE_BID_LEVEL_BUFFER_ID] = (list, decoder) -> { for (WrtInputParams params : list){ params.userSafeBidLevelBuffer((int)decoder.parameterValueLong()); } };
    }

    @Override
    public void handleStratWrtParamUpdateRequest(final com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder parameters, final WrtInputParams params) {
        parameters.next();
        if (parameters.parameterType() == ParameterType.PARAM_ID) {
            final int paramId = (int)parameters.parameterValueLong();
            parameters.next();
            if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                SINGLE_SPEED_ARG_WRT_UPDATES[paramId].accept(params, parameters);
                return;
            }
        }
        throw new IllegalArgumentException("Received GET request for STRATWRTPARAMUPDATE with unsupported parameters");
    }

    @Override
    public void handleStratWrtParamUpdateRequest(final ParametersDecoder parameters, final Collection<WrtInputParams> strategyWrtParamsList) {
        parameters.next();
        if (parameters.parameterType() == ParameterType.PARAM_ID) {
            final int paramId = (int)parameters.parameterValueLong();
            parameters.next();
            if (parameters.parameterType() == ParameterType.PARAM_VALUE) {
                MULTIPLE_SPEED_ARG_WRT_UPDATES[paramId].accept(strategyWrtParamsList, parameters);
                return;
            }
        }
        throw new IllegalArgumentException("Received GET request for STRATWRTPARAMUPDATE multiple updates with unsupported parameters");
    }

    @Override
    public void populateSchema(final StrategyType strategyType) {        
        strategyType.addField(EXIT_MODE_ID, StrategyParamSource.STRATEGY_ID, StrategyParamType.LONG, BooleanType.FALSE);

        strategyType.addField(VELOCITY_THRESHOLD_ID, StrategyParamSource.UNDERLYING_SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(OUT_NUM_ACTIVE_WARRANTS_ID, StrategyParamSource.UNDERLYING_SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_TOTAL_WARRANTS_ID, StrategyParamSource.UNDERLYING_SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);

        strategyType.addField(OUT_NUM_ACTIVE_WARRANTS_ID, StrategyParamSource.ISSUER_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_TOTAL_WARRANTS_ID, StrategyParamSource.ISSUER_SID, StrategyParamType.LONG, BooleanType.TRUE);

        strategyType.addField(STATUS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(MM_BID_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(MM_ASK_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(BASE_ORDER_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(MAX_ORDER_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(ORDER_SIZE_INCREMENT_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);        
        strategyType.addField(ORDER_SIZE_MULTIPLIER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);        
        strategyType.addField(IN_OUT_CURRENT_ORDER_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(OUT_ACTUAL_ORDER_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(ORDER_SIZE_REMAINDER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);        
        strategyType.addField(PROFIT_RUN_TICKS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(SELL_AT_QUICK_PROFIT_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(TICK_SENSITIVITY_THRESHOLD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_STOP_LOSS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_STOP_LOSS_TRIGGER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(STOP_PROFIT_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(ALLOWED_MAX_SPREAD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(WIDE_SPREAD_BUFFER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(TURNOVER_MAKING_SIZE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(TURNOVER_MAKING_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(BAN_TO_DOWNVOL_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(BAN_TO_TOMAKE_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(SELLING_BAN_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        //strategyType.addField(HOLDING_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(SPREAD_OBSERVE_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(MARKET_OUTLOOK_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(SELL_ON_VOL_DOWN_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(USE_HOLD_BID_BAN_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_ISSUER_MAX_LAG_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        //strategyType.addField(ALLOW_STOPLOSS_ON_FLASHING_BID_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(RESET_STOPLOSS_ON_DOWNVOL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(DEFAULT_PRICING_MODE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(STRATEGY_TRIGGER_TYPE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(ISSUER_MAX_LAG_CAP_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(SELL_TO_NON_ISSUER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(TICK_BUFFER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(STOP_LOSS_TICK_BUFFER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_SAFE_BID_LEVEL_BUFFER_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_DO_NOT_SELL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(TRADES_VOLUME_THRESHOLD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);

        strategyType.addField(OUT_TICK_SENSITIVITY_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_WARRANT_SPREAD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_PRICING_MODE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_PRICING_DELTA_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_IMPLIED_VOL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        //strategyType.addField(OUT_ENTER_PRICE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_EXIT_LEVEL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_PROFIT_RUN_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_STOP_LOSS_ADJUSTMENT_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_SPREAD_RESETS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_WAVG_DOWNVOLS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_WAVG_UPVOLS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_MPRC_DOWNVOLS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NUM_MPRC_UPVOLS_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_ISSUER_LAG_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_ISSUER_SMOOTHING_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_SAFE_BID_PRICE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_SPREAD_STATE_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_ACTIVE_BUCKET_INTERVAL_BEGIN_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_ACTIVE_BUCKET_INTERVAL_END_EXCL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_ACTIVE_BUCKET_INTERVAL_DATA_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NEXT_BUCKET_INTERVAL_BEGIN_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NEXT_BUCKET_INTERVAL_END_EXCL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(OUT_NEXT_BUCKET_INTERVAL_DATA_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
        strategyType.addField(UND_TRADE_VOL_THRESHOLD_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.FALSE);
        strategyType.addField(OUT_UND_TRADE_VOL_ID, StrategyParamSource.SECURITY_SID, StrategyParamType.LONG, BooleanType.TRUE);
    }

    @Override
    public void handleStrategyTypeParamsSbe(final StrategyParamsSbeDecoder sbe, final ParamsSbeEncodable strategyParams) {
        final int limit = sbe.limit();
        StrategyParamsSbeDecoder.ParametersDecoder param = sbe.parameters();
        while (param.hasNext()) {
            param = param.next();
            longParameterValues[param.parameterId()] = param.parameterValueLong();
        }
        sbe.limit(limit);
        final GenericStrategyTypeParams params = (GenericStrategyTypeParams)strategyParams;
        populateStrategyTypeInputParams(params);
    }

    @Override
    public void handleStrategyUndParamsSbe(final StrategyUndParamsSbeDecoder sbe, final ParamsSbeEncodable strategyUndParams) {
        final int limit = sbe.limit();
        StrategyUndParamsSbeDecoder.ParametersDecoder param = sbe.parameters();
        while (param.hasNext()) {
            param = param.next();
            longParameterValues[param.parameterId()] = param.parameterValueLong();
        }
        sbe.limit(limit);
        final GenericUndParams params = (GenericUndParams)strategyUndParams;
        populateUndInputParams(params);
        populateUndOutputParams(params);
    }

    @Override
    public void handleStrategyIssuerParamsSbe(final StrategyIssuerParamsSbeDecoder sbe, final ParamsSbeEncodable strategyIssuerParams) {
        final int limit = sbe.limit();
        StrategyIssuerParamsSbeDecoder.ParametersDecoder param = sbe.parameters().next();
        while (param.hasNext()) {
            param = param.next();
            longParameterValues[param.parameterId()] = param.parameterValueLong();
        }
        sbe.limit(limit);
        final GenericIssuerParams params = (GenericIssuerParams)strategyIssuerParams;
        populateIssuerInputParams(params);
        populateIssuerOutputParams(params);
    }

    @Override
    public void handleStrategyWrtParamsSbe(final StrategyWrtParamsSbeDecoder sbe, final ParamsSbeEncodable strategyWrtParams) {
        final int limit = sbe.limit();
        StrategyWrtParamsSbeDecoder.ParametersDecoder param = sbe.parameters();
        while (param.hasNext()) {
            param = param.next();
            longParameterValues[param.parameterId()] = param.parameterValueLong();
        }
        sbe.limit(limit);
        final GenericWrtParams params = (GenericWrtParams)strategyWrtParams;
        populateWrtInputParams(params);
        populateWrtOutputParams(params);
    }


	@Override
	public void handleStrategyIssuerUndParamsSbe(StrategyIssuerUndParamsSbeDecoder sbe, ParamsSbeEncodable strategyIssuerUndParams) {
		final int limit = sbe.limit();
		StrategyIssuerUndParamsSbeDecoder.ParametersDecoder param = sbe.parameters();
        while (param.hasNext()) {
            param = param.next();
            longParameterValues[param.parameterId()] = param.parameterValueLong();
        }
		sbe.limit(limit);
		final GenericIssuerUndParams params = (GenericIssuerUndParams)strategyIssuerUndParams;
        populateIssuerUndInputParams(params);
        populateIssuerUndOutputParams(params);
	}
	
    private void populateStrategyTypeInputParams(final StrategyTypeInputParams params) {
        params.exitMode(StrategyExitMode.get((byte)longParameterValues[EXIT_MODE_ID]));
    }

    private void populateUndInputParams(final UndInputParams params) {
        params.velocityThreshold(longParameterValues[VELOCITY_THRESHOLD_ID]);
    }

    private void populateUndOutputParams(final UndOutputParams params) {
        params.numActiveWarrants((int)longParameterValues[OUT_NUM_ACTIVE_WARRANTS_ID]);
        params.numTotalWarrants((int)longParameterValues[OUT_NUM_TOTAL_WARRANTS_ID]);
    }

    private void populateIssuerUndInputParams(final IssuerUndInputParams params) {
        params.undTradeVolThreshold(longParameterValues[UND_TRADE_VOL_THRESHOLD_ID]);
    }
    
    private void populateIssuerUndOutputParams(final IssuerUndOutputParams params) {
        params.undTradeVol(longParameterValues[OUT_UND_TRADE_VOL_ID]);
    }

    private void populateIssuerInputParams(final IssuerInputParams params) {
        params.issuerMaxLag(longParameterValues[IN_OUT_ISSUER_MAX_LAG_ID]);
        //params.allowStopLossOnFlashingBid(longParameterValues[ALLOW_STOPLOSS_ON_FLASHING_BID_ID] == 1);
        params.resetStopLossOnVolDown(longParameterValues[RESET_STOPLOSS_ON_DOWNVOL_ID] == 1);
        params.issuerMaxLagCap(longParameterValues[ISSUER_MAX_LAG_CAP_ID]);
        params.defaultPricingMode(PricingMode.get((byte)longParameterValues[DEFAULT_PRICING_MODE_ID]));
        params.strategyTriggerType(StrategyTriggerType.get((byte)longParameterValues[STRATEGY_TRIGGER_TYPE_ID]));
        params.runTicksThreshold((int)longParameterValues[PROFIT_RUN_TICKS_ID]);
        params.sellAtQuickProfit(longParameterValues[SELL_AT_QUICK_PROFIT_ID] == 1);
        params.sellToNonIssuer(longParameterValues[SELL_TO_NON_ISSUER_ID] == 1);
        params.tickBuffer((int)longParameterValues[TICK_BUFFER_ID]);
        params.stopLossTickBuffer((int)longParameterValues[STOP_LOSS_TICK_BUFFER_ID]);
        params.sellOnVolDown(longParameterValues[SELL_ON_VOL_DOWN_ID] == 1);
        params.sellOnVolDownBanPeriod(longParameterValues[BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID]);
        params.useHoldBidBan(longParameterValues[USE_HOLD_BID_BAN_ID] == 1);
        params.baseOrderSize((int)longParameterValues[BASE_ORDER_SIZE_ID]);
        params.maxOrderSize((int)longParameterValues[MAX_ORDER_SIZE_ID]);        
        params.orderSizeIncrement((int)longParameterValues[ORDER_SIZE_INCREMENT_ID]);
        params.orderSizeRemainder((int)longParameterValues[ORDER_SIZE_REMAINDER_ID]);
        params.currentOrderSize((int)longParameterValues[IN_OUT_CURRENT_ORDER_SIZE_ID]);
        params.tradesVolumeThreshold(longParameterValues[TRADES_VOLUME_THRESHOLD_ID]);
    }

    private void populateIssuerOutputParams(final IssuerOutputParams params) {
        params.numActiveWarrants((int)longParameterValues[OUT_NUM_ACTIVE_WARRANTS_ID]);
        params.numTotalWarrants((int)longParameterValues[OUT_NUM_TOTAL_WARRANTS_ID]);
    }

    private void populateWrtInputParams(final WrtInputParams params) {
        params.mmBidSize((int)longParameterValues[MM_BID_SIZE_ID]);
        params.mmAskSize((int)longParameterValues[MM_ASK_SIZE_ID]);
        params.orderSizeMultiplier((int)longParameterValues[ORDER_SIZE_MULTIPLIER_ID]);
        params.tickSensitivityThreshold((int)longParameterValues[TICK_SENSITIVITY_THRESHOLD_ID]);
        params.stopLoss(longParameterValues[IN_OUT_STOP_LOSS_ID]);
        params.stopLoss(longParameterValues[OUT_STOP_LOSS_ADJUSTMENT_ID]);
        params.stopLossTrigger(longParameterValues[IN_OUT_STOP_LOSS_TRIGGER_ID]);
        params.stopProfit(longParameterValues[STOP_PROFIT_ID]);        
        params.allowedMaxSpread((int)longParameterValues[ALLOWED_MAX_SPREAD_ID]);
        params.wideSpreadBuffer((int)longParameterValues[WIDE_SPREAD_BUFFER_ID]);
        params.turnoverMakingSize((int)longParameterValues[TURNOVER_MAKING_SIZE_ID]);
        params.turnoverMakingPeriod(longParameterValues[TURNOVER_MAKING_PERIOD_ID]);
        params.banPeriodToDownVol(longParameterValues[BAN_TO_DOWNVOL_PERIOD_ID]);
        params.banPeriodToTurnoverMaking(longParameterValues[BAN_TO_TOMAKE_PERIOD_ID]);
        params.sellingBanPeriod(longParameterValues[SELLING_BAN_PERIOD_ID]);
        //params.holdingPeriod(longParameterValues[HOLDING_PERIOD_ID]);
        params.spreadObservationPeriod(longParameterValues[SPREAD_OBSERVE_PERIOD_ID]);
        params.marketOutlook(MarketOutlookType.get((byte)longParameterValues[MARKET_OUTLOOK_ID]));
        params.manualOrderTicksFromEnterPrice((int)longParameterValues[MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID]);
        params.safeBidLevelBuffer((int)longParameterValues[IN_OUT_SAFE_BID_LEVEL_BUFFER_ID]);
        params.allowStopLossOnWideSpread(longParameterValues[IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID] == 1);
        params.doNotSell(longParameterValues[IN_OUT_DO_NOT_SELL_ID] == 1);
        params.ignoreMmSizeOnSell(longParameterValues[IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID] == 1);
        params.sellAtBreakEvenOnly(longParameterValues[IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID] == 1);
        populateIssuerInputParams(params);
    }

    private void populateWrtOutputParams(final WrtOutputParams params) {
        params.status(StrategyStatusType.get((byte)longParameterValues[STATUS_ID]));
        params.stopLoss(longParameterValues[IN_OUT_STOP_LOSS_ID]);
        params.stopLossTrigger(longParameterValues[IN_OUT_STOP_LOSS_TRIGGER_ID]);
        params.issuerMaxLag(longParameterValues[IN_OUT_ISSUER_MAX_LAG_ID]);
        params.tickSensitivity((int)longParameterValues[OUT_TICK_SENSITIVITY_ID]);
        params.warrantSpread((int)longParameterValues[OUT_WARRANT_SPREAD_ID]);
        params.pricingMode(PricingMode.get((byte)longParameterValues[OUT_PRICING_MODE_ID]));
        params.greeks().delta((int)longParameterValues[OUT_PRICING_DELTA_ID]);
        params.greeks().impliedVol((int)longParameterValues[OUT_IMPLIED_VOL_ID]);
        params.enterPrice((int)longParameterValues[OUT_ENTER_PRICE_ID]);
        params.exitLevel((int)longParameterValues[OUT_EXIT_LEVEL_ID]);
        params.profitRun((int)longParameterValues[OUT_PROFIT_RUN_ID]);
        params.safeBidPrice((int)longParameterValues[OUT_SAFE_BID_PRICE_ID]);
        params.spreadState(SpreadState.get((byte)longParameterValues[OUT_SPREAD_STATE_ID]));
        //params.enterBidPrice();
        //params.enterSpotPrice();
        params.issuerLag(longParameterValues[OUT_ISSUER_LAG_ID]);
        params.issuerSmoothing(longParameterValues[OUT_ISSUER_SMOOTHING_ID]);
        params.numSpreadResets((int)longParameterValues[OUT_NUM_SPREAD_RESETS_ID]);
        params.numWAvgDownVols((int)longParameterValues[OUT_NUM_WAVG_DOWNVOLS_ID]);
        params.numWAvgUpVols((int)longParameterValues[OUT_NUM_WAVG_UPVOLS_ID]);
        params.numMPrcDownVols((int)longParameterValues[OUT_NUM_MPRC_DOWNVOLS_ID]);
        params.numMPrcUpVols((int)longParameterValues[OUT_NUM_MPRC_UPVOLS_ID]);
    }
    
    static public String getFieldDescription(final int parameterId) {
        if (parameterId > MAX_ID || parameterId < 0){
            LOG.error("Invalid field id {}!", box(parameterId));
        }
        return FIELD_DESCRIPTIONS[parameterId];
    }


}
