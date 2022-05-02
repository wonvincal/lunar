package com.lunar.core;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class PerfDataManager {
	private final Int2ObjectMap<PerfData> perfDataBySinkId;
	
	public PerfDataManager(){
		perfDataBySinkId = new Int2ObjectOpenHashMap<PerfData>();
	}
	
	public PerfDataManager roundTripNs(int sinkId, long latency){
		if (perfDataBySinkId.containsKey(sinkId)){
			perfDataBySinkId.get(sinkId).latency(latency);
		}
		else{
			perfDataBySinkId.put(sinkId, new PerfData(sinkId, latency));
		}
		return this;
	}
	
	public Int2ObjectMap<PerfData> perfDataBySinkId(){
		return perfDataBySinkId;
	}
	
	public static class PerfData {
		private final int sinkId;
		private long roundTripNs;
		public PerfData(int sinkId, long latency){
			this.sinkId = sinkId;
			this.roundTripNs = latency;
		}
		public long roundTripNs(){
			return roundTripNs;
		}
		public int sinkId(){
			return sinkId;
		}
		public PerfData latency(long latency){
			this.roundTripNs = latency;
			return this;
		}
	}
}
