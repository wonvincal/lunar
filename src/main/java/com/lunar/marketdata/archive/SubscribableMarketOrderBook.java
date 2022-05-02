package com.lunar.marketdata.archive;

import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder.EntryDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * This class holds an order book of a security and its subscribers
 * @author wongca
 *
 */
public final class SubscribableMarketOrderBook {
	private final long secSid;
	private final ObjectArrayList<MessageSinkRef> subscribers;
	private final MarketOrderBook orderBook;
	
	SubscribableMarketOrderBook(long secSid, MarketOrderBook orderBook){
		this.secSid = secSid;
		this.orderBook = orderBook;
		MessageSinkRef[] items = new MessageSinkRef[ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY];
		this.subscribers = ObjectArrayList.wrap(items);
	}
	
	public long instSid(){
		return this.secSid;
	}
	
	public MarketOrderBook orderBook(){
		return this.orderBook;
	}
	
	public static SubscribableMarketOrderBook of(long instSid, MarketOrderBook newOrderBook){
		return new SubscribableMarketOrderBook(instSid, newOrderBook);
	}
	
	/**
	 * 
	 * @param sink
	 * @return true if sink is added
	 * 		   false if sink has already subscribed
	 */
	public boolean addSubscriber(MessageSinkRef sink){
		if (!subscribers.contains(sink)){
			return subscribers.add(sink);			
		}
		return false;
	}
	
	public boolean removeSubscriber(MessageSinkRef sink){
		return subscribers.rem(sink);
	}
	
	/**
	 * TODO move this as part of the key, a special hash map that
	 * @return
	 */
	public boolean hasSubscriber(){
		return !subscribers.isEmpty();
	}
	
	public void process(EntryDecoder entry){
		orderBook.process(entry);
	}
}
