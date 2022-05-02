package com.lunar.message.io.fbs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.entity.ChartData;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.ChartDataSbeEncoder;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;
import com.lunar.message.sender.ChartDataSender;
import com.lunar.service.ServiceConstant;

public class ChartDataFbsEncoderTest {
	static final Logger LOG = LogManager.getLogger(ChartDataFbsEncoderTest.class);

	private final MutableDirectBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
	private final ChartDataSbeEncoder sbeEncoder = new ChartDataSbeEncoder();
	private final ChartDataSbeDecoder sbeDecoder = new ChartDataSbeDecoder();
	private final ByteBuffer fbsStringBuffer = ByteBuffer.allocate(ServiceConstant.MAX_STRING_SIZE);
	private FlatBufferBuilder builder = new FlatBufferBuilder(0);
	
	@Test
	public void test(){
		final long secSid = 12345;
		final int vwap = 94100;
		final long dataTime =  LocalDateTime.now().getNano();
		final int windowSizeInSec = 60;
		final int open = 94150;
		final int close = 94350;
		final int high = 94450;
		final int low = 94010;
		final int pointOfControl = 94190;
		final int valueAreaLow = 94180;
		final int valueAreaHigh = 94320;
		final VolumeClusterDirectionType dirType = VolumeClusterDirectionType.DOWN;
		ChartData entity = ChartData.of(secSid, dataTime, windowSizeInSec, open, 
				close, high, low, pointOfControl, valueAreaLow, 
				valueAreaHigh, vwap, dirType);
		
		final int[] volAtPrices = new int[]{94180, 94190, 94200, 94210, 94220, 94230, 94240, 94250, 94260, 94270};
		final long[] bidQtyList = new long[]{23, 24, 25, 26, 27, 28, 29, 30, 31, 32};
		final long[] askQtyList = new long[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
		final boolean[] significantList = new boolean[]{false, true, false, true, false, true, false, true, false, true};
		
		for (int i = 0 ; i < volAtPrices.length; i++){
			entity.addAskQty(volAtPrices[i], askQtyList[i]);
			entity.addBidQty(volAtPrices[i], bidQtyList[i]);
			entity.significant(volAtPrices[i], significantList[i]);
		}
		
		ChartDataSender.encodeChartDataOnly(buffer, 0, sbeEncoder, entity);
		sbeDecoder.wrap(buffer, 0, ChartDataSbeDecoder.BLOCK_LENGTH, ChartDataSbeDecoder.SCHEMA_VERSION);
		int[] intBuffer = new int[128];
		int offset = ChartDataFbsEncoder.toByteBufferInFbs(builder, fbsStringBuffer, sbeDecoder, intBuffer);
		builder.finish(offset);
		
		ChartDataFbs chartDataFbs = ChartDataFbs.getRootAsChartDataFbs(builder.dataBuffer());
		assertEquals(secSid, chartDataFbs.secSid());
		assertEquals(vwap, chartDataFbs.vwap());
		assertEquals(dataTime, chartDataFbs.dataTime());
		assertEquals(windowSizeInSec, chartDataFbs.windowSizeInSec());
		assertEquals(open, chartDataFbs.open());
		assertEquals(close, chartDataFbs.close());
		assertEquals(high, chartDataFbs.high());
		assertEquals(low, chartDataFbs.low());
		assertEquals(valueAreaLow, chartDataFbs.valueAreaLow());
		assertEquals(valueAreaHigh, chartDataFbs.valueAreaHigh());
		assertEquals(dirType.value(), chartDataFbs.volClusterDirection());
	
		for (int i = 0 ; i < volAtPrices.length; i++){
			VolAtPriceFbs fbs = chartDataFbs.volAtPrices(i);
			assertEquals(volAtPrices[i], fbs.price());
			assertEquals(bidQtyList[i], fbs.bidQty());
			assertEquals(askQtyList[i], fbs.askQty());
			assertEquals(significantList[i], fbs.significant());
		}
	}
}
