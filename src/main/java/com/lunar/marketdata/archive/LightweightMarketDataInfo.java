package com.lunar.marketdata.archive;

import java.util.concurrent.atomic.AtomicInteger;

import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * See if we can modify this class to fit it inside a long.  If yes, we can pack all info into a
 * Long2Long hash map.
 * 
 * Each field should only be written by one thread
 * 
 * @author wongca
 *
 */
public final class LightweightMarketDataInfo {
	private boolean isSubscribed; // written by market data service
	private AtomicInteger seq; // written by real-time service
	private final ObjectArrayList<MessageSinkRef> subscribers;
	
	public LightweightMarketDataInfo(){
		seq = new AtomicInteger(0);
		isSubscribed = false;
		MessageSinkRef[] items = new MessageSinkRef[ServiceConstant.DEFAULT_MKT_DATA_SUBSCRIBERS_CAPACITY];
		this.subscribers = ObjectArrayList.wrap(items);
		this.subscribers.size(0);
	}
	
	public boolean isSubscribed(){
		return isSubscribed;
	}

	public LightweightMarketDataInfo subscribed(boolean isSubscribed){
		this.isSubscribed = isSubscribed;
		return this;
	}

	public int getAndIncrementSeq(){
		return seq.getAndIncrement();
	}
	
	public boolean addSubscriber(MessageSinkRef sink){
		if (!subscribers.contains(sink)){
			subscribers.add(sink);
			isSubscribed = true;
			return true;			
		}
		return false;
	}
	
	public boolean removeSubscriber(MessageSinkRef sink){
		boolean removed =  subscribers.rem(sink);
		isSubscribed = !subscribers.isEmpty();
		return removed;
	}
	
	public boolean publish(Object data){
		return false;
	}
}
