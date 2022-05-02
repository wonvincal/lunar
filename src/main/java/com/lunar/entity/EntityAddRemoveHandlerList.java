package com.lunar.entity;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.lang.reflect.Array;


public class EntityAddRemoveHandlerList<T extends Entity> {
	private final ListWrapper<T> handlersWrapper;
	private final EntityAddRemoveHandler<T> nullHandler;
	private EntityAddRemoveHandler<T> handler;

	EntityAddRemoveHandlerList(int capacity, EntityAddRemoveHandler<T> nullHandler){
		this.handlersWrapper = new ListWrapper<T>(capacity);
		this.handler = nullHandler;
		this.nullHandler = nullHandler;
	}
	public EntityAddRemoveHandler<T> add(EntityAddRemoveHandler<T> newHandler){
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
	public EntityAddRemoveHandler<T> remove(EntityAddRemoveHandler<T> remove){
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
	public EntityAddRemoveHandler<T> nullHandler(){
		return nullHandler;
	}
	public EntityAddRemoveHandler<T> handler(){
		return this.handler;
	}
	private static class ListWrapper<T extends Entity> implements EntityAddRemoveHandler<T> {
		private final ReferenceArrayList<EntityAddRemoveHandler<T>> handlers;
		ListWrapper(int capacity){
			@SuppressWarnings("unchecked")
			EntityAddRemoveHandler<T>[] item = (EntityAddRemoveHandler<T>[])(Array.newInstance(EntityAddRemoveHandler.class, capacity));
			handlers = ReferenceArrayList.wrap(item);
			handlers.size(0);
		}
		boolean add(EntityAddRemoveHandler<T> handler){
			if (!handlers.contains(handler)){
				return handlers.add(handler);
			}
			return true; // return turn if it is already in the list
		}
		public boolean contains(EntityAddRemoveHandler<T> handler){
			return handlers.contains(handler);
		}
		public boolean remove(EntityAddRemoveHandler<T> handler){
			return handlers.rem(handler);
		}		
		public void clear(){
			handlers.clear();
		}
		public EntityAddRemoveHandler<T> get(int index){
			return handlers.get(0);
		}
		public int size(){
			return handlers.size();
		}
		public boolean isEmpty(){
			return handlers.isEmpty();
		}
		@Override
		public void onAdd(T entity) {
			EntityAddRemoveHandler<T>[] items = handlers.elements(); 
			for (int i = 0; i < items.length; i++){
				items[i].onAdd(entity);
			}
		}
		@Override
		public void onRemove(T entity) {
			EntityAddRemoveHandler<T>[] items = handlers.elements(); 
			for (int i = 0; i < items.length; i++){
				items[i].onRemove(entity);
			}
		}
	}
}
