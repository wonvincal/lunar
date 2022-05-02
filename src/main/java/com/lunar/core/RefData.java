package com.lunar.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

/**
 * It is likely that the child class will have fields like
 * int field1;
 * int field2;
 * int field3;
 * List subscribers
 * How can we make sure that the subscribers will be on the same cache line as the last update?
 * Better tune this carefully per entity type
 * 
 * @author Calvin
 *
 */
//public class RefData extends RefDataColdFields implements Entity {
public class RefData extends RefDataColdFields  {
	@SuppressWarnings("unused")
	private final int sid;
	private final ObjectArrayList<MessageSinkRef> subscribers;

	public RefData(int sid, int initialCapacity, String name){
		super(name);
		this.sid = sid;
		MessageSinkRef[] items = new MessageSinkRef[ServiceConstant.DEFAULT_REF_DATA_SUBSCRIBERS_CAPACITY];
		this.subscribers = ObjectArrayList.wrap(items);
		this.subscribers.size(0);
	}
	
//	@Override
//	public int sid() {
//		return sid;
//	}
//
//	@Override
//	public MessageSinkRef[] subscribers() {
//		return this.subscribers.elements();
//	}
	
	public void addSubscriber(MessageSinkRef sinkRef){
		if (!this.subscribers.contains(sinkRef)){
			this.subscribers.add(sinkRef);
		}
	}

	public boolean removeSubscriber(MessageSinkRef sinkRef){
		return this.subscribers.rem(sinkRef);
	}
}
