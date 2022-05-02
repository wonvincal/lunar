package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.scoreboard.ScoreBoard;
import com.lunar.strategy.scoreboard.ScoreBoardSchema;

public class ScoreBoardDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(ScoreBoardDecoder.class);
	private final ScoreBoardSbeDecoder sbe = new ScoreBoardSbeDecoder();
	private final HandlerList<ScoreBoardSbeDecoder> handlerList;
    private long[] longParameterValues = new long[ScoreBoardSchema.MAX_FIELD_ID + 1];
    private boolean[] booleanParameterValues = new boolean[ScoreBoardSchema.MAX_FIELD_ID + 1];


	ScoreBoardDecoder(Handler<ScoreBoardSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<ScoreBoardSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId) {
        sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, MarketStatsSbeDecoder.SCHEMA_VERSION);
        handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
    }   

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}
	
	@Override
	public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return handlerList.output(outputStream, userSupplied, journalRecord, buffer, offset, header, sbe);
	}

	public static String decodeToString(ScoreBoardSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("secSid:%d, score: %d", 
        		sbe.secSid(), sbe.score()));
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public ScoreBoardSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<ScoreBoardSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static ScoreBoardDecoder of(){
	    return new ScoreBoardDecoder(NULL_HANDLER);
	}

	static final Handler<ScoreBoardSbeDecoder> NULL_HANDLER = new Handler<ScoreBoardSbeDecoder>() {
	    public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSbeDecoder codec) {
            LOG.warn("Message sent to null handler of ScoreBoardSbeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
	    }
	};
	
	public void decodeScoreBoard(final ScoreBoardSbeDecoder sbe, final ScoreBoard scoreBoard) {
        final int limit = sbe.limit();
        ScoreBoardSbeDecoder.DetailsDecoder field = sbe.details();
        for (int i = 0; i < ScoreBoardSchema.MAX_FIELD_ID + 1; i++) {
            longParameterValues[i] = 0;
            booleanParameterValues[i] = false;
        }
        while (field.hasNext()) {
            field = field.next();
            longParameterValues[field.fieldId()] = field.fieldValueLong();
            booleanParameterValues[field.fieldId()] = true;
        }
        sbe.limit(limit);
        if (booleanParameterValues[ScoreBoardSchema.OUR_PREV_SCORE_WITH_PUNTERS_ID]) {
            scoreBoard.speedArbHybridStats().setOurPrevScoreWithPunter((int)longParameterValues[ScoreBoardSchema.OUR_PREV_SCORE_WITH_PUNTERS_ID]);
        }
        if (booleanParameterValues[ScoreBoardSchema.PREV_DAY_OUTSTANDING_PERCENT_ID]) {
            scoreBoard.marketStats().setPrevDayOsPercent((int)longParameterValues[ScoreBoardSchema.PREV_DAY_OUTSTANDING_PERCENT_ID]);
        }
        if (booleanParameterValues[ScoreBoardSchema.PREV_DAY_OUTSTANDING_PERCENT_CHANGE_ID]) {
            scoreBoard.marketStats().setPrevDayOsPercentChange((int)longParameterValues[ScoreBoardSchema.PREV_DAY_OUTSTANDING_PERCENT_CHANGE_ID]);
        }
        if (booleanParameterValues[ScoreBoardSchema.PREV_DAY_OUTSTANDING_ID]) {
            scoreBoard.marketStats().setPrevDayOutstanding(longParameterValues[ScoreBoardSchema.PREV_DAY_OUTSTANDING_ID]);
        }
        if (booleanParameterValues[ScoreBoardSchema.PREV_DAY_NET_SOLD_ID]) {
            scoreBoard.marketStats().setPrevDayNetSold(longParameterValues[ScoreBoardSchema.PREV_DAY_NET_SOLD_ID]);
        }
        if (booleanParameterValues[ScoreBoardSchema.PREV_DAY_NET_VEGA_SOLD_ID]) {
            scoreBoard.marketStats().setPrevDayNetVegaSold(longParameterValues[ScoreBoardSchema.PREV_DAY_NET_VEGA_SOLD_ID]);
        }
        if (booleanParameterValues[ScoreBoardSchema.PREV_DAY_IVOL_ID]) {
            scoreBoard.marketStats().setPrevDayImpliedVol((int)longParameterValues[ScoreBoardSchema.PREV_DAY_IVOL_ID]);
        }
	}

}
