package com.lunar.core;

import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.agrona.MutableDirectBuffer;

public class SubscriptionChannel {
	private final int channelId;
	private boolean init;
	private final ReferenceOpenHashSet<MutableDirectBuffer> updates;
	private final SubscriberList subscribers;
	/** 
	 * seq keeps changing.  It will give a false-sharing cache effect 
	 * that subscribers have been changed.  */
	private long seq;
	
	public static SubscriptionChannel of(int channelId, long startSeq, int initialCapacity){
		return new SubscriptionChannel(channelId, true, initialCapacity, startSeq);
	}
	
	public static SubscriptionChannel of(int channelId, int initialCapacity){
		return new SubscriptionChannel(channelId, false, initialCapacity, ServiceConstant.NULL_SEQUENCE);
	}

	SubscriptionChannel(int channelId, boolean init, int initialCapacity, long startSeq){
		if (init && startSeq <= 0){
			throw new IllegalArgumentException("Initial sequence must be greater than zero in order to be declared as initialized");
		}
		this.channelId = channelId;
		this.init = init;
		this.seq = startSeq;
		this.updates = new ReferenceOpenHashSet<>(initialCapacity);
		this.subscribers = SubscriberList.of();
	}
	
	
	public SubscriptionChannel init(long sequence){
		this.seq = sequence;
		this.init = true;
		return this;
	}

	public boolean init(){
		return init;
	}
	
	public int channelId(){
		return channelId;
	}

	public long sequence(){
		return seq;
	}
	
	public SubscriptionChannel sequence(long value){
		this.seq = value;
		return this;
	}
	
	public long getAndIncrementSeq(){
		return seq++;
	}

	/**
	 * Add subscriber only if it does not exist
	 * @param sink
	 * @return
	 */
	public boolean addSubscriber(MessageSinkRef sink){
		return subscribers.add(sink);
	}
	
	public boolean removeSubscriber(MessageSinkRef sink){
		return subscribers.remove(sink);
	}
	
	public int numSubscribers(){
		return subscribers.size();
	}
	
	public ReferenceOpenHashSet<MutableDirectBuffer> updates(){
		return updates;
	}
	
	/**
	 * Return subscribers array.
	 * 
	 * Needs to think about this clearly
	 * <ol>
	 * <li>By using ObjectArrayList, whenever a new subscriber is added, the backing
	 * array will increase by at most one element.  During that time, a new array is
	 * created.
	 * <li>When a subscriber is removed, the backing array won't be shrunk.
	 * </ol>
	 * Should we ...
	 * 
	 * @return
	 */
	public MessageSinkRef[] subscribers(){
		return this.subscribers.elements();
	}
}
