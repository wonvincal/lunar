package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.Message;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

public interface Decoder<T> {
	static final Logger LOG = LogManager.getLogger(Decoder.class);

	/* Include sequence number */
	void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header);
	
	default void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int reponseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		throw new UnsupportedOperationException("decodeWithEmbedded has not been implemented [templateId:" + embeddedTemplateId + "]");
	}

	String dump(DirectBuffer buffer, int offsetToPayload, MessageHeaderDecoder header);

	default String dumpWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int reponseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		return dump(buffer, offset, header);
	}

	default Message decodeAsMessage(DirectBuffer buffer, int offset, MessageHeaderDecoder decoder){
		throw new UnsupportedOperationException();
	}
	
	default boolean output(PrintStream outputStream, Object[] userSupplied, JournalRecordSbeDecoder journalRecord, DirectBuffer buffer, int offset, MessageHeaderDecoder header){
		decode(buffer, offset, header);
		return false;
	}
	
	default boolean registerHandler(Handler<T> handler){
		return false;
	}
	
	default boolean unregisterHandler(Handler<T> handler){
		return false;
	}

	public static Decoder NULL_HANDLER = new Decoder() {
		
		@Override
		public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
			return "";
		}
		
		@Override
		public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
			LOG.debug("decoded message with null handler: offset:{}, blockLength:{}, version:{}, templateId:{}, offset:{}, capacity:{}", 
					offset, 
					header.blockLength(), 
					header.version(), 
					header.templateId(), 
					offset, 
					buffer.capacity());
			throw new RuntimeException();
		}

		@Override
		public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId) {
			LOG.debug("decoded message with clientKey with null handler [offset:{}, blockLength:{}, version:{}, templateId:{}, frame offset:{}, frame len:{}, clientKey:{}]", 
					offset, 
					header.blockLength(), 
					header.version(), 
					header.templateId(), 
					offset, 
					buffer.capacity(), 
					clientKey);
			throw new RuntimeException();
		}

	};
}
