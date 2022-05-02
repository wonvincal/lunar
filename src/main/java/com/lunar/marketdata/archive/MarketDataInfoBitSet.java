package com.lunar.marketdata.archive;

public interface MarketDataInfoBitSet {
	/**
	 * 
	 * @param sid Internal system id
	 * @param value
	 */
	void setSubscribed(long sid, boolean value);
	
	/**
	 * 
	 * @param id source specific 32 bits id
	 * @return
	 */
	int isSubscribed(int id);
	
	/**
	 * Get a volatile variable to make sure cached data is refreshed
	 * @return
	 */
	long volatileUpdateTime();
}
