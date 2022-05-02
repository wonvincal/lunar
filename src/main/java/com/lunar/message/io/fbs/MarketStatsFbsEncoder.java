package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;

/**
 * One sender for object
 * Thread Safety: No
 * @author wongca
 *
 */
public class MarketStatsFbsEncoder {
	static final Logger LOG = LogManager.getLogger(MarketStatsFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, MarketStatsSbeDecoder stats){
	    MarketStatsFbs.startMarketStatsFbs(builder);
	    MarketStatsFbs.addSecSid(builder, stats.secSid());
	    MarketStatsFbs.addOpen(builder, stats.open());
	    MarketStatsFbs.addHigh(builder, stats.high());
	    MarketStatsFbs.addLow(builder, stats.low());
	    MarketStatsFbs.addClose(builder, stats.close());
	    MarketStatsFbs.addVolume(builder, stats.volume());
	    MarketStatsFbs.addTurnover(builder, stats.turnover());
	    MarketStatsFbs.addTransactTime(builder, stats.transactTime());
	    MarketStatsFbs.addSeqNum(builder, stats.seqNum());
		return MarketStatsFbs.endMarketStatsFbs(builder);
	}
}

