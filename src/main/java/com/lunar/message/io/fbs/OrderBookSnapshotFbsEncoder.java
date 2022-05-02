package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;

public class OrderBookSnapshotFbsEncoder {
	static final Logger LOG = LogManager.getLogger(OrderBookSnapshotFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, OrderBookSnapshotSbeDecoder md){
		final int limit = md.limit();
		OrderBookSnapshotSbeDecoder.AskDepthDecoder askDepthsDecoder = md.askDepth();
		int numAskDepth = askDepthsDecoder.count();
		int[] askDepths = null;
		if (numAskDepth > 0) {
			askDepths = new int[numAskDepth];
			int i = 0;
			for (OrderBookSnapshotSbeDecoder.AskDepthDecoder entry : askDepthsDecoder){
				askDepths[i] = OrderBookSnapshotEntryFbs.createOrderBookSnapshotEntryFbs(builder, 
						entry.price(),
						entry.quantity(),
						entry.tickLevel());
				i++;
			}
		}
		OrderBookSnapshotSbeDecoder.BidDepthDecoder bidDepthsDecoder = md.bidDepth();
		int numBidDepth = bidDepthsDecoder.count();
		int[] bidDepths = null;
		if (numBidDepth > 0) {
			bidDepths = new int[numBidDepth];
			int i = 0;
			for (OrderBookSnapshotSbeDecoder.BidDepthDecoder entry : bidDepthsDecoder){
				bidDepths[i] = OrderBookSnapshotEntryFbs.createOrderBookSnapshotEntryFbs(builder, 
						entry.price(),
						entry.quantity(),
						entry.tickLevel());
				i++;
			}
		}
		int askDepthOffset = numAskDepth == 0 ? 0 : OrderBookSnapshotFbs.createAskDepthVector(builder, askDepths);
		int bidDepthOffset = numBidDepth == 0 ? 0 : OrderBookSnapshotFbs.createBidDepthVector(builder, bidDepths);

		OrderBookSnapshotFbs.startOrderBookSnapshotFbs(builder);
		OrderBookSnapshotFbs.addSecSid(builder, md.secSid());
		OrderBookSnapshotFbs.addSeqNum(builder, md.seqNum());
		OrderBookSnapshotFbs.addTransactTime(builder, md.transactTime());
		OrderBookSnapshotFbs.addAskDepth(builder, askDepthOffset);
		OrderBookSnapshotFbs.addBidDepth(builder, bidDepthOffset);		
		
		md.limit(limit);
		return OrderBookSnapshotFbs.endOrderBookSnapshotFbs(builder);
	}
}
