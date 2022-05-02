package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;

public class AggregateOrderBookUpdateFbsEncoder {
	static final Logger LOG = LogManager.getLogger(AggregateOrderBookUpdateFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, AggregateOrderBookUpdateSbeDecoder md){
		EntryDecoder entryGroup = md.entry();
		int numEntires = entryGroup.count();
		int[] entryEndOffsets = new int[numEntires];
		int i = 0;
		for (EntryDecoder entry : entryGroup){
			entryEndOffsets[i] = AggregateOrderBookUpdateEntryFbs.createAggregateOrderBookUpdateEntryFbs(builder, 
					entry.transactTime(), 
					entry.price(), 
					entry.tickLevel(), 
					entry.quantity(), 
					entry.numOrders(), 
					entry.entryType().value(), 
					entry.priceLevel(), 
					entry.side().value(), 
					entry.updateAction().value());
			i++;
		}
		int entriesOffset = AggregateOrderBookUpdateFbs.createEntriesVector(builder, entryEndOffsets);
		AggregateOrderBookUpdateFbs.startAggregateOrderBookUpdateFbs(builder);
		AggregateOrderBookUpdateFbs.addSecSid(builder, md.secSid());
		AggregateOrderBookUpdateFbs.addSeqNum(builder, md.seqNum());
		if (md.isSnapshot() != BooleanType.NULL_VAL){
			AggregateOrderBookUpdateFbs.addIsSnapshot(builder, (md.isSnapshot() == BooleanType.TRUE) ? true : false);
		}
		AggregateOrderBookUpdateFbs.addEntries(builder, entriesOffset);
		return AggregateOrderBookUpdateFbs.endAggregateOrderBookUpdateFbs(builder);
	}
}
