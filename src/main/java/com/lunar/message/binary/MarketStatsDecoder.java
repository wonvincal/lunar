package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class MarketStatsDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(MarketStatsDecoder.class);
	private final MarketStatsSbeDecoder sbe = new MarketStatsSbeDecoder();
	private final HandlerList<MarketStatsSbeDecoder> handlerList;

	MarketStatsDecoder(Handler<MarketStatsSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<MarketStatsSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(MarketStatsSbeDecoder sbe){
	    final int origLimit = sbe.limit();
        final StringBuilder sb = new StringBuilder(String.format("secSid:%d, open: %d, high: %d, low: %d, close: %d, volume: %d, turnover: %d", 
        		sbe.secSid(), sbe.open(), sbe.high(), sbe.low(), sbe.close(), sbe.volume(), sbe.turnover()));
        sbe.limit(origLimit);	    
		return sb.toString();
	}
	
	public MarketStatsSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<MarketStatsSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static MarketStatsDecoder of(){
	    return new MarketStatsDecoder(NULL_HANDLER);
	}

	static final Handler<MarketStatsSbeDecoder> NULL_HANDLER = new Handler<MarketStatsSbeDecoder>() {
	    public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder codec) {
            LOG.warn("Message sent to null handler of MarketStatsSbeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
	    }
	};

}
