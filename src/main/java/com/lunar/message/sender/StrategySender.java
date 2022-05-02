package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategyParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategySwitchSbeEncoder;
import com.lunar.message.io.sbe.StrategySwitchType;
import com.lunar.message.io.sbe.StrategyTypeSbeEncoder;
import com.lunar.message.io.sbe.StrategyTypeSbeEncoder.FieldsEncoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeEncoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeEncoder;
import com.lunar.strategy.GenericStrategySchema;
import com.lunar.strategy.parameters.BucketOutputParams;
import com.lunar.strategy.parameters.GenericIssuerParams;
import com.lunar.strategy.parameters.GenericIssuerUndParams;
import com.lunar.strategy.parameters.GenericStrategyTypeParams;
import com.lunar.strategy.parameters.GenericUndParams;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.util.StringUtil;

public class StrategySender extends SbeEncodableSender {
	static final Logger LOG = LogManager.getLogger(StrategySender.class);
    static private int NUM_PARAMS_STRATEGYTYPEPARAMS = 1;    
    static private int NUM_PARAMS_STRATEGYUNDPARAMS = 3;    
    static private int NUM_PARAMS_STRATEGYISSUERPARAMS = 3;
    static private int NUM_PARAMS_STRATEGYISSUERUNDPARAMS = 2;
    
	/**
	 * Create a MarketDataOrderBookSnapshotSender with a specific MessageSender
	 * @param msgSender
	 * @return
	 */
	public static StrategySender of(MessageSender msgSender){
		return new StrategySender(msgSender);
	}
	
	StrategySender(MessageSender msgSender){
	    super(msgSender);
	}
	
    static public int encodeStrategyTypeWithoutHeader(final MutableDirectBuffer dstBuffer, int offset, final MutableDirectBuffer stringBuffer, final StrategyTypeSbeEncoder sbe, final StrategyType strategyType) {
        FieldsEncoder fieldsEncoder = sbe.wrap(dstBuffer, offset)
            .strategyId(strategyType.sid())
            .putName(StringUtil.copyAndPadSpace(strategyType.name(), StrategyTypeSbeEncoder.nameCharacterEncoding(), stringBuffer, StrategyTypeSbeEncoder.nameLength()).byteArray(), 0)
            .fieldsCount(strategyType.getFields().size());
        for (StrategyType.Field field : strategyType.getFields()) {
            if (field != null) {
            	fieldsEncoder = fieldsEncoder.next();
            	fieldsEncoder.parameterId(field.parameterId())
            		.parameterSource(field.source())
            		.parameterType(field.type())
            		.parameterReadOnly(field.isReadOnly());
            }
        }
        return sbe.encodedLength();
    }	

    static public int encodeStrategySwitchWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategySwitchSbeEncoder sbe,
            final StrategySwitchType switchType,
            final StrategyParamSource source,
            final long sid,
            final BooleanType onOff) {
        sbe.wrap(buffer, offset)
            .switchType(switchType)
            .switchSource(source)
            .sourceSid(sid)
            .onOff(onOff);
        return sbe.encodedLength();
    }
    
    public static int expectedEncodedLength(final GenericStrategyTypeParams StrategyParams){
        int size = StrategyParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * NUM_PARAMS_STRATEGYTYPEPARAMS;
        return size;
    }   

    static public int encodeStrategyTypeParamsWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyParamsSbeEncoder sbe,
            final GenericStrategyTypeParams params) {
        sbe.wrap(buffer, offset)
            .strategyId(params.strategyId()).parametersCount(NUM_PARAMS_STRATEGYTYPEPARAMS).next()
            .parameterId(GenericStrategySchema.EXIT_MODE_ID).parameterValueLong(params.exitMode().value());
        return sbe.encodedLength();
    }   
	
    public static int expectedEncodedLength(final GenericUndParams strategyParams){
        int size = StrategyUndParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyUndParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyUndParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * NUM_PARAMS_STRATEGYUNDPARAMS;
        return size;
    }   	
	
    static public int encodeUndParamsWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyUndParamsSbeEncoder sbe,
            final GenericUndParams params) {
        sbe.wrap(buffer, offset)
            .strategyId(params.strategyId()).undSid(params.underlyingSid()).parametersCount(NUM_PARAMS_STRATEGYUNDPARAMS).next()
            .parameterId(GenericStrategySchema.VELOCITY_THRESHOLD_ID).parameterValueLong(params.velocityThreshold()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_ACTIVE_WARRANTS_ID).parameterValueLong(params.numActiveWarrants()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_TOTAL_WARRANTS_ID).parameterValueLong(params.numTotalWarrants());
        return sbe.encodedLength();           
    }
	
    static private int NUM_PARAMS_STRATEGYWRTPARAMS_STATS_ONLY = 28;
    
    public static int expectedEncodedLengthStatsOnly(final GenericWrtParams strategyParams){
        int size = StrategyWrtParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyWrtParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyWrtParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * NUM_PARAMS_STRATEGYWRTPARAMS_STATS_ONLY;
        return size;
    }
    
    static public int encodeStrategyWrtParamsStatsOnlyWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyWrtParamsSbeEncoder sbe,
            final GenericWrtParams params) {
        sbe.wrap(buffer,  offset)
            .strategyId(params.strategyId()).secSid(params.secSid()).parametersCount(NUM_PARAMS_STRATEGYWRTPARAMS_STATS_ONLY).next()
            .parameterId(GenericStrategySchema.IN_OUT_STOP_LOSS_ID).parameterValueLong(params.stopLoss()).next()
            .parameterId(GenericStrategySchema.IN_OUT_STOP_LOSS_TRIGGER_ID).parameterValueLong(params.stopLossTrigger()).next()
            .parameterId(GenericStrategySchema.IN_OUT_ISSUER_MAX_LAG_ID).parameterValueLong(params.issuerMaxLag()).next()
            .parameterId(GenericStrategySchema.STATUS_ID).parameterValueLong(params.status().value()).next()
            .parameterId(GenericStrategySchema.OUT_TICK_SENSITIVITY_ID).parameterValueLong(params.tickSensitivity()).next()
            
            .parameterId(GenericStrategySchema.OUT_WARRANT_SPREAD_ID).parameterValueLong(params.warrantSpread()).next()
            .parameterId(GenericStrategySchema.OUT_PRICING_MODE_ID).parameterValueLong(params.pricingMode().value()).next()
            .parameterId(GenericStrategySchema.OUT_PRICING_DELTA_ID).parameterValueLong(params.greeks().delta()).next()
            .parameterId(GenericStrategySchema.OUT_IMPLIED_VOL_ID).parameterValueLong(params.greeks().impliedVol()).next()
            .parameterId(GenericStrategySchema.OUT_EXIT_LEVEL_ID).parameterValueLong(params.exitLevel()).next()

            .parameterId(GenericStrategySchema.OUT_PROFIT_RUN_ID).parameterValueLong(params.profitRun()).next()
            .parameterId(GenericStrategySchema.OUT_STOP_LOSS_ADJUSTMENT_ID).parameterValueLong(params.stopLossAdjustment()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_SPREAD_RESETS_ID).parameterValueLong(params.numSpreadResets()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_WAVG_DOWNVOLS_ID).parameterValueLong(params.numWAvgDownVols()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_WAVG_UPVOLS_ID).parameterValueLong(params.numWAvgUpVols()).next()
            
            .parameterId(GenericStrategySchema.OUT_NUM_MPRC_DOWNVOLS_ID).parameterValueLong(params.numMPrcDownVols()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_MPRC_UPVOLS_ID).parameterValueLong(params.numMPrcUpVols()).next()
            .parameterId(GenericStrategySchema.OUT_ISSUER_LAG_ID).parameterValueLong(params.issuerLag()).next()
            .parameterId(GenericStrategySchema.OUT_ISSUER_SMOOTHING_ID).parameterValueLong(params.issuerSmoothing()).next()            
            .parameterId(GenericStrategySchema.OUT_SPREAD_STATE_ID).parameterValueLong(params.spreadState().value()).next()
            
            .parameterId(GenericStrategySchema.IN_OUT_CURRENT_ORDER_SIZE_ID).parameterValueLong(params.currentOrderSize()).next()
            .parameterId(GenericStrategySchema.OUT_ACTUAL_ORDER_SIZE_ID).parameterValueLong(params.orderSize()).next()
            .parameterId(GenericStrategySchema.IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID).parameterValueLong(params.allowStopLossOnWideSpread() ? 1 : 0).next()
            .parameterId(GenericStrategySchema.IN_OUT_DO_NOT_SELL_ID).parameterValueLong(params.doNotSell() ? 1 : 0).next()            
            .parameterId(GenericStrategySchema.IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID).parameterValueLong(params.ignoreMmSizeOnSell() ? 1 : 0).next()
            
            .parameterId(GenericStrategySchema.IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID).parameterValueLong(params.sellAtBreakEvenOnly() ? 1 : 0).next()
            .parameterId(GenericStrategySchema.IN_OUT_SAFE_BID_LEVEL_BUFFER_ID).parameterValueLong(params.safeBidLevelBuffer()).next()
            .parameterId(GenericStrategySchema.OUT_SAFE_BID_PRICE_ID).parameterValueLong(params.safeBidPrice());
        
        return sbe.encodedLength();
    }

    static private int NUM_PARAMS_STRATEGYWRTPARAMS = 60;
    
    public static int expectedEncodedLength(final GenericWrtParams strategyParams){
        int size = StrategyWrtParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyWrtParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyWrtParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * NUM_PARAMS_STRATEGYWRTPARAMS;
        return size;
    }
    
    public static int expectedEncodedLength(final BucketOutputParams strategyParams){
        int size = StrategyWrtParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyWrtParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyWrtParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * BucketOutputParams.NUM_PARAMS;
        return size;
    }

    static public int encodeBucketOutputParamsWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyWrtParamsSbeEncoder sbe,
            final BucketOutputParams params) {
        sbe.wrap(buffer,  offset)
        .strategyId(params.strategyId()).secSid(params.secSid()).parametersCount(BucketOutputParams.NUM_PARAMS).next()
        .parameterId(GenericStrategySchema.OUT_ACTIVE_BUCKET_INTERVAL_BEGIN_ID).parameterValueLong(params.activeBucketBegin()).next()
        .parameterId(GenericStrategySchema.OUT_ACTIVE_BUCKET_INTERVAL_DATA_ID).parameterValueLong(params.activeBucketData()).next()
        .parameterId(GenericStrategySchema.OUT_ACTIVE_BUCKET_INTERVAL_END_EXCL_ID).parameterValueLong(params.activeBucketEndExcl()).next()
        .parameterId(GenericStrategySchema.OUT_NEXT_BUCKET_INTERVAL_BEGIN_ID).parameterValueLong(params.nextBucketBegin()).next()
        .parameterId(GenericStrategySchema.OUT_NEXT_BUCKET_INTERVAL_DATA_ID).parameterValueLong(params.nextBucketData()).next()
        .parameterId(GenericStrategySchema.OUT_NEXT_BUCKET_INTERVAL_END_EXCL_ID).parameterValueLong(params.nextBucketEndExcl());
        return sbe.encodedLength();
    }
    
    static public int encodeWrtParamsWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyWrtParamsSbeEncoder sbe,
            final GenericWrtParams params) {
        sbe.wrap(buffer,  offset)
            .strategyId(params.strategyId()).secSid(params.secSid()).parametersCount(NUM_PARAMS_STRATEGYWRTPARAMS).next()
            .parameterId(GenericStrategySchema.MM_BID_SIZE_ID).parameterValueLong(params.mmBidSize()).next()
            .parameterId(GenericStrategySchema.MM_ASK_SIZE_ID).parameterValueLong(params.mmAskSize()).next()
            .parameterId(GenericStrategySchema.BASE_ORDER_SIZE_ID).parameterValueLong(params.baseOrderSize()).next()
            .parameterId(GenericStrategySchema.MAX_ORDER_SIZE_ID).parameterValueLong(params.maxOrderSize()).next()
            .parameterId(GenericStrategySchema.IN_OUT_CURRENT_ORDER_SIZE_ID).parameterValueLong(params.currentOrderSize()).next()
            
            .parameterId(GenericStrategySchema.ORDER_SIZE_INCREMENT_ID).parameterValueLong(params.orderSizeIncrement()).next()
            .parameterId(GenericStrategySchema.ORDER_SIZE_MULTIPLIER_ID).parameterValueLong(params.orderSizeMultiplier()).next()
            .parameterId(GenericStrategySchema.ORDER_SIZE_REMAINDER_ID).parameterValueLong(params.orderSizeRemainder()).next()
            .parameterId(GenericStrategySchema.OUT_ACTUAL_ORDER_SIZE_ID).parameterValueLong(params.orderSize()).next()            
            .parameterId(GenericStrategySchema.PROFIT_RUN_TICKS_ID).parameterValueLong(params.runTicksThreshold()).next()
            
            .parameterId(GenericStrategySchema.SELL_AT_QUICK_PROFIT_ID).parameterValueLong(params.sellAtQuickProfit() ? 1 : 0).next()
            .parameterId(GenericStrategySchema.TICK_SENSITIVITY_THRESHOLD_ID).parameterValueLong(params.tickSensitivityThreshold()).next()            
            .parameterId(GenericStrategySchema.IN_OUT_STOP_LOSS_ID).parameterValueLong(params.stopLoss()).next()
            .parameterId(GenericStrategySchema.OUT_STOP_LOSS_ADJUSTMENT_ID).parameterValueLong(params.stopLossAdjustment()).next()
            .parameterId(GenericStrategySchema.IN_OUT_STOP_LOSS_TRIGGER_ID).parameterValueLong(params.stopLossTrigger()).next()
            
            .parameterId(GenericStrategySchema.STOP_PROFIT_ID).parameterValueLong(params.stopProfit()).next()            
            .parameterId(GenericStrategySchema.ALLOWED_MAX_SPREAD_ID).parameterValueLong(params.allowedMaxSpread()).next()
            .parameterId(GenericStrategySchema.WIDE_SPREAD_BUFFER_ID).parameterValueLong(params.wideSpreadBuffer()).next()
            .parameterId(GenericStrategySchema.TURNOVER_MAKING_SIZE_ID).parameterValueLong(params.turnoverMakingSize()).next()
            .parameterId(GenericStrategySchema.TURNOVER_MAKING_PERIOD_ID).parameterValueLong(params.turnoverMakingPeriod()).next()
            
            .parameterId(GenericStrategySchema.BAN_TO_DOWNVOL_PERIOD_ID).parameterValueLong(params.banPeriodToDownVol()).next()
            .parameterId(GenericStrategySchema.BAN_TO_TOMAKE_PERIOD_ID).parameterValueLong(params.banPeriodToTurnoverMaking()).next()            
            .parameterId(GenericStrategySchema.SELLING_BAN_PERIOD_ID).parameterValueLong(params.sellingBanPeriod()).next()
            .parameterId(GenericStrategySchema.SPREAD_OBSERVE_PERIOD_ID).parameterValueLong(params.spreadObservationPeriod()).next()           
            .parameterId(GenericStrategySchema.MARKET_OUTLOOK_ID).parameterValueLong(params.marketOutlook().value()).next()
            
            .parameterId(GenericStrategySchema.SELL_ON_VOL_DOWN_ID).parameterValueLong(params.sellOnVolDown() ? 1 : 0).next()
            .parameterId(GenericStrategySchema.BAN_TO_SELL_ON_VOL_DOWN_PERIOD_ID).parameterValueLong(params.sellOnVolDownBanPeriod()).next()
            .parameterId(GenericStrategySchema.USE_HOLD_BID_BAN_ID).parameterValueLong(params.useHoldBidBan() ? 1 : 0).next()            
            .parameterId(GenericStrategySchema.IN_OUT_ISSUER_MAX_LAG_ID).parameterValueLong(params.issuerMaxLag()).next()            
            .parameterId(GenericStrategySchema.RESET_STOPLOSS_ON_DOWNVOL_ID).parameterValueLong(params.resetStopLossOnVolDown() ? 1 : 0).next()
            
            .parameterId(GenericStrategySchema.DEFAULT_PRICING_MODE_ID).parameterValueLong(params.defaultPricingMode().value()).next()
            .parameterId(GenericStrategySchema.STRATEGY_TRIGGER_TYPE_ID).parameterValueLong(params.strategyTriggerType().value()).next()            
            .parameterId(GenericStrategySchema.ISSUER_MAX_LAG_CAP_ID).parameterValueLong(params.issuerMaxLagCap()).next()            
            .parameterId(GenericStrategySchema.SELL_TO_NON_ISSUER_ID).parameterValueLong(params.sellToNonIssuer() ? 1 : 0).next()            
            .parameterId(GenericStrategySchema.TICK_BUFFER_ID).parameterValueLong(params.tickBuffer()).next()
            
            .parameterId(GenericStrategySchema.STOP_LOSS_TICK_BUFFER_ID).parameterValueLong(params.stopLossTickBuffer()).next()
            .parameterId(GenericStrategySchema.MANUAL_ORDER_TICKS_FROM_ENTER_PRICE_ID).parameterValueLong(params.manualOrderTicksFromEnterPrice()).next()            
            .parameterId(GenericStrategySchema.IN_OUT_ALLOW_STOPLOSS_ON_WIDE_SPREAD_ID).parameterValueLong(params.allowStopLossOnWideSpread() ? 1 : 0).next()            
            .parameterId(GenericStrategySchema.IN_OUT_SAFE_BID_LEVEL_BUFFER_ID).parameterValueLong(params.safeBidLevelBuffer()).next()
            .parameterId(GenericStrategySchema.TRADES_VOLUME_THRESHOLD_ID).parameterValueLong(params.tradesVolumeThreshold()).next()
            
            .parameterId(GenericStrategySchema.IN_OUT_DO_NOT_SELL_ID).parameterValueLong(params.doNotSell() ? 1 : 0).next() 
            .parameterId(GenericStrategySchema.IN_OUT_IGNORE_MM_SIZE_ON_SELL_ID).parameterValueLong(params.ignoreMmSizeOnSell() ? 1 : 0).next()
            .parameterId(GenericStrategySchema.IN_OUT_SELL_ON_BREAKEVEN_ONLY_ID).parameterValueLong(params.sellAtBreakEvenOnly() ? 1 : 0).next()
            .parameterId(GenericStrategySchema.STATUS_ID).parameterValueLong(params.status().value()).next()            
            .parameterId(GenericStrategySchema.OUT_TICK_SENSITIVITY_ID).parameterValueLong(params.tickSensitivity()).next()
            
            .parameterId(GenericStrategySchema.OUT_WARRANT_SPREAD_ID).parameterValueLong(params.warrantSpread()).next()
            .parameterId(GenericStrategySchema.OUT_PRICING_MODE_ID).parameterValueLong(params.pricingMode().value()).next()
            .parameterId(GenericStrategySchema.OUT_PRICING_DELTA_ID).parameterValueLong(params.greeks().delta()).next()            
            .parameterId(GenericStrategySchema.OUT_IMPLIED_VOL_ID).parameterValueLong(params.greeks().impliedVol()).next()
            .parameterId(GenericStrategySchema.OUT_EXIT_LEVEL_ID).parameterValueLong(params.exitLevel()).next()
            
            .parameterId(GenericStrategySchema.OUT_PROFIT_RUN_ID).parameterValueLong(params.profitRun()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_SPREAD_RESETS_ID).parameterValueLong(params.numSpreadResets()).next()            
            .parameterId(GenericStrategySchema.OUT_NUM_WAVG_DOWNVOLS_ID).parameterValueLong(params.numWAvgDownVols()).next()            
            .parameterId(GenericStrategySchema.OUT_NUM_WAVG_UPVOLS_ID).parameterValueLong(params.numWAvgUpVols()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_MPRC_DOWNVOLS_ID).parameterValueLong(params.numMPrcDownVols()).next()
            
            .parameterId(GenericStrategySchema.OUT_NUM_MPRC_UPVOLS_ID).parameterValueLong(params.numMPrcUpVols()).next()
            .parameterId(GenericStrategySchema.OUT_ISSUER_LAG_ID).parameterValueLong(params.issuerLag()).next()            
            .parameterId(GenericStrategySchema.OUT_ISSUER_SMOOTHING_ID).parameterValueLong(params.issuerSmoothing()).next()            
            .parameterId(GenericStrategySchema.OUT_SAFE_BID_PRICE_ID).parameterValueLong(params.safeBidPrice()).next()
            .parameterId(GenericStrategySchema.OUT_SPREAD_STATE_ID).parameterValueLong(params.spreadState().value());
        return sbe.encodedLength();
    }
    
    public static int expectedEncodedLength(final GenericIssuerParams params){
        int size = StrategyIssuerParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyIssuerParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyIssuerParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * NUM_PARAMS_STRATEGYISSUERPARAMS;
        return size;
    }       
    
    public static int expectedEncodedLength(final GenericIssuerUndParams params){
        int size = StrategyIssuerUndParamsSbeEncoder.BLOCK_LENGTH + 
                StrategyIssuerUndParamsSbeEncoder.ParametersEncoder.sbeHeaderSize() + 
                StrategyIssuerUndParamsSbeEncoder.ParametersEncoder.sbeBlockLength() * NUM_PARAMS_STRATEGYISSUERUNDPARAMS;
        return size;
    }       

    static public int encodeIssuerParamsWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyIssuerParamsSbeEncoder sbe,
            final GenericIssuerParams params) {
        sbe.wrap(buffer, offset)
            .strategyId(params.strategyId()).issuerSid((int)params.issuerSid()).parametersCount(NUM_PARAMS_STRATEGYISSUERPARAMS).next()
            .parameterId(GenericStrategySchema.OUT_NUM_ACTIVE_WARRANTS_ID).parameterValueLong(params.numActiveWarrants()).next()
            .parameterId(GenericStrategySchema.OUT_NUM_TOTAL_WARRANTS_ID).parameterValueLong(params.numTotalWarrants());
        return sbe.encodedLength();
    }

//    .strategyId(params.strategyId()).undSid(params.underlyingSid()).parametersCount(NUM_PARAMS_STRATEGYUNDPARAMS).next()
//    .parameterId(GenericStrategySchema.VELOCITY_THRESHOLD_ID).parameterValueLong(params.velocityThreshold()).next()
//    .parameterId(GenericStrategySchema.OUT_NUM_ACTIVE_WARRANTS_ID).parameterValueLong(params.numActiveWarrants()).next()
//    .parameterId(GenericStrategySchema.OUT_NUM_TOTAL_WARRANTS_ID).parameterValueLong(params.numTotalWarrants());

    static public int encodeIssuerUndParamsWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final StrategyIssuerUndParamsSbeEncoder sbe,
            final GenericIssuerUndParams params) {
        sbe.wrap(buffer, offset)
            .strategyId(params.strategyId())
            .issuerSid((int)params.issuerSid())
            .undSid(params.undSid())
            .issuerUndSid(params.issuerUndSid())
            .parametersCount(NUM_PARAMS_STRATEGYISSUERUNDPARAMS).next()
            .parameterId(GenericStrategySchema.UND_TRADE_VOL_THRESHOLD_ID).parameterValueLong(params.undTradeVolThreshold()).next()
            .parameterId(GenericStrategySchema.OUT_UND_TRADE_VOL_ID).parameterValueLong(params.undTradeVol());
        return sbe.encodedLength();
    }
}
