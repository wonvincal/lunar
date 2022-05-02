package com.lunar.core;

import com.lunar.core.ServiceStatusTracker.ServiceStatusChangeHandler;
import com.lunar.message.ServiceStatus;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

final class ServiceStatusChangeHandlerList implements ServiceStatusChangeHandler {
	private final ListWrapper handlersWrapper;
	private final ServiceStatusChangeHandler nullHandler;
	private ServiceStatusChangeHandler handler;

	public static ServiceStatusChangeHandlerList of(int capacity, ServiceStatusChangeHandler nullHandler){
		return new ServiceStatusChangeHandlerList(capacity, nullHandler);
	}
	
	ServiceStatusChangeHandlerList(int capacity, ServiceStatusChangeHandler nullHandler){
		this.handlersWrapper = new ListWrapper(capacity);
		this.handler = nullHandler;
		this.nullHandler = nullHandler;
	}
	
	public ServiceStatusChangeHandler add(ServiceStatusChangeHandler newHandler){
		if (newHandler == null || newHandler == nullHandler){
			throw new IllegalArgumentException("cannot add null handler");
		}
		if (this.handler == nullHandler){ // no handler at the moment
			this.handler = newHandler;
			return this.handler;
		}
		if (this.handler == newHandler){ // one handler and is same as input
			return this.handler;
		}
		if (handlersWrapper.isEmpty()){ // add existing one and new handler to wrapper
			this.handlersWrapper.add(handler);
			this.handlersWrapper.add(newHandler);
		}
		else if (!handlersWrapper.contains(newHandler)){
			this.handlersWrapper.add(newHandler);
		}
		this.handler = this.handlersWrapper;
		return this.handler;
	}
	public ServiceStatusChangeHandler remove(ServiceStatusChangeHandler remove){
		if (remove == null || remove == nullHandler){
			throw new IllegalArgumentException("cannot remoive null handler");
		}
		if (handlersWrapper.remove(remove)){
			if (handlersWrapper.size() >= 2){
				assert this.handler == handlersWrapper;
				return this.handler; // return list wrapper is size >= 2
			}
			this.handler = handlersWrapper.get(0); // take the last handler out
			handlersWrapper.clear();
			return this.handler;
		}
		if (this.handler == remove){
			this.handler = nullHandler; // replace last active handler with null handler
		}
		return this.handler;
	}
	public ServiceStatusChangeHandler nullHandler(){
		return nullHandler;
	}
	
	private static class ListWrapper implements ServiceStatusChangeHandler {
		private final ReferenceArrayList<ServiceStatusChangeHandler> handlers;
		ListWrapper(int capacity){
			ServiceStatusChangeHandler[] backingArray = new ServiceStatusChangeHandler[capacity];
			handlers = ReferenceArrayList.wrap(backingArray);
			handlers.size(0);
		}
		boolean add(ServiceStatusChangeHandler handler){
			if (!handlers.contains(handler)){
				return handlers.add(handler);
			}
			return true; // return turn if it is already in the list
		}
		@Override
		public void handle(ServiceStatus status) {
			ServiceStatusChangeHandler[] items = handlers.elements();
			for (int i = 0; i < handlers.size(); i++){
				items[i].handle(status);
			}
		}
		public boolean contains(ServiceStatusChangeHandler handler){
			return handlers.contains(handler);
		}
		public boolean remove(ServiceStatusChangeHandler handler){
			return handlers.rem(handler);
		}		
		public void clear(){
			handlers.clear();
		}
		public ServiceStatusChangeHandler get(int index){
			return handlers.get(0);
		}
		public int size(){
			return handlers.size();
		}
		public boolean isEmpty(){
			return handlers.isEmpty();
		}
	}

	@Override
	public void handle(ServiceStatus status) {
		this.handler.handle(status);
	}

	/**
	 * 
	 * @return true if there is handler (exclude NULL_HANDLER) in the list
	 */
	public boolean hasHandler(){
		return (handler != nullHandler) || (handlersWrapper.size() > 0);
	}
}
