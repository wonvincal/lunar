package com.lunar.core;

import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * <strong>IMPORTANT</strong><br/><br/>
 * You need to use both {@link SubscriberList#elements()}
 * and {@link SubscriberList#size()} to traverse the list of subscribers.
 * @author wongca
 *
 */
public class SubscriberList {
	/**
	 * Use ObjectArrayList instead of plain array so that 
	 * I can use its add and remove methods
	 */
	private final ObjectArrayList<MessageSinkRef> subscribers;

    public static SubscriberList of(int initialCapacity){
        return new SubscriberList(initialCapacity);
    }
	
	public static SubscriberList of(){
		return new SubscriberList();
	}
	
	SubscriberList(){
	    this(0);
	}
	
    SubscriberList(int initialCapacity){
        this.subscribers = ObjectArrayList.wrap(new MessageSinkRef[initialCapacity]);
        this.subscribers.size(0);
    }
	

	/**
	 * Add subscriber only if it does not exist
	 * @param sink
	 * @return
	 */
	public boolean add(MessageSinkRef sink){
		if (!subscribers.contains(sink)){
			return subscribers.add(sink);
		}
		return false;
	}
	
	public boolean remove(MessageSinkRef sink){
		return subscribers.rem(sink);
	}
	
	public int size(){
		return subscribers.size();
	}

	/**
	 * Return subscribers array.  Size of array is not always equal to number of
	 * subscribers.
	 *  
	 * @return
	 */
	public MessageSinkRef[] elements(){
		return this.subscribers.elements();
	}

	public boolean contains(MessageSinkRef sink){
		return subscribers.contains(sink);
	}
	
	public void clear(){
		subscribers.clear();
	}
}
