package com.lunar.entity;

import com.lunar.core.SbeEncodable;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public abstract class Entity implements SbeEncodable {
	private int lastUpdateSeq;
	private final long sid;
	// TODO test if sid should be placed next to subscribers
	// since the likely use case is: 1) access sid, 2) access subscribers
	private final ObjectArrayList<MessageSinkRef> subscribers;

	Entity(long sid){
		this.sid = sid;
		MessageSinkRef[] items = new MessageSinkRef[ServiceConstant.DEFAULT_REF_DATA_SUBSCRIBERS_CAPACITY];
		this.subscribers = ObjectArrayList.wrap(items);
		this.subscribers.size(0);
	}
	public long sid(){
		return this.sid;
	}
	public MessageSinkRef[] subscribers(){
		return this.subscribers.elements();
	}
	/**
	 * Add subscriber that listens to changes of entity
	 * @param sink
	 * @return
	 */
	boolean addSubscriber(MessageSinkRef sink){
		if (!subscribers.contains(sink)){
			return subscribers.add(sink);
		}
		return false;
	}
	boolean removeSubscriber(MessageSinkRef sink){
		return subscribers.rem(sink);		
	}
	public Entity lastUpdateSeq(int value){
		this.lastUpdateSeq = value;
		return this;
	}
	public int lastUpdateSeq(){
		return lastUpdateSeq;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (sid ^ (sid >>> 32));
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Entity other = (Entity) obj;
		if (sid != other.sid)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "entitySid: " + sid;
	}
}
