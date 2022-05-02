package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.GenericTrackerSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.TriggerInfoDecoder;
import com.lunar.service.ServiceConstant;

public class GenericTrackerDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(GenericTrackerDecoder.class);
	private final GenericTrackerSbeDecoder sbe = new GenericTrackerSbeDecoder();
	private final HandlerList<GenericTrackerSbeDecoder> handlerList;

	GenericTrackerDecoder(Handler<GenericTrackerSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<GenericTrackerSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}
	
    @Override
    public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
        sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, GenericTrackerSbeDecoder.SCHEMA_VERSION);
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

	public static String decodeToString(GenericTrackerSbeDecoder sbe){
	    final TriggerInfoDecoder triggerInfo = sbe.triggerInfo();
	    return String.format("sendBy: %d, stepId: %d, triggeredBy:%d, triggerSeqNum: %d, timestamp: %d", sbe.sendBy(), sbe.stepId().value(), triggerInfo.triggeredBy(), triggerInfo.triggerSeqNum(), triggerInfo.nanoOfDay());
	}
	
	@Override
	public boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return handlerList.output(outputStream, userSupplied, journalRecord, buffer, offset, header, sbe);
	}
	
	public GenericTrackerSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<GenericTrackerSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static GenericTrackerDecoder of(){
		return new GenericTrackerDecoder(NULL_HANDLER);
	}

	static final Handler<GenericTrackerSbeDecoder> NULL_HANDLER = new Handler<GenericTrackerSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, GenericTrackerSbeDecoder codec) {
			LOG.warn("message sent to null handler of GenericTracker");
		}
	};

}
