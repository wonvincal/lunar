package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

public interface Handler<C> {
	
	void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, C payload);
	
	default void handleWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, C payload){
	    handle(buffer, offset, header, payload);
	}
	
	default boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header, C payload){
		handle(buffer, offset, header, payload);
		return false;
	}

	default Class<C> templateType(){
		return null;
	}
}
	}