package com.lunar.core;

import com.lunar.message.sink.MessageSinkRef;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class MessageSinkRefList {
	/**
	 * Use ObjectArrayList instead of plain array so that 
	 * I can use its add and remove methods
	 */
	private final ObjectArrayList<MessageSinkRef> subscribers;

    public static MessageSinkRefList of(int initialCapacity){
        return new MessageSinkRefList(initialCapacity);
    }
	
	public static MessageSinkRefList of(){
		return new MessageSinkRefList();
	}
	
	MessageSinkRefList(){
	    this(0);
	}
	
	MessageSinkRefList(int initialCapacity){
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
