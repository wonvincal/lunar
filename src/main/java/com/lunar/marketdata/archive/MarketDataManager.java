package com.lunar.marketdata.archive;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;
import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.longs.Long2ObjectVanillaOpenHashMap;

/**
 * 
 * @author wongca
 *
 */
public final class MarketDataManager {
	static final Logger LOG = LogManager.getLogger(MarketDataManager.class);
	// TODO create our own hash map that has 'hasSubscribers' info
	// immediately accessible (instead of thru MarketData)
	private final Long2ObjectVanillaOpenHashMap<SubscribableMarketOrderBook> items;
	
	/**
	 * Provide a quick way to look at aggregated market data info of a security 
	 */
	private final MarketDataInfoBitSet mdi;
	private final SubscribableMarketOrderBookBuilder builder;
	
	MarketDataManager(int capacity, MarketDataInfoBitSet mdi, SubscribableMarketOrderBookBuilder builder){
		items = new Long2ObjectVanillaOpenHashMap<SubscribableMarketOrderBook>(capacity);		
		this.mdi = mdi;
		this.builder = builder;
	}
	
	public static MarketDataManager of(int capacity, MarketDataInfoBitSet mdi, SubscribableMarketOrderBookBuilder builder){
		return new MarketDataManager(capacity, mdi, builder);
	}
	
	public void subscribe(long secSid, MessageSinkRef sink){
		SubscribableMarketOrderBook ob = items.get(secSid);
		if (ob == items.defaultReturnValue()){
			ob = builder.build(secSid);
			items.put(secSid, ob);
		}
		ob.addSubscriber(sink);
		mdi.setSubscribed(secSid, ob.hasSubscriber());
	}
	
	public boolean unsubscribe(long secSid, MessageSinkRef subscriber){
		SubscribableMarketOrderBook ob = items.get(secSid);
		if (ob == items.defaultReturnValue()){
			LOG.warn("no subscription to remove for instId:{}, subscriber:{}", secSid, subscriber);
			return true;
		}
		boolean result = ob.removeSubscriber(subscriber);
		mdi.setSubscribed(secSid, ob.hasSubscriber());
		return result;
	}
	
	public void process(AggregateOrderBookUpdateSbeDecoder snapshot){
		SubscribableMarketOrderBook ob = items.get(snapshot.secSid());
		
		// most of the time we will have a non-null value because we should
		// only be getting updates for the ones that we've subscribed
		// we can set default value of the map such that a null handler will 
		// be returned, but that can only be made if SecurityMarketData is 
		// not final, which impacts performance
		if (ob != items.defaultReturnValue()){
			EntryDecoder entries = snapshot.entry();
			while (entries.hasNext()){
				ob.process(entries.next());
			}
		}
	}
	
}