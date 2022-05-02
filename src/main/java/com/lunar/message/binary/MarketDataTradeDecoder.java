package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.service.ServiceConstant;

public class MarketDataTradeDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(MarketDataTradeDecoder.class);
	private final MarketDataTradeSbeDecoder sbe = new MarketDataTradeSbeDecoder();
	private final HandlerList<MarketDataTradeSbeDecoder> handlerList;

	MarketDataTradeDecoder(Handler<MarketDataTradeSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<MarketDataTradeSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
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
	
	public static String decodeToString(MarketDataTradeSbeDecoder sbe){
		int origLimit = sbe.limit();
		StringBuilder sb = new StringBuilder(String.format("secSid:%d", sbe.secSid()));
		sb.append(", quantity:").append(sbe.quantity())
			.append(", price:").append(sbe.price());
		sbe.limit(origLimit);					
		return sb.toString();
	}
	
	public HandlerList<MarketDataTradeSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static MarketDataTradeDecoder of(){
		return new MarketDataTradeDecoder(NULL_HANDLER);
	}

	static final Handler<MarketDataTradeSbeDecoder> NULL_HANDLER = new Handler<MarketDataTradeSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder codec) {
			LOG.error("Message sent to null handler of MarketDataTradeDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
