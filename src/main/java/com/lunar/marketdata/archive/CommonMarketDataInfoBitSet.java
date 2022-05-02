package com.lunar.marketdata.archive;

import it.unimi.dsi.fastutil.ints.Int2IntVanillaOpenHashMap;

/**
 * Since we have store only one piece of information (i.e. isSubscribed) at the
 * moment, there is no need to use bitwise mask.
 * @author Calvin
 *
 */
public class CommonMarketDataInfoBitSet implements MarketDataInfoBitSet {
	private final Int2IntVanillaOpenHashMap flags;
	private volatile long updateTime;
	
	public CommonMarketDataInfoBitSet(int capacity){
		this.flags = new Int2IntVanillaOpenHashMap(capacity);
	}
	@Override
	public void setSubscribed(long sid, boolean isSubscribed){
		int srcSpecificId = (int)sid; // take only the lower 32 bits
		int changeTo = (isSubscribed ? 1 : 0);
		if (this.flags.get(srcSpecificId) != changeTo){
			this.flags.put(srcSpecificId, changeTo);
			updateTime = System.nanoTime();			
		}
	}
	public long volatileUpdateTime(){
		return updateTime;
	}
	/**
	 * 
	 * @param id
	 * @return 0 if not subscribed, otherwise 1
	 */
	public int isSubscribed(int id){
		return this.flags.get(id);
	}
	
}
