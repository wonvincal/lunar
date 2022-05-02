package com.lunar.message.io.fbs;


import java.nio.ByteBuffer;

import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.marketdata.MarketStats;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeEncoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.sender.MarketStatsSender;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.junit.Assert.*;

public class MarketStatsFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(MarketStatsFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final MarketStatsSbeEncoder sbeEncoder = new MarketStatsSbeEncoder();
	private final MarketStatsSbeDecoder sbeDecoder = new MarketStatsSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
	    MarketStats stats = MarketStats.of();
	    stats.open(123);
	    stats.close(321);
	    stats.turnover(12345);
	    stats.volume(54321);
	    MarketStatsSender.encodeMarketStatsWithoutHeader(buffer, 0, sbeEncoder, stats);
		sbeDecoder.wrap(buffer, 0, StrategyWrtParamsSbeDecoder.BLOCK_LENGTH, StrategyWrtParamsSbeDecoder.SCHEMA_VERSION);
		
		int offset = MarketStatsFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder);
		builder.finish(offset);
		
		MarketStatsFbs marketStatsFbs = MarketStatsFbs.getRootAsMarketStatsFbs(builder.dataBuffer());
		assertMarketStats(stats, marketStatsFbs);
	}
	
	public static void assertMarketStats(MarketStats stats, MarketStatsFbs statsFbs){
        assertEquals(stats.open(), statsFbs.open());
        assertEquals(stats.close(), statsFbs.close());
        assertEquals(stats.turnover(), statsFbs.turnover());
        assertEquals(stats.volume(), statsFbs.volume());        
	}
}

