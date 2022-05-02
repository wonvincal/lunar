package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.ChartDataSbeDecoder.VolAtPricesDecoder;

public class ChartDataFbsEncoder {
	static final Logger LOG = LogManager.getLogger(ChartDataFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, ChartDataSbeDecoder chartData, int[] buffer){
		int limit = chartData.limit();
		
		VolAtPricesDecoder volAtPrices = chartData.volAtPrices();
		int numPrices = volAtPrices.count();
		
		int i = 0;
		for (VolAtPricesDecoder volAtPrice : volAtPrices){
			buffer[i] = VolAtPriceFbs.createVolAtPriceFbs(builder, 
					volAtPrice.price(), 
					volAtPrice.bidQty(), 
					volAtPrice.askQty(), 
					(volAtPrice.significant() == BooleanType.TRUE) ? true : false,
					volAtPrice.volumeClusterType().value());
			i++;
		}

		ChartDataFbs.startVolAtPricesVector(builder, numPrices);				
		for (i = numPrices - 1; i >= 0; i--){
			builder.addOffset(buffer[i]);
		}
		int offset = builder.endVector();

		ChartDataFbs.startChartDataFbs(builder);
		ChartDataFbs.addSecSid(builder, chartData.secSid());
		ChartDataFbs.addDataTime(builder, chartData.dataTime());
		ChartDataFbs.addWindowSizeInSec(builder, chartData.windowSizeInSec());
	    ChartDataFbs.addVolAtPrices(builder, offset);
	    ChartDataFbs.addValueAreaLow(builder, chartData.valueAreaLow());
	    ChartDataFbs.addValueAreaHigh(builder, chartData.valueAreaHigh());
	    ChartDataFbs.addVwap(builder, chartData.vwap());
	    ChartDataFbs.addLow(builder, chartData.low());
	    ChartDataFbs.addClose(builder, chartData.close());
	    ChartDataFbs.addHigh(builder, chartData.high());
	    ChartDataFbs.addOpen(builder, chartData.open());
	    ChartDataFbs.addVolClusterDirection(builder, chartData.volClusterDirection().value());
		chartData.limit(limit);
		return ChartDataFbs.endChartDataFbs(builder);
	}
}
