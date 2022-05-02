package com.lunar.marketdata.archive;

import com.lunar.config.ServiceConfig;

/**
 * Two threads getting multicast traffics from each channel
 * There are be lots of channels.  For channels with high frequency of data and low latency requirement, we want to have
 * @author wongca
 *
 */
public abstract class MarketDataRealtimeFeed implements Runnable {
	
	public MarketDataRealtimeFeed(ServiceConfig config){}

	abstract public MarketDataSubscriptionManager subscriptionManager();
}