package com.lunar.core;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import com.lunar.core.ServiceStatusTracker.AggregatedServiceStatusChangeHandler;


final class AggregatedServiceStatusChangeHandlerList implements AggregatedServiceStatusChangeHandler {
	private final ListWrapper handlersWrapper;
	private final AggregatedServiceStatusChangeHandler nullHandler;
	private AggregatedServiceStatusChangeHandler handler;

	private static AggregatedServiceStatusChangeHandler NULL_AGGREGATED_SERVICE_STATUS_HANDLER = new AggregatedServiceStatusChangeHandler() {
		@Override
		public void handle(boolean status) {}
	};
	
	public static AggregatedServiceStatusChangeHandlerList of(int capacity){
		return new AggregatedServiceStatusChangeHandlerList(capacity, NULL_AGGREGATED_SERVICE_STATUS_HANDLER);
	}

	AggregatedServiceStatusChangeHandlerList(int capacity, AggregatedServiceStatusChangeHandler nullHandler){
		this.handlersWrapper = new ListWrapper(capacity);
		this.handler = nullHandler;
		this.nullHandler = nullHandler;
	}
	public AggregatedServiceStatusChangeHandler add(AggregatedServiceStatusChangeHandler newHandler){
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
		if (handlersWrapper.isEmpty()){
			this.handlersWrapper.add(handler);
			this.handlersWrapper.add(newHandler);
		}
		else if (!handlersWrapper.contains(newHandler)){
			this.handlersWrapper.add(newHandler);
		}
		this.handler = this.handlersWrapper;
		return this.handler;
	}
	public AggregatedServiceStatusChangeHandler remove(AggregatedServiceStatusChangeHandler remove){
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
	public AggregatedServiceStatusChangeHandler nullHandler(){
		return nullHandler;
	}
	
	private static class ListWrapper implements AggregatedServiceStatusChangeHandler {
		private final ReferenceArrayList<AggregatedServiceStatusChangeHandler> handlers;
		ListWrapper(int capacity){
			handlers = ReferenceArrayList.wrap(new AggregatedServiceStatusChangeHandler[capacity]);
			handlers.size(0);
		}
		boolean add(AggregatedServiceStatusChangeHandler handler){
			if (!handlers.contains(handler)){
				return handlers.add(handler);
			}
			return true; // return turn if it is already in the list
		}
		@Override
		public void handle(boolean status) {
			AggregatedServiceStatusChangeHandler[] items = handlers.elements();
			for (int i = 0; i < handlers.size(); i++){
				items[i].handle(status);
			}
		}
		public boolean contains(AggregatedServiceStatusChangeHandler handler){
			return handlers.contains(handler);
		}
		public boolean remove(AggregatedServiceStatusChangeHandler handler){
			return handlers.rem(handler);
		}		
		public void clear(){
			handlers.clear();
		}
		public AggregatedServiceStatusChangeHandler get(int index){
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
	public void handle(boolean status) {
		this.handler.handle(status);
	}
	
	public boolean hasHandler(){
		return (handler != nullHandler) || (handlersWrapper.size() > 0);
	}
}
