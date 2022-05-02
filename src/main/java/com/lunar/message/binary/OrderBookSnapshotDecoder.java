package com.lunar.message.binary;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.service.ServiceConstant;

public class OrderBookSnapshotDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(OrderBookSnapshotDecoder.class);
	private final OrderBookSnapshotSbeDecoder sbe = new OrderBookSnapshotSbeDecoder();
	private final HandlerList<OrderBookSnapshotSbeDecoder> handlerList;

	OrderBookSnapshotDecoder(Handler<OrderBookSnapshotSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<OrderBookSnapshotSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}
	
	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId) {
        sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, OrderBookSnapshotSbeDecoder.SCHEMA_VERSION);
        handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
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

	public static String decodeToString(OrderBookSnapshotSbeDecoder sbe){
		int origLimit = sbe.limit();
		OrderBookSnapshotSbeDecoder.AskDepthDecoder askDepth = sbe.askDepth();
		OrderBookSnapshotSbeDecoder.BidDepthDecoder bidDepth = sbe.bidDepth();		
		StringBuilder sb = new StringBuilder(String.format("secSid:%d, askCount:%d, bidCount:%d",
				sbe.secSid(),
				askDepth.count(),
				bidDepth.count()));
		for (OrderBookSnapshotSbeDecoder.AskDepthDecoder tick :askDepth){
			sb.append(", quantity:").append(tick.quantity())
			  .append(", price:").append(tick.price());
		}
		for (OrderBookSnapshotSbeDecoder.BidDepthDecoder tick :bidDepth){
			sb.append(", quantity:").append(tick.quantity())
			  .append(", price:").append(tick.price());
		}
		sbe.limit(origLimit);					
		return sb.toString();
	}
	
	public HandlerList<OrderBookSnapshotSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static OrderBookSnapshotDecoder of(){
		return new OrderBookSnapshotDecoder(NULL_HANDLER);
	}

	static final Handler<OrderBookSnapshotSbeDecoder> NULL_HANDLER = new Handler<OrderBookSnapshotSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder codec) {
			LOG.error("Message sent to null handler of OrderBookSnapshotDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
