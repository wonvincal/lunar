package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ScoreBoardSbeEncoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeEncoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeEncoder.FieldsEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.scoreboard.ScoreBoard;
import com.lunar.strategy.scoreboard.ScoreBoardSchema;
import com.lunar.strategy.scoreboard.stats.MarketStats;
import com.lunar.strategy.scoreboard.stats.WarrantTradesStats;
import com.lunar.strategy.scoreboard.stats.SpeedArbHybridStats;
import com.lunar.strategy.scoreboard.stats.WarrantBehaviourStats;

public class ScoreBoardSender extends SbeEncodableSender {
    static final Logger LOG = LogManager.getLogger(ScoreBoardSender.class);

    public static ScoreBoardSender of(final MessageSender msgSender){
        return new ScoreBoardSender(msgSender);
    }
    
    ScoreBoardSender(final MessageSender msgSender){
        super(msgSender);
    }
    
    public static int encodeScoreBoardSchema(final MessageSender sender,
            final int dstSinkId,
            final MutableDirectBuffer buffer, 
            final int offset,
            final ScoreBoardSchemaSbeEncoder sbe,
            final ScoreBoardSchema schema){
        int payloadLength = encodeScoreBoardSchemaOnly(buffer, 
                offset + sender.headerSize(), 
                sender.stringBuffer(), 
                sbe, 
                schema);
        sender.encodeHeader(dstSinkId,
                buffer,
                offset, 
                ScoreBoardSchemaSbeEncoder.BLOCK_LENGTH, 
                ScoreBoardSchemaSbeEncoder.TEMPLATE_ID, 
                ScoreBoardSchemaSbeEncoder.SCHEMA_ID, 
                ScoreBoardSchemaSbeEncoder.SCHEMA_VERSION,
                payloadLength);
        return sender.headerSize() + payloadLength; 
    }

    public static int encodeScoreBoardSchemaOnly(final MutableDirectBuffer dstBuffer, 
            int offset, 
            final MutableDirectBuffer stringBuffer,
            ScoreBoardSchemaSbeEncoder sbe,
            ScoreBoardSchema schema) {
        FieldsEncoder fieldsEncoder = 
    	sbe.wrap(dstBuffer, offset)
    	.scoreBoardSid(ServiceConstant.DEFAULT_SCORE_BOARD_SCHEMA_ID)
    	.fieldsCount(schema.fields().size());        
        for (final ScoreBoardSchema.Field field : schema.fields()) {
            fieldsEncoder = fieldsEncoder.next();
            fieldsEncoder.fieldId(field.getFieldId())
            .fieldType(field.getFieldType());
        }
        return sbe.encodedLength();
    }

    public static int expectedEncodedLength(final ScoreBoardSchema schema) {
        int size = ScoreBoardSchemaSbeEncoder.BLOCK_LENGTH +
                ScoreBoardSchemaSbeEncoder.FieldsEncoder.sbeHeaderSize() +
                ScoreBoardSchemaSbeEncoder.FieldsEncoder.sbeBlockLength() * schema.fields().size();
        return size;
    }    
    
    public void sendScoreBoard(final MessageSinkRef[] sinks, final ScoreBoard scoreBoard) {
        sendSbeEncodable(sinks, scoreBoard.punterTradeSetStats());
        sendSbeEncodable(sinks, scoreBoard.marketStats());
        sendSbeEncodable(sinks, scoreBoard.speedArbHybridStats());
        sendSbeEncodable(sinks, scoreBoard.warrantBehaviourStats());
    }
    
    public void sendScoreBoard(final MessageSinkRef persistSink, final MessageSinkRef[] sinks, final ScoreBoard scoreBoard) {
        sendSbeEncodable(persistSink, sinks, scoreBoard.punterTradeSetStats());
        sendSbeEncodable(persistSink, sinks, scoreBoard.marketStats());
        sendSbeEncodable(persistSink, sinks, scoreBoard.speedArbHybridStats());
        sendSbeEncodable(persistSink, sinks, scoreBoard.warrantBehaviourStats());     
    }
    
    public long trySendScoreBoard(final MessageSinkRef sink, final ScoreBoard scoreBoard) {
        long result = trySendSbeEncodable(sink, scoreBoard.punterTradeSetStats());
        if (result != MessageSink.OK)
            return result;
        result = trySendSbeEncodable(sink, scoreBoard.marketStats());
        if (result != MessageSink.OK)
            return result;
        result = trySendSbeEncodable(sink, scoreBoard.speedArbHybridStats());
        if (result != MessageSink.OK)
            return result;
        result = trySendSbeEncodable(sink, scoreBoard.warrantBehaviourStats());
        return result;
    }
    
    static public int sendScoreBoard(final ResponseSender responseSender, final MessageSinkRef sink, final int clientKey, final BooleanType isLast, int responseMsgSeq, final ResultType resultType, final ScoreBoard scoreBoard){
        responseSender.sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseMsgSeq++, resultType, scoreBoard.punterTradeSetStats());
        responseSender.sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseMsgSeq++, resultType, scoreBoard.marketStats());
        responseSender.sendSbeEncodable(sink, clientKey, BooleanType.FALSE, responseMsgSeq++, resultType, scoreBoard.speedArbHybridStats());
        responseSender.sendSbeEncodable(sink, clientKey, isLast, responseMsgSeq++, resultType, scoreBoard.warrantBehaviourStats());
        return responseMsgSeq;
    }
    
    private static int NUM_PUNTER_TRADESET_STATS_FIELDS = 8; 
    public static int encodePunterTradeSetStats(final MutableDirectBuffer dstBuffer, 
            final int offset, 
            final MutableDirectBuffer stringBuffer,
            final ScoreBoardSbeEncoder sbe,
            final WarrantTradesStats stats) {
        final ScoreBoardSbeEncoder.DetailsEncoder details = sbe.wrap(dstBuffer, offset).secSid(stats.getSecSid()).score(stats.getScoreBoard().getScore()).detailsCount(NUM_PUNTER_TRADESET_STATS_FIELDS).next();
        encodePunterTradeSetStatsDetails(details, stats);
        return sbe.encodedLength();
    } 
    
    private static ScoreBoardSbeEncoder.DetailsEncoder encodePunterTradeSetStatsDetails(final ScoreBoardSbeEncoder.DetailsEncoder sbe, final WarrantTradesStats stats) {
        sbe.fieldId(ScoreBoardSchema.RUNNING_NET_SOLD_ID).fieldValueLong(stats.getNetSold()).next()
        .fieldId(ScoreBoardSchema.RUNNING_NET_REVENUE_ID).fieldValueLong(stats.getNetRevenue()).next()
        .fieldId(ScoreBoardSchema.RUNNING_NET_PNL_ID).fieldValueLong(stats.getNetPnl()).next()        
        .fieldId(ScoreBoardSchema.RUNNING_GROSS_SOLD_ID).fieldValueLong(stats.getGrossSold()).next()        
        .fieldId(ScoreBoardSchema.RUNNING_GROSS_REVENUE_ID).fieldValueLong(stats.getGrossRevenue()).next()
        
        .fieldId(ScoreBoardSchema.RUNNING_GROSS_PNL_ID).fieldValueLong(stats.getGrossPnl()).next()
        .fieldId(ScoreBoardSchema.NUMBER_BIG_TRADES_ID).fieldValueLong(stats.getNumberBigTrades()).next()
        .fieldId(ScoreBoardSchema.NUMBER_UNCERTAIN_TRADES_ID).fieldValueLong(stats.getNumberUncertainTrades());

        return sbe;
    } 
    
    public static int expectedEncodedLength(final WarrantTradesStats stats) {
        int size = ScoreBoardSbeEncoder.BLOCK_LENGTH +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeHeaderSize() +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeBlockLength() * NUM_PUNTER_TRADESET_STATS_FIELDS;
        return size;
    }
    
    private static int NUM_SPEEDARBHYBRID_STATS_FIELDS = 10; 
    public static int encodeSpeedArbHybridStats(final MutableDirectBuffer dstBuffer, 
            final int offset, 
            final MutableDirectBuffer stringBuffer,
            final ScoreBoardSbeEncoder sbe,
            final SpeedArbHybridStats stats) {
        final ScoreBoardSbeEncoder.DetailsEncoder details = sbe.wrap(dstBuffer, offset).secSid(stats.getSecSid()).score(stats.getScoreBoard().getScore()).detailsCount(NUM_SPEEDARBHYBRID_STATS_FIELDS).next();
        encodeSpeedArbHybridStatsDetails(details, stats);
        return sbe.encodedLength();
    } 
    
    private static ScoreBoardSbeEncoder.DetailsEncoder encodeSpeedArbHybridStatsDetails(final ScoreBoardSbeEncoder.DetailsEncoder sbe, final SpeedArbHybridStats stats) {
        sbe.fieldId(ScoreBoardSchema.OUR_SCORE_ID).fieldValueLong(stats.getOurScore()).next()
        .fieldId(ScoreBoardSchema.OUR_SCORE_WITH_PUNTERS_ID).fieldValueLong(stats.getOurScoreWithPunter()).next()
        .fieldId(ScoreBoardSchema.OUR_PREV_SCORE_WITH_PUNTERS_ID).fieldValueLong(stats.getOurPrevScoreWithPunter()).next()
        .fieldId(ScoreBoardSchema.OUR_MTM_SCORE_ID).fieldValueLong(stats.getOurMtmScore()).next()
        .fieldId(ScoreBoardSchema.OUR_MTM_SCORE_WITH_PUNTERS_ID).fieldValueLong(stats.getOurMtmScoreWithPunter()).next()
        
        .fieldId(ScoreBoardSchema.OUR_PNL_TICKS_WITH_PUNTERS_ID).fieldValueLong(stats.getOurPnlTicksWithPunter()).next()
        .fieldId(ScoreBoardSchema.NUM_OF_OUR_WINS_WITH_PUNTERS_ID).fieldValueLong(stats.getNumOurWinsWithPunter()).next()
        .fieldId(ScoreBoardSchema.NUM_OF_OUR_BREAKEVENS_ID).fieldValueLong(stats.getNumOurBreakEvens()).next()
        .fieldId(ScoreBoardSchema.NUM_OF_OUR_LOSSES_ID).fieldValueLong(stats.getNumOurLosses()).next()
        .fieldId(ScoreBoardSchema.OUR_MTM_THEORETICAL_PENALTY_ID).fieldValueLong(stats.getOurMtmTheoreticalPenalty());
        
        return sbe;
    } 
    
    public static int expectedEncodedLength(final SpeedArbHybridStats stats) {
        int size = ScoreBoardSbeEncoder.BLOCK_LENGTH +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeHeaderSize() +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeBlockLength() * NUM_SPEEDARBHYBRID_STATS_FIELDS;
        return size;
    }    

    private static int NUM_MARKET_STATS_FIELDS = 12; 
    public static int encodeMarketStats(final MutableDirectBuffer dstBuffer, 
            final int offset, 
            final MutableDirectBuffer stringBuffer,
            final ScoreBoardSbeEncoder sbe,
            final MarketStats stats) {
        final ScoreBoardSbeEncoder.DetailsEncoder details = sbe.wrap(dstBuffer, offset).secSid(stats.getSecSid()).score(stats.getScoreBoard().getScore()).detailsCount(NUM_MARKET_STATS_FIELDS).next();
        encodeMarketStatsDetails(details, stats);
        return sbe.encodedLength();
    } 
    
    private static ScoreBoardSbeEncoder.DetailsEncoder encodeMarketStatsDetails(final ScoreBoardSbeEncoder.DetailsEncoder sbe, final MarketStats stats) {
        sbe.fieldId(ScoreBoardSchema.ISSUER_SMOOTHING_ID).fieldValueLong(stats.getIssuerSmoothing()).next()
        .fieldId(ScoreBoardSchema.TICK_SENSITIVITY_ID).fieldValueLong(stats.getTickSensitivity()).next()
        .fieldId(ScoreBoardSchema.PREV_DAY_OUTSTANDING_ID).fieldValueLong(stats.getPrevDayOutstanding()).next()
        .fieldId(ScoreBoardSchema.PREV_DAY_OUTSTANDING_PERCENT_ID).fieldValueLong(stats.getPrevDayOsPercent()).next()        
        .fieldId(ScoreBoardSchema.PREV_DAY_NET_SOLD_ID).fieldValueLong(stats.getPrevDayNetSold()).next()
        
        .fieldId(ScoreBoardSchema.PREV_DAY_NET_VEGA_SOLD_ID).fieldValueLong(stats.getPrevDayNetVegaSold()).next()
        .fieldId(ScoreBoardSchema.PREV_DAY_OUTSTANDING_PERCENT_CHANGE_ID).fieldValueLong(stats.getPrevDayOsPercentChange()).next()
        .fieldId(ScoreBoardSchema.IVOL_ID).fieldValueLong(stats.getImpliedVol()).next()
        .fieldId(ScoreBoardSchema.PREV_DAY_IVOL_ID).fieldValueLong(stats.getPrevDayImpliedVol()).next()
        .fieldId(ScoreBoardSchema.BID_IVOL_ID).fieldValueLong(stats.getBidImpliedVol()).next()
        
        .fieldId(ScoreBoardSchema.ASK_IVOL_ID).fieldValueLong(stats.getAskImpliedVol()).next()
        .fieldId(ScoreBoardSchema.VOL_PER_TICK_ID).fieldValueLong(stats.getVolPerTick());
        return sbe;
    } 
    
    public static int expectedEncodedLength(final MarketStats stats) {
        int size = ScoreBoardSbeEncoder.BLOCK_LENGTH +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeHeaderSize() +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeBlockLength() * NUM_MARKET_STATS_FIELDS;
        return size;
    }    

    private static int NUM_WARRANT_BEHAVIOUR_STATS_FIELDS = 36; 
    public static int encodeWarrantBehaviourStats(final MutableDirectBuffer dstBuffer, 
            final int offset, 
            final MutableDirectBuffer stringBuffer,
            final ScoreBoardSbeEncoder sbe,
            final WarrantBehaviourStats stats) {
        final ScoreBoardSbeEncoder.DetailsEncoder details = sbe.wrap(dstBuffer, offset).secSid(stats.getSecSid()).score(stats.getScoreBoard().getScore()).detailsCount(NUM_WARRANT_BEHAVIOUR_STATS_FIELDS).next();
        encodeWarrantBehaviourStats(details, stats);
        return sbe.encodedLength();
    } 
    
    private static ScoreBoardSbeEncoder.DetailsEncoder encodeWarrantBehaviourStats(final ScoreBoardSbeEncoder.DetailsEncoder sbe, final WarrantBehaviourStats stats) {
        sbe
        .fieldId(ScoreBoardSchema.NUM_PUNTER_TRADES_ID).fieldValueLong(stats.getNumPunterBuyTrades()).next()
        .fieldId(ScoreBoardSchema.NUM_PUNTER_TRIGGERS_ID).fieldValueLong(stats.getNumPunterTriggers()).next()        
        .fieldId(ScoreBoardSchema.NUM_PUNTER_WINS_ID).fieldValueLong(stats.getNumProfits()).next()
        .fieldId(ScoreBoardSchema.NUM_PUNTER_BREAKEVENS_ID).fieldValueLong(stats.getNumTotalBreakEvens()).next()
        .fieldId(ScoreBoardSchema.NUM_PUNTER_BREAKEVEN_OR_WINS_ID).fieldValueLong(stats.getNumTotalBreakEvensOrProfits()).next()
        
        .fieldId(ScoreBoardSchema.NUM_PUNTER_LOSSES_ID).fieldValueLong(stats.getNumLosses()).next()        
        .fieldId(ScoreBoardSchema.NUM_CONSECUTIVE_BREAKEVEN_OR_WINS_ID).fieldValueLong(stats.getNumConsecutiveBreakEvensOrProfits()).next()
        .fieldId(ScoreBoardSchema.NUM_CONSECUTIVE_LOSSES_ID).fieldValueLong(stats.getNumConsecutiveLosses()).next()
        .fieldId(ScoreBoardSchema.LAST_TIME_PUNTER_TRIGGER_ID).fieldValueLong(stats.getLastTimePunterTrigger()).next()        
        .fieldId(ScoreBoardSchema.AVG_BREAKEVEN_TIME_ID).fieldValueLong(stats.getAvgBreakEvenTime()).next()
        
        .fieldId(ScoreBoardSchema.MIN_BREAKEVEN_TIME_ID).fieldValueLong(stats.getMinBreakEvenTime()).next()
        .fieldId(ScoreBoardSchema.LAST_BREAKEVEN_TIME_ID).fieldValueLong(stats.getLastBreakEvenTime()).next()
        .fieldId(ScoreBoardSchema.LAST_TIME_BREAKEVEN_ID).fieldValueLong(stats.getLastTimeBreakEven()).next()        
        .fieldId(ScoreBoardSchema.AVG_WIN_TIME_ID).fieldValueLong(stats.getAvgProfitTime()).next()
        .fieldId(ScoreBoardSchema.LAST_WIN_TIME_ID).fieldValueLong(stats.getLastProfitTime()).next()
        .fieldId(ScoreBoardSchema.LAST_TIME_WIN_ID).fieldValueLong(stats.getLastTimeProfits()).next()        
        .fieldId(ScoreBoardSchema.LAST_TRIGGER_TOTAL_BUY_QUANTITY_ID).fieldValueLong(stats.getLastTriggerTotalBuyQuantity()).next()
        
        .fieldId(ScoreBoardSchema.LAST_TRIGGER_TOTAL_TRADES_ID).fieldValueLong(stats.getLastTriggerTotalTrades()).next()
        .fieldId(ScoreBoardSchema.MAX_PROFITABLE_QUANTITY_ID).fieldValueLong(stats.getMaxProfitableQuantity()).next()
        .fieldId(ScoreBoardSchema.MODE_PROFITABLE_QUANTITY_ID).fieldValueLong(stats.getModeProfitableQuantity()).next()
        .fieldId(ScoreBoardSchema.TWA_SPREAD_ID).fieldValueLong(stats.getTwaSpread()).next()
        .fieldId(ScoreBoardSchema.NORMALIZED_TWA_SPREAD_ID).fieldValueLong(stats.getNormalizedTwaSpread()).next()

        .fieldId(ScoreBoardSchema.TWA_SPREAD_IN_POSITION_ID).fieldValueLong(stats.getTwaSpreadInPosition()).next()
        .fieldId(ScoreBoardSchema.NORMALIZED_TWA_SPREAD_IN_POSITION_ID).fieldValueLong(stats.getNormalizedTwaSpreadInPosition()).next()
        .fieldId(ScoreBoardSchema.MODE_SPREAD_ID).fieldValueLong(stats.getModeSpread()).next()        
        .fieldId(ScoreBoardSchema.NORMALIZED_MODE_SPREAD_ID).fieldValueLong(stats.getNormalizedModeSpread()).next()        
        .fieldId(ScoreBoardSchema.NUM_DROP_VOLS_ID).fieldValueLong(stats.getNumDropVols()).next()
        
        .fieldId(ScoreBoardSchema.NUM_AUTO_DROP_VOLS_ON_BUY_ID).fieldValueLong(stats.getNumAutoDropVolsOnBuy()).next()
        .fieldId(ScoreBoardSchema.NUM_AUTO_DROP_VOLS_ON_SELL_ID).fieldValueLong(stats.getNumAutoDropVolsOnSell()).next()        
        .fieldId(ScoreBoardSchema.NUM_MANUAL_DROP_VOLS_ON_BUY_ID).fieldValueLong(stats.getNumManualDropVolsOnBuy()).next()        
        .fieldId(ScoreBoardSchema.NUM_MANUAL_DROP_VOLS_ON_SELL_ID).fieldValueLong(stats.getNumManualDropVolsOnSell()).next()
        .fieldId(ScoreBoardSchema.NUM_RAISE_VOLS_ID).fieldValueLong(stats.getNumRaiseVols()).next()
        
        .fieldId(ScoreBoardSchema.NUM_AUTO_RAISE_VOLS_ON_SELL_ID).fieldValueLong(stats.getNumAutoRaiseVolsOnSell()).next()
        .fieldId(ScoreBoardSchema.NUM_MANUAL_RAISE_VOLS_ON_SELL_ID).fieldValueLong(stats.getNumManualRaiseVolsOnSell()).next()        
        .fieldId(ScoreBoardSchema.AVG_SMOOTHING_ID).fieldValueLong(stats.getAvgSmoothingTime()).next()        
        .fieldId(ScoreBoardSchema.AVG_SMOOTHING_PER_TICK_ID).fieldValueLong(stats.getAvgSmoothingTimePerTick());
        return sbe;
    } 
    
    public static int expectedEncodedLength(final WarrantBehaviourStats stats) {
        int size = ScoreBoardSbeEncoder.BLOCK_LENGTH +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeHeaderSize() +
                ScoreBoardSbeEncoder.DetailsEncoder.sbeBlockLength() * NUM_WARRANT_BEHAVIOUR_STATS_FIELDS;
        return size;
    }    

}
