package com.lunar.core;

import com.lunar.message.sink.MessageSinkRef;

/*
 * This class is to be instantiate by MarketDataService
 * MarketDataService will add or remove subscribers
 * The MarketDataSource, which is expected to run on a different thread from MarketDataService, will read the list of subscribers
 * This class needs to support one producer and multiple consumers.
 * To avoid locking, the subscription lists are immutable.
 * The assumption is that we have far fewer subscription calls (especially when running) than reading the list of subscribers.
 * To avoid using concurrent lock, there is a limitation in that the list of instruments must be known when this class is constructed
 * (although we can get around it by making subscriptionsBySymbol/subscriptionsBySid non-final and volatile,
 * and re-create the list when the instrument universe is updated)
 */
public class MultiThreadSubscriberList {
	private volatile MessageSinkRef[] sinks;
		
	final private int maxCapacity;
	
	public MultiThreadSubscriberList(int maxCapacity) {
		this.maxCapacity = maxCapacity;
		this.sinks = new MessageSinkRef[maxCapacity];
	}

	public boolean add(final MessageSinkRef sink) {
		final MessageSinkRef[] newSinks = new MessageSinkRef[maxCapacity];
		for (int i = 0; i < maxCapacity; i++) {
			final MessageSinkRef oldSink = sinks[i];				
			if (oldSink == null) {
				newSinks[i] = sink;
				sinks = newSinks;
				return true;
			}
			else if (oldSink.sinkId() == sink.sinkId()) {
				return true;
			}
			else {
				newSinks[i] = oldSink;
			}
		}
		return false;
	}
	
	public boolean removeMessageSink(final MessageSinkRef sink) {
			final MessageSinkRef[] newSinks = new MessageSinkRef[maxCapacity];
			int j = 0;
			boolean hasRemoved = false;
			for (int i = 0; i < maxCapacity; i++) {
				final MessageSinkRef oldSink = sinks[i];		
				if (oldSink == null) {
					break;
				}
				else if (oldSink.sinkId() != sink.sinkId()) {
					newSinks[j++] = oldSink;
				}
				else {
					hasRemoved = true;
				}
			}
			if (hasRemoved) {
				sinks = newSinks;
			}
		return false;
	}
	
	public MessageSinkRef[] elements() {
	    return sinks;
	}	
	
}
