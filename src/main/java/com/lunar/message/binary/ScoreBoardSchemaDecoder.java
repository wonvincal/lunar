package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.service.ServiceConstant;

public class ScoreBoardSchemaDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(ScoreBoardSchemaDecoder.class);
	private final ScoreBoardSchemaSbeDecoder sbe = new ScoreBoardSchemaSbeDecoder();
	private final HandlerList<ScoreBoardSchemaSbeDecoder> handlerList;


	ScoreBoardSchemaDecoder(Handler<ScoreBoardSchemaSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<ScoreBoardSchemaSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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

	public static String decodeToString(ScoreBoardSchemaSbeDecoder sbe){
		return "";
	}
	
	public ScoreBoardSchemaSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<ScoreBoardSchemaSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static ScoreBoardSchemaDecoder of(){
	    return new ScoreBoardSchemaDecoder(NULL_HANDLER);
	}

	static final Handler<ScoreBoardSchemaSbeDecoder> NULL_HANDLER = new Handler<ScoreBoardSchemaSbeDecoder>() {
	    public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, ScoreBoardSchemaSbeDecoder codec) {
            LOG.warn("Message sent to null handler of ScoreBoardSchemaSbeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
	    }
	};

}
