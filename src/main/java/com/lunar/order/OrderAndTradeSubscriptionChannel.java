package com.lunar.order;

import com.lunar.core.SubscriberList;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.OrderAndTradeSnapshotService.OrderBuffer;
import com.lunar.order.OrderAndTradeSnapshotService.TradeBuffer;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class OrderAndTradeSubscriptionChannel {
	private final int channelId;
	private boolean init;
	private final ReferenceOpenHashSet<OrderBuffer> orderUpdates;
	private final ReferenceOpenHashSet<TradeBuffer> tradeUpdates;
	private final SubscriberList orderSubscribers;
	private final SubscriberList tradeSubscribers;
	
	/** 
	 * seq keeps changing.  It will give a false-sharing cache effect 
	 * that subscribers have been changed.  */
	private long seq;
	
	public static OrderAndTradeSubscriptionChannel of(int channelId, long startSeq, int initialOrderCapacity, int initialTradeCapacity){
		return new OrderAndTradeSubscriptionChannel(channelId, true, initialOrderCapacity, initialTradeCapacity, startSeq);
	}
	
	public static OrderAndTradeSubscriptionChannel of(int channelId, int initialOrderCapacity, int initialTradeCapacity){
		return new OrderAndTradeSubscriptionChannel(channelId, false, initialOrderCapacity, initialTradeCapacity, ServiceConstant.NULL_SEQUENCE);
	}

	OrderAndTradeSubscriptionChannel(int channelId, boolean init, int initialOrderCapacity, int initialTradeCapacity, long startSeq){
		if (init && startSeq <= 0){
			throw new IllegalArgumentException("Initial sequence must be greater than zero in order to be declared as initialized: " + startSeq);
		}
		this.channelId = channelId;
		this.init = init;
		this.seq = startSeq;
		this.orderUpdates = new ReferenceOpenHashSet<>(initialOrderCapacity);
		this.tradeUpdates = new ReferenceOpenHashSet<>(initialTradeCapacity);
		this.orderSubscribers = SubscriberList.of();
		this.tradeSubscribers = SubscriberList.of();
	}
	
	
	public OrderAndTradeSubscriptionChannel init(long sequence){
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
	
	public OrderAndTradeSubscriptionChannel sequence(long value){
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
	public boolean addOrderSubscriber(MessageSinkRef sink){
		return orderSubscribers.add(sink);
	}
	
	public boolean removeOrderSubscriber(MessageSinkRef sink){
		return orderSubscribers.remove(sink);
	}
	
	public int numOrderSubscribers(){
		return orderSubscribers.size();
	}
	
	public boolean addTradeSubscriber(MessageSinkRef sink){
		return tradeSubscribers.add(sink);
	}
	
	public boolean removeTradeSubscriber(MessageSinkRef sink){
		return tradeSubscribers.remove(sink);
	}

	public int numTradeSubscribers(){
		return tradeSubscribers.size();
	}

	public ReferenceOpenHashSet<OrderBuffer> orderUpdates(){
		return orderUpdates;
	}
	
	public ReferenceOpenHashSet<TradeBuffer> tradeUpdates(){
		return tradeUpdates;
	}
	
	public boolean addOrderUpdate(OrderBuffer orderBuffer){
		return orderUpdates.add(orderBuffer);
	}
	
	public void clearOrderUpdates(){
		this.orderUpdates.clear();
	}
	
	public boolean addTradeUpdate(TradeBuffer tradeBuffer){
		return tradeUpdates.add(tradeBuffer);
	}

	public void clearTradeUpdates(){
		this.tradeUpdates.clear();
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
	public SubscriberList orderSubscribers(){
		return this.orderSubscribers;
	}

	public SubscriberList tradeSubscribers(){
		return this.tradeSubscribers;
	}
}
