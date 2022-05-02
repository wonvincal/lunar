package com.lunar.analysis;

import com.lunar.entity.ChartData;
import com.lunar.entity.ChartData.VolumeAtPrice;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

public interface ChartDataHandler {
	void handleNewChartData(ChartData data);
	void handleNewChartData(long secSid,
			long dataTime, int windowSizeInSec, 
			int open, int close, int high, int low, int vwap, int valueAreaHigh, int valueAreaLow,
			VolumeClusterDirectionType dirType,
			Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices);
	
	public static final ChartDataHandler NULL_CHART_DATA_HANDLER = new ChartDataHandler() {
		
		@Override
		public void handleNewChartData(ChartData data) {
		}
		
		@Override
		public void handleNewChartData(long secSid,
				long dataTime, int windowSizeInSec, 
				int open, int close, int high, int low, int vwap, int valueAreaHigh, int valueAreaLow,
				VolumeClusterDirectionType dirType,
				Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices){

		}
		
	};
}
